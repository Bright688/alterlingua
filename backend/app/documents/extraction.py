"""Pulls translatable text out of a PDF or DOCX file as an ordered list of translation units ("paragraphs" below,
though for a PDF each unit is really a whole page -- see extract_pdf).

Known, stated limitation: only body text is read. Tables, headers/footers, footnotes, text inside images and PDF
form fields are not extracted and so are not translated -- regenerating those faithfully needs per-format work
this first version does not attempt, and silently guessing at their structure would be worse than leaving them out
and saying so (see docs/privacy.md and docs/build-log.md).
"""

from dataclasses import dataclass
from pathlib import Path

from docx import Document as DocxDocument
from pypdf import PdfReader

from app.core.errors import InvalidDocumentError

# A style name is kept only when regeneration can plausibly reuse it (see regeneration.py); anything else collapses
# to the default body style rather than guessing.
_KNOWN_DOCX_STYLES = frozenset(
    {"Normal", "Title", "Subtitle", "Quote", "Intense Quote"}
    | {f"Heading {n}" for n in range(1, 10)}
)


@dataclass(frozen=True)
class Paragraph:
    text: str  # "" for a blank line kept only to preserve spacing; never sent for translation
    style: str | None = None  # a DOCX paragraph style name, when it is one regeneration can reapply


def extract_docx(path: Path) -> list[Paragraph]:
    document = DocxDocument(path)
    paragraphs: list[Paragraph] = []
    for para in document.paragraphs:
        text = para.text.strip()
        style_name = para.style.name if para.style is not None else None
        paragraphs.append(Paragraph(text=text, style=style_name if style_name in _KNOWN_DOCX_STYLES else None))
    if not any(p.text for p in paragraphs):
        raise InvalidDocumentError("No translatable text was found in the document.")
    return paragraphs


def extract_pdf(path: Path) -> list[Paragraph]:
    """One translation unit per PAGE, not per paragraph.

    A PDF has no paragraph markers at all -- it is positioned text runs -- and a real extractor (pypdf included)
    separates lines within the same paragraph the same way it separates genuinely distinct paragraphs: a single
    "\\n", with no reliable blank-line gap to split on (checked against real reportlab- and, informally,
    Word-exported PDFs). Splitting on blank lines therefore silently merges unrelated paragraphs together more
    often than it correctly separates them, which is worse than the coarser but honest page-level unit used here.
    """
    reader = PdfReader(path)
    if reader.is_encrypted:
        raise InvalidDocumentError("The PDF is password-protected and cannot be read.")
    paragraphs: list[Paragraph] = []
    for page in reader.pages:
        page_text = page.extract_text() or ""
        lines = [line.strip() for line in page_text.splitlines() if line.strip()]
        paragraphs.append(Paragraph(text=" ".join(lines)))
        paragraphs.append(Paragraph(text=""))  # a page break, kept as spacing
    if not any(p.text for p in paragraphs):
        raise InvalidDocumentError("No translatable text was found in the document. Scanned/image-only PDFs are not supported.")
    return paragraphs


def extract(path: Path, fmt: str) -> list[Paragraph]:
    if fmt == "docx":
        return extract_docx(path)
    return extract_pdf(path)
