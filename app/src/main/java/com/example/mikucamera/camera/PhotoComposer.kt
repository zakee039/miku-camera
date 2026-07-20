package com.example.mikucamera.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.example.mikucamera.model.WatermarkRenderSpec
import com.example.mikucamera.ui.WatermarkRenderer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object PhotoComposer {
    fun composeAndSave(context: Context, source: File, spec: WatermarkRenderSpec): Uri {
        val decoded = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("无法读取相机照片")
        val exifOriented = orient(decoded, source)
        if (exifOriented !== decoded) decoded.recycle()

        // Capture uses the same ViewPort / ROTATION_0 as the preview, so the
        // buffer is already (or nearly) the on-screen 3:4 FOV. Crop to the
        // physical viewfinder aspect before any landscape rotation so the
        // saved frame matches what the user saw.
        val viewfinderAspect = spec.viewfinderWidth.toFloat() /
            spec.viewfinderHeight.coerceAtLeast(1).toFloat()
        val framed = cropToAspect(exifOriented, viewfinderAspect)
        if (framed !== exifOriented) exifOriented.recycle()

        // Rotate into the physical posture (portrait stays, landscape rolls 90/270).
        val oriented = rotateToPhysicalOrientation(framed, spec.orientationDegrees)
        if (oriented !== framed) framed.recycle()

        val output = oriented.copy(Bitmap.Config.ARGB_8888, true)
        if (output !== oriented) oriented.recycle()
        val canvas = Canvas(output)
        WatermarkRenderer.draw(
            canvas = canvas,
            width = output.width,
            height = output.height,
            preset = spec.preset,
            bitmap = spec.bitmap,
            timeText = spec.timeText,
            locationText = spec.locationText,
            previewWidth = spec.previewWidth
        )

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "watermark_$stamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/水印相机")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册文件")
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                check(output.compress(Bitmap.CompressFormat.JPEG, 95, stream)) { "照片保存失败" }
            } ?: error("无法写入相册")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        } finally {
            output.recycle()
        }
    }

    private fun orient(bitmap: Bitmap, file: File): Bitmap {
        val orientation = runCatching {
            ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * After ViewPort capture at ROTATION_0, the buffer matches the portrait
     * viewfinder. OrientationEventListener degrees (CCW from natural) tell us
     * how to roll that buffer so gallery orientation matches how the phone
     * was held.
     */
    private fun rotateToPhysicalOrientation(bitmap: Bitmap, orientationDegrees: Int): Bitmap {
        val normalized = ((orientationDegrees % 360) + 360) % 360
        val degrees = when (normalized) {
            90 -> 90f
            180 -> 180f
            270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Center-crop so width/height equals [targetAspect] (width ÷ height). */
    private fun cropToAspect(bitmap: Bitmap, targetAspect: Float): Bitmap {
        if (targetAspect <= 0f) return bitmap
        val srcAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        if (abs(srcAspect - targetAspect) < 0.005f) return bitmap

        return if (srcAspect > targetAspect) {
            val newWidth = (bitmap.height * targetAspect).roundToInt().coerceAtLeast(1)
                .coerceAtMost(bitmap.width)
            val x = (bitmap.width - newWidth) / 2
            Bitmap.createBitmap(bitmap, x, 0, newWidth, bitmap.height)
        } else {
            val newHeight = (bitmap.width / targetAspect).roundToInt().coerceAtLeast(1)
                .coerceAtMost(bitmap.height)
            val y = (bitmap.height - newHeight) / 2
            Bitmap.createBitmap(bitmap, 0, y, bitmap.width, newHeight)
        }
    }
}
