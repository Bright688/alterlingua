"""DEVELOPMENT-ONLY provider. It is NOT a real translator.

It knows a few sample phrases in every catalogue language so the whole pipeline (API, validation, capability checks,
Unicode handling, Android integration) can be exercised without an API key. Any other text comes back clearly
marked as fake, for example ``[es] some text``, so it can never be mistaken for a real translation.
Replace it with a real provider through ``ALTERLINGUA_TRANSLATION_PROVIDER``.
"""

import unicodedata

from app.core.errors import SourceLanguageUndetectedError
from app.translation.languages import LANGUAGES
from app.translation.provider import (
    ProviderCapabilities,
    ProviderRequest,
    ProviderResult,
    TranslationProvider,
)

_PHRASEBOOK: list[dict[str, str]] = [
    {
        "en": "Are you coming tomorrow?",
        "fr": "Tu viens demain ?",
        "es": "¿Vienes mañana?",
        "de": "Kommst du morgen?",
        "it": "Vieni domani?",
        "nl": "Kom je morgen?",
        "zh": "你明天来吗？",
        "ja": "明日来ますか？",
    },
    {
        "en": "I'll send you the quotation before noon.",
        "fr": "Je vous enverrai le devis avant midi.",
        "es": "Le enviaré el presupuesto antes del mediodía.",
        "de": "Ich schicke Ihnen das Angebot vor Mittag.",
        "it": "Le invierò il preventivo prima di mezzogiorno.",
        "nl": "Ik stuur u de offerte vóór de middag.",
        "zh": "我会在中午前把报价单发给您。",
        "ja": "正午までに見積書をお送りします。",
    },
    {
        "en": "Thank you very much!",
        "fr": "Merci beaucoup !",
        "es": "¡Muchas gracias!",
        "de": "Vielen Dank!",
        "it": "Grazie mille!",
        "nl": "Hartelijk bedankt!",
        "zh": "非常感谢！",
        "ja": "どうもありがとうございます！",
    },
]


# One sample sentence per language (the first phrase), used by the development speech provider.
SAMPLE_PHRASES: dict[str, str] = dict(_PHRASEBOOK[0])


def _key(text: str) -> str:
    return unicodedata.normalize("NFC", text).strip().casefold()


_LOOKUP: dict[str, tuple[int, str]] = {
    _key(phrase): (index, code)
    for index, entry in enumerate(_PHRASEBOOK)
    for code, phrase in entry.items()
}


def _detect_by_script(text: str) -> str | None:
    """Only the writing systems that identify a language on their own: kana means Japanese, Han alone means Chinese."""
    has_kana = any("぀" <= ch <= "ヿ" for ch in text)
    if has_kana:
        return "ja"
    if any("一" <= ch <= "鿿" for ch in text):
        return "zh"
    return None


class FakeTranslationProvider(TranslationProvider):
    name = "fake"

    def capabilities(self) -> ProviderCapabilities:
        return ProviderCapabilities(languages=frozenset(LANGUAGES), auto_detect=True)

    async def translate(self, request: ProviderRequest) -> ProviderResult:
        known = _LOOKUP.get(_key(request.text))
        source = request.source
        if source is None:
            source = known[1] if known else _detect_by_script(request.text)
            if source is None:
                raise SourceLanguageUndetectedError(
                    "Could not tell which language the text is in. Choose the source language."
                )
        if known:
            translated = _PHRASEBOOK[known[0]].get(request.target)
            if translated:
                return ProviderResult(translation=translated, detected_source=source)
        return ProviderResult(translation=f"[{request.target}] {request.text}", detected_source=source)
