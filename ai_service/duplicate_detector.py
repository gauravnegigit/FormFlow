import hashlib, re
from difflib import SequenceMatcher
from pydantic import BaseModel

class DuplicateMatch(BaseModel):
    submission_id: str
    score: float
    reasons: list[str]

def _clean(v) -> str:
    return re.sub(r"[^\w]", "", str(v).lower()) if v is not None else ""

def file_hash(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()

def find_duplicates(new: dict, existing: list[dict], identifier_fields: list[str],
                    fuzzy_fields: list[str] | None = None,
                    threshold: float = 0.8) -> list[DuplicateMatch]:
    """
    new / existing items: {"id": str, "data": {field: value}, "file_hashes": [str]}
    identifier_fields: exact-match keys (email, phone, aadhaar_number...) -> strong signal
    fuzzy_fields: text fields compared by similarity (name, address, ...)
    """
    fuzzy_fields = fuzzy_fields or []
    results = []

    for old in existing:
        if old["id"] == new.get("id"):
            continue
        score, reasons = 0.0, []

        for f in identifier_fields:  # exact identifier match
            a, b = _clean(new["data"].get(f)), _clean(old["data"].get(f))
            if a and a == b:
                score = max(score, 0.9)
                reasons.append(f"Same {f}")

        if set(new.get("file_hashes", [])) & set(old.get("file_hashes", [])):
            score = max(score, 0.95)
            reasons.append("Identical uploaded file")

        sims = []
        for f in fuzzy_fields:
            a, b = _clean(new["data"].get(f)), _clean(old["data"].get(f))
            if a and b:
                s = SequenceMatcher(None, a, b).ratio()
                sims.append(s)
                if s >= 0.9:
                    reasons.append(f"Very similar {f}")
        if sims:
            avg = sum(sims) / len(sims)
            if reasons:   # fuzzy similarity boosts an existing signal
                score = min(1.0, max(score, avg) + 0.05 * len(reasons))
            else:
                score = max(score, avg * 0.8)

        if score >= threshold:
            results.append(DuplicateMatch(submission_id=old["id"],
                                          score=round(score, 2), reasons=reasons or ["High overall similarity"]))

    return sorted(results, key=lambda m: m.score, reverse=True)