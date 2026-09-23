"""Version 1 of the API. Later modules add their routers here."""

from fastapi import APIRouter

from app.speech.router import router as speech_router
from app.translation.router import router as translation_router

router = APIRouter(prefix="/v1")
router.include_router(translation_router)
router.include_router(speech_router)
