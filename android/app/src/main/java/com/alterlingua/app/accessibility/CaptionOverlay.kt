package com.alterlingua.app.accessibility

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.WindowManager

/**
 * Draws each translation directly under its message, in one transparent, touch-through window laid over the chat app.
 *
 * It is an accessibility overlay window ([WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY]), which only an
 * accessibility service can add and which needs no "Display over other apps" permission. It cannot be tapped or focused
 * (touches go straight through to the chat), it draws nothing but the captions, and it is removed whenever there is
 * nothing to show or the chat app is no longer the one in front.
 *
 * Must be used from the main thread, with the accessibility service itself as [context] (that is what entitles it to add
 * this kind of window).
 */
class CaptionOverlay(private val context: Context) {
    private val windowManager: WindowManager? = context.getSystemService(WindowManager::class.java)
    private var view: CaptionView? = null

    /** Sizes captions are measured in, for [CaptionPlacer]. */
    val metrics: CaptionMetrics = CaptionMetrics(context)

    val isShowing: Boolean get() = view != null

    fun show(captions: List<Caption>) {
        if (captions.isEmpty()) {
            hide()
            return
        }
        val existing = view
        if (existing != null) {
            existing.captions = captions
            existing.invalidate()
            return
        }
        val wm = windowManager ?: return
        val created = CaptionView(context, metrics).also { it.captions = captions }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            wm.addView(created, params)
            view = created
        } catch (_: RuntimeException) {
            // The window could not be added (for example the service is being torn down): show nothing rather than crash.
            view = null
        }
    }

    fun hide() {
        val current = view ?: return
        view = null
        try {
            windowManager?.removeView(current)
        } catch (_: RuntimeException) {
            // already gone
        }
    }
}

/** Text sizes and box geometry of a caption, in pixels for this display. */
class CaptionMetrics(context: Context) {
    private val displayMetrics = context.resources.displayMetrics
    private val density = displayMetrics.density
    val paddingH: Int = (6 * density).toInt()

    /** Tight vertical padding: in a run of messages from one sender there are only ~12 px between them. */
    val paddingV: Int = (0.5f * density).toInt().coerceAtLeast(1)
    val marginPx: Int = (8 * density).toInt()
    val cornerPx: Float = 6 * density

    /** The text sizes a caption may use, largest first; the largest that fits the room under its message is chosen. */
    val textSizesPx: List<Float> = listOf(10f, 8.5f, 7.5f, 7f).map { TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, it, displayMetrics) }

    private val measurePaints = HashMap<Float, TextPaint>()

    private fun paintFor(sizePx: Float): TextPaint =
        measurePaints.getOrPut(sizePx) { TextPaint(Paint.ANTI_ALIAS_FLAG).also { it.textSize = sizePx } }

    /** Height of one line at [sizePx], including the box's own top and bottom padding. */
    private fun lineHeightFor(sizePx: Float): Int = paintFor(sizePx).fontMetricsInt.let { (it.descent - it.ascent) + 2 * paddingV }

    /** How many lines [text] takes in a caption box [widthPx] wide at [sizePx], with the paint the caption is drawn with. */
    private fun measureLines(text: String, widthPx: Int, sizePx: Float): Int {
        val textWidth = (widthPx - 2 * paddingH).coerceAtLeast(1)
        return StaticLayout.Builder.obtain(text, 0, text.length, paintFor(sizePx), textWidth).setIncludePad(false).build().lineCount.coerceAtLeast(1)
    }

    /** The numbers [CaptionPlacer] needs. */
    val sizes: CaptionSizes = CaptionSizes(
        textSizesPx = textSizesPx,
        lineHeightPx = ::lineHeightFor,
        marginPx = marginPx,
        minWidthPx = (200 * density).toInt(),
        overlapAllowancePx = (4 * density).toInt(),
        measureLines = ::measureLines,
    )
}

private class CaptionView(context: Context, private val metrics: CaptionMetrics) : View(context) {
    var captions: List<Caption> = emptyList()

    private val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (night) 0xF01E2A36.toInt() else 0xF0EAF4FF.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = context.resources.displayMetrics.density * 0.75f
        color = if (night) 0x804FA3E8.toInt() else 0x802F80C8.toInt()
    }
    private val textColor = if (night) 0xFFE8F1FA.toInt() else 0xFF0B2A4A.toInt()
    private val paints = HashMap<Float, TextPaint>()
    private val origin = IntArray(2)

    private fun paintFor(sizePx: Float): TextPaint = paints.getOrPut(sizePx) {
        TextPaint(Paint.ANTI_ALIAS_FLAG).also {
            it.textSize = sizePx
            it.color = textColor
        }
    }

    override fun onDraw(canvas: Canvas) {
        // The window starts at the screen's top-left, but the status bar or a cut-out can still offset it a little,
        // so every caption is moved by wherever this view really is on the screen.
        getLocationOnScreen(origin)
        val padV = metrics.paddingV
        for (caption in captions) {
            val textWidth = (caption.width - 2 * metrics.paddingH).coerceAtLeast(1)
            val layout = StaticLayout.Builder.obtain(caption.text, 0, caption.text.length, paintFor(caption.textSizePx), textWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setMaxLines(caption.maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            val left = (caption.left - origin[0]).toFloat()
            val top = (caption.top - origin[1]).toFloat()
            val box = RectF(left, top, left + caption.width, top + layout.height + 2 * padV)
            canvas.drawRoundRect(box, metrics.cornerPx, metrics.cornerPx, background)
            canvas.drawRoundRect(box, metrics.cornerPx, metrics.cornerPx, border)
            canvas.save()
            canvas.translate(left + metrics.paddingH, top + padV)
            layout.draw(canvas)
            canvas.restore()
        }
    }
}
