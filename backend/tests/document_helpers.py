"""Small helpers for the document-translation tests: tiny valid PDF and DOCX files."""

import io

from docx import Document
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate

PDF_CONTENT_TYPE = "application/pdf"
DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"


def make_pdf(pages: list[str]) -> bytes:
    """One PDF page per string (extract_pdf reads one translation unit per page -- see its docstring)."""
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=A4)
    style = getSampleStyleSheet()["Normal"]
    story: list[object] = []
    for index, text in enumerate(pages or [""]):
        if index:
            story.append(PageBreak())
        story.append(Paragraph(text, style))
    doc.build(story)
    return buffer.getvalue()


def make_blank_pdf() -> bytes:
    """A real PDF with a page but no extractable text at all (stands in for a scanned, image-only PDF)."""
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=A4)
    doc.build([Paragraph("", getSampleStyleSheet()["Normal"])])
    return buffer.getvalue()


def make_docx(paragraphs: list[tuple[str, str | None]]) -> bytes:
    document = Document()
    for text, style in paragraphs:
        document.add_paragraph(text, style=style)
    buffer = io.BytesIO()
    document.save(buffer)
    return buffer.getvalue()
