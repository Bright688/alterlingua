package com.alterlingua.app.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.alterlingua.app.R
import com.alterlingua.app.share.SharedVoiceMessages
import com.alterlingua.app.share.SharedVoiceState
import com.alterlingua.app.ui.UiText

/** What the user asked for on the captured-voice-note panel. */
enum class CapturedNoteAction { LISTEN, OPEN, RETRY, DISMISS, TRANSLATE }

/** What the keyboard shows about a captured voice note. Holds private text, so it is never printed. */
sealed interface CapturedNoteUi {
    data object Hidden : CapturedNoteUi

    /** The recording has ended and is being transcribed and translated. */
    data object Working : CapturedNoteUi

    /** The recording is kept on the phone until the user asks for it to be translated (automatic translation is off). */
    data object Waiting : CapturedNoteUi

    data class Result(
        val originalLanguage: String,
        val original: String,
        val userLanguage: String,
        val translation: String,
        /** The voice note is already in the user's language, so there is only one text to show. */
        val sameLanguage: Boolean,
        val unclear: Boolean,
        val canListen: Boolean,
        val playing: Boolean,
    ) : CapturedNoteUi {
        override fun toString() = "CapturedNoteUi.Result(redacted)"
    }

    data class Failed(val message: UiText, val canRetry: Boolean) : CapturedNoteUi
}

/** Turns the state of a voice note's processing into what the keyboard shows. Pure, so it is tested without Android. */
fun capturedNoteUi(state: SharedVoiceState?, started: Boolean = true): CapturedNoteUi = when {
    state == null || state == SharedVoiceState.Closed -> CapturedNoteUi.Hidden
    !started -> CapturedNoteUi.Waiting
    else -> capturedNoteUiOf(state)
}

private fun capturedNoteUiOf(state: SharedVoiceState): CapturedNoteUi = when (state) {
    SharedVoiceState.Closed -> CapturedNoteUi.Hidden
    SharedVoiceState.Idle, is SharedVoiceState.Working -> CapturedNoteUi.Working
    is SharedVoiceState.Failed -> CapturedNoteUi.Failed(SharedVoiceMessages.forState(state).message, state.canRetry)
    is SharedVoiceState.Result -> with(state.value) {
        CapturedNoteUi.Result(
            originalLanguage = originalLanguage?.nativeName ?: originalCode.uppercase(),
            original = transcript,
            userLanguage = userLanguage.nativeName,
            translation = translation,
            sameLanguage = alreadyInYourLanguage,
            unclear = unclear,
            canListen = canListen,
            playing = playing,
        )
    }
}

/**
 * The captured-voice-note panel: a header strip that replaces the toolbar and a body that replaces the keys. It shows the
 * transcript and the translation of a voice note the listening session captured, with Listen and Open (the full screen).
 * It never types anything into the chat: it is for reading a note someone sent, not for writing a reply.
 */
