"""Request options and response for POST /v1/audio/translate."""

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
