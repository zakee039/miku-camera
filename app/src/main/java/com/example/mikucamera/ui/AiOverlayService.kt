package com.example.mikucamera.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
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
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Small always-on-top task indicator. Its expanded list opens a selected task in MainActivity. */
class AiOverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var overlay: View? = null
    private var expanded = false
    private var collapsedX = POSITION_UNSET
    private var collapsedY = POSITION_UNSET
    private var collapsedWidth = 0
    private var collapsedHeight = 0
    private var expandsRight = false

    private val positionPrefs by lazy {
        getSharedPreferences(POSITION_PREFS, Context.MODE_PRIVATE)
    }

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
            if (intent?.hasExtra(EXTRA_EXPANDED) == true) {
                expanded = intent.getBooleanExtra(EXTRA_EXPANDED, false)
            }
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

        val (screenWidth, screenHeight) = screenSize()
        val collapsedMeasure = collapsedView(tasks)
        measureOverlay(collapsedMeasure, screenWidth, screenHeight, exactWidth = null)
        collapsedWidth = collapsedMeasure.measuredWidth
        collapsedHeight = collapsedMeasure.measuredHeight
        loadAndClampCollapsedPosition(screenWidth, screenHeight)

        val content = if (expanded) {
            expandedView(tasks, (screenHeight - dp(120)).coerceAtLeast(dp(96)))
        } else {
            collapsedMeasure
        }
        val exactWidth = if (expanded) dp(EXPANDED_WIDTH_DP) else null
        measureOverlay(content, screenWidth, screenHeight, exactWidth)
        val contentWidth = exactWidth ?: content.measuredWidth
        val contentHeight = content.measuredHeight

        expandsRight = collapsedX + collapsedWidth / 2 < screenWidth / 2
        val desiredX = when {
            !expanded -> collapsedX
            expandsRight -> collapsedX
            else -> collapsedX + collapsedWidth - contentWidth
        }
        val desiredY = if (expanded && collapsedY + contentHeight > screenHeight) {
            screenHeight - contentHeight - dp(EDGE_MARGIN_DP)
        } else {
            collapsedY
        }
        val params = WindowManager.LayoutParams(
            exactWidth ?: WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = desiredX.coerceIn(0, (screenWidth - contentWidth).coerceAtLeast(0))
            y = desiredY.coerceIn(0, (screenHeight - contentHeight).coerceAtLeast(0))
        }

        if (expanded) {
            val header = content.tag as View
            installDragHandler(header, content) { expanded = false; redraw() }
        } else {
            installDragHandler(content, content) { expanded = true; redraw() }
        }
        overlay = content
        windowManager.addView(content, params)
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
        background = rounded(Color.WHITE); elevation = dp(8).toFloat()
    }

    private fun expandedView(tasks: List<AiTransaction>, maxListHeight: Int): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)); background = rounded(Color.WHITE); elevation = dp(8).toFloat()
        val header = LinearLayout(this@AiOverlayService).apply {
            gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL; setPadding(0, 0, 0, dp(8))
            addView(TextView(this@AiOverlayService).apply { text = ">"; textSize = 16f; setTextColor(Color.DKGRAY); setPadding(0, 0, dp(24), 0) })
            addView(counter(tasks.count { it.state == AiTransactionState.RUNNING }, Color.BLACK)); addView(separator())
            addView(counter(tasks.count { it.state == AiTransactionState.SUCCESS }, Color.rgb(30, 140, 75))); addView(separator())
            addView(counter(tasks.count { it.state == AiTransactionState.FAILED }, Color.rgb(205, 55, 55)))
        }
        addView(header)
        val rows = LinearLayout(this@AiOverlayService).apply {
            orientation = LinearLayout.VERTICAL
            tasks.forEach { task -> addView(taskRow(task)) }
        }
        addView(MaxHeightScrollView(this@AiOverlayService, maxListHeight).apply {
            isVerticalScrollBarEnabled = tasks.size > 3
            addView(rows, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        tag = header
    }

    private fun installDragHandler(handle: View, content: View, onTap: () -> Unit) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        handle.setOnClickListener { onTap() }
        handle.setOnTouchListener { _, event ->
            val params = content.layoutParams as? WindowManager.LayoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!moved && hypot(dx.toDouble(), dy.toDouble()) > touchSlop.toDouble()) moved = true
                    if (moved) {
                        val (screenWidth, screenHeight) = screenSize()
                        val viewWidth = content.width.takeIf { it > 0 } ?: content.measuredWidth
                        val viewHeight = content.height.takeIf { it > 0 } ?: content.measuredHeight
                        params.x = (startX + dx).roundToInt()
                            .coerceIn(0, (screenWidth - viewWidth).coerceAtLeast(0))
                        params.y = (startY + dy).roundToInt()
                            .coerceIn(0, (screenHeight - viewHeight).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(content, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        rememberDraggedPosition(params, content)
                    } else {
                        handle.performClick()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) rememberDraggedPosition(params, content)
                    true
                }
                else -> true
            }
        }
    }

    private fun rememberDraggedPosition(params: WindowManager.LayoutParams, content: View) {
        val contentWidth = content.width.takeIf { it > 0 } ?: content.measuredWidth
        collapsedX = if (expanded && !expandsRight) {
            params.x + contentWidth - collapsedWidth
        } else {
            params.x
        }
        collapsedY = params.y
        val (screenWidth, screenHeight) = screenSize()
        loadAndClampCollapsedPosition(screenWidth, screenHeight)
        positionPrefs.edit()
            .putInt(KEY_POSITION_X, collapsedX)
            .putInt(KEY_POSITION_Y, collapsedY)
            .apply()
    }

    private fun loadAndClampCollapsedPosition(screenWidth: Int, screenHeight: Int) {
        if (collapsedX == POSITION_UNSET) {
            collapsedX = positionPrefs.getInt(
                KEY_POSITION_X,
                screenWidth - collapsedWidth - dp(EDGE_MARGIN_DP)
            )
        }
        if (collapsedY == POSITION_UNSET) {
            collapsedY = positionPrefs.getInt(KEY_POSITION_Y, dp(DEFAULT_Y_DP))
        }
        collapsedX = collapsedX.coerceIn(0, (screenWidth - collapsedWidth).coerceAtLeast(0))
        collapsedY = collapsedY.coerceIn(0, (screenHeight - collapsedHeight).coerceAtLeast(0))
    }

    private fun measureOverlay(view: View, screenWidth: Int, screenHeight: Int, exactWidth: Int?) {
        val widthSpec = if (exactWidth != null) {
            View.MeasureSpec.makeMeasureSpec(exactWidth, View.MeasureSpec.EXACTLY)
        } else {
            View.MeasureSpec.makeMeasureSpec(screenWidth, View.MeasureSpec.AT_MOST)
        }
        view.measure(
            widthSpec,
            View.MeasureSpec.makeMeasureSpec(screenHeight, View.MeasureSpec.AT_MOST)
        )
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val point = Point()
            windowManager.defaultDisplay.getRealSize(point)
            point.x to point.y
        }
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
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (overlay != null) redraw()
    }

    private class MaxHeightScrollView(context: Context, private val maxHeight: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                widthMeasureSpec,
                View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "miku_ai_overlay"; private const val NOTIFICATION_ID = 302; private const val EXTRA_EXPANDED = "expanded"
        private const val POSITION_PREFS = "ai_overlay_position"
        private const val KEY_POSITION_X = "x"
        private const val KEY_POSITION_Y = "y"
        private const val POSITION_UNSET = Int.MIN_VALUE
        private const val EXPANDED_WIDTH_DP = 292
        private const val EDGE_MARGIN_DP = 12
        private const val DEFAULT_Y_DP = 84
        fun refresh(context: Context) {
            if (Settings.canDrawOverlays(context) && AiTransactionStore(context).all().isNotEmpty()) {
                ContextCompat.startForegroundService(context, Intent(context, AiOverlayService::class.java))
            } else {
                context.stopService(Intent(context, AiOverlayService::class.java))
            }
        }
    }
}
