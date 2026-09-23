package com.alterlingua.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/** How a key looks. */
enum class KeyLook { LETTER, FUNCTION, ACTION }

/**
 * One key, drawn by hand so it stays light and fast. Real views (rather than one big canvas) mean each key gets
 * its own touch stream, so fast two-thumb typing works, and each key is visible to screen readers.
 */
@SuppressLint("ViewConstructor") // only ever created in code by the keyboard view
class KeyCapView(
    context: Context,
    private val look: KeyLook,
    private val colors: KeyboardColors,
    private val repeatable: Boolean = false,
    private val onPress: () -> Unit,
) : View(context) {

    var label: String? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var icon: Drawable? = null
        set(value) {
            if (field !== value) {
                field = value?.mutate()
                invalidate()
            }
        }

    /** A label size in sp that replaces the default (used by the space bar, whose language name is smaller than a letter). */
    var labelSizeSp: Float? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    /** Typed by flicking the key left, up, right or down (the kana keyboard), in that order. Empty for most keys. */
    var flick: List<String> = emptyList()

    /** Called with the flicked-to form. */
    var onFlick: ((String) -> Unit)? = null

    private var downX = 0f
    private var downY = 0f
    private var flicked = false

    /** The accented forms shown when the key is held, in the current capitalisation. Empty for most keys. */
    var alternates: () -> List<String> = { emptyList() }

    /** Called with the accented form the user chose. */
    var onAlternate: ((String) -> Unit)? = null

    private var popup: PopupWindow? = null

    private val density = resources.displayMetrics.density
    private val radius = 8 * density
    private val shadowOffset = 1 * density
    // The visible key is drawn a little smaller than the view itself, so neighbouring keys still look separated by a
    // gap even though their actual touch targets now meet edge to edge at the midpoint (no dead zone between them:
    // a finger landing near a boundary always registers as whichever key it is actually closer to). Previously this
    // gap was a real layout margin, which shrank the touch target by the same amount on every side (CLAUDE.md
    // feedback: keys were too small and easy to miss onto a neighbour).
    private val visualInset = 1.5f * density
    private val bounds = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private val repeatRunnable = object : Runnable {
        override fun run() {
            fire(haptic = false)
            postDelayed(this, REPEAT_INTERVAL_MILLIS)
        }
    }

    init {
        isClickable = true
        isFocusable = false
        // A tap (including one from a screen reader) types once. Touch on a repeating key is handled below.
        setOnClickListener { fire(haptic = true) }
        setOnLongClickListener { showAlternates() }
    }

    /** Shows the accented forms in a small strip above the key; tapping one types it. Returns false when there are none. */
    private fun showAlternates(): Boolean {
        val forms = alternates()
        if (forms.isEmpty()) return false
        dismissAlternates()
        val strip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(colors.functionKey)
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
        }
        for (form in forms) {
            strip.addView(
                TextView(context).apply {
                    text = form
                    textSize = 22f
                    gravity = Gravity.CENTER
                    setTextColor(colors.text)
                    contentDescription = form
                    setOnClickListener {
                        dismissAlternates()
                        onAlternate?.invoke(form)
                    }
                },
                LinearLayout.LayoutParams((40 * density).toInt(), (46 * density).toInt()),
            )
        }
        strip.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val window = PopupWindow(strip, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, false)
        window.isOutsideTouchable = true
        window.elevation = 8 * density
        popup = window
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        window.showAsDropDown(this, (width - strip.measuredWidth) / 2, -(height + strip.measuredHeight))
        return true
    }

    private fun dismissAlternates() {
        popup?.dismiss()
        popup = null
    }

    private fun fire(haptic: Boolean) {
        if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        onPress()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val base = when {
            look == KeyLook.ACTION -> if (isPressed) colors.actionKeyPressed else colors.actionKey
            isPressed -> colors.pressed
            look == KeyLook.FUNCTION -> colors.functionKey
            else -> colors.letterKey
        }
        // A thin darker edge under the key gives the small lift the design shows. Both rects are drawn inset from
        // the view's own edges (see [visualInset]): the touch target is the full view, the drawn key is smaller.
        fill.color = colors.keyShadow
        bounds.set(visualInset, shadowOffset + visualInset, w - visualInset, h - visualInset)
        canvas.drawRoundRect(bounds, radius, radius, fill)
        fill.color = base
        bounds.set(visualInset, visualInset, w - visualInset, h - shadowOffset - visualInset)
        canvas.drawRoundRect(bounds, radius, radius, fill)

        val contentColor = if (look == KeyLook.ACTION) colors.onAction else if (look == KeyLook.FUNCTION) colors.functionText else colors.text
        val cy = (h - shadowOffset) / 2f
        icon?.let { drawable ->
            val size = (24 * density).toInt()
            drawable.setTint(contentColor)
            drawable.setBounds(
                (w / 2f - size / 2f).toInt(), (cy - size / 2f).toInt(),
                (w / 2f + size / 2f).toInt(), (cy + size / 2f).toInt(),
            )
            drawable.draw(canvas)
        }
        label?.let { text ->
            val isLetter = look == KeyLook.LETTER
            textPaint.color = contentColor
            textPaint.typeface = if (isLetter) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
            textPaint.textSize = sp(labelSizeSp ?: if (isLetter) 23f else 15f)
            val metrics = textPaint.fontMetrics
            canvas.drawText(text, w / 2f, cy - (metrics.ascent + metrics.descent) / 2f, textPaint)
        }
    }

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    // Touch on a repeating key is handled in onTouchEvent; this keeps clicks from screen readers working.
    override fun performClick(): Boolean = super.performClick()

    // Delete types on touch-down and repeats while held, so it deliberately does not wait for a click.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (flick.isNotEmpty()) return flickTouch(event)
        if (!repeatable) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                fire(haptic = true)
                postDelayed(repeatRunnable, REPEAT_START_DELAY_MILLIS)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> stopRepeating()
            MotionEvent.ACTION_MOVE -> if (event.x < 0 || event.x > width || event.y < 0 || event.y > height) stopRepeating()
        }
        return true
    }

    /** A tap types the key; a flick left, up, right or down types the matching form; holding still opens the list of forms. */
    private fun flickTouch(event: MotionEvent): Boolean {
        val threshold = 24 * density
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                flicked = false
                return super.onTouchEvent(event)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!flicked && (kotlin.math.abs(dx) > threshold || kotlin.math.abs(dy) > threshold)) {
                    flicked = true
                    cancelLongPress()
                    dismissAlternates()
                    isPressed = false
                }
                return if (flicked) true else super.onTouchEvent(event)
            }
            MotionEvent.ACTION_UP -> {
                if (!flicked) return super.onTouchEvent(event)
                val dx = event.x - downX
                val dy = event.y - downY
                val index = if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) (if (dx < 0) 0 else 2) else (if (dy < 0) 1 else 3)
                flicked = false
                isPressed = false
                flick.getOrNull(index)?.let {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onFlick?.invoke(it)
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                flicked = false
                return super.onTouchEvent(event)
            }
        }
        return super.onTouchEvent(event)
    }

    private fun stopRepeating() {
        removeCallbacks(repeatRunnable)
        isPressed = false
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(repeatRunnable)
        dismissAlternates()
        super.onDetachedFromWindow()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Button::class.java.name
    }

    private companion object {
        const val REPEAT_START_DELAY_MILLIS = 400L
        const val REPEAT_INTERVAL_MILLIS = 50L
    }
}
