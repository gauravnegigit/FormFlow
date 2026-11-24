import os , json 
from pathlib import Path 
from typing import Literal , Optional , TypedDict
from dotenv import load_dotenv
from pydantic import BaseModel, Field
from langchain_core.prompts import ChatPromptTemplate
from langgraph.graph import StateGraph, START, END
from client import _llm

class LLMRoutingChoice(BaseModel):
    chosen_role: str = Field(description="Must be exactly one of the available roles")
    priority: Literal["low", "normal", "high"] = "normal"
    reasoning: str = Field(description="1-2 sentences explaining the choice")

class RoutingDecision(BaseModel):
    assigned_role: Optional[str]
    priority: str
    reasons: list[str]

class RouterState(TypedDict, total=False):
    form_data: dict
    document_text: str
    verification: dict          # output of verify_document
    duplicates: list            # output of find_duplicates (list of dicts)
    reviewers: list[dict]       # [{"id","name","role","expertise","open_cases"}]
    guidelines: str             # org-defined routing instructions (free text)
    default_role: str
    context: str
    roles: list[str]
    choice: dict
    reasons: list[str]
    decision: dict

SYSTEM_PROMPT = """You route form submissions to the right reviewer role for an organization.
Choose ONE role from the AVAILABLE ROLES list, using the submission content, the
document findings and the organization's guidelines. Never invent roles.
If guidelines conflict with your own judgement, follow the guidelines.
Set priority 'high' for urgent, high-value, risky or inconsistent submissions.
Set priority 'medium' for standard submissions that meet expectations.
Set priority 'low' for low-value, low-risk or clearly invalid submissions."""

def build_context(state: RouterState) -> str:
    roles = {}
    for r in state["reviewers"]:
        roles.setdefault(r["role"], set())
        if r.get("expertise"):
            roles[r["role"]].add(r["expertise"])
    roles_desc = "\n".join(f"- {role}: {'; '.join(exp) or 'no description'}" for role, exp in roles.items())

    ver = state.get("verification") or {}
    ver_txt = (f"Matched: {ver.get('matched')}\nMismatched: {ver.get('mismatched')}\n"
               f"Issues: {ver.get('issues')}\nConfidence: {ver.get('confidence')}") if ver else "Not available"


def llm_route(state: RouterState) ->dict :
    prompt = ChatPromptTemplate.from_messages([("system" , SYSTEM_PROMPT) , ("human" , "{context}")])
    chain = prompt | _llm().with_structured_output(LLMRoutingChoice)
    try:
        choice = chain.invoke({"context": state["context"]})
        return {"choice": choice.model_dump()}
    except Exception as e:
        return {"choice": {"category": "uncategorised", "chosen_role": "", "priority": "normal",
                           "reasoning": f"LLM routing failed ({type(e).__name__}); fallback used"}}

def validate_choice(state: RouterState) -> dict :
    choice , roles , reasons = dict(state["choice"]) , state["roles"], list(state["reasons"])
    if choice["chosen_role"] not in roles :
        fallback = state.get("default_role")
        reasons.append(f"Invalid/missing role from AI; using fallback '{fallback}'")
        choice["chosen_role"] = fallback
    else :
        reasons.append(choice["reasoning"])
    return {"choice" : choice , "reasons" : reasons}

def assign_reviewer(state: RouterState) ->dict :
    role, choice = state["choice"]["chosen_role"], state["choice"]
    candidates = [r for r in state["reviewers"] if r["role"] == role]
    reasons = list(state["reasons"])
    reviewer_id = None
    if candidates:
        best = min(candidates, key=lambda r: r.get("open_cases", 0))  # load balancing
        reviewer_id = best["id"]
        reasons.append(f"Assigned to {best.get('name', best['id'])} (lowest workload in '{role}')")
    else:
        reasons.append("No reviewer available; left unassigned")
    decision = RoutingDecision(assigned_reviewer_id=reviewer_id, assigned_role=role or None,
                               category=choice["category"], priority=choice["priority"],
                               escalated=state["escalated"], reasons=reasons)
    return {"decision": decision.model_dump()}


g = StateGraph(RouterState)
g.add_node("build_context", build_context)
g.add_node("llm_route", llm_route)
g.add_node("validate_choice", validate_choice)
g.add_node("assign_reviewer", assign_reviewer)

g.add_edge(START, "build_context")
g.add_edge("build_context", "llm_route")
g.add_edge("llm_route", "validate_choice")
g.add_edge("validate_choice", "assign_reviewer")
g.add_edge("assign_reviewer", END)

graph = g.compile()

# ---------------- Public API ----------------
def route_submission(form_title: str, form_data: dict, reviewers: list[dict],document_text: str = "", verification: Optional[dict] = None,duplicates: Optional[list] = None, guidelines: str = "",default_role: str = "", escalation_role: str = "") -> dict:
    if not reviewers:
        return RoutingDecision(assigned_reviewer_id=None, assigned_role=None, category="unrouted",priority="normal", escalated=False,reasons=["No reviewers configured for this organization"]).model_dump()
    result = graph.invoke({
        "form_title": form_title, "form_data": form_data, "reviewers": reviewers,
        "document_text": document_text, "verification": verification or {},
        "duplicates": duplicates or [], "guidelines": guidelines,
        "default_role": default_role, "escalation_role": escalation_role})
    return result["decision"]
