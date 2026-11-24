import os 
from pathlib import Path
from typing import Any
import fitz
import pytesseract
from PIL import Image
from docx import Document
from pydantic import BaseModel, Field

SUPPORTED_FORMATS = {
    ".pdf", ".png", ".jpg", ".jpeg",
    ".tiff", ".bmp", ".docx", ".txt"
}

def extract_text(file_path: str) -> str:
    """Extract text from supported documents."""

    path = Path(file_path)

    if not path.is_file():
        raise FileNotFoundError(f"Document not found: {file_path}")

    if path.suffix.lower() not in SUPPORTED_FORMATS:
        raise ValueError(f"Unsupported format: {path.suffix}")

    extension = path.suffix.lower()

    if extension == ".pdf":
        return _extract_pdf(path)

    if extension in {".png", ".jpg", ".jpeg", ".tiff", ".bmp"}:
        return _extract_image(path)

    if extension == ".docx":
        return _extract_docx(path)

    if extension == ".txt":
        return path.read_text(encoding="utf-8")

    return ""

def _extract_pdf(path: Path) -> str:
    pages = []
    with fitz.open(path) as pdf:
        for page in pdf:
            text = page.get_text().strip()
            if not text:  # scanned page -> OCR
                pix = page.get_pixmap(dpi=300)
                img = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
                text = pytesseract.image_to_string(img)
            pages.append(text)
    return "\n\n".join(pages)

def _extract_image(path: Path) -> str:
    image = Image.open(path)
    return pytesseract.image_to_string(image)

def _extract_docx(path: Path) -> str:
    document = Document(path)

    paragraphs = [
        paragraph.text
        for paragraph in document.paragraphs
        if paragraph.text.strip()
    ]

    tables = [
        " | ".join(cell.text for cell in row.cells)
        for table in document.tables
        for row in table.rows
    ]

    return "\n".join(paragraphs + tables)