"""Response shape for GET /v1/reading/{language}."""

from pydantic import BaseModel, Field


class ReadingPage(BaseModel):
    language: str
    offset: int
    sentences: list[str]
    total: int = Field(description="How many reading sentences this language has in total.")
