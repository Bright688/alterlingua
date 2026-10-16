"""The document translation pipeline: a PDF or DOCX file in, extraction, translation of each paragraph, a
regenerated PDF or DOCX file out. Reuses the same translation service and language catalogue as POST /v1/translate
and POST /v1/audio/translate -- there is no document-specific translation logic here.

Known, stated limitation (see docs/build-log.md): one paragraph that fails to translate fails the whole document,
rather than returning a partially-translated file. A future round could add a partial-result mode, mirroring
POST /v1/audio/translate's allow_partial, if that trade-off turns out to matter in practice.
"""

import asyncio
import logging
import time
from dataclasses import dataclass

from app.core.errors import DocumentTooComplexError
from app.core.logging import kv
from app.documents.extraction import Paragraph, extract
from app.documents.regeneration import regenerate
from app.documents.upload import TemporaryDocument
from app.documents.schemas import DocumentTranslateOptions
from app.translation.languages import AUTO
from app.translation.schemas import TranslateRequest
from app.translation.service import TranslationService

logger = logging.getLogger(__name__)

# Translation units beyond this are refused before any provider call is made, so one huge document cannot run up
# an unbounded number of translation requests in a single uploaded-file request. A unit is a DOCX paragraph or a
# PDF page (see app/documents/extraction.py), so this effectively caps page count for PDFs.
DEFAULT_MAX_PARAGRAPHS = 300

# Translation requests for one document's paragraphs run at once, capped so a large document does not flood the
# translation provider.
_MAX_CONCURRENT_TRANSLATIONS = 5


@dataclass(frozen=True)
class DocumentTranslationResult:
    content: bytes
    content_type: str
    source_language: str
    target_language: str
    paragraph_count: int


class DocumentTranslationService:
    def __init__(self, translation: TranslationService, *, max_paragraphs: int = DEFAULT_MAX_PARAGRAPHS) -> None:
        self._translation = translation
        self._max_paragraphs = max_paragraphs

    def check_request(self, options: DocumentTranslateOptions) -> tuple[str, str | None]:
        """Validates languages and capabilities before any file is read. Returns (target, explicit source or None)."""
        target = self._translation.ensure_target_supported(options.target)
        if options.source == AUTO:
            return target, None
        source = self._translation.require_language(options.source, "source").code
        return target, source

    async def _translate_text(self, text: str, source: str | None, target: str, options: DocumentTranslateOptions) -> tuple[str, str]:
        """Returns (translation, source_language_used)."""
        result = await self._translation.translate(
            TranslateRequest(text=text, source=source or AUTO, target=target, context=options.context, tone=options.tone)
        )
        return result.translation, result.source_language

    async def translate_document(
        self, document: TemporaryDocument, options: DocumentTranslateOptions, target: str, explicit_source: str | None
    ) -> DocumentTranslationResult:
        started = time.perf_counter()
        paragraphs = extract(document.path, document.format)
        translatable = [(index, p) for index, p in enumerate(paragraphs) if p.text]
        if len(translatable) > self._max_paragraphs:
            raise DocumentTooComplexError(
                f"The document has more than {self._max_paragraphs} translatable paragraphs.",
                max_paragraphs=self._max_paragraphs,
            )

        source = explicit_source
        first_translation: str | None = None
        if source is None:
            # One detection call on the first non-blank paragraph decides the source for the whole document, rather
            # than detecting per paragraph (which could disagree paragraph to paragraph in a mixed-language scan).
            # Its translation is kept and reused below, so this paragraph is never sent to the provider twice.
            first_translation, source = await self._translate_text(translatable[0][1].text, None, target, options)

        semaphore = asyncio.Semaphore(_MAX_CONCURRENT_TRANSLATIONS)

        async def translate_one(text: str) -> str:
            async with semaphore:
                translation, _ = await self._translate_text(text, source, target, options)
                return translation

        rest = translatable[1:] if first_translation is not None else translatable
        rest_translations = await asyncio.gather(*(translate_one(p.text) for _, p in rest))
        translations = ([first_translation] if first_translation is not None else []) + list(rest_translations)

        translated_paragraphs = list(paragraphs)
        for (index, original), translation in zip(translatable, translations, strict=True):
            translated_paragraphs[index] = Paragraph(text=translation, style=original.style)

        content = regenerate(translated_paragraphs, document.format)

        logger.info(
            "document_translation_completed %s",
            kv(
                format=document.format,
                source_language=source or "unknown",
                target_language=target,
                auto_detected=explicit_source is None,
                paragraph_count=len(translatable),
                document_bytes=document.size,
                latency_ms=round((time.perf_counter() - started) * 1000),
            ),
        )
        return DocumentTranslationResult(
            content=content,
            content_type=document.content_type,
            source_language=source or "",
            target_language=target,
            paragraph_count=len(translatable),
        )
