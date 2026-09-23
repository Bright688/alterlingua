"""POST /v1/audio/translate, POST /v1/audio/speak and GET /v1/audio/voices: one route for every language pair."""

from fastapi import APIRouter, Depends, File, Form, Request, UploadFile
from pydantic import ValidationError

from app.core.errors import AppError
from app.speech.audio import temporary_audio
from app.speech.schemas import AudioSpeakOptions, AudioSpeakResponse, AudioTranslateOptions, AudioTranslateResponse, VoicesResponse
from app.speech.service import SpeechTranslationService
from app.speech.speak_service import SpeechSynthesisService

router = APIRouter(prefix="/audio", tags=["speech"])


class InvalidRequestError(AppError):
    status_code = 422
    code = "invalid_request"


def get_speech_service(request: Request) -> SpeechTranslationService:
    return request.app.state.speech_service


def get_synthesis_service(request: Request) -> SpeechSynthesisService:
    return request.app.state.synthesis_service


@router.get("/voices", response_model=VoicesResponse)
async def list_voices(service: SpeechSynthesisService = Depends(get_synthesis_service)) -> VoicesResponse:
    """Which languages can be spoken, and with which voices. Clients check this before offering translated voice."""
    return service.voices()


@router.post("/speak", response_model=AudioSpeakResponse)
async def speak_audio(
    request: Request,
    audio: UploadFile = File(description="The recording (wav, mp3, m4a/mp4, aac, ogg/opus, webm, flac, amr)."),
    target: str = Form(description="The language to translate into and speak, for example 'es'."),
    source: str = Form("auto", description="The language spoken, or 'auto' to detect it."),
    context: str = Form("messaging"),
    tone: str = Form("natural"),
    voice: str = Form("", description="Optional voice id from GET /v1/audio/voices."),
    service: SpeechSynthesisService = Depends(get_synthesis_service),
) -> AudioSpeakResponse:
    try:
        options = AudioSpeakOptions(target=target, source=source, context=context, tone=tone, voice=voice)
    except ValidationError as error:
        details = [{"field": ".".join(str(p) for p in item["loc"]), "message": item["msg"]} for item in error.errors()]
        raise InvalidRequestError("The request is not valid.", details=details) from None

    settings = request.app.state.settings
    try:
        checked_target, spoken, chosen_voice = service.check_request(options)  # cheap checks before touching the audio
        async with temporary_audio(
            audio, audio.content_type, max_bytes=settings.max_audio_bytes, directory=settings.temp_dir
        ) as recording:
            return await service.speak_audio(recording, options, checked_target, spoken, chosen_voice)
    finally:
        await audio.close()


@router.post("/translate", response_model=AudioTranslateResponse)
async def translate_audio(
    request: Request,
    audio: UploadFile = File(description="The recording (wav, mp3, m4a/mp4, aac, ogg/opus, webm, flac, amr)."),
    target: str = Form(description="The language to translate into, for example 'es'."),
    source: str = Form("auto", description="The language spoken, or 'auto' to detect it."),
    context: str = Form("messaging"),
    tone: str = Form("natural"),
    service: SpeechTranslationService = Depends(get_speech_service),
) -> AudioTranslateResponse:
    try:
        options = AudioTranslateOptions(target=target, source=source, context=context, tone=tone)
    except ValidationError as error:
        # Only the field and the problem are reported, never the submitted values.
        details = [{"field": ".".join(str(p) for p in item["loc"]), "message": item["msg"]} for item in error.errors()]
        raise InvalidRequestError("The request is not valid.", details=details) from None

    settings = request.app.state.settings
    try:
        checked_target, spoken = service.check_request(options)  # cheap checks before touching the audio
        async with temporary_audio(
            audio, audio.content_type, max_bytes=settings.max_audio_bytes, directory=settings.temp_dir
        ) as recording:
            return await service.translate_audio(recording, options, checked_target, spoken)
    finally:
        await audio.close()
