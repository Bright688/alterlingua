"""Response shape for GET /v1/curriculum/{language}."""

from pydantic import BaseModel, Field


class CurriculumPage(BaseModel):
    language: str
    offset: int
    words: list[str]
    total: int = Field(description="How many words this language's curriculum has in total.")
