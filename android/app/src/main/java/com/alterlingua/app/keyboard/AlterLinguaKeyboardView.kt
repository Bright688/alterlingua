package com.alterlingua.app.keyboard

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.alterlingua.app.R
import com.alterlingua.app.keyboard.handwriting.HandwritingActions
import com.alterlingua.app.keyboard.handwriting.HandwritingPanel
import com.alterlingua.app.keyboard.handwriting.HandwritingState
import com.alterlingua.app.keyboard.handwriting.InkStroke
import com.alterlingua.app.learning.KeyboardStyle
import com.alterlingua.app.learning.Language
import java.util.Locale

/** What the user asked for on the toolbar. */
sealed interface ToolbarAction {
    data object OpenLanguages : ToolbarAction
    data object CloseLanguages : ToolbarAction
    data class SelectLanguage(val language: Language) : ToolbarAction
    data object Translate : ToolbarAction
    data object Microphone : ToolbarAction
    data object VoiceNote : ToolbarAction
    data object OpenSettings : ToolbarAction
    data object Undo : ToolbarAction
    data object Retry : ToolbarAction
    data object Restore : ToolbarAction
    data object DismissStatus : ToolbarAction
    data object CancelTranslation : ToolbarAction

    /** A button on the voice panel. */
    data class Voice(val action: VoiceAction) : ToolbarAction

    /** A button on the captured-voice-note panel. */
    data class CapturedNote(val action: CapturedNoteAction) : ToolbarAction
}

/**
 * The keyboard panel: a toolbar on top, and below it either the keys or the "Translate to" language list.
 * It only shows things; typing is done by [KeyboardController] and the toolbar's choices by [ToolbarController].
 */
class AlterLinguaKeyboardView(context: Context) : LinearLayout(context) {

    /** Called with the key that was pressed. */
    var onKey: ((KeySpec) -> Unit)? = null

    /** Called with what the user did on the toolbar. */
    var onToolbarAction: ((ToolbarAction) -> Unit)? = null

    private val colors = KeyboardColors.forContext(context)
    private val density = resources.displayMetrics.density
    private val sidePadding = (3 * density).toInt()
    private val topPadding = (4 * density).toInt()
    private val bottomPadding = (8 * density).toInt()

    /** The language the user types in; it picks the letter layout. Changing it rebuilds the keys. */
    var typingLanguage: String = "en"
        set(value) {
            if (field != value) {
                field = value
                relayout()
            }
        }

    /** How that language is typed (for example pinyin on nine keys); null for languages with one way of typing. */
    var keyboardStyle: KeyboardStyle? = null
        set(value) {
            if (field != value) {
                field = value
                relayout()
            }
        }

    private fun relayout() {
        builtPage = null
        applyContentHeight()
        lastState?.let { render(it) }
    }

    private val contentFrame = FrameLayout(context)

    /** The height of the key area: the rows of the letters page (four, or five for Zhuyin), the slim row of the language's own characters counted smaller. The language list and voice panel share it. */
    private fun contentHeight(): Int {
        val rows = KeyboardLayouts.rows(KeyboardPage.LETTERS, typingLanguage, keyboardStyle)
        val slim = KeyboardLayouts.hasExtras(typingLanguage, keyboardStyle)
        val dp = rows.indices.sumOf { if (slim && it == 0) EXTRAS_KEY_HEIGHT_DP else KEY_HEIGHT_DP }
        return (maxOf(dp, if (handwritingOpen) HANDWRITING_HEIGHT_DP else 0) * density).toInt()
    }

    private fun applyContentHeight() {
        val height = contentHeight()
        for (child in listOf<View>(keysColumn, languagePanel, voicePanel.body, handwritingPanel.body)) {
            if (child.layoutParams?.height != height) child.layoutParams = child.layoutParams.apply { this.height = height }
        }
    }

    private var lastState: KeyboardState? = null
    private var builtPage: KeyboardPage? = null
    private val letterKeys = mutableListOf<Pair<KeySpec.Character, KeyCapView>>()
    private var shiftKey: KeyCapView? = null

