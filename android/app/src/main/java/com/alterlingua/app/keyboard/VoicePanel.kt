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
import com.alterlingua.app.learning.Language
import java.util.Locale

/** What the user asked for on the voice panel. */
enum class VoiceAction { STOP, CANCEL, ALLOW_MICROPHONE, INSERT, EDIT, LISTEN, RECORD_AGAIN, RETRY, INSERT_HEARD }

/**
 * The compact voice panel: a header strip that replaces the toolbar and a body that replaces the keys.
 * While the translation is being edited only the header shows, so the keys stay available for typing.
 */
class VoicePanel(
    private val context: Context,
    private val colors: KeyboardColors,
    private val onAction: (VoiceAction) -> Unit,
) {
    val header = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE // screen readers read each new state
    }
    val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val density = context.resources.displayMetrics.density
    private var timerView: TextView? = null
    private var waveform: WaveformView? = null

    /** True while the header should be taller than the toolbar (the translation is being edited). */
    var wantsTallHeader = false
        private set

    /** True while the panel replaces the keys. */
    var coversKeys = false
        private set

    fun render(state: VoiceUiState) {
        // The recording changes ten times a second: only the timer and waveform are updated then.
        if (state is VoiceUiState.Recording && timerView != null && waveform != null) {
            timerView?.text = formatTime(state.elapsedMillis)
            timerView?.contentDescription = context.getString(R.string.voice_duration_description, formatTime(state.elapsedMillis))
            waveform?.setLevels(state.levels)
            return
        }
        header.removeAllViews()
        body.removeAllViews()
        timerView = null
        waveform = null
        wantsTallHeader = false
        coversKeys = state != VoiceUiState.Idle
        val pad = (10 * density).toInt()
        header.setPadding(pad, 0, (6 * density).toInt(), 0)
        body.setPadding(pad, (2 * density).toInt(), pad, (2 * density).toInt())

        when (state) {
            VoiceUiState.Idle -> Unit
            VoiceUiState.NeedsPermission -> {
                titleHeader(context.getString(R.string.voice_permission_title), closable = true)
                body.addView(text(context.getString(R.string.voice_permission_body), 14f, bold = false, color = colors.text), fill())
                body.addView(row(button(context.getString(R.string.voice_allow), ToolbarButtonStyle.PRIMARY, VoiceAction.ALLOW_MICROPHONE)))
            }
            is VoiceUiState.Recording -> renderRecording(state)
            is VoiceUiState.Understanding -> renderWaiting(context.getString(R.string.voice_understanding))
            is VoiceUiState.Translating -> renderWaiting(context.getString(R.string.voice_translating_to, state.target.displayName))
            is VoiceUiState.Result -> if (state.editing) renderEditing(state) else renderResult(state)
            is VoiceUiState.Failed -> renderFailed(state)
        }
    }

    // ---- states ----

    private fun renderRecording(state: VoiceUiState.Recording) {
        val dot = text("●", 14f, bold = false, color = ERROR_RED)
        header.addView(dot, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        header.addView(
            text(context.getString(R.string.voice_speak_in, state.spoken.displayName), 14f, bold = true, color = colors.text),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = (6 * density).toInt() },
        )
        val timer = text(formatTime(state.elapsedMillis), 14f, bold = true, color = colors.functionText)
        timer.contentDescription = context.getString(R.string.voice_duration_description, formatTime(state.elapsedMillis))
        header.addView(timer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        timerView = timer

        val wave = WaveformView(context, colors.actionKey, colors.outline)
        wave.setLevels(state.levels)
        waveform = wave
        body.addView(wave, fill())
        body.addView(
            row(
                button(context.getString(R.string.voice_cancel), ToolbarButtonStyle.TONAL, VoiceAction.CANCEL),
                button(context.getString(R.string.voice_stop), ToolbarButtonStyle.PRIMARY, VoiceAction.STOP),
            ),
        )
    }

    private fun renderWaiting(message: String) {
        val spinner = ProgressBar(context).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(colors.actionKey)
        }
        header.addView(spinner, LinearLayout.LayoutParams((20 * density).toInt(), (20 * density).toInt()))
        header.addView(
            text(message, 14f, bold = true, color = colors.text),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = (8 * density).toInt() },
        )
        body.addView(
            text(context.getString(R.string.voice_upload_note), 13f, bold = false, color = colors.functionText, gravity = Gravity.CENTER),
            fill(),
        )
        body.addView(row(button(context.getString(R.string.voice_cancel), ToolbarButtonStyle.TONAL, VoiceAction.CANCEL)))
    }

    private fun renderResult(state: VoiceUiState.Result) {
        titleHeader(context.getString(R.string.voice_title), closable = true)
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        column.addView(text(context.getString(R.string.voice_original, languageName(state.original)), 12f, bold = true, color = colors.functionText))
        column.addView(text(state.originalText, 14f, bold = false, color = colors.text))
        column.addView(
            text(context.getString(R.string.voice_translated, state.target.displayName), 12f, bold = true, color = colors.functionText),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() },
        )
        column.addView(text(state.translation, 16f, bold = true, color = colors.text))
        body.addView(ScrollView(context).apply { addView(column) }, fill())
        body.addView(
            row(
                button(context.getString(R.string.voice_insert), ToolbarButtonStyle.PRIMARY, VoiceAction.INSERT, weight = 2f),
                button(context.getString(R.string.voice_edit), ToolbarButtonStyle.TONAL, VoiceAction.EDIT),
            ),
        )
        body.addView(
            row(
                button(
                    context.getString(if (state.playing) R.string.voice_stop_listening else R.string.voice_listen),
                    ToolbarButtonStyle.TONAL,
                    VoiceAction.LISTEN,
                ),
                button(context.getString(R.string.voice_record_again), ToolbarButtonStyle.TONAL, VoiceAction.RECORD_AGAIN),
            ),
        )
        coversKeys = true
    }

    /** Editing: the keys are visible, so only the header shows the text being edited, with the Insert button. */
    private fun renderEditing(state: VoiceUiState.Result) {
        wantsTallHeader = true
        coversKeys = false
        val preview = text(state.translation + "▏", 15f, bold = true, color = colors.text, maxLines = 3)
        header.addView(preview, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(
            button(context.getString(R.string.voice_insert), ToolbarButtonStyle.PRIMARY, VoiceAction.INSERT),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (34 * density).toInt()).apply { leftMargin = (6 * density).toInt() },
        )
        header.addView(closeButton(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (34 * density).toInt()).apply { leftMargin = (6 * density).toInt() })
    }

    private fun renderFailed(state: VoiceUiState.Failed) {
        titleHeader(context.getString(R.string.voice_title), closable = true)
        body.addView(text(context.getString(state.failure.messageRes()), 14f, bold = false, color = colors.text), fill())
        state.heard?.takeIf { it.isNotBlank() }?.let {
            body.addView(text(context.getString(R.string.voice_heard, it), 13f, bold = false, color = colors.functionText, maxLines = 3))
        }
        val actions = mutableListOf<ToolbarButtonView>()
        if (state.canRetry) actions += button(context.getString(R.string.voice_retry), ToolbarButtonStyle.PRIMARY, VoiceAction.RETRY)
        if (!state.heard.isNullOrBlank()) actions += button(context.getString(R.string.voice_insert_heard), ToolbarButtonStyle.PRIMARY, VoiceAction.INSERT_HEARD)
        if (state.failure != com.alterlingua.app.translation.VoiceFailure.NOT_AVAILABLE_HERE) {
            actions += button(context.getString(R.string.voice_record_again), ToolbarButtonStyle.TONAL, VoiceAction.RECORD_AGAIN)
        }
        if (actions.isNotEmpty()) body.addView(row(*actions.toTypedArray()))
    }

    // ---- building blocks ----

    private fun titleHeader(title: String, closable: Boolean) {
        header.addView(text(title, 14f, bold = true, color = colors.text), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (closable) header.addView(closeButton(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, (34 * density).toInt()))
    }

    private fun closeButton() = ToolbarButtonView(context, ToolbarButtonStyle.TONAL, colors) { onAction(VoiceAction.CANCEL) }.apply {
        leadingIcon = ContextCompat.getDrawable(context, R.drawable.ic_tool_close)
        contentDescription = context.getString(R.string.voice_close)
    }

    private fun button(label: String, style: ToolbarButtonStyle, action: VoiceAction, weight: Float = 1f) =
        ToolbarButtonView(context, style, colors) { onAction(action) }.apply {
            this.label = label
            tag = weight
        }

    /** A row of buttons sharing the width. */
    private fun row(vararg buttons: ToolbarButtonView): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        val gap = (3 * density).toInt()
        for (button in buttons) {
            addView(button, LinearLayout.LayoutParams(0, (36 * density).toInt(), button.tag as Float).apply { setMargins(gap, gap, gap, gap) })
        }
    }

    private fun fill() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

    private fun text(value: String, sizeSp: Float, bold: Boolean, color: Int, maxLines: Int = Int.MAX_VALUE, gravity: Int = Gravity.START) =
        TextView(context).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
            this.maxLines = maxLines
            if (maxLines != Int.MAX_VALUE) ellipsize = TextUtils.TruncateAt.END
            this.gravity = gravity
        }

    private fun languageName(language: Language?) = language?.displayName ?: "?"

    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }

    private companion object {
        const val ERROR_RED = 0xFFBA1A1A.toInt()
    }
}
