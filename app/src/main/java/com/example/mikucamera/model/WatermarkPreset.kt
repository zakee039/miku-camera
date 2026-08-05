package com.example.mikucamera.model

import org.json.JSONObject
import java.util.UUID

data class WatermarkPreset(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var imageUri: String? = null,
    var imageX: Float = 0.5f,
    var imageY: Float = 0.5f,
    var imageWidthFraction: Float = 0.5f,
    var imageRotation: Float = 0f,
    var outlinePx: Float = 0f,
    var showTime: Boolean = false,
    var timeX: Float = 0.985f,
    var timeY: Float = 0.94f,
    var timeScale: Float = 0.75f,
    var timeRotation: Float = 0f,
    var showLocation: Boolean = false,
    /** When true, append street / house number after 市+区. Default off (市+区 only). */
    var includeStreet: Boolean = false,
    var locationX: Float = 0.985f,
    var locationY: Float = 0.97f,
    var locationScale: Float = 0.75f,
    var locationRotation: Float = 0f,
    var portraitLayout: WatermarkLayout? = null,
    var landscapeLayout: WatermarkLayout? = null,
    var layoutVersion: Int = CURRENT_LAYOUT_VERSION
) {
    fun copyForEditing() = copy(
        portraitLayout = portraitLayout?.copy(),
        landscapeLayout = landscapeLayout?.copy()
    )

    fun activeLayout(): WatermarkLayout = WatermarkLayout(
        imageX = imageX,
        imageY = imageY,
        imageWidthFraction = imageWidthFraction,
        imageRotation = imageRotation,
        timeX = timeX,
        timeY = timeY,
        timeScale = timeScale,
        timeRotation = timeRotation,
        locationX = locationX,
        locationY = locationY,
        locationScale = locationScale,
        locationRotation = locationRotation
    )

    fun applyLayout(layout: WatermarkLayout) {
        imageX = layout.imageX
        imageY = layout.imageY
        imageWidthFraction = layout.imageWidthFraction
        imageRotation = layout.imageRotation
        timeX = layout.timeX
        timeY = layout.timeY
        timeScale = layout.timeScale
        timeRotation = layout.timeRotation
        locationX = layout.locationX
        locationY = layout.locationY
        locationScale = layout.locationScale
        locationRotation = layout.locationRotation
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("imageUri", imageUri ?: JSONObject.NULL)
        put("imageX", imageX.toDouble())
        put("imageY", imageY.toDouble())
        put("imageWidthFraction", imageWidthFraction.toDouble())
        put("imageRotation", imageRotation.toDouble())
        put("outlinePx", outlinePx.toDouble())
        put("showTime", showTime)
        put("timeX", timeX.toDouble())
        put("timeY", timeY.toDouble())
        put("timeScale", timeScale.toDouble())
        put("timeRotation", timeRotation.toDouble())
        put("showLocation", showLocation)
        put("includeStreet", includeStreet)
        put("locationX", locationX.toDouble())
        put("locationY", locationY.toDouble())
        put("locationScale", locationScale.toDouble())
        put("locationRotation", locationRotation.toDouble())
        put("portraitLayout", portraitLayout?.toJson() ?: JSONObject.NULL)
        put("landscapeLayout", landscapeLayout?.toJson() ?: JSONObject.NULL)
        put("layoutVersion", layoutVersion)
    }

    companion object {
        const val CURRENT_LAYOUT_VERSION = 10
        const val BUILTIN_MIKU_ID = "builtin_miku"

        /** Creates one complete default layout for each device orientation. */
        fun newDraft(): WatermarkPreset {
            val preset = WatermarkPreset()
            val portrait = preset.activeLayout()
            val landscape = portrait.rotateToLandscape(true).copy(
                timeY = 0.93f,
                locationY = 0.98f
            )
            preset.portraitLayout = portrait
            preset.landscapeLayout = landscape
            return preset
        }

        fun builtinMiku(imageUri: String): WatermarkPreset {
            val preset = newDraft().copy(
                id = BUILTIN_MIKU_ID,
                name = "miku",
                imageUri = imageUri,
                showTime = true,
                showLocation = true,
                includeStreet = false
            )
            val portrait = preset.activeLayout()
            preset.portraitLayout = portrait
            preset.landscapeLayout = portrait.rotateToLandscape(true).copy(
                timeY = 0.93f,
                locationY = 0.98f
            )
            return preset
        }

        fun fromJson(json: JSONObject): WatermarkPreset = WatermarkPreset(
            id = json.optString("id", UUID.randomUUID().toString()),
            name = json.optString("name", "未命名水印"),
            imageUri = json.optString("imageUri").takeIf { it.isNotBlank() && it != "null" },
            imageX = json.optDouble("imageX", 0.5).toFloat(),
            imageY = json.optDouble("imageY", 0.5).toFloat(),
            imageWidthFraction = json.optDouble("imageWidthFraction", 0.5).toFloat(),
            imageRotation = json.optDouble("imageRotation", 0.0).toFloat(),
            outlinePx = json.optDouble("outlinePx", 0.0).toFloat(),
            showTime = json.optBoolean("showTime", false),
            timeX = json.optDouble("timeX", 0.985).toFloat(),
            timeY = json.optDouble("timeY", 0.93).toFloat(),
            timeScale = json.optDouble("timeScale", 1.0).toFloat(),
            timeRotation = json.optDouble("timeRotation", 0.0).toFloat(),
            showLocation = json.optBoolean("showLocation", false),
            includeStreet = json.optBoolean("includeStreet", false),
            locationX = json.optDouble("locationX", 0.985).toFloat(),
            locationY = json.optDouble("locationY", 0.985).toFloat(),
            locationScale = json.optDouble("locationScale", 0.8).toFloat(),
            locationRotation = json.optDouble("locationRotation", 0.0).toFloat(),
            // Version 1 used a screen-coordinate rotation that moved a
            // bottom-left watermark to the top-left in landscape. Discard
            // those generated orientation layouts and regenerate them using
            // the corrected edge-preserving mapping.
            portraitLayout = if (json.optInt("layoutVersion", 0) >= 2) {
                json.optJSONObject("portraitLayout")?.let(WatermarkLayout::fromJson)
            } else null,
            landscapeLayout = if (json.optInt("layoutVersion", 0) >= 2) {
                json.optJSONObject("landscapeLayout")?.let(WatermarkLayout::fromJson)
            } else null,
            layoutVersion = json.optInt("layoutVersion", 2)
        )
    }
}

