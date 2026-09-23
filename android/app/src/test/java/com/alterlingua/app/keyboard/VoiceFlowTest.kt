package com.alterlingua.app.keyboard

import android.text.InputType
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.translation.VoiceApi
import com.alterlingua.app.translation.VoiceFailure
import com.alterlingua.app.translation.VoiceResult
import com.alterlingua.app.translation.VoiceTranslation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

private class FakeRecorder : VoiceRecorder {
    var canStart = true
    var stopWorks = true
    var level = 12_000
    var started = 0
    var cancelled = 0
    var file: File? = null

    override fun start(file: File): Boolean {
        if (!canStart) return false
        started++
        this.file = file
        file.writeBytes(ByteArray(2_000) { 1 })
        return true
    }

    override fun amplitude() = level
    override fun stop() = stopWorks
    override fun cancel() { cancelled++ }
}

private class FakePlayer : VoicePlayer {
    var playing: File? = null
    var onDone: (() -> Unit)? = null
    var stops = 0
    override fun play(file: File, onDone: () -> Unit): Boolean {
        playing = file
        this.onDone = onDone
        return true
    }

    override fun stop() { stops++; playing = null }
}

private class Call(val fileExisted: Boolean, val type: String, val source: String, val target: String)

private class FakeVoiceApi(var handler: suspend (Call) -> VoiceResult) : VoiceApi {
    val calls = mutableListOf<Call>()
    var cancelled = false
    override suspend fun translate(audio: File, contentType: String, source: String, target: String): VoiceResult {
        val call = Call(audio.exists() && audio.length() > 0, contentType, source, target)
        calls += call
        return try {
            handler(call)
        } catch (e: CancellationException) {
            cancelled = true
            throw e
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceFlowTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val plainText = EditorTraits(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
    private val answers = mapOf("es" to "¿Vienes mañana?", "fr" to "Tu viens demain ?", "ja" to "明日来ますか？", "de" to "Kommst du morgen?", "zh" to "你明天来吗？")

    private fun answer(call: Call) =
        VoiceResult.Success(VoiceTranslation(call.source, "Are you coming tomorrow?", call.target, answers.getValue(call.target)))

    private class Setup(
        val flow: VoiceFlow,
        val recorder: FakeRecorder,
        val player: FakePlayer,
        val api: FakeVoiceApi,
        val files: VoiceFiles,
        val dir: File,
        val languages: Array<VoiceLanguages>,
        val inserted: MutableList<String>,
        val states: MutableList<VoiceUiState>,
        val permission: BooleanArray,
    ) {
        fun leftovers() = dir.listFiles().orEmpty().toList()
    }

    private fun TestScope.setup(
        traits: EditorTraits = plainText,
        spoken: Language = Languages.English,
        target: Language = Languages.Spanish,
        permission: Boolean = true,
        api: FakeVoiceApi = FakeVoiceApi { answer(it) },
        maxRecordingMillis: Long = 60_000,
    ): Setup {
        val dir = folder.newFolder()
        val recorder = FakeRecorder()
        val player = FakePlayer()
        val files = VoiceFiles(dir)
        val languages = arrayOf(VoiceLanguages(spoken, target))
        val inserted = mutableListOf<String>()
        val states = mutableListOf<VoiceUiState>()
        val granted = booleanArrayOf(permission)
        val flow = VoiceFlow(
            recorder = recorder, player = player, api = api, files = files,
            hasPermission = { granted[0] }, traits = { traits },
            languages = { languages[0] }, insert = { inserted += it },
            scope = backgroundScope, maxRecordingMillis = maxRecordingMillis,
        )
        flow.onStateChanged = { states += it }
        return Setup(flow, recorder, player, api, files, dir, languages, inserted, states, granted)
    }

    /** Records for [millis] and stops. */
    private fun TestScope.record(s: Setup, millis: Long = 2_000) {
        s.flow.start(); runCurrent()
        advanceTimeBy(millis); runCurrent()
        s.flow.stop(); runCurrent()
    }

    // ---- the happy path, for several target languages ----

    @Test
    fun recordUnderstandTranslateInsert_forSeveralTargets() = runTest {
        for (target in listOf(Languages.Spanish, Languages.French, Languages.Japanese, Languages.German, Languages.Chinese)) {
            val s = setup(target = target, api = FakeVoiceApi { delay(3_000); answer(it) })

            s.flow.start(); runCurrent()
            assertEquals(VoiceUiState.Recording(Languages.English, 0, emptyList()), s.flow.state)
            advanceTimeBy(2_000); runCurrent()
            val recording = s.flow.state as VoiceUiState.Recording
            assertEquals(2_000, recording.elapsedMillis)
            assertEquals(20, recording.levels.size)

            s.flow.stop(); runCurrent()
            assertEquals(VoiceUiState.Understanding(target), s.flow.state) // "Understanding your message…"
            advanceTimeBy(1_600); runCurrent()
            assertEquals(VoiceUiState.Translating(target), s.flow.state) // "Translating to <the selected language>…"

            advanceTimeBy(2_000); runCurrent()
            val result = s.flow.state as VoiceUiState.Result
            assertEquals(Languages.English, result.original)
            assertEquals("Are you coming tomorrow?", result.originalText)
            assertEquals(target, result.target)
            assertEquals(answers.getValue(target.code), result.translation)

            val call = s.api.calls.single()
            assertEquals("audio/mp4", call.type)
            assertEquals("en", call.source)
            assertEquals(target.code, call.target)
            assertTrue(call.fileExisted)

            assertTrue(s.inserted.isEmpty()) // nothing goes into the field until the user asks
            s.flow.insertTranslation()
            assertEquals(listOf(answers.getValue(target.code)), s.inserted)
            assertEquals(VoiceUiState.Idle, s.flow.state)
            assertTrue(s.leftovers().isEmpty())
        }
    }

    @Test
    fun anInsertedVoiceTranslation_isHandedToTheLearningEngine_inTheTargetLanguage() = runTest {
        val heard = mutableListOf<TranslationInteraction>()
        val s = setup(target = Languages.Japanese)
        val flow = VoiceFlow(
            s.recorder, s.player, s.api, s.files, { true }, { plainText }, { VoiceLanguages(Languages.English, Languages.Japanese) },
            { }, backgroundScope, learning = LearningRecorder { heard += it },
        )
        flow.start(); runCurrent(); advanceTimeBy(2_000); runCurrent(); flow.stop(); runCurrent()
        flow.insertTranslation()
        assertEquals(listOf(TranslationInteraction(InteractionKind.OUTGOING_VOICE, "明日来ますか？", "ja")), heard)
    }

    @Test
    fun nothingIsHandedToTheLearningEngine_ifTheUserDoesNotInsert() = runTest {
        val heard = mutableListOf<TranslationInteraction>()
        val s = setup()
        val flow = VoiceFlow(
            s.recorder, s.player, s.api, s.files, { true }, { plainText }, { VoiceLanguages(Languages.English, Languages.Spanish) },
            { }, backgroundScope, learning = LearningRecorder { heard += it },
        )
        flow.start(); runCurrent(); advanceTimeBy(2_000); runCurrent(); flow.stop(); runCurrent()
        flow.cancel()
        assertTrue(heard.isEmpty())
    }

    @Test
    fun theSpokenLanguageIsTheUsersOwn_notAlwaysEnglish() = runTest {
        val s = setup(spoken = Languages.French, target = Languages.Japanese)
        s.flow.start(); runCurrent()
        assertEquals(Languages.French, (s.flow.state as VoiceUiState.Recording).spoken)
        advanceTimeBy(1_000); runCurrent()
        s.flow.stop(); runCurrent()
        assertEquals("fr", s.api.calls.single().source)
        assertEquals(Languages.French, (s.flow.state as VoiceUiState.Result).original)
    }

    @Test
    fun theTargetIsReadWhenRecordingStops_soAChangeWhileSpeakingIsUsed() = runTest {
        val s = setup(target = Languages.Spanish)
        s.flow.start(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        s.languages[0] = VoiceLanguages(Languages.English, Languages.French) // the user changed the language
        s.flow.stop(); runCurrent()
        assertEquals("fr", s.api.calls.single().target)
    }

    // ---- cancel, permission, where voice is not available ----

    @Test
    fun cancelWhileRecording_stopsTheMicrophone_deletesTheFile_andUploadsNothing() = runTest {
        val s = setup()
        s.flow.start(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        assertEquals(1, s.leftovers().size)

        s.flow.cancel()

        assertEquals(VoiceUiState.Idle, s.flow.state)
        assertTrue(s.recorder.cancelled >= 1)
        assertTrue(s.leftovers().isEmpty())
        assertTrue(s.api.calls.isEmpty())
        advanceTimeBy(5_000); runCurrent()
        assertEquals(VoiceUiState.Idle, s.flow.state) // the timer stopped too
    }

    @Test
    fun withoutMicrophonePermission_itExplainsAndDoesNotRecord() = runTest {
        val s = setup(permission = false)
        s.flow.start(); runCurrent()
        assertEquals(VoiceUiState.NeedsPermission, s.flow.state)
        assertEquals(0, s.recorder.started)

        s.permission[0] = true // the user allowed it and tapped the microphone again
        s.flow.start(); runCurrent()
        assertTrue(s.flow.state is VoiceUiState.Recording)
    }

    @Test
    fun passwordFieldsAndKeyOnlyFieldsDoNotRecord() = runTest {
        for (type in listOf(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_NULL)) {
            val s = setup(traits = EditorTraits(type))
            s.flow.start(); runCurrent()
            assertEquals(VoiceUiState.Failed(VoiceFailure.NOT_AVAILABLE_HERE, canRetry = false), s.flow.state)
            assertEquals(0, s.recorder.started)
        }
    }

    @Test
    fun aMicrophoneThatCannotStart_isReported_andLeavesNoFile() = runTest {
        val s = setup()
        s.recorder.canStart = false
        s.flow.start(); runCurrent()
        assertEquals(VoiceUiState.Failed(VoiceFailure.MIC_UNAVAILABLE, canRetry = false), s.flow.state)
        assertTrue(s.leftovers().isEmpty())
    }

    // ---- nothing to upload ----

    @Test
    fun aClipThatIsTooShort_isNotUploaded() = runTest {
        val s = setup()
        record(s, millis = 300)
        assertEquals(VoiceUiState.Failed(VoiceFailure.TOO_SHORT, canRetry = false), s.flow.state)
        assertTrue(s.api.calls.isEmpty())
        assertTrue(s.leftovers().isEmpty())
    }

    @Test
    fun silence_isNotUploaded() = runTest {
        val s = setup()
        s.recorder.level = 100
        record(s)
        assertEquals(VoiceUiState.Failed(VoiceFailure.NO_SPEECH, canRetry = false), s.flow.state)
        assertTrue(s.api.calls.isEmpty())
        assertTrue(s.leftovers().isEmpty())
    }

    @Test
    fun aRecorderThatCannotFinishTheFile_isTooShort() = runTest {
        val s = setup()
        s.recorder.stopWorks = false
        record(s)
        assertEquals(VoiceFailure.TOO_SHORT, (s.flow.state as VoiceUiState.Failed).failure)
        assertTrue(s.leftovers().isEmpty())
    }

    // ---- backend failures ----

    @Test
    fun everyBackendFailure_endsInAMessage_andDeletesTheRecordingUnlessRetryIsPossible() = runTest {
        for (failure in VoiceFailure.entries) {
            val s = setup(api = FakeVoiceApi { VoiceResult.Failure(failure) })
            record(s)
            val state = s.flow.state as VoiceUiState.Failed
            assertEquals(failure.name, failure, state.failure)
            assertEquals(failure.name, failure.canRetry, state.canRetry)
            assertEquals(failure.name, if (failure.canRetry) 1 else 0, s.leftovers().size)
            assertTrue(s.inserted.isEmpty())
        }
    }

    @Test
    fun offlineThenBackOnline_retrySendsTheSameRecordingAgain() = runTest {
        var online = false
        val s = setup(api = FakeVoiceApi { if (online) answer(it) else VoiceResult.Failure(VoiceFailure.OFFLINE) })
        record(s)
        assertEquals(VoiceUiState.Failed(VoiceFailure.OFFLINE, canRetry = true), s.flow.state)

        online = true
        s.flow.retry(); runCurrent()

        assertTrue(s.flow.state is VoiceUiState.Result)
        assertEquals(2, s.api.calls.size)
        assertTrue(s.api.calls[1].fileExisted)
    }

    @Test
    fun anUnsupportedSpeechLanguage_isReported() = runTest {
        val s = setup(api = FakeVoiceApi { VoiceResult.Failure(VoiceFailure.UNSUPPORTED_SPEECH_LANGUAGE) })
        record(s)
        assertEquals(VoiceUiState.Failed(VoiceFailure.UNSUPPORTED_SPEECH_LANGUAGE, canRetry = false), s.flow.state)
        assertTrue(s.leftovers().isEmpty())
    }

    @Test
    fun aPartialResult_keepsWhatWasHeard_andCanInsertIt() = runTest {
        val s = setup(api = FakeVoiceApi { VoiceResult.Failure(VoiceFailure.PARTIAL, heard = "Tell him I'll be late") })
        record(s)
        val failed = s.flow.state as VoiceUiState.Failed
        assertEquals("Tell him I'll be late", failed.heard)

        s.flow.insertHeard()

        assertEquals(listOf("Tell him I'll be late"), s.inserted)
        assertEquals(VoiceUiState.Idle, s.flow.state)
        assertTrue(s.leftovers().isEmpty())
    }

    @Test
    fun aBackendThatNeverAnswers_timesOut_andKeepsTheRecordingForRetry() = runTest {
        val s = setup(api = FakeVoiceApi { awaitCancellation() })
        record(s)
        assertTrue(s.flow.state is VoiceUiState.Understanding)
        advanceTimeBy(90_001); runCurrent()
        assertEquals(VoiceUiState.Failed(VoiceFailure.TIMEOUT, canRetry = true), s.flow.state)
        assertEquals(1, s.leftovers().size)
    }

    // ---- the keyboard is dismissed or the user switches apps ----

    @Test
    fun dismissedWhileRecording_stopsTheMicrophone_andDeletesTheFile() = runTest {
        val s = setup()
        s.flow.start(); runCurrent()
        advanceTimeBy(1_000); runCurrent()
        s.flow.onInputFinished()
        assertEquals(VoiceUiState.Idle, s.flow.state)
        assertTrue(s.recorder.cancelled >= 1)
        assertTrue(s.leftovers().isEmpty())
        assertTrue(s.api.calls.isEmpty())
    }

    @Test
    fun switchingAppsWhileUploading_cancelsTheRequest_deletesTheFile_andInsertsNothing() = runTest {
        val s = setup(api = FakeVoiceApi { awaitCancellation() })
        record(s)
        assertTrue(s.flow.state is VoiceUiState.Understanding)

        s.flow.onInputFinished()
        runCurrent()
        advanceTimeBy(200_000); runCurrent()

        assertTrue(s.api.cancelled)
        assertEquals(VoiceUiState.Idle, s.flow.state)
        assertTrue(s.leftovers().isEmpty())
        assertTrue(s.inserted.isEmpty())
    }

    @Test
    fun leavingWithAResultNotInserted_deletesTheRecording() = runTest {
        val s = setup()
        record(s)
        assertTrue(s.flow.state is VoiceUiState.Result)
        assertEquals(1, s.leftovers().size)
        s.flow.onInputStarted(restarting = false) // another field or app took over
        assertEquals(VoiceUiState.Idle, s.flow.state)
        assertTrue(s.leftovers().isEmpty())
        assertTrue(s.inserted.isEmpty())
    }

    @Test
    fun restartingTheSameField_keepsTheResult() = runTest {
        val s = setup()
        record(s)
        s.flow.onInputStarted(restarting = true)
        assertTrue(s.flow.state is VoiceUiState.Result)
    }

    @Test
    fun closingTheResult_isAlwaysCleanUp() = runTest {
        val s = setup()
        record(s)
        s.flow.cancel()
        assertTrue(s.leftovers().isEmpty())
    }

    // ---- the review actions ----

    @Test
    fun listenPlaysTheRecording_andTogglesOff() = runTest {
        val s = setup()
        record(s)
        s.flow.listen()
        assertNotEquals(null, s.player.playing)
        assertTrue((s.flow.state as VoiceUiState.Result).playing)

        s.player.onDone?.invoke() // playback ended by itself
        assertFalse((s.flow.state as VoiceUiState.Result).playing)

        s.flow.listen()
        s.flow.listen() // second tap stops it
        assertFalse((s.flow.state as VoiceUiState.Result).playing)
    }

    @Test
    fun closingStopsPlayback() = runTest {
        val s = setup()
        record(s)
        s.flow.listen()
        s.flow.onInputFinished()
        assertNull(s.player.playing)
    }

    @Test
    fun editingChangesWhatIsInserted() = runTest {
        val s = setup(target = Languages.French)
        record(s)
        s.flow.edit()
        assertTrue(s.flow.isEditing)

        s.flow.editTarget.commitText(" 🙂")
        assertEquals("Tu viens demain ? 🙂", (s.flow.state as VoiceUiState.Result).translation)
        s.flow.editTarget.deleteBackward() // removes the whole emoji, not half of it
        assertEquals("Tu viens demain ? ", (s.flow.state as VoiceUiState.Result).translation)
        s.flow.editTarget.commitText("Merci")

        s.flow.insertTranslation()
        assertEquals(listOf("Tu viens demain ? Merci"), s.inserted)
    }

    @Test
    fun anEmptiedTranslation_cannotBeInserted() = runTest {
        val s = setup()
        record(s)
        s.flow.edit()
        repeat(40) { s.flow.editTarget.deleteBackward() }
        s.flow.insertTranslation()
        assertTrue(s.inserted.isEmpty())
        assertTrue(s.flow.state is VoiceUiState.Result)
    }

    @Test
    fun recordAgain_deletesTheOldRecordingAndStartsANewOne() = runTest {
        val s = setup()
        record(s)
        val first = s.recorder.file
        s.flow.recordAgain(); runCurrent()
        assertTrue(s.flow.state is VoiceUiState.Recording)
        assertFalse(first!!.exists())
        assertNotEquals(first, s.recorder.file)
        assertEquals(1, s.leftovers().size)
        assertTrue(s.inserted.isEmpty())
    }

    // ---- limits and robustness ----

    @Test
    fun theRecordingStopsByItselfAtTheTimeLimit_andIsUploaded() = runTest {
        val s = setup(maxRecordingMillis = 5_000)
        s.flow.start(); runCurrent()
        advanceTimeBy(5_100); runCurrent()
        assertTrue(s.flow.state is VoiceUiState.Result)
        assertEquals(1, s.api.calls.size)
    }

    @Test
    fun startingWhileBusyOrStoppingWhenNotRecording_isIgnored() = runTest {
        val s = setup(api = FakeVoiceApi { awaitCancellation() })
        s.flow.stop()
        assertEquals(VoiceUiState.Idle, s.flow.state)
        s.flow.start(); runCurrent()
        s.flow.start(); runCurrent()
        assertEquals(1, s.recorder.started)
        record(s.also { }, millis = 0) // stops the running recording
    }

    @Test
    fun leftoverRecordingsFromACrashAreSwept() {
        val dir = folder.newFolder()
        File(dir, "voice-old.m4a").writeBytes(byteArrayOf(1))
        File(dir, "voice-older.m4a").writeBytes(byteArrayOf(1))
        VoiceFiles(dir).sweep()
        assertTrue(dir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun theFlowCanOnlyInsertText_itHasNoWayToSend() {
        val publicMethods = VoiceFlow::class.java.methods.map { it.name }
        assertFalse(publicMethods.any { it.contains("send", ignoreCase = true) || it.contains("click", ignoreCase = true) })
    }

    @Test
    fun theSwitchableTargetRoutesTypingToTheOverride() {
        val typed = mutableListOf<String>()
        val primary = object : TextTarget by NoopTarget { override fun commitText(text: String) { typed += "field:$text" } }
        val other = object : TextTarget by NoopTarget { override fun commitText(text: String) { typed += "edit:$text" } }
        val switchable = SwitchableTextTarget(primary)
        switchable.commitText("a")
        switchable.override = other
        switchable.commitText("b")
        switchable.override = null
        switchable.commitText("c")
        assertEquals(listOf("field:a", "edit:b", "field:c"), typed)
    }

    private object NoopTarget : TextTarget {
        override fun commitText(text: String) = Unit
        override fun deleteBackward() = Unit
        override fun performEditorAction(actionId: Int) = Unit
        override fun sendKey(keyCode: Int) = Unit
        override fun finishComposing() = Unit
        override fun cursorCapsMode(inputType: Int) = 0
    }
}
