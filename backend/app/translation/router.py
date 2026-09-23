"""POST /v1/translate: a single route for every language pair."""

from fastapi import APIRouter, Depends, Request

from app.translation.schemas import TranslateRequest, TranslateResponse
from app.translation.service import TranslationService

router = APIRouter(tags=["translation"])


def get_translation_service(request: Request) -> TranslationService:
    return request.app.state.translation_service


@router.post("/translate", response_model=TranslateResponse)
async def translate(
    body: TranslateRequest,
    service: TranslationService = Depends(get_translation_service),
) -> TranslateResponse:
    return await service.translate(body)
