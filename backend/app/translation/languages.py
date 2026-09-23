"""The central language catalogue (CLAUDE.md 6.1, 6.5, 6.6). Add a language here, not in business logic.

Identity is the base language code ("zh"). A locale such as "zh-CN" is a separate idea and is never part of it.
Feature flags are separate because translation, speech and lessons need not cover the same languages.
"""

import re
from dataclasses import dataclass

AUTO = "auto"

_CODE_PATTERN = re.compile(r"^[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8})*$")


@dataclass(frozen=True)
class Language:
    code: str
    native_name: str
    english_name: str
    default_locale: str
    translation_supported: bool = True
    # Product-level intent: AlterLingua wants speech recognition for these languages. What a given provider can
    # actually do is decided by its own capabilities (see app/speech/provider.py).
    speech_to_text_supported: bool = True
    # Product-level intent, like speech_to_text_supported: which voices exist is up to the provider (app/speech/tts_provider.py).
    text_to_speech_supported: bool = True
    learning_supported: bool = True


LANGUAGES: dict[str, Language] = {
    lang.code: lang
    for lang in (
        Language("en", "English", "English", "en-US"),
        Language("fr", "Français", "French", "fr-FR"),
        Language("es", "Español", "Spanish", "es-ES"),
        Language("de", "Deutsch", "German", "de-DE"),
        Language("it", "Italiano", "Italian", "it-IT"),
        Language("nl", "Nederlands", "Dutch", "nl-NL"),
        Language("zh", "中文", "Chinese", "zh-CN"),
        Language("ja", "日本語", "Japanese", "ja-JP"),
    )
}


def is_well_formed_code(value: str) -> bool:
    """True for shapes like "es", "fr-FR" or "zh_Hans"; says nothing about whether we support it."""
    return bool(_CODE_PATTERN.match(value))


def base_code(value: str) -> str:
    """"fr-FR" -> "fr", " EN " -> "en", "zh_Hans" -> "zh"."""
    return value.strip().replace("_", "-").split("-", 1)[0].lower()


def get_language(code: str) -> Language | None:
    return LANGUAGES.get(base_code(code))
