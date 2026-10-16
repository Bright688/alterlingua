"""Request options for POST /v1/documents/translate."""

from pydantic import BaseModel, ConfigDict, field_validator

from app.translation.languages import AUTO
from app.translation.schemas import Context, Tone, _clean_language_code


class DocumentTranslateOptions(BaseModel):
    """The form fields sent next to the document file."""

    model_config = ConfigDict(extra="forbid")

    target: str
    source: str = AUTO
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
