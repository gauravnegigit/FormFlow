import os 
from langchain_groq import ChatGroq
from dotenv import load_dotenv
from pathlib import Path 

load_dotenv(Path(__file__).resolve().parents[1] / ".env")

def _llm():
    return ChatGroq(model=os.getenv("GROQ_MODEL", "openai/gpt-oss-120b"), temperature=0)