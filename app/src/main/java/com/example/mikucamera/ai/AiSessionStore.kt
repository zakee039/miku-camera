package com.example.mikucamera.ai

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 持久化 AI 拍摄会话，使进程被杀后重开仍能恢复到上次的提示词页 / 结果页。
 *
 * 说明：
 * - 原图与结果图保存在 filesDir/ai_session/ 下（系统不会像 cacheDir 那样自动清理）。
 * - 生成中的网络请求不续跑；若进程在生成阶段被杀，恢复时按 PROMPT 处理（保留原图，可重新生成）。
 */
class AiSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val sessionDir = File(context.filesDir, "ai_session").apply { if (!exists()) mkdirs() }

    data class Snapshot(
        val originalPath: String,
        val captureTime: String,
        val captureLocation: String,
        val prompt: String,
        val stage: String, // PROMPT / GENERATING / RESULT
        val resultPath: String?,
        val resultSaved: Boolean = false,
        val transactionId: String? = null
    ) {
        val originalFile get() = File(originalPath)
        val resultFile get() = resultPath?.let { File(it) }
    }

    fun save(snapshot: Snapshot) {
        prefs.edit().putString(KEY_SNAPSHOT, JSONObject().apply {
            put("originalPath", snapshot.originalPath)
            put("captureTime", snapshot.captureTime)
            put("captureLocation", snapshot.captureLocation)
            put("prompt", snapshot.prompt)
            put("stage", snapshot.stage)
            put("resultPath", snapshot.resultPath ?: "")
            put("resultSaved", snapshot.resultSaved)
            put("transactionId", snapshot.transactionId ?: "")
        }.toString()).apply()
    }

    fun load(): Snapshot? = runCatching {
        val raw = prefs.getString(KEY_SNAPSHOT, null) ?: return null
        val json = JSONObject(raw)
        val originalPath = json.optString("originalPath").takeIf { it.isNotBlank() } ?: return null
        // 原图文件必须仍存在，否则恢复无意义。
        if (!File(originalPath).exists()) {
            clear()
            return null
        }
        val resultPath = json.optString("resultPath").takeIf { it.isNotBlank() }
        if (resultPath != null && !File(resultPath).exists()) {
            // 结果图丢失则降级为提示词阶段。
            return Snapshot(
                originalPath = originalPath,
                captureTime = json.optString("captureTime", ""),
                captureLocation = json.optString("captureLocation", ""),
                prompt = json.optString("prompt", ""),
                stage = "PROMPT",
                resultPath = null,
                resultSaved = false,
                transactionId = json.optString("transactionId").ifBlank { null }
            )
        }
        var stage = json.optString("stage", "PROMPT")
        if (stage == "RESULT" && resultPath == null) stage = "PROMPT"
        Snapshot(
            originalPath = originalPath,
            captureTime = json.optString("captureTime", ""),
            captureLocation = json.optString("captureLocation", ""),
            prompt = json.optString("prompt", ""),
            stage = stage,
            resultPath = resultPath,
            resultSaved = json.optBoolean("resultSaved", false),
            transactionId = json.optString("transactionId").ifBlank { null }
        )
    }.getOrNull()

    /** 持久化目录下的新文件（原图/结果图），避免被系统清理。 */
    fun newFile(prefix: String, suffix: String): File =
        File.createTempFile(prefix, suffix, sessionDir)

    fun clear(deleteFiles: Boolean = true) {
        if (deleteFiles) load()?.let { snapshot ->
            snapshot.originalFile.delete()
            snapshot.resultFile?.delete()
        }
        prefs.edit().remove(KEY_SNAPSHOT).apply()
    }

    companion object {
        private const val PREFS = "ai_session"
        private const val KEY_SNAPSHOT = "snapshot"
    }
}
