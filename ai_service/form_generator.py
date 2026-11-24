import os
import re
import json 
from typing import Any, Literal , Optional
from pydantic import BaseModel, Field , ConfigDict, field_validator
from langchain_core.prompts import ChatPromptTemplate
from pathlib import Path 
from client import _llm

# -------------------- DYNAMIC FORM SCHEMAS --------------------

class FieldOption(BaseModel):
    label: str
    value: str

class ValidationRule(BaseModel):
    pattern: Optional[str] = None
    allowed_file_types: list[str] = Field(default_factory=list)
    max_file_size_mb: Optional[int] = None

class ConditionalRule(BaseModel):
    depends_on: str
    operator: Literal[
        "equals", "not_equals", "contains",
        "not_empty", "empty"
    ]
    value: Any = None

class FormField(BaseModel):
    id: str
    label: str
    type: Literal[
        "text", "textarea", "number", "email",
        "phone", "date", "datetime", "url",
        "checkbox", "select", "multiselect","file"
    ]
    required: bool = False
    options: list[FieldOption] = Field(default_factory=list)
    validations: list[ValidationRule] = Field(default_factory=list)
    conditions: list[ConditionalRule] = Field(default_factory=list)

class FormSection(BaseModel):
    id: str
    title: str
    description: str | None = None
    fields: list[FormField]

class FormDefinition(BaseModel):
    title: str
    description: str
    sections: list[FormSection]
    settings: dict[str, Any] = Field(default_factory=dict)
    version: str = "1.0"

    @field_validator("sections")
    @classmethod
    def validate_sections(cls, sections):
        section_ids = [s.id for s in sections]

        if len(section_ids) != len(set(section_ids)):
            raise ValueError("Duplicate section IDs found")

        field_ids = [
            field.id
            for section in sections
            for field in section.fields
        ]

        if len(field_ids) != len(set(field_ids)):
            raise ValueError("Duplicate field IDs found")

        return sections

class FormGenerationRequest(BaseModel):
    prompt: str
    organization_context: str | None = None
    locale: str = "en"


# -------------------- FORM GENERATOR --------------------

SYSTEM_PROMPT = """
You are an expert dynamic form designer for FormFlow.

Your task is to generate organization-independent form definitions.

IMPORTANT RULES:

1. Generate forms based entirely on the user's requirements.
2. Never assume a particular industry or organization.
3. Support any organization, including businesses, educational
   institutions, government bodies and non-profits.
4. Divide forms into logical sections.
5. Generate unique snake_case IDs for every section and field.
6. Select appropriate field types.
7. Add validation rules wherever necessary.
8. Add conditional visibility rules when required.
9. Select and multiselect fields must contain appropriate options.
10. File fields should specify relevant file requirements in metadata
    when provided by the user.
11. Do not invent mandatory fields unrelated to the requirements.
12. Use metadata for organization-specific extensions.
13. Keep labels and descriptions understandable to end users.
14. Do not include actual user data or fabricated organization policies.
15. Return a complete structured form definition.

The generated form is a DRAFT and must be reviewed before publication.
"""

# def generate_form(request: FormGenerationRequest) -> FormDefinition:
#     if not request.strip():
#         raise ValueError("Form generation prompt cannot be empty")

#     llm = get_llm()

#     structured_llm = llm.with_structured_output(FormDefinition)

#     user_prompt = f"""
#     FORM REQUIREMENTS:
#     {request.prompt}

#     ORGANIZATION CONTEXT:
#     {request.organization_context or "Not provided"}

#     LANGUAGE:
#     {request.locale}

#     Generate the complete dynamic form schema.
#     """

#     result = structured_llm.invoke([
#         ("system", SYSTEM_PROMPT),
#         ("human", user_prompt)
#     ])

#     return result

def generate_form(requirements: str) -> FormDefinition:
    if not requirements.strip():
        raise ValueError("Form requirements cannot be empty.")

    llm = _llm()
    form_llm = llm.with_structured_output(FormDefinition)
    prompt = ChatPromptTemplate.from_messages([("system" , SYSTEM_PROMPT) , ("human" , "Create a form based on these requirements:\n{requirements}")])

    chain = prompt | form_llm 
    result = chain.invoke({"requirements" : requirements})

    return result

def form_to_json(form: FormDefinition) -> dict:
    return form.model_dump(mode="json")

if __name__ == "__main__":
    req = input("Enter the requirements for the form : ")
    form = generate_form(req)
    print(form)
    res = form_to_json(form)
    with open("data.json", "w" , encoding = "utf-8") as file :
        json.dump(res , file , indent = 4)
    print(res)