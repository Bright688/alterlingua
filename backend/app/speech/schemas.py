"""Request options and response for POST /v1/audio/translate."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, field_validator

from app.translation.languages import AUTO
from app.translation.schemas import Context, Tone, _clean_language_code


class AudioTranslateOptions(BaseModel):
    """The form fields sent next to the audio file."""

    model_config = ConfigDict(extra="forbid")

    target: str
    source: str = AUTO  # the language spoken, or "auto" to detect it
    context: Context = "messaging"
    tone: Tone = "natural"
    # Languages the speaker is likely to be using, most likely first (the app sends the user's learning language and own
    # language). Only used when automatic detection names a language we cannot translate or hears nothing, to try again
    # with these instead of giving up. Codes that are not valid languages are ignored.
    hints: list[str] = []

    @field_validator("hints", mode="before")
    @classmethod
    def _clean_hints(cls, value: object) -> list[str]:
        if isinstance(value, str):
            value = value.split(",")
        cleaned: list[str] = []
        for item in value if isinstance(value, (list, tuple)) else []:
            try:
                code = _clean_language_code(str(item), allow_auto=False)
            except ValueError:
                continue
            if code not in cleaned:
                cleaned.append(code)
        return cleaned[:3]

    @field_validator("source")
    @classmethod
    def _clean_source(cls, value: str) -> str:
        return _clean_language_code(value, allow_auto=True)

    @field_validator("target")
    @classmethod
    def _clean_target(cls, value: str) -> str:
        return _clean_language_code(value, allow_auto=False)


class AudioTranslateResponse(BaseModel):
    source_language: str
    transcript: str
    target_language: str
    translation: str
    # "unclear" when the speech engine was not sure of its words (noise, a poor recording), so the app can say some words may
    # be wrong; "clear" when it was. Left out when the engine does not report how sure it is.
    clarity: Literal["clear", "unclear"] | None = None


class AudioSpeakOptions(AudioTranslateOptions):
    """The form fields of POST /v1/audio/speak: the translate fields plus an optional voice."""

    voice: str | None = None  # a voice id from GET /v1/audio/voices; None lets the provider's first voice for the language speak

    @field_validator("voice")
    @classmethod
    def _clean_voice(cls, value: str | None) -> str | None:
        value = (value or "").strip()
        return value or None


class SpokenAudio(BaseModel):
    """The generated speech. ``data`` is the audio file, base64-encoded."""

    content_type: str
    voice: str
    size_bytes: int
    data: str


class AudioSpeakResponse(BaseModel):
    source_language: str
    transcript: str
    target_language: str
    translation: str
    audio: SpokenAudio


class VoiceInfo(BaseModel):
    id: str
    locale: str
    gender: str | None = None


class LanguageVoices(BaseModel):
    language: str
    voices: list[VoiceInfo]


class VoicesResponse(BaseModel):
    provider: str
    languages: list[LanguageVoices]
