package com.example.mikucamera.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import com.example.mikucamera.model.WatermarkPreset
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

object WatermarkRenderer {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
    }

    fun draw(
        canvas: Canvas,
        width: Int,
        height: Int,
        preset: WatermarkPreset,
        bitmap: Bitmap?,
        timeText: String,
        locationText: String,
        previewWidth: Int = width
    ) {
        if (bitmap != null) drawBitmap(canvas, width, height, preset, bitmap, previewWidth)
        if (preset.showTime) {
            drawText(canvas, width, height, timeText, preset.timeX, preset.timeY, preset.timeScale)
        }
        if (preset.showLocation && locationText.isNotBlank()) {
            drawText(
                canvas,
                width,
                height,
                locationText,
                preset.locationX,
                preset.locationY,
                preset.timeScale
            )
        }
    }

    fun imageRect(width: Int, preset: WatermarkPreset, bitmap: Bitmap): RectF {
        val targetWidth = width * preset.imageWidthFraction
        val targetHeight = targetWidth * bitmap.height / bitmap.width.toFloat()
        return RectF(-targetWidth / 2f, -targetHeight / 2f, targetWidth / 2f, targetHeight / 2f)
    }

    fun textWidth(width: Int, text: String, scale: Float): Float {
        return textPaint(width, scale, Paint.Style.FILL).measureText(text)
    }

    // Two points smaller than the previous default while keeping both lines
    // on the same baseline scale.
    fun textHeight(width: Int, scale: Float): Float = width * 0.044f * scale

    private fun drawBitmap(
        canvas: Canvas,
        width: Int,
        height: Int,
        preset: WatermarkPreset,
        bitmap: Bitmap,
        previewWidth: Int
    ) {
        val rect = imageRect(width, preset, bitmap)
        canvas.save()
        canvas.translate(preset.imageX * width, preset.imageY * height)
        canvas.rotate(preset.imageRotation)

        val outline = preset.outlinePx * width / previewWidth.coerceAtLeast(1).toFloat()
        if (outline > 0.25f) {
            drawPngOutline(canvas, bitmap, rect, outline)
        }
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
        canvas.restore()
    }

    /**
     * Reliable white outline for arbitrary PNG alpha: stamp a solid silhouette
     * around the shape in a ring of offsets. Works on hardware and software
     * canvases (unlike BlurMaskFilter, which often fails on hardware layers).
     */
    private fun drawPngOutline(canvas: Canvas, bitmap: Bitmap, rect: RectF, outlinePx: Float) {
        val radius = outlinePx.coerceIn(0.5f, 64f)
        val steps = max(12, (radius * 2.5f).roundToInt().coerceAtMost(36))
        for (i in 0 until steps) {
            val angle = (Math.PI * 2.0 * i) / steps
            val dx = (cos(angle) * radius).toFloat()
            val dy = (sin(angle) * radius).toFloat()
            canvas.save()
            canvas.translate(dx, dy)
            canvas.drawBitmap(bitmap, null, rect, outlinePaint)
            canvas.restore()
        }
        // Extra cardinal stamps for denser coverage on thin strokes.
        val cardinal = floatArrayOf(0f, radius, 0f, -radius, radius, 0f, -radius, 0f)
        var i = 0
        while (i < cardinal.size) {
            canvas.save()
            canvas.translate(cardinal[i], cardinal[i + 1])
            canvas.drawBitmap(bitmap, null, rect, outlinePaint)
            canvas.restore()
            i += 2
        }
    }

    private fun drawText(
        canvas: Canvas,
        width: Int,
        height: Int,
        text: String,
        centerX: Float,
        centerY: Float,
        scale: Float
    ) {
        canvas.save()
        canvas.translate(centerX * width, centerY * height)
        val stroke = textPaint(width, scale, Paint.Style.STROKE).apply {
            color = Color.argb(210, 0, 0, 0)
            strokeWidth = (width * 0.004f).coerceAtLeast(2f)
        }
        val fill = textPaint(width, scale, Paint.Style.FILL).apply { color = Color.WHITE }
        val x = -fill.measureText(text)
        val y = -(fill.ascent() + fill.descent()) / 2f
        canvas.drawText(text, x, y, stroke)
        canvas.drawText(text, x, y, fill)
        canvas.restore()
    }

    private fun textPaint(width: Int, scale: Float, style: Paint.Style) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = width * 0.044f * scale
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.LEFT
        this.style = style
    }
}
