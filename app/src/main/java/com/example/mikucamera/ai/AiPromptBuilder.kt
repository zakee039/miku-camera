package com.example.mikucamera.ai

object AiPromptBuilder {
    fun build(
        userPrompt: String,
        captureTime: String,
        captureLocation: String,
        visualStyle: AiVisualStyle,
        outfitStyle: AiOutfitStyle,
        includeTimeWatermark: Boolean,
        includeLocationWatermark: Boolean
    ): String {
        val location = captureLocation.ifBlank { "地点信息未获取" }
        val visualDirection = when (visualStyle) {
            AiVisualStyle.ANIME ->
                "MANDATORY ANIME MODE: Miku herself must be a clearly hand-drawn Japanese 2D anime character with visible clean line art, expressive anime facial features, and cel shading. She must not look like a real human, live-action cosplayer, photorealistic person, 3D CGI render, figurine, doll, or game character. Integrate the 2D anime Miku convincingly into the photographed scene without converting real people or the whole source photograph into anime."
            AiVisualStyle.REALISTIC ->
                "MANDATORY LIVE-ACTION PHOTOREALISTIC MODE: Depict Miku as a believable real adult human woman who was physically present when this camera photograph was taken. She must have realistic human facial anatomy, natural skin texture and pores, individual hair strands, physically realistic fabric, body proportions, lighting, shadows, reflections, depth of field, lens characteristics, and camera noise matching the source photograph. Preserve her unmistakable turquoise twin tails and Miku identity. STRICTLY FORBIDDEN: anime, manga, illustration, drawing, painting, cel shading, 2D line art, semi-realistic anime, 3D CGI, game-render appearance, plastic skin, figurine, doll, toy, mannequin, or cosplay-photo styling. The result must read as an authentic live-action photograph, not rendered artwork."
        }
        val outfitDirection = when (outfitStyle) {
            AiOutfitStyle.OFFICIAL ->
                "MANDATORY OFFICIAL OUTFIT MODE: Miku must wear her iconic official-style outfit: turquoise twin tails, gray sleeveless top, black detached sleeves, black pleated skirt, turquoise accents, and thigh-high boots. Do not redesign it into unrelated clothing."
            AiOutfitStyle.SCENE_ADAPTIVE ->
                "MANDATORY SCENE-ADAPTIVE NON-OFFICIAL OUTFIT MODE: First inspect the photographed place, weather, temperature, season, time of day, activity, and social context, then choose a concrete outfit that a person would naturally wear there. Examples include a summer dress or T-shirt and shorts outdoors in heat, a coat and scarf in winter, casual streetwear in a city, sportswear for exercise, hiking clothes in nature, a swimsuit or light resort wear at a beach, school or campus casual wear near a school, or elegant attire at a formal venue. The clothing must visibly respond to this specific scene and must not default to Miku's iconic official costume. STRICTLY FORBIDDEN in this mode: the complete official combination of gray sleeveless top, black detached arm sleeves, black pleated mini skirt, turquoise necktie/accent trim, and thigh-high boots. Do not use stage-performance, cosplay, or generic idol clothing unless the photographed activity itself clearly requires it. Preserve her turquoise twin tails and unmistakable Miku identity through hair and color accents, not by reverting to the official costume."
        }
        val watermarkDirection = buildWatermarkDirection(
            captureTime = captureTime,
            location = location,
            includeTime = includeTimeWatermark,
            includeLocation = includeLocationWatermark
        )
        val finalExclusion = when {
            includeTimeWatermark && includeLocationWatermark ->
                "Do not add an ordinary app mark, logo, signature, extra date, or extra place name."
            includeTimeWatermark ->
                "Do not add a logo, signature, or any extra date beyond the selected text."
            includeLocationWatermark ->
                "Do not add a logo, signature, or any extra place name beyond the selected text."
            else -> "Do not add any logo or signature."
        }
        return """
            Edit the supplied clean camera photograph as one coherent finished photo. Preserve the input photograph's original composition and aspect ratio, and do not arbitrarily crop or reframe important people or subjects.
            Integrate Hatsune Miku naturally into the photographed scene. Preserve the real people, their identities, faces, bodies, poses, and the main composition. If people are visible, let Miku interact with them in a friendly and believable way without replacing or deforming anyone. If the photo is scenery only, blend Miku into the environment with matching perspective, scale, lighting, shadows, reflections, depth, and color.

            $watermarkDirection

            User direction for this photo:
            ${userPrompt.ifBlank { AiSettingsStore.DEFAULT_PROMPT }}

            FINAL MANDATORY STYLE OVERRIDE — this has higher priority than any conflicting style words in the user direction above:
            $visualDirection

            MANDATORY OUTFIT DIRECTION:
            $outfitDirection

            Before returning the image, verify that Miku unmistakably follows the selected mandatory visual style. Keep the result visually coherent with that style. $finalExclusion
        """.trimIndent()
    }

    private fun buildWatermarkDirection(
        captureTime: String,
        location: String,
        includeTime: Boolean,
        includeLocation: Boolean
    ): String {
        val decoration =
            "Make it playful, cute, charming, visually interesting, and clearly readable. Match it to the scene using tasteful Miku-themed turquoise accents, small music notes, stars, hearts, ribbons, stickers, rounded cards, or other context-appropriate kawaii decorations. Keep it away from faces and important subjects."
        return when {
            includeTime && includeLocation -> """
                Design a decorative time-and-location mark as an integral part of the generated image. $decoration Give the selected time and location a coherent two-line composition instead of a plain utilitarian camera timestamp. Render only the following exact selected Chinese text once; do not translate, rewrite, omit, duplicate, or invent any characters:
                时间：$captureTime
                地点：$location
            """.trimIndent()
            includeTime -> """
                Design a decorative time mark as an integral part of the generated image. $decoration Use one compact decorative line instead of a plain utilitarian camera timestamp. Render only the following exact selected Chinese text once; do not translate, rewrite, omit, duplicate, or invent any characters:
                时间：$captureTime
            """.trimIndent()
            includeLocation -> """
                Design a decorative location mark as an integral part of the generated image. $decoration Use one compact decorative line. Render only the following exact selected Chinese text once; do not translate, rewrite, omit, duplicate, or invent any characters:
                地点：$location
            """.trimIndent()
            else -> ""
        }
    }
}
