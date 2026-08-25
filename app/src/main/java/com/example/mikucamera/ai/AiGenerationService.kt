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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Adaptive runner: grows from one lane to three after success, and backs off on rate limits. */
class AiGenerationService : Service() {
    private val workExecutor = ThreadPoolExecutor(1, MAX_PARALLEL, 30, TimeUnit.SECONDS, LinkedBlockingQueue())
    private val retryScheduler = ScheduledThreadPoolExecutor(1)
    private val clients = ConcurrentHashMap<String, AiImageClient>()
    private val activeWork = AtomicInteger(0)
    private val pendingRetries = AtomicInteger(0)
    private val rateLimitUntil = AtomicLong(0L)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
        if (intent.action == ACTION_CANCEL) {
            clients[taskId]?.cancel()
            AiTransactionStore(this).update(taskId) { it.copy(state = AiTransactionState.FAILED, message = "已取消生成") }
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification("Miku 正在后台创作"))
        submit(taskId, startId)
        return START_NOT_STICKY
    }

    private fun submit(taskId: String, startId: Int) {
        activeWork.incrementAndGet()
        workExecutor.execute { generate(taskId, startId) }
    }

    private fun generate(taskId: String, startId: Int) {
        val store = AiTransactionStore(this)
        val task = store.get(taskId) ?: return finishWork(startId)
        if (task.state != AiTransactionState.RUNNING) return finishWork(startId)
        val waitMillis = rateLimitUntil.get() - System.currentTimeMillis()
        if (waitMillis > 0) {
            scheduleRetry(taskId, startId, store, waitMillis, "请求过多，正在排队等待")
            return finishWork(startId)
        }
        val settings = AiSettingsStore(this).load()
        val configuration = task.configuration ?: AiGenerationConfiguration.from(settings)
        // Credentials are resolved from the original profile when it still exists.
        // They are not persisted in a task, avoiding a plaintext API-key copy.
        val credentialProfile = settings.profiles.firstOrNull { it.id == configuration.profileId }
        if (credentialProfile == null) {
            store.update(taskId) { it.copy(state = AiTransactionState.FAILED, message = "事务使用的 API 配置已被删除") }
            return finishWork(startId)
        }
        if (credentialProfile.apiKey.isBlank() || !configuration.preset.supportsCurrentProtocol) {
            store.update(taskId) { it.copy(state = AiTransactionState.FAILED, message = "AI 配置不可用") }
            return finishWork(startId)
        }
        val destination = store.newFile("miku_ai_result_", ".jpg")
        try {
            store.update(taskId) { it.copy(message = "正在准备干净照片", resultPath = null, resultSaved = false) }
            val prompt = AiPromptBuilder.build(task.prompt, task.captureTime, task.captureLocation, configuration.visualStyle,
                configuration.outfitStyle, task.includeTimeWatermark, task.includeLocationWatermark)
            val client = AiImageClient()
            clients[taskId] = client
            client.createEdit(
                baseUrl = configuration.baseUrl, endpointPath = configuration.endpoint, apiKey = credentialProfile.apiKey, model = configuration.model,
                visualStyle = configuration.visualStyle, outfitStyle = configuration.outfitStyle, source = task.originalFile,
                prompt = prompt, destination = destination,
                onProgress = { status -> store.update(taskId) { current -> current.copy(message = status) } },
                useGenerationsProtocol = configuration.preset.useGenerationsProtocol,
                useGeminiProtocol = configuration.preset.useGeminiProtocol,
                useQwenProtocol = configuration.preset.useQwenProtocol
            )
            increaseConcurrency()
            store.update(taskId) { it.copy(state = AiTransactionState.SUCCESS, message = "生成完成", resultPath = destination.absolutePath) }
        } catch (error: Throwable) {
            destination.delete()
            if (isRateLimited(error)) {
                backOffAndRetry(taskId, startId, store)
                return
            }
            store.update(taskId) { it.copy(state = AiTransactionState.FAILED, message = error.message?.take(120) ?: "生成失败") }
        } finally {
            clients.remove(taskId)
            AiOverlayService.refresh(this)
            finishWork(startId)
        }
    }

    private fun backOffAndRetry(taskId: String, startId: Int, store: AiTransactionStore) {
        workExecutor.corePoolSize = 1
        val waitMillis = RETRY_DELAY_SECONDS * 1_000L
        rateLimitUntil.set(System.currentTimeMillis() + waitMillis)
        scheduleRetry(taskId, startId, store, waitMillis, "请求过多，15 秒后自动重试")
    }

    private fun scheduleRetry(taskId: String, startId: Int, store: AiTransactionStore, delayMillis: Long, message: String) {
        store.update(taskId) { it.copy(message = message) }
        AiOverlayService.refresh(this)
        pendingRetries.incrementAndGet()
        retryScheduler.schedule({
            pendingRetries.decrementAndGet()
            if (store.get(taskId)?.state == AiTransactionState.RUNNING) submit(taskId, startId)
            else stopIfIdle(startId)
        }, delayMillis, TimeUnit.MILLISECONDS)
    }

    private fun increaseConcurrency() {
        val next = (workExecutor.corePoolSize + 1).coerceAtMost(MAX_PARALLEL)
        if (next > workExecutor.corePoolSize) {
            workExecutor.corePoolSize = next
            workExecutor.prestartAllCoreThreads()
        }
    }

    private fun isRateLimited(error: Throwable): Boolean = error.message?.contains("429") == true ||
        error.message?.contains("请求过多") == true || error.message?.contains("rate limit", ignoreCase = true) == true

    private fun finishWork(startId: Int) {
        activeWork.decrementAndGet()
        stopIfIdle(startId)
    }

    private fun stopIfIdle(startId: Int) {
        if (activeWork.get() <= 0 && pendingRetries.get() <= 0 && workExecutor.queue.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
    }

    private fun notification(text: String): android.app.Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Miku AI 事务", NotificationManager.IMPORTANCE_LOW))
        return NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(R.drawable.ic_shutter_dot)
            .setContentTitle("miku camera").setContentText(text).setOngoing(true).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { workExecutor.shutdownNow(); retryScheduler.shutdownNow(); super.onDestroy() }

    companion object {
        private const val CHANNEL_ID = "miku_ai_generation"; private const val NOTIFICATION_ID = 301
        private const val EXTRA_TASK_ID = "task_id"; private const val ACTION_CANCEL = "cancel"
        private const val MAX_PARALLEL = 3; private const val RETRY_DELAY_SECONDS = 15L
        fun start(context: Context, taskId: String) {
            ContextCompat.startForegroundService(context, Intent(context, AiGenerationService::class.java).putExtra(EXTRA_TASK_ID, taskId))
        }
        fun retry(context: Context, taskId: String) {
            AiTransactionStore(context).update(taskId) {
                it.copy(state = AiTransactionState.RUNNING, message = "正在重新提交", resultPath = null, resultSaved = false)
            }
            // The overlay does not receive the activity's transaction broadcast.
            // Refresh it here so failed -> running is visible before network work starts.
            AiOverlayService.refresh(context.applicationContext)
            start(context, taskId)
        }
        fun cancel(context: Context, taskId: String) {
            ContextCompat.startForegroundService(context, Intent(context, AiGenerationService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_TASK_ID, taskId))
        }
    }
}
