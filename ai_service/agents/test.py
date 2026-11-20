from dotenv import load_dotenv
from pathlib import Path

script_dir = Path(__file__).resolve().parent.parent
project_root = script_dir.parents[0]
print(project_root)
load_dotenv(project_root / ".env")