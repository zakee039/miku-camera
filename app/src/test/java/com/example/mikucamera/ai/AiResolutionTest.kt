package com.example.mikucamera.ai

import org.junit.Assert.*
import org.junit.Test

class AiResolutionTest {
    @Test fun sizesPreservePortraitAndLandscapeAspectRatio() {
        assertEquals("768x1024", AiResolution.K1.size(3000, 4000))
        assertEquals("1536x2048", AiResolution.K2.size(3000, 4000))
        assertEquals("4096x3072", AiResolution.K4.size(4000, 3000))
        assertEquals("1536*2048", AiResolution.K2.size(3000, 4000, "*"))
    }
    @Test fun openAi4kHonorsPixelAndEdgeLimits() {
        assertEquals("3840x2160", AiResolution.K4.openAiSize(3840, 2160))
        for ((w, h) in listOf(3000 to 4000, 4000 to 3000, 1000 to 1000, 1000 to 3000)) {
            val (ow, oh) = AiResolution.K4.openAiSize(w, h).split("x").map(String::toInt)
            assertTrue(ow * oh <= 3840 * 2160)
            assertTrue(maxOf(ow, oh) <= 3840)
            assertEquals(0, ow % 16); assertEquals(0, oh % 16)
            assertEquals(w.toDouble() / h, ow.toDouble() / oh, .015)
        }
    }
    @Test fun legacyTasksGetSafeDefault() {
        assertEquals(AiResolution.K2, AiResolution.fromStored(null))
        assertEquals(AiResolution.K2, AiResolution.fromStored("unexpected"))
        assertEquals(AiResolution.K4, AiResolution.fromStored("K4"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnreadableSource() { AiResolution.K2.dimensions(0, 0) }
    @Test fun batchContainsFourUniqueStylesWithSameServiceAndResolution() {
        val config = AiGenerationConfiguration("profile", AiServicePreset.CUSTOM_OPENAI,
            "https://example.test/v1", "/images/edits", "gpt-image-2", AiVisualStyle.REALISTIC,
            AiOutfitStyle.SCENE_ADAPTIVE, AiResolution.K4)
        val combinations = config.allCombinations()
        assertEquals(4, combinations.size)
        assertEquals(4, combinations.map { it.visualStyle to it.outfitStyle }.toSet().size)
        combinations.forEach {
            assertEquals(config, it.copy(visualStyle = config.visualStyle, outfitStyle = config.outfitStyle))
        }
    }
}
