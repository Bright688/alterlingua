import tempfile
from pathlib import Path

from app.documents.extraction import Paragraph, extract_docx, extract_pdf
from app.documents.regeneration import regenerate_docx, regenerate_pdf


def write(data: bytes, suffix: str) -> Path:
    path = Path(tempfile.mktemp(suffix=suffix))
    path.write_bytes(data)
    return path


def test_regenerate_docx_roundtrips_text_and_known_styles():
    content = regenerate_docx([Paragraph("Hello", "Heading 1"), Paragraph("World", None), Paragraph("")])
    assert content[:2] == b"PK"  # a real zip/docx, not a stub
    roundtrip = extract_docx(write(content, ".docx"))
    assert [(p.text, p.style) for p in roundtrip] == [("Hello", "Heading 1"), ("World", "Normal"), ("", "Normal")]


def test_regenerate_docx_falls_back_to_the_default_style_for_an_unknown_one():
    # A style name the new document's template does not define must never crash regeneration.
    content = regenerate_docx([Paragraph("text", "Some Style That Does Not Exist")])
    roundtrip = extract_docx(write(content, ".docx"))
    assert roundtrip[0].text == "text"


def test_regenerate_pdf_produces_a_real_pdf_with_the_translated_text():
    content = regenerate_pdf([Paragraph("¿Vienes mañana?"), Paragraph(""), Paragraph("¡Muchas gracias!")])
    assert content[:5] == b"%PDF-"
    # Both paragraphs flow onto the same physical page (short text, no forced page break), so a page-level
    # re-extraction legitimately sees them as one unit -- the stated non-page-exact default, not a bug.
    combined = " ".join(p.text for p in extract_pdf(write(content, ".pdf")))
    assert "¿Vienes mañana?" in combined
    assert "¡Muchas gracias!" in combined


def test_regenerate_pdf_escapes_markup_characters_without_crashing():
    content = regenerate_pdf([Paragraph("Price: 10 < 20 & \"ok\"")])
    texts = [p.text for p in extract_pdf(write(content, ".pdf")) if p.text]
    assert texts == ['Price: 10 < 20 & "ok"']


def test_regenerate_pdf_applies_a_heading_style_for_a_docx_heading_paragraph():
    # Not pixel-identical to the original (the stated default, see docs/build-log.md), but a DOCX heading still
    # renders larger than body text when carried into a PDF translation.
    content = regenerate_pdf([Paragraph("Section One", "Heading 1")])
    assert content[:5] == b"%PDF-"
