"""For a shared voice note the recording is transcribed a few ways at once (plain with automatic detection, and levelled and forced
to each likely language) and the result the engine is most sure of wins. Found on the live service: a French voice note was taken
for Russian and refused, and the words that were recognised were still not right."""

import logging

import pytest

from app.core.errors import ProviderError, ProviderUnavailableError
from app.speech.provider import SpeechCapabilities, SpeechResult, SpeechToTextProvider
from app.speech.schemas import AudioTranslateOptions
from app.translation.fake_provider import SAMPLE_PHRASES
from tests.audio_helpers import make_wav
from tests.conftest import make_client

FR = SAMPLE_PHRASES["fr"]
EN = SAMPLE_PHRASES["en"]
ES = SAMPLE_PHRASES["es"]


class Scripted(SpeechToTextProvider):
    """Answers per requested language (None means automatic detection); records what it was asked, and with which file."""

    name = "scripted"

    def __init__(self, script: dict):
        self.script = script
        self.asked: list[tuple[str | None, str, str]] = []  # (language, file name, content type)

    def capabilities(self):
        return SpeechCapabilities(frozenset({"en", "fr", "es", "de", "it", "nl", "zh", "ja"}), True)

    async def transcribe(self, request):
        self.asked.append((request.language, request.audio_path.name, request.content_type))
        outcome = self.script[request.language]
        if isinstance(outcome, Exception):
            raise outcome
        return outcome


def leveller(created: list):
    """A stand-in for the levelling step: writes a small file next to the original and records it."""

    async def level(path):
        out = path.with_name(path.stem + "-leveled.wav")
        out.write_bytes(b"RIFFleveled")
        created.append(out)
        return out

    return level


def post(stt, *, normaliser=None, **form):
    form.setdefault("target", "en")
    client = make_client(stt=stt)
    if normaliser is not None:
        client.app.state.speech_service._normaliser = normaliser
    return client.post("/v1/audio/translate", files={"audio": ("clip.ogg", make_wav(), "audio/wav")}, data=form)


def languages(stt):
    return [language for language, _, _ in stt.asked]


# ---- the failures that were found --------------------------------------------------------------------------------------

def test_a_note_taken_for_an_unsupported_language_is_recovered_by_the_levelled_forced_attempt():
    created = []
    stt = Scripted({
        None: SpeechResult("мне нужно", "ru", -1.6),  # a French note taken for Russian
        "fr": SpeechResult(FR, "fr", -0.3),
        "en": SpeechResult("nonsense forced into english", "en", -1.5),
    })
    response = post(stt, source="auto", hints="fr,en", normaliser=leveller(created))
    assert response.status_code == 200
    body = response.json()
    assert (body["source_language"], body["transcript"], body["translation"]) == ("fr", FR, EN)
    assert sorted(languages(stt), key=str) == [None, "en", "fr"]


def test_the_plain_attempt_gets_the_original_file_and_the_forced_ones_the_levelled_copy():
    created = []
    stt = Scripted({None: SpeechResult(FR, "fr", -0.2), "fr": SpeechResult(FR, "fr", -0.2), "en": SpeechResult("x", "en", -0.9)})
    post(stt, source="auto", hints="fr,en", normaliser=leveller(created))
    by_language = {language: (name, content_type) for language, name, content_type in stt.asked}
    assert not by_language[None][0].endswith("-leveled.wav")  # the original, as uploaded
    for code in ("fr", "en"):
        assert by_language[code][0].endswith("-leveled.wav") and by_language[code][1] == "audio/wav"


def test_the_levelled_copy_is_deleted_as_soon_as_it_has_been_used():
    created = []
    stt = Scripted({None: SpeechResult(FR, "fr", -0.2), "fr": SpeechResult(FR, "fr", -0.2)})
    post(stt, source="auto", hints="fr", normaliser=leveller(created))
    assert created and not any(path.exists() for path in created)


def test_when_the_recording_cannot_be_levelled_the_forced_attempts_use_the_original_file():
    async def cannot(path):
        return None

    stt = Scripted({None: SpeechResult("xx", "yo", -1.6), "fr": SpeechResult(FR, "fr", -0.3)})
    body = post(stt, source="auto", hints="fr", normaliser=cannot).json()
    assert body["source_language"] == "fr"
    assert all(not name.endswith("-leveled.wav") for _, name, _ in stt.asked)


def test_without_hints_there_is_one_plain_attempt_as_before():
    stt = Scripted({None: SpeechResult("mbote", "yo", -1.6)})
    response = post(stt, source="auto")
    assert response.status_code == 422 and response.json()["error"]["code"] == "unsupported_language"
    assert languages(stt) == [None]


def test_an_explicit_source_language_is_never_second_guessed():
    stt = Scripted({"fr": SpeechResult(FR, "fr", -0.2)})
    assert post(stt, source="fr", hints="en").status_code == 200 and languages(stt) == ["fr"]


# ---- how the attempts are judged --------------------------------------------------------------------------------------------

def test_a_forced_attempt_must_clearly_beat_automatic_detection_to_replace_it():
    # A gain of 0.02 is noise: measured on degraded French, an attempt that won by that little was actually the worse one.
    stt = Scripted({None: SpeechResult(EN, "fr", -0.20), "fr": SpeechResult(FR, "fr", -0.18)})
    body = post(stt, source="auto", target="es", hints="fr", normaliser=leveller([])).json()
    assert body["transcript"] == EN


def test_a_clearly_more_confident_forced_attempt_replaces_automatic_detection():
    stt = Scripted({None: SpeechResult(EN, "fr", -0.58), "fr": SpeechResult(FR, "fr", -0.30)})
    body = post(stt, source="auto", target="es", hints="fr", normaliser=leveller([])).json()
    assert body["transcript"] == FR


