"""GET /v1/curriculum/{language}: a page of the standard curriculum for one language."""

from fastapi import APIRouter, Depends, Query, Request

from app.core.errors import UnsupportedLanguageError
from app.translation.languages import base_code, is_well_formed_code
from app.vocabulary.schemas import CurriculumPage
from app.vocabulary.service import CurriculumService

router = APIRouter(tags=["vocabulary"])


def get_curriculum_service(request: Request) -> CurriculumService:
    return request.app.state.curriculum_service


@router.get("/curriculum/{language}", response_model=CurriculumPage)
async def curriculum_page(
    language: str,
    offset: int = Query(0, ge=0),
    limit: int = Query(20, ge=1, le=100),
    service: CurriculumService = Depends(get_curriculum_service),  # noqa: B008 - FastAPI's own pattern
) -> CurriculumPage:
    if not is_well_formed_code(language):
        raise UnsupportedLanguageError(f"'{language}' is not a language code.", language=language)
    words, total = service.page(base_code(language), offset, limit)
    return CurriculumPage(language=base_code(language), offset=offset, words=words, total=total)
