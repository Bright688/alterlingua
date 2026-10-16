import asyncio
import tempfile
from pathlib import Path

import pytest

from app.core.errors import DocumentTooComplexError, SameLanguageError
from app.documents.extraction import extract_docx, extract_pdf
from app.documents.schemas import DocumentTranslateOptions
from app.documents.service import DocumentTranslationService
from app.documents.upload import DOCX, PDF, TemporaryDocument
from app.translation.fake_provider import FakeTranslationProvider
from app.translation.service import TranslationService
from tests.conftest import SpyProvider
from tests.document_helpers import DOCX_CONTENT_TYPE, PDF_CONTENT_TYPE, make_docx, make_pdf


def write(data: bytes, suffix: str) -> Path:
    path = Path(tempfile.mktemp(suffix=suffix))
    path.write_bytes(data)
    return path


def temp_pdf(pages: list[str]) -> TemporaryDocument:
    data = make_pdf(pages)
    return TemporaryDocument(path=write(data, ".pdf"), content_type=PDF_CONTENT_TYPE, format=PDF, size=len(data))


def temp_docx(paragraphs: list[tuple[str, str | None]]) -> TemporaryDocument:
    data = make_docx(paragraphs)
    return TemporaryDocument(path=write(data, ".docx"), content_type=DOCX_CONTENT_TYPE, format=DOCX, size=len(data))


def make_service(provider=None, **kw) -> DocumentTranslationService:
    translation = TranslationService(provider or FakeTranslationProvider(), max_text_chars=5000, timeout_seconds=15)
    return DocumentTranslationService(translation, **kw)


def run(coro):
    return asyncio.run(coro)


def test_pdf_round_trip_with_auto_detected_source():
    service = make_service()
    document = temp_pdf(["Are you coming tomorrow?"])
    options = DocumentTranslateOptions(target="es", source="auto")
    target, explicit_source = service.check_request(options)
    result = run(service.translate_document(document, options, target, explicit_source))

    assert result.content_type == PDF_CONTENT_TYPE
    assert result.source_language == "en"
    assert result.target_language == "es"
    assert result.paragraph_count == 1
    translated = [p.text for p in extract_pdf(write(result.content, ".pdf")) if p.text]
    assert translated == ["¿Vienes mañana?"]


def test_docx_round_trip_preserves_paragraph_style():
    service = make_service()
    document = temp_docx([("Thank you very much!", "Heading 1")])
    options = DocumentTranslateOptions(target="es", source="en")
    target, explicit_source = service.check_request(options)
    result = run(service.translate_document(document, options, target, explicit_source))

    assert result.content_type == DOCX_CONTENT_TYPE
    assert result.source_language == "en"
    translated = extract_docx(write(result.content, ".docx"))
    assert (translated[0].text, translated[0].style) == ("¡Muchas gracias!", "Heading 1")


def test_explicit_source_is_used_for_every_paragraph_without_detection():
    service = make_service()
    document = temp_docx([("something not in the phrasebook", None), ("another original sentence", None)])
    options = DocumentTranslateOptions(target="es", source="en")
    target, explicit_source = service.check_request(options)
    result = run(service.translate_document(document, options, target, explicit_source))
    assert result.source_language == "en"
    translated = [p.text for p in extract_docx(write(result.content, ".docx"))]
    assert translated == ["[es] something not in the phrasebook", "[es] another original sentence"]


def test_a_document_with_too_many_translatable_units_is_refused_before_any_provider_call():
    spy = SpyProvider()
    service = make_service(spy, max_paragraphs=1)
    document = temp_docx([("first", None), ("second", None)])
    options = DocumentTranslateOptions(target="es", source="en")
    target, explicit_source = service.check_request(options)
    with pytest.raises(DocumentTooComplexError):
        run(service.translate_document(document, options, target, explicit_source))
    assert spy.calls == []


def test_source_equal_to_target_is_refused():
    service = make_service()
    document = temp_docx([("hello", None)])
    options = DocumentTranslateOptions(target="en", source="en")
    target, explicit_source = service.check_request(options)
    with pytest.raises(SameLanguageError):
        run(service.translate_document(document, options, target, explicit_source))


def test_the_first_paragraph_used_for_detection_is_only_sent_to_the_provider_once():
    spy = SpyProvider()
    service = make_service(spy)
    document = temp_docx([("Are you coming tomorrow?", None)])
    options = DocumentTranslateOptions(target="es", source="auto")
    target, explicit_source = service.check_request(options)
    run(service.translate_document(document, options, target, explicit_source))
    assert len(spy.calls) == 1
