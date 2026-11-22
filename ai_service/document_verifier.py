import os, re
from datetime import datetime
from difflib import SequenceMatcher
from pathlib import Path
from typing import Optional
from dotenv import load_dotenv
from pydantic import BaseModel, Field
from langchain_groq import ChatGroq
from langchain_core.prompts import ChatPromptTemplate

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

class ExtractedField(BaseModel):
    field: str
    value: Optional[str] = None  # null if not found

class ExtractionResult(BaseModel):
    document_type: str
    fields: list[ExtractedField]
    is_readable: bool = True
    issues: list[str] = Field(default_factory=list)  # expired, unclear, etc.

class Mismatch(BaseModel):
    field: str
    form_value: str
    document_value: str

class VerificationResult(BaseModel):
    matched: list[str]
    mismatched: list[Mismatch]
    not_in_document: list[str]   # in form, not found in doc
    not_in_form: dict[str, str]  # in doc, not in form
    not_applicable: list[str]    # form fields this doc type can't contain
    issues: list[str]
    confidence: float
    summary: str

SYSTEM_PROMPT = """You extract data from document text for verification.
Rules:
- Extract ONLY values explicitly present in the text. Never guess.
- Return null for any requested field that is not present.
- Use snake_case field names; use the requested names where applicable.
- Also extract other key identifiers you see (e.g. father_name, issue_date).
- Report issues: expired document, illegible text, wrong document type,
  signs of tampering or missing sections."""

def _llm():
    return ChatGroq(model=os.getenv("GROQ_MODEL", "openai/gpt-oss-120b"), temperature=0)

def extract_fields(document_text: str, expected_document_type: str , requested_fields: list[str]) -> ExtractionResult:
    prompt = ChatPromptTemplate.from_messages([
        ("system", SYSTEM_PROMPT),
        ("human", "Expected document type: {doc_type}\nRequested fields: {fields}\n\nDOCUMENT TEXT:\n{text}")])
    
    chain = prompt | _llm().with_structured_output(ExtractionResult)
    return chain.invoke({"doc_type": expected_document_type,
                    "fields": ", ".join(requested_fields),
                    "text": document_text[:12000]})

# ---------- comparison helpers ----------
_DATE_FORMATS = ["%d/%m/%Y", "%d-%m-%Y", "%Y-%m-%d", "%d %b %Y", "%d %B %Y", "%d.%m.%Y", "%m/%d/%Y"]

def _norm(v: str) -> str:
    v = re.sub(r"[^\w\s]", " ", v.lower())
    return " ".join(sorted(v.split()))  # order-insensitive: "Rahul Kumar" == "KUMAR RAHUL"

def _to_date(v: str):
    for f in _DATE_FORMATS:
        try:
            return datetime.strptime(v.strip(), f).date()
        except ValueError:
            pass
    return None

def _similarity(a: str, b: str) -> float:
    da, db = _to_date(a), _to_date(b)
    if da and db:
        return 1.0 if da == db else 0.0
    na, nb = re.sub(r"\s", "", _norm(a)), re.sub(r"\s", "", _norm(b))
    if na.isdigit() or nb.isdigit() or any(c.isdigit() for c in na):  # IDs/numbers: exact
        return 1.0 if na == nb else 0.0
    return SequenceMatcher(None, _norm(a), _norm(b)).ratio()

def verify_document(form_data: dict[str, str], document_text: str,document_type: str, expected_fields: list[str],threshold: float = 0.85) -> VerificationResult:

    ext = extract_fields(document_text, document_type, expected_fields)
    doc = {f.field: f.value for f in ext.fields if f.value}

    matched, mismatched, not_in_doc, n_a = [], [], [], []
    for field, form_val in form_data.items():
        if not form_val or not str(form_val).strip():
            continue
        if field not in expected_fields:
            n_a.append(field)
            continue
        if field not in doc:
            not_in_doc.append(field)
            continue
        score = _similarity(str(form_val), doc[field])
        if score >= threshold:
            matched.append(field)
        else:
            mismatched.append(Mismatch(field=field, form_value=str(form_val),document_value=doc[field]))

    not_in_form = {k: v for k, v in doc.items()
                   if k not in form_data or not str(form_data.get(k, "")).strip()}

    compared = len(matched) + len(mismatched)
    confidence = len(matched) / compared if compared else 0.0
    if not ext.is_readable:
        confidence *= 0.65
    confidence = round(max(0.0, confidence - 0.1 * len(not_in_doc) - 0.15 * len(ext.issues)), 2)

    summary = (f"{len(matched)} matched, {len(mismatched)} mismatched, "
               f"{len(not_in_doc)} not found in document, {len(not_in_form)} extra in document."
               + (f" Issues: {'; '.join(ext.issues)}." if ext.issues else ""))

    return VerificationResult(matched=matched, mismatched=mismatched , not_in_document=not_in_doc, not_in_form=not_in_form , not_applicable=n_a, issues=ext.issues , confidence=confidence, summary=summary)