package com.alterlingua.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.alterlingua.app.R
import com.alterlingua.app.learning.Language

/** The "Translate to" list shown in place of the keys. Each language shows its own name first (CLAUDE.md 6.2). */
@SuppressLint("ViewConstructor") // only ever created in code by the keyboard view
class LanguagePanelView(
    context: Context,
    private val colors: KeyboardColors,
    private val onSelect: (Language) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            TextView(context).apply {
                text = context.getString(R.string.toolbar_translate_to)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(colors.functionText)
                setPadding((8 * density).toInt(), 0, 0, 0)
            },
            LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f),
        )
        header.addView(
            ToolbarButtonView(context, ToolbarButtonStyle.TONAL, colors, onClose).apply {
                leadingIcon = ContextCompat.getDrawable(context, R.drawable.ic_tool_close)
                contentDescription = context.getString(R.string.toolbar_close)
            },
            LayoutParams(LayoutParams.WRAP_CONTENT, (34 * density).toInt()),
        )
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, (HEADER_HEIGHT_DP * density).toInt()))
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Shows [languages] in two columns, with [selected] marked. */
    fun setLanguages(languages: List<Language>, selected: Language?) {
        grid.removeAllViews()
        val gap = (2 * density).toInt()
        languages.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (index in 0 until 2) {
                val language = pair.getOrNull(index)
                val cell: View = if (language == null) {
                    View(context)
                } else {
                    LanguageOptionView(context, colors, language, language.code == selected?.code) { onSelect(language) }
                }
                row.addView(cell, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(gap, gap, gap, gap) })
            }
            grid.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, (ROW_HEIGHT_DP * density).toInt()))
        }
    }

    companion object {
        const val HEADER_HEIGHT_DP = 40
        const val ROW_HEIGHT_DP = 36
    }
}

/** One language in the list: its own name, the English name smaller beside it, and a check mark when chosen. */
@SuppressLint("ViewConstructor") // only ever created in code
private class LanguageOptionView(
    context: Context,
    private val colors: KeyboardColors,
    private val language: Language,
    private val selected: Boolean,
    private val onPress: () -> Unit,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val bounds = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 15f, resources.displayMetrics)
    }
    private val englishPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, resources.displayMetrics)
    }
    private val check = ContextCompat.getDrawable(context, R.drawable.ic_tool_check)?.mutate()

    init {
        isClickable = true
        isFocusable = false
        contentDescription = language.englishName.takeIf { it != language.nativeName }
            ?.let { "${language.nativeName}, $it" } ?: language.nativeName
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onPress()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = 8 * density
        bounds.set(0f, 0f, w, h)
        fill.color = if (isPressed) colors.pressed else if (selected) colors.functionKey else colors.letterKey
        canvas.drawRoundRect(bounds, radius, radius, fill)

        val cy = h / 2f
        var x = 12 * density
        namePaint.color = colors.text
        val nameMetrics = namePaint.fontMetrics
        canvas.drawText(language.nativeName, x, cy - (nameMetrics.ascent + nameMetrics.descent) / 2f, namePaint)
        language.secondaryName?.let { english ->
            x += namePaint.measureText(language.nativeName) + 6 * density
            englishPaint.color = colors.functionText
            val m = englishPaint.fontMetrics
            canvas.drawText(english, x, cy - (m.ascent + m.descent) / 2f, englishPaint)
        }
        if (selected) {
            check?.let {
                val size = 18 * density
                it.setTint(colors.text)
                it.setBounds((w - size - 10 * density).toInt(), (cy - size / 2f).toInt(), (w - 10 * density).toInt(), (cy + size / 2f).toInt())
                it.draw(canvas)
            }
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Button::class.java.name
        info.isSelected = selected
    }
}
