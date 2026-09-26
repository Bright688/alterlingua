package com.alterlingua.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button

enum class ToolbarButtonStyle { OUTLINED, PRIMARY, TONAL }

/**
 * A pill-shaped toolbar button: an optional status dot, leading icon, label and trailing icon. With only an icon
 * it is a round icon button.
 */
@SuppressLint("ViewConstructor") // only ever created in code by the keyboard view
class ToolbarButtonView(
    context: Context,
    private val style: ToolbarButtonStyle,
    private val colors: KeyboardColors,
    private val onPress: () -> Unit,
) : View(context) {

    var label: String? = null
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    var leadingIcon: Drawable? = null
        set(value) {
            field = value?.mutate()
            requestLayout()
            invalidate()
        }

    var trailingIcon: Drawable? = null
        set(value) {
            field = value?.mutate()
            requestLayout()
            invalidate()
        }

    var showDot = false
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    private val density = resources.displayMetrics.density
    private val iconSize = 18 * density
    private val gap = 6 * density
    private val padding = 12 * density
    private val dotSize = 8 * density
    private val bounds = RectF()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        strokeWidth = 1 * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, resources.displayMetrics)
    }

    init {
        isClickable = true
        isFocusable = false
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            onPress()
        }
    }

    private fun contentColor() = if (style == ToolbarButtonStyle.PRIMARY) colors.onAction else if (style == ToolbarButtonStyle.TONAL) colors.functionText else colors.text

    private fun contentWidth(): Float {
        var width = 0f
        val text = label
        if (showDot) width += dotSize
        if (leadingIcon != null) width += (if (width > 0) gap else 0f) + iconSize
        if (text != null) width += (if (width > 0) gap else 0f) + textPaint.measureText(text)
        if (trailingIcon != null) width += (if (width > 0) 2 * density else 0f) + iconSize
        return width
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val iconOnly = label == null && !showDot && trailingIcon == null
        val desired = if (iconOnly) (32 * density) else contentWidth() + 2 * padding
        val width = resolveSize(desired.toInt(), widthMeasureSpec)
        setMeasuredDimension(width, resolveSize((34 * density).toInt(), heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2f
        bounds.set(0f, 0f, w, h)
        fill.color = when (style) {
            ToolbarButtonStyle.PRIMARY -> if (isPressed) colors.actionKeyPressed else colors.actionKey
            ToolbarButtonStyle.TONAL -> if (isPressed) colors.pressed else colors.functionKey
            ToolbarButtonStyle.OUTLINED -> if (isPressed) colors.pressed else colors.letterKey
        }
        canvas.drawRoundRect(bounds, radius, radius, fill)
        if (style == ToolbarButtonStyle.OUTLINED) {
            stroke.color = colors.outline
            bounds.inset(0.5f * density, 0.5f * density)
            canvas.drawRoundRect(bounds, radius, radius, stroke)
        }

        val color = contentColor()
        var x = (w - contentWidth()) / 2f
        val cy = h / 2f
        if (showDot) {
            fill.color = colors.accent
            canvas.drawCircle(x + dotSize / 2f, cy, dotSize / 2f, fill)
            x += dotSize + gap
        }
        leadingIcon?.let {
            drawIcon(canvas, it, x, cy, color)
            x += iconSize + gap
        }
        label?.let { text ->
            textPaint.color = color
            val metrics = textPaint.fontMetrics
            canvas.drawText(text, x, cy - (metrics.ascent + metrics.descent) / 2f, textPaint)
            x += textPaint.measureText(text) + 2 * density
        }
        trailingIcon?.let { drawIcon(canvas, it, x, cy, color) }
    }

    private fun drawIcon(canvas: Canvas, icon: Drawable, x: Float, cy: Float, color: Int) {
        icon.setTint(color)
        icon.setBounds(x.toInt(), (cy - iconSize / 2f).toInt(), (x + iconSize).toInt(), (cy + iconSize / 2f).toInt())
        icon.draw(canvas)
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = Button::class.java.name
    }
}
