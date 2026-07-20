package com.example.mikucamera.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.mikucamera.model.WatermarkLayout
import com.example.mikucamera.model.WatermarkPreset
import com.example.mikucamera.model.WatermarkRenderSpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.hypot

class WatermarkOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private enum class Element { IMAGE, TIME, LOCATION }

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 1.5f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 8f), 0f)
    }
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private var preset = WatermarkPreset()
    private var bitmap: Bitmap? = null
    private var locationText = ""
    private var editingEnabled = false
    private var landscapeMode = false
    private var physicalRotationDegrees = 0
    private var captureOrientationDegrees = 0
    var onChanged: (() -> Unit)? = null
    private var selected: Element? = null
    private var lastX = 0f
    private var lastY = 0f
    private var pinchStartDistance = 0f
    private var pinchStartAngle = 0f
    private var pinchStartScale = 1f
    private var pinchStartRotation = 0f

    private val clockTicker = object : Runnable {
        override fun run() {
            if (preset.showTime) invalidate()
            postDelayed(this, 1_000L)
        }
    }

    init { post(clockTicker) }

    fun setPreset(value: WatermarkPreset, loadedBitmap: Bitmap?): Boolean {
        preset = value.copyForEditing()
        var generatedLayout = false
        if (preset.portraitLayout == null && preset.landscapeLayout == null) {
            preset.portraitLayout = preset.activeLayout()
        }
        if (landscapeMode) {
            if (preset.landscapeLayout == null) {
                preset.landscapeLayout = preset.portraitLayout?.rotateToLandscape(true)
                generatedLayout = true
            }
            preset.landscapeLayout?.let(preset::applyLayout)
        } else {
            if (preset.portraitLayout == null) {
                preset.portraitLayout = preset.landscapeLayout?.rotateToPortrait(true)
                generatedLayout = true
            }
            preset.portraitLayout?.let(preset::applyLayout)
        }
        bitmap = loadedBitmap
        selected = null
        invalidate()
        return generatedLayout
    }

    fun currentPreset(): WatermarkPreset {
        saveActiveLayout()
        return preset.copyForEditing()
    }

    fun setDisplayOrientation(landscape: Boolean): Boolean {
        return setPhysicalRotation(if (landscape) 90 else 0)
    }

    /**
     * Rotates only the watermark canvas. The Activity and the viewfinder keep
     * their portrait bounds even when the user holds the phone sideways.
     */
    fun setPhysicalRotation(degrees: Int, captureDegrees: Int = degrees): Boolean {
        val normalized = ((degrees % 360) + 360) % 360
        val normalizedCapture = ((captureDegrees % 360) + 360) % 360
        if (physicalRotationDegrees == normalized && captureOrientationDegrees == normalizedCapture) return false
        saveActiveLayout()
        val generatedLayout: WatermarkLayout?
        physicalRotationDegrees = normalized
        captureOrientationDegrees = normalizedCapture
        landscapeMode = normalized == 90 || normalized == 270
        if (landscapeMode) {
            generatedLayout = preset.landscapeLayout ?: preset.portraitLayout?.rotateToLandscape(true)
            preset.landscapeLayout = generatedLayout
            generatedLayout?.let(preset::applyLayout)
        } else {
            generatedLayout = preset.portraitLayout ?: preset.landscapeLayout?.rotateToPortrait(true)
            preset.portraitLayout = generatedLayout
            generatedLayout?.let(preset::applyLayout)
        }
        invalidate()
        return generatedLayout != null
    }

    fun setUploadedImage(uri: String, loadedBitmap: Bitmap) {
        preset.imageUri = uri
        preset.imageX = 0.5f
        preset.imageY = 0.5f
        preset.imageWidthFraction = 0.5f
        preset.imageRotation = 0f
        val defaultLayout = preset.activeLayout()
        if (landscapeMode) {
            preset.landscapeLayout = defaultLayout
            preset.portraitLayout = defaultLayout.rotateToPortrait(true)
        } else {
            preset.portraitLayout = defaultLayout
            preset.landscapeLayout = defaultLayout.rotateToLandscape(true)
        }
        bitmap = loadedBitmap
        selected = Element.IMAGE
        invalidate()
        onChanged?.invoke()
    }

    fun setOutlinePx(value: Float) {
        preset.outlinePx = value
        invalidate()
        onChanged?.invoke()
    }

    fun setShowTime(show: Boolean) {
        preset.showTime = show
        if (show) selected = Element.TIME
        invalidate()
        onChanged?.invoke()
    }

    fun setShowLocation(show: Boolean) {
        preset.showLocation = show
        if (show) selected = Element.LOCATION
        invalidate()
        onChanged?.invoke()
    }

    fun setLocationText(value: String) {
        locationText = value
        invalidate()
        onChanged?.invoke()
    }

    fun setEditingEnabled(enabled: Boolean) {
        editingEnabled = enabled
        if (!enabled) selected = null
        invalidate()
    }

    fun renderSpec(): WatermarkRenderSpec = WatermarkRenderSpec(
        preset = preset.copy(),
        bitmap = bitmap,
        timeText = timeFormat.format(Date()),
        locationText = locationText,
        previewWidth = logicalWidth(),
        previewHeight = logicalHeight(),
        viewfinderWidth = width.coerceAtLeast(1),
        viewfinderHeight = height.coerceAtLeast(1),
        orientationDegrees = captureOrientationDegrees
    )

    private fun saveActiveLayout() {
        if (landscapeMode) {
            preset.landscapeLayout = preset.activeLayout()
        } else {
            preset.portraitLayout = preset.activeLayout()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val time = timeFormat.format(Date())
        canvas.save()
        val logicalWidth = logicalWidth()
        val logicalHeight = logicalHeight()
        canvas.translate(width / 2f, height / 2f)
        canvas.rotate(physicalRotationDegrees.toFloat())
        canvas.translate(-logicalWidth / 2f, -logicalHeight / 2f)
        WatermarkRenderer.draw(
            canvas,
            logicalWidth,
            logicalHeight,
            preset,
            bitmap,
            time,
            locationText
        )
        // Selection ring in full editor, or while the user is dragging on camera.
        if (editingEnabled || selected != null) {
            drawSelection(canvas, time, logicalWidth, logicalHeight)
        }
        canvas.restore()
    }

    /**
     * Watermark elements can be dragged/pinched in both camera and editor modes.
     * Misses return false so the preview under this view can handle focus/exposure.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val point = toLogicalPoint(event.x, event.y)
                selected = hitTest(point.first, point.second)
                if (selected == null) {
                    invalidate()
                    return false
                }
                parent.requestDisallowInterceptTouchEvent(true)
                lastX = point.first
                lastY = point.second
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> if (event.pointerCount >= 2 && selected != null) {
                pinchStartDistance = pointerDistance(event).coerceAtLeast(1f)
                pinchStartAngle = pointerAngle(event)
                pinchStartScale = selectedScale()
                pinchStartRotation = selectedRotation()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (selected == null) return false
                if (event.pointerCount >= 2) {
                    val ratio = pointerDistance(event) / pinchStartDistance.coerceAtLeast(1f)
                    setSelectedScale((pinchStartScale * ratio).coerceIn(0.15f, 3f))
                    setSelectedRotation(pinchStartRotation + normalizeAngle(pointerAngle(event) - pinchStartAngle))
                    val first = toLogicalPoint(event.getX(0), event.getY(0))
                    val second = toLogicalPoint(event.getX(1), event.getY(1))
                    val logicalWidth = logicalWidth().toFloat()
                    val logicalHeight = logicalHeight().toFloat()
                    setSelectedCenter(
                        (first.first + second.first) / 2f / logicalWidth,
                        (first.second + second.second) / 2f / logicalHeight
                    )
                } else {
                    val point = toLogicalPoint(event.x, event.y)
                    moveSelected(
                        (point.first - lastX) / logicalWidth(),
                        (point.second - lastY) / logicalHeight()
                    )
                    lastX = point.first
                    lastY = point.second
                }
                invalidate()
                onChanged?.invoke()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount == 2) {
                    val remaining = if (event.actionIndex == 0) 1 else 0
                    val point = toLogicalPoint(event.getX(remaining), event.getY(remaining))
                    lastX = point.first
                    lastY = point.second
                }
                return selected != null
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                val handled = selected != null
                // Camera mode: drop selection chrome after the gesture so the
                // viewfinder stays clean; editor keeps the selection.
                if (!editingEnabled) {
                    selected = null
                    invalidate()
                }
                return handled
            }
        }
        return selected != null
    }

    private fun hitTest(x: Float, y: Float): Element? {
        val candidates = listOf(Element.LOCATION, Element.TIME, Element.IMAGE)
        return candidates.firstOrNull { isInside(it, x, y) }
    }

    private fun isInside(element: Element, x: Float, y: Float): Boolean {
        val (cx, cy) = centerOf(element)
        val logicalWidth = logicalWidth()
        val logicalHeight = logicalHeight()
        val dx = x - cx * logicalWidth
        val dy = y - cy * logicalHeight
        val radians = Math.toRadians((-rotationOf(element)).toDouble())
        val localX = dx * kotlin.math.cos(radians) - dy * kotlin.math.sin(radians)
        val localY = dx * kotlin.math.sin(radians) + dy * kotlin.math.cos(radians)
        val bounds = localBounds(element, logicalWidth)
        return bounds.contains(localX.toFloat(), localY.toFloat())
    }

    private fun localBounds(element: Element, logicalWidth: Int): RectF = when (element) {
        Element.IMAGE -> bitmap?.let { WatermarkRenderer.imageRect(logicalWidth, preset, it) } ?: RectF()
        Element.TIME -> textBounds(timeFormat.format(Date()), preset.timeScale, logicalWidth)
        Element.LOCATION -> textBounds(locationText, preset.locationScale, logicalWidth)
    }.apply { inset(-20f, -20f) }

    private fun textBounds(text: String, scale: Float, logicalWidth: Int = logicalWidth()): RectF {
        val textWidth = WatermarkRenderer.textWidth(logicalWidth, text, scale)
        val textHeight = WatermarkRenderer.textHeight(logicalWidth, scale)
        return RectF(-textWidth / 2f, -textHeight / 2f, textWidth / 2f, textHeight / 2f)
    }

    private fun drawSelection(canvas: Canvas, time: String, logicalWidth: Int, logicalHeight: Int) {
        val element = selected ?: return
        if (element == Element.IMAGE && bitmap == null) return
        if (element == Element.TIME && !preset.showTime) return
        if (element == Element.LOCATION && (!preset.showLocation || locationText.isBlank())) return
        val bounds = when (element) {
            Element.IMAGE -> WatermarkRenderer.imageRect(logicalWidth, preset, bitmap!!)
            Element.TIME -> textBounds(time, preset.timeScale, logicalWidth)
            Element.LOCATION -> textBounds(locationText, preset.locationScale, logicalWidth)
        }
        val (cx, cy) = centerOf(element)
        canvas.save()
        canvas.translate(cx * logicalWidth, cy * logicalHeight)
        canvas.rotate(rotationOf(element))
        canvas.drawRect(bounds, selectionPaint)
        canvas.restore()
    }

    private fun centerOf(element: Element): Pair<Float, Float> = when (element) {
        Element.IMAGE -> preset.imageX to preset.imageY
        Element.TIME -> preset.timeX to preset.timeY
        Element.LOCATION -> preset.locationX to preset.locationY
    }

    private fun rotationOf(element: Element): Float = when (element) {
        Element.IMAGE -> preset.imageRotation
        Element.TIME -> preset.timeRotation
        Element.LOCATION -> preset.locationRotation
    }

    private fun selectedScale(): Float = when (selected) {
        Element.IMAGE -> preset.imageWidthFraction
        Element.TIME -> preset.timeScale
        Element.LOCATION -> preset.locationScale
        null -> 1f
    }

    private fun selectedRotation(): Float = selected?.let(::rotationOf) ?: 0f

    private fun setSelectedScale(value: Float) {
        when (selected) {
            Element.IMAGE -> preset.imageWidthFraction = value.coerceIn(0.05f, 2.5f)
            Element.TIME -> preset.timeScale = value
            Element.LOCATION -> preset.locationScale = value
            null -> Unit
        }
    }

    private fun setSelectedRotation(value: Float) {
        when (selected) {
            Element.IMAGE -> preset.imageRotation = value
            Element.TIME -> preset.timeRotation = value
            Element.LOCATION -> preset.locationRotation = value
            null -> Unit
        }
    }

    private fun setSelectedCenter(x: Float, y: Float) {
        val safeX = x.coerceIn(0f, 1f)
        val safeY = y.coerceIn(0f, 1f)
        when (selected) {
            Element.IMAGE -> { preset.imageX = safeX; preset.imageY = safeY }
            Element.TIME -> { preset.timeX = safeX; preset.timeY = safeY }
            Element.LOCATION -> { preset.locationX = safeX; preset.locationY = safeY }
            null -> Unit
        }
    }

    private fun moveSelected(dx: Float, dy: Float) {
        val (x, y) = selected?.let(::centerOf) ?: return
        setSelectedCenter(x + dx, y + dy)
    }

    private fun logicalWidth(): Int = if (landscapeMode) height else width

    private fun logicalHeight(): Int = if (landscapeMode) width else height

    /** Converts a touch point in the fixed portrait view into the rotated canvas. */
    private fun toLogicalPoint(x: Float, y: Float): Pair<Float, Float> {
        val logicalWidth = logicalWidth().toFloat()
        val logicalHeight = logicalHeight().toFloat()
        val screenCenterX = width / 2f
        val screenCenterY = height / 2f
        val logicalCenterX = logicalWidth / 2f
        val logicalCenterY = logicalHeight / 2f
        return when (physicalRotationDegrees) {
            90 -> logicalCenterX + (y - screenCenterY) to logicalCenterY - (x - screenCenterX)
            180 -> width - x to height - y
            270 -> logicalCenterX - (y - screenCenterY) to logicalCenterY + (x - screenCenterX)
            else -> x to y
        }
    }

    private fun pointerDistance(event: MotionEvent): Float = hypot(
        event.getX(1) - event.getX(0),
        event.getY(1) - event.getY(0)
    )

    private fun pointerAngle(event: MotionEvent): Float = Math.toDegrees(
        atan2(
            event.getY(1) - event.getY(0),
            event.getX(1) - event.getX(0)
        ).toDouble()
    ).toFloat()

    private fun normalizeAngle(value: Float): Float {
        var angle = value
        while (angle > 180f) angle -= 360f
        while (angle < -180f) angle += 360f
        return angle
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(clockTicker)
        super.onDetachedFromWindow()
    }
}
