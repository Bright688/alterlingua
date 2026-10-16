"""Receiving a document upload safely: check what it is, limit its size, keep it only briefly, always delete it.

Mirrors app/speech/audio.py's temporary_audio. PRIVACY (CLAUDE.md sections 22 and 25): the document exists only in a
private temporary file (readable by this process only) while it is processed. It is deleted in every case: success,
error, timeout or crash.
"""

import os
import tempfile
import zipfile
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from app.core.errors import DocumentTooLargeError, InvalidDocumentError, UnsupportedDocumentTypeError

PDF = "pdf"
DOCX = "docx"

# DOCX is the modern, actively maintained Word format (a zip of XML parts) and is what "doc files" means here. The
# legacy binary .doc format is not supported: it needs a different, much heavier parser and is increasingly rare.
_DOCX_CONTENT_TYPES = frozenset({
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
})
_PDF_CONTENT_TYPES = frozenset({"application/pdf"})
ALLOWED_DOCUMENT_TYPES: frozenset[str] = _PDF_CONTENT_TYPES | _DOCX_CONTENT_TYPES

_SUFFIX = {PDF: ".pdf", DOCX: ".docx"}
_CHUNK = 64 * 1024


def base_content_type(declared: str | None) -> str:
    return (declared or "").split(";", 1)[0].strip().lower()


def check_declared_type(declared: str | None) -> str:
    content_type = base_content_type(declared)
    if content_type not in ALLOWED_DOCUMENT_TYPES:
        raise UnsupportedDocumentTypeError(
            "The file is not a supported document type.",
            received=content_type or None,
            supported=sorted(ALLOWED_DOCUMENT_TYPES),
        )
    return content_type


def sniff_format(path: Path, content_type: str) -> str:
    """Confirms the file's own bytes match what it claims to be, whatever the client declared."""
    with path.open("rb") as handle:
        head = handle.read(8)
    if content_type in _PDF_CONTENT_TYPES:
        if head[:5] == b"%PDF-":
            return PDF
        raise UnsupportedDocumentTypeError("The file's contents do not match a PDF.", received=content_type)
    # DOCX is a zip archive; a real one has word/document.xml inside, which also tells a .docx from a .zip,
    # a .xlsx or a .pptx that merely shares the zip container format.
    if head[:4] == b"PK\x03\x04":
        try:
            with zipfile.ZipFile(path) as archive:
                if "word/document.xml" in archive.namelist():
                    return DOCX
        except zipfile.BadZipFile:
            pass
    raise UnsupportedDocumentTypeError("The file's contents do not match a Word (.docx) document.", received=content_type)


class Upload(Protocol):
    async def read(self, size: int = ...) -> bytes: ...


@dataclass(frozen=True)
class TemporaryDocument:
    path: Path
    content_type: str
    format: str
    size: int


@asynccontextmanager
async def temporary_document(
    upload: Upload, declared_type: str | None, *, max_bytes: int, directory: str = ""
) -> AsyncIterator[TemporaryDocument]:
    """Writes the upload to a private temporary file, checking type and size, and deletes it when the block ends.

    Raises 415 for a wrong type (also when the bytes do not match the declared type), 413 for too large, 422 for empty.
    """
    content_type = check_declared_type(declared_type)
    fd, name = tempfile.mkstemp(prefix="alterlingua-", suffix=".doc", dir=directory or None)  # mode 0600
    path = Path(name)
    try:
        size = 0
        with os.fdopen(fd, "wb") as out:
            while True:
                chunk = await upload.read(_CHUNK)
                if not chunk:
                    break
                size += len(chunk)
                if size > max_bytes:
                    raise DocumentTooLargeError(f"The document is larger than {max_bytes} bytes.", max_bytes=max_bytes)
                out.write(chunk)
        if size == 0:
            raise InvalidDocumentError("The document is empty.")
        fmt = sniff_format(path, content_type)
        final = path.with_suffix(_SUFFIX[fmt])
        path.rename(final)
        path = final
        yield TemporaryDocument(path=path, content_type=content_type, format=fmt, size=size)
    finally:
        for candidate in {path, Path(name)}:
            candidate.unlink(missing_ok=True)
