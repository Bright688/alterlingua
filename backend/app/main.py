"""Application factory and error handling."""

import logging
import time
from collections.abc import Awaitable, Callable

from fastapi import Depends, FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.responses import Response

from app import __version__
from app.api import health, v1
from app.core.config import Settings, get_settings
from app.core.errors import AppError, AudioTooLargeError
from app.core.logging import configure_logging, kv
from app.core.security import check_startup, make_guard
from app.speech.provider import SpeechToTextProvider
from app.speech.registry import create_stt_provider, create_tts_provider
from app.speech.service import SpeechTranslationService
from app.speech.speak_service import SpeechSynthesisService
from app.speech.tts_provider import TextToSpeechProvider
from app.translation.provider import TranslationProvider
from app.translation.registry import create_provider
from app.translation.service import TranslationService

logger = logging.getLogger(__name__)

# Room for the multipart envelope (boundaries and form fields) around the audio itself.
MULTIPART_OVERHEAD_BYTES = 64 * 1024


def create_app(
    settings: Settings | None = None,
    provider: TranslationProvider | None = None,
    stt_provider: SpeechToTextProvider | None = None,
    tts_provider: TextToSpeechProvider | None = None,
) -> FastAPI:
    """Builds the app. Tests pass their own ``settings`` or ``provider``."""
    settings = settings or get_settings()
    check_startup(settings)
    configure_logging(settings.log_level)
    provider = provider or create_provider(settings)
    if provider.name == "fake":
        logger.warning("translation_provider_is_fake %s", kv(note="development_only_not_real_translation"))

    stt_provider = stt_provider or create_stt_provider(settings)
    if stt_provider.name == "fake":
        logger.warning("speech_provider_is_fake %s", kv(note="development_only_not_real_recognition"))

    tts_provider = tts_provider or create_tts_provider(settings)
    if tts_provider.name == "fake":
        logger.warning("text_to_speech_provider_is_fake %s", kv(note="development_only_not_real_speech"))

    # The interactive API pages describe every route and are not needed in production.
    docs_enabled = settings.environment != "production"
    app = FastAPI(
        title="AlterLingua API",
        version=__version__,
        docs_url="/docs" if docs_enabled else None,
        redoc_url="/redoc" if docs_enabled else None,
        openapi_url="/openapi.json" if docs_enabled else None,
    )
    app.state.settings = settings
    app.state.translation_service = TranslationService(
        provider,
        max_text_chars=settings.max_text_chars,
        timeout_seconds=settings.provider_timeout_seconds,
    )

    app.state.speech_service = SpeechTranslationService(
        stt_provider, app.state.translation_service, timeout_seconds=settings.stt_timeout_seconds
    )

    app.state.synthesis_service = SpeechSynthesisService(
        app.state.speech_service, tts_provider, timeout_seconds=settings.tts_timeout_seconds, max_chars=settings.max_tts_chars
    )

    @app.middleware("http")
    async def refuse_oversized_audio(request: Request, call_next: Callable[[Request], Awaitable[Response]]) -> Response:
        # Turn away an obviously too large upload from its declared size, before its body is read.
        if request.method == "POST" and request.url.path in ("/v1/audio/translate", "/v1/audio/speak"):
            declared = request.headers.get("content-length", "")
            if declared.isdigit() and int(declared) > settings.max_audio_bytes + MULTIPART_OVERHEAD_BYTES:
                error = AudioTooLargeError(f"The audio is larger than {settings.max_audio_bytes} bytes.", max_bytes=settings.max_audio_bytes)
                return JSONResponse(status_code=error.status_code, content=error.to_body())
        return await call_next(request)

    @app.middleware("http")
    async def never_cache(request: Request, call_next: Callable[[Request], Awaitable[Response]]) -> Response:
        # Translations, transcripts and generated speech are private: no proxy or client may keep a copy.
        response = await call_next(request)
        if request.url.path.startswith("/v1/"):
            response.headers["Cache-Control"] = "no-store"
            response.headers["Pragma"] = "no-cache"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.middleware("http")
    async def log_request(request: Request, call_next: Callable[[Request], Awaitable[Response]]) -> Response:
        started = time.perf_counter()
        response = await call_next(request)
        # Method, path and status only: never the body, and never a query string.
        logger.info(
            "http_request_completed %s",
            kv(method=request.method, path=request.url.path, status=response.status_code,
               latency_ms=round((time.perf_counter() - started) * 1000)),
        )
        return response

    @app.exception_handler(AppError)
    async def handle_app_error(request: Request, error: AppError) -> JSONResponse:
        # The error's code names the kind of problem (never any text from the request), so a rejected request can be
        # told apart from another with the same status.
        logger.info("request_rejected %s", kv(path=request.url.path, status=error.status_code, code=error.code))
        return JSONResponse(status_code=error.status_code, content=error.to_body())

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(_: Request, error: RequestValidationError) -> JSONResponse:
        # FastAPI's default answer repeats the submitted values; ours names only the field and the problem.
        details = [
            {"field": ".".join(str(part) for part in item["loc"] if part != "body"), "message": item["msg"]}
            for item in error.errors()
        ]
        return JSONResponse(
            status_code=422,
            content={"error": {"code": "invalid_request", "message": "The request is not valid.", "details": details}},
        )

    @app.exception_handler(Exception)
    async def handle_unexpected(_: Request, error: Exception) -> JSONResponse:
        # Only the exception type is logged: its message could contain the user's text.
        logger.error("unhandled_error %s", kv(error_type=type(error).__name__))
        return JSONResponse(
            status_code=500, content={"error": {"code": "internal_error", "message": "Something went wrong."}}
        )

    app.include_router(health.router)
    app.include_router(v1.router, dependencies=[Depends(make_guard(settings))])
    return app


def app_factory() -> FastAPI:
    """Entry point for uvicorn: ``uvicorn app.main:app_factory --factory``."""
    return create_app()
