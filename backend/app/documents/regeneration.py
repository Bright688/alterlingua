"""Writes a translated paragraph list back out as a real PDF or DOCX file.

Known, stated default (see docs/build-log.md): output preserves paragraph order, not the original's pixel-exact
layout (page breaks, fonts, images, tables). This matches how mainstream document translators work; true
layout-preserving translation of arbitrary PDFs is a much larger, open-ended problem than it sounds.
"""

import io
from xml.sax.saxutils import escape

from docx import Document as DocxDocument
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import Paragraph as PdfParagraph
from reportlab.platypus import SimpleDocTemplate, Spacer

from app.documents.extraction import Paragraph

_PDF_STYLE_FOR_DOCX_STYLE = {
    "Title": "Title",
    "Subtitle": "Italic",
    "Heading 1": "Heading1",
    "Heading 2": "Heading2",
    "Heading 3": "Heading3",
}


def regenerate_docx(paragraphs: list[Paragraph]) -> bytes:
    document = DocxDocument()
    available_styles = {style.name for style in document.styles}
    for paragraph in paragraphs:
        style = paragraph.style if paragraph.style in available_styles else None
        document.add_paragraph(paragraph.text, style=style)
    buffer = io.BytesIO()
    document.save(buffer)
    return buffer.getvalue()


def regenerate_pdf(paragraphs: list[Paragraph]) -> bytes:
    buffer = io.BytesIO()
    doc = SimpleDocTemplate(buffer, pagesize=A4, leftMargin=2 * cm, rightMargin=2 * cm, topMargin=2 * cm, bottomMargin=2 * cm)
    sheet = getSampleStyleSheet()
    story: list[object] = []
    for paragraph in paragraphs:
        if not paragraph.text:
            story.append(Spacer(1, 0.4 * cm))
            continue
        style_name = _PDF_STYLE_FOR_DOCX_STYLE.get(paragraph.style or "", "BodyText")
        story.append(PdfParagraph(escape(paragraph.text), sheet[style_name]))
        story.append(Spacer(1, 0.2 * cm))
    doc.build(story)
    return buffer.getvalue()


def regenerate(paragraphs: list[Paragraph], fmt: str) -> bytes:
    if fmt == "docx":
        return regenerate_docx(paragraphs)
    return regenerate_pdf(paragraphs)
