import os
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Optional
from pydantic import BaseModel, Field
from client import _llm
from langchain_core.prompts import ChatPromptTemplate

class CommonIssue(BaseModel):
    theme: str = Field(description="Short name for a group of similar rejection reasons or "
                                   "document issues, e.g. 'Blurry document scan'.")
    count: int = Field(description="Total occurrences across the grouped items. "
                                   "Must be taken from the provided statistics, never estimated.")
    description: str = Field(description="One sentence explaining the issue and why it occurs.")

class NarrativeReport(BaseModel):
    executive_summary: str = Field(description="2-3 sentences covering volume, approval rate and backlog.")
    common_issues: list[CommonIssue] = Field(description="At most 5 themes, ordered by count, highest first.")
    observations: list[str] = Field(description="Factual trends visible in the data. Empty list if data is limited.")
    recommendations: list[str] = Field(description="Actionable steps, e.g. add a validation rule or clarify a field label.")

class ReportStats(BaseModel):
    total: int
    status_counts: dict[str, int]
    approval_rate: float            
    rejection_rate: float
    pending_backlog: int            
    avg_review_hours: Optional[float]
    by_category: dict[str, int]
    top_rejection_reasons: list[tuple[str, int]]
    top_mismatched_fields: list[tuple[str, int]]
    top_document_issues: list[tuple[str, int]]
    duplicate_flagged: int
    low_confidence_count: int

class Report(BaseModel):
    form_title: str
    period: str
    stats: ReportStats
    narrative: NarrativeReport

def _hours(start: Optional[str], end: Optional[str]) -> Optional[float]:
    try:
        return (datetime.fromisoformat(end) - datetime.fromisoformat(start)).total_seconds() / 3600
    except (TypeError, ValueError):
        return None

def compute_stats(submissions: list[dict]) -> ReportStats:
    """
    Each submission: {
      "id", "status", "category", "assigned_to", "created_at", "reviewed_at",
      "rejection_reason", "duplicate_flag": bool,
      "verification": {"mismatched": [{"field":..}], "issues": [..], "confidence": float}
    }
    """
    status = Counter(s.get("status", "Unknown") for s in submissions)
    approved, rejected = status.get("Approved", 0), status.get("Rejected", 0)
    decided = approved + rejected

    times = [h for s in submissions
             if s.get("status") in ("Approved", "Rejected")
             and (h := _hours(s.get("created_at"), s.get("reviewed_at"))) is not None]

    reasons = Counter((s.get("rejection_reason") or "").strip().lower()
                      for s in submissions if s.get("status") == "Rejected" and s.get("rejection_reason"))
    mismatched, doc_issues = Counter(), Counter()
    low_conf = 0
    for s in submissions:
        v = s.get("verification") or {}
        for m in v.get("mismatched", []):
            mismatched[m.get("field", "unknown")] += 1
        for i in v.get("issues", []):
            doc_issues[i.strip().lower()] += 1
        if v and v.get("confidence", 1) < 0.6:
            low_conf += 1

    return ReportStats(
        total=len(submissions),
        status_counts=dict(status),
        approval_rate=round(approved / decided, 3) if decided else 0.0,
        rejection_rate=round(rejected / decided, 3) if decided else 0.0,
        pending_backlog=status.get("Pending", 0) + status.get("Under Review", 0),
        avg_review_hours=round(sum(times) / len(times), 1) if times else None,
        by_category=dict(Counter(s.get("category") or "Uncategorised" for s in submissions)),
        top_rejection_reasons=reasons.most_common(5),
        top_mismatched_fields=mismatched.most_common(5),
        top_document_issues=doc_issues.most_common(5),
        duplicate_flagged=sum(1 for s in submissions if s.get("duplicate_flag")),
        low_confidence_count=low_conf,
    )

# -------------------- NARRATIVE (LLM) --------------------

SYSTEM_PROMPT = """You are an analyst writing a report for administrators of a form
submission and approval system.

RULES:
- Use ONLY the statistics provided. Never invent or recompute numbers.
- Group similar rejection reasons and document issues into clear themes
  and give each theme a count taken from the data.
- Be factual and concise. If data is limited, say so instead of speculating.
- Recommendations must be actionable (e.g. clarify a form field, add a
  validation rule, rebalance reviewer workload).
- Do not mention or infer any applicant's personal details."""

def _fallback(stats: ReportStats) -> NarrativeReport:
    """Used if the LLM call fails, so reports are never blocked."""
    return NarrativeReport(
        executive_summary=(f"{stats.total} submissions received. Approval rate "
                           f"{stats.approval_rate:.0%}, rejection rate {stats.rejection_rate:.0%}, "
                           f"{stats.pending_backlog} awaiting review."),
        common_issues=[CommonIssue(theme=r, count=c, description="Rejection reason")
                       for r, c in stats.top_rejection_reasons[:2]],
        observations=[], recommendations=[])

def generate_narrative(stats: ReportStats, form_title: str) -> NarrativeReport:
    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT),
        ("human", "Form: {title}\n\nSTATISTICS:\n{stats}\n\n"
                  "Write the report narrative.")])
    chain = prompt | _llm().with_structured_output(NarrativeReport)
    try:
        return chain.invoke({"title": form_title, "stats": stats.model_dump_json(indent=2)})
    except Exception:
        return _fallback(stats)

# -------------------- PUBLIC API --------------------

def generate_report(submissions: list[dict], form_title: str = "All forms",
                    period: str = "All time") -> Report:
    if not submissions:
        raise ValueError("No submissions available to generate a report.")
    stats = compute_stats(submissions)
    return Report(form_title=form_title, period=period, stats=stats,
                  narrative=generate_narrative(stats, form_title))


if __name__ == "__main__":
    sample = [
        {"id": "1", "status": "Approved", "category": "General", "assigned_to": "rev1",
         "created_at": "2026-11-20T10:00:00", "reviewed_at": "2026-11-20T14:00:00",
         "verification": {"mismatched": [], "issues": [], "confidence": 0.95}},
        {"id": "2", "status": "Rejected", "category": "OBC", "assigned_to": "rev1",
         "created_at": "2026-11-20T11:00:00", "reviewed_at": "2026-11-21T11:00:00",
         "rejection_reason": "Name mismatch with document",
         "verification": {"mismatched": [{"field": "name"}], "issues": ["blurry scan"], "confidence": 0.4}},
        {"id": "3", "status": "Pending", "category": "General", "assigned_to": "rev2",
         "created_at": "2026-11-22T09:00:00", "duplicate_flag": True},
    ]
    print((generate_report(sample, "Admission Form", "Nov 2026")))