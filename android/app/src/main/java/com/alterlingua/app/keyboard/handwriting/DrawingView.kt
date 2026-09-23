package com.alterlingua.app.keyboard.handwriting

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/** A pad to write on with a finger. Reports each finished stroke; it keeps the drawn lines only for showing them. */
@SuppressLint("ViewConstructor") // only ever created in code by the keyboard view
class DrawingView(
    context: Context,
    private val lineColor: Int,
    private val onStrokeStarted: () -> Unit,
    private val onStrokeFinished: (InkStroke) -> Unit,
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 4.5f * resources.displayMetrics.density
    }
    private val drawn = mutableListOf<Path>()
    private var current: Path? = null
    private var points = mutableListOf<InkPoint>()

    /** Wipes the drawing. */
    fun clear() {
        drawn.clear()
        current = null
        points = mutableListOf()
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                onStrokeStarted()
                points = mutableListOf(InkPoint(event.x, event.y, event.eventTime))
                current = Path().apply { moveTo(event.x, event.y) }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.historySize) {
                    add(event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i))
                }
                add(event.x, event.y, event.eventTime)
            }
            MotionEvent.ACTION_UP -> {
                add(event.x, event.y, event.eventTime)
                current?.let { drawn += it }
                current = null
                val stroke = points.toList()
                points = mutableListOf()
                onStrokeFinished(stroke)
            }
            MotionEvent.ACTION_CANCEL -> {
                current = null
                points = mutableListOf()
            }
        }
        invalidate()
        return true
    }

    private fun add(x: Float, y: Float, time: Long) {
        points += InkPoint(x, y, time)
        current?.lineTo(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        drawn.forEach { canvas.drawPath(it, paint) }
        current?.let { canvas.drawPath(it, paint) }
    }
}
