package com.example.mikucamera.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.RectF
import com.example.mikucamera.model.WatermarkPreset

object WatermarkRenderer {
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val smoothOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        color = Color.WHITE
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
            drawText(canvas, width, height, timeText, preset.timeX, preset.timeY, preset.timeScale, preset.timeRotation)
        }
        if (preset.showLocation && locationText.isNotBlank()) {
            drawText(
                canvas,
                width,
                height,
                locationText,
                preset.locationX,
                preset.locationY,
                preset.locationScale,
                preset.locationRotation
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

    fun textHeight(width: Int, scale: Float): Float = width * 0.052f * scale

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
            // Extract the PNG alpha mask and blur only its outside edge. This
            // produces an anti-aliased outline instead of the jagged 24-copy
            // dilation previously used here.
            val alphaMask = bitmap.extractAlpha()
            smoothOutlinePaint.maskFilter = BlurMaskFilter(
                outline.coerceAtMost(48f),
                BlurMaskFilter.Blur.OUTER
            )
            canvas.drawBitmap(alphaMask, null, rect, smoothOutlinePaint)
            smoothOutlinePaint.maskFilter = null
            alphaMask.recycle()
        }
        canvas.drawBitmap(bitmap, null, rect, bitmapPaint)
        canvas.restore()
    }

    private fun drawText(
        canvas: Canvas,
        width: Int,
        height: Int,
        text: String,
        centerX: Float,
        centerY: Float,
        scale: Float,
        rotation: Float
    ) {
        canvas.save()
        canvas.translate(centerX * width, centerY * height)
        canvas.rotate(rotation)
        val stroke = textPaint(width, scale, Paint.Style.STROKE).apply {
            color = Color.argb(210, 0, 0, 0)
            strokeWidth = (width * 0.004f).coerceAtLeast(2f)
        }
        val fill = textPaint(width, scale, Paint.Style.FILL).apply { color = Color.WHITE }
        val x = -fill.measureText(text) / 2f
        val y = -(fill.ascent() + fill.descent()) / 2f
        canvas.drawText(text, x, y, stroke)
        canvas.drawText(text, x, y, fill)
        canvas.restore()
    }

    private fun textPaint(width: Int, scale: Float, style: Paint.Style) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = width * 0.052f * scale
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        textAlign = Paint.Align.LEFT
        this.style = style
    }
}
