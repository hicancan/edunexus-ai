from __future__ import annotations

import logging
from io import BytesIO
from pathlib import Path

from docx import Document as DocxDocument
from pypdf import PdfReader

logger = logging.getLogger("edunexus.ai.extraction")


def extract_text(file_path: str) -> str:
    path = Path(file_path)
    if not path.exists() or not path.is_file():
        return ""
    try:
        return extract_text_from_bytes(path.name, path.read_bytes())
    except Exception:
        return ""


def extract_text_from_bytes(filename: str, file_content: bytes) -> str:
    suffix = Path(filename).suffix.lower()
    if suffix == ".pdf":
        return extract_pdf_bytes(file_content)
    if suffix in {".docx", ".doc"}:
        return extract_docx_bytes(file_content)
    return file_content.decode("utf-8", errors="ignore")


def extract_pdf(path: Path) -> str:
    try:
        return extract_pdf_bytes(path.read_bytes())
    except Exception as ex:
        logger.error("Failed to extract pdf %s: %s", path, ex, exc_info=True)
        return ""


def extract_pdf_bytes(file_content: bytes) -> str:
    try:
        reader = PdfReader(BytesIO(file_content))
        return "\n".join((page.extract_text() or "") for page in reader.pages)
    except Exception as ex:
        logger.error("Failed to extract pdf bytes: %s", ex, exc_info=True)
        return ""


def extract_docx(path: Path) -> str:
    try:
        return extract_docx_bytes(path.read_bytes())
    except Exception as ex:
        logger.error("Failed to extract docx %s: %s", path, ex, exc_info=True)
        return ""


def extract_docx_bytes(file_content: bytes) -> str:
    try:
        document = DocxDocument(BytesIO(file_content))
        return "\n".join(row.text for row in document.paragraphs if row.text)
    except Exception as ex:
        logger.error("Failed to extract docx bytes: %s", ex, exc_info=True)
        return ""