    /** Called with the index of the conversion candidate the user tapped. */
    var onCandidate: ((Int) -> Unit)? = null

    private val candidateStrip = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val candidateBar = android.widget.HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        visibility = GONE
        addView(candidateStrip)
    }

    private var toolbarState = ToolbarState()
    private var shownLanguages: Pair<Language?, Boolean>? = null

    private val keysColumn = LinearLayout(context).apply { orientation = VERTICAL }
    private val languagePanel = LanguagePanelView(
        context,
        colors,
        onSelect = { onToolbarAction?.invoke(ToolbarAction.SelectLanguage(it)) },
        onClose = { onToolbarAction?.invoke(ToolbarAction.CloseLanguages) },
    )
    private val languageButton = ToolbarButtonView(context, ToolbarButtonStyle.OUTLINED, colors) {
        onToolbarAction?.invoke(if (toolbarState.panel == ToolbarPanel.LANGUAGES) ToolbarAction.CloseLanguages else ToolbarAction.OpenLanguages)
    }
    private val toolbarRow = LinearLayout(context)
    private val noticeView = TextView(context)
    private val statusRow = LinearLayout(context)
    /** What the handwriting pad asks for; set by the keyboard service. */
    var handwritingActions: HandwritingActions? = null

    private val handwritingPanel = HandwritingPanel(
        context,
        colors,
        object : HandwritingActions {
            override fun onStrokeStarted() { handwritingActions?.onStrokeStarted() }
            override fun onStrokeFinished(stroke: InkStroke) { handwritingActions?.onStrokeFinished(stroke) }
            override fun onCandidate(index: Int) { handwritingActions?.onCandidate(index) }
            override fun onBackspace() { handwritingActions?.onBackspace() }
            override fun onSpace() { handwritingActions?.onSpace() }
            override fun onEnter() { handwritingActions?.onEnter() }
            override fun onPeriod() { handwritingActions?.onPeriod() }
            override fun onSwitchKeyboard() { handwritingActions?.onSwitchKeyboard() }
            override fun onBackToKeys() { handwritingActions?.onBackToKeys() }
            override fun onRetry() { handwritingActions?.onRetry() }
        },
    )
    private var handwritingOpen = false
    private var handwritingState = HandwritingState()
    private var handwritingLanguageName = ""

    private val voicePanel = VoicePanel(context, colors) { onToolbarAction?.invoke(ToolbarAction.Voice(it)) }
    private var voiceState: VoiceUiState = VoiceUiState.Idle
    private val capturedNotePanel = CapturedNotePanel(context, colors) { onToolbarAction?.invoke(ToolbarAction.CapturedNote(it)) }
    private var capturedNoteState: CapturedNoteUi = CapturedNoteUi.Hidden
    private var toolbarFrame: FrameLayout? = null
    private var translationState: TranslationUiState = TranslationUiState.Idle
    private var noticeShown = false
    private val hideNotice = Runnable {
        noticeShown = false
        updateToolbarArea()
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(colors.board)
        // Android 15+ draws keyboards behind the navigation bar, so keep the keys clear of it. The system's own
        // inset callback below is the accurate source, but on the very first show of a fresh keyboard window it can
        // arrive a frame after the window's height is already locked in, clipping the bottom row (seen on a real
        // device). Starting with a safe minimum avoids that race; the listener corrects it to the exact value as
        // soon as it fires, whether that is immediately or a frame later.
        setPadding(sidePadding, topPadding, sidePadding, bottomPadding + (MIN_NAVIGATION_BAR_INSET_DP * density).toInt())
        ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
            val navigationBar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = bottomPadding + navigationBar.bottom)
            insets
        }

        addView(createToolbar(), LayoutParams(LayoutParams.MATCH_PARENT, (TOOLBAR_HEIGHT_DP * density).toInt()))

        addView(candidateBar, LayoutParams(LayoutParams.MATCH_PARENT, (CANDIDATE_BAR_HEIGHT_DP * density).toInt()))

        val contentHeight = contentHeight()
        val content = contentFrame
        content.addView(keysColumn, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, contentHeight))
        content.addView(languagePanel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, contentHeight))
        languagePanel.visibility = GONE
        content.addView(voicePanel.body, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, contentHeight))
        voicePanel.body.visibility = GONE
        content.addView(capturedNotePanel.body, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, contentHeight))
        capturedNotePanel.body.visibility = GONE
        content.addView(handwritingPanel.body, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, contentHeight))
        handwritingPanel.body.visibility = GONE
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun createToolbar(): View {
        val frame = FrameLayout(context)
        toolbarFrame = frame

        toolbarRow.orientation = HORIZONTAL
        toolbarRow.gravity = Gravity.CENTER_VERTICAL
        toolbarRow.setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)

        languageButton.showDot = true
        languageButton.label = ToolbarState().label
        languageButton.trailingIcon = ContextCompat.getDrawable(context, R.drawable.ic_tool_expand)
        val translate = ToolbarButtonView(context, ToolbarButtonStyle.PRIMARY, colors) { onToolbarAction?.invoke(ToolbarAction.Translate) }.apply {
            leadingIcon = ContextCompat.getDrawable(context, R.drawable.ic_tool_translate)
            label = context.getString(R.string.toolbar_translate)
        }
        val microphone = ToolbarButtonView(context, ToolbarButtonStyle.TONAL, colors) { onToolbarAction?.invoke(ToolbarAction.Microphone) }.apply {
            leadingIcon = ContextCompat.getDrawable(context, R.drawable.ic_tool_mic)
            contentDescription = context.getString(R.string.toolbar_microphone)
        }
        val voiceNote = ToolbarButtonView(context, ToolbarButtonStyle.TONAL, colors) { onToolbarAction?.invoke(ToolbarAction.VoiceNote) }.apply {
            leadingIcon = ContextCompat.getDrawable(context, R.drawable.ic_tool_voicenote)
            contentDescription = context.getString(R.string.toolbar_voice_note)
        }
        val settings = ToolbarButtonView(context, ToolbarButtonStyle.TONAL, colors) { onToolbarAction?.invoke(ToolbarAction.OpenSettings) }.apply {
            leadingIcon = ContextCompat.getDrawable(context, R.drawable.ic_tool_settings)
            contentDescription = context.getString(R.string.toolbar_settings)
        }

        val height = (34 * density).toInt()
        val gap = (6 * density).toInt()
        toolbarRow.addView(languageButton, LayoutParams(LayoutParams.WRAP_CONTENT, height))
        toolbarRow.addView(translate, LayoutParams(LayoutParams.WRAP_CONTENT, height).apply { leftMargin = gap })
        toolbarRow.addView(View(context), LayoutParams(0, 1, 1f))
        // Three icon buttons share the right side; the smaller gap keeps the toolbar inside a 360 dp screen in every language.
        val iconGap = (4 * density).toInt()
        toolbarRow.addView(microphone, LayoutParams(LayoutParams.WRAP_CONTENT, height).apply { rightMargin = iconGap })
        toolbarRow.addView(voiceNote, LayoutParams(LayoutParams.WRAP_CONTENT, height).apply { rightMargin = iconGap })
        toolbarRow.addView(settings, LayoutParams(LayoutParams.WRAP_CONTENT, height))

        statusRow.orientation = HORIZONTAL
        statusRow.gravity = Gravity.CENTER_VERTICAL
        statusRow.setPadding((10 * density).toInt(), 0, (6 * density).toInt(), 0)
        statusRow.visibility = GONE
        statusRow.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE // screen readers read each new status

        noticeView.visibility = GONE
        noticeView.gravity = Gravity.CENTER
        noticeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        noticeView.setTextColor(colors.functionText)
        noticeView.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE // screen readers read it when it appears

        frame.addView(toolbarRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(statusRow, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        frame.addView(voicePanel.header, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        voicePanel.header.visibility = GONE
        frame.addView(capturedNotePanel.header, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        capturedNotePanel.header.visibility = GONE
        frame.addView(noticeView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return frame
    }

    /** Shows a short message in place of the toolbar for a moment. */
    fun showNotice(text: String) {
        noticeView.text = text
        noticeShown = true
        updateToolbarArea()
        removeCallbacks(hideNotice)
        postDelayed(hideNotice, NOTICE_MILLIS)
    }

    /** One thing fills the toolbar strip: a short notice, the voice panel's header, the translation status, or the toolbar. */
    private fun updateToolbarArea() {
        val voiceActive = voiceState != VoiceUiState.Idle
        val statusActive = translationState != TranslationUiState.Idle
        val capturedActive = capturedNoteShowing()
        noticeView.visibility = if (noticeShown) VISIBLE else GONE
        voicePanel.header.visibility = if (!noticeShown && voiceActive) VISIBLE else GONE
        capturedNotePanel.header.visibility = if (!noticeShown && capturedActive) VISIBLE else GONE
        statusRow.visibility = if (!noticeShown && !voiceActive && statusActive) VISIBLE else GONE
        toolbarRow.visibility = if (!noticeShown && !voiceActive && !statusActive && !capturedActive) VISIBLE else GONE
    }

    /**
     * A captured voice note is shown only when nothing else owns the strip and the keys: not while the user is dictating or
     * translating, choosing a language, or handwriting. It comes back on its own when they are done, until it is closed.
     */
    private fun capturedNoteShowing() = capturedNoteState != CapturedNoteUi.Hidden &&
        voiceState == VoiceUiState.Idle &&
        translationState == TranslationUiState.Idle &&
        toolbarState.panel != ToolbarPanel.LANGUAGES &&
        !handwritingOpen

    /** Shows the captured voice note (transcribing, the result, or a problem), or hides it. */
    fun renderCapturedNote(state: CapturedNoteUi) {
        capturedNoteState = state
        capturedNotePanel.render(state)
        updateToolbarArea()
        updateContentArea()
    }

    /** Shows the voice panel state: recording, waiting, the result to review, or an error. */
    fun renderVoice(state: VoiceUiState) {
        voiceState = state
        voicePanel.render(state)
        // While the translation is being edited the strip is taller, to show the text; the keys stay for typing.
        toolbarFrame?.let { frame ->
            val height = ((if (voicePanel.wantsTallHeader) EDIT_HEADER_HEIGHT_DP else TOOLBAR_HEIGHT_DP) * density).toInt()
            if (frame.layoutParams.height != height) {
                frame.layoutParams = frame.layoutParams.apply { this.height = height }
            }
        }
        updateToolbarArea()
        updateContentArea()
    }

    /** The area under the strip shows the voice panel, the language list, or the keys. */
    private fun updateContentArea() {
        val voiceBody = voiceState != VoiceUiState.Idle && voicePanel.coversKeys
        val showList = toolbarState.panel == ToolbarPanel.LANGUAGES && voiceState == VoiceUiState.Idle
        val noteBody = capturedNoteShowing()
        languagePanel.visibility = if (showList) VISIBLE else GONE
        voicePanel.body.visibility = if (voiceBody) VISIBLE else GONE
        capturedNotePanel.body.visibility = if (noteBody) VISIBLE else GONE
        handwritingPanel.body.visibility = if (handwritingOpen && !voiceBody && !showList) VISIBLE else GONE
        keysColumn.visibility = if (showList || voiceBody || handwritingOpen || noteBody) INVISIBLE else VISIBLE
        if (handwritingOpen) candidateBar.visibility = GONE
    }

    /** Shows or hides the handwriting pad in place of the keys; [languageName] is the language being written. */
    fun showHandwriting(open: Boolean, languageName: String) {
        handwritingOpen = open
        handwritingLanguageName = languageName
        applyContentHeight()
        updateToolbarArea()
        updateContentArea()
        if (open) handwritingPanel.render(handwritingState, languageName)
    }

    val isHandwritingOpen: Boolean get() = handwritingOpen

    fun renderHandwriting(state: HandwritingState) {
        handwritingState = state
        if (handwritingOpen) handwritingPanel.render(state, handwritingLanguageName)
    }

    /** Shows the translation status in place of the toolbar: translating, translated with Undo, or an error. */
    fun renderTranslation(state: TranslationUiState) {
        translationState = state
        statusRow.removeAllViews()
        val height = (34 * density).toInt()
        val gap = (6 * density).toInt()

        fun text(value: String, sizeSp: Float, bold: Boolean, maxLines: Int = 1) = TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(colors.text)
            this.maxLines = maxLines
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        fun button(label: String, style: ToolbarButtonStyle, action: ToolbarAction) =
            ToolbarButtonView(context, style, colors) { onToolbarAction?.invoke(action) }.apply { this.label = label }

        fun spacedText(view: TextView) = statusRow.addView(view, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = gap; rightMargin = gap })

        when (state) {
            TranslationUiState.Idle -> Unit
            is TranslationUiState.Translating -> {
                val spinner = android.widget.ProgressBar(context).apply {
                    isIndeterminate = true
                    indeterminateTintList = android.content.res.ColorStateList.valueOf(colors.actionKey)
                }
                statusRow.addView(spinner, LayoutParams((20 * density).toInt(), (20 * density).toInt()))
                spacedText(text(context.getString(R.string.translation_translating, state.target.displayName), 13f, bold = true))
                statusRow.addView(button(context.getString(R.string.translation_cancel), ToolbarButtonStyle.TONAL, ToolbarAction.CancelTranslation), LayoutParams(LayoutParams.WRAP_CONTENT, height))
            }
            is TranslationUiState.Translated -> {
                val check = android.widget.ImageView(context).apply {
                    setImageDrawable(ContextCompat.getDrawable(context, R.drawable.ic_tool_check)?.mutate()?.also { it.setTint(colors.accent) })
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                statusRow.addView(check, LayoutParams((20 * density).toInt(), (20 * density).toInt()))
                spacedText(text(context.getString(R.string.translation_done), 14f, bold = true))
                statusRow.addView(button(context.getString(R.string.translation_undo), ToolbarButtonStyle.PRIMARY, ToolbarAction.Undo), LayoutParams(LayoutParams.WRAP_CONTENT, height))
            }
            TranslationUiState.Restored -> {
                spacedText(text(context.getString(R.string.translation_restored), 13f, bold = true))
            }
            is TranslationUiState.Failed -> {
                spacedText(text(context.getString(state.failure.messageRes(state.canRestore)), 12f, bold = false, maxLines = 2))
                if (state.canRestore) {
                    statusRow.addView(button(context.getString(R.string.translation_restore), ToolbarButtonStyle.PRIMARY, ToolbarAction.Restore), LayoutParams(LayoutParams.WRAP_CONTENT, height))
                } else if (state.canRetry) {
                    statusRow.addView(button(context.getString(R.string.translation_retry), ToolbarButtonStyle.PRIMARY, ToolbarAction.Retry), LayoutParams(LayoutParams.WRAP_CONTENT, height))
                }
                val close = ToolbarButtonView(context, ToolbarButtonStyle.TONAL, colors) { onToolbarAction?.invoke(ToolbarAction.DismissStatus) }.apply {
                    leadingIcon = ContextCompat.getDrawable(context, R.drawable.ic_tool_close)
                    contentDescription = context.getString(R.string.translation_dismiss)
                }
                statusRow.addView(close, LayoutParams(LayoutParams.WRAP_CONTENT, height).apply { leftMargin = gap })
            }
        }
        updateToolbarArea()
        updateContentArea() // a captured voice note gives way to the translation status, and comes back after it
    }

    /** Shows the toolbar [state]: the language chip, and the language list when it is open. */
    fun renderToolbar(state: ToolbarState) {
        toolbarState = state
        val target = state.target
        languageButton.label = state.label
        languageButton.isEnabled = target != null
        languageButton.alpha = if (target != null) 1f else 0.6f
        languageButton.contentDescription = target?.let { context.getString(R.string.toolbar_language_description, it.nativeName) }
            ?: context.getString(R.string.toolbar_language_loading)

        val showList = state.panel == ToolbarPanel.LANGUAGES
        if (showList && shownLanguages != (target to true)) {
            languagePanel.setLanguages(state.selectable, target)
        }
        shownLanguages = if (showList) target to true else null
        updateToolbarArea()
        updateContentArea()
    }

    /** Shows what is being typed and the conversion candidates (日本語) in a strip above the keys; hides the strip when nothing is being typed. */
    fun renderCandidates(composition: com.alterlingua.app.keyboard.engine.Composition) {
        candidateStrip.removeAllViews()
        candidateBar.visibility = if (composition.isEmpty) GONE else VISIBLE
        if (composition.isEmpty) return
        val pad = (12 * density).toInt()
        fun chip(text: String, color: Int, index: Int?) = TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(pad, 0, pad, 0)
            if (index != null) setOnClickListener { onCandidate?.invoke(index) }
        }
        candidateStrip.addView(chip(composition.preedit.ifEmpty { composition.typed }, colors.text, null).apply { alpha = 0.6f }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        composition.candidates.forEachIndexed { index, text ->
            candidateStrip.addView(chip(text, colors.text, index), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }
        candidateBar.scrollTo(0, 0)
    }

    /** Shows [state]: rebuilds the keys when the page changes, otherwise only updates capitals and the shift icon. */
    fun render(state: KeyboardState) {
        lastState = state
        if (builtPage != state.page) build(state.page)
        val capitals = state.page == KeyboardPage.LETTERS && state.shift != ShiftState.OFF
        for ((spec, view) in letterKeys) {
            view.label = spec.label ?: if (capitals) KeySpec.Character.capital(spec.text) else spec.text
            view.contentDescription = view.label
        }
        shiftKey?.let { key ->
            val (drawable, description) = when (state.shift) {
                ShiftState.OFF -> R.drawable.ic_key_shift to R.string.key_shift
                ShiftState.ONCE -> R.drawable.ic_key_shift_on to R.string.key_shift_on
                ShiftState.CAPS_LOCK -> R.drawable.ic_key_caps to R.string.key_caps_lock
            }
            key.icon = ContextCompat.getDrawable(context, drawable)
            key.contentDescription = context.getString(description)
        }
    }

    private fun build(page: KeyboardPage) {
        keysColumn.removeAllViews()
        letterKeys.clear()
        shiftKey = null
        // No layout margin between keys any more: each key's touch target fills its full share of the row (and the
        // row fills its full share of the key area), meeting its neighbours exactly at the midpoint. The visible gap
        // between keys is drawn inside KeyCapView itself (see its `visualInset`), so the look is unchanged but a
        // finger landing near a boundary is never lost to a dead zone between two keys (CLAUDE.md feedback: keys
        // were too small and easy to miss onto a neighbour).
        val hasExtras = KeyboardLayouts.hasExtras(typingLanguage, keyboardStyle)
        for ((index, row) in KeyboardLayouts.rows(page, typingLanguage, keyboardStyle).withIndex()) {
            val slim = hasExtras && index == 0
            val rowHeight = ((if (slim) EXTRAS_KEY_HEIGHT_DP else KEY_HEIGHT_DP) * density).toInt()
            val rowView = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (spec in row) {
                val view = createKey(spec)
                rowView.addView(view, LayoutParams(0, LayoutParams.MATCH_PARENT, spec.weight))
            }
            keysColumn.addView(rowView, LayoutParams(LayoutParams.MATCH_PARENT, rowHeight))
        }
        builtPage = page
    }

    private fun createKey(spec: KeySpec): View = when (spec) {
        is KeySpec.Spacer -> View(context)
        is KeySpec.Character -> KeyCapView(context, KeyLook.LETTER, colors) { onKey?.invoke(spec) }.also {
            it.label = spec.label ?: spec.text
            if (spec.flick.isNotEmpty()) {
                it.flick = spec.flick
                it.onFlick = { form -> onKey?.invoke(KeySpec.Character(form)) }
            }
            if (spec.alternates.isNotEmpty()) {
                it.alternates = {
                    val capitals = lastState?.let { s -> s.page == KeyboardPage.LETTERS && s.shift != ShiftState.OFF } == true
                    spec.alternates.map { form -> if (capitals) KeySpec.Character.capital(form) else form }
                }
                it.onAlternate = { form -> onKey?.invoke(KeySpec.Character(form.lowercase(Locale.ROOT))) }
            }
            letterKeys += spec to it
        }
        is KeySpec.Function -> createFunctionKey(spec)
    }

    private fun createFunctionKey(spec: KeySpec.Function): KeyCapView {
        val action = spec.action
        val look = if (action == KeyAction.ENTER) KeyLook.ACTION else if (action == KeyAction.SPACE) KeyLook.LETTER else KeyLook.FUNCTION
        val key = KeyCapView(context, look, colors, repeatable = action == KeyAction.BACKSPACE) { onKey?.invoke(spec) }
        when (action) {
            KeyAction.SHIFT -> shiftKey = key
            KeyAction.BACKSPACE -> key.setIcon(R.drawable.ic_key_backspace, R.string.key_backspace)
            KeyAction.ENTER -> key.setIcon(R.drawable.ic_key_enter, R.string.key_enter)
            KeyAction.SWITCH_KEYBOARD -> key.setIcon(R.drawable.ic_key_globe, R.string.key_switch_keyboard)
            KeyAction.HANDWRITING -> key.setIcon(R.drawable.ic_key_pen, R.string.key_handwriting)
            KeyAction.SPACE -> {
                key.label = com.alterlingua.app.learning.Languages.fromCode(typingLanguage)?.nativeName ?: context.getString(R.string.keyboard_name)
                key.labelSizeSp = SPACE_LABEL_SP
                key.contentDescription = context.getString(R.string.key_space)
            }
            KeyAction.SHOW_SYMBOLS -> key.setText("?123", R.string.key_symbols)
            KeyAction.SHOW_MORE_SYMBOLS -> key.setText("=\\<", R.string.key_more_symbols)
            KeyAction.SHOW_LETTERS -> key.setText("ABC", R.string.key_letters)
        }
        return key
    }

    private fun KeyCapView.setIcon(drawable: Int, description: Int) {
        icon = ContextCompat.getDrawable(context, drawable)
        contentDescription = context.getString(description)
    }

    private fun KeyCapView.setText(text: String, description: Int) {
        label = text
        contentDescription = context.getString(description)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Nudges a fresh IME window into delivering its real navigation-bar inset promptly, rather than waiting for
        // whatever triggers it to be dispatched on its own; the listener above then applies the accurate value.
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideNotice)
        super.onDetachedFromWindow()
    }

    private companion object {
        // A conservative minimum for a gesture-navigation handle area (Android's own is commonly 24-48dp); used only
        // as a starting value until the real inset arrives (see the comment in `init`), never as a source of truth.
        const val MIN_NAVIGATION_BAR_INSET_DP = 48
        const val KEY_HEIGHT_DP = 48
        const val EXTRAS_KEY_HEIGHT_DP = 40
        const val HANDWRITING_HEIGHT_DP = 232
        const val SPACE_LABEL_SP = 14f
        const val CANDIDATE_BAR_HEIGHT_DP = 44
        const val TOOLBAR_HEIGHT_DP = 44
        const val EDIT_HEADER_HEIGHT_DP = 84
        const val NOTICE_MILLIS = 2500L
    }
}
