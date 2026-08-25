package com.example.mikucamera.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class AiTransactionState { RUNNING, SUCCESS, FAILED }

/**
 * Non-secret generation choices frozen with a task.  API keys deliberately stay
 * in [AiSettingsStore], but all behaviour-affecting choices are kept here so a
 * retry cannot silently inherit a later global-settings change.
 */
data class AiGenerationConfiguration(
    val profileId: String,
    val preset: AiServicePreset,
    val baseUrl: String,
    val endpoint: String,
    val model: String,
    val visualStyle: AiVisualStyle,
    val outfitStyle: AiOutfitStyle
) {
    companion object {
        fun from(settings: AiSettings): AiGenerationConfiguration {
            val profile = settings.activeProfile
            return AiGenerationConfiguration(
                profileId = profile.id,
                preset = profile.preset,
                baseUrl = profile.baseUrl,
                endpoint = profile.endpoint,
                model = profile.model,
                visualStyle = settings.visualStyle,
                outfitStyle = settings.outfitStyle
            )
        }
    }
}

data class AiTransaction(
    val id: String = UUID.randomUUID().toString(),
    val originalPath: String,
    val captureTime: String,
    val captureLocation: String,
    val prompt: String,
    val includeTimeWatermark: Boolean,
    val includeLocationWatermark: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
    val state: AiTransactionState = AiTransactionState.RUNNING,
    val message: String = "等待开始生成",
    val resultPath: String? = null,
    val resultSaved: Boolean = false,
    /** Null only for transactions created by versions before configuration snapshots. */
    val configuration: AiGenerationConfiguration? = null,
    /** Settings may be refreshed while this is zero; once submitted, retries are frozen. */
    val attemptCount: Int = 0
) {
    val originalFile get() = File(originalPath)
    val resultFile get() = resultPath?.let(::File)
}

/** Durable task list shared by the activity, generator and system overlay. */
class AiTransactionStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val transactionDir = File(context.filesDir, "ai_transactions").apply { if (!exists()) mkdirs() }

    fun all(): List<AiTransaction> = synchronized(STORE_LOCK) {
        read().sortedByDescending { it.createdAt }
    }

    fun get(id: String): AiTransaction? = synchronized(STORE_LOCK) {
        read().firstOrNull { it.id == id }
    }

    fun create(transaction: AiTransaction): AiTransaction = synchronized(STORE_LOCK) {
        write(read() + transaction)
        notifyChanged()
        transaction
    }

    fun update(id: String, transform: (AiTransaction) -> AiTransaction): AiTransaction? = synchronized(STORE_LOCK) {
        var updated: AiTransaction? = null
        write(read().map { task ->
            if (task.id == id) transform(task).also { updated = it } else task
        })
        if (updated != null) notifyChanged()
        updated
    }

    fun remove(id: String, deleteFiles: Boolean = true) = synchronized(STORE_LOCK) {
        val current = read()
        current.firstOrNull { it.id == id }?.let { task ->
            if (deleteFiles) {
                task.originalFile.delete()
                task.resultFile?.delete()
            }
        }
        write(current.filterNot { it.id == id })
        notifyChanged()
    }

    fun newFile(prefix: String, suffix: String) = File.createTempFile(prefix, suffix, transactionDir)

    private fun notifyChanged() {
        context.sendBroadcast(android.content.Intent(ACTION_CHANGED).setPackage(context.packageName))
    }

    private fun read(): List<AiTransaction> = runCatching {
        val array = JSONArray(prefs.getString(KEY_TASKS, "[]"))
        List(array.length()) { index -> array.getJSONObject(index).toTask() }
            .filter { it.originalFile.exists() }
    }.getOrDefault(emptyList())

    private fun write(tasks: List<AiTransaction>) {
        val array = JSONArray()
        tasks.forEach { task -> array.put(task.toJson()) }
        prefs.edit().putString(KEY_TASKS, array.toString()).apply()
    }

    private fun AiTransaction.toJson() = JSONObject().apply {
        put("id", id); put("originalPath", originalPath); put("captureTime", captureTime)
        put("captureLocation", captureLocation); put("prompt", prompt); put("includeTime", includeTimeWatermark)
        put("includeLocation", includeLocationWatermark); put("createdAt", createdAt); put("state", state.name)
        put("message", message); put("resultPath", resultPath ?: ""); put("resultSaved", resultSaved)
        put("attemptCount", attemptCount)
        configuration?.let { config ->
            put("configuration", JSONObject().apply {
                put("profileId", config.profileId); put("preset", config.preset.name)
                put("baseUrl", config.baseUrl); put("endpoint", config.endpoint); put("model", config.model)
                put("visualStyle", config.visualStyle.name); put("outfitStyle", config.outfitStyle.name)
            })
        }
    }

    private fun JSONObject.toTask() = AiTransaction(
        id = optString("id"), originalPath = optString("originalPath"), captureTime = optString("captureTime"),
        captureLocation = optString("captureLocation"), prompt = optString("prompt"),
        includeTimeWatermark = optBoolean("includeTime", true), includeLocationWatermark = optBoolean("includeLocation", true),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        state = runCatching { AiTransactionState.valueOf(optString("state")) }.getOrDefault(AiTransactionState.RUNNING),
        message = optString("message", "等待开始生成"), resultPath = optString("resultPath").ifBlank { null },
        resultSaved = optBoolean("resultSaved", false),
        configuration = optJSONObject("configuration")?.let { config -> runCatching {
            AiGenerationConfiguration(
                profileId = config.optString("profileId"),
                preset = AiServicePreset.fromStored(config.optString("preset")),
                baseUrl = config.optString("baseUrl"),
                endpoint = config.optString("endpoint"),
                model = config.optString("model"),
                visualStyle = AiVisualStyle.fromStored(config.optString("visualStyle")),
                outfitStyle = AiOutfitStyle.fromStored(config.optString("outfitStyle"))
            )
        }.getOrNull() },
        attemptCount = optInt("attemptCount", 0)
    )

    companion object {
        const val ACTION_CHANGED = "com.example.mikucamera.AI_TRANSACTION_CHANGED"
        /** Shared by every Activity/Service store instance in this app process. */
        private val STORE_LOCK = Any()
        private const val PREFS = "ai_transactions"
        private const val KEY_TASKS = "tasks"
    }
}
