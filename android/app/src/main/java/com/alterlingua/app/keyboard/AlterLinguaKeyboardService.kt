package com.alterlingua.app.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.inputmethodservice.InputMethodService
import android.text.InputType
import androidx.core.content.ContextCompat
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.flow.first
import com.alterlingua.app.AlterLinguaApplication
import com.alterlingua.app.R
import com.alterlingua.app.capture.CaptureRequest
import com.alterlingua.app.capture.CapturedNote
import com.alterlingua.app.capture.ChatApp
import com.alterlingua.app.capture.VoiceCaptureLauncher
import com.alterlingua.app.capture.VoiceCaptureResultActivity
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.alterlingua.app.navigation.AppLinks
import com.alterlingua.app.storage.setTargetLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * The AlterLingua keyboard: a real Android input method.
 *
 * It types text into whatever field the user is in through the field's `InputConnection`, and has a toolbar showing
 * the translation target (read from the app's saved settings) with a language chooser. When the user taps Translate
 * it reads the text of the current field, sends only that text to the backend, and replaces it with the answer
 * (with Undo). Nothing is stored or logged, and it never sends a message. Voice is not built yet.
 */
class AlterLinguaKeyboardService : InputMethodService() {

    private var keyboardView: AlterLinguaKeyboardView? = null

    /** The captured voice note the keyboard is showing (or would show), and what it looks like now. */
    private var shownNote: CapturedNote? = null
    private var capturedUi: CapturedNoteUi = CapturedNoteUi.Hidden
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Typing goes to the app's field, or to the voice panel's editing text while a translation is being edited.
    private val typingTarget = SwitchableTextTarget(InputConnectionTextTarget { currentInputConnection })

    private val controller = KeyboardController(
        target = typingTarget,
        switchKeyboard = ::switchToAnotherKeyboard,
    )

    private val toolbar = ToolbarController(
        saveTarget = { language -> scope.launch { settings.setTargetLanguage(language) } },
        onEvent = ::handleToolbarEvent,
    )

    // Lazy: Android attaches the app to a service only AFTER constructing it, so nothing here may touch `application`
    // while the service is being constructed (doing so crashed the keyboard the moment it was chosen).
    private val translation by lazy {
        TranslationFlow(
            composer = { currentInputConnection?.let(::InputConnectionComposer) },
            traits = { controller.editorTraits },
            api = (application as AlterLinguaApplication).translationApi,
            // Always the user's current choice, read when Translate is tapped. Never a fixed language.
            targetLanguage = { settings.settings.first().targetLanguage },
            scope = scope,
            // AUTO detects the language of the text; with a fixed source it is always the user's source language.
            sourceLanguage = { settings.settings.first().languagePreferences.requestSource },
            learning = (application as AlterLinguaApplication).learningRecorder,
        )
    }

    private val voice by lazy {
        VoiceFlow(
            recorder = MediaRecorderVoiceRecorder(this),
            player = MediaPlayerVoicePlayer(),
            api = (application as AlterLinguaApplication).voiceApi,
            files = VoiceFiles.forContext(this),
            hasPermission = { ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
            traits = { controller.editorTraits },
            // The user speaks their own language; the target is the current selection.
            languages = { settings.settings.first().let { VoiceLanguages(spoken = it.nativeLanguage, target = it.targetLanguage) } },
            // Only ever typed text into the field, and only when the user taps Insert. Nothing is sent.
            insert = { text -> controller.insertText(text) },
            scope = scope,
            learning = (application as AlterLinguaApplication).learningRecorder,
        )
    }

    // The same saved settings the app uses (one DataStore per process).
    private val settings get() = (application as AlterLinguaApplication).userSettings

    override fun onCreate() {
        super.onCreate()
        controller.onStateChanged = { keyboardView?.render(it) }
        VoiceFiles.forContext(this).sweep() // recordings left behind by a crash
        voice.onStateChanged = { state ->
            keyboardView?.renderVoice(state)
            val editing = (state as? VoiceUiState.Result)?.editing == true
            typingTarget.override = if (editing) voice.editTarget else null
            controller.traitsOverride = if (editing) EditorTraits(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE) else null
        }
        toolbar.onStateChanged = { keyboardView?.renderToolbar(it) }
        translation.onStateChanged = { keyboardView?.renderTranslation(it) }
        watchCapturedNotes()
        scope.launch {
            settings.settings.collect {
                toolbar.onSettingsChanged(it)
                autoTranslateEnabled = it.autoTranslateEnabled
                // AlterLingua's own keyboard labels follow the app language; a change rebuilds the keyboard in the new language.
                val language = it.appLanguage.takeIf { _ -> it.appLanguageChosen }
                if (language != appLanguage) {
                    appLanguage = language
                    handwriting.close()
                    if (keyboardView != null) setInputView(onCreateInputView())
                }
                // The letters follow the language the user types in (their own language); only the keys are rebuilt.
                if (it.nativeLanguage.code != typingLanguage) {
                    closeHandwriting()
                    typingLanguage = it.nativeLanguage.code
                    keyboardStyle = it.keyboardStyleFor(typingLanguage)
                    keyboardView?.keyboardStyle = keyboardStyle
                    keyboardView?.typingLanguage = typingLanguage
                    setUpConversion()
                } else if (it.keyboardStyleFor(typingLanguage) != keyboardStyle) {
                    keyboardStyle = it.keyboardStyleFor(typingLanguage)
                    keyboardView?.keyboardStyle = keyboardStyle
                    setUpConversion() // 中文 nine-key and QWERTY pinyin use different schemas
                }
            }
        }
    }

    /** The user's chosen app language (null until chosen: the phone's language is used). */
    private var typingLanguage: String = "en"

    /** Settings → Auto-translate: off by default (CLAUDE.md 17: manual Translate is the trusted default). */
    private var autoTranslateEnabled: Boolean = false

    /** How the typing language is typed (for example pinyin on nine keys); null when it has only one way. */
    private var keyboardStyle: com.alterlingua.app.learning.KeyboardStyle? = null

    // Kana-to-kanji conversion for 日本語 (Mozc, on the phone). Null when the typing language needs none or the engine could not start:
    // the kana keys then type kana directly.
    private var composition: com.alterlingua.app.keyboard.engine.CompositionController? = null

    private var rime: com.alterlingua.app.keyboard.engine.RimeEngine? = null

    private fun setUpConversion() {
        composition?.cancel()
        composition = null
        rime?.close()
        rime = null
        keyboardView?.renderCandidates(com.alterlingua.app.keyboard.engine.Composition())
        val language = typingLanguage
        val engine: com.alterlingua.app.keyboard.engine.CandidateEngine
        val loaded: () -> Boolean
        when (language) {
            "ja" -> com.alterlingua.app.keyboard.engine.MozcEngine(this).let { engine = it; loaded = { it.available } }
            "zh" -> com.alterlingua.app.keyboard.engine.RimeEngine(
                this,
                when (keyboardStyle) {
                    com.alterlingua.app.learning.KeyboardStyle.ZHUYIN -> com.alterlingua.app.keyboard.engine.RimeEngine.SCHEMA_ZHUYIN
                    com.alterlingua.app.learning.KeyboardStyle.PINYIN_26 -> com.alterlingua.app.keyboard.engine.RimeEngine.SCHEMA_PINYIN
                    else -> com.alterlingua.app.keyboard.engine.RimeEngine.SCHEMA_STROKE
                },
            ).let { engine = it; rime = it; loaded = { it.available } }
            else -> return
        }
        scope.launch {
            // Loading the library and preparing its data the first time takes a moment: keep it off the main thread.
            val ready = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { loaded() }
            if (ready && typingLanguage == language) {
                composition = com.alterlingua.app.keyboard.engine.CompositionController(engine, typingTarget).also { controller ->
                    controller.onChanged = { keyboardView?.renderCandidates(it) }
                }
            }
        }
    }

    /** 日本語: every kana key is converted. 中文: the lowercase letters (pinyin); capitals, punctuation and symbols are typed as they are. */
    private fun takesConversion(key: KeySpec.Character): Boolean {
        if (controller.state.page != KeyboardPage.LETTERS) return false
        val letter = controller.state.shift == ShiftState.OFF && key.text.length == 1 && key.text[0] in 'a'..'z'
        return when (typingLanguage) {
            // Stroke and Zhuyin: the keys that show a symbol are the ones the engine takes; punctuation is typed as it is.
            "zh" -> if (keyboardStyle == com.alterlingua.app.learning.KeyboardStyle.PINYIN_26) letter else key.label != null
            "ja" -> if (keyboardStyle == com.alterlingua.app.learning.KeyboardStyle.ROMAJI) letter else true
            else -> true
        }
    }

    // ---- Handwriting: a pad in place of the keys. Recognition runs on the phone (ML Kit); nothing drawn is kept.

    private val handwriting = com.alterlingua.app.keyboard.handwriting.HandwritingController(
        com.alterlingua.app.keyboard.handwriting.MlKitInkRecognizer(),
        scope,
    ).also { controller -> controller.onChanged = { keyboardView?.renderHandwriting(it) } }

    private fun handwritingLanguageName(): String =
        com.alterlingua.app.learning.Languages.fromCode(typingLanguage)?.nativeName ?: typingLanguage

    private fun openHandwriting() {
        composition?.flush() // whatever is being typed stays in the field first
        keyboardView?.showHandwriting(true, handwritingLanguageName())
        handwriting.open(typingLanguage)
    }

    private fun closeHandwriting() {
        if (keyboardView?.isHandwritingOpen == true) keyboardView?.showHandwriting(false, "")
        handwriting.close()
    }

    private val handwritingActions = object : com.alterlingua.app.keyboard.handwriting.HandwritingActions {
        override fun onStrokeStarted() = handwriting.strokeStarted()
        override fun onStrokeFinished(stroke: com.alterlingua.app.keyboard.handwriting.InkStroke) = handwriting.strokeFinished(stroke)

        override fun onCandidate(index: Int) {
            val text = handwriting.choose(index) ?: return
            translation.onUserEdited()
            if (autoTranslateEnabled) translation.scheduleAutoTranslate()
            typingTarget.commitText(text)
        }

        // On the pad, backspace first wipes what is drawn; with nothing drawn it deletes text as usual.
        override fun onBackspace() {
            if (!handwriting.clear()) controller.onKey(KeySpec.Function(KeyAction.BACKSPACE, 1f))
        }

        override fun onSpace() = controller.onKey(KeySpec.Function(KeyAction.SPACE, 1f))
        override fun onEnter() = controller.onKey(KeySpec.Function(KeyAction.ENTER, 1f))

        override fun onPeriod() {
            translation.onUserEdited()
            if (autoTranslateEnabled) translation.scheduleAutoTranslate()
            controller.onKey(KeySpec.Character(if (typingLanguage == "zh" || typingLanguage == "ja") "。" else "."))
        }

        override fun onSwitchKeyboard() = controller.onKey(KeySpec.Function(KeyAction.SWITCH_KEYBOARD, 1f))
        override fun onBackToKeys() = closeHandwriting()
        override fun onRetry() {
            handwriting.retry()
        }
    }

    /** Sends a key to the conversion while one is active. Returns true when the conversion handled it. */
    private fun routeToConversion(key: KeySpec): Boolean {
        val conversion = composition ?: return false
        return when {
            key is KeySpec.Character && takesConversion(key) -> { conversion.type(key.text); true }
            key is KeySpec.Function && key.action == KeyAction.SPACE -> conversion.space()
            key is KeySpec.Function && key.action == KeyAction.BACKSPACE -> conversion.backspace()
            key is KeySpec.Function && key.action == KeyAction.ENTER -> conversion.enter()
            key is KeySpec.Spacer -> false
            else -> { conversion.flush(); false } // symbols, page switches: keep what was typed first
        }
    }

    private var appLanguage: com.alterlingua.app.learning.Language? = null

    override fun onCreateInputView(): View =
        AlterLinguaKeyboardView(com.alterlingua.app.localization.AppLanguage.wrap(this, appLanguage)).also { view ->
            view.onKey = { key ->
                if (key is KeySpec.Function && key.action == KeyAction.HANDWRITING) {
                    openHandwriting()
                } else {
                    if (key.changesText) {
                        translation.onUserEdited()
                        if (autoTranslateEnabled) translation.scheduleAutoTranslate()
                    }
                    if (!routeToConversion(key)) controller.onKey(key)
                }
            }
            view.onCandidate = { index -> composition?.choose(index) }
            view.handwritingActions = handwritingActions
            view.keyboardStyle = keyboardStyle
            view.typingLanguage = typingLanguage
            view.onToolbarAction = ::onToolbarAction
            view.render(controller.state)
            view.renderToolbar(toolbar.state)
            view.renderTranslation(translation.state)
            view.renderVoice(voice.state)
            view.renderCapturedNote(capturedUi)
            keyboardView = view
        }

    /**
     * Shows the newest captured voice note (see the capture package) on the keyboard while it is being translated and after,
     * until the user closes it. It is in memory only; a note older than ten minutes is not brought back when the keyboard opens.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun watchCapturedNotes() {
        val notes = (application as AlterLinguaApplication).capturedNotes
        scope.launch {
            notes.latest
                .flatMapLatest { note ->
                    if (note == null) {
                        flowOf(null to CapturedNoteUi.Hidden)
                    } else {
                        combine(note.viewModel.uiState, notes.dismissed) { state, dismissed ->
                            note to if (dismissed == note.address) CapturedNoteUi.Hidden else capturedNoteUi(state)
                        }
                    }
                }
                .collect { (note, ui) ->
                    shownNote = note
                    capturedUi = ui
                    keyboardView?.renderCapturedNote(ui)
                }
        }
    }

    /** Called when the keyboard opens: a captured note nobody looked at for ten minutes is put away. */
    private fun putAwayStaleNote() {
        val note = shownNote ?: return
        if (System.currentTimeMillis() - note.createdAt > STALE_NOTE_MILLIS) {
            (application as AlterLinguaApplication).capturedNotes.dismissOnKeyboard(note.address)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        putAwayStaleNote()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (!restarting) toolbar.closeLanguages()
        if (!restarting) composition?.cancel()
        translation.onInputStarted(restarting)
        voice.onInputStarted(restarting)
        controller.start(EditorTraits(attribute?.inputType ?: 0, attribute?.imeOptions ?: 0), restarting)
    }

    /** The keyboard was hidden: stop recording and delete the recording (the field itself may still be focused). */
    override fun onFinishInputView(finishingInput: Boolean) {
        voice.onInputFinished()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        translation.onInputFinished() // switching apps: stop any translation in progress and forget the original text
        closeHandwriting() // whatever was drawn is dropped
        voice.onInputFinished()
        controller.finish()
        super.onFinishInput()
    }

    /** The keyboard stays a compact panel in landscape instead of taking over the whole screen. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        translation.release()
        voice.release()
        scope.cancel()
        controller.onStateChanged = null
        translation.onStateChanged = null
        voice.onStateChanged = null
        toolbar.onStateChanged = null
        keyboardView = null
        super.onDestroy()
    }

    private fun onToolbarAction(action: ToolbarAction) {
        composition?.flush() // typed kana still being converted must be in the field before it is read or replaced
        when (action) {
            ToolbarAction.OpenLanguages -> toolbar.openLanguages()
            ToolbarAction.CloseLanguages -> toolbar.closeLanguages()
            is ToolbarAction.SelectLanguage -> {
                translation.cancel() // a translation already under way was for the old language
                toolbar.selectLanguage(action.language)
            }
            ToolbarAction.Undo, ToolbarAction.Restore -> translation.undo()
            ToolbarAction.Retry -> translation.translate()
            ToolbarAction.DismissStatus -> translation.dismiss()
            ToolbarAction.CancelTranslation -> translation.cancel()
            ToolbarAction.Translate -> toolbar.onTranslate()
            ToolbarAction.Microphone -> toolbar.onMicrophone()
            is ToolbarAction.Voice -> onVoiceAction(action.action)
            ToolbarAction.VoiceNote -> toolbar.onVoiceNote()
            ToolbarAction.OpenSettings -> toolbar.onSettings()
            is ToolbarAction.CapturedNote -> onCapturedNoteAction(action.action)
        }
    }

    /** A button on the captured-voice-note panel. Nothing here types into the chat or sends anything. */
    private fun onCapturedNoteAction(action: CapturedNoteAction) {
        val note = shownNote ?: return
        val notes = (application as AlterLinguaApplication).capturedNotes
        when (action) {
            CapturedNoteAction.LISTEN -> note.viewModel.listen()
            CapturedNoteAction.RETRY -> note.viewModel.retry()
            CapturedNoteAction.DISMISS -> notes.dismissOnKeyboard(note.address)
            CapturedNoteAction.OPEN -> {
                startActivity(
                    Intent(this, VoiceCaptureResultActivity::class.java)
                        .putExtra(VoiceCaptureResultActivity.EXTRA_ADDRESS, note.address)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                requestHideSelf(0)
            }
        }
    }

    private fun handleToolbarEvent(event: ToolbarEvent) {
        when (event) {
            ToolbarEvent.Translate -> {
                toolbar.closeLanguages()
                translation.translate()
            }
            ToolbarEvent.Voice -> {
                translation.cancel()
                toolbar.closeLanguages()
                voice.start()
            }
            ToolbarEvent.VoiceNote -> {
                translation.cancel()
                toolbar.closeLanguages()
                startVoiceNoteCapture()
            }
            ToolbarEvent.OpenSettings -> {
                startActivity(AppLinks.settingsIntent(this))
                requestHideSelf(0)
            }
        }
    }

    /**
     * Opens the "capture a voice note" screen for the chat app being typed into. An input method can always see the app
     * it is connected to, so its name and Android's user id for it are read here (the capture is then limited to that
     * app's sound), and only those two values are passed on. Nothing else about the app or its chat is read.
     */
    private fun startVoiceNoteCapture() {
        val chatApp = currentInputEditorInfo?.packageName?.takeIf { it != packageName }
        var app: ChatApp? = null
        if (chatApp != null) {
            try {
                val info = packageManager.getApplicationInfo(chatApp, 0)
                app = ChatApp(chatApp, packageManager.getApplicationLabel(info).toString(), info.uid)
            } catch (_: PackageManager.NameNotFoundException) {
                // Leave the capture unlimited rather than failing; the screen says which app it will listen to.
            }
        }
        startActivity(VoiceCaptureLauncher.intent(this, CaptureRequest(listOfNotNull(app), keepListening = false)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        requestHideSelf(0)
    }

    private fun onVoiceAction(action: VoiceAction) {
        when (action) {
            VoiceAction.STOP -> voice.stop()
            VoiceAction.CANCEL -> voice.cancel()
            VoiceAction.ALLOW_MICROPHONE -> {
                // The keyboard cannot show the question itself; this opens an invisible screen that does.
                startActivity(Intent(this, MicrophonePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                voice.cancel()
            }
            VoiceAction.INSERT -> voice.insertTranslation()
            VoiceAction.EDIT -> voice.edit()
            VoiceAction.LISTEN -> voice.listen()
            VoiceAction.RECORD_AGAIN -> voice.recordAgain()
            VoiceAction.RETRY -> voice.retry()
            VoiceAction.INSERT_HEARD -> voice.insertHeard()
        }
    }

    /** Every keyboard must offer a way to leave it (Android's guidance): the globe key. */
    private fun switchToAnotherKeyboard() {
        val switched = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && switchToNextInputMethod(false)
        if (!switched) getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }
}

/** A captured voice note that has been on the keyboard this long without being looked at is put away when the keyboard opens. */
private const val STALE_NOTE_MILLIS = 10L * 60 * 1000
