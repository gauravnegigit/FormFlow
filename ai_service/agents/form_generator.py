import os
from typing import Optional, Literal
from pydantic import BaseModel, Field
from langchain_groq import ChatGroq
from langchain_core.prompts import ChatPromptTemplate
from pathlib import Path
from dotenv import load_dotenv

script_dir = Path(__file__).resolve().parent.parent
project_root = script_dir.parents[0]
load_dotenv(project_root / ".env")

# ---------- Schemas ----------
FieldType = Literal[
    "TEXT", "NUMBER", "DROPDOWN",
    "CHECKBOX", "DATE", "FILE"
]

class FieldValidation(BaseModel):
    min_length: Optional[int] = None
    max_length: Optional[int] = None
    min_value: Optional[float] = None
    max_value: Optional[float] = None
    pattern: Optional[str] = None
    allowed_file_types: list[str] = Field(default_factory=list)
    max_file_size_mb: Optional[int] = None

class FormField(BaseModel):
    field_id: str
    label: str
    type: FieldType
    required: bool = False
    placeholder: Optional[str] = None
    options: list[str] = Field(default_factory=list)
    validation: FieldValidation = Field(default_factory=FieldValidation)
    condition: Optional[str] = None


class FormSection(BaseModel):
    title: str
    description: Optional[str] = None
    fields: list[FormField]

class GeneratedForm(BaseModel):
    title: str
    description: str
    sections: list[FormSection]


# ---------- LLM ----------

llm = ChatGroq(
    model=os.getenv("GROQ_MODEL", "openai/gpt-oss-120b"),
    temperature=0
)

form_llm = llm.with_structured_output(GeneratedForm)

prompt = ChatPromptTemplate.from_messages([
    ("system", """
    You are an expert form designer for FormFlow.

    Convert the user's instructions into a structured digital form.

    Rules:
    1. Use only TEXT, NUMBER, DROPDOWN, CHECKBOX, DATE and FILE.
    2. Assign every field a unique snake_case field_id.
    3. Mark mandatory fields as required.
    4. Add suitable validation rules.
    5. Provide dropdown options wherever needed.
    6. Use conditions for dependent fields.
    7. Organize related fields into logical sections.
    8. Avoid unnecessary fields.
    9. Do not invent organizational policies.
    10. Return a draft form, not a published form.
    """),
    ("human", "Create a form based on these requirements:\n{requirements}")
])


def generate_form(requirements: str) -> dict:
    if not requirements.strip():
        raise ValueError("Form requirements cannot be empty.")

    chain = prompt | form_llm
    result = chain.invoke({"requirements": requirements})

    return {
        "success": True,
        "status": "DRAFT",
        "form": result.model_dump()
    }

if __name__ == "__main__":
    req = input("Enter your requirements for the form : ")
    res = generate_form(req)
    print(res["form"])