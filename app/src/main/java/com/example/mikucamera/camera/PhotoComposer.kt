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

object PhotoComposer {
    fun composeAndSave(context: Context, source: File, spec: WatermarkRenderSpec): Uri {
        val decoded = BitmapFactory.decodeFile(source.absolutePath)
            ?: error("无法读取相机照片")
        val exifOriented = orient(decoded, source)
        if (exifOriented !== decoded) decoded.recycle()

        // CameraX normally writes the correct EXIF rotation. When a device or
        // camera HAL omits that metadata, use the physical posture as a safe
        // fallback so portrait captures stay 9:16 and landscape captures stay
        // 16:9. The two landscape directions are kept distinct (90 vs 270).
        val oriented = ensurePhysicalAspect(exifOriented, spec.orientationDegrees)
        if (oriented !== exifOriented) exifOriented.recycle()

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
        val orientation = runCatching { ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
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

    private fun ensurePhysicalAspect(bitmap: Bitmap, orientationDegrees: Int): Bitmap {
        val landscape = orientationDegrees == 90 || orientationDegrees == 270
        val bitmapLandscape = bitmap.width > bitmap.height
        if (landscape == bitmapLandscape) return bitmap

        val degrees = when (orientationDegrees) {
            90 -> 270f
            else -> 90f
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
