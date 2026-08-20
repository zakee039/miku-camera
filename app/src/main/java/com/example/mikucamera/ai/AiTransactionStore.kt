package com.example.mikucamera.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class AiTransactionState { RUNNING, SUCCESS, FAILED }

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
    val resultSaved: Boolean = false
) {
    val originalFile get() = File(originalPath)
    val resultFile get() = resultPath?.let(::File)
}

/** Durable task list shared by the activity, generator and system overlay. */
class AiTransactionStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val transactionDir = File(context.filesDir, "ai_transactions").apply { if (!exists()) mkdirs() }

    @Synchronized fun all(): List<AiTransaction> = read().sortedByDescending { it.createdAt }

    @Synchronized fun get(id: String): AiTransaction? = read().firstOrNull { it.id == id }

    @Synchronized fun create(transaction: AiTransaction): AiTransaction {
        write(read() + transaction)
        notifyChanged()
        return transaction
    }

    @Synchronized fun update(id: String, transform: (AiTransaction) -> AiTransaction): AiTransaction? {
        var updated: AiTransaction? = null
        write(read().map { task ->
            if (task.id == id) transform(task).also { updated = it } else task
        })
        if (updated != null) notifyChanged()
        return updated
    }

    @Synchronized fun remove(id: String, deleteFiles: Boolean = true) {
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
    }

    private fun JSONObject.toTask() = AiTransaction(
        id = optString("id"), originalPath = optString("originalPath"), captureTime = optString("captureTime"),
        captureLocation = optString("captureLocation"), prompt = optString("prompt"),
        includeTimeWatermark = optBoolean("includeTime", true), includeLocationWatermark = optBoolean("includeLocation", true),
        createdAt = optLong("createdAt", System.currentTimeMillis()),
        state = runCatching { AiTransactionState.valueOf(optString("state")) }.getOrDefault(AiTransactionState.RUNNING),
        message = optString("message", "等待开始生成"), resultPath = optString("resultPath").ifBlank { null },
        resultSaved = optBoolean("resultSaved", false)
    )

    companion object {
        const val ACTION_CHANGED = "com.example.mikucamera.AI_TRANSACTION_CHANGED"
        private const val PREFS = "ai_transactions"
        private const val KEY_TASKS = "tasks"
    }
}
