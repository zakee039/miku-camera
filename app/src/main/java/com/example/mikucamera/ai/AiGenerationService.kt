package com.example.mikucamera.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mikucamera.R
import com.example.mikucamera.ui.AiOverlayService
import java.io.EOFException
import java.net.ProtocolException
import java.net.SocketException
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
    private val rateLimitUntil = ConcurrentHashMap<String, AtomicLong>()
    private val abortRetries = ConcurrentHashMap<String, AtomicInteger>()
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val lockSync = Any()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID) ?: return START_NOT_STICKY
        if (intent.action == ACTION_CANCEL) {
            abortRetries.remove(taskId)
            clients[taskId]?.cancel()
            AiTransactionStore(this).update(taskId) { it.copy(state = AiTransactionState.FAILED, message = "已取消生成") }
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, notification("Miku 正在后台创作"))
        acquireLocks()
        submit(taskId, startId)
        return START_NOT_STICKY
    }

    private fun acquireLocks() = synchronized(lockSync) {
        runCatching {
            val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (wakeLock == null && pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "miku:AiGenerationWakeLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(15 * 60 * 1000L)
            }
        }
        runCatching {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wifiLock == null && wm != null) {
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm.createWifiLock(mode, "miku:AiGenerationWifiLock").apply {
                    setReferenceCounted(false)
                }
            }
            if (wifiLock?.isHeld == false) {
                wifiLock?.acquire()
            }
        }
    }

    private fun releaseLocks() = synchronized(lockSync) {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        runCatching {
            if (wifiLock?.isHeld == true) wifiLock?.release()
        }
    }

    private fun submit(taskId: String, startId: Int) {
        acquireLocks()
        activeWork.incrementAndGet()
        workExecutor.execute { generate(taskId, startId) }
    }

    private fun generate(taskId: String, startId: Int) {
        val store = AiTransactionStore(this)
        val task = store.get(taskId) ?: return finishWork(startId)
        if (task.state != AiTransactionState.RUNNING) return finishWork(startId)
        val settings = AiSettingsStore(this).load()
        val configuration = task.configuration ?: AiGenerationConfiguration.from(settings)
        val cooldownKey = "${configuration.profileId}|${configuration.model}"
        val waitMillis = (rateLimitUntil[cooldownKey]?.get() ?: 0L) - System.currentTimeMillis()
        if (waitMillis > 0) {
            scheduleRetry(taskId, startId, store, waitMillis, "服务额度冷却中，${formatRetryDelay((waitMillis + 999L) / 1_000L)}后自动重试")
            return finishWork(startId)
        }
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
            val refFile = task.referenceFile
            val prompt = AiPromptBuilder.build(
                userPrompt = task.prompt,
                captureTime = task.captureTime,
                captureLocation = task.captureLocation,
                visualStyle = configuration.visualStyle,
                outfitStyle = configuration.outfitStyle,
                includeTimeWatermark = task.includeTimeWatermark,
                includeLocationWatermark = task.includeLocationWatermark,
                hasReferenceImage = refFile != null
            )
            val client = AiImageClient()
            clients[taskId] = client
            client.createEdit(
                baseUrl = configuration.baseUrl, endpointPath = configuration.endpoint, apiKey = credentialProfile.apiKey, model = configuration.model,
                visualStyle = configuration.visualStyle, outfitStyle = configuration.outfitStyle, source = task.originalFile,
                prompt = prompt, destination = destination,
                onProgress = { status -> store.update(taskId) { current -> current.copy(message = status) } },
                useGenerationsProtocol = configuration.preset.useGenerationsProtocol,
                useGeminiProtocol = configuration.preset.useGeminiProtocol,
                useQwenProtocol = configuration.preset.useQwenProtocol,
                resolution = configuration.resolution,
                referenceImage = refFile
            )
            increaseConcurrency()
            rateLimitUntil.remove(cooldownKey)
            abortRetries.remove(taskId)
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(destination.absolutePath, bounds)
            store.update(taskId) { it.copy(state = AiTransactionState.SUCCESS, message = "生成完成 · ${bounds.outWidth} × ${bounds.outHeight}", resultPath = destination.absolutePath) }
        } catch (error: Throwable) {
            destination.delete()
            if (isRateLimited(error)) {
                backOffAndRetry(taskId, startId, store, cooldownKey, error)
                return
            }
            if (isConnectionAborted(error)) {
                val count = abortRetries.computeIfAbsent(taskId) { AtomicInteger(0) }.incrementAndGet()
                if (count <= MAX_ABORT_RETRIES && store.get(taskId)?.state == AiTransactionState.RUNNING) {
                    scheduleRetry(taskId, startId, store, ABORT_RETRY_DELAY_MILLIS, "网络中断，正在自动重连重试（$count/$MAX_ABORT_RETRIES）")
                    return
                }
            }
            abortRetries.remove(taskId)
            store.update(taskId) { it.copy(state = AiTransactionState.FAILED, message = error.message?.take(800) ?: "生成失败") }
        } finally {
            clients.remove(taskId)
            AiOverlayService.refresh(this)
            finishWork(startId)
        }
    }

    private fun backOffAndRetry(taskId: String, startId: Int, store: AiTransactionStore, cooldownKey: String, error: Throwable) {
        workExecutor.corePoolSize = 1
        val retrySeconds = (error as? AiImageException)?.retryAfterSeconds?.coerceAtLeast(1L) ?: RETRY_DELAY_SECONDS
        val waitMillis = retrySeconds * 1_000L
        rateLimitUntil.computeIfAbsent(cooldownKey) { AtomicLong(0L) }.set(System.currentTimeMillis() + waitMillis)
        val message = if (retrySeconds == RETRY_DELAY_SECONDS) {
            "请求过多，15 秒后自动重试"
        } else {
            "服务额度冷却中，${formatRetryDelay(retrySeconds)}后自动重试"
        }
        scheduleRetry(taskId, startId, store, waitMillis, message)
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

    private fun isRateLimited(error: Throwable): Boolean =
        (error as? AiImageException)?.statusCode == 429 ||
            error.message?.contains("429") == true ||
            error.message?.contains("请求过多") == true ||
            error.message?.contains("rate limit", ignoreCase = true) == true

    private fun isConnectionAborted(error: Throwable): Boolean {
        val cause = error.cause ?: error
        if (cause is SocketException || cause is EOFException || cause is ProtocolException) return true
        val msg = "${error.message.orEmpty()} ${cause.message.orEmpty()}"
        return msg.contains("Software caused connection abort", ignoreCase = true) ||
            msg.contains("Connection reset", ignoreCase = true) ||
            msg.contains("Broken pipe", ignoreCase = true) ||
            msg.contains("unexpected end of stream", ignoreCase = true)
    }

    private fun formatRetryDelay(seconds: Long): String {
        val safe = seconds.coerceAtLeast(1L)
        val hours = safe / 3_600L
        val minutes = (safe % 3_600L) / 60L
        val secs = safe % 60L
        return buildString {
            if (hours > 0) append(hours).append("小时")
            if (minutes > 0) append(minutes).append("分钟")
            if (secs > 0 || (hours == 0L && minutes == 0L)) append(secs).append("秒")
        }
    }

    private fun finishWork(startId: Int) {
        activeWork.decrementAndGet()
        stopIfIdle(startId)
    }

    private fun stopIfIdle(startId: Int) {
        if (activeWork.get() <= 0 && pendingRetries.get() <= 0 && workExecutor.queue.isEmpty()) {
            releaseLocks()
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
    override fun onDestroy() {
        releaseLocks()
        workExecutor.shutdownNow()
        retryScheduler.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "miku_ai_generation"; private const val NOTIFICATION_ID = 301
        private const val EXTRA_TASK_ID = "task_id"; private const val ACTION_CANCEL = "cancel"
        private const val MAX_PARALLEL = 3; private const val RETRY_DELAY_SECONDS = 15L
        private const val MAX_ABORT_RETRIES = 2; private const val ABORT_RETRY_DELAY_MILLIS = 2_000L
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
