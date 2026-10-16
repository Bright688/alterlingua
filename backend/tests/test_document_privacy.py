import logging

from tests.conftest import make_client
from tests.document_helpers import DOCX_CONTENT_TYPE, PDF_CONTENT_TYPE, make_docx, make_pdf

SECRET = "please wire 4200 euros for invoice XYZ-9917"


def post(client, data, content_type, **form):
    form.setdefault("target", "es")
    return client.post("/v1/documents/translate", files={"file": ("doc", data, content_type)}, data=form)


def logged(caplog) -> str:
    return "\n".join(record.getMessage() for record in caplog.records)


def test_success_logs_no_document_text(caplog):
    caplog.set_level(logging.DEBUG)
    response = post(make_client(), make_docx([(SECRET, None)]), DOCX_CONTENT_TYPE, source="en")
    assert response.status_code == 200
    text = logged(caplog)
    assert "document_translation_completed" in text
    assert "format=docx" in text and "paragraph_count=" in text and "document_bytes=" in text
    for private in ("invoice", "XYZ", "4200", "[es]"):
        assert private not in text, private


def test_failure_logs_no_document_text(caplog):
    caplog.set_level(logging.DEBUG)
    # An unsupported source is refused before any content is read, but the request body still carried the secret.
    response = post(make_client(), make_pdf([SECRET]), PDF_CONTENT_TYPE, source="pt")
    assert response.status_code == 422
    assert "invoice" not in logged(caplog) and "4200" not in logged(caplog)


def test_error_bodies_never_echo_document_text(caplog):
    response = post(make_client(), make_docx([(SECRET, None)]), PDF_CONTENT_TYPE)  # docx declared as pdf: refused
    assert response.status_code == 415
    assert "invoice" not in response.text and SECRET not in response.text
