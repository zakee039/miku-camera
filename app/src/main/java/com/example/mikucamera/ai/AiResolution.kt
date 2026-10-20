package com.example.mikucamera.ai

import kotlin.math.roundToInt

/** Long edge in pixels; preserve the source aspect ratio, aligned to 16 pixels. */
enum class AiResolution(val label: String, val longEdge: Int) {
    K1("1K", 1024), K2("2K", 2048), K4("4K", 4096);

    fun dimensions(width: Int, height: Int): Pair<Int, Int> {
        require(width > 0 && height > 0) { "无法读取原图尺寸" }
        val ratio = width.toDouble() / height
        require(ratio in (1.0 / 3.0)..3.0) { "请使用宽高比在 1:3 到 3:1 之间的照片" }
        fun aligned(value: Double) = ((value / 16).roundToInt() * 16).coerceAtLeast(16)
        return if (width >= height) longEdge to aligned(longEdge / ratio)
        else aligned(longEdge * ratio) to longEdge
    }

    fun size(width: Int, height: Int, separator: String = "x"): String =
        dimensions(width, height).let { "${it.first}$separator${it.second}" }

    /** OpenAI's 4K tier is capped at 3840 × 2160 pixels, with 16px edges. */
    fun openAiSize(width: Int, height: Int): String {
        val (w, h) = dimensions(width, height)
        val scale = minOf(1.0, 3840.0 / maxOf(w, h),
            kotlin.math.sqrt((3840.0 * 2160.0) / (w.toDouble() * h)))
        fun edge(value: Int) = ((value * scale / 16).toInt() * 16).coerceAtLeast(16)
        return "${edge(w)}x${edge(h)}"
    }

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: K2
    }
}
