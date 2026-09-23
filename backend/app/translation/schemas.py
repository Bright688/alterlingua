"""Request and response shapes for POST /v1/translate."""

import unicodedata
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.translation.languages import AUTO, base_code, is_well_formed_code

Context = Literal["messaging", "general"]
Tone = Literal["natural", "formal", "casual"]

# Control characters other than these have no place in a chat message.
_ALLOWED_CONTROL = {"\n", "\r", "\t"}


def _clean_language_code(value: str, *, allow_auto: bool) -> str:
    candidate = value.strip()
    if allow_auto and candidate.lower() == AUTO:
        return AUTO
    if not is_well_formed_code(candidate):
        raise ValueError("must be a language code such as 'en', 'fr' or 'zh-CN'" + (" or 'auto'" if allow_auto else ""))
    return base_code(candidate)


class TranslateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    text: str
    source: str = AUTO
    target: str
    context: Context = "messaging"
    tone: Tone = "natural"

    @field_validator("text")
    @classmethod
    def _clean_text(cls, value: str) -> str:
        # NFC makes the same visible text always the same code points (for example "é" typed as e + accent).
        value = unicodedata.normalize("NFC", value)
        if not value.strip():
            raise ValueError("must not be empty")
        if any(unicodedata.category(ch) == "Cc" and ch not in _ALLOWED_CONTROL for ch in value):
            raise ValueError("must not contain control characters")
        return value

    @field_validator("source")
    @classmethod
    def _clean_source(cls, value: str) -> str:
        return _clean_language_code(value, allow_auto=True)

    @field_validator("target")
    @classmethod
    def _clean_target(cls, value: str) -> str:
        return _clean_language_code(value, allow_auto=False)


class TranslateResponse(BaseModel):
    translation: str
    source_language: str = Field(description="The language of the input: the given one, or the detected one for 'auto'.")
    target_language: str
