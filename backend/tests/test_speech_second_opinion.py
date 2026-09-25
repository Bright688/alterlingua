"""When automatic language detection fails on an unclear recording, the recording is tried again in the languages the caller
says are likely. Found on the live service: a French voice note was refused as "language not supported" because the engine
took it for a language outside the catalogue."""

import logging

import pytest

from app.core.errors import ProviderError
from app.speech.provider import SpeechCapabilities, SpeechResult, SpeechToTextProvider
from app.speech.schemas import AudioTranslateOptions
from app.translation.fake_provider import SAMPLE_PHRASES
from tests.audio_helpers import make_wav
from tests.conftest import make_client

FR = SAMPLE_PHRASES["fr"]
EN = SAMPLE_PHRASES["en"]


class Scripted(SpeechToTextProvider):
    """Answers per requested language (None means automatic detection); records what it was asked."""

    name = "scripted"

    def __init__(self, script: dict):
        self.script = script
        self.asked: list[str | None] = []

    def capabilities(self):
        return SpeechCapabilities(frozenset({"en", "fr", "es", "de", "it", "nl", "zh", "ja"}), True)

    async def transcribe(self, request):
        self.asked.append(request.language)
        outcome = self.script[request.language]
        if isinstance(outcome, Exception):
            raise outcome
        return outcome


def post(stt, **form):
    form.setdefault("target", "en")
    client = make_client(stt=stt)
    return client.post("/v1/audio/translate", files={"audio": ("clip.ogg", make_wav(), "audio/wav")}, data=form)


# ---- the failure that was found -----------------------------------------------------------------------------------------

def test_a_language_outside_the_catalogue_is_retried_in_the_hinted_languages_and_the_sure_attempt_wins():
    stt = Scripted({
        None: SpeechResult("mbote nzambe", "yo", -1.6),  # taken for Yoruba
        "fr": SpeechResult(FR, "fr", -0.3),
        "en": SpeechResult("nonsense forced into english", "en", -1.5),
    })
    response = post(stt, source="auto", hints="fr,en")
    assert response.status_code == 200
    body = response.json()
    assert (body["source_language"], body["transcript"], body["translation"]) == ("fr", FR, EN)
    assert stt.asked == [None, "fr", "en"]


def test_without_hints_the_old_answer_stands_language_not_supported():
    stt = Scripted({None: SpeechResult("mbote", "yo", -1.6)})
    response = post(stt, source="auto")
    assert response.status_code == 422 and response.json()["error"]["code"] == "unsupported_language"
    assert stt.asked == [None]


def test_a_supported_detected_language_is_never_second_guessed():
    stt = Scripted({None: SpeechResult(FR, "fr", -0.2)})
    body = post(stt, source="auto", hints="en,es").json()
    assert body["source_language"] == "fr" and stt.asked == [None]


def test_an_explicit_source_language_never_retries():
    stt = Scripted({"fr": SpeechResult(FR, "fr", -0.2)})
    assert post(stt, source="fr", hints="en").status_code == 200 and stt.asked == ["fr"]


def test_when_nothing_was_heard_the_hinted_languages_are_tried():
    stt = Scripted({None: SpeechResult("", "fr", None), "fr": SpeechResult(FR, "fr", -0.4)})
    body = post(stt, source="auto", hints="fr").json()
    assert body["transcript"] == FR and stt.asked == [None, "fr"]


# ---- how the attempts are judged --------------------------------------------------------------------------------------------

def test_speech_in_a_language_that_really_is_unsupported_is_still_refused_not_turned_into_nonsense():
    stt = Scripted({
        None: SpeechResult("uma frase em portugues", "pt", -0.4),
        "fr": SpeechResult("garbled text forced into french", "fr", -1.7),
        "en": SpeechResult("garbled text forced into english", "en", -1.4),
    })
    response = post(stt, source="auto", hints="fr,en")
    assert response.status_code == 422 and response.json()["error"]["code"] == "unsupported_language"


def test_the_most_confident_attempt_wins_not_the_first_hint():
    stt = Scripted({None: SpeechResult("xx", "yo", -1.6), "fr": SpeechResult(FR, "fr", -0.8), "en": SpeechResult(EN, "en", -0.2)})
    assert post(stt, source="auto", target="fr", hints="fr,en").json()["source_language"] == "en"


def test_when_no_confidence_is_reported_the_first_hint_wins():
    stt = Scripted({None: SpeechResult("xx", "yo"), "fr": SpeechResult(FR, "fr"), "en": SpeechResult(EN, "en")})
    assert post(stt, source="auto", hints="fr,en").json()["source_language"] == "fr"


def test_an_attempt_that_fails_is_skipped_and_the_next_hint_is_tried():
    stt = Scripted({None: SpeechResult("xx", "yo", -1.6), "fr": ProviderError("down"), "en": SpeechResult(EN, "en", -0.3)})
    body = post(stt, source="auto", target="fr", hints="fr,en").json()
    assert body["source_language"] == "en"


def test_a_hinted_language_that_cannot_be_recognised_is_not_asked():
    stt = Scripted({None: SpeechResult("xx", "yo", -1.6), "fr": SpeechResult(FR, "fr", -0.3)})
    body = post(stt, source="auto", hints="pt,fr").json()  # Portuguese is not a catalogue language
    assert body["source_language"] == "fr" and stt.asked == [None, "fr"]


# ---- the hints field ----------------------------------------------------------------------------------------------------------

def test_hints_are_cleaned_deduplicated_and_limited_to_three():
    options = AudioTranslateOptions(target="en", hints=" fr, EN ,!!,fr-CA,es,de,it")
    assert options.hints == ["fr", "en", "es"]


def test_hints_default_to_none_and_the_field_is_optional_on_the_route():
    assert AudioTranslateOptions(target="en").hints == []
    stt = Scripted({None: SpeechResult(FR, "fr", -0.2)})
    assert post(stt, source="auto").status_code == 200


# ---- what the server logs ---------------------------------------------------------------------------------------------------

def test_a_rejected_request_logs_its_kind_and_the_language_code_but_no_content(caplog):
    stt = Scripted({None: SpeechResult("a secret sentence nobody may log", "yo", -1.0)})
    with caplog.at_level(logging.INFO):
        response = post(stt, source="auto")
    assert response.status_code == 422
    line = next(record.getMessage() for record in caplog.records if "request_rejected" in record.getMessage())
    assert "code=unsupported_language" in line and "language=yo" in line and "role=source" in line
    assert "secret" not in caplog.text
