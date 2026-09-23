"""The translation service: validates the request against the language catalogue and the provider's
capabilities, calls the provider, and checks its answer. There is no per-language-pair logic here."""

import asyncio
import logging
import time

from app.core.errors import (
    AutoDetectUnavailableError,
    ProviderError,
    ProviderTimeoutError,
    SameLanguageError,
    TextTooLongError,
    UnsupportedLanguageError,
    UnsupportedLanguagePairError,
)
from app.core.logging import kv
from app.translation.languages import AUTO, LANGUAGES, Language
from app.translation.provider import ProviderRequest, TranslationProvider
from app.translation.schemas import TranslateRequest, TranslateResponse

logger = logging.getLogger(__name__)


def _supported_codes() -> list[str]:
    return sorted(code for code, lang in LANGUAGES.items() if lang.translation_supported)


class TranslationService:
    def __init__(self, provider: TranslationProvider, *, max_text_chars: int, timeout_seconds: float) -> None:
        self._provider = provider
        self._max_text_chars = max_text_chars
        self._timeout = timeout_seconds

    @property
    def provider_name(self) -> str:
        return self._provider.name

    def require_language(self, code: str, role: str) -> Language:
        """The catalogue entry for [code], or a controlled unsupported-language error naming its [role]."""
        language = LANGUAGES.get(code)
        if language is None or not language.translation_supported:
            raise UnsupportedLanguageError(
                f"The {role} language {code!r} is not supported.",
                language=code,
                role=role,
                supported=_supported_codes(),
            )
        return language

    def ensure_target_supported(self, code: str) -> str:
        """Checks that [code] can be translated into, by the catalogue and by the provider, before any costly work."""
        target = self.require_language(code, "target").code
        caps = self._provider.capabilities()
        if target not in caps.languages:
            raise UnsupportedLanguageError(
                f"The translation provider does not support the target language {target!r}.",
                language=target,
                role="target",
                provider=self._provider.name,
                supported=sorted(caps.languages & set(_supported_codes())),
            )
        return target

    async def translate(self, request: TranslateRequest) -> TranslateResponse:
        started = time.perf_counter()
        # Characters, not bytes: 日本語 is 3 characters (9 bytes in UTF-8).
        if len(request.text) > self._max_text_chars:
            raise TextTooLongError(
                f"The text is longer than {self._max_text_chars} characters.", max_characters=self._max_text_chars
            )

        target = self.require_language(request.target, "target").code
        explicit_source = None
        if request.source != AUTO:
            explicit_source = self.require_language(request.source, "source").code
            if explicit_source == target:
                raise SameLanguageError("The source and target languages are the same.", language=target)

        caps = self._provider.capabilities()
        if explicit_source is None and not caps.auto_detect:
            raise AutoDetectUnavailableError(
                "The translation provider cannot detect the source language. Choose it explicitly.",
                provider=self._provider.name,
            )
        if target not in caps.languages:
            raise UnsupportedLanguageError(
                f"The translation provider does not support the target language {target!r}.",
                language=target,
                role="target",
                provider=self._provider.name,
                supported=sorted(caps.languages & set(_supported_codes())),
            )
        if explicit_source is not None and not caps.supports_pair(explicit_source, target):
            raise UnsupportedLanguagePairError(
                f"The translation provider cannot translate {explicit_source!r} to {target!r}.",
                source=explicit_source,
                target=target,
                provider=self._provider.name,
            )

        try:
            result = await asyncio.wait_for(
                self._provider.translate(
                    ProviderRequest(request.text, explicit_source, target, request.context, request.tone)
                ),
                timeout=self._timeout,
            )
        except TimeoutError:
            raise ProviderTimeoutError("The translation provider took too long.", provider=self._provider.name) from None

        source = explicit_source or (result.detected_source or "").lower()
        if not source or source not in LANGUAGES:
            raise ProviderError("The translation provider did not report a usable source language.")
        if not result.translation.strip():
            raise ProviderError("The translation provider returned an empty translation.")

        # Text that is already in the target language is returned as it is, never re-translated.
        translation = request.text if source == target else result.translation

        logger.info(
            "translation_request_completed %s",
            kv(
                source_language=source,
                target_language=target,
                auto_detected=explicit_source is None,
                input_chars=len(request.text),
                provider=self._provider.name,
                latency_ms=round((time.perf_counter() - started) * 1000),
            ),
        )
        return TranslateResponse(translation=translation, source_language=source, target_language=target)
