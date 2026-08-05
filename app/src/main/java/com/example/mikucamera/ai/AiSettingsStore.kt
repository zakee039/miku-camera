package com.example.mikucamera.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiVisualStyle(val displayName: String) {
    ANIME("二次元"),
    REALISTIC("三次元（真人写实）");

    companion object {
        fun fromStored(value: String?): AiVisualStyle =
            entries.firstOrNull { it.name == value } ?: ANIME
    }
}

enum class AiOutfitStyle(val displayName: String) {
    OFFICIAL("公式服"),
    SCENE_ADAPTIVE("依据场景自行搭配（非公式服）");

    companion object {
        fun fromStored(value: String?): AiOutfitStyle =
            entries.firstOrNull { it.name == value } ?: SCENE_ADAPTIVE
    }
}

data class AiSettings(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val visualStyle: AiVisualStyle,
    val outfitStyle: AiOutfitStyle,
    val defaultPrompt: String
)

class AiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): AiSettings = AiSettings(
        baseUrl = prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
        apiKey = decrypt(prefs.getString(KEY_API_KEY, null)).orEmpty(),
        model = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL,
        visualStyle = AiVisualStyle.fromStored(prefs.getString(KEY_VISUAL_STYLE, null)),
        outfitStyle = AiOutfitStyle.fromStored(prefs.getString(KEY_OUTFIT_STYLE, null)),
        defaultPrompt = prefs.getString(KEY_DEFAULT_PROMPT, DEFAULT_PROMPT) ?: DEFAULT_PROMPT
    )

    fun save(
        baseUrl: String,
        apiKey: String,
        model: String,
        visualStyle: AiVisualStyle,
        outfitStyle: AiOutfitStyle,
        defaultPrompt: String
    ) {
        val normalizedModel = model.trim()
        require(normalizedModel.isNotBlank()) { "模型名称不能为空" }
        require(normalizedModel.length <= 200 && normalizedModel.none { it == '\r' || it == '\n' }) {
            "模型名称格式无效"
        }
        val editor = prefs.edit()
            .putString(KEY_BASE_URL, AiImageClient.normalizeBaseUrl(baseUrl))
            .putString(KEY_MODEL, normalizedModel)
            .putString(KEY_VISUAL_STYLE, visualStyle.name)
            .putString(KEY_OUTFIT_STYLE, outfitStyle.name)
            .putString(KEY_DEFAULT_PROMPT, defaultPrompt.trim())
        if (apiKey.isBlank()) {
            editor.remove(KEY_API_KEY)
        } else {
            editor.putString(KEY_API_KEY, encrypt(apiKey.trim()))
        }
        editor.apply()
    }

    fun hasAcceptedUploadNotice(): Boolean = prefs.getBoolean(KEY_NOTICE_ACCEPTED, false)

    fun acceptUploadNotice() {
        prefs.edit().putBoolean(KEY_NOTICE_ACCEPTED, true).apply()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val encrypted = Base64.encodeToString(cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return "$iv:$encrypted"
    }

    private fun decrypt(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return runCatching {
            val parts = value.split(':', limit = 2)
            require(parts.size == 2)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        private const val PREFS = "ai_settings"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key_encrypted"
        private const val KEY_MODEL = "model"
        private const val KEY_VISUAL_STYLE = "visual_style"
        private const val KEY_OUTFIT_STYLE = "outfit_style"
        private const val KEY_DEFAULT_PROMPT = "default_prompt"
        private const val KEY_NOTICE_ACCEPTED = "upload_notice_accepted"
        private const val KEY_ALIAS = "miku_camera_openai_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-image-1.5"

        const val DEFAULT_PROMPT =
            "让 Miku 自然地融入照片。若画面中有人，让 Miku 与人物产生自然有趣的互动；" +
                "若是纯风景，让 Miku 与环境、光线和构图自然融合。"
    }
}
