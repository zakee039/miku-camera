package com.example.mikucamera.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.mikucamera.MainActivity
import com.example.mikucamera.R
import com.example.mikucamera.ai.AiGenerationService
import com.example.mikucamera.ai.AiTransaction
import com.example.mikucamera.ai.AiTransactionState
import com.example.mikucamera.ai.AiTransactionStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small always-on-top task indicator. Its expanded list opens a selected task in MainActivity. */
class AiOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var expanded = false

    override fun onCreate() { super.onCreate(); windowManager = getSystemService(WINDOW_SERVICE) as WindowManager }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || AiTransactionStore(this).all().isEmpty()) {
            removeOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, notification())
            expanded = intent?.getBooleanExtra(EXTRA_EXPANDED, false) ?: expanded
            redraw()
        } catch (_: Throwable) {
            // OEM window managers can reject an overlay immediately after permission changes.
            // Never let a window failure terminate the camera process.
            removeOverlay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_STICKY
    }

    private fun redraw() {
        removeOverlay()
        val tasks = AiTransactionStore(this).all()
        if (tasks.isEmpty()) { overlay = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return }
        val content = if (expanded) expandedView(tasks) else collapsedView(tasks)
        overlay = content
        windowManager.addView(content, WindowManager.LayoutParams(
            (if (expanded) dp(292) else WindowManager.LayoutParams.WRAP_CONTENT),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x = dp(12); y = dp(84) })
    }

    private fun removeOverlay() {
        overlay?.let { view -> runCatching { windowManager.removeViewImmediate(view) } }
        overlay = null
    }

    private fun collapsedView(tasks: List<AiTransaction>): View = LinearLayout(this).apply {
        val running = tasks.count { it.state == AiTransactionState.RUNNING }
        val success = tasks.count { it.state == AiTransactionState.SUCCESS }
        val failed = tasks.count { it.state == AiTransactionState.FAILED }
        gravity = Gravity.CENTER; orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(10), dp(12), dp(10))
        addView(counter(running, Color.BLACK)); addView(separator()); addView(counter(success, Color.rgb(30, 140, 75))); addView(separator()); addView(counter(failed, Color.rgb(205, 55, 55)))
        background = rounded(Color.WHITE); elevation = dp(8).toFloat(); setOnClickListener { expanded = true; redraw() }
    }

    private fun expandedView(tasks: List<AiTransaction>): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(Color.WHITE); elevation = dp(8).toFloat()
        addView(LinearLayout(this@AiOverlayService).apply {
            gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(8))
            addView(TextView(this@AiOverlayService).apply { text = ">"; textSize = 16f; setTextColor(Color.DKGRAY); setPadding(0, 0, dp(24), 0) })
            addView(counter(tasks.count { it.state == AiTransactionState.RUNNING }, Color.BLACK)); addView(separator())
            addView(counter(tasks.count { it.state == AiTransactionState.SUCCESS }, Color.rgb(30, 140, 75))); addView(separator())
            addView(counter(tasks.count { it.state == AiTransactionState.FAILED }, Color.rgb(205, 55, 55)))
            setOnClickListener { expanded = false; redraw() }
        })
        tasks.forEach { task -> addView(taskRow(task)) }
    }

    private fun taskRow(task: AiTransaction): View = LinearLayout(this).apply {
        val index = AiTransactionStore(this@AiOverlayService).all().indexOfFirst { it.id == task.id } + 1
        gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(9), 0, dp(9))
        addView(TextView(this@AiOverlayService).apply {
            text = "$index，${SimpleDateFormat("HH:mm:ss", Locale.CHINA).format(Date(task.createdAt))}，${task.message}"
            textSize = 13f; setTextColor(stateColor(task.state)); maxLines = 2
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openTask(task.id) }
        })
        if (task.state == AiTransactionState.FAILED) {
            addView(TextView(this@AiOverlayService).apply {
                text = "↻"; textSize = 18f; gravity = Gravity.CENTER; contentDescription = "重试"
                setTextColor(Color.WHITE); background = rounded(Color.rgb(205, 55, 55)); setPadding(dp(7), dp(2), dp(7), dp(2))
                setOnClickListener { AiGenerationService.retry(this@AiOverlayService, task.id) }
            })
        }
    }

    private fun openTask(id: String) {
        val intent = Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_AI_TRANSACTION_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }

    private fun counter(count: Int, color: Int) = TextView(this).apply { text = count.toString(); textSize = 15f; setTextColor(color) }
    private fun separator() = TextView(this).apply { text = " | "; textSize = 15f; setTextColor(Color.GRAY) }
    private fun stateColor(state: AiTransactionState) = when (state) { AiTransactionState.RUNNING -> Color.BLACK; AiTransactionState.SUCCESS -> Color.rgb(30, 140, 75); AiTransactionState.FAILED -> Color.rgb(205, 55, 55) }
    private fun rounded(color: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(12).toFloat() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun notification(): android.app.Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Miku AI 事务", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shutter_dot)
            .setContentTitle("miku camera 事务")
            .setContentText("点击悬浮窗查看 AI 事务")
            .setOngoing(true)
            .build()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { removeOverlay(); super.onDestroy() }

    companion object {
        private const val CHANNEL_ID = "miku_ai_overlay"; private const val NOTIFICATION_ID = 302; private const val EXTRA_EXPANDED = "expanded"
        fun refresh(context: Context) {
            if (Settings.canDrawOverlays(context) && AiTransactionStore(context).all().isNotEmpty()) {
                ContextCompat.startForegroundService(context, Intent(context, AiOverlayService::class.java))
            } else {
                context.stopService(Intent(context, AiOverlayService::class.java))
            }
        }
    }
}
