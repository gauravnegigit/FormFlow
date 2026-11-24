from document_processor import extract_text
from document_verifier import verify_document
from duplicate_detector import find_duplicates, file_hash
from workflow_router import route_submission , RoutingRule, Condition
import json

text = extract_text("jee_result.pdf")

form_data = {
    "name": "GAURAV NEGI",
    "dob": "30/01/2006",
    "application_number": "250310151216",
    "category": "General",
    "percentile": "99.23",
    "phone": "8826459201",          # not on the scorecard -> not_applicable
}

result = verify_document(
    form_data=form_data,
    document_text=text,
    document_type="JEE Main Scorecard",
    expected_fields=["name", "dob", "application_number", "category", "percentile",
                     "mother_name", "father_name"],
)

result1 = result.model_dump_json(indent=2)

with open("verify.json", "w" , encoding = "utf-8") as file :
    json.dump(result1 , file , indent = 2)

new = {"id": "s2", "data": form_data, "file_hashes": [file_hash("jee_result.pdf")]}
old = [{"id": "s1", "data": form_data, "file_hashes": [file_hash("jee_result.pdf")]}]
dups = find_duplicates(new, old, identifier_fields=["application_number"], fuzzy_fields=["name"])
print(dups)

rules = [RoutingRule(name="Top rank", assign_to="senior_reviewer", priority="high",conditions=[Condition(field="percentile", operator="gt", value=99)])]
print(route_submission(form_data, rules, "reviewer_1",verification=result.model_dump(), duplicates=dups,escalation_assignee="admin"))