package com.alterlingua.app.keyboard

import android.text.InputType
import com.alterlingua.app.learning.Language
import com.alterlingua.app.learning.Languages
import com.alterlingua.app.learning.engine.InteractionKind
import com.alterlingua.app.learning.engine.LearningRecorder
import com.alterlingua.app.learning.engine.TranslationInteraction
import com.alterlingua.app.translation.Translation
import com.alterlingua.app.translation.TranslationApi
import com.alterlingua.app.translation.TranslationFailure
import com.alterlingua.app.translation.TranslationRequest
import com.alterlingua.app.translation.TranslationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A text field in memory. Records what was done to it. */
private class FakeComposer(var text: String = "", var selStart: Int = text.length, var selEnd: Int = text.length) : Composer {
    var reads: ComposerRead? = null // when set, read() returns this instead of the real text
    var ignoreWrites = false
    var corruptWrites = false
    var writes = 0

    override fun read(maxChars: Int): ComposerRead = reads ?: if (text.length > maxChars) ComposerRead.TooLong else ComposerRead.Text(text, selStart, selEnd)

    override fun replaceAll(currentLength: Int, newText: String, selectionStart: Int, selectionEnd: Int): Boolean {
        writes++
        if (ignoreWrites) return true // says yes, does nothing
        text = if (corruptWrites) "???" else newText
        selStart = selectionStart.coerceIn(0, text.length)
        selEnd = selectionEnd.coerceIn(0, text.length)
        return true
    }
}

private class FakeApi(var handler: suspend (TranslationRequest) -> TranslationResult) : TranslationApi {
    val requests = mutableListOf<TranslationRequest>()
    override suspend fun translate(request: TranslationRequest): TranslationResult {
        requests += request
        return handler(request)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TranslationFlowTest {

    private val phrase = "Are you coming tomorrow?"
    private val answers = mapOf("es" to "¿Vienes mañana?", "fr" to "Tu viens demain ?", "ja" to "明日来ますか？", "de" to "Kommst du morgen?", "zh" to "你明天来吗？")
    private val plainText = EditorTraits(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)

    private fun success(request: TranslationRequest) =
        TranslationResult.Success(Translation(answers.getValue(request.target), "en", request.target))

    private class Setup(
        val composer: FakeComposer,
        val api: FakeApi,
        val flow: TranslationFlow,
        val target: Array<Language>,
        val states: MutableList<TranslationUiState>,
    )

    private fun TestScope.setup(
        text: String = phrase,
        traits: EditorTraits = plainText,
        api: FakeApi = FakeApi { success(it) },
        target: Language = Languages.Spanish,
        composerAvailable: Boolean = true,
        timeoutMillis: Long = 25_000,
        maxChars: Int = 5_000,
    ): Setup {
        val composer = FakeComposer(text)
        val current = arrayOf(target)
        val states = mutableListOf<TranslationUiState>()
        val flow = TranslationFlow(
            composer = { if (composerAvailable) composer else null },
            traits = { traits },
            api = api,
            targetLanguage = { current[0] },
            scope = backgroundScope,
            timeoutMillis = timeoutMillis,
            maxChars = maxChars,
        )
        flow.onStateChanged = { states += it }
        return Setup(composer, api, flow, current, states)
    }

    // ---- the three examples: the target always comes from the selection ----

    @Test
    fun exampleA_targetEspanol() = runTest {
        val s = setup(target = Languages.Spanish)
        s.flow.translate()
        runCurrent()

        assertEquals(listOf(TranslationRequest(phrase, "es", "auto", "messaging", "natural")), s.api.requests)
        assertEquals("¿Vienes mañana?", s.composer.text)
        assertEquals(TranslationUiState.Translated(Languages.Spanish), s.flow.state)
        assertEquals(s.composer.text.length, s.composer.selEnd) // cursor at the end of the new text
    }

    @Test
    fun exampleB_targetFrancais() = runTest {
        val s = setup(target = Languages.French)
        s.flow.translate()
        runCurrent()
        assertEquals("fr", s.api.requests.single().target)
        assertEquals("Tu viens demain ?", s.composer.text)
    }

    @Test
    fun exampleC_target日本語() = runTest {
        val s = setup(target = Languages.Japanese)
        s.flow.translate()
        runCurrent()
        assertEquals("ja", s.api.requests.single().target)
        assertEquals("明日来ますか？", s.composer.text)
        assertEquals(TranslationUiState.Translated(Languages.Japanese), s.flow.state)
    }

    @Test
    fun theTargetIsReadAgainEveryTime_soChangingItChangesTheRequest() = runTest {
        val s = setup(target = Languages.Spanish)
        s.flow.translate(); runCurrent()
        assertEquals("¿Vienes mañana?", s.composer.text)

        s.flow.dismiss()
        s.composer.text = phrase
        s.target[0] = Languages.German
        s.flow.translate(); runCurrent()
        s.flow.dismiss()
        s.composer.text = phrase
        s.target[0] = Languages.Chinese
        s.flow.translate(); runCurrent()

        assertEquals(listOf("es", "de", "zh"), s.api.requests.map { it.target })
        assertEquals("你明天来吗？", s.composer.text)
    }

    @Test
    fun aSuccessfulTranslation_isHandedToTheLearningEngine_inTheTargetLanguage() = runTest {
        val heard = mutableListOf<TranslationInteraction>()
        val composer = FakeComposer(phrase)
        val flow = TranslationFlow({ composer }, { plainText }, FakeApi { success(it) }, { Languages.Spanish }, backgroundScope, learning = LearningRecorder { heard += it })
        flow.translate(); runCurrent()
        assertEquals(listOf(TranslationInteraction(InteractionKind.OUTGOING_TEXT, "¿Vienes mañana?", "es")), heard)
    }

    @Test
    fun failuresAndCancelledTranslations_teachNothing() = runTest {
        val heard = mutableListOf<TranslationInteraction>()
        val recorder = LearningRecorder { heard += it }
        for (failure in TranslationFailure.entries) {
            val flow = TranslationFlow({ FakeComposer(phrase) }, { plainText }, FakeApi { TranslationResult.Failure(failure) }, { Languages.French }, backgroundScope, learning = recorder)
            flow.translate(); runCurrent()
        }
        val empty = TranslationFlow({ FakeComposer("   ") }, { plainText }, FakeApi { success(it) }, { Languages.French }, backgroundScope, learning = recorder)
        empty.translate(); runCurrent()
        assertTrue(heard.isEmpty())
    }

    @Test
    fun theStatesGoTranslating_thenTranslated() = runTest {
        val s = setup(target = Languages.French)
        s.flow.translate(); runCurrent()
        assertEquals(listOf(TranslationUiState.Translating(Languages.French), TranslationUiState.Translated(Languages.French)), s.states)
    }

    // ---- undo ----

    @Test
    fun undoRestoresTheExactOriginal_includingLinesEmojiSpacesAndTheSelection() = runTest {
        val original = "  Are you coming 🙂\n  tomorrow?  \n\n日本語 café "
        val s = setup(text = original, target = Languages.French)
        s.composer.selStart = 4
        s.composer.selEnd = 9
        s.flow.translate(); runCurrent()
        assertEquals("Tu viens demain ?", s.composer.text)

        s.flow.undo()

        assertEquals(original, s.composer.text)
        assertEquals(4, s.composer.selStart)
        assertEquals(9, s.composer.selEnd)
        assertEquals(TranslationUiState.Restored, s.flow.state)
    }

    @Test
    fun undoDoesNotOverwriteTextTheUserChangedAfterwards() = runTest {
        val s = setup(target = Languages.Spanish)
        s.flow.translate(); runCurrent()
        s.composer.text = "¿Vienes mañana? Y tú?" // the user kept typing
        val writes = s.composer.writes

        s.flow.undo()

        assertEquals("¿Vienes mañana? Y tú?", s.composer.text)
        assertEquals(writes, s.composer.writes)
        assertEquals(TranslationUiState.Failed(TranslationFailure.UNDO_UNAVAILABLE, canRetry = false), s.flow.state)
    }

    @Test
    fun undoWithNothingToUndoDoesNothing() = runTest {
        val s = setup()
        s.flow.undo()
        assertEquals(TranslationUiState.Idle, s.flow.state)
        assertEquals(0, s.composer.writes)
    }

    @Test
    fun theResultBannerAndUndoExpire() = runTest {
        val s = setup()
        s.flow.translate(); runCurrent()
        assertTrue(s.flow.state is TranslationUiState.Translated)
        advanceTimeBy(10_001); runCurrent()
        assertEquals(TranslationUiState.Idle, s.flow.state)
        s.flow.undo()
        assertEquals("¿Vienes mañana?", s.composer.text) // undo is gone; the translation stays
    }

    @Test
    fun typingDismissesTheBanner_butNotWhileWaiting() = runTest {
        val s = setup()
        s.flow.translate(); runCurrent()
        s.flow.onUserEdited()
        assertEquals(TranslationUiState.Idle, s.flow.state)

        val slow = setup(api = FakeApi { awaitCancellation() })
        slow.flow.translate(); runCurrent()
        slow.flow.onUserEdited()
        assertTrue(slow.flow.state is TranslationUiState.Translating)
    }

    // ---- nothing to translate / can't translate here ----

    @Test
    fun emptyOrBlankText_isRejected_andNothingIsSent() = runTest {
        for (text in listOf("", "   ", "\n\t \n")) {
            val s = setup(text = text)
            s.flow.translate(); runCurrent()
            assertEquals(TranslationUiState.Failed(TranslationFailure.EMPTY_TEXT, canRetry = false), s.flow.state)
            assertTrue(s.api.requests.isEmpty())
            assertEquals(text, s.composer.text)
        }
    }

    @Test
    fun passwordFields_areNeverSent() = runTest {
        for (type in listOf(
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
        )) {
            val s = setup(text = "hunter2 secret", traits = EditorTraits(type))
            s.flow.translate(); runCurrent()
            assertEquals(TranslationFailure.PASSWORD_FIELD, (s.flow.state as TranslationUiState.Failed).failure)
            assertTrue(s.api.requests.isEmpty())
            assertEquals("hunter2 secret", s.composer.text)
        }
    }

    @Test
    fun unsupportedEditors_areRefused() = runTest {
        val keyEventOnly = setup(traits = EditorTraits(InputType.TYPE_NULL))
        keyEventOnly.flow.translate(); runCurrent()
        assertEquals(TranslationFailure.UNSUPPORTED_EDITOR, (keyEventOnly.flow.state as TranslationUiState.Failed).failure)

        val noConnection = setup(composerAvailable = false)
        noConnection.flow.translate(); runCurrent()
        assertEquals(TranslationFailure.UNSUPPORTED_EDITOR, (noConnection.flow.state as TranslationUiState.Failed).failure)

        val cannotRead = setup()
        cannotRead.composer.reads = ComposerRead.Unsupported
        cannotRead.flow.translate(); runCurrent()
        assertEquals(TranslationFailure.UNSUPPORTED_EDITOR, (cannotRead.flow.state as TranslationUiState.Failed).failure)

        for (s in listOf(keyEventOnly, noConnection, cannotRead)) assertTrue(s.api.requests.isEmpty())
    }

    @Test
    fun tooMuchText_isRefused_andNothingIsSent() = runTest {
        val s = setup(text = "a".repeat(101), maxChars = 100)
        s.flow.translate(); runCurrent()
        assertEquals(TranslationFailure.TEXT_TOO_LONG, (s.flow.state as TranslationUiState.Failed).failure)
        assertTrue(s.api.requests.isEmpty())
        assertEquals("a".repeat(101), s.composer.text)
    }

    // ---- failures never cost the user's text ----

    @Test
    fun everyBackendFailure_leavesTheTextExactlyAsItWas() = runTest {
        for (failure in TranslationFailure.entries) {
            val original = "  $phrase 🙂\n日本語 "
            val s = setup(text = original, api = FakeApi { TranslationResult.Failure(failure) })
            s.flow.translate(); runCurrent()

            assertEquals(failure.name, original, s.composer.text)
            assertEquals(failure.name, 0, s.composer.writes)
            assertEquals(failure.name, TranslationUiState.Failed(failure, failure.canRetry), s.flow.state)
        }
    }

    @Test
    fun offlineThenBackOnline_retryWorks() = runTest {
        var online = false
        val s = setup(api = FakeApi { if (online) success(it) else TranslationResult.Failure(TranslationFailure.OFFLINE) })
        s.flow.translate(); runCurrent()
        assertEquals(TranslationUiState.Failed(TranslationFailure.OFFLINE, canRetry = true), s.flow.state)
        assertEquals(phrase, s.composer.text)

        online = true
        s.flow.translate(); runCurrent() // the Retry button
        assertEquals("¿Vienes mañana?", s.composer.text)
        assertEquals(2, s.api.requests.size)
    }

    @Test
    fun aBackendThatNeverAnswers_timesOut_andKeepsTheText() = runTest {
        val s = setup(api = FakeApi { awaitCancellation() }, timeoutMillis = 25_000)
        s.flow.translate(); runCurrent()
        assertTrue(s.flow.state is TranslationUiState.Translating)

        advanceTimeBy(25_001); runCurrent()

        assertEquals(TranslationUiState.Failed(TranslationFailure.TIMEOUT, canRetry = true), s.flow.state)
        assertEquals(phrase, s.composer.text)
        assertEquals(0, s.composer.writes)
    }

    @Test
    fun tappingTranslateTwice_sendsOnlyOneRequest() = runTest {
        val s = setup(api = FakeApi { awaitCancellation() })
        s.flow.translate(); runCurrent()
        s.flow.translate(); runCurrent()
        assertEquals(1, s.api.requests.size)
    }

    // ---- the user moves on or edits while waiting ----

    @Test
    fun switchingApps_cancelsTheRequest_andWritesNothing() = runTest {
        var cancelled = false
        val s = setup(api = FakeApi {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        })
        s.flow.translate(); runCurrent()
        assertTrue(s.flow.state is TranslationUiState.Translating)

        s.flow.onInputFinished() // Android tells the keyboard the field was left
        runCurrent()
        advanceTimeBy(60_000); runCurrent()

        assertTrue(cancelled)
        assertEquals(TranslationUiState.Idle, s.flow.state)
        assertEquals(phrase, s.composer.text)
        assertEquals(0, s.composer.writes)
    }

    @Test
    fun aNewFieldCancels_butRestartingTheSameFieldDoesNot() = runTest {
        val s = setup(api = FakeApi { awaitCancellation() })
        s.flow.translate(); runCurrent()
        s.flow.onInputStarted(restarting = true)
        assertTrue(s.flow.state is TranslationUiState.Translating)
        s.flow.onInputStarted(restarting = false)
        assertEquals(TranslationUiState.Idle, s.flow.state)
    }

    @Test
    fun leavingTheField_forgetsTheOriginal_soUndoDoesNothing() = runTest {
        val s = setup()
        s.flow.translate(); runCurrent()
        s.flow.onInputFinished()
        val writes = s.composer.writes
        s.flow.undo()
        assertEquals(writes, s.composer.writes)
    }

    @Test
    fun theFieldDisappearingBeforeTheAnswer_writesNothing() = runTest {
        var available = true
        val composer = FakeComposer(phrase)
        val flow = TranslationFlow(
            composer = { if (available) composer else null },
            traits = { plainText },
            api = FakeApi { available = false; success(it) }, // the connection goes away while waiting
            targetLanguage = { Languages.Spanish },
            scope = backgroundScope,
        )
        flow.translate(); runCurrent()
        assertEquals(phrase, composer.text)
        assertEquals(0, composer.writes)
        assertEquals(TranslationUiState.Idle, flow.state)
    }

    @Test
    fun textEditedWhileWaiting_isNeverOverwritten() = runTest {
        val s = setup(api = FakeApi {
            TranslationResult.Success(Translation("¿Vienes mañana?", "en", "es"))
        })
        s.flow.translate()
        s.composer.text = "$phrase And bring the report." // typed after tapping Translate, before the answer arrived
        runCurrent()

        assertEquals("$phrase And bring the report.", s.composer.text)
        assertEquals(0, s.composer.writes)
        assertEquals(TranslationUiState.Failed(TranslationFailure.TEXT_CHANGED, canRetry = true), s.flow.state)
    }

    // ---- writing into the field goes wrong ----

    @Test
    fun aFieldThatIgnoresTheWrite_keepsTheOriginal_andReportsIt() = runTest {
        val s = setup()
        s.composer.ignoreWrites = true
        s.flow.translate(); runCurrent()
        assertEquals(phrase, s.composer.text)
        assertEquals(TranslationUiState.Failed(TranslationFailure.APPLY_FAILED, canRetry = true), s.flow.state)
    }

    @Test
    fun ifTheFieldGarblesTheWrite_theOriginalIsPutBack() = runTest {
        val s = setup()
        var garble = true
        val composer = object : Composer by s.composer {
            override fun replaceAll(currentLength: Int, newText: String, selectionStart: Int, selectionEnd: Int): Boolean {
                if (garble && newText != phrase) { // garble only the translation; rolling back works
                    s.composer.text = "???"
                    garble = false
                    return true
                }
                return s.composer.replaceAll(currentLength, newText, selectionStart, selectionEnd)
            }
        }
        val flow = TranslationFlow({ composer }, { plainText }, s.api, { Languages.Spanish }, backgroundScope)
        flow.translate(); runCurrent()
        assertEquals(phrase, s.composer.text)
        assertEquals(TranslationUiState.Failed(TranslationFailure.APPLY_FAILED, canRetry = true), flow.state)
    }

    @Test
    fun ifTheOriginalCannotBePutBackAutomatically_restoreGetsItBack() = runTest {
        val s = setup()
        s.composer.corruptWrites = true
        s.flow.translate(); runCurrent()
        assertEquals("???", s.composer.text)
        assertEquals(TranslationUiState.Failed(TranslationFailure.APPLY_FAILED, canRetry = false, canRestore = true), s.flow.state)

        s.composer.corruptWrites = false // the field works again
        s.flow.undo() // the Restore button
        assertEquals(phrase, s.composer.text)
        assertEquals(TranslationUiState.Restored, s.flow.state)
    }

    // ---- what it can and cannot touch ----

    @Test
    fun theFieldInterfaceOnlyReadsAndReplacesText() {
        val methods = Composer::class.java.methods.map { it.name }.toSet()
        assertEquals(setOf("read", "replaceAll"), methods)
        assertFalse(methods.any { it.contains("send", ignoreCase = true) || it.contains("click", ignoreCase = true) })
    }

    // ---- the source language: AUTO or a fixed one ----

    @Test
    fun aFixedSourceLanguage_isSentAsTheSource() = runTest {
        val api = FakeApi { success(it) }
        val composer = FakeComposer("¿Vienes mañana?")
        val flow = TranslationFlow({ composer }, { plainText }, api, { Languages.German }, backgroundScope, sourceLanguage = { "es" })
        flow.translate()
        runCurrent()
        assertEquals(listOf(TranslationRequest("¿Vienes mañana?", "de", "es", "messaging", "natural")), api.requests)
    }

    @Test
    fun withoutASourceChoice_theSourceIsDetected() = runTest {
        val api = FakeApi { success(it) }
        val flow = TranslationFlow({ FakeComposer("Are you coming tomorrow?") }, { plainText }, api, { Languages.French }, backgroundScope)
        flow.translate()
        runCurrent()
        assertEquals("auto", api.requests.single().source)
    }

    @Test
    fun theSourceIsReadWhenTranslateIsTapped_notFixedEarlier() = runTest {
        val api = FakeApi { success(it) }
        var source = "auto"
        val flow = TranslationFlow({ FakeComposer("hola") }, { plainText }, api, { Languages.French }, backgroundScope, sourceLanguage = { source })
        source = "es"
        flow.translate()
        runCurrent()
        assertEquals("es", api.requests.single().source)
    }

    // ---- auto-translate: a Settings toggle, off by default (CLAUDE.md 17: never character-by-character) ----

    @Test
    fun autoTranslate_doesNotFireUntilTypingPauses() = runTest {
        val s = setup(target = Languages.French)
        s.flow.scheduleAutoTranslate(afterMillis = 900)
        advanceTimeBy(500); runCurrent()
        assertTrue(s.api.requests.isEmpty())

        advanceTimeBy(500); runCurrent()
        assertEquals("Tu viens demain ?", s.composer.text)
        assertEquals(TranslationUiState.Translated(Languages.French), s.flow.state)
    }

    @Test
    fun autoTranslate_theRealDefaultPause_isLongEnoughToNotFireBetweenWords() = runTest {
        // Real-device feedback: a 900ms pause fired mid-sentence, during an ordinary pause between words while the
        // message was still being composed. The default must sit clearly past that.
        val s = setup(target = Languages.French)
        s.flow.scheduleAutoTranslate() // the actual default the keyboard uses, not a value chosen by the test
        advanceTimeBy(1_500); runCurrent()
        assertTrue("a normal pause between words must not trigger it", s.api.requests.isEmpty())

        advanceTimeBy(2_000); runCurrent() // now genuinely paused (well past 2.5s total)
        assertEquals("Tu viens demain ?", s.composer.text)
    }

    @Test
    fun autoTranslate_aBurstOfKeysSendsOnlyOneRequest() = runTest {
        val s = setup(target = Languages.French)
        repeat(20) {
            s.flow.scheduleAutoTranslate(afterMillis = 900)
            advanceTimeBy(100); runCurrent() // each key restarts the countdown well before it can fire
        }
        assertTrue(s.api.requests.isEmpty()) // never paused long enough yet

        advanceTimeBy(900); runCurrent()
        assertEquals(1, s.api.requests.size)
    }

    @Test
    fun autoTranslate_doesNothingIfTheFieldIsBlankWhenItFires() = runTest {
        val s = setup(text = "", target = Languages.French)
        s.flow.scheduleAutoTranslate(afterMillis = 900)
        advanceTimeBy(1_000); runCurrent()
        assertTrue(s.api.requests.isEmpty())
        assertEquals(TranslationUiState.Idle, s.flow.state)
    }

    @Test
    fun autoTranslate_isCancelledWhenTheFieldIsLeft() = runTest {
        val s = setup(target = Languages.French)
        s.flow.scheduleAutoTranslate(afterMillis = 900)
        s.flow.onInputFinished()
        advanceTimeBy(2_000); runCurrent()
        assertTrue(s.api.requests.isEmpty())
    }
}