class CapturedNotePanel(
    private val context: Context,
    private val colors: KeyboardColors,
    private val onAction: (CapturedNoteAction) -> Unit,
) {
    val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE // screen readers read each new state
    }
    val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val density = context.resources.displayMetrics.density

    fun render(state: CapturedNoteUi) {
        header.removeAllViews()
        body.removeAllViews()
        val pad = (10 * density).toInt()
        header.setPadding(pad, 0, (6 * density).toInt(), 0)
        body.setPadding(pad, (2 * density).toInt(), pad, (2 * density).toInt())
        when (state) {
            CapturedNoteUi.Hidden -> Unit
            CapturedNoteUi.Working -> renderWorking()
            CapturedNoteUi.Waiting -> renderWaiting()
            is CapturedNoteUi.Result -> renderResult(state)
            is CapturedNoteUi.Failed -> renderFailed(state)
        }
    }

    private fun renderWorking() {
        val spinner = ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(colors.actionKey)
        }
        header.addView(spinner, LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt()))
        header.addView(
            text(context.getString(R.string.kbn_working), 14f, bold = true, color = colors.text),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = (8 * density).toInt() },
        )
        header.addView(closeButton(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (34 * density).toInt()))
        body.addView(text(context.getString(R.string.voice_upload_note), 13f, bold = false, color = colors.functionText, gravity = Gravity.CENTER), fill())
    }

    private fun renderWaiting() {
        header.addView(text(context.getString(R.string.kbn_title), 14f, bold = true, color = colors.text), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(closeButton(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (34 * density).toInt()))
        body.addView(text(context.getString(R.string.kbn_waiting), 14f, bold = false, color = colors.text, gravity = Gravity.CENTER), fill())
        body.addView(row(button(context.getString(R.string.kbn_translate), ToolbarButtonStyle.PRIMARY, CapturedNoteAction.TRANSLATE)))
    }

    private fun renderResult(state: CapturedNoteUi.Result) {
        header.addView(text(context.getString(R.string.kbn_title), 14f, bold = true, color = colors.text), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(closeButton(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (34 * density).toInt()))
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        if (state.unclear) column.addView(text(context.getString(R.string.kbn_unclear), 12f, bold = true, color = ERROR_RED))
        if (state.sameLanguage) {
            column.addView(text(context.getString(R.string.sv_same_language, state.userLanguage), 12f, bold = true, color = colors.functionText))
            column.addView(text(state.original, 16f, bold = true, color = colors.text))
        } else {
            column.addView(text(context.getString(R.string.voice_original, state.originalLanguage), 12f, bold = true, color = colors.functionText))
            column.addView(text(state.original, 14f, bold = false, color = colors.text))
            column.addView(
                text(context.getString(R.string.voice_translated, state.userLanguage), 12f, bold = true, color = colors.functionText),
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() },
            )
            column.addView(text(state.translation, 16f, bold = true, color = colors.text))
        }
        body.addView(ScrollView(context).apply { addView(column) }, fill())
        val actions = mutableListOf<ToolbarButtonView>()
        if (state.canListen && !state.sameLanguage) {
            actions += button(context.getString(if (state.playing) R.string.voice_stop_listening else R.string.voice_listen), ToolbarButtonStyle.TONAL, CapturedNoteAction.LISTEN)
        }
        actions += button(context.getString(R.string.kbn_open), ToolbarButtonStyle.PRIMARY, CapturedNoteAction.OPEN)
        body.addView(row(*actions.toTypedArray()))
    }

    private fun renderFailed(state: CapturedNoteUi.Failed) {
        header.addView(text(context.getString(R.string.kbn_title), 14f, bold = true, color = colors.text), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(closeButton(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (34 * density).toInt()))
        body.addView(text(context.getString(state.message.id, *state.message.args.toTypedArray()), 14f, bold = false, color = colors.text), fill())
        val actions = mutableListOf<ToolbarButtonView>()
        if (state.canRetry) actions += button(context.getString(R.string.voice_retry), ToolbarButtonStyle.PRIMARY, CapturedNoteAction.RETRY)
        actions += button(context.getString(R.string.kbn_open), ToolbarButtonStyle.TONAL, CapturedNoteAction.OPEN)
        body.addView(row(*actions.toTypedArray()))
    }

    // ---- building blocks ----

    private fun closeButton() = ToolbarButtonView(context, ToolbarButtonStyle.TONAL, colors) { onAction(CapturedNoteAction.DISMISS) }.apply {
        leadingIcon = ContextCompat.getDrawable(context, R.drawable.ic_tool_close)
        contentDescription = context.getString(R.string.voice_close)
    }

    private fun button(label: String, style: ToolbarButtonStyle, action: CapturedNoteAction) =
        ToolbarButtonView(context, style, colors) { onAction(action) }.apply { this.label = label }

    /** A row of buttons sharing the width. */
    private fun row(vararg buttons: ToolbarButtonView): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val gap = (3 * density).toInt()
        for (button in buttons) {
            addView(button, LinearLayout.LayoutParams(0, (36 * density).toInt(), 1f).apply { setMargins(gap, gap, gap, gap) })
        }
    }

    private fun fill() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

    private fun text(value: String, sizeSp: Float, bold: Boolean, color: Int, gravity: Int = Gravity.START) =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
            this.gravity = gravity
            ellipsize = TextUtils.TruncateAt.END
        }

    private companion object {
        const val ERROR_RED = 0xFFBA1A1A.toInt()
    }
}
