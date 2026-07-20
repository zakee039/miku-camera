package com.example.mikucamera.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Combined focus box + exposure control (system-camera style).
 * Sun icon and EV track sit on the right of the focus box; vertical swipe
 * anywhere on that right strip adjusts exposure without precise thumb targeting.
 */
class FocusExposureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val accent = Color.parseColor("#FFCC00")
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.FILL
    }
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val sunFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        style = Paint.Style.FILL
    }

    private val focusSize = 72f * density
    private val cornerLen = 16f * density
    private val sunGap = 14f * density
    private val sunRadius = 7f * density
    private val trackHalf = 42f * density
    private val hitPad = 28f * density

    /** 0..1 where 0.5 is neutral exposure. */
    var exposureFraction: Float = 0.5f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var onExposureChanged: ((fraction: Float) -> Unit)? = null

    private var lastTouchY = 0f
    private var draggingExposure = false
    private val hideRunnable = Runnable { visibility = GONE }

    init {
        visibility = GONE
        isClickable = false
    }

    fun showAt(centerX: Float, centerY: Float) {
        val halfW = (focusSize / 2f + sunGap + sunRadius * 2f + hitPad)
        val halfH = (trackHalf + sunRadius * 2f + hitPad).coerceAtLeast(focusSize / 2f + hitPad)
        val w = (halfW * 2f).roundToInt()
        val h = (halfH * 2f).roundToInt()
        val params = (layoutParams as? android.widget.FrameLayout.LayoutParams)
            ?: android.widget.FrameLayout.LayoutParams(w, h)
        params.width = w
        params.height = h
        params.leftMargin = (centerX - w / 2f).roundToInt()
        params.topMargin = (centerY - h / 2f).roundToInt()
        layoutParams = params
        visibility = VISIBLE
        alpha = 1f
        animate().cancel()
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, 3_000L)
        invalidate()
    }

    fun dismiss() {
        removeCallbacks(hideRunnable)
        visibility = GONE
    }

    fun scheduleAutoHide(delayMs: Long = 3_000L) {
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, delayMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f - (sunGap + sunRadius) / 2f
        val cy = height / 2f
        val half = focusSize / 2f
        drawCornerBox(canvas, cx, cy, half)
        drawExposureChrome(canvas, cx + half + sunGap, cy)
    }

    private fun drawCornerBox(canvas: Canvas, cx: Float, cy: Float, half: Float) {
        val left = cx - half
        val top = cy - half
        val right = cx + half
        val bottom = cy + half
        // Top-left
        canvas.drawLine(left, top, left + cornerLen, top, boxPaint)
        canvas.drawLine(left, top, left, top + cornerLen, boxPaint)
        // Top-right
        canvas.drawLine(right - cornerLen, top, right, top, boxPaint)
        canvas.drawLine(right, top, right, top + cornerLen, boxPaint)
        // Bottom-left
        canvas.drawLine(left, bottom - cornerLen, left, bottom, boxPaint)
        canvas.drawLine(left, bottom, left + cornerLen, bottom, boxPaint)
        // Bottom-right
        canvas.drawLine(right, bottom - cornerLen, right, bottom, boxPaint)
        canvas.drawLine(right - cornerLen, bottom, right, bottom, boxPaint)
    }

    private fun drawExposureChrome(canvas: Canvas, sunCx: Float, cy: Float) {
        val trackTop = cy - trackHalf
        val trackBottom = cy + trackHalf
        canvas.drawLine(sunCx, trackTop, sunCx, trackBottom, trackPaint)

        // Sun moves with exposure: top = brighter, bottom = darker.
        val sunCy = trackBottom - exposureFraction * (trackBottom - trackTop)
        // Rays
        val ray = sunRadius + 3.5f * density
        for (i in 0 until 8) {
            val angle = Math.toRadians(i * 45.0)
            val sx = sunCx + (kotlin.math.cos(angle) * (sunRadius + 1.5f * density)).toFloat()
            val sy = sunCy + (kotlin.math.sin(angle) * (sunRadius + 1.5f * density)).toFloat()
            val ex = sunCx + (kotlin.math.cos(angle) * ray).toFloat()
            val ey = sunCy + (kotlin.math.sin(angle) * ray).toFloat()
            canvas.drawLine(sx, sy, ex, ey, sunPaint)
        }
        canvas.drawCircle(sunCx, sunCy, sunRadius * 0.55f, sunFillPaint)
        canvas.drawCircle(sunCx, sunCy, sunRadius, sunPaint)
    }

    /** Hit-test the generous exposure strip to the right of the focus box. */
    fun isOnExposureStrip(localX: Float, @Suppress("UNUSED_PARAMETER") localY: Float): Boolean {
        val cx = width / 2f - (sunGap + sunRadius) / 2f
        val half = focusSize / 2f
        val stripLeft = cx + half * 0.35f
        return localX >= stripLeft
    }

    fun applyVerticalDelta(dy: Float) {
        // Finger up -> brighter (higher fraction). Sensitivity: ~120dp full range.
        val range = 120f * density
        exposureFraction = (exposureFraction - dy / range).coerceIn(0f, 1f)
        onExposureChanged?.invoke(exposureFraction)
        scheduleAutoHide()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!isOnExposureStrip(event.x, event.y)) return false
                parent.requestDisallowInterceptTouchEvent(true)
                draggingExposure = true
                lastTouchY = event.y
                scheduleAutoHide()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!draggingExposure) return false
                val dy = event.y - lastTouchY
                if (abs(dy) > 0.5f) {
                    applyVerticalDelta(dy)
                    lastTouchY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!draggingExposure) return false
                draggingExposure = false
                parent.requestDisallowInterceptTouchEvent(false)
                scheduleAutoHide()
                return true
            }
        }
        return false
    }

    fun contentBoundsInParent(): RectF {
        val lp = layoutParams as android.widget.FrameLayout.LayoutParams
        return RectF(
            lp.leftMargin.toFloat(),
            lp.topMargin.toFloat(),
            lp.leftMargin + width.toFloat(),
            lp.topMargin + height.toFloat()
        )
    }
}
