import pytest


def post(client, body):
    return client.post("/v1/translate", json=body)


def fields(response):
    return {d["field"] for d in response.json()["error"]["details"]}


@pytest.mark.parametrize("text", ["", "   ", "\n\t "])
def test_empty_text_is_rejected(client, text):
    response = post(client, {"text": text, "target": "fr"})
    assert response.status_code == 422
    assert response.json()["error"]["code"] == "invalid_request"
    assert fields(response) == {"text"}


def test_control_characters_are_rejected_but_newlines_are_fine(client):
    assert post(client, {"text": "bad\x00text", "target": "fr", "source": "en"}).status_code == 422
    assert post(client, {"text": "Thank you very much!\n", "target": "fr", "source": "en"}).status_code == 200


@pytest.mark.parametrize("target", ["", "f", "french", "fr!", "12", "auto", "fr-", "fr--FR"])
def test_malformed_target_codes(client, target):
    response = post(client, {"text": "Bonjour", "target": target})
    assert response.status_code == 422
    assert "target" in fields(response)


def test_missing_target_and_missing_text(client):
    assert fields(post(client, {"text": "Hi"})) == {"target"}
    assert fields(post(client, {"target": "fr"})) == {"text"}


@pytest.mark.parametrize("body", [{"context": "shouting"}, {"tone": "rude"}, {"unknown": 1}])
def test_unknown_options_and_extra_fields_are_rejected(client, body):
    assert post(client, {"text": "Hi", "target": "fr", **body}).status_code == 422


def test_wrong_types_and_invalid_json(client):
    assert post(client, {"text": 5, "target": "fr"}).status_code == 422
    response = client.post("/v1/translate", content=b"{not json", headers={"content-type": "application/json"})
    assert response.status_code == 422


def test_validation_errors_do_not_echo_the_submitted_text(client):
    secret = "my private message\x00"
    response = post(client, {"text": secret, "target": "fr"})
    assert response.status_code == 422
    assert "private" not in response.text


def test_wrong_method_and_unknown_path(client):
    assert client.get("/v1/translate").status_code == 405
    assert client.get("/v1/translate-english-to-french").status_code == 404
