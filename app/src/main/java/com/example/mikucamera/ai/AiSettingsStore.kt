package com.example.mikucamera.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiVisualStyle(val displayName: String) {
    ANIME("二次元"), REALISTIC("三次元（真人写实）");
    companion object { fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: ANIME }
}

enum class AiOutfitStyle(val displayName: String) {
    OFFICIAL("公式服"), SCENE_ADAPTIVE("依据场景自行搭配（非公式服）");
    companion object { fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: SCENE_ADAPTIVE }
}

enum class AiServicePreset(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultEndpoint: String,
    val defaultModel: String,
    val supportsCurrentProtocol: Boolean,
    val useGenerationsProtocol: Boolean = false,
    val useGeminiProtocol: Boolean = false,
    val useQwenProtocol: Boolean = false
) {
    OPENAI("OpenAI", "https://api.openai.com/v1", "/images/edits", "gpt-image-2", true),
    VOLCENGINE_ARK("字节跳动 / 火山方舟", "https://ark.cn-beijing.volces.com/api/v3", "/images/generations", "doubao-seedream-5-0-260128", true, true),
    QWEN("Qwen / 阿里云百炼", "https://dashscope.aliyuncs.com", "/api/v1/services/aigc/multimodal-generation/generation", "qwen-image-3.0-pro", true, false, false, true),
    GEMINI("Gemini / Nano Banana", "https://generativelanguage.googleapis.com", "/v1beta/interactions", "gemini-3.1-flash-image", true, false, true),
    CUSTOM_OPENAI("自定义 OpenAI 兼容", "", "/images/edits", "", true);

    companion object { fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: CUSTOM_OPENAI }
}

data class AiApiProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val preset: AiServicePreset,
    val baseUrl: String,
    val endpoint: String,
    val apiKey: String,
    val model: String
)

data class AiSettings(
    val profiles: List<AiApiProfile>,
    val activeProfileId: String,
    val visualStyle: AiVisualStyle,
    val outfitStyle: AiOutfitStyle,
    val defaultPrompt: String,
    val includeTimeWatermark: Boolean,
    val includeLocationWatermark: Boolean
) {
    val activeProfile: AiApiProfile get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles.first()
    val baseUrl: String get() = activeProfile.baseUrl
    val apiKey: String get() = activeProfile.apiKey
    val model: String get() = activeProfile.model
}

class AiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AiSettings {
        val storedProfiles = loadProfiles()
        val profiles = storedProfiles
            .map(::migrateProtocolProfile)
            .ifEmpty { listOf(legacyProfile()) }
        val activeId = prefs.getString(KEY_ACTIVE_PROFILE, null).takeIf { id -> profiles.any { it.id == id } } ?: profiles.first().id
        if (profiles != storedProfiles && storedProfiles.isNotEmpty()) {
            saveProfiles(profiles, activeId)
        }
        return AiSettings(profiles, activeId, AiVisualStyle.fromStored(prefs.getString(KEY_VISUAL_STYLE, null)),
            AiOutfitStyle.fromStored(prefs.getString(KEY_OUTFIT_STYLE, null)),
            prefs.getString(KEY_DEFAULT_PROMPT, DEFAULT_PROMPT) ?: DEFAULT_PROMPT,
            prefs.getBoolean(KEY_TIME_WATERMARK, true), prefs.getBoolean(KEY_LOCATION_WATERMARK, true))
    }

    fun saveImageSettings(visualStyle: AiVisualStyle, outfitStyle: AiOutfitStyle, defaultPrompt: String, includeTime: Boolean, includeLocation: Boolean) {
        prefs.edit().putString(KEY_VISUAL_STYLE, visualStyle.name).putString(KEY_OUTFIT_STYLE, outfitStyle.name)
            .putString(KEY_DEFAULT_PROMPT, defaultPrompt.trim()).putBoolean(KEY_TIME_WATERMARK, includeTime)
            .putBoolean(KEY_LOCATION_WATERMARK, includeLocation).apply()
    }

    // Compatibility bridge for callers migrating from the single-profile settings UI.
    fun save(baseUrl: String, apiKey: String, model: String, visualStyle: AiVisualStyle, outfitStyle: AiOutfitStyle, defaultPrompt: String) {
        val current = load()
        val profile = current.activeProfile.copy(baseUrl = baseUrl, apiKey = apiKey, model = model)
        saveProfiles(current.profiles.map { if (it.id == profile.id) profile else it }, profile.id)
        saveImageSettings(visualStyle, outfitStyle, defaultPrompt, current.includeTimeWatermark, current.includeLocationWatermark)
    }

    fun saveProfiles(profiles: List<AiApiProfile>, activeProfileId: String) {
        require(profiles.isNotEmpty()) { "至少保留一组 API 配置" }
        val array = JSONArray()
        profiles.forEach { profile ->
            require(profile.name.isNotBlank()) { "配置名称不能为空" }
            require(profile.model.isNotBlank()) { "模型名称不能为空" }
            val baseUrl = AiImageClient.normalizeBaseUrl(profile.baseUrl)
            array.put(JSONObject().put("id", profile.id).put("name", profile.name.trim()).put("preset", profile.preset.name)
                .put("baseUrl", baseUrl).put("endpoint", profile.endpoint.trim()).put("model", profile.model.trim())
                .put("apiKey", encrypt(profile.apiKey.trim())))
        }
        prefs.edit().putString(KEY_PROFILES, array.toString()).putString(KEY_ACTIVE_PROFILE, activeProfileId).apply()
    }

    fun hasAcceptedUploadNotice() = prefs.getBoolean(KEY_NOTICE_ACCEPTED, false)
    fun acceptUploadNotice() { prefs.edit().putBoolean(KEY_NOTICE_ACCEPTED, true).apply() }

    private fun loadProfiles(): List<AiApiProfile> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PROFILES, "[]"))
        List(array.length()) { index -> array.getJSONObject(index).let { item ->
            AiApiProfile(item.optString("id").ifBlank { UUID.randomUUID().toString() }, item.optString("name"),
                AiServicePreset.fromStored(item.optString("preset")), item.optString("baseUrl"), item.optString("endpoint"),
                decrypt(item.optString("apiKey")).orEmpty(), item.optString("model"))
        }}
    }.getOrDefault(emptyList())

    /**
     * 1.4.3 的 Qwen/Gemini 预设保存的是当时未实现的占位协议地址。
     * 仅替换已知的旧默认值，用户手动填写过的服务地址、接口或模型保持不变。
     */
    private fun migrateProtocolProfile(profile: AiApiProfile): AiApiProfile = when (profile.preset) {
        AiServicePreset.QWEN -> profile.copy(
            baseUrl = profile.baseUrl.replaceKnownLegacyValue(
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                AiServicePreset.QWEN.defaultBaseUrl
            ),
            endpoint = profile.endpoint.replaceKnownLegacyValue(
                "/images/generations",
                AiServicePreset.QWEN.defaultEndpoint
            ),
            model = profile.model.replaceKnownLegacyValue(
                "qwen-image-plus",
                AiServicePreset.QWEN.defaultModel
            )
        )
        AiServicePreset.GEMINI -> profile.copy(
            baseUrl = profile.baseUrl.replaceKnownLegacyValue(
                "https://generativelanguage.googleapis.com/v1beta",
                AiServicePreset.GEMINI.defaultBaseUrl
            ),
            endpoint = profile.endpoint.replaceKnownLegacyValue(
                "/models/gemini-2.5-flash-image:generateContent",
                AiServicePreset.GEMINI.defaultEndpoint
            ),
            model = profile.model.replaceKnownLegacyValue(
                "gemini-2.5-flash-image",
                AiServicePreset.GEMINI.defaultModel
            )
        )
        else -> profile
    }

    private fun String.replaceKnownLegacyValue(legacy: String, replacement: String): String =
        if (this == legacy) replacement else this

    private fun legacyProfile() = AiApiProfile(name = "OpenAI", preset = AiServicePreset.OPENAI,
        baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL, endpoint = "/images/edits",
        apiKey = decrypt(prefs.getString(KEY_API_KEY, null)).orEmpty(), model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)

    private fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }
    private fun decrypt(value: String?): String? = if (value.isNullOrBlank()) null else runCatching {
        val parts = value.split(':', limit = 2); require(parts.size == 2)
        val cipher = Cipher.getInstance(TRANSFORMATION); cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
    }.getOrNull()
    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }; (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()); generateKey()
        }
    }
    companion object {
        private const val PREFS = "ai_settings"; private const val KEY_PROFILES = "profiles"; private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_VISUAL_STYLE = "visual_style"; private const val KEY_OUTFIT_STYLE = "outfit_style"; private const val KEY_DEFAULT_PROMPT = "default_prompt"
        private const val KEY_TIME_WATERMARK = "time_watermark"; private const val KEY_LOCATION_WATERMARK = "location_watermark"; private const val KEY_NOTICE_ACCEPTED = "upload_notice_accepted"
        private const val KEY_BASE_URL = "base_url"; private const val KEY_API_KEY = "api_key_encrypted"; private const val KEY_MODEL = "model"
        private const val KEY_ALIAS = "miku_camera_openai_key"; private const val ANDROID_KEYSTORE = "AndroidKeyStore"; private const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"; const val DEFAULT_MODEL = "gpt-image-2"
        const val DEFAULT_PROMPT = "让 Miku 自然地融入照片。若画面中有人，让 Miku 与人物产生自然有趣的互动；若是纯风景，让 Miku 与环境、光线和构图自然融合。"
    }
}
