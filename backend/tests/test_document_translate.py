import tempfile
from pathlib import Path

import pytest

from app.documents.extraction import extract_docx, extract_pdf
from tests.conftest import make_client
from tests.document_helpers import DOCX_CONTENT_TYPE, PDF_CONTENT_TYPE, make_blank_pdf, make_docx, make_pdf


def post(client, data, content_type, filename="file.bin", **form):
    form.setdefault("target", "es")
    return client.post("/v1/documents/translate", files={"file": (filename, data, content_type)}, data=form)


def error_code(response):
    return response.json()["error"]["code"]


def write(data: bytes, suffix: str) -> Path:
    path = Path(tempfile.mktemp(suffix=suffix))
    path.write_bytes(data)
    return path


# ---- happy path ----

def test_a_pdf_is_translated_and_a_real_pdf_comes_back(client):
    response = post(client, make_pdf(["Are you coming tomorrow?"]), PDF_CONTENT_TYPE, "doc.pdf")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith(PDF_CONTENT_TYPE)
    assert response.headers["x-source-language"] == "en"
    assert response.headers["x-target-language"] == "es"
    translated = [p.text for p in extract_pdf(write(response.content, ".pdf")) if p.text]
    assert translated == ["¿Vienes mañana?"]


def test_a_docx_is_translated_and_a_real_docx_comes_back(client):
    response = post(client, make_docx([("Thank you very much!", None)]), DOCX_CONTENT_TYPE, "doc.docx")
    assert response.status_code == 200
    assert response.headers["content-type"].startswith(DOCX_CONTENT_TYPE)
    translated = [p.text for p in extract_docx(write(response.content, ".docx"))]
    assert translated == ["¡Muchas gracias!"]


def test_content_type_parameters_and_case_are_ignored(client):
    response = post(client, make_pdf(["Thank you very much!"]), "Application/PDF; charset=binary", "doc.pdf")
    assert response.status_code == 200


# ---- document type ----

@pytest.mark.parametrize("content_type", ["text/plain", "application/json", "image/png", "application/octet-stream", ""])
def test_types_that_are_not_a_supported_document_are_refused(client, content_type):
    response = post(client, make_pdf(["hello"]), content_type)
    assert response.status_code == 415
    assert error_code(response) == "unsupported_document_type"


def test_a_file_whose_bytes_do_not_match_the_declared_type_is_refused(client):
    assert post(client, b"this is plain text, not a document at all", PDF_CONTENT_TYPE).status_code == 415
    docx = make_docx([("hello", None)])
    assert post(client, docx, PDF_CONTENT_TYPE).status_code == 415  # docx bytes declared as pdf
    pdf = make_pdf(["hello"])
    assert post(client, pdf, DOCX_CONTENT_TYPE).status_code == 415  # pdf bytes declared as docx


def test_a_refused_file_never_reaches_translation(tmp_path):
    client = make_client(temp_dir=str(tmp_path))
    post(client, b"not a document", PDF_CONTENT_TYPE)
    post(client, make_pdf(["hi"]), "text/plain")
    assert list(tmp_path.iterdir()) == []


def test_no_translatable_text_is_refused(client):
    response = post(client, make_blank_pdf(), PDF_CONTENT_TYPE)
    assert response.status_code == 422
    assert error_code(response) == "invalid_document"
    response = post(client, make_docx([("", None)]), DOCX_CONTENT_TYPE)
    assert response.status_code == 422
    assert error_code(response) == "invalid_document"


# ---- size ----

def test_an_empty_file_is_refused(client):
    response = post(client, b"", PDF_CONTENT_TYPE)
    assert response.status_code == 422
    assert error_code(response) == "invalid_document"


def test_a_document_over_the_limit_is_refused(tmp_path):
    small = make_pdf(["hi"])
    client = make_client(max_document_bytes=len(small), temp_dir=str(tmp_path))
    assert post(client, small, PDF_CONTENT_TYPE, source="en").status_code == 200
    big = make_pdf(["this is a somewhat longer sentence than the small one above"])
    response = post(client, big, PDF_CONTENT_TYPE, source="en")
    assert response.status_code == 413
    assert error_code(response) == "document_too_large"
    assert list(tmp_path.iterdir()) == []


def test_an_obviously_huge_upload_is_turned_away_from_its_declared_size(tmp_path):
    client = make_client(max_document_bytes=1024, temp_dir=str(tmp_path))
    response = post(client, b"%PDF-1.4\n" + b"\x00" * 300_000, PDF_CONTENT_TYPE)
    assert response.status_code == 413
    assert error_code(response) == "document_too_large"
    assert list(tmp_path.iterdir()) == []


# ---- too many translatable units ----

def test_a_document_with_too_many_units_is_refused(tmp_path):
    client = make_client(document_max_paragraphs=1, temp_dir=str(tmp_path))
    response = post(client, make_docx([("first", None), ("second", None)]), DOCX_CONTENT_TYPE)
    assert response.status_code == 422
    assert error_code(response) == "document_too_complex"
    assert list(tmp_path.iterdir()) == []


# ---- the form fields ----

def test_missing_file_or_target_is_reported_by_field(client):
    no_file = client.post("/v1/documents/translate", data={"target": "es"})
    assert no_file.status_code == 422
    assert {d["field"] for d in no_file.json()["error"]["details"]} == {"file"}

    no_target = client.post(
        "/v1/documents/translate", files={"file": ("doc.pdf", make_pdf(["hi"]), PDF_CONTENT_TYPE)}
    )
    assert no_target.status_code == 422
    assert {d["field"] for d in no_target.json()["error"]["details"]} == {"target"}


@pytest.mark.parametrize("target", ["", "f", "french", "12", "auto", "fr--FR"])
def test_malformed_target_codes(client, target):
    response = post(client, make_pdf(["hi"]), PDF_CONTENT_TYPE, target=target)
    assert response.status_code == 422
    assert error_code(response) == "invalid_request"


def test_an_unsupported_source_language_is_controlled(client):
    response = post(client, make_pdf(["hi"]), PDF_CONTENT_TYPE, source="pt")
    assert response.status_code == 422
    error = response.json()["error"]
    assert (error["code"], error["language"], error["role"]) == ("unsupported_language", "pt", "source")


def test_wrong_method(client):
    assert client.get("/v1/documents/translate").status_code == 405


# ---- temporary file lifecycle ----

def test_the_uploaded_file_is_deleted_whether_translation_succeeds_or_fails(tmp_path):
    client = make_client(temp_dir=str(tmp_path))
    post(client, make_pdf(["Are you coming tomorrow?"]), PDF_CONTENT_TYPE)
    post(client, make_blank_pdf(), PDF_CONTENT_TYPE)
    post(client, b"not a document", PDF_CONTENT_TYPE)
    assert list(tmp_path.iterdir()) == []
