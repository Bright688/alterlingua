package com.alterlingua.app.keyboard.handwriting

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.alterlingua.app.R
import com.alterlingua.app.keyboard.KeyCapView
import com.alterlingua.app.keyboard.KeyLook
import com.alterlingua.app.keyboard.KeyboardColors

/** What the pad asks the keyboard to do. */
interface HandwritingActions {
    fun onStrokeStarted()
    fun onStrokeFinished(stroke: InkStroke)

    /** The writer chose candidate [index]. */
    fun onCandidate(index: Int)
    fun onBackspace()
    fun onSpace()
    fun onEnter()
    fun onPeriod()
    fun onSwitchKeyboard()

    /** Back to the letter keys. */
    fun onBackToKeys()
    fun onRetry()
}

/**
 * The handwriting pad: candidates on top, a pad to write on with backspace and enter beside it, and a bottom row to go back to the
 * keys. It takes the place of the keys.
 */
class HandwritingPanel(
    private val context: Context,
    private val colors: KeyboardColors,
    private val actions: HandwritingActions,
) {
    private val density = context.resources.displayMetrics.density
    val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val stripScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(strip) }
    private val pad = DrawingView(context, colors.text, actions::onStrokeStarted) { stroke -> actions.onStrokeFinished(stroke) }
    private val message = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setTextColor(colors.functionText)
        setPadding(dp(16), 0, dp(16), 0)
    }
    private val spaceKey: KeyCapView

    private fun dp(value: Int) = (value * density).toInt()

    init {
        val padColor = colors.letterKey
        val padArea = FrameLayout(context).apply {
            background = GradientDrawable().apply { setColor(padColor); cornerRadius = 10 * density }
            addView(pad, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(message, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
        val backspace = KeyCapView(context, KeyLook.FUNCTION, colors, repeatable = false) { actions.onBackspace() }.apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_key_backspace)
            contentDescription = context.getString(R.string.key_backspace)
        }
        val enter = KeyCapView(context, KeyLook.ACTION, colors) { actions.onEnter() }.apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_key_enter)
            contentDescription = context.getString(R.string.key_enter)
        }
        val side = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(backspace, LinearLayout.LayoutParams(dp(56), 0, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            addView(enter, LinearLayout.LayoutParams(dp(56), 0, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
        }
        val middle = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(padArea, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            addView(side, LinearLayout.LayoutParams(dp(60), LinearLayout.LayoutParams.MATCH_PARENT))
        }
        val back = KeyCapView(context, KeyLook.FUNCTION, colors) { actions.onBackToKeys() }.apply {
            label = "ABC"
            contentDescription = context.getString(R.string.key_letters)
        }
        val globe = KeyCapView(context, KeyLook.FUNCTION, colors) { actions.onSwitchKeyboard() }.apply {
            icon = ContextCompat.getDrawable(context, R.drawable.ic_key_globe)
            contentDescription = context.getString(R.string.key_switch_keyboard)
        }
        spaceKey = KeyCapView(context, KeyLook.LETTER, colors) { actions.onSpace() }.apply {
            label = context.getString(R.string.hw_label)
            labelSizeSp = 13f
            contentDescription = context.getString(R.string.key_space)
        }
        val period = KeyCapView(context, KeyLook.LETTER, colors) { actions.onPeriod() }.apply { label = "." }
        val bottom = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            fun add(view: View, weight: Float) = addView(view, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })
            add(back, 1.3f); add(globe, 1f); add(spaceKey, 5.2f); add(period, 1f)
        }
        body.addView(stripScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(42)))
        body.addView(middle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        body.addView(bottom, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)))
    }

    /** Shows [state]: the candidates, or a message while the language model is prepared, downloaded, or could not be had. */
    fun render(state: HandwritingState, languageName: String) {
        strip.removeAllViews()
        message.visibility = View.GONE
        when (state.status) {
            HandwritingStatus.PREPARING -> showMessage(context.getString(R.string.hw_preparing, languageName))
            HandwritingStatus.DOWNLOADING -> showMessage(context.getString(R.string.hw_downloading, languageName))
            HandwritingStatus.FAILED -> {
                showMessage(context.getString(R.string.hw_failed))
                strip.addView(chip(context.getString(R.string.hw_retry), colors.accent, null) { actions.onRetry() })
            }
            HandwritingStatus.READY -> {
                if (state.candidates.isEmpty() && !state.hasInk) showMessage(context.getString(R.string.hw_hint))
                state.candidates.forEachIndexed { index, text -> strip.addView(chip(text, colors.text, index) { actions.onCandidate(index) }) }
            }
        }
        if (!state.hasInk) pad.clear()
        stripScroll.scrollTo(0, 0)
    }

    private fun showMessage(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
    }

    private fun chip(text: String, color: Int, index: Int?, onClick: () -> Unit) = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(dp(14), 0, dp(14), 0)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
        contentDescription = text
    }
}
