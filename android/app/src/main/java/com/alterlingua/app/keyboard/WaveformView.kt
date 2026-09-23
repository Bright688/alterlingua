package com.alterlingua.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** The live sound level while recording: a row of bars, newest on the right. */
@SuppressLint("ViewConstructor") // only ever created in code by the voice panel
class WaveformView(context: Context, private val barColor: Int, private val baseColor: Int) : View(context) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = RectF()
    private var levels: List<Float> = emptyList()

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO // the timer says everything a screen reader needs
    }

    fun setLevels(new: List<Float>) {
        levels = new
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bars = VoiceFlow.WAVEFORM_BARS
        val slot = width / bars.toFloat()
        val barWidth = (slot * 0.55f).coerceAtLeast(2 * density)
        val minHeight = 3 * density
        val centre = height / 2f
        for (index in 0 until bars) {
            val level = levels.getOrNull(index - (bars - levels.size))
            // Quiet sound still shows a little, so the panel never looks frozen.
            val barHeight = if (level != null) minHeight + level.coerceIn(0f, 1f) * (height - minHeight) else minHeight
            paint.color = if (level != null) barColor else baseColor
            val x = index * slot + (slot - barWidth) / 2f
            bounds.set(x, centre - barHeight / 2f, x + barWidth, centre + barHeight / 2f)
            canvas.drawRoundRect(bounds, barWidth / 2f, barWidth / 2f, paint)
        }
    }
}
