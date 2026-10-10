package com.alterlingua.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.alterlingua.app.R
import com.alterlingua.app.learning.Language

/**
 * The "Translate to" list shown in place of the keys. Each language shows its own name first (CLAUDE.md 6.2), with a
 * search field (CLAUDE.md 6.8) to narrow a list of up to eight languages down by native or English name or code.
 */
@SuppressLint("ViewConstructor") // only ever created in code by the keyboard view
class LanguagePanelView(
    context: Context,
    private val colors: KeyboardColors,
    private val onSelect: (Language) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val grid = LinearLayout(context).apply { orientation = VERTICAL }
    private val emptyState = TextView(context).apply {
        text = context.getString(R.string.toolbar_no_languages_found)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTextColor(colors.functionText)
        gravity = Gravity.CENTER
        visibility = GONE
    }

    private var allLanguages: List<Language> = emptyList()
    private var selectedLanguage: Language? = null

    private val searchField = EditText(context).apply {
        hint = context.getString(R.string.toolbar_search_languages)
        setSingleLine(true)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(colors.text)
        setHintTextColor(colors.functionText)
        setPadding((10 * density).toInt(), 0, (10 * density).toInt(), 0)
        // GradientDrawable has its own getColors()/setColors(IntArray) pair, which Kotlin would otherwise treat as a
        // synthetic `colors` property shadowing the outer KeyboardColors inside `apply {}` — `also { d -> ... }` with
        // an explicit receiver name avoids that entirely.
        background = GradientDrawable().also { d ->
            d.shape = GradientDrawable.RECTANGLE
            d.cornerRadius = 10 * density
            d.setColor(colors.letterKey)
            d.setStroke((1 * density).toInt(), colors.outline)
        }
        imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        doAfterTextChanged { applyFilter() }
    }

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
        addView(
            searchField,
            LayoutParams(LayoutParams.MATCH_PARENT, (SEARCH_HEIGHT_DP * density).toInt()).apply {
                val margin = (4 * density).toInt()
                leftMargin = margin; rightMargin = margin; bottomMargin = margin
            },
        )
        // Scrollable, not a fixed height: the search field shares the panel's existing height budget with the list,
        // so on a shorter keyboard (or with every language still matching) the list scrolls rather than clipping.
        val listArea = LinearLayout(context).apply { orientation = VERTICAL }
        listArea.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        listArea.addView(
            emptyState,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * density).toInt() },
        )
        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            addView(listArea, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    /** Shows [languages] in two columns, with [selected] marked. The search field is cleared, so every fresh open starts unfiltered. */
    fun setLanguages(languages: List<Language>, selected: Language?) {
        allLanguages = languages
        selectedLanguage = selected
        searchField.setText("")
        applyFilter()
    }

    private fun applyFilter() {
        val query = searchField.text?.toString()?.trim().orEmpty()
        val matches = if (query.isEmpty()) {
            allLanguages
        } else {
            allLanguages.filter {
                it.nativeName.contains(query, ignoreCase = true) || it.englishName.contains(query, ignoreCase = true) || it.code.contains(query, ignoreCase = true)
            }
        }
        renderGrid(matches)
        grid.visibility = if (matches.isEmpty()) GONE else VISIBLE
        emptyState.visibility = if (matches.isEmpty()) VISIBLE else GONE
    }

    private fun renderGrid(languages: List<Language>) {
        grid.removeAllViews()
        val gap = (2 * density).toInt()
        languages.chunked(2).forEach { pair ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            for (index in 0 until 2) {
                val language = pair.getOrNull(index)
                val cell: View = if (language == null) {
                    View(context)
                } else {
                    LanguageOptionView(context, colors, language, language.code == selectedLanguage?.code) { onSelect(language) }
                }
                row.addView(cell, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { setMargins(gap, gap, gap, gap) })
            }
            grid.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, (ROW_HEIGHT_DP * density).toInt()))
        }
    }

    companion object {
        const val HEADER_HEIGHT_DP = 40
        const val SEARCH_HEIGHT_DP = 36
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
