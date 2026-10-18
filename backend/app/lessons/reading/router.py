"""GET /v1/reading/{language}: a page of the standard Reading practice sentences for one language."""

from fastapi import APIRouter, Depends, Query, Request

from app.core.errors import UnsupportedLanguageError
from app.lessons.reading.schemas import ReadingPage
from app.lessons.reading.service import ReadingService
from app.translation.languages import base_code, is_well_formed_code

router = APIRouter(tags=["reading"])


def get_reading_service(request: Request) -> ReadingService:
    return request.app.state.reading_service


@router.get("/reading/{language}", response_model=ReadingPage)
async def reading_page(
    language: str,
    offset: int = Query(0, ge=0),
    limit: int = Query(10, ge=1, le=50),
    service: ReadingService = Depends(get_reading_service),  # noqa: B008 - FastAPI's own pattern
) -> ReadingPage:
    if not is_well_formed_code(language):
        raise UnsupportedLanguageError(f"'{language}' is not a language code.", language=language)
    sentences, total = service.page(base_code(language), offset, limit)
    return ReadingPage(language=base_code(language), offset=offset, sentences=sentences, total=total)
