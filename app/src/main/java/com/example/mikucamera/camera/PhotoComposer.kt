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
    fun composeAndSave(
        context: Context,
        source: File,
        spec: WatermarkRenderSpec,
        capturedAt: Long = System.currentTimeMillis()
    ): Uri {
        val clean = prepareCleanBitmap(source, spec)
        val output = clean.copy(Bitmap.Config.ARGB_8888, true)
        if (output !== clean) clean.recycle()
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

        try {
            return saveBitmapToGallery(context, output, "origin", capturedAt)
        } finally {
            output.recycle()
        }
    }

    /**
     * Produces the clean photo that matches the CameraX preview. No PNG, time,
     * location, or other watermark is applied. AI mode uses only this path.
     */
    fun prepareCleanPhoto(source: File, spec: WatermarkRenderSpec, destination: File): File {
        val clean = prepareCleanBitmap(source, spec)
        try {
            destination.outputStream().buffered().use { stream ->
                check(clean.compress(Bitmap.CompressFormat.JPEG, 95, stream)) { "干净照片写入失败" }
            }
            return destination
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            clean.recycle()
        }
    }

    /** Copies an already encoded image to the gallery without resizing/recompressing it. */
    fun saveFileToGallery(
        context: Context,
        source: File,
        suffix: String,
        mimeType: String = "image/jpeg",
        capturedAt: Long = System.currentTimeMillis()
    ): Uri {
        val extension = when (mimeType) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        return createGalleryEntry(context, suffix, mimeType, extension, capturedAt) { stream ->
            source.inputStream().buffered().use { input -> input.copyTo(stream) }
        }
    }

    private fun saveBitmapToGallery(context: Context, bitmap: Bitmap, suffix: String, capturedAt: Long): Uri {
        return createGalleryEntry(context, suffix, "image/jpeg", "jpg", capturedAt) { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) { "照片保存失败" }
        }
    }

    private fun createGalleryEntry(
        context: Context,
        suffix: String,
        mimeType: String,
        extension: String,
        capturedAt: Long,
        write: (java.io.OutputStream) -> Unit
    ): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(capturedAt))
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "miku_${stamp}_$suffix.$extension")
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/miku camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法创建相册文件")
        try {
            resolver.openOutputStream(uri)?.use(write) ?: error("无法写入相册")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            }
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun prepareCleanBitmap(source: File, spec: WatermarkRenderSpec): Bitmap {
        val decoded = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("无法读取相机照片")
        val exifOriented = orient(decoded, source)
        if (exifOriented !== decoded) decoded.recycle()

        val viewfinderAspect = spec.viewfinderWidth.toFloat() /
            spec.viewfinderHeight.coerceAtLeast(1).toFloat()
        val framed = cropToAspect(exifOriented, viewfinderAspect)
        if (framed !== exifOriented) exifOriented.recycle()

        val oriented = rotateToPhysicalOrientation(
            framed,
            spec.orientationDegrees,
            spec.isFrontFacing
        )
        if (oriented !== framed) framed.recycle()

        val previewMatched = if (spec.isFrontFacing) mirrorHorizontally(oriented) else oriented
        if (previewMatched !== oriented) oriented.recycle()
        return previewMatched
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
    private fun rotateToPhysicalOrientation(
        bitmap: Bitmap,
        orientationDegrees: Int,
        isFrontFacing: Boolean
    ): Bitmap {
        val normalized = ((orientationDegrees % 360) + 360) % 360
        // With the fixed ROTATION_0 capture pipeline, front and back sensors
        // have opposite landscape baselines. Using the back-camera direction
        // for a front-camera frame rotates the saved photo by an extra 180°.
        val lensAdjusted = if (isFrontFacing) {
            when (normalized) {
                90 -> 270
                270 -> 90
                else -> normalized
            }
        } else normalized
        val degrees = when (lensAdjusted) {
            90 -> 90f
            180 -> 180f
            270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun mirrorHorizontally(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { preScale(-1f, 1f) }
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
