package com.example.mikucamera

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentUris
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputType
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.mikucamera.camera.PhotoComposer
import com.example.mikucamera.ai.AiImageClient
import com.example.mikucamera.ai.AiImageException
import com.example.mikucamera.ai.AiGenerationService
import com.example.mikucamera.ai.AiGenerationConfiguration
import com.example.mikucamera.ai.AiSessionStore
import com.example.mikucamera.ai.AiTransaction
import com.example.mikucamera.ai.AiTransactionState
import com.example.mikucamera.ai.AiTransactionStore
import com.example.mikucamera.ai.AiApiProfile
import com.example.mikucamera.ai.AiPromptBuilder
import com.example.mikucamera.ai.AiOutfitStyle
import com.example.mikucamera.ai.AiServicePreset
import com.example.mikucamera.ai.AiSettingsStore
import com.example.mikucamera.ui.MikuUi
import com.example.mikucamera.ai.AiResolution
import com.example.mikucamera.ai.AiVisualStyle
import com.example.mikucamera.data.PresetStore
import com.example.mikucamera.location.LocationFormatter
import com.example.mikucamera.location.LocationProvider
import com.example.mikucamera.model.WatermarkPreset
import com.example.mikucamera.ui.FocusExposureView
import com.example.mikucamera.ui.AiOverlayService
import com.example.mikucamera.ui.WatermarkOverlayView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private enum class CameraMode { NORMAL, AI }
    private enum class AiStage { CAPTURE, PROMPT, GENERATING, RESULT }
    private enum class AiCaptureTarget { ORIGINAL, REFERENCE }

    private data class AiSession(
        var originalFile: File,
        var captureTime: String,
        var captureLocation: String,
        var resultFile: File? = null,
        var resultSaved: Boolean = false,
        var transactionId: String? = null,
        var referenceFile: File? = null
    )

    private lateinit var root: View
    private lateinit var contentStage: FrameLayout
    private lateinit var viewfinderHost: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var overlay: WatermarkOverlayView
    private lateinit var cameraControls: View
    private lateinit var bottomChrome: View
    private lateinit var captureControls: View
    private lateinit var editorControls: View
    private lateinit var flashButton: View
    private lateinit var flashIcon: ImageView
    private lateinit var flashBadge: TextView
    private lateinit var switchCameraButton: ImageButton
    private lateinit var captureButton: ImageButton
    private lateinit var normalLeftTools: View
    private lateinit var aiLeftTools: View
    private lateinit var selectWatermarkButton: ImageView
    private lateinit var aiModeButton: ImageView
    private lateinit var aboutButton: ImageView
    private lateinit var aiBackButton: ImageView
    private lateinit var aiSettingsButton: ImageView
    private lateinit var aiCaptureProgressBar: View
    private lateinit var locationStatusView: TextView
    private lateinit var recentPhotoView: ImageView
    private lateinit var aiImportButton: MaterialButton
    private lateinit var timeSwitch: SwitchMaterial
    private lateinit var locationSwitch: SwitchMaterial
    private lateinit var streetSwitch: SwitchMaterial
    private lateinit var outlineSeekBar: android.widget.SeekBar
    private lateinit var watermarkNameEditText: EditText
    private lateinit var focusExposure: FocusExposureView
    private lateinit var aiPageHost: View
    private lateinit var aiPageBackButton: ImageView
    private lateinit var aiPageSettingsButton: ImageView
    private lateinit var aiPromptPanel: View
    private lateinit var aiGeneratingPanel: View
    private lateinit var aiResultPanel: View
    private lateinit var aiResultSaveActions: View
    private lateinit var aiResultActions: View
    private lateinit var aiResultPostSaveActions: View
    private lateinit var aiOriginalPreview: ImageView
    private lateinit var aiReferencePreview: ImageView
    private lateinit var aiReferenceEmptyHint: TextView
    private lateinit var aiReferenceClearButton: TextView
    private lateinit var aiShootOriginalButton: MaterialButton
    private lateinit var aiImportOriginalButton: MaterialButton
    private lateinit var aiShootReferenceButton: MaterialButton
    private lateinit var aiImportReferenceButton: MaterialButton
    private lateinit var aiResultPreview: ImageView
    private lateinit var aiPromptEditText: EditText
    private lateinit var aiVisualStyleText: TextView
    private lateinit var aiOutfitStyleText: TextView
    private lateinit var aiVisualStyleToggleButton: MaterialButton
    private lateinit var aiOutfitStyleToggleButton: MaterialButton
    private lateinit var aiTimeEditText: TextView
    private lateinit var aiLocationEditText: TextView
    private lateinit var aiTimeWatermarkSwitch: SwitchMaterial
    private lateinit var aiLocationWatermarkSwitch: SwitchMaterial
    private lateinit var aiGenerationStatusText: TextView
    private lateinit var aiCaptureStep: TextView
    private lateinit var aiPromptStep: TextView
    private lateinit var aiGenerateStep: TextView
    private lateinit var aiPageCaptureStep: TextView
    private lateinit var aiPagePromptStep: TextView
    private lateinit var aiPageGenerateStep: TextView
    private lateinit var aiPagePromptConnector: View
    private val store by lazy { PresetStore(this) }
    private val locationProvider by lazy { LocationProvider(this) }
    private val aiSettingsStore by lazy { AiSettingsStore(this) }
    private val aiSessionStore by lazy { AiSessionStore(this) }
    private val aiTransactionStore by lazy { AiTransactionStore(this) }
    private val aiImageClient = AiImageClient()
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val aiExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var physicalRotationDegrees = 0
    private var currentLocation = ""
    /** Last reverse-geocode result; reformatted when 门牌 toggles without re-fetch. */
    private var lastAddress: Address? = null
    private var lastLatLonFallback: String? = null
    private var editing = false
    private var editingExistingPreset = false
    private var editingOriginalName = ""
    private var presetBeforeEdit: WatermarkPreset? = null
    private var recentPhotoUri: Uri? = null
    private var cameraMode = CameraMode.NORMAL
    private var aiStage = AiStage.CAPTURE
    private var aiCaptureTarget = AiCaptureTarget.ORIGINAL
    private var returningToPromptOnCancelCapture = false
    private var onPresetReferenceUpdated: (() -> Unit)? = null
    private var aiSession: AiSession? = null
    @Volatile private var aiGenerationId = 0L
    private var lastAiDebugLog = ""
    private var overlayPromptShown = false
    private var locationPermissionRequested = false
    private var cameraPermissionRequested = false
    private var photoPermissionRequested = false
    private val transactionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshVisibleTransaction() }
    }
    private val density by lazy { resources.displayMetrics.density }
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastExposureLocalY = 0f
    private var exposureGestureActive = false
    private var exposureIndex = 0
    private var exposureMin = 0
    private var exposureMax = 0
    private var boundViewfinderWidth = 0
    private var boundViewfinderHeight = 0
    private var statusBarInsetTop = 0
    private var navBarInsetBottom = 0
    private var lockedBottomHeight = 0
    private var stoppedToBackground = false
    private var launchingInternalIntent = false

    private fun topSafeMarginPx(): Int = maxOf(statusBarInsetTop + (6 * density).roundToInt(), (42 * density).roundToInt())
    private fun bottomSafeMarginPx(): Int = maxOf(navBarInsetBottom, 50)

    private val orientationListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val snapped = when {
                    orientation < 45 || orientation >= 315 -> 0
                    orientation < 135 -> 90
                    orientation < 225 -> 180
                    else -> 270
                }
                if (snapped != physicalRotationDegrees) applyPhysicalOrientation(snapped)
            }
        }
    }

    private val startupPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (!hasCameraPermission() && grants[Manifest.permission.CAMERA] == false) {
            toast("需要相机权限才能拍照")
        }
        if (hasLocationPermission()) {
            requestLocation(silent = true)
        } else if (
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == false &&
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == false
        ) {
            toast("未授予定位权限，地点水印可能无法显示地址")
        }
        continuePermissionSequence()
    }
    private val pngPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        cameraExecutor.execute {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            runOnUiThread {
                if (bitmap == null) {
                    toast("PNG 读取失败")
                } else {
                    overlay.setUploadedImage(uri.toString(), bitmap)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        hideStatusBar()
        setContentView(R.layout.activity_main)
        bindViews()
        applyMikuDesign()
        bindActions()
        bindBackNavigation()
        enterCameraMode()
        installBundledMikuPreset()
        restoreLastSelectedPreset()
        loadLatestPhoto()
        restoreAiSessionIfNeeded()
        if (aiSession == null && aiSettingsStore.load().startInAiMode) {
            aiSettingsStore.acceptUploadNotice()
            enterAiCaptureStage()
        }
        continuePermissionSequence()
        AiOverlayService.refresh(this)
        intent.getStringExtra(EXTRA_AI_TRANSACTION_ID)?.let(::openAiTransaction)
    }

    /** Immersive camera UI: status bar hidden so the viewfinder is not framed by system chrome. */
    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = MikuUi.background
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= 27) MikuUi.background else MikuUi.ink
        if (Build.VERSION.SDK_INT >= 29) window.decorView.isForceDarkAllowed = false
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightNavigationBars = true
        controller.isAppearanceLightStatusBars = true
        controller.hide(WindowInsetsCompat.Type.statusBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        contentStage = findViewById(R.id.contentStage)
        viewfinderHost = findViewById(R.id.viewfinderHost)
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.watermarkOverlay)
        cameraControls = findViewById(R.id.cameraControls)
        bottomChrome = findViewById(R.id.bottomChrome)
        captureControls = findViewById(R.id.captureControls)
        editorControls = findViewById(R.id.editorControls)
        flashButton = findViewById(R.id.flashButton)
        flashIcon = findViewById(R.id.flashIcon)
        flashBadge = findViewById(R.id.flashBadge)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        captureButton = findViewById(R.id.captureButton)
        normalLeftTools = findViewById(R.id.normalLeftTools)
        aiLeftTools = findViewById(R.id.aiLeftTools)
        selectWatermarkButton = findViewById(R.id.selectWatermarkButton)
        aiModeButton = findViewById(R.id.aiModeButton)
        aboutButton = findViewById(R.id.aboutButton)
        aiBackButton = findViewById(R.id.aiBackButton)
        aiSettingsButton = findViewById(R.id.aiSettingsButton)
        aiCaptureProgressBar = findViewById(R.id.aiCaptureProgressBar)
        locationStatusView = findViewById(R.id.locationStatusView)
        // Keep marquee running in the middle slot (never steals width from side buttons).
        locationStatusView.isSelected = true
        recentPhotoView = findViewById(R.id.recentPhotoView)
        aiImportButton = findViewById(R.id.aiImportButton)
        timeSwitch = findViewById(R.id.timeSwitch)
        locationSwitch = findViewById(R.id.locationSwitch)
        streetSwitch = findViewById(R.id.streetSwitch)
        outlineSeekBar = findViewById(R.id.outlineSeekBar)
        watermarkNameEditText = findViewById(R.id.watermarkNameEditText)
        aiPageHost = findViewById(R.id.aiPageHost)
        aiPageBackButton = findViewById(R.id.aiPageBackButton)
        aiPageSettingsButton = findViewById(R.id.aiPageSettingsButton)
        aiPromptPanel = findViewById(R.id.aiPromptPanel)
        aiGeneratingPanel = findViewById(R.id.aiGeneratingPanel)
        aiResultPanel = findViewById(R.id.aiResultPanel)
        aiResultSaveActions = findViewById(R.id.aiResultSaveActions)
        aiResultActions = findViewById(R.id.aiResultActions)
        aiResultPostSaveActions = findViewById(R.id.aiResultPostSaveActions)
        aiOriginalPreview = findViewById(R.id.aiOriginalPreview)
        aiReferencePreview = findViewById(R.id.aiReferencePreview)
        aiReferenceEmptyHint = findViewById(R.id.aiReferenceEmptyHint)
        aiReferenceClearButton = findViewById(R.id.aiReferenceClearButton)
        aiShootOriginalButton = findViewById(R.id.aiShootOriginalButton)
        aiImportOriginalButton = findViewById(R.id.aiImportOriginalButton)
        aiShootReferenceButton = findViewById(R.id.aiShootReferenceButton)
        aiImportReferenceButton = findViewById(R.id.aiImportReferenceButton)
        aiResultPreview = findViewById(R.id.aiResultPreview)
        aiPromptEditText = findViewById(R.id.aiPromptEditText)
        aiVisualStyleText = findViewById(R.id.aiVisualStyleText)
        aiOutfitStyleText = findViewById(R.id.aiOutfitStyleText)
        aiVisualStyleToggleButton = findViewById(R.id.aiVisualStyleToggleButton)
        aiOutfitStyleToggleButton = findViewById(R.id.aiOutfitStyleToggleButton)
        aiTimeEditText = findViewById(R.id.aiTimeEditText)
        aiLocationEditText = findViewById(R.id.aiLocationEditText)
        aiTimeWatermarkSwitch = findViewById(R.id.aiTimeWatermarkSwitch)
        aiLocationWatermarkSwitch = findViewById(R.id.aiLocationWatermarkSwitch)
        aiGenerationStatusText = findViewById(R.id.aiGenerationStatusText)
        aiCaptureStep = findViewById(R.id.aiCaptureStep)
        aiPromptStep = findViewById(R.id.aiPromptStep)
        aiGenerateStep = findViewById(R.id.aiGenerateStep)
        aiPageCaptureStep = findViewById(R.id.aiPageCaptureStep)
        aiPagePromptStep = findViewById(R.id.aiPagePromptStep)
        aiPageGenerateStep = findViewById(R.id.aiPageGenerateStep)
        aiPagePromptConnector = findViewById(R.id.aiPagePromptConnector)
        createFocusExposureView()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            statusBarInsetTop = bars.top
            navBarInsetBottom = bars.bottom
            val side = (16 * density).roundToInt()
            // Floating tools on preview; shutter padding stays inside the middle-lower stage
            // (bottom safe margin is ~50px above the screen edge).
            cameraControls.setPadding(side, 0, side, (8 * density).roundToInt())
            val pad = (6 * density).roundToInt()
            captureControls.setPadding(side / 2, pad, side / 2, pad)
            editorControls.setPadding(side, pad, side, pad)
            aiPageHost.setPadding(0, topSafeMarginPx(), 0, bottomSafeMarginPx())
            root.post { layoutViewfinder() }
            insets
        }
        aiPageHost.setPadding(0, topSafeMarginPx(), 0, bottomSafeMarginPx())
        ViewCompat.requestApplyInsets(root)

        viewfinderHost.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val w = right - left
            val h = bottom - top
            if (w > 0 && h > 0 && (w != boundViewfinderWidth || h != boundViewfinderHeight)) {
                if (cameraProvider != null) bindUseCases()
            }
        }

        overlay.onChanged = { persistCurrentPresetIfSaved() }
        root.post {
            layoutViewfinder()
            applyPhysicalOrientation(physicalRotationDegrees)
        }
    }

    /**
     * Device-independent layout:
     *
     * ```
     * [ top natural extension ]       ← background extends to top (away from hole-punch)
     * [ middle-lower content stage ]  ← full width, gathered toward lower area
     *     [ topBar: 4 icons ]         ← outside FOV, immediately above viewfinder
     *     [ 3:4 viewfinder ]          ← capture FOV only (所见即所得)
     *     [ bottom chrome ]           ← shutter / editor, immediately below viewfinder
     * [ ~50px bottom safe margin ]    ← rounded corner clearance
     * ```
     */
    private fun layoutViewfinder() {
        val rootW = root.width
        val rootH = root.height
        if (rootW <= 0 || rootH <= 0) return

        val stageW = rootW
        val widthSpec = View.MeasureSpec.makeMeasureSpec(stageW, View.MeasureSpec.EXACTLY)
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)

        // --- Top tool bar (outside FOV) ---
        cameraControls.measure(widthSpec, unspec)
        // Keep a stable top bar even when hidden in editor (INVISIBLE keeps measure).
        val topH = cameraControls.measuredHeight.coerceAtLeast((52 * density).roundToInt())

        // --- Bottom chrome (fixed for camera/editor so FOV never jumps) ---
        captureControls.measure(widthSpec, unspec)
        editorControls.measure(widthSpec, unspec)
        val bottomH = maxOf(
            captureControls.measuredHeight,
            editorControls.measuredHeight.coerceAtMost((200 * density).roundToInt()),
            (88 * density).roundToInt()
        )
        lockedBottomHeight = bottomH

        // AI mode inherits the exact same viewfinder size and CameraX framing
        // as normal mode. Its workflow indicator floats over the preview and
        // never participates in the FOV calculation.
        val vfW = stageW
        val idealVfH = (vfW * 4f / 3f).roundToInt()

        val bottomMargin = bottomSafeMarginPx()
        val minTopMargin = (48 * density).roundToInt()
        val availableH = (rootH - bottomMargin - minTopMargin).coerceAtLeast(1)

        val idealStageH = topH + idealVfH + bottomH
        val stageH = idealStageH.coerceAtMost(availableH)
        val stageTop = (rootH - bottomMargin - stageH).coerceAtLeast(minTopMargin)

        contentStage.layoutParams = FrameLayout.LayoutParams(stageW, stageH).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = stageTop
        }

        val maxVfH = (stageH - topH - bottomH).coerceAtLeast(1)
        val vfH = idealVfH.coerceAtMost(maxVfH)
        val actualBottomH = (stageH - topH - vfH).coerceAtLeast(bottomH)

        cameraControls.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            topH
        ).apply {
            gravity = Gravity.TOP
        }

        viewfinderHost.layoutParams = FrameLayout.LayoutParams(vfW, vfH).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = topH
        }

        aiCaptureProgressBar.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            (40 * density).roundToInt()
        ).apply {
            gravity = Gravity.TOP
            topMargin = topH
        }
        aiCaptureProgressBar.bringToFront()

        bottomChrome.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            actualBottomH
        ).apply {
            gravity = Gravity.BOTTOM
        }
    }

    private fun createFocusExposureView() {
        focusExposure = FocusExposureView(this).apply {
            elevation = 8f * density
            onExposureChanged = { fraction -> applyExposureFraction(fraction) }
        }
        // Lives inside the viewfinder so coordinates match the preview FOV.
        viewfinderHost.addView(
            focusExposure,
            FrameLayout.LayoutParams(
                (140 * density).roundToInt(),
                (140 * density).roundToInt()
            )
        )
    }

    override fun onResume() {
        super.onResume()
        hideStatusBar()
        orientationListener.enable()
        if (overlayPromptShown) continuePermissionSequence()
        if (hasLocationPermission()) startLocationWarmup()
        refreshVisibleTransaction()
    }

    override fun onPause() {
        orientationListener.disable()
        super.onPause()
    }

    private fun dp(value: Int) = MikuUi.dp(this, value)

    private fun applyMikuDesign() {
        viewfinderHost.setBackgroundColor(Color.BLACK)
        viewfinderHost.clipToOutline = false
        MikuUi.styleTree(editorControls)
        MikuUi.styleTree(aiPageHost)
        MikuUi.button(findViewById(R.id.uploadButton), false)
        MikuUi.button(findViewById(R.id.cancelEditButton), false)
        MikuUi.button(findViewById(R.id.saveWatermarkButton), true)
        MikuUi.dangerButton(findViewById(R.id.deleteWatermarkButton))
        MikuUi.dangerButton(findViewById(R.id.aiCancelGenerationButton))
        MikuUi.button(findViewById(R.id.aiGenerateAllButton), false)
        MikuUi.button(findViewById(R.id.aiGenerateButton), true)
        MikuUi.button(aiShootOriginalButton, false)
        MikuUi.button(aiImportOriginalButton, false)
        MikuUi.button(aiShootReferenceButton, false)
        MikuUi.button(aiImportReferenceButton, false)
        MikuUi.button(findViewById(R.id.aiSaveBothButton), true)
        MikuUi.editText(watermarkNameEditText, 12)
        watermarkNameEditText.setPadding(dp(12), 0, dp(12), 0)
        MikuUi.editText(aiPromptEditText, 14)
        MikuUi.switch(timeSwitch)
        MikuUi.switch(locationSwitch)
        MikuUi.switch(streetSwitch)
        MikuUi.switch(aiTimeWatermarkSwitch)
        MikuUi.switch(aiLocationWatermarkSwitch)
        MikuUi.seekBar(outlineSeekBar)
        listOf(aiOriginalPreview, aiReferencePreview, aiResultPreview).forEach { it.clipToOutline = true }
        MikuUi.toolbarIcon(aiPageBackButton, false)
        MikuUi.toolbarIcon(aiPageSettingsButton, false)
        MikuUi.toolbarIcon(aiBackButton, false)
        MikuUi.toolbarIcon(aiSettingsButton, false)
        updateCameraModeButtons()
    }

    private fun updateCameraModeButtons() {
        MikuUi.toolbarIcon(selectWatermarkButton, cameraMode == CameraMode.NORMAL)
        MikuUi.toolbarIcon(aiModeButton, cameraMode == CameraMode.AI)
        MikuUi.toolbarIcon(aboutButton, false)
    }

    private fun startAllCombinations() {
        val session = aiSession ?: return
        val config = currentGenerationConfiguration()
        val settings = aiSettingsStore.load()
        if (settings.profiles.firstOrNull { it.id == config.profileId }?.apiKey.isNullOrBlank()) {
            toast("请先设置 API Key"); showAiApiProfilesDialog(); return
        }
        val prompt = aiPromptEditText.text.toString().trim()
        val time = session.captureTime
        val location = session.captureLocation
        val includeTime = aiTimeWatermarkSwitch.isChecked
        val includeLocation = aiLocationWatermarkSwitch.isChecked
        val source = session.originalFile
        val refSource = session.referenceFile
        val allButton = findViewById<MaterialButton>(R.id.aiGenerateAllButton)
        allButton.isEnabled = false
        aiExecutor.execute {
            val created = mutableListOf<AiTransaction>()
            try {
                config.allCombinations().forEach { combination ->
                        created += createAiTransactionSnapshot(source, time, location, prompt, includeTime, includeLocation,
                            combination, referenceSource = refSource)
                }
                runOnUiThread {
                    allButton.isEnabled = true
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    created.forEach { task ->
                        aiTransactionStore.update(task.id) { it.copy(state = AiTransactionState.RUNNING, attemptCount = 1, message = "已加入生成队列") }
                        AiGenerationService.start(this, task.id)
                    }
                    session.transactionId?.let { id ->
                        if (aiTransactionStore.get(id)?.attemptCount == 0) aiTransactionStore.remove(id)
                    }
                    aiSessionStore.clear(deleteFiles = false)
                    aiSession = null
                    enterAiCaptureStage()
                    AiOverlayService.refresh(this)
                    toast("已创建 4 个搭配任务")
                    showTaskList()
                }
            } catch (error: Exception) {
                created.forEach { aiTransactionStore.remove(it.id) }
                runOnUiThread { allButton.isEnabled = true; toast("创建失败：${error.message}") }
            }
        }
    }

    private fun showTaskList() {
        val dialog = BottomSheetDialog(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), bottomSafeMarginPx())
            background = MikuUi.rounded(this@MainActivity, MikuUi.background, 24)
        }
        content.addView(TextView(this).apply { text = "生成任务"; textSize = 21f; setTextColor(MikuUi.ink) })
        val tasks = aiTransactionStore.all()
        if (tasks.isEmpty()) content.addView(TextView(this).apply {
            text = "还没有任务，拍一张照片开始吧"; setPadding(0, dp(16), 0, dp(16)); setTextColor(MikuUi.muted)
        })
        tasks.forEach { task ->
            val config = task.configuration
            val state = when(task.state) {
                AiTransactionState.DRAFT -> "待生成"
                AiTransactionState.RUNNING -> "生成中"
                AiTransactionState.SUCCESS -> "已完成"
                AiTransactionState.FAILED -> "未完成"
            }
            content.addView(MaterialButton(this).apply {
                text = "${config?.visualStyle?.displayName.orEmpty()} · ${config?.outfitStyle?.displayName.orEmpty()} · ${config?.resolution?.label ?: "2K"}  /  $state"
                MikuUi.button(this); maxLines = 2
                setOnClickListener { dialog.dismiss(); openAiTransaction(task.id) }
            }, LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(8) })
        }
        content.addView(MaterialButton(this).apply {
            text = "刷新状态"; MikuUi.button(this, true)
            setOnClickListener { dialog.dismiss(); showTaskList() }
        }, LinearLayout.LayoutParams(-1, dp(44)).apply { topMargin = dp(8) })
        dialog.setContentView(ScrollView(this).apply { addView(content) })
        dialog.show()
    }

    private fun bindActions() {
        previewView.setOnTouchListener { _, event -> handlePreviewTouch(event) }
        flashButton.setOnClickListener {
            if (camera?.cameraInfo?.hasFlashUnit() != true) {
                toast("当前镜头没有可用的闪光灯")
                return@setOnClickListener
            }
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                else -> ImageCapture.FLASH_MODE_OFF
            }
            updateFlashControl()
        }
        switchCameraButton.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else CameraSelector.LENS_FACING_BACK
            startCamera()
        }
        captureButton.setOnClickListener { capturePhoto() }
        aiImportButton.setOnClickListener {
            launchingInternalIntent = true
            aiPhotoPicker.launch(
                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        recentPhotoView.setOnClickListener { openRecentPhoto() }
        selectWatermarkButton.setOnClickListener {
            if (cameraMode == CameraMode.AI) {
                requestExitAiMode()
            } else {
                showWatermarkChooser()
            }
        }
        aiModeButton.setOnClickListener {
            if (cameraMode != CameraMode.AI) {
                requestEnterAiMode()
            }
        }
        aboutButton.setOnClickListener { showAiSettingsHome() }
        aiBackButton.setOnClickListener { requestExitAiMode() }
        aiSettingsButton.setOnClickListener { showAiSettingsHome() }
        aiPageBackButton.setOnClickListener { requestExitAiMode() }
        aiPageSettingsButton.setOnClickListener { showAiSettingsHome() }
        findViewById<MaterialButton>(R.id.aiRetakeButton).setOnClickListener { returnToAiCapture() }
        aiShootOriginalButton.setOnClickListener { startPromptOriginalCapture() }
        aiImportOriginalButton.setOnClickListener {
            launchingInternalIntent = true
            aiOriginalReplacePicker.launch(
                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        aiShootReferenceButton.setOnClickListener { startPromptReferenceCapture() }
        aiImportReferenceButton.setOnClickListener {
            launchingInternalIntent = true
            aiReferencePicker.launch(
                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        aiReferenceClearButton.setOnClickListener { clearCurrentReferenceImage() }
        findViewById<MaterialButton>(R.id.aiGenerateButton).setOnClickListener { startAiGeneration() }
        findViewById<MaterialButton>(R.id.aiCancelGenerationButton).setOnClickListener { cancelAiGeneration() }
        findViewById<MaterialButton>(R.id.aiEditPromptDuringGenerationButton).setOnClickListener { editPromptWhileGenerating() }
        findViewById<MaterialButton>(R.id.aiRetakeDuringGenerationButton).setOnClickListener { retakeWhileGenerating() }
        findViewById<MaterialButton>(R.id.aiEditPromptButton).setOnClickListener { showAiPromptStage() }
        findViewById<MaterialButton>(R.id.aiRegenerateButton).setOnClickListener { confirmRegenerate() }
        findViewById<MaterialButton>(R.id.aiSavedRetakeButton).setOnClickListener { returnToAiCapture() }
        findViewById<MaterialButton>(R.id.aiSavedEditPromptButton).setOnClickListener { showAiPromptStage() }
        findViewById<MaterialButton>(R.id.aiSaveResultButton).setOnClickListener { editPromptFromResult() }
        findViewById<MaterialButton>(R.id.aiSaveOriginalButton).setOnClickListener { returnToAiCapture() }
        findViewById<MaterialButton>(R.id.aiSaveBothButton).setOnClickListener { saveAiSelection(saveOriginal = false, saveResult = true) }
        aiResultPreview.setOnTouchListener { _, event ->
            val session = aiSession ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { aiResultPreview.setImageURI(Uri.fromFile(session.originalFile)); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    aiResultPreview.setImageURI(session.resultFile?.let(Uri::fromFile)); true
                }
                else -> true
            }
        }
        aiVisualStyleToggleButton.setOnClickListener { selectGenerationOptions(visual = AiVisualStyle.ANIME) }
        findViewById<MaterialButton>(R.id.aiRealisticButton).setOnClickListener { selectGenerationOptions(visual = AiVisualStyle.REALISTIC) }
        aiOutfitStyleToggleButton.setOnClickListener { selectGenerationOptions(outfit = AiOutfitStyle.OFFICIAL) }
        findViewById<MaterialButton>(R.id.aiAdaptiveButton).setOnClickListener { selectGenerationOptions(outfit = AiOutfitStyle.SCENE_ADAPTIVE) }
        listOf(R.id.aiResolution1, R.id.aiResolution2, R.id.aiResolution4).zip(AiResolution.entries).forEach { (id, resolution) ->
            findViewById<MaterialButton>(id).setOnClickListener { selectGenerationOptions(resolution = resolution) }
        }
        findViewById<MaterialButton>(R.id.aiGenerateAllButton).setOnClickListener { startAllCombinations() }
        aiTimeEditText.setOnClickListener { editAiMetadata(time = true) }
        aiLocationEditText.setOnClickListener { editAiMetadata(time = false) }
        findViewById<MaterialButton>(R.id.uploadButton).setOnClickListener {
            launchingInternalIntent = true
            pngPicker.launch(arrayOf("image/png"))
        }
        findViewById<MaterialButton>(R.id.cancelEditButton).setOnClickListener { cancelEditor() }
        findViewById<MaterialButton>(R.id.saveWatermarkButton).setOnClickListener { saveCurrentPreset() }
        findViewById<MaterialButton>(R.id.deleteWatermarkButton).setOnClickListener { deleteCurrentPreset() }
        outlineSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                overlay.setOutlinePx(value.toFloat())
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        timeSwitch.setOnCheckedChangeListener { _, checked -> overlay.setShowTime(checked) }
        // "地点" = whether to draw location on the watermark, NOT GPS on/off.
        locationSwitch.setOnCheckedChangeListener { _, checked ->
            overlay.setShowLocation(checked)
            updateStreetSwitchVisibility(checked)
            if (checked) applyCachedLocationLabel()
        }
        streetSwitch.setOnCheckedChangeListener { _, checked ->
            overlay.setIncludeStreet(checked)
            applyCachedLocationLabel()
        }
    }

    private fun bindBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (cameraMode == CameraMode.AI) {
                    if (aiStage == AiStage.CAPTURE && returningToPromptOnCancelCapture && aiSession != null) {
                        returningToPromptOnCancelCapture = false
                        aiCaptureTarget = AiCaptureTarget.ORIGINAL
                        showAiPromptStage(resetPrompt = false)
                    } else if (aiStage == AiStage.CAPTURE && aiSettingsStore.load().startInAiMode) {
                        finish()
                    } else {
                        requestExitAiMode()
                    }
                } else if (editing) {
                    cancelEditor()
                } else {
                    finish()
                }
            }
        })
    }

    private fun startPromptOriginalCapture() {
        aiCaptureTarget = AiCaptureTarget.ORIGINAL
        returningToPromptOnCancelCapture = aiSession != null
        enterAiCaptureStage()
        toast("请拍摄新的原图")
    }

    private fun startPromptReferenceCapture() {
        aiCaptureTarget = AiCaptureTarget.REFERENCE
        returningToPromptOnCancelCapture = true
        enterAiCaptureStage()
        toast("请拍摄形象参考图")
    }

    private fun updateAiReferencePreview() {
        val ref = aiSession?.referenceFile?.takeIf { it.isFile && it.length() > 0L }
        aiReferencePreview.setImageURI(null)
        if (ref != null) {
            aiReferencePreview.setImageURI(Uri.fromFile(ref))
            aiReferenceEmptyHint.visibility = View.GONE
            aiReferenceClearButton.visibility = View.VISIBLE
        } else {
            aiReferenceEmptyHint.visibility = View.VISIBLE
            aiReferenceClearButton.visibility = View.GONE
        }
    }

    private fun clearCurrentReferenceImage() {
        val session = aiSession ?: return
        session.referenceFile?.delete()
        session.referenceFile = null
        session.transactionId?.let { id ->
            val oldTask = aiTransactionStore.get(id)
            oldTask?.referenceFile?.delete()
            aiTransactionStore.update(id) { it.copy(referencePath = null) }
        }
        updateAiReferencePreview()
        persistAiSession(stage = "PROMPT", prompt = aiPromptEditText.text.toString())
    }

    private fun prepareDefaultReferenceCopy(): File? {
        val presetFile = aiSettingsStore.load().defaultReferenceFile ?: return null
        return runCatching {
            val copy = aiSessionStore.newFile("miku_ai_ref_", ".jpg")
            presetFile.copyTo(copy, overwrite = true)
            copy
        }.getOrNull()
    }

    private fun requestEnterAiMode() {
        if (aiSettingsStore.hasAcceptedUploadNotice()) {
            enterAiCaptureStage()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("使用 AI mode")
            .setMessage(
                "AI mode 会把干净照片、拍摄时间、地点文字和提示词发送到 AI 设置中配置的服务进行图像生成。" +
                    "普通水印和上传的 PNG 不会发送。生成可能产生 API 费用，是否继续？"
            )
            .setNegativeButton("取消", null)
            .setPositiveButton("同意并进入") { _, _ ->
                aiSettingsStore.acceptUploadNotice()
                enterAiCaptureStage()
            }
            .show()
    }

    private fun enterAiCaptureStage() {
        cameraMode = CameraMode.AI
        updateCameraModeButtons()
        aiStage = AiStage.CAPTURE
        editing = false
        aiPageHost.visibility = View.GONE
        cameraControls.visibility = View.VISIBLE
        viewfinderHost.visibility = View.VISIBLE
        bottomChrome.visibility = View.VISIBLE
        captureControls.visibility = View.VISIBLE
        editorControls.visibility = View.GONE
        normalLeftTools.visibility = View.VISIBLE
        aiLeftTools.visibility = View.GONE
        aiCaptureProgressBar.visibility = View.VISIBLE
        locationStatusView.visibility = View.VISIBLE
        flashButton.visibility = View.VISIBLE
        recentPhotoView.visibility = View.INVISIBLE
        aiImportButton.visibility = View.VISIBLE
        overlay.visibility = View.GONE
        overlay.setEditingEnabled(false)
        updateAiWorkflow(AiStage.CAPTURE)
        captureButton.isEnabled = true
        root.post {
            layoutViewfinder()
            if (cameraProvider == null) startCamera() else bindUseCases()
            applyControlRotation(physicalRotationDegrees)
        }
    }

    private fun showAiPromptStage(resetPrompt: Boolean = false) {
        val session = aiSession ?: return enterAiCaptureStage()
        aiStage = AiStage.PROMPT
        aiCaptureProgressBar.visibility = View.GONE
        camera?.cameraControl?.enableTorch(false)
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
        aiPageHost.visibility = View.VISIBLE
        viewfinderHost.visibility = View.GONE
        bottomChrome.visibility = View.GONE
        cameraControls.visibility = View.GONE
        aiPromptPanel.visibility = View.VISIBLE
        aiGeneratingPanel.visibility = View.GONE
        aiResultPanel.visibility = View.GONE
        if (resetPrompt || aiPromptEditText.text.isNullOrBlank()) {
            aiPromptEditText.setText(aiSettingsStore.load().defaultPrompt)
            aiPromptEditText.setSelection(aiPromptEditText.text.length)
        }
        if (resetPrompt) {
            val settings = aiSettingsStore.load()
            aiTimeWatermarkSwitch.isChecked = settings.includeTimeWatermark
            aiLocationWatermarkSwitch.isChecked = settings.includeLocationWatermark
        }
        aiOriginalPreview.setImageURI(null)
        aiOriginalPreview.setImageURI(Uri.fromFile(session.originalFile))
        returningToPromptOnCancelCapture = false
        aiCaptureTarget = AiCaptureTarget.ORIGINAL
        updateAiReferencePreview()
        updateAiMetadataLabel()
        updateAiWorkflow(AiStage.PROMPT)
        persistAiSession(stage = "PROMPT", prompt = aiPromptEditText.text.toString())
    }

    private fun startAiGeneration() {
        val session = aiSession ?: return toast("请先拍摄照片")
        val settings = aiSettingsStore.load()
        val taskId = ensureAiTransaction(session)
        val existingTask = aiTransactionStore.get(taskId) ?: return toast("该事务已被清除")
        val configuration = existingTask.configuration ?: AiGenerationConfiguration.from(settings)
        val credentialProfile = settings.profiles.firstOrNull { it.id == configuration.profileId }
        if (credentialProfile == null) {
            toast("该事务使用的 API 配置已被删除，请重新选择配置")
            showAiApiProfilesDialog()
            return
        }
        if (!configuration.preset.supportsCurrentProtocol) {
            toast("${configuration.preset.displayName} 预设需要专用接口适配，当前版本暂不能直接生成")
            showAiApiProfilesDialog()
            return
        }
        if (credentialProfile.apiKey.isBlank()) {
            toast("请先设置 API Key")
            showAiSettingsHome()
            return
        }
        aiTransactionStore.update(taskId) {
            it.copy(
                captureTime = session.captureTime,
                captureLocation = session.captureLocation,
                prompt = aiPromptEditText.text.toString().trim(),
                includeTimeWatermark = aiTimeWatermarkSwitch.isChecked,
                includeLocationWatermark = aiLocationWatermarkSwitch.isChecked,
                state = AiTransactionState.RUNNING,
                message = "正在处理中",
                resultPath = null,
                resultSaved = false,
                // A draft may still adopt settings changed from the prompt page.
                // Once submitted, retries must use its original configuration.
                configuration = configuration,
                attemptCount = it.attemptCount + 1
            )
        }
        session.resultFile?.delete()
        session.resultFile = null
        session.resultSaved = false
        persistAiSession(stage = "GENERATING", prompt = aiPromptEditText.text.toString())
        showAiGeneratingStage()
        AiGenerationService.start(this, taskId)
        AiOverlayService.refresh(this)
    }

    private fun showAiResultStage() {
        val session = aiSession ?: return
        val result = session.resultFile ?: return
        aiStage = AiStage.RESULT
        aiPageHost.visibility = View.VISIBLE
        viewfinderHost.visibility = View.GONE
        bottomChrome.visibility = View.GONE
        cameraControls.visibility = View.GONE
        aiPromptPanel.visibility = View.GONE
        aiGeneratingPanel.visibility = View.GONE
        aiResultPanel.visibility = View.VISIBLE
        aiPageSettingsButton.isEnabled = true
        aiPageSettingsButton.alpha = 1f
        aiResultPreview.setImageURI(null)
        aiResultPreview.setImageURI(Uri.fromFile(result))
        updateAiResultActions()
        updateAiWorkflow(AiStage.RESULT)
        persistAiSession(stage = "RESULT", prompt = aiPromptEditText.text.toString(), resultPath = result.absolutePath)
    }

    private fun cancelAiGeneration() {
        aiGenerationId++
        aiImageClient.cancel()
        aiSession?.transactionId?.let { AiGenerationService.cancel(this, it) }
        aiPageSettingsButton.isEnabled = true
        aiPageSettingsButton.alpha = 1f
        showAiPromptStage()
        toast("已停止等待，原图仍然保留")
    }

    private fun editPromptWhileGenerating() {
        val session = aiSession ?: return
        val sourceTask = session.transactionId?.let { aiTransactionStore.get(it) }
        val prompt = sourceTask?.prompt.orEmpty()
        val includeTime = sourceTask?.includeTimeWatermark ?: aiTimeWatermarkSwitch.isChecked
        val includeLocation = sourceTask?.includeLocationWatermark ?: aiLocationWatermarkSwitch.isChecked
        aiGenerationStatusText.text = "正在准备新的提示词任务"
        aiExecutor.execute {
            try {
                val copy = aiSessionStore.newFile("miku_ai_original_", ".jpg")
                session.originalFile.copyTo(copy, overwrite = true)
                val refCopy = session.referenceFile?.takeIf { it.isFile }?.let { ref ->
                    aiSessionStore.newFile("miku_ai_ref_", ".jpg").also { ref.copyTo(it, overwrite = true) }
                }
                val task = createAiTransactionSnapshot(
                    source = copy,
                    captureTime = session.captureTime,
                    captureLocation = session.captureLocation,
                    prompt = prompt,
                    includeTime = includeTime,
                    includeLocation = includeLocation,
                    configuration = sourceTask?.configuration,
                    referenceSource = refCopy
                )
                runOnUiThread {
                    // The original task keeps its own file and continues in the background.
                    aiSessionStore.clear(deleteFiles = false)
                    aiSession = AiSession(copy, session.captureTime, session.captureLocation, transactionId = task.id, referenceFile = refCopy)
                    aiPromptEditText.setText(prompt)
                    showAiPromptStage()
                }
            } catch (error: Throwable) {
                runOnUiThread { toast("无法复制原图: ${error.message ?: "未知错误"}") }
            }
        }
    }

    private fun retakeWhileGenerating() {
        returnToAiCaptureKeepingTask()
        toast("当前任务已转入后台处理")
    }

    private fun returnToAiCaptureKeepingTask() {
        val session = aiSession
        aiSessionStore.clear(deleteFiles = false)
        aiSession = null
        // The background task has its own original-file snapshot, so the former
        // screen session can be discarded without touching a running request.
        session?.let { abandoned -> aiExecutor.execute { deleteAiSessionFiles(abandoned) } }
        aiPromptEditText.text?.clear()
        enterAiCaptureStage()
    }

    private fun editPromptFromResult() {
        val session = aiSession ?: return
        val prompt = aiPromptEditText.text.toString().ifBlank { aiSettingsStore.load().defaultPrompt }
        val includeTime = aiTimeWatermarkSwitch.isChecked
        val includeLocation = aiLocationWatermarkSwitch.isChecked
        val previousTask = session.transactionId?.let { aiTransactionStore.get(it) }
        setAiResultButtonsEnabled(false)
        aiExecutor.execute {
            try {
                val originalCopy = aiSessionStore.newFile("miku_ai_original_", ".jpg")
                session.originalFile.copyTo(originalCopy, overwrite = true)
                val refCopy = session.referenceFile?.takeIf { it.isFile }?.let { ref ->
                    aiSessionStore.newFile("miku_ai_ref_", ".jpg").also { ref.copyTo(it, overwrite = true) }
                }
                val task = createAiTransactionSnapshot(
                    source = originalCopy,
                    captureTime = session.captureTime,
                    captureLocation = session.captureLocation,
                    prompt = prompt,
                    includeTime = includeTime,
                    includeLocation = includeLocation,
                    configuration = previousTask?.configuration,
                    referenceSource = refCopy
                )
                runOnUiThread {
                    removeTransactionWithoutBlocking(session)
                    aiSessionStore.clear(deleteFiles = false)
                    aiSession = AiSession(originalCopy, session.captureTime, session.captureLocation, transactionId = task.id, referenceFile = refCopy)
                    AiOverlayService.refresh(this@MainActivity)
                    showAiPromptStage()
                    setAiResultButtonsEnabled(true)
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    setAiResultButtonsEnabled(true)
                    toast("无法创建新的提示词任务: ${error.message ?: "未知错误"}")
                }
            }
        }
    }

    private fun confirmRegenerate() {
        AlertDialog.Builder(this)
            .setTitle("重新生成")
            .setMessage("重新生成会再次调用 OpenAI API，并可能再次产生费用。")
            .setNegativeButton("取消", null)
            .setPositiveButton("继续生成") { _, _ -> startAiGeneration() }
            .show()
    }

    private fun returnToAiCapture() {
        aiSession?.let { session ->
            removeTransactionWithoutBlocking(session)
        }
        aiSessionStore.clear(deleteFiles = false)
        aiSession = null
        aiPromptEditText.text?.clear()
        enterAiCaptureStage()
    }

    private fun saveAiSelection(saveOriginal: Boolean, saveResult: Boolean) {
        val session = aiSession ?: return toast("没有可保存的照片")
        if (saveResult && session.resultFile == null) return toast("AI 图片尚未生成")
        val capturedAt = session.transactionId
            ?.let(aiTransactionStore::get)
            ?.createdAt
            ?: session.originalFile.lastModified().takeIf { it > 0L }
            ?: System.currentTimeMillis()
        setAiResultButtonsEnabled(false)
        aiPageBackButton.isEnabled = false
        aiExecutor.execute {
            try {
                var latest: Uri? = null
                if (saveOriginal) {
                    latest = PhotoComposer.saveFileToGallery(
                        this, session.originalFile, "origin", "image/jpeg", capturedAt
                    )
                }
                if (saveResult) {
                    latest = PhotoComposer.saveFileToGallery(
                        this,
                        session.resultFile!!,
                        "AI",
                        AiImageClient.detectImageMime(session.resultFile!!),
                        capturedAt
                    )
                }
                runOnUiThread {
                    session.resultSaved = true
                    session.transactionId?.let { id ->
                        aiTransactionStore.update(id) { task -> task.copy(resultSaved = true) }
                        AiOverlayService.refresh(this@MainActivity)
                    }
                    updateRecentPhoto(latest)
                    persistAiSession(stage = "RESULT", prompt = aiPromptEditText.text.toString(), resultPath = session.resultFile?.absolutePath)
                    updateAiResultActions()
                    setAiResultButtonsEnabled(true)
                    aiPageBackButton.isEnabled = true
                    toast("AI 图片已保存")
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    setAiResultButtonsEnabled(true)
                    aiPageBackButton.isEnabled = true
                    toast("保存失败: ${error.message ?: "未知错误"}")
                }
            }
        }
    }

    private fun setAiResultButtonsEnabled(enabled: Boolean) {
        listOf(
            R.id.aiSaveResultButton, R.id.aiSaveOriginalButton, R.id.aiSaveBothButton,
            R.id.aiEditPromptButton, R.id.aiRegenerateButton,
            R.id.aiSavedRetakeButton, R.id.aiSavedEditPromptButton
        ).forEach { findViewById<View>(it).isEnabled = enabled }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, transactionReceiver, IntentFilter(AiTransactionStore.ACTION_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        if (stoppedToBackground && aiSession == null && !editing) {
            val wantAi = aiSettingsStore.load().startInAiMode
            if (wantAi && cameraMode != CameraMode.AI) {
                aiSettingsStore.acceptUploadNotice()
                enterAiCaptureStage()
            } else if (!wantAi && cameraMode == CameraMode.AI && aiStage == AiStage.CAPTURE) {
                exitAiMode(deleteSession = true)
            }
        }
        stoppedToBackground = false
        launchingInternalIntent = false
    }

    override fun onStop() {
        unregisterReceiver(transactionReceiver)
        if (!isChangingConfigurations && !launchingInternalIntent) {
            stoppedToBackground = true
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_AI_TRANSACTION_ID)?.let(::openAiTransaction)
    }

    private fun showAiGeneratingStage() {
        hideKeyboard()
        aiStage = AiStage.GENERATING
        aiCaptureProgressBar.visibility = View.GONE
        // A running transaction can be reopened from the overlay while the normal
        // camera is visible. Bring the full AI page forward, just like the result page.
        camera?.cameraControl?.enableTorch(false)
        cameraProvider?.unbindAll()
        camera = null
        imageCapture = null
        aiPageHost.visibility = View.VISIBLE
        viewfinderHost.visibility = View.GONE
        bottomChrome.visibility = View.GONE
        cameraControls.visibility = View.GONE
        aiPromptPanel.visibility = View.GONE
        aiResultPanel.visibility = View.GONE
        aiGeneratingPanel.visibility = View.VISIBLE
        aiGenerationStatusText.text = "正在后台准备照片"
        // Settings stay available throughout the workflow. Existing transactions
        // retain their own configuration snapshot, so changing defaults is safe.
        aiPageSettingsButton.isEnabled = true
        aiPageSettingsButton.alpha = 1f
        updateAiWorkflow(AiStage.GENERATING)
    }

    private fun ensureAiTransaction(session: AiSession): String {
        session.transactionId?.let { return it }
        val settings = aiSettingsStore.load()
        return createAiTransactionSnapshot(
            source = session.originalFile,
            captureTime = session.captureTime,
            captureLocation = session.captureLocation,
            prompt = aiPromptEditText.text.toString().ifBlank { settings.defaultPrompt },
            includeTime = aiTimeWatermarkSwitch.isChecked,
            includeLocation = aiLocationWatermarkSwitch.isChecked,
            configuration = AiGenerationConfiguration.from(settings),
            referenceSource = session.referenceFile
        ).id.also { session.transactionId = it }
    }

    /** Gives each durable task its own input file, separate from the visible session. */
    private fun createAiTransactionSnapshot(
        source: File,
        captureTime: String,
        captureLocation: String,
        prompt: String,
        includeTime: Boolean,
        includeLocation: Boolean,
        configuration: AiGenerationConfiguration? = null,
        createdAt: Long = System.currentTimeMillis(),
        referenceSource: File? = null
    ): AiTransaction {
        val transactionOriginal = aiTransactionStore.newFile("miku_ai_task_original_", ".jpg")
        var transactionRef: File? = null
        try {
            source.copyTo(transactionOriginal, overwrite = true)
            if (referenceSource != null && referenceSource.isFile && referenceSource.length() > 0L) {
                transactionRef = aiTransactionStore.newFile("miku_ai_task_ref_", ".jpg").also {
                    referenceSource.copyTo(it, overwrite = true)
                }
            }
            return aiTransactionStore.create(
                AiTransaction(
                    originalPath = transactionOriginal.absolutePath,
                    captureTime = captureTime,
                    captureLocation = captureLocation,
                    prompt = prompt,
                    includeTimeWatermark = includeTime,
                    includeLocationWatermark = includeLocation,
                    createdAt = createdAt,
                    configuration = configuration ?: AiGenerationConfiguration.from(aiSettingsStore.load()),
                    referencePath = transactionRef?.absolutePath
                )
            )
        } catch (error: Throwable) {
            transactionOriginal.delete()
            transactionRef?.delete()
            throw error
        }
    }

    /**
     * Removes task metadata immediately, but moves image deletion and overlay
     * work off the UI thread so returning to the camera never waits on storage.
     */
    private fun removeTransactionWithoutBlocking(session: AiSession) {
        val task = session.transactionId?.let { aiTransactionStore.get(it) }
        session.transactionId?.let { aiTransactionStore.remove(it, deleteFiles = false) }
        aiExecutor.execute {
            deleteAiSessionFiles(session)
            task?.originalFile?.delete()
            task?.resultFile?.delete()
            task?.referenceFile?.delete()
            AiOverlayService.refresh(applicationContext)
        }
    }

    private fun updateTransactionFromCurrentSession() {
        val session = aiSession ?: return
        val id = ensureAiTransaction(session)
        aiTransactionStore.update(id) {
            it.copy(
                captureTime = session.captureTime,
                captureLocation = session.captureLocation,
                prompt = aiPromptEditText.text.toString().trim(),
                includeTimeWatermark = aiTimeWatermarkSwitch.isChecked,
                includeLocationWatermark = aiLocationWatermarkSwitch.isChecked
            )
        }
        AiOverlayService.refresh(this)
    }

    private val aiPhotoPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@registerForActivityResult
        importAiPhoto(uri)
    }

    private val aiOriginalReplacePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@registerForActivityResult
        replaceCurrentOriginalPhoto(uri)
    }

    private val aiReferencePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@registerForActivityResult
        importReferencePhoto(uri)
    }

    private val presetReferencePicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@registerForActivityResult
        importPresetReferencePhoto(uri)
    }

    private fun updateAiResultActions() {
        aiResultSaveActions.visibility = View.VISIBLE
        aiResultActions.visibility = View.GONE
        aiResultPostSaveActions.visibility = View.GONE
    }

    private fun requestExitAiMode() {
        when (aiStage) {
            AiStage.CAPTURE -> {
                if (returningToPromptOnCancelCapture && aiSession != null) {
                    returningToPromptOnCancelCapture = false
                    aiCaptureTarget = AiCaptureTarget.ORIGINAL
                    showAiPromptStage(resetPrompt = false)
                } else {
                    exitAiMode(deleteSession = true)
                }
            }
            AiStage.GENERATING -> AlertDialog.Builder(this)
                .setTitle("退出 AI mode")
                .setMessage("是否停止等待并放弃本次照片？服务端若已开始生成，本次请求仍可能产生费用。")
                .setNegativeButton("继续等待", null)
                .setPositiveButton("退出") { _, _ ->
                    aiGenerationId++
                    aiImageClient.cancel()
                    aiSession?.transactionId?.let { AiGenerationService.cancel(this, it) }
                    exitAiMode(deleteSession = true)
                }
                .show()
            AiStage.RESULT -> returnToAiCapture()
            AiStage.PROMPT -> confirmExitAiModeWithUnsavedPhoto()
        }
    }

    private fun confirmExitAiModeWithUnsavedPhoto() {
        AlertDialog.Builder(this)
            .setTitle("退出 AI mode")
            .setMessage("当前照片尚未保存，确定放弃并退出吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("放弃") { _, _ -> exitAiMode(deleteSession = true) }
            .show()
    }

    private fun exitAiMode(deleteSession: Boolean) {
        hideKeyboard()
        if (deleteSession) {
            aiSession?.let(::removeTransactionWithoutBlocking)
            aiSessionStore.clear(deleteFiles = false)
        }
        aiSession = null
        aiGenerationId++
        cameraMode = CameraMode.NORMAL
        updateCameraModeButtons()
        aiStage = AiStage.CAPTURE
        aiPageHost.visibility = View.GONE
        cameraControls.visibility = View.VISIBLE
        viewfinderHost.visibility = View.VISIBLE
        bottomChrome.visibility = View.VISIBLE
        captureControls.visibility = View.VISIBLE
        editorControls.visibility = View.GONE
        normalLeftTools.visibility = View.VISIBLE
        aiLeftTools.visibility = View.GONE
        aiCaptureProgressBar.visibility = View.GONE
        flashButton.visibility = View.VISIBLE
        recentPhotoView.visibility = View.VISIBLE
        aiImportButton.visibility = View.GONE
        overlay.visibility = View.VISIBLE
        setAiResultButtonsEnabled(true)
        root.post {
            layoutViewfinder()
            bindUseCases()
            applyPhysicalOrientation(physicalRotationDegrees)
        }
    }

    private fun updateAiWorkflow(stage: AiStage) {
        fun style(view: TextView, text: String, active: Boolean, complete: Boolean = false) {
            view.text = when {
                complete -> "✓ $text"
                active -> "● $text"
                else -> "○ $text"
            }
            view.setTextColor(if (active || complete) MikuUi.accentInk else MikuUi.muted)
            view.setTypeface(null, if (active) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        style(aiCaptureStep, "拍照", stage == AiStage.CAPTURE)
        style(aiPromptStep, "提示词", false)
        style(aiGenerateStep, "AI生成", false)
        val generatingOrResult = stage == AiStage.GENERATING || stage == AiStage.RESULT
        style(aiPageCaptureStep, "拍照", false, complete = true)
        style(aiPagePromptStep, "提示词", stage == AiStage.PROMPT, complete = generatingOrResult)
        style(aiPageGenerateStep, "AI生成", generatingOrResult)
        aiPagePromptConnector.setBackgroundColor(
            if (generatingOrResult) MikuUi.accent else MikuUi.border
        )
    }

    private fun showAiSettingsHome() {
        showAiImageSettingsDialog()
    }

    private fun showAiImageSettingsDialog() {
        val current = aiSettingsStore.load()
        var startAi = current.startInAiMode
        val dialog = android.app.Dialog(this, R.style.Theme_MikuCamera)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(MikuUi.background)
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        // Extend top naturally below the punch-hole / status-bar line,
        // and leave ~50px safe margin at the bottom.
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setBackgroundColor(MikuUi.background)
            setPadding(dp(16), topSafeMarginPx(), dp(16), bottomSafeMarginPx())
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val backBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_toolbar_back)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = "返回"
            isClickable = true
            isFocusable = true
            MikuUi.toolbarIcon(this, false)
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(backBtn, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(TextView(this).apply {
            text = "相机设置"
            textSize = 20f
            setTextColor(MikuUi.ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(14), 0, 0, 0)
        })
        page.addView(header)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun section(title: String) {
            content.addView(TextView(this).apply {
                text = title
                textSize = 13f
                setTextColor(MikuUi.muted)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(6))
            })
        }

        val prompt = EditText(this).apply {
            setText(current.defaultPrompt)
            minLines = 3
            maxLines = 6
            gravity = Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            MikuUi.editText(this, 14)
        }
        val time = SwitchMaterial(this).apply {
            text = "时间水印"
            isChecked = current.includeTimeWatermark
            minHeight = dp(44)
            MikuUi.switch(this)
        }
        val location = SwitchMaterial(this).apply {
            text = "地点水印"
            isChecked = current.includeLocationWatermark
            minHeight = dp(44)
            MikuUi.switch(this)
        }

        fun saveNow() {
            val promptText = prompt.text?.toString()?.trim()?.ifBlank { AiSettingsStore.DEFAULT_PROMPT }
                ?: AiSettingsStore.DEFAULT_PROMPT
            aiSettingsStore.savePreferences(startAi, promptText, time.isChecked, location.isChecked)
        }

        section("默认启动")
        val modes = LinearLayout(this).apply {
            background = MikuUi.bordered(this@MainActivity, MikuUi.soft, MikuUi.border, 14)
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val normal = MaterialButton(this).apply { text = "水印相机" }
        val ai = MaterialButton(this).apply { text = "AI 模式" }
        fun updateModes() {
            MikuUi.button(normal, !startAi)
            MikuUi.button(ai, startAi)
        }
        fun applyStartupModeSelection(enableAi: Boolean) {
            startAi = enableAi
            if (enableAi) aiSettingsStore.acceptUploadNotice()
            updateModes()
            saveNow()
            if (aiSession == null && !editing) {
                if (enableAi && cameraMode != CameraMode.AI) {
                    enterAiCaptureStage()
                } else if (!enableAi && cameraMode == CameraMode.AI && aiStage == AiStage.CAPTURE) {
                    exitAiMode(deleteSession = true)
                }
            }
        }
        normal.setOnClickListener { applyStartupModeSelection(false) }
        ai.setOnClickListener { applyStartupModeSelection(true) }
        modes.addView(normal, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) })
        modes.addView(ai, LinearLayout.LayoutParams(0, dp(42), 1f))
        updateModes()
        content.addView(modes)

        section("默认提示词")
        prompt.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) { saveNow() }
        })
        content.addView(prompt)

        section("形象参考预设")
        val presetRefCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = MikuUi.bordered(this@MainActivity, MikuUi.surface, MikuUi.border, 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val presetThumb = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = MikuUi.rounded(this@MainActivity, MikuUi.soft, 10)
            clipToOutline = true
        }
        presetRefCard.addView(presetThumb, LinearLayout.LayoutParams(dp(54), dp(54)).apply { marginEnd = dp(12) })
        val presetInfoCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val presetTitle = TextView(this).apply {
            text = "默认形象参考图"
            textSize = 14f
            setTextColor(MikuUi.ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val presetDesc = TextView(this).apply {
            textSize = 12f
            setTextColor(MikuUi.muted)
            setPadding(0, dp(2), 0, 0)
        }
        presetInfoCol.addView(presetTitle)
        presetInfoCol.addView(presetDesc)
        presetRefCard.addView(presetInfoCol, LinearLayout.LayoutParams(0, -2, 1f))

        val presetClearBtn = MaterialButton(this).apply {
            text = "清除"
            textSize = 12f
            minWidth = 0
            setPadding(dp(10), 0, dp(10), 0)
            MikuUi.dangerButton(this)
        }
        val presetSelectBtn = MaterialButton(this).apply {
            text = "导入"
            textSize = 12f
            minWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            MikuUi.button(this, true)
            setOnClickListener {
                launchingInternalIntent = true
                presetReferencePicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        }
        presetRefCard.addView(presetClearBtn, LinearLayout.LayoutParams(-2, dp(36)).apply { marginEnd = dp(6) })
        presetRefCard.addView(presetSelectBtn, LinearLayout.LayoutParams(-2, dp(36)))

        fun refreshPresetRefUi() {
            val file = aiSettingsStore.load().defaultReferenceFile
            presetThumb.setImageURI(null)
            if (file != null) {
                presetThumb.setImageURI(Uri.fromFile(file))
                presetDesc.text = "已设置预设，提示词页将默认调用此图"
                presetSelectBtn.text = "更换"
                presetClearBtn.visibility = View.VISIBLE
            } else {
                presetDesc.text = "未设置预设，提示词页默认为空"
                presetSelectBtn.text = "导入"
                presetClearBtn.visibility = View.GONE
            }
        }
        presetClearBtn.setOnClickListener {
            aiSettingsStore.clearDefaultReferenceImage()
            refreshPresetRefUi()
            toast("已清除形象参考预设")
        }
        onPresetReferenceUpdated = { refreshPresetRefUi() }
        refreshPresetRefUi()
        content.addView(presetRefCard)

        section("生成水印")
        time.setOnCheckedChangeListener { _, _ -> saveNow() }
        location.setOnCheckedChangeListener { _, _ -> saveNow() }
        content.addView(time)
        content.addView(location)

        section("服务与应用")
        content.addView(MaterialButton(this).apply {
            text = "API 配置  ›"
            MikuUi.button(this)
            setOnClickListener { showAiApiProfilesDialog() }
        }, LinearLayout.LayoutParams(-1, dp(46)).apply { bottomMargin = dp(8) })

        section("关于 miku camera")
        val aboutCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = MikuUi.bordered(this@MainActivity, MikuUi.surface, MikuUi.border, 16)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = "miku camera"
            textSize = 18f
            setTextColor(MikuUi.ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(this).apply {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            text = "v$versionName · 作者：zakee"
            textSize = 12f
            setTextColor(MikuUi.muted)
        })
        aboutCard.addView(titleRow)
        aboutCard.addView(TextView(this).apply {
            text = "miku camera 是一款以 Miku 为主角的 Android 相机。普通模式可将 Miku 水印、时间与地点自然叠加在照片中；AI mode 会结合人物或风景重新创作，让 Miku 与现实画面互动融合，并生成有趣可爱的专属时间地点水印。"
            textSize = 13f
            setTextColor(MikuUi.ink)
            setLineSpacing(0f, 1.18f)
            setPadding(0, dp(10), 0, dp(14))
        })
        val aboutButtonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        aboutButtonRow.addView(
            aboutLinkButton("个人主页", "https://zakee.fun", primary = false),
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
        )
        aboutButtonRow.addView(
            aboutLinkButton("Buy me a coffee", "https://ifdian.net/a/zakee/plan", primary = true),
            LinearLayout.LayoutParams(0, dp(42), 1f)
        )
        aboutCard.addView(aboutButtonRow)
        content.addView(aboutCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        page.addView(content, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))
        dialog.setContentView(scroll)
        dialog.setOnDismissListener {
            onPresetReferenceUpdated = null
            hideKeyboard()
            saveNow()
            hideStatusBar()
        }
        dialog.window?.apply {
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setLayout(-1, -1)
            statusBarColor = MikuUi.background
            navigationBarColor = MikuUi.background
            WindowInsetsControllerCompat(this, decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
                hide(WindowInsetsCompat.Type.statusBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    private fun maskApiKey(key: String): String {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return "未设置"
        if (trimmed.length <= 8) return "••••••••"
        return trimmed.take(4) + "••••" + trimmed.takeLast(4)
    }

    private fun showAiApiProfilesDialog() {
        val dialog = android.app.Dialog(this, R.style.Theme_MikuCamera)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(MikuUi.background)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setBackgroundColor(MikuUi.background)
            setPadding(dp(16), topSafeMarginPx(), dp(16), bottomSafeMarginPx())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val backBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_toolbar_back)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = "返回设置页"
            isClickable = true
            isFocusable = true
            MikuUi.toolbarIcon(this, false)
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(backBtn, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(TextView(this).apply {
            text = "API 配置"
            textSize = 20f
            setTextColor(MikuUi.ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(14), 0, 0, 0)
        })
        page.addView(header)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(content, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))

        var dropdownExpanded = false

        fun rebuildContent() {
            content.removeAllViews()
            val settings = aiSettingsStore.load()
            val active = settings.activeProfile

            // 第一项：当前使用 [显示当前配置名称，点击后可展开列表]
            val currentRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            currentRow.addView(TextView(this).apply {
                text = "当前使用"
                textSize = 14f
                setTextColor(MikuUi.ink)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, -2, 1f))

            val dropdownList = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = MikuUi.bordered(this@MainActivity, MikuUi.surface, MikuUi.border, 14)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                visibility = if (dropdownExpanded) View.VISIBLE else View.GONE
            }

            val selectorButton = MaterialButton(this).apply {
                text = "${active.name}  ${if (dropdownExpanded) "▴" else "▾"}"
                MikuUi.button(this, false)
                setPadding(dp(14), 0, dp(14), 0)
                setOnClickListener {
                    dropdownExpanded = !dropdownExpanded
                    dropdownList.visibility = if (dropdownExpanded) View.VISIBLE else View.GONE
                    text = "${active.name}  ${if (dropdownExpanded) "▴" else "▾"}"
                }
            }
            currentRow.addView(selectorButton, LinearLayout.LayoutParams(-2, dp(42)))
            content.addView(currentRow)

            settings.profiles.forEach { profile ->
                val isSelected = profile.id == settings.activeProfileId
                dropdownList.addView(MaterialButton(this).apply {
                    text = "${if (isSelected) "✓ " else "    "}${profile.name}（${profile.model}）"
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    MikuUi.button(this, isSelected)
                    setPadding(dp(12), 0, dp(12), 0)
                    setOnClickListener {
                        dropdownExpanded = false
                        if (!isSelected) {
                            aiSettingsStore.saveProfiles(settings.profiles, profile.id)
                            updateAiMetadataLabel()
                        }
                        rebuildContent()
                    }
                }, LinearLayout.LayoutParams(-1, dp(40)).apply { bottomMargin = dp(4) })
            }
            content.addView(dropdownList, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

            // 下面排列添加的 API Key 配置
            content.addView(TextView(this).apply {
                text = "已添加的 API Key"
                textSize = 13f
                setTextColor(MikuUi.muted)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(12), 0, dp(8))
            })

            settings.profiles.forEach { profile ->
                val isActive = profile.id == settings.activeProfileId
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = MikuUi.bordered(
                        this@MainActivity,
                        if (isActive) MikuUi.soft else MikuUi.surface,
                        if (isActive) MikuUi.accent else MikuUi.border,
                        14
                    )
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        showAiApiProfileEditor(profile) { rebuildContent() }
                    }
                }
                val infoCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }
                infoCol.addView(TextView(this).apply {
                    text = profile.name + if (isActive) "  ·  当前使用" else ""
                    textSize = 15f
                    setTextColor(if (isActive) MikuUi.accentInk else MikuUi.ink)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                infoCol.addView(TextView(this).apply {
                    text = "${profile.preset.displayName} · ${profile.model}"
                    textSize = 12f
                    setTextColor(MikuUi.muted)
                    setPadding(0, dp(2), 0, 0)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                infoCol.addView(TextView(this).apply {
                    val hasKey = profile.apiKey.isNotBlank()
                    text = if (hasKey) "API Key：${maskApiKey(profile.apiKey)}" else "API Key：未填写（点击设置）"
                    textSize = 12f
                    setTextColor(if (hasKey) MikuUi.ink else Color.parseColor("#D94A56"))
                    setPadding(0, dp(4), 0, 0)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                card.addView(infoCol, LinearLayout.LayoutParams(0, -2, 1f))
                card.addView(TextView(this).apply {
                    text = "编辑 ›"
                    textSize = 13f
                    setTextColor(MikuUi.accentInk)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(dp(8), 0, 0, 0)
                })
                content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            }

            content.addView(MaterialButton(this).apply {
                text = "+ 新增 API 配置"
                MikuUi.button(this, true)
                setOnClickListener {
                    showAiApiProfileEditor(null) { rebuildContent() }
                }
            }, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(6) })
        }

        rebuildContent()
        dialog.setContentView(scroll)
        dialog.setOnDismissListener { hideStatusBar() }
        dialog.window?.apply {
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setLayout(-1, -1)
            statusBarColor = MikuUi.background
            navigationBarColor = MikuUi.background
            WindowInsetsControllerCompat(this, decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
                hide(WindowInsetsCompat.Type.statusBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    private fun showAiApiProfileEditor(existing: AiApiProfile?, onChanged: (() -> Unit)? = null) {
        val selected = existing ?: AiApiProfile(
            name = "OpenAI",
            preset = AiServicePreset.OPENAI,
            baseUrl = AiServicePreset.OPENAI.defaultBaseUrl,
            endpoint = AiServicePreset.OPENAI.defaultEndpoint,
            apiKey = "",
            model = AiServicePreset.OPENAI.defaultModel
        )
        val dialog = android.app.Dialog(this, R.style.Theme_MikuCamera)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(MikuUi.background)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setBackgroundColor(MikuUi.background)
            setPadding(dp(16), topSafeMarginPx(), dp(16), bottomSafeMarginPx())
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val backBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_toolbar_back)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = "返回"
            isClickable = true
            isFocusable = true
            MikuUi.toolbarIcon(this, false)
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(backBtn, LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(TextView(this).apply {
            text = if (existing == null) "新增 API 配置" else "编辑 API 配置"
            textSize = 20f
            setTextColor(MikuUi.ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(14), 0, 0, 0)
        })
        page.addView(header)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        fun label(text: String) {
            container.addView(TextView(this).apply {
                this.text = text
                textSize = 13f
                setTextColor(MikuUi.muted)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, dp(10), 0, dp(4))
            })
        }
        fun input(value: String, hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
            setText(value)
            this.hint = hint
            inputType = type
            setSingleLine(true)
            setSelection(text.length)
            MikuUi.editText(this, 12)
        }
        val nameInput = input(selected.name, "例如：OpenAI 主账号")
        val presetSpinner = Spinner(this).apply {
            background = MikuUi.bordered(this@MainActivity, MikuUi.surface, MikuUi.border, 12)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        val presets = AiServicePreset.entries.toList()
        presetSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, presets.map { it.displayName })
        presetSpinner.setSelection(presets.indexOf(selected.preset).coerceAtLeast(0))
        val baseUrlInput = input(selected.baseUrl, "https://...", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val endpointInput = input(selected.endpoint, "/images/edits")
        val keyInput = input(selected.apiKey, "sk-...", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        val modelInput = input(selected.model, "模型名称")
        val modelHint = TextView(this).apply {
            textSize = 12f
            setTextColor(MikuUi.muted)
            setPadding(0, dp(4), 0, 0)
        }
        val hint = TextView(this).apply {
            textSize = 12f
            setTextColor(MikuUi.muted)
            setPadding(0, dp(8), 0, 0)
        }
        fun updatePresetHint(fillDefaults: Boolean) {
            val preset = presets[presetSpinner.selectedItemPosition]
            if (fillDefaults) {
                if (nameInput.text.toString().isBlank() || presets.any { it.displayName.startsWith(nameInput.text.toString()) }) {
                    nameInput.setText(preset.displayName.substringBefore(" /"))
                }
                baseUrlInput.setText(preset.defaultBaseUrl)
                endpointInput.setText(preset.defaultEndpoint)
                modelInput.setText(preset.defaultModel)
            }
            modelHint.text = "默认模型：${preset.defaultModel}（可手动修改为其他模型）"
            hint.text = if (preset.supportsCurrentProtocol) {
                when {
                    preset.useGeminiProtocol -> "使用 Gemini Interactions API，可直接生成（本地照片会以内联 Base64 提交）。"
                    preset.useQwenProtocol -> "使用通义千问多模态生成协议，可直接生成（本地照片会以内联 Base64 提交，返回结果需下载）。"
                    preset.useGenerationsProtocol -> "使用火山方舟图片生成协议，可直接生成（本地照片会以内联 Base64 提交）。"
                    else -> "使用 OpenAI 图片编辑兼容协议，可直接生成。"
                }
            } else {
                "已保存官方预设；该服务需要专用请求协议适配，当前版本不能直接生成。"
            }
        }
        presetSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                updatePresetHint(position != presets.indexOf(selected.preset))
            }
        }
        updatePresetHint(false)
        label("配置名称"); container.addView(nameInput)
        label("服务类型 / 预设"); container.addView(presetSpinner, LinearLayout.LayoutParams(-1, dp(44)))
        label("API Key"); container.addView(keyInput)
        label("模型"); container.addView(modelInput); container.addView(modelHint)
        label("Base URL"); container.addView(baseUrlInput)
        label("接口路径"); container.addView(endpointInput); container.addView(hint)

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        if (existing != null) {
            actionRow.addView(MaterialButton(this).apply {
                text = "删除"
                MikuUi.dangerButton(this)
                setOnClickListener {
                    val current = aiSettingsStore.load()
                    if (current.profiles.size == 1) {
                        toast("至少保留一组 API 配置")
                        return@setOnClickListener
                    }
                    val profiles = current.profiles.filterNot { it.id == existing.id }
                    val nextActive = if (current.activeProfileId == existing.id) profiles.first().id else current.activeProfileId
                    aiSettingsStore.saveProfiles(profiles, nextActive)
                    updateAiMetadataLabel()
                    dialog.dismiss()
                    onChanged?.invoke()
                }
            }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) })
        }
        actionRow.addView(MaterialButton(this).apply {
            text = "保存配置"
            MikuUi.button(this, true)
            setOnClickListener {
                val preset = presets[presetSpinner.selectedItemPosition]
                val updated = selected.copy(
                    name = nameInput.text.toString(),
                    preset = preset,
                    baseUrl = baseUrlInput.text.toString(),
                    endpoint = endpointInput.text.toString(),
                    apiKey = keyInput.text.toString(),
                    model = modelInput.text.toString()
                )
                val current = aiSettingsStore.load()
                val profiles = if (existing == null) current.profiles + updated else current.profiles.map { if (it.id == updated.id) updated else it }
                runCatching { aiSettingsStore.saveProfiles(profiles, updated.id) }.onSuccess {
                    updateAiMetadataLabel()
                    toast("API 配置已保存")
                    dialog.dismiss()
                    onChanged?.invoke()
                }.onFailure { toast(it.message ?: "API 配置无效") }
            }
        }, LinearLayout.LayoutParams(0, dp(44), 1.4f))
        container.addView(actionRow)

        page.addView(container, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(page, FrameLayout.LayoutParams(-1, -2))
        dialog.setContentView(scroll)
        dialog.setOnDismissListener {
            hideKeyboard()
            hideStatusBar()
        }
        dialog.window?.apply {
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setLayout(-1, -1)
            statusBarColor = MikuUi.background
            navigationBarColor = MikuUi.background
            WindowInsetsControllerCompat(this, decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
                hide(WindowInsetsCompat.Type.statusBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    private fun showAboutDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).roundToInt(),
                0,
                (24 * density).roundToInt(),
                (8 * density).roundToInt()
            )
        }
        container.addView(TextView(this).apply {
            text = "miku camera"
            textSize = 22f
            setTextColor(MikuUi.ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        container.addView(TextView(this).apply {
            text = "作者：zakee"
            textSize = 15f
            setTextColor(MikuUi.muted)
            setPadding(0, (8 * density).roundToInt(), 0, 0)
        })
        container.addView(TextView(this).apply {
            text = "miku camera 是一款以 Miku 为主角的 Android 相机。普通模式可将 Miku 水印、时间与地点自然叠加在照片中；AI mode 会结合人物或风景重新创作，让 Miku 与现实画面互动融合，并生成有趣可爱的专属时间地点水印。"
            textSize = 14f
            setTextColor(MikuUi.ink)
            setLineSpacing(0f, 1.15f)
            setPadding(0, (14 * density).roundToInt(), 0, (18 * density).roundToInt())
        })
        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        buttonRow.addView(
            aboutLinkButton("个人主页", "https://zakee.fun", primary = false),
            LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(
            aboutLinkButton("Buy me a coffee", "https://ifdian.net/a/zakee/plan", primary = true),
            LinearLayout.LayoutParams(0, dp(44), 1f)
        )
        container.addView(buttonRow)
        container.addView(TextView(this).apply {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            text = "版本 $versionName"
            textSize = 12f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(MikuUi.muted)
            setPadding(0, (12 * density).roundToInt(), 0, 0)
        })

        AlertDialog.Builder(this)
            .setTitle("关于")
            .setView(container)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun aboutLinkButton(label: String, url: String, primary: Boolean): View {
        return MaterialButton(this).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
            MikuUi.button(this, primary)
            setOnClickListener { openExternalUrl(url) }
        }
    }

    private fun resolveThemeColor(attribute: Int): Int {
        val value = TypedValue()
        if (!theme.resolveAttribute(attribute, value, true)) return Color.WHITE
        return if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
    }

    private fun openExternalUrl(url: String) {
        launchingInternalIntent = true
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            launchingInternalIntent = false
            toast("没有可打开网页的应用")
        }
    }

    private fun currentGenerationConfiguration(): AiGenerationConfiguration =
        aiSession?.transactionId?.let { aiTransactionStore.get(it)?.configuration }
            ?: AiGenerationConfiguration.from(aiSettingsStore.load())

    private fun updateAiMetadataLabel() {
        if (!::aiVisualStyleText.isInitialized) return
        val session = aiSession ?: return
        val config = currentGenerationConfiguration()
        aiTimeEditText.text = session.captureTime
        aiLocationEditText.text = session.captureLocation.ifBlank { "未获取到地点" }
        aiVisualStyleText.text = "形象"
        aiOutfitStyleText.text = "服装"
        fun selected(id: Int, checked: Boolean) {
            findViewById<MaterialButton>(id).apply {
                MikuUi.button(this, checked); isSelected = checked
                contentDescription = "$text${if (checked) "，已选择" else ""}"
            }
        }
        selected(R.id.aiVisualStyleToggleButton, config.visualStyle == AiVisualStyle.ANIME)
        selected(R.id.aiRealisticButton, config.visualStyle == AiVisualStyle.REALISTIC)
        selected(R.id.aiOutfitStyleToggleButton, config.outfitStyle == AiOutfitStyle.OFFICIAL)
        selected(R.id.aiAdaptiveButton, config.outfitStyle == AiOutfitStyle.SCENE_ADAPTIVE)
        listOf(R.id.aiResolution1, R.id.aiResolution2, R.id.aiResolution4).zip(AiResolution.entries).forEach { (id, resolution) ->
            selected(id, config.resolution == resolution)
        }
        findViewById<TextView>(R.id.aiResolutionHint).visibility = View.GONE
    }

    private fun selectGenerationOptions(
        visual: AiVisualStyle = currentGenerationConfiguration().visualStyle,
        outfit: AiOutfitStyle = currentGenerationConfiguration().outfitStyle,
        resolution: AiResolution = currentGenerationConfiguration().resolution
    ) {
        val session = aiSession ?: return
        val settings = aiSettingsStore.load()
        val config = currentGenerationConfiguration().copy(visualStyle = visual, outfitStyle = outfit, resolution = resolution)
        aiSettingsStore.saveImageSettings(visual, outfit, settings.defaultPrompt, settings.includeTimeWatermark, settings.includeLocationWatermark)
        aiSettingsStore.saveResolution(resolution)
        val existing = session.transactionId?.let { aiTransactionStore.get(it) }
        if (existing != null && existing.attemptCount > 0) {
            // Editing a submitted task creates a separate draft; retries retain their snapshot.
            val task = createAiTransactionSnapshot(session.originalFile, session.captureTime, session.captureLocation,
                aiPromptEditText.text.toString(), aiTimeWatermarkSwitch.isChecked, aiLocationWatermarkSwitch.isChecked, config,
                referenceSource = session.referenceFile)
            aiSession = AiSession(task.originalFile, task.captureTime, task.captureLocation, transactionId = task.id, referenceFile = session.referenceFile)
        } else {
            val id = ensureAiTransaction(session)
            aiTransactionStore.update(id) { it.copy(configuration = config) }
        }
        updateAiMetadataLabel()
        persistAiSession("PROMPT", aiPromptEditText.text.toString())
    }

    private fun editAiMetadata(time: Boolean) {
        val session = aiSession ?: return
        val input = EditText(this).apply {
            setText(if (time) session.captureTime else session.captureLocation)
            MikuUi.editText(this, 12)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), dp(4))
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle(if (time) "编辑拍摄时间" else "编辑拍摄地点")
            .setView(box)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ ->
                if (time) session.captureTime = input.text.toString().trim() else session.captureLocation = input.text.toString().trim()
                updateAiMetadataLabel()
                updateTransactionFromCurrentSession()
                persistAiSession("PROMPT", aiPromptEditText.text.toString())
            }
            .show()
    }

    private fun showAiDebugLog(log: String) {
        val safeLog = log.ifBlank { "没有可用日志。请重新生成后，在失败提示中查看。" }
        val logView = TextView(this).apply {
            text = safeLog
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setPadding(
                (16 * density).roundToInt(),
                (12 * density).roundToInt(),
                (16 * density).roundToInt(),
                (12 * density).roundToInt()
            )
        }
        val scroll = ScrollView(this).apply {
            addView(logView)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("AI 请求日志（已脱敏）")
            .setMessage("日志不包含 API Key、照片内容、完整提示词或 Base64 图片。")
            .setView(scroll)
            .setNegativeButton("关闭", null)
            .setPositiveButton("复制日志", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("miku camera AI log", safeLog))
                toast("日志已复制")
            }
        }
        dialog.show()
    }

    private fun buildFallbackAiLog(
        baseUrl: String,
        endpointPath: String,
        model: String,
        visualStyle: AiVisualStyle,
        outfitStyle: AiOutfitStyle,
        error: Throwable
    ): String {
        val safeEndpoint = runCatching { AiImageClient.endpoint(baseUrl, endpointPath) }
            .getOrDefault("Base URL 无效")
            .substringBefore('?')
        val safeMessage = (error.message ?: "unknown")
            .replace(Regex("sk-[A-Za-z0-9_-]{8,}"), "sk-<redacted>")
            .replace(Regex("[A-Za-z0-9+/=_-]{256,}"), "<long-data-redacted>")
        return buildString {
            appendLine("miku camera AI request log (sensitive values removed)")
            appendLine("endpoint=$safeEndpoint")
            appendLine("model=$model")
            appendLine("visualStyle=${visualStyle.name} (${visualStyle.displayName})")
            appendLine("outfitStyle=${outfitStyle.name} (${outfitStyle.displayName})")
            appendLine("apiKey=configured (not logged)")
            appendLine("errorType=${error.javaClass.simpleName}")
            appendLine("errorMessage=$safeMessage")
        }
    }

    private fun formatAiCaptureTime(): String =
        SimpleDateFormat("yyyy年M月d日H时", Locale.CHINA).format(Date())

    private fun deleteAiSessionFiles(session: AiSession) {
        session.originalFile.delete()
        session.resultFile?.delete()
        session.referenceFile?.delete()
    }

    /** 将会话状态持久化到本地（原图/结果图已存于 filesDir/ai_session，此处写元数据）。 */
    private fun persistAiSession(stage: String, prompt: String, resultPath: String? = aiSession?.resultFile?.absolutePath) {
        val session = aiSession ?: return
        aiSessionStore.save(
            AiSessionStore.Snapshot(
                originalPath = session.originalFile.absolutePath,
                captureTime = session.captureTime,
                captureLocation = session.captureLocation,
                prompt = prompt,
                stage = stage,
                resultPath = resultPath,
                resultSaved = session.resultSaved,
                transactionId = session.transactionId,
                referencePath = session.referenceFile?.absolutePath
            )
        )
    }

    /** 进程被杀后重开时，若有未完成的持久化会话则恢复界面（复用现有 stage 显示）。 */
    private fun restoreAiSessionIfNeeded() {
        val snapshot = aiSessionStore.load() ?: return
        // 已处于 AI 会话中（本次启动即由拍摄进入）则不重复恢复。
        if (aiSession != null) return
        aiSession = AiSession(
            originalFile = snapshot.originalFile,
            captureTime = snapshot.captureTime,
            captureLocation = snapshot.captureLocation,
            resultFile = snapshot.resultFile,
            resultSaved = snapshot.resultSaved,
            transactionId = snapshot.transactionId,
            referenceFile = snapshot.referenceFile
        )
        cameraMode = CameraMode.AI
        when (snapshot.stage) {
            "RESULT" -> {
                aiPromptEditText.setText(snapshot.prompt)
                aiPromptEditText.setSelection(aiPromptEditText.text.length)
                showAiResultStage()
            }
            else -> {
                // PROMPT 或 GENERATING（生成未完成）：恢复提示词页，回填上次编辑的提示词。
                val prompt = snapshot.prompt.ifBlank { aiSettingsStore.load().defaultPrompt }.toString()
                aiPromptEditText.setText(prompt)
                aiPromptEditText.setSelection(aiPromptEditText.text.length)
                if (snapshot.stage == "GENERATING") {
                    toast("上次 AI 生成未完成（应用已被关闭），已为你保留原图，可重新生成")
                }
                showAiPromptStage()
            }
        }
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(currentFocus?.windowToken, 0)
        currentFocus?.clearFocus()
    }

    private fun updateStreetSwitchVisibility(locationDisplayOn: Boolean) {
        streetSwitch.visibility = if (locationDisplayOn) View.VISIBLE else View.GONE
    }

    /**
     * GPS always runs with the camera session. The watermark "地点" switch only
     * controls whether the resolved address is drawn on the photo.
     */
    private fun startLocationWarmup() {
        if (hasLocationPermission()) {
            requestLocation(silent = true)
        }
    }

    private fun continuePermissionSequence() {
        val missingRuntimePermissions = buildList {
            if (!hasLocationPermission() && !locationPermissionRequested) {
                locationPermissionRequested = true
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (!hasCameraPermission() && !cameraPermissionRequested) {
                cameraPermissionRequested = true
                addAll(requiredCameraPermissions())
            }
            if (!hasPhotoPermission() && !photoPermissionRequested) {
                photoPermissionRequested = true
                add(photoPermissionName())
            }
        }
        when {
            missingRuntimePermissions.isNotEmpty() -> {
                startupPermissions.launch(missingRuntimePermissions.distinct().toTypedArray())
            }
            !Settings.canDrawOverlays(this) && !overlayPromptShown -> {
                if (hasCameraPermission()) startCamera()
                if (hasLocationPermission()) startLocationWarmup()
                overlayPromptShown = true
                AlertDialog.Builder(this)
                    .setTitle("开启悬浮窗功能")
                    .setMessage("AI 事务可在后台继续处理。开启悬浮窗后可随时查看进行中、成功和失败的任务。")
                    .setNegativeButton("暂不") { _, _ -> startCamera(); startLocationWarmup() }
                    .setPositiveButton("去开启") { _, _ ->
                        launchingInternalIntent = true
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    }
                    .show()
            }
            else -> {
                if (hasCameraPermission()) startCamera()
                if (hasLocationPermission()) startLocationWarmup()
                AiOverlayService.refresh(this)
            }
        }
    }

    private fun scheduleAiOverlayRefresh(delayMillis: Long = 450L) {
        root.postDelayed({
            if (!isFinishing && !isDestroyed) {
                runCatching { AiOverlayService.refresh(applicationContext) }
            }
        }, delayMillis)
    }

    private fun hasPhotoPermission(): Boolean = ContextCompat.checkSelfPermission(this, photoPermissionName()) == PackageManager.PERMISSION_GRANTED
    private fun photoPermissionName(): String = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun importAiPhoto(uri: Uri) {
        if (cameraMode != CameraMode.AI) return
        if (aiCaptureTarget == AiCaptureTarget.REFERENCE && aiSession != null) {
            importReferencePhoto(uri)
            return
        }
        if (returningToPromptOnCancelCapture && aiSession != null) {
            replaceCurrentOriginalPhoto(uri)
            return
        }
        captureButton.isEnabled = false
        cameraExecutor.execute {
            try {
                val imported = aiSessionStore.newFile("miku_ai_original_", ".jpg")
                contentResolver.openInputStream(uri)?.use { input -> imported.outputStream().use { input.copyTo(it) } }
                    ?: throw IllegalArgumentException("无法读取照片")
                val refCopy = prepareDefaultReferenceCopy()
                val time = formatAiCaptureTime()
                val location = formatLocationLabel(includePoi = true).orEmpty()
                val settings = aiSettingsStore.load()
                val task = createAiTransactionSnapshot(
                    source = imported,
                    captureTime = time,
                    captureLocation = location,
                    prompt = settings.defaultPrompt,
                    includeTime = settings.includeTimeWatermark,
                    includeLocation = settings.includeLocationWatermark,
                    configuration = AiGenerationConfiguration.from(settings),
                    referenceSource = refCopy
                )
                val importedSession = AiSession(imported, time, location, transactionId = task.id, referenceFile = refCopy)
                runOnUiThread {
                    if (cameraMode != CameraMode.AI) {
                        removeTransactionWithoutBlocking(importedSession)
                        captureButton.isEnabled = true
                        return@runOnUiThread
                    }
                    aiSession?.let(::removeTransactionWithoutBlocking)
                    aiSession = importedSession
                    persistAiSession("PROMPT", settings.defaultPrompt)
                    AiOverlayService.refresh(this@MainActivity)
                    captureButton.isEnabled = true
                    showAiPromptStage(resetPrompt = true)
                }
            } catch (error: Throwable) {
                runOnUiThread { captureButton.isEnabled = true; toast("导入失败: ${error.message ?: "未知错误"}") }
            }
        }
    }

    private fun replaceCurrentOriginalPhoto(uri: Uri) {
        val session = aiSession ?: return importAiPhoto(uri)
        cameraExecutor.execute {
            try {
                val imported = aiSessionStore.newFile("miku_ai_original_", ".jpg")
                contentResolver.openInputStream(uri)?.use { input -> imported.outputStream().use { input.copyTo(it) } }
                    ?: throw IllegalArgumentException("无法读取照片")
                val oldOriginal = session.originalFile
                val id = ensureAiTransaction(session)
                val taskOriginal = aiTransactionStore.newFile("miku_ai_task_original_", ".jpg")
                imported.copyTo(taskOriginal, overwrite = true)
                val oldTaskOriginal = aiTransactionStore.get(id)?.originalFile
                aiTransactionStore.update(id) { it.copy(originalPath = taskOriginal.absolutePath) }
                if (oldTaskOriginal?.absolutePath != taskOriginal.absolutePath) oldTaskOriginal?.delete()
                runOnUiThread {
                    session.originalFile = imported
                    if (oldOriginal.absolutePath != imported.absolutePath) oldOriginal.delete()
                    returningToPromptOnCancelCapture = false
                    aiCaptureTarget = AiCaptureTarget.ORIGINAL
                    showAiPromptStage(resetPrompt = false)
                    AiOverlayService.refresh(this@MainActivity)
                }
            } catch (error: Throwable) {
                runOnUiThread { toast("导入原图失败: ${error.message ?: "未知错误"}") }
            }
        }
    }

    private fun importReferencePhoto(uri: Uri) {
        val session = aiSession ?: return
        cameraExecutor.execute {
            try {
                val refFile = aiSessionStore.newFile("miku_ai_ref_", ".jpg")
                contentResolver.openInputStream(uri)?.use { input -> refFile.outputStream().use { input.copyTo(it) } }
                    ?: throw IllegalArgumentException("无法读取形象参考图")
                val id = ensureAiTransaction(session)
                val taskRef = aiTransactionStore.newFile("miku_ai_task_ref_", ".jpg")
                refFile.copyTo(taskRef, overwrite = true)
                val oldTaskRef = aiTransactionStore.get(id)?.referenceFile
                aiTransactionStore.update(id) { it.copy(referencePath = taskRef.absolutePath) }
                if (oldTaskRef?.absolutePath != taskRef.absolutePath) oldTaskRef?.delete()
                val oldSessionRef = session.referenceFile
                runOnUiThread {
                    session.referenceFile = refFile
                    if (oldSessionRef?.absolutePath != refFile.absolutePath) oldSessionRef?.delete()
                    returningToPromptOnCancelCapture = false
                    aiCaptureTarget = AiCaptureTarget.ORIGINAL
                    showAiPromptStage(resetPrompt = false)
                    AiOverlayService.refresh(this@MainActivity)
                }
            } catch (error: Throwable) {
                runOnUiThread { toast("导入形象参考失败: ${error.message ?: "未知错误"}") }
            }
        }
    }

    private fun importPresetReferencePhoto(uri: Uri) {
        cameraExecutor.execute {
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    aiSettingsStore.saveDefaultReferenceImage(input)
                } ?: throw IllegalArgumentException("无法读取图片")
                runOnUiThread {
                    onPresetReferenceUpdated?.invoke()
                    toast("已更新默认形象参考预设")
                }
            } catch (error: Throwable) {
                runOnUiThread { toast("设置预设失败: ${error.message ?: "未知错误"}") }
            }
        }
    }

    private fun openAiTransaction(id: String) {
        // Preserve prompt-page edits before switching to another durable transaction.
        if (aiStage == AiStage.PROMPT && aiSession?.transactionId != null) {
            updateTransactionFromCurrentSession()
        }
        val task = aiTransactionStore.get(id) ?: return toast("该事务已被清除")
        aiSession = AiSession(
            originalFile = task.originalFile,
            captureTime = task.captureTime,
            captureLocation = task.captureLocation,
            resultFile = task.resultFile,
            resultSaved = task.resultSaved,
            transactionId = task.id,
            referenceFile = task.referenceFile
        )
        cameraMode = CameraMode.AI
        aiPromptEditText.setText(task.prompt)
        aiTimeWatermarkSwitch.isChecked = task.includeTimeWatermark
        aiLocationWatermarkSwitch.isChecked = task.includeLocationWatermark
        when (task.state) {
            AiTransactionState.DRAFT -> showAiPromptStage()
            AiTransactionState.SUCCESS -> showAiResultStage()
            AiTransactionState.RUNNING -> showAiGeneratingStage()
            AiTransactionState.FAILED -> { showAiPromptStage(); toast("上次生成失败：${task.message}") }
        }
    }

    private fun refreshVisibleTransaction() {
        val id = aiSession?.transactionId ?: return
        val task = aiTransactionStore.get(id) ?: return
        if (task.state == AiTransactionState.RUNNING && aiStage == AiStage.GENERATING) {
            aiGenerationStatusText.text = task.message
        } else if (task.state == AiTransactionState.SUCCESS && aiStage == AiStage.GENERATING) {
            aiSession?.resultFile = task.resultFile
            aiSession?.resultSaved = task.resultSaved
            showAiResultStage()
        } else if (task.state == AiTransactionState.FAILED && aiStage == AiStage.GENERATING) {
            showAiPromptStage()
            toast("AI 生成失败：${task.message}")
        }
    }

    private fun handlePreviewTouch(event: MotionEvent): Boolean {
        if (editing || aiPageHost.visibility == View.VISIBLE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = event.x
                touchDownY = event.y
                val local = mapPreviewToFocusLocal(event.x, event.y)
                lastExposureLocalY = local.second
                exposureGestureActive = focusExposure.visibility == View.VISIBLE &&
                    isOnFocusExposureStrip(local.first, local.second)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (focusExposure.visibility == View.VISIBLE) {
                    val local = mapPreviewToFocusLocal(event.x, event.y)
                    val onStrip = exposureGestureActive || isOnFocusExposureStrip(local.first, local.second)
                    val dy = local.second - lastExposureLocalY
                    if (onStrip && abs(dy) > touchSlop / 2f) {
                        exposureGestureActive = true
                        // Use local Y so "up/down" follows the current device orientation.
                        focusExposure.applyVerticalDelta(dy)
                        lastExposureLocalY = local.second
                    } else if (onStrip) {
                        lastExposureLocalY = local.second
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchDownX
                val dy = event.y - touchDownY
                if (!exposureGestureActive && abs(dx) < touchSlop && abs(dy) < touchSlop) {
                    // Empty area only — watermark hits are handled by the overlay
                    // so position/scale can be changed without leaving camera mode.
                    focusAt(event.x, event.y)
                }
                exposureGestureActive = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                exposureGestureActive = false
                return true
            }
        }
        return true
    }

    /** Map a preview-local point into FocusExposureView space, undoing its rotation. */
    private fun mapPreviewToFocusLocal(previewX: Float, previewY: Float): Pair<Float, Float> {
        val cx = focusExposure.left + focusExposure.width / 2f
        val cy = focusExposure.top + focusExposure.height / 2f
        val dx = previewX - cx
        val dy = previewY - cy
        val rad = Math.toRadians(-focusExposure.rotation.toDouble())
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        val lx = dx * cos - dy * sin + focusExposure.width / 2f
        val ly = dx * sin + dy * cos + focusExposure.height / 2f
        return lx to ly
    }

    /** Whether a point in focus-view local coords is on the right-side exposure strip. */
    private fun isOnFocusExposureStrip(localX: Float, localY: Float): Boolean {
        if (focusExposure.visibility != View.VISIBLE) return false
        // Generous strip so one-handed swipes do not need precise targeting.
        return localX >= focusExposure.width * 0.28f - 24f * density &&
            localX <= focusExposure.width + 36f * density &&
            localY >= -24f * density &&
            localY <= focusExposure.height + 24f * density
    }

    private fun focusAt(x: Float, y: Float) {
        val currentCamera = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        currentCamera.cameraControl.startFocusAndMetering(action)
        showFocusExposure(x, y)
    }

    private fun showFocusExposure(x: Float, y: Float) {
        syncExposureRangeFromCamera()
        // Preview and focus indicator share viewfinderHost coordinates.
        focusExposure.exposureFraction = currentExposureFraction()
        focusExposure.rotation = ((360 - physicalRotationDegrees) % 360).toFloat()
        focusExposure.showAt(x, y)
    }

    private fun syncExposureRangeFromCamera() {
        val exposure = camera?.cameraInfo?.exposureState
        if (exposure == null || !exposure.isExposureCompensationSupported) {
            exposureMin = 0
            exposureMax = 0
            exposureIndex = 0
            return
        }
        val range = exposure.exposureCompensationRange
        exposureMin = range.lower
        exposureMax = range.upper
        exposureIndex = exposure.exposureCompensationIndex.coerceIn(exposureMin, exposureMax)
    }

    private fun currentExposureFraction(): Float {
        if (exposureMax <= exposureMin) return 0.5f
        return (exposureIndex - exposureMin).toFloat() / (exposureMax - exposureMin).toFloat()
    }

    private fun applyExposureFraction(fraction: Float) {
        if (exposureMax <= exposureMin) return
        val index = (exposureMin + fraction * (exposureMax - exposureMin)).roundToInt()
            .coerceIn(exposureMin, exposureMax)
        if (index == exposureIndex) return
        exposureIndex = index
        camera?.cameraControl?.setExposureCompensationIndex(index)
    }

    private fun startCamera() {
        if (!hasCameraPermission()) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            cameraProvider = future.get()
            bindUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        if (previewView.width <= 0 || previewView.height <= 0) {
            previewView.post { bindUseCases() }
            return
        }
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        // Keep preview + capture on the same rotation as the fixed portrait
        // viewfinder. Shared ViewPort (from PreviewView) makes the saved FOV
        // match the on-screen frame; PhotoComposer then rolls for landscape.
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER
        val preview = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(Surface.ROTATION_0)
            .build()

        val viewPort = previewView.viewPort
            ?: ViewPort.Builder(
                Rational(previewView.width, previewView.height),
                Surface.ROTATION_0
            ).setScaleType(ViewPort.FILL_CENTER).build()

        val group = UseCaseGroup.Builder()
            .setViewPort(viewPort)
            .addUseCase(preview)
            .addUseCase(capture)
            .build()

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, group)
            imageCapture = capture
            boundViewfinderWidth = previewView.width
            boundViewfinderHeight = previewView.height
            updateFlashControl()
            syncExposureRangeFromCamera()
        } catch (_: Exception) {
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                lensFacing = CameraSelector.LENS_FACING_BACK
                toast("设备没有前置镜头，已切回后置镜头")
                bindUseCases()
            } else toast("相机启动失败")
        }
    }

    private fun updateFlashControl() {
        val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
        flashButton.isEnabled = hasFlash
        flashButton.alpha = if (hasFlash) 1f else 0.45f
        flashBadge.visibility = View.GONE
        val iconRes = when {
            !hasFlash || flashMode == ImageCapture.FLASH_MODE_OFF -> R.drawable.ic_toolbar_flash_off
            flashMode == ImageCapture.FLASH_MODE_ON -> R.drawable.ic_toolbar_flash_on
            else -> R.drawable.ic_toolbar_flash_auto
        }
        flashIcon.setImageResource(iconRes)
        MikuUi.toolbarIcon(flashButton, hasFlash && flashMode != ImageCapture.FLASH_MODE_OFF)
        if (!hasFlash) {
            camera?.cameraControl?.enableTorch(false)
            imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
        } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
            // Some phones expose a flash unit but do not reliably fire ImageCapture's
            // flash callback. Torch mode is a reliable fallback for the "开" state.
            imageCapture?.flashMode = ImageCapture.FLASH_MODE_OFF
            camera?.cameraControl?.enableTorch(true)
        } else {
            camera?.cameraControl?.enableTorch(false)
            imageCapture?.flashMode = flashMode
        }
    }

    private fun applyPhysicalOrientation(degrees: Int) {
        physicalRotationDegrees = degrees
        // The viewfinder stays fixed. Watermark canvas rotates with posture;
        // editor chrome stays upright (see applyControlRotation).
        val visualRotation = ((360 - degrees) % 360)
        val generated = if (cameraMode == CameraMode.NORMAL) {
            overlay.setPhysicalRotation(visualRotation, degrees)
        } else false
        applyControlRotation(degrees)
        // Capture stays on ROTATION_0 with the viewfinder ViewPort; orientation
        // is applied in PhotoComposer so FOV stays locked to the preview.
        if (focusExposure.visibility == View.VISIBLE) {
            focusExposure.rotation = visualRotation.toFloat()
        }
        if (generated && !editing && cameraMode == CameraMode.NORMAL) persistCurrentPresetIfSaved()
    }

    private fun applyControlRotation(degrees: Int) {
        val rotation = ((360 - degrees) % 360).toFloat()
        // Only camera chrome follows device orientation. Editor controls
        // (upload / switches / seekbar / save) stay upright for readability.
        listOf<View>(
            selectWatermarkButton,
            aiModeButton,
            aiBackButton,
            aiSettingsButton,
            aboutButton,
            flashButton,
            recentPhotoView,
            captureButton,
            switchCameraButton
        ).forEach { it.rotation = rotation }

        listOf<View>(
            findViewById(R.id.uploadButton),
            timeSwitch,
            locationSwitch,
            streetSwitch,
            outlineSeekBar,
            findViewById(R.id.cancelEditButton),
            findViewById(R.id.saveWatermarkButton),
            findViewById(R.id.deleteWatermarkButton)
        ).forEach { it.rotation = 0f }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        hideStatusBar()
        root.post {
            layoutViewfinder()
            applyPhysicalOrientation(physicalRotationDegrees)
        }
    }

    private fun persistCurrentPresetIfSaved() {
        val current = overlay.currentPreset()
        if (store.loadAll().any { it.id == current.id }) {
            store.save(current)
        }
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return toast("相机尚未准备好")
        captureButton.isEnabled = false
        playShutterApertureAnimation()
        val file = File.createTempFile("capture_", ".jpg", cacheDir)
        val captureModeAtShutter = cameraMode
        val aiCaptureTimeAtShutter = if (captureModeAtShutter == CameraMode.AI) formatAiCaptureTime() else ""
        val fileNameTimeAtShutter = System.currentTimeMillis()
        val aiLocationAtShutter = if (captureModeAtShutter == CameraMode.AI) {
            formatLocationLabel(includePoi = true).orEmpty()
        } else ""
        // Snapshot the active lens with the render state. Camera callbacks run
        // asynchronously, so lensFacing may change before composition starts.
        val spec = overlay.renderSpec().copy(
            viewfinderWidth = previewView.width.coerceAtLeast(1),
            viewfinderHeight = previewView.height.coerceAtLeast(1),
            orientationDegrees = physicalRotationDegrees,
            isFrontFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
        )
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    if (captureModeAtShutter == CameraMode.AI) {
                        if (aiCaptureTarget == AiCaptureTarget.REFERENCE && aiSession != null) {
                            val refFile = aiSessionStore.newFile("miku_ai_ref_", ".jpg")
                            PhotoComposer.prepareCleanPhoto(file, spec, refFile)
                            val session = aiSession!!
                            val id = ensureAiTransaction(session)
                            val taskRef = aiTransactionStore.newFile("miku_ai_task_ref_", ".jpg")
                            refFile.copyTo(taskRef, overwrite = true)
                            val oldTaskRef = aiTransactionStore.get(id)?.referenceFile
                            aiTransactionStore.update(id) { it.copy(referencePath = taskRef.absolutePath) }
                            if (oldTaskRef?.absolutePath != taskRef.absolutePath) oldTaskRef?.delete()
                            val oldSessionRef = session.referenceFile
                            runOnUiThread {
                                session.referenceFile = refFile
                                if (oldSessionRef?.absolutePath != refFile.absolutePath) oldSessionRef?.delete()
                                returningToPromptOnCancelCapture = false
                                aiCaptureTarget = AiCaptureTarget.ORIGINAL
                                captureButton.isEnabled = true
                                showAiPromptStage(resetPrompt = false)
                                scheduleAiOverlayRefresh()
                            }
                            return
                        }
                        val clean = aiSessionStore.newFile("miku_ai_original_", ".jpg")
                        PhotoComposer.prepareCleanPhoto(file, spec, clean)
                        val existingRef = if (returningToPromptOnCancelCapture) {
                            aiSession?.referenceFile?.takeIf { it.isFile && it.length() > 0L }?.let { ref ->
                                aiSessionStore.newFile("miku_ai_ref_", ".jpg").also { ref.copyTo(it, overwrite = true) }
                            }
                        } else null
                        val refCopy = existingRef ?: prepareDefaultReferenceCopy()
                        val settings = aiSettingsStore.load()
                        val task = createAiTransactionSnapshot(
                            source = clean,
                            captureTime = aiCaptureTimeAtShutter,
                            captureLocation = aiLocationAtShutter,
                            prompt = settings.defaultPrompt,
                            includeTime = settings.includeTimeWatermark,
                            includeLocation = settings.includeLocationWatermark,
                            configuration = AiGenerationConfiguration.from(settings),
                            createdAt = fileNameTimeAtShutter,
                            referenceSource = refCopy
                        )
                        val originalUri = PhotoComposer.saveFileToGallery(
                            this@MainActivity,
                            clean,
                            "origin",
                            "image/jpeg",
                            task.createdAt
                        )
                        val session = AiSession(
                            originalFile = clean,
                            captureTime = aiCaptureTimeAtShutter,
                            captureLocation = aiLocationAtShutter,
                            transactionId = task.id,
                            referenceFile = refCopy
                        )
                        val resetPromptFlag = !returningToPromptOnCancelCapture
                        runOnUiThread {
                            if (cameraMode != CameraMode.AI) {
                                removeTransactionWithoutBlocking(session)
                                return@runOnUiThread
                            }
                            aiSession?.let(::removeTransactionWithoutBlocking)
                            aiSession = session
                            returningToPromptOnCancelCapture = false
                            aiCaptureTarget = AiCaptureTarget.ORIGINAL
                            captureButton.isEnabled = true
                            updateRecentPhoto(originalUri)
                            persistAiSession(stage = "PROMPT", prompt = aiPromptEditText.text.toString().ifBlank { settings.defaultPrompt })
                            showAiPromptStage(resetPrompt = resetPromptFlag)
                            // CameraX is still unwinding its callback. Delay system-window work.
                            scheduleAiOverlayRefresh()
                        }
                    } else {
                        val uri = PhotoComposer.composeAndSave(
                            this@MainActivity,
                            file,
                            spec,
                            fileNameTimeAtShutter
                        )
                        runOnUiThread {
                            updateRecentPhoto(uri)
                            toast("已保存到相册")
                        }
                    }
                } catch (error: Throwable) {
                    runOnUiThread { toast("保存失败: ${error.message ?: "未知错误"}") }
                } finally {
                    file.delete()
                    runOnUiThread { if (aiStage == AiStage.CAPTURE || cameraMode == CameraMode.NORMAL) captureButton.isEnabled = true }
                }
            }
            override fun onError(exception: ImageCaptureException) {
                file.delete()
                runOnUiThread {
                    captureButton.isEnabled = true
                    toast("拍照失败: ${exception.message ?: "未知错误"}")
                }
            }
        })
    }

    /** Quick “iris close then open” on the shutter — shrink then spring back. */
    private fun playShutterApertureAnimation() {
        captureButton.animate().cancel()
        captureButton.scaleX = 1f
        captureButton.scaleY = 1f
        captureButton.animate()
            .scaleX(0.72f)
            .scaleY(0.72f)
            .setDuration(70L)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                captureButton.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(160L)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.6f))
                    .start()
            }
            .start()
    }

    private fun loadLatestPhoto() {
        cameraExecutor.execute {
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection: String?
            val selectionArgs: Array<String>?
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                selectionArgs = arrayOf("${Environment.DIRECTORY_PICTURES}/miku camera/%")
            } else {
                selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                selectionArgs = arrayOf("watermark_%", "miku_%")
            }
            val uri = runCatching {
                contentResolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Images.Media.DATE_ADDED} DESC"
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        ContentUris.withAppendedId(collection, cursor.getLong(0))
                    } else null
                }
            }.getOrNull()
            runOnUiThread { updateRecentPhoto(uri) }
        }
    }

    private fun updateRecentPhoto(uri: Uri?) {
        recentPhotoUri = uri
        recentPhotoView.setImageURI(uri)
    }

    private fun openRecentPhoto() {
        val uri = recentPhotoUri ?: return toast("还没有拍摄过照片")
        launchingInternalIntent = true
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }.onFailure {
            launchingInternalIntent = false
            toast("无法打开相册")
        }
    }

    private fun showWatermarkChooser() {
        val dialog = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).roundToInt(), (16 * density).roundToInt(), (20 * density).roundToInt(), bottomSafeMarginPx())
        }
        container.addView(TextView(this).apply {
            text = "选择水印"
            textSize = 20f
            setTextColor(MikuUi.ink)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, (10 * density).roundToInt())
        })
        val newButton = MaterialButton(this).apply {
            text = "+ 新建水印"
            setOnClickListener { dialog.dismiss(); startEditor(WatermarkPreset.newDraft()) }
        }
        container.addView(newButton, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(6) })
        store.loadAll().forEach { preset ->
            container.addView(buildWatermarkRow(preset, dialog))
        }
        MikuUi.styleTree(container)
        MikuUi.button(newButton, true)
        container.background = MikuUi.rounded(this, MikuUi.background, 24)
        dialog.setContentView(ScrollView(this).apply { addView(container) })
        dialog.show()
    }

    private fun buildWatermarkRow(preset: WatermarkPreset, dialog: BottomSheetDialog): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * density).roundToInt(), 0, (8 * density).roundToInt())
        }

        val thumbSize = (48 * density).roundToInt()
        val thumb = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(thumbSize, thumbSize).apply {
                marginEnd = (12 * density).roundToInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(MikuUi.soft)
                cornerRadius = 8 * density
            }
            contentDescription = "水印预览"
            setImageResource(android.R.color.transparent)
        }
        row.addView(thumb)
        loadBitmap(preset.imageUri) { bitmap ->
            if (bitmap != null) thumb.setImageBitmap(bitmap)
        }

        val nameButton = MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (12 * density).roundToInt()
            }
            text = (if (preset.id == this@MainActivity.overlay.currentPreset().id) "✓ " else "") + preset.name.ifBlank { "未命名水印" }
            setOnClickListener {
                dialog.dismiss()
                selectPreset(preset)
            }
        }
        row.addView(nameButton)

        val editButton = MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "编辑"
            setOnClickListener {
                dialog.dismiss()
                startEditor(preset)
            }
        }
        row.addView(editButton)
        return row
    }

    private fun selectPreset(preset: WatermarkPreset) {
        store.select(preset.id)
        loadBitmap(preset.imageUri) { bitmap ->
            val generated = overlay.setPreset(preset, bitmap)
            enterCameraMode()
            if (generated) persistCurrentPresetIfSaved()
            // GPS is already warm; only refresh on-screen text if 地点 is displayed.
            if (preset.showLocation) applyCachedLocationLabel()
        }
    }

    private fun restoreLastSelectedPreset() {
        val preset = store.loadSelected() ?: return
        loadBitmap(preset.imageUri) { bitmap ->
            val generated = overlay.setPreset(preset, bitmap)
            if (generated) persistCurrentPresetIfSaved()
            if (preset.showLocation) applyCachedLocationLabel()
        }
    }

    private fun installBundledMikuPreset() {
        val uri = Uri.parse(
            "android.resource://$packageName/drawable/default_watermark"
        ).toString()
        store.installBundledPresetIfNeeded(WatermarkPreset.builtinMiku(uri))
    }

    private fun startEditor(preset: WatermarkPreset) {
        editing = true
        editingExistingPreset = store.loadAll().any { it.id == preset.id }
        editingOriginalName = preset.name
        presetBeforeEdit = overlay.currentPreset()
        watermarkNameEditText.setText(preset.name)
        watermarkNameEditText.setSelection(watermarkNameEditText.text.length)
        // INVISIBLE keeps top-bar height so the 3:4 FOV never jumps.
        cameraControls.visibility = View.INVISIBLE
        captureControls.visibility = View.GONE
        editorControls.visibility = View.VISIBLE
        focusExposure.dismiss()
        applyControlRotation(physicalRotationDegrees)
        loadBitmap(preset.imageUri) { bitmap ->
            overlay.setPreset(preset, bitmap)
            overlay.setEditingEnabled(true)
            timeSwitch.isChecked = preset.showTime
            // Avoid listener side-effects fighting preset load.
            locationSwitch.setOnCheckedChangeListener(null)
            streetSwitch.setOnCheckedChangeListener(null)
            locationSwitch.isChecked = preset.showLocation
            streetSwitch.isChecked = preset.includeStreet
            updateStreetSwitchVisibility(preset.showLocation)
            locationSwitch.setOnCheckedChangeListener { _, checked ->
                // Display-only: do not start/stop GPS here.
                overlay.setShowLocation(checked)
                updateStreetSwitchVisibility(checked)
                if (checked) applyCachedLocationLabel()
            }
            streetSwitch.setOnCheckedChangeListener { _, checked ->
                overlay.setIncludeStreet(checked)
                applyCachedLocationLabel()
            }
            outlineSeekBar.progress = preset.outlinePx.toInt()
            if (preset.showLocation) applyCachedLocationLabel()
        }
        root.post { layoutViewfinder() }
    }

    private fun enterCameraMode() {
        editing = false
        editingExistingPreset = false
        editingOriginalName = ""
        presetBeforeEdit = null
        cameraControls.visibility = View.VISIBLE
        captureControls.visibility = View.VISIBLE
        editorControls.visibility = View.GONE
        overlay.setEditingEnabled(false)
        hideStatusBar()
        root.post { layoutViewfinder() }
        startLocationWarmup()
        if (overlay.currentPreset().showLocation) applyCachedLocationLabel()
    }

    private fun cancelEditor() {
        val previous = presetBeforeEdit
        if (previous == null) {
            enterCameraMode()
            return
        }
        loadBitmap(previous.imageUri) { bitmap ->
            overlay.setPreset(previous, bitmap)
            enterCameraMode()
        }
    }

    private fun saveCurrentPreset() {
        val current = overlay.currentPreset()
        val name = watermarkNameEditText.text.toString().trim().ifBlank {
            current.name.ifBlank {
                "水印 " + SimpleDateFormat("MMdd-HHmm", Locale.getDefault()).format(Date())
            }
        }
        // The overlay is the authoritative editing state. Update its name
        // before saving so later location/layout callbacks cannot overwrite
        // the stored preset with the previous blank name.
        overlay.setPresetName(name)
        val preset = overlay.currentPreset()
        if (!store.save(preset)) {
            toast("水印保存失败")
            return
        }
        store.select(preset.id)
        val saved = store.loadAll().firstOrNull { it.id == preset.id }
        if (saved?.name != name) {
            toast("水印名称保存失败")
            return
        }
        toast(if (editingExistingPreset) "水印已更新" else "水印已保存")
        presetBeforeEdit = null
        enterCameraMode()
    }

    private fun deleteCurrentPreset() {
        val preset = overlay.currentPreset()
        val editedName = watermarkNameEditText.text.toString().trim()
        val name = when {
            editingExistingPreset && editedName.isNotBlank() -> editedName
            editingExistingPreset && editingOriginalName.isNotBlank() -> editingOriginalName
            preset.name.isNotBlank() -> preset.name
            else -> "该水印"
        }
        if (!editingExistingPreset && store.loadAll().none { it.id == preset.id }) {
            // Brand-new draft: nothing in store — just discard.
            AlertDialog.Builder(this)
                .setTitle("删除水印")
                .setMessage("当前水印尚未保存，确定放弃编辑吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除") { _, _ ->
                    clearActiveWatermark()
                    toast("已放弃")
                }
                .show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("删除水印")
            .setMessage("确定删除「$name」吗？此操作不可恢复。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                store.delete(preset.id)
                clearActiveWatermark()
                toast("水印已删除")
            }
            .show()
    }

    private fun clearActiveWatermark() {
        presetBeforeEdit = null
        store.clearSelection()
        overlay.setPreset(WatermarkPreset(), null)
        enterCameraMode()
    }

    private fun loadBitmap(uriString: String?, onLoaded: (android.graphics.Bitmap?) -> Unit) {
        if (uriString.isNullOrBlank()) {
            onLoaded(null)
            return
        }
        cameraExecutor.execute {
            val bitmap = runCatching {
                contentResolver.openInputStream(android.net.Uri.parse(uriString))?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            runOnUiThread { onLoaded(bitmap) }
        }
    }

    /** Background GPS + reverse geocode. Always silent; independent of 地点 display switch. */
    private fun requestLocation(silent: Boolean = true) {
        setLocationStatusText("正在定位中")
        locationProvider.request { location ->
            if (location == null) {
                setLocationStatusText("定位失败")
                if (!silent) toast("暂时无法获取当前位置")
                return@request
            }
            cameraExecutor.execute {
                val address = runCatching {
                    if (Geocoder.isPresent()) {
                        @Suppress("DEPRECATION")
                        Geocoder(this, Locale.getDefault())
                            .getFromLocation(location.latitude, location.longitude, 1)
                            ?.firstOrNull()
                    } else null
                }.getOrNull()
                val fallback = "%.5f, %.5f".format(Locale.US, location.latitude, location.longitude)
                runOnUiThread {
                    lastAddress = address
                    lastLatLonFallback = fallback
                    applyCachedLocationLabel()
                }
            }
        }
    }

    /**
     * Rebuild location text from cache.
     * - Top bar: always 市+区+门牌/POI (not controlled by watermark 门牌 switch).
     * - Watermark: 市+区, plus POI only if the active preset has 门牌 on.
     */
    private fun applyCachedLocationLabel() {
        val statusLabel = formatLocationLabel(includePoi = true)
        if (!statusLabel.isNullOrBlank()) {
            currentLocation = statusLabel
            setLocationStatusText(statusLabel)
        }
        if (overlay.currentPreset().showLocation) {
            val watermarkLabel = formatLocationLabel(
                includePoi = overlay.currentPreset().includeStreet
            )
            if (!watermarkLabel.isNullOrBlank()) {
                overlay.setLocationText(watermarkLabel)
            }
        }
    }

    private fun formatLocationLabel(includePoi: Boolean): String? {
        return lastAddress?.let { LocationFormatter.format(it, includePoi) }
            ?.takeIf { it.isNotBlank() }
            ?: lastLatLonFallback
            ?: currentLocation.takeIf { it.isNotBlank() }
    }

    /** Middle slot only (weight=1); long text marquees horizontally, never covers side buttons. */
    private fun setLocationStatusText(text: String) {
        locationStatusView.text = text
        // Re-select so marquee restarts after text change.
        locationStatusView.isSelected = false
        locationStatusView.isSelected = true
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun requiredCameraPermissions() = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray()
    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        aiGenerationId++
        aiImageClient.cancel()
        aiExecutor.shutdownNow()
        // AI 会话文件由用户明确保存、重拍或放弃时清理。Activity 被系统销毁后，
        // restoreAiSessionIfNeeded() 需要这些文件来恢复提示词页或结果页。
        aiSession = null
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AI_TRANSACTION_ID = "ai_transaction_id"
    }
}
