package com.example.mikucamera.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mikucamera.R
import com.example.mikucamera.ui.AiOverlayService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap

/** Runs AI requests outside MainActivity so returning to the camera never cancels a task. */
class AiGenerationService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private val running = AtomicInteger(0)
    private val clients = ConcurrentHashMap<String, AiImageClient>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
        if (intent.action == ACTION_CANCEL) {
            clients[taskId]?.cancel()
            AiTransactionStore(this).update(taskId) { it.copy(state = AiTransactionState.FAILED, message = "已取消生成") }
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification("Miku 正在后台创作"))
        running.incrementAndGet()
        executor.execute { generate(taskId, startId) }
        return START_NOT_STICKY
    }

    private fun generate(taskId: String, startId: Int) {
        val store = AiTransactionStore(this)
        val task = store.get(taskId) ?: return finishTask(startId)
        if (task.state != AiTransactionState.RUNNING) return finishTask(startId)
        val settings = AiSettingsStore(this).load()
        val profile = settings.activeProfile
        if (settings.apiKey.isBlank() || !profile.preset.supportsCurrentProtocol) {
            store.update(taskId) { it.copy(state = AiTransactionState.FAILED, message = "AI 配置不可用") }
            return finishTask(startId)
        }
        val destination = store.newFile("miku_ai_result_", ".jpg")
        try {
            store.update(taskId) { it.copy(state = AiTransactionState.RUNNING, message = "正在准备干净照片", resultPath = null, resultSaved = false) }
            val prompt = AiPromptBuilder.build(task.prompt, task.captureTime, task.captureLocation, settings.visualStyle,
                settings.outfitStyle, task.includeTimeWatermark, task.includeLocationWatermark)
            val client = AiImageClient()
            clients[taskId] = client
            client.createEdit(
                baseUrl = settings.baseUrl, endpointPath = profile.endpoint, apiKey = settings.apiKey, model = settings.model,
                visualStyle = settings.visualStyle, outfitStyle = settings.outfitStyle, source = task.originalFile,
                prompt = prompt, destination = destination,
                onProgress = { status -> store.update(taskId) { current -> current.copy(message = status) } },
                useGenerationsProtocol = profile.preset.useGenerationsProtocol,
                useGeminiProtocol = profile.preset.useGeminiProtocol,
                useQwenProtocol = profile.preset.useQwenProtocol
            )
            store.update(taskId) { it.copy(state = AiTransactionState.SUCCESS, message = "生成完成", resultPath = destination.absolutePath) }
        } catch (error: Throwable) {
            destination.delete()
            store.update(taskId) { it.copy(state = AiTransactionState.FAILED, message = error.message?.take(120) ?: "生成失败") }
        } finally {
            clients.remove(taskId)
            AiOverlayService.refresh(this)
            finishTask(startId)
        }
    }

    private fun finishTask(startId: Int) {
        if (running.decrementAndGet() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    private fun notification(text: String): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Miku AI 事务", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("miku camera").setContentText(text).setOngoing(true).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }

    companion object {
        private const val CHANNEL_ID = "miku_ai_generation"; private const val NOTIFICATION_ID = 301
        private const val EXTRA_TASK_ID = "task_id"; private const val ACTION_CANCEL = "cancel"
        fun start(context: Context, taskId: String) {
            ContextCompat.startForegroundService(context, Intent(context, AiGenerationService::class.java).putExtra(EXTRA_TASK_ID, taskId))
        }
        fun cancel(context: Context, taskId: String) {
            ContextCompat.startForegroundService(context, Intent(context, AiGenerationService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_TASK_ID, taskId))
        }
    }
}