data class WatermarkLayout(
    val imageX: Float,
    val imageY: Float,
    val imageWidthFraction: Float,
    val imageRotation: Float,
    val timeX: Float,
    val timeY: Float,
    val timeScale: Float,
    val timeRotation: Float,
    val locationX: Float,
    val locationY: Float,
    val locationScale: Float,
    val locationRotation: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("imageX", imageX.toDouble())
        put("imageY", imageY.toDouble())
        put("imageWidthFraction", imageWidthFraction.toDouble())
        put("imageRotation", imageRotation.toDouble())
        put("timeX", timeX.toDouble())
        put("timeY", timeY.toDouble())
        put("timeScale", timeScale.toDouble())
        put("timeRotation", timeRotation.toDouble())
        put("locationX", locationX.toDouble())
        put("locationY", locationY.toDouble())
        put("locationScale", locationScale.toDouble())
        put("locationRotation", locationRotation.toDouble())
    }

    fun rotateToLandscape(clockwise: Boolean): WatermarkLayout {
        val widthScale = 9f / 16f
        return copy(
            // Keep the same relative edge/corner. A watermark at the
            // portrait bottom-left remains at the landscape bottom-left.
            imageX = imageX,
            imageY = imageY,
            imageWidthFraction = imageWidthFraction * widthScale,
            timeX = timeX,
            timeY = timeY,
            timeScale = timeScale,
            locationX = locationX,
            locationY = locationY,
            locationScale = locationScale
        )
    }

    fun rotateToPortrait(clockwise: Boolean): WatermarkLayout {
        val widthScale = 16f / 9f
        return copy(
            imageX = imageX,
            imageY = imageY,
            imageWidthFraction = imageWidthFraction * widthScale,
            timeX = timeX,
            timeY = timeY,
            timeScale = timeScale,
            locationX = locationX,
            locationY = locationY,
            locationScale = locationScale
        )
    }

    companion object {
        fun fromJson(json: JSONObject): WatermarkLayout = WatermarkLayout(
            imageX = json.optDouble("imageX", 0.5).toFloat(),
            imageY = json.optDouble("imageY", 0.5).toFloat(),
            imageWidthFraction = json.optDouble("imageWidthFraction", 0.5).toFloat(),
            imageRotation = json.optDouble("imageRotation", 0.0).toFloat(),
            timeX = json.optDouble("timeX", 0.5).toFloat(),
            timeY = json.optDouble("timeY", 0.76).toFloat(),
            timeScale = json.optDouble("timeScale", 1.0).toFloat(),
            timeRotation = json.optDouble("timeRotation", 0.0).toFloat(),
            locationX = json.optDouble("locationX", 0.5).toFloat(),
            locationY = json.optDouble("locationY", 0.83).toFloat(),
            locationScale = json.optDouble("locationScale", 0.8).toFloat(),
            locationRotation = json.optDouble("locationRotation", 0.0).toFloat()
        )
    }
}

data class WatermarkRenderSpec(
    val preset: WatermarkPreset,
    val bitmap: android.graphics.Bitmap?,
    val timeText: String,
    val locationText: String,
    /** Logical canvas size used for watermark layout (swaps in landscape). */
    val previewWidth: Int,
    val previewHeight: Int = previewWidth,
    /** Physical PreviewView size (always the on-screen 9:16 frame). */
    val viewfinderWidth: Int = previewWidth,
    val viewfinderHeight: Int = previewHeight,
    /** Physical device orientation, rounded to the nearest right angle. */
    val orientationDegrees: Int = 0,
    /** Front camera sensors use the opposite landscape rotation baseline. */
    val isFrontFacing: Boolean = false
)
