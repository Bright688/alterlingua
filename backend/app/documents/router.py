"""POST /v1/documents/translate: upload a PDF or DOCX file, receive a translated file of the same format back."""

from fastapi import APIRouter, Depends, File, Form, Request, UploadFile
from fastapi.responses import Response
from pydantic import ValidationError

from app.core.errors import AppError
from app.documents.schemas import DocumentTranslateOptions
from app.documents.service import DocumentTranslationService
from app.documents.upload import temporary_document

router = APIRouter(prefix="/documents", tags=["documents"])

_FILENAME = {"pdf": "translated.pdf", "docx": "translated.docx"}


class InvalidRequestError(AppError):
    status_code = 422
    code = "invalid_request"


def get_document_service(request: Request) -> DocumentTranslationService:
    return request.app.state.document_service


@router.post("/translate")
async def translate_document(
    request: Request,
    file: UploadFile = File(description="The document to translate (PDF, or Word .docx)."),
    target: str = Form(description="The language to translate into, for example 'es'."),
    source: str = Form("auto", description="The document's language, or 'auto' to detect it."),
    context: str = Form("messaging"),
    tone: str = Form("natural"),
    service: DocumentTranslationService = Depends(get_document_service),
) -> Response:
    try:
        options = DocumentTranslateOptions(target=target, source=source, context=context, tone=tone)
    except ValidationError as error:
        details = [{"field": ".".join(str(p) for p in item["loc"]), "message": item["msg"]} for item in error.errors()]
        raise InvalidRequestError("The request is not valid.", details=details) from None

    settings = request.app.state.settings
    try:
        checked_target, explicit_source = service.check_request(options)  # cheap checks before touching the file
        async with temporary_document(
            file, file.content_type, max_bytes=settings.max_document_bytes, directory=settings.temp_dir
        ) as document:
            result = await service.translate_document(document, options, checked_target, explicit_source)
    finally:
        await file.close()

    filename = _FILENAME["pdf" if result.content_type == "application/pdf" else "docx"]
    return Response(
        content=result.content,
        media_type=result.content_type,
        headers={
            "Content-Disposition": f'attachment; filename="{filename}"',
            "X-Source-Language": result.source_language,
            "X-Target-Language": result.target_language,
        },
    )
