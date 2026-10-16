import tempfile
from pathlib import Path

import pytest

from app.core.errors import InvalidDocumentError
from app.documents.extraction import extract_docx, extract_pdf
from tests.document_helpers import make_blank_pdf, make_docx, make_pdf


def write(data: bytes, suffix: str) -> Path:
    path = Path(tempfile.mktemp(suffix=suffix))
    path.write_bytes(data)
    return path


def test_pdf_extracts_one_unit_per_page_in_order():
    path = write(make_pdf(["Are you coming tomorrow?", "Thank you very much!"]), ".pdf")
    texts = [p.text for p in extract_pdf(path) if p.text]
    assert texts == ["Are you coming tomorrow?", "Thank you very much!"]


def test_pdf_keeps_a_blank_unit_between_pages_for_spacing():
    paragraphs = extract_pdf(write(make_pdf(["One", "Two"]), ".pdf"))
    assert paragraphs[1].text == ""  # the page break between "One" and "Two"


def test_pdf_with_no_extractable_text_is_refused():
    with pytest.raises(InvalidDocumentError):
        extract_pdf(write(make_blank_pdf(), ".pdf"))


def test_pdf_wrapped_lines_within_a_page_are_joined_with_a_space():
    long_text = "word " * 200  # long enough to wrap across several lines on an A4 page
    paragraphs = extract_pdf(write(make_pdf([long_text.strip()]), ".pdf"))
    assert "\n" not in paragraphs[0].text
    assert paragraphs[0].text.count("word") == 200


def test_docx_extracts_each_paragraph_with_its_style():
    path = write(
        make_docx([("Title text", "Title"), ("Are you coming tomorrow?", None), ("", None), ("Heading text", "Heading 1")]),
        ".docx",
    )
    paragraphs = extract_docx(path)
    assert [(p.text, p.style) for p in paragraphs] == [
        ("Title text", "Title"),
        ("Are you coming tomorrow?", "Normal"),
        ("", "Normal"),
        ("Heading text", "Heading 1"),
    ]


def test_docx_unknown_styles_collapse_to_none_rather_than_being_guessed_at():
    path = write(make_docx([("a bullet point", "List Bullet")]), ".docx")
    assert extract_docx(path)[0].style is None


def test_docx_with_only_blank_paragraphs_is_refused():
    with pytest.raises(InvalidDocumentError):
        extract_docx(write(make_docx([("", None), ("", None)]), ".docx"))