def test_speech_in_a_language_that_really_is_unsupported_is_still_refused_not_turned_into_nonsense():
    stt = Scripted({
        None: SpeechResult("uma frase em portugues", "pt", -0.4),
        "fr": SpeechResult("garbled text forced into french", "fr", -1.7),
        "en": SpeechResult("garbled text forced into english", "en", -1.4),
    })
    response = post(stt, source="auto", hints="fr,en", normaliser=leveller([]))
    assert response.status_code == 422 and response.json()["error"]["code"] == "unsupported_language"


def test_a_supported_language_outside_the_hints_is_kept_when_forcing_the_hints_gives_nonsense():
    stt = Scripted({
        None: SpeechResult(ES, "es", -0.15),
        "fr": SpeechResult("forced nonsense", "fr", -0.9),
        "en": SpeechResult("forced nonsense", "en", -0.8),
    })
    body = post(stt, source="auto", hints="fr,en", normaliser=leveller([])).json()
    assert body["source_language"] == "es" and body["transcript"] == ES


def test_the_most_confident_of_several_forced_attempts_wins():
    stt = Scripted({None: SpeechResult("xx", "yo", -1.6), "fr": SpeechResult(FR, "fr", -0.8), "en": SpeechResult(EN, "en", -0.2)})
    assert post(stt, source="auto", target="fr", hints="fr,en", normaliser=leveller([])).json()["source_language"] == "en"


def test_when_no_confidence_is_reported_automatic_detection_is_used():
    stt = Scripted({None: SpeechResult(FR, "fr"), "fr": SpeechResult(EN, "fr"), "en": SpeechResult(EN, "en")})
    body = post(stt, source="auto", hints="fr,en", normaliser=leveller([])).json()
    assert body["transcript"] == FR


def test_an_attempt_that_fails_is_skipped_and_the_others_still_count():
    stt = Scripted({None: ProviderError("boom"), "fr": ProviderUnavailableError("down"), "en": SpeechResult(EN, "en", -0.3)})
    body = post(stt, source="auto", target="fr", hints="fr,en", normaliser=leveller([])).json()
    assert body["source_language"] == "en"


def test_when_every_attempt_fails_the_first_failure_is_reported():
    stt = Scripted({None: ProviderUnavailableError("down"), "fr": ProviderUnavailableError("down")})
    response = post(stt, source="auto", hints="fr", normaliser=leveller([]))
    assert response.status_code == 503 and response.json()["error"]["code"] == "provider_unavailable"


def test_at_most_two_hinted_languages_are_tried_and_unsupported_ones_are_not():
    stt = Scripted({None: SpeechResult(FR, "fr", -0.2), "fr": SpeechResult(FR, "fr", -0.2), "en": SpeechResult(EN, "en", -0.2), "es": SpeechResult(ES, "es", -0.2)})
    post(stt, source="auto", hints="pt,fr,en,es", normaliser=leveller([]))  # pt is not a catalogue language
    assert sorted(languages(stt), key=str) == [None, "en", "fr"]


# ---- telling the user when the recording was unclear ---------------------------------------------------------------------------

@pytest.mark.parametrize(("confidence", "clarity"), [(-0.1, "clear"), (-0.49, "clear"), (-0.51, "unclear"), (-0.9, "unclear")])
def test_the_answer_says_whether_the_recording_was_clear(confidence, clarity):
    stt = Scripted({None: SpeechResult(FR, "fr", confidence), "fr": SpeechResult(FR, "fr", confidence)})
    assert post(stt, source="auto", hints="fr", normaliser=leveller([])).json()["clarity"] == clarity


def test_no_clarity_is_reported_when_the_engine_does_not_say_how_sure_it_is():
    stt = Scripted({None: SpeechResult(FR, "fr")})
    assert "clarity" not in post(stt, source="auto").json()


# ---- the hints field ----------------------------------------------------------------------------------------------------------

def test_hints_are_cleaned_deduplicated_and_limited_to_three():
    assert AudioTranslateOptions(target="en", hints=" fr, EN ,!!,fr-CA,es,de,it").hints == ["fr", "en", "es"]


def test_hints_default_to_none_and_the_field_is_optional_on_the_route():
    assert AudioTranslateOptions(target="en").hints == []
    assert post(Scripted({None: SpeechResult(FR, "fr", -0.2)}), source="auto").status_code == 200


# ---- what the server logs ---------------------------------------------------------------------------------------------------

def test_a_rejected_request_logs_its_kind_and_the_language_code_but_no_content(caplog):
    stt = Scripted({None: SpeechResult("a secret sentence nobody may log", "yo", -1.0)})
    with caplog.at_level(logging.INFO):
        response = post(stt, source="auto")
    assert response.status_code == 422
    line = next(record.getMessage() for record in caplog.records if "request_rejected" in record.getMessage())
    assert "code=unsupported_language" in line and "language=yo" in line and "role=source" in line
    assert "secret" not in caplog.text


def test_the_best_of_log_line_names_the_attempts_and_the_winner_but_no_words(caplog):
    stt = Scripted({None: SpeechResult("a private sentence nobody may log", "ru", -1.6), "fr": SpeechResult(FR, "fr", -0.3)})
    with caplog.at_level(logging.INFO):
        post(stt, source="auto", hints="fr", normaliser=leveller([]))
    line = next(record.getMessage() for record in caplog.records if "speech_best_of" in record.getMessage())
    assert "chosen=fr" in line and "auto_language=ru" in line and "leveled=True" in line
    assert "private" not in caplog.text and FR not in caplog.text
