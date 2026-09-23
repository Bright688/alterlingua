package com.alterlingua.app.learning.pronunciation

import com.alterlingua.app.keyboard.VoiceFiles
import com.alterlingua.app.keyboard.VoiceRecorder
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.MasteryStatus
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.UnitKey
import com.alterlingua.app.learning.engine.UnitType
import com.alterlingua.app.learning.lessons.CardKind
import com.alterlingua.app.learning.lessons.LessonCard
import com.alterlingua.app.learning.lessons.LessonContext
import com.alterlingua.app.learning.lessons.LessonLanguages
import com.alterlingua.app.learning.map.InMemoryLanguageMapStore
import com.alterlingua.app.learning.map.LanguageMapService
import com.alterlingua.app.learning.map.MasteryCalculator
import com.alterlingua.app.share.Speaker
import com.alterlingua.app.translation.VoiceApi
import com.alterlingua.app.translation.VoiceFailure
import com.alterlingua.app.translation.VoiceResult
import com.alterlingua.app.translation.VoiceTranslation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PronunciationPracticeTest {
    @get:Rule val folder = TemporaryFolder()

    private class FakeRecorder : VoiceRecorder {
        var startOk = true
        var stopOk = true
        var level = 12_000
        var started = 0
        var cancelled = 0
        override fun start(file: File): Boolean { started++; if (startOk) file.writeBytes(ByteArray(2_000) { 1 }); return startOk }
        override fun amplitude() = level
        override fun stop() = stopOk
        override fun cancel() { cancelled++ }
    }

    private class FakeApi : VoiceApi {
        val calls = mutableListOf<List<String>>()
        var existedDuringCall = false
        var answer: VoiceResult = VoiceResult.Success(VoiceTranslation("fr", "acompte", "en", "deposit"))
        override suspend fun translate(audio: File, contentType: String, source: String, target: String): VoiceResult {
            calls += listOf(contentType, source, target)
            existedDuringCall = audio.exists()
            return answer
        }
    }

    private class FakeSpeaker : Speaker {
        var voice = true
        val spoken = mutableListOf<Pair<String, String>>()
        var stopped = 0
        var done: (() -> Unit)? = null
        override suspend fun canSpeak(language: Language) = voice
        override fun speak(text: String, language: Language, onDone: () -> Unit): Boolean { spoken += text to language.code; done = onDone; return voice }
        override suspend fun synthesizeToFile(text: String, language: Language, file: java.io.File): Boolean {
            if (!voice) return false
            file.writeBytes(text.toByteArray())
            return true
        }
        override fun stop() { stopped++ }
        override fun shutdown() = Unit
    }

    private val recorder = FakeRecorder()
    private val api = FakeApi()
    private val speaker = FakeSpeaker()
    private val mapStore = InMemoryLanguageMapStore()
    private var now = 1_000_000L
    private val map = LanguageMapService(mapStore, clock = { now })
    private var permission = true
    private var learning: Language = Languages.French
    private val cache by lazy { folder.newFolder("pron") }

    private fun card(term: String = "acompte", language: String = "fr", type: UnitType = UnitType.WORD) = LessonCard(
        UnitKey(language, term, type), term, CardKind.WORD_CARD, null, null, LessonContext(1, InteractionKind.INCOMING_MESSAGE, 0, 0, emptyList()), MasteryStatus.LEARNING,
    )

    private fun TestScope.practice() = PronunciationPractice(
        scope = backgroundScope, recorder = recorder, files = VoiceFiles(cache), api = api, speaker = speaker, map = map,
        languages = { LessonLanguages(learning, Languages.English) }, hasPermission = { permission }, clock = { now },
    )

    private fun files() = cache.listFiles()?.size ?: 0

    /** Bind, record for [millis], stop. */
    private fun TestScope.attempt(p: PronunciationPractice, millis: Long = 1_200) {
        p.startRecording()
        advanceTimeBy(millis)
        runCurrent()
        p.stopRecording()
        runCurrent()
    }

    private fun PronunciationPractice.phase() = uiState.value.phase
    private suspend fun item(term: String = "acompte", language: String = "fr") = map.item(UnitKey(language, term, UnitType.WORD))

    // ---- Listen ----

    @Test fun listen_readsTheWordInTheLanguageBeingLearned() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        assertTrue(p.uiState.value.canListen)
        p.listen()
        assertEquals(listOf("acompte" to "fr"), speaker.spoken)
        assertTrue(p.uiState.value.listening)
        speaker.done!!.invoke()
        assertFalse(p.uiState.value.listening)
    }

    @Test fun listen_isNotOffered_whenThePhoneHasNoVoice() = runTest {
        speaker.voice = false
        val p = practice()
        p.bind(card()); runCurrent()
        assertFalse(p.uiState.value.canListen)
        p.listen()
        assertTrue(speaker.spoken.isEmpty())
    }

    @Test fun listen_followsTheLearningLanguage_notAlwaysFrench() = runTest {
        for (language in Languages.supported) {
            learning = language
            speaker.spoken.clear()
            val p = practice()
            p.bind(card(term = "x", language = language.code)); runCurrent()
            p.listen()
            assertEquals(language.code, speaker.spoken.single().second)
        }
    }

    // ---- Repeat, record, assess ----

    @Test fun sayingTheWordWell_givesGood_andRecordsAGoodPractice() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        attempt(p)
        val phase = p.phase() as PracticePhase.Feedback
        assertEquals(PronunciationVerdict.GOOD, phase.feedback.verdict)
        val saved = item()!!
        assertEquals(1, saved.pronunciationTries)
        assertEquals(1, saved.pronunciationGood)
    }

    @Test fun theRecordingIsSentInTheLanguageBeingLearned_toBeRecognised() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        attempt(p)
        assertEquals(listOf("audio/mp4", "fr", "en"), api.calls.single())
        assertTrue("the recording existed while it was sent", api.existedDuringCall)
    }

    @Test fun theRecordingIsDeletedAsSoonAsTheAnswerArrives() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        attempt(p)
        assertEquals(0, files())
    }

    @Test fun sayingSomethingElse_givesTryAgain_showsWhatWasHeard_andCountsATryWithoutAPoint() = runTest {
        api.answer = VoiceResult.Success(VoiceTranslation("fr", "paiement", "en", "payment"))
        val p = practice()
        p.bind(card()); runCurrent()
        attempt(p)
        val feedback = (p.phase() as PracticePhase.Feedback).feedback
        assertEquals(PronunciationVerdict.TRY_AGAIN, feedback.verdict)
        assertEquals("paiement", feedback.heard)
        assertEquals(1, item()!!.pronunciationTries)
        assertEquals(0, item()!!.pronunciationGood)
    }

    @Test fun aNearMiss_isNearly_notGood() = runTest {
        api.answer = VoiceResult.Success(VoiceTranslation("fr", "fournisseure", "en", "x"))
        val p = practice()
        p.bind(card("fournisseur")); runCurrent()
        attempt(p)
        assertEquals(PronunciationVerdict.CLOSE, (p.phase() as PracticePhase.Feedback).feedback.verdict)
        assertEquals(0, item("fournisseur")!!.pronunciationGood)
    }

    @Test fun aPhraseIsPractisedAsAWhole() = runTest {
        api.answer = VoiceResult.Success(VoiceTranslation("fr", "Avant midi.", "en", "before noon"))
        val p = practice()
        p.bind(card("avant midi", type = UnitType.PHRASE)); runCurrent()
        attempt(p)
        assertEquals(PronunciationVerdict.GOOD, (p.phase() as PracticePhase.Feedback).feedback.verdict)
        assertEquals(1, map.item(UnitKey("fr", "avant midi", UnitType.PHRASE))!!.pronunciationGood)
    }

    @Test fun otherLanguages_areAssessedWithTheirOwnRules() = runTest {
        learning = Languages.Japanese
        api.answer = VoiceResult.Success(VoiceTranslation("ja", "ミツモリ", "en", "quotation"))
        val p = practice()
        p.bind(card("みつもり", "ja")); runCurrent()
        attempt(p)
        assertEquals(PronunciationVerdict.GOOD, (p.phase() as PracticePhase.Feedback).feedback.verdict)
        assertEquals("ja", api.calls.single()[1])
        assertNull(item("みつもり", "fr"))
    }

    // ---- what is NOT recorded as practice ----

    @Test fun silence_isNotUploaded_andNotRecordedAsPractice() = runTest {
        recorder.level = 0
        val p = practice()
        p.bind(card()); runCurrent()
        attempt(p)
        assertEquals(PronunciationVerdict.NOT_HEARD, (p.phase() as PracticePhase.Feedback).feedback.verdict)
        assertTrue(api.calls.isEmpty())
        assertNull(item())
        assertEquals(0, files())
    }

    @Test fun aTooShortRecording_isNotUploaded() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        p.startRecording()
        advanceTimeBy(200); runCurrent()
        p.stopRecording()
        assertEquals(PronunciationVerdict.NOT_HEARD, (p.phase() as PracticePhase.Feedback).feedback.verdict)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun nothingRecognised_isNotHeard_andNotRecorded() = runTest {
        api.answer = VoiceResult.Failure(VoiceFailure.NO_SPEECH)
        val p = practice()
        p.bind(card()); runCurrent()
        attempt(p)
        assertEquals(PronunciationVerdict.NOT_HEARD, (p.phase() as PracticePhase.Feedback).feedback.verdict)
        assertNull(item())
    }

    @Test fun aServiceProblem_isShownAsAProblem_notAsABadAttempt_andNothingIsRecorded() = runTest {
        api.answer = VoiceResult.Failure(VoiceFailure.OFFLINE)
        val p = practice()
        p.bind(card()); runCurrent()
        attempt(p)
        val phase = p.phase() as PracticePhase.Problem
        assertEquals(VoiceFailure.OFFLINE, phase.failure)
        assertTrue(phase.canRetry)
        assertNull(item())
        assertEquals(0, files())
    }

    @Test fun whenOnlyThePartialTranscriptIsAvailable_itIsStillJudged() = runTest {
        api.answer = VoiceResult.Failure(VoiceFailure.PARTIAL, heard = "acompte")
        val p = practice()
        p.bind(card()); runCurrent()
        attempt(p)
        assertEquals(PronunciationVerdict.GOOD, (p.phase() as PracticePhase.Feedback).feedback.verdict)
    }

    // ---- microphone ----

    @Test fun withoutTheMicrophonePermission_itAsks_andNothingIsRecorded() = runTest {
        permission = false
        val p = practice()
        p.bind(card()); runCurrent()
        p.startRecording()
        assertEquals(PracticePhase.NeedsPermission, p.phase())
        assertEquals(0, recorder.started)
        permission = true
        p.permissionAnswered(true)
        assertTrue(p.phase() is PracticePhase.Recording)
    }

    @Test fun refusingThePermission_returnsToIdle() = runTest {
        permission = false
        val p = practice()
        p.bind(card()); runCurrent()
        p.startRecording()
        p.permissionAnswered(false)
        assertEquals(PracticePhase.Idle, p.phase())
    }

    @Test fun aMicrophoneThatCannotStart_isAProblem() = runTest {
        recorder.startOk = false
        val p = practice()
        p.bind(card()); runCurrent()
        p.startRecording()
        assertEquals(VoiceFailure.MIC_UNAVAILABLE, (p.phase() as PracticePhase.Problem).failure)
        assertEquals(0, files())
    }

    @Test fun recordingStopsByItself_atTheTimeLimit() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        p.startRecording()
        advanceTimeBy(PronunciationPractice.MAX_MILLIS + 500); runCurrent()
        assertFalse(p.phase() is PracticePhase.Recording)
    }

    @Test fun startingListenWhileRecording_isNotPossible_andRecordingStopsTheVoice() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        p.listen()
        p.startRecording()
        assertFalse(p.uiState.value.listening)
        assertTrue(speaker.stopped >= 1)
    }

    // ---- moving on, leaving ----

    @Test fun movingToTheNextCard_startsOver_andDeletesAnyRecording() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        p.startRecording()
        advanceTimeBy(300); runCurrent()
        p.bind(card("devis")); runCurrent()
        assertEquals(PracticePhase.Idle, p.phase())
        assertEquals("devis", p.uiState.value.term)
        assertEquals(0, files())
        assertTrue(recorder.cancelled >= 1)
    }

    @Test fun leavingTheScreen_stopsRecording_andDeletesTheAudio() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        p.startRecording()
        advanceTimeBy(300); runCurrent()
        assertEquals(1, files())
        p.release()
        assertEquals(0, files())
        assertEquals(PracticePhase.Idle, p.phase())
    }

    // ---- the mastery signal ----

    @Test fun goodPractice_addsToTheScore_withoutMakingAnythingMastered() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        repeat(6) { attempt(p) }
        val saved = item()!!
        assertEquals(6, saved.pronunciationGood)
        val evaluation = MasteryCalculator().evaluate(saved.evidence, now)
        assertEquals("the points are capped", 9.0, evaluation.factors.single { it.kind.name == "PRONUNCIATION" }.points, 0.0)
        assertTrue("practice alone is never mastery", evaluation.state != MasteryStatus.MASTERED && evaluation.state != MasteryStatus.FAMILIAR)
    }

    @Test fun aMissedAttempt_neverLowersTheScore() = runTest {
        api.answer = VoiceResult.Success(VoiceTranslation("fr", "compte", "en", "x"))
        val p = practice()
        p.bind(card()); runCurrent()
        val before = MasteryCalculator().evaluate(com.alterlingua.app.learning.map.MasteryEvidence(exposures = 4, lastSeenMillis = now), now).score
        repeat(3) { attempt(p) }
        val after = MasteryCalculator().evaluate(item()!!.evidence.copy(exposures = 4), now).score
        assertEquals(before, after, 0.0)
    }

    @Test fun thePracticeSavesOnlyCounts_noTranscriptNoAudio() = runTest {
        val p = practice()
        p.bind(card()); runCurrent()
        attempt(p)
        val saved = item()!!
        assertEquals("acompte", saved.displayForm)
        assertEquals(0, files())
    }
}
