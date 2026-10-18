"""Version 1 of the API. Later modules add their routers here."""

from fastapi import APIRouter

from app.documents.router import router as documents_router
from app.lessons.reading.router import router as reading_router
from app.speech.router import router as speech_router
from app.translation.router import router as translation_router
from app.vocabulary.router import router as vocabulary_router

router = APIRouter(prefix="/v1")
router.include_router(translation_router)
router.include_router(speech_router)
router.include_router(vocabulary_router)
router.include_router(reading_router)
router.include_router(documents_router)
