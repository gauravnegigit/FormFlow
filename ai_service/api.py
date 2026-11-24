from fastapi import FastAPI, UploadFile, File, Form, HTTPException, Header
from pydantic import BaseModel
import json, tempfile, os, shutil
from form_generator import generate_form, form_to_json
from document_processor import extract_text
from document_verifier import verify_document
from duplicate_detector import find_duplicates, file_hash
from workflow_router import route_submission, RoutingRule

app = FastAPI(title="FormFlow AI Service")
API_KEY = os.getenv("AI_SERVICE_KEY", "change-me")

def auth(key: str | None):
    if key != API_KEY:
        raise HTTPException(401, "Invalid API key")

class FormReq(BaseModel):
    prompt: str 

@app.post("/ai/generate-form")
def gen_form(req: FormReq, x_api_key: str = Header(None)):
    auth(x_api_key)
    return form_to_json(generate_form(req.prompt))

@app.post("/ai/verify-document")
async def verify(file: UploadFile = File(...),
                 form_data: str = Form(...),          # JSON string
                 document_type: str = Form(...),
                 expected_fields: str = Form(...),    # JSON list
                 x_api_key: str = Header(None)):
    auth(x_api_key)
    suffix = os.path.splitext(file.filename)[1]
    with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as tmp:
        shutil.copyfileobj(file.file, tmp)
        path = tmp.name

    try:
        text = extract_text(path)
        result = verify_document(json.loads(form_data) , text , document_type , json.loads(expected_fields))
        return {**result.model_dump(),  "file_hash" : file_hash(path)}
    finally:
        os.remove(path)
