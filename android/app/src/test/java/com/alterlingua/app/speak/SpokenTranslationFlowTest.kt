package com.alterlingua.app.speak

import com.alterlingua.app.keyboard.VoiceFiles
import com.alterlingua.app.keyboard.VoicePlayer
import com.alterlingua.app.keyboard.VoiceRecorder
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.TranslationInteraction
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SpokenTranslationFlowTest {
    @get:Rule val folder = TemporaryFolder()

    private class FakeRecorder : VoiceRecorder {
        var startOk = true
        var level = 12_000
        var cancelled = 0
        override fun start(file: File): Boolean { if (startOk) file.writeBytes(ByteArray(3_000) { 2 }); return startOk }
        override fun amplitude() = level
        override fun stop() = true
        override fun cancel() { cancelled++ }
    }

    private class FakePlayer : VoicePlayer {
        val played = mutableListOf<File>()
        var stopped = 0
        var done: (() -> Unit)? = null
        var ok = true
        override fun play(file: File, onDone: () -> Unit): Boolean { played += file; done = onDone; return ok }
        override fun stop() { stopped++ }
    }

    /** Transcribes and translates only; no speech comes back from the backend (CLAUDE.md 23). */
    private class FakeApi : VoiceApi {
        val calls = mutableListOf<List<String>>()
        var recordingExisted = false
        var answer: (String, String) -> VoiceResult = { source, target ->
            VoiceResult.Success(VoiceTranslation(source, "Are you coming tomorrow?", target, "translation in $target"))
        }
        override suspend fun translate(audio: File, contentType: String, source: String, target: String): VoiceResult {
            calls += listOf(contentType, source, target)
            recordingExisted = audio.exists()
            return answer(source, target)
        }
    }

    /** The on-device voice: writes the spoken text as bytes, so a test can tell what would have been said. */
    private class FakeSpeaker : Speaker {
        var voice = true
        var synthesisOk = true
        val synthesized = mutableListOf<Pair<String, String>>()
        override suspend fun canSpeak(language: Language) = voice
        override fun speak(text: String, language: Language, onDone: () -> Unit): Boolean = false
        override suspend fun synthesizeToFile(text: String, language: Language, file: File): Boolean {
            synthesized += text to language.code
            if (!synthesisOk) return false
            file.writeBytes(text.toByteArray())
            return true
        }
        override fun stop() = Unit
        override fun shutdown() = Unit
    }

    private val recorder = FakeRecorder()
    private val player = FakePlayer()
    private val api = FakeApi()
    private val speaker = FakeSpeaker()
    private val heard = mutableListOf<TranslationInteraction>()
    private var permission = true
    private var spoken: Language = Languages.English
    private var target: Language = Languages.Spanish
    private var now = 10_000_000L
    private val recordingsDir by lazy { folder.newFolder("rec") }
    private val spokenDir by lazy { folder.newFolder("spoken") }

    private fun TestScope.flow() = SpokenTranslationFlow(
        scope = backgroundScope, recorder = recorder, recordings = VoiceFiles(recordingsDir), spokenFiles = SpokenAudioFiles(spokenDir) { now },
        player = player, api = api, speaker = speaker, languages = { SpokenLanguages(spoken, target) }, hasPermission = { permission }, learning = { heard += it },
    ).also { it.open(); runCurrent() }

    private fun TestScope.record(f: SpokenTranslationFlow, millis: Long = 1_500) {
        f.start()
        advanceTimeBy(millis); runCurrent()
        f.stop()
        runCurrent()
    }

    private fun recordings() = recordingsDir.listFiles()?.size ?: 0
    private fun generated() = spokenDir.listFiles()?.size ?: 0
    private fun SpokenTranslationFlow.phase() = uiState.value.phase
    private fun SpokenTranslationFlow.result() = (phase() as SpeakPhase.Result).value

    // ---- the flow ----

    @Test fun englishVoiceToSpanishSpeech_endToEnd() = runTest {
        val f = flow()
        assertEquals(Languages.English, f.uiState.value.spoken)
        assertEquals(Languages.Spanish, f.uiState.value.target)
        record(f)
        val r = f.result()
        assertEquals("translation in es", r.translation)
        assertEquals("Are you coming tomorrow?", r.transcript)
        assertEquals(Languages.Spanish, r.target)
        assertEquals(listOf("audio/mp4", "en", "es"), api.calls.single())
        assertEquals(listOf("translation in es" to "es"), speaker.synthesized)
        assertArrayEquals("translation in es".toByteArray(), r.audioFile.readBytes())
        assertTrue(r.audioFile.name.endsWith(".wav"))
        assertEquals("audio/wav", r.audioType)
    }

    @Test fun anyTargetLanguageCanBeChosen_andIsTheOneAskedFor() = runTest {
        for (choice in Languages.supported.filter { it != spoken }) {
            api.calls.clear()
            val f = flow()
            f.chooseTarget(choice)
            record(f)
            assertEquals(choice.code, api.calls.single()[2])
            assertEquals(choice, f.result().target)
            f.release()
        }
    }

    @Test fun theSpokenLanguageIsTheUsersOwn_notAlwaysEnglish() = runTest {
        for (own in Languages.supported) {
            api.calls.clear()
            spoken = own
            target = if (own == Languages.French) Languages.English else Languages.French
            val f = flow()
            record(f)
            assertEquals(own.code, api.calls.single()[1])
            f.release()
        }
    }

    @Test fun theTargetCannotBeChangedWhileRecordingOrWorking() = runTest {
        val f = flow()
        f.start()
        f.chooseTarget(Languages.Japanese)
        assertEquals(Languages.Spanish, f.uiState.value.target)
    }

    // ---- listen before sharing ----

    @Test fun listen_playsTheGeneratedSpeech_andTogglesOff() = runTest {
        val f = flow(); record(f)
        f.listen()
        assertEquals(listOf(f.result().audioFile), player.played)
        assertTrue(f.result().playing)
        f.listen()
        assertFalse(f.result().playing)
        assertTrue(player.stopped >= 1)
    }

    @Test fun listen_endsWhenThePlaybackEnds() = runTest {
        val f = flow(); record(f)
        f.listen()
        player.done!!.invoke()
        assertFalse(f.result().playing)
    }

    @Test fun nothingIsSharedOrLearnedFromUntilTheUserTapsShare() = runTest {
        val f = flow(); record(f)
        f.listen()
        assertTrue(heard.isEmpty())
        val shared = f.prepareShare()
        assertNotNull(shared)
        assertEquals(listOf(TranslationInteraction(InteractionKind.OUTGOING_VOICE, "translation in es", "es")), heard)
    }

    @Test fun sharingStopsPlayback_andLeavesTheFileInPlaceForTheShareSheet() = runTest {
        val f = flow(); record(f)
        f.listen()
        val shared = f.prepareShare()!!
        assertFalse(f.result().playing)
        assertTrue(shared.audioFile.exists())
    }

    @Test fun withNoResultYet_thereIsNothingToShare() = runTest {
        assertNull(flow().prepareShare())
    }

    // ---- temporary audio ----

    @Test fun theRecordingIsDeletedAsSoonAsItIsTranslated_theGeneratedSpeechIsKept() = runTest {
        val f = flow(); record(f)
        assertTrue("the recording existed while it was sent", api.recordingExisted)
        assertEquals(0, recordings())
        assertEquals(1, generated())
    }

    @Test fun recordingAgain_deletesTheGeneratedSpeech() = runTest {
        val f = flow(); record(f)
        f.recordAgain()
        assertEquals(0, generated())
        assertEquals(SpeakPhase.Idle, f.phase())
    }

    @Test fun leavingTheScreen_deletesEverything() = runTest {
        val f = flow(); record(f)
        f.release()
        assertEquals(0, generated()); assertEquals(0, recordings())
    }

    @Test fun leavingWhileRecording_stopsAndDeletesTheRecording() = runTest {
        val f = flow()
        f.start(); advanceTimeBy(300); runCurrent()
        assertEquals(1, recordings())
        f.release()
        assertEquals(0, recordings())
        assertTrue(recorder.cancelled >= 1)
    }

    @Test fun oldGeneratedFilesAreSweptWhenTheScreenOpens() = runTest {
        val old = File(spokenDir, "voice-old.wav").also { it.writeBytes(byteArrayOf(1)); it.setLastModified(now - 2 * 60 * 60 * 1000) }
        val fresh = File(spokenDir, "voice-new.wav").also { it.writeBytes(byteArrayOf(1)); it.setLastModified(now) }
        flow()
        assertFalse(old.exists()); assertTrue(fresh.exists())
    }

    // ---- problems ----

    @Test fun silence_isNotUploaded() = runTest {
        recorder.level = 0
        val f = flow(); record(f)
        assertEquals(VoiceFailure.NO_SPEECH, (f.phase() as SpeakPhase.Problem).failure)
        assertTrue(api.calls.isEmpty()); assertEquals(0, recordings())
    }

    @Test fun aTooShortRecording_isNotUploaded() = runTest {
        val f = flow()
        f.start(); advanceTimeBy(200); runCurrent(); f.stop()
        assertEquals(VoiceFailure.TOO_SHORT, (f.phase() as SpeakPhase.Problem).failure)
        assertTrue(api.calls.isEmpty())
    }

    @Test fun aRetryableBackendFailure_keepsTheRecording_andTryAgainUsesIt() = runTest {
        var first = true
        api.answer = { s, t -> if (first) { first = false; VoiceResult.Failure(VoiceFailure.OFFLINE) } else VoiceResult.Success(VoiceTranslation(s, "hi", t, "hola")) }
        val f = flow(); record(f)
        val problem = f.phase() as SpeakPhase.Problem
        assertTrue(problem.canRetry)
        assertEquals(1, recordings())
        f.retry(); runCurrent()
        assertEquals("hola", f.result().translation)
        assertEquals(0, recordings())
        assertTrue("the second request used the same recording", api.recordingExisted)
    }

    @Test fun aTargetWithNoVoiceOnThisPhone_isNotRetryable_andDeletesTheRecording() = runTest {
        speaker.voice = false
        val f = flow(); record(f)
        assertEquals(VoiceFailure.NO_VOICE_FOR_TARGET, (f.phase() as SpeakPhase.Problem).failure)
        assertFalse((f.phase() as SpeakPhase.Problem).canRetry)
        assertEquals(0, recordings()); assertEquals(0, generated())
        assertTrue("nothing is generated once there is no voice to speak it", speaker.synthesized.isEmpty())
    }

    @Test fun aSynthesisFailure_isRetryable_withoutTouchingTheNetworkAgain() = runTest {
        speaker.synthesisOk = false
        val f = flow(); record(f)
        val problem = f.phase() as SpeakPhase.Problem
        assertEquals(VoiceFailure.TRANSLATION_FAILED, problem.failure)
        assertTrue(problem.canRetry)
        // the recording is already gone: retrying speaks the same translated text again, it does not re-upload
        assertEquals(0, recordings())
        assertEquals(1, api.calls.size)

        speaker.synthesisOk = true
        f.retry(); runCurrent()
        assertEquals("translation in es", f.result().translation)
        assertEquals("retry after a synthesis failure must not call the backend again", 1, api.calls.size)
        assertEquals(1, generated())
    }

    @Test fun theMicrophonePermissionIsAskedInPlace() = runTest {
        permission = false
        val f = flow()
        f.start()
        assertEquals(SpeakPhase.NeedsPermission, f.phase())
        permission = true
        f.permissionAnswered(true)
        assertTrue(f.phase() is SpeakPhase.Recording)
    }

    @Test fun refusingThePermissionReturnsToIdle() = runTest {
        permission = false
        val f = flow()
        f.start(); f.permissionAnswered(false)
        assertEquals(SpeakPhase.Idle, f.phase())
    }

    @Test fun aMicrophoneThatCannotStart_isAProblem() = runTest {
        recorder.startOk = false
        val f = flow(); f.start()
        assertEquals(VoiceFailure.MIC_UNAVAILABLE, (f.phase() as SpeakPhase.Problem).failure)
    }
}
