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
import com.example.mikucamera.ai.AiSessionStore
import com.example.mikucamera.ai.AiTransaction
import com.example.mikucamera.ai.AiTransactionState
import com.example.mikucamera.ai.AiTransactionStore
import com.example.mikucamera.ai.AiApiProfile
import com.example.mikucamera.ai.AiPromptBuilder
import com.example.mikucamera.ai.AiOutfitStyle
import com.example.mikucamera.ai.AiServicePreset
import com.example.mikucamera.ai.AiSettingsStore
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

    private data class AiSession(
        val originalFile: File,
        var captureTime: String,
        var captureLocation: String,
        var resultFile: File? = null,
        var resultSaved: Boolean = false,
        var transactionId: String? = null
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
    private lateinit var flashIcon: TextView
    private lateinit var flashBadge: TextView
    private lateinit var switchCameraButton: ImageButton
    private lateinit var captureButton: ImageButton
    private lateinit var normalLeftTools: View
    private lateinit var aiLeftTools: View
    private lateinit var selectWatermarkButton: TextView
    private lateinit var aiModeButton: TextView
    private lateinit var aboutButton: TextView
    private lateinit var aiBackButton: TextView
    private lateinit var aiSettingsButton: TextView
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
    private lateinit var aiPageBackButton: TextView
    private lateinit var aiPageSettingsButton: TextView
    private lateinit var aiPromptPanel: View
    private lateinit var aiGeneratingPanel: View
    private lateinit var aiResultPanel: View
    private lateinit var aiResultSaveActions: View
    private lateinit var aiResultActions: View
    private lateinit var aiResultPostSaveActions: View
    private lateinit var aiOriginalPreview: ImageView
    private lateinit var aiResultPreview: ImageView
    private lateinit var aiPromptEditText: EditText
    private lateinit var aiMetadataText: TextView
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
    private var navBarInsetBottom = 0
    private var lockedBottomHeight = 0

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

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.CAMERA] != true) toast("需要相机权限才能拍照")
        continuePermissionSequence()
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        // GPS is independent of the watermark "地点" display switch.
        if (grants.values.any { it }) {
            requestLocation(silent = true)
        } else {
            toast("未授予定位权限，地点水印可能无法显示地址")
        }
        continuePermissionSequence()
    }
    private val photoPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
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
        bindActions()
        bindBackNavigation()
        enterCameraMode()
        installBundledMikuPreset()
        restoreLastSelectedPreset()
        loadLatestPhoto()
        restoreAiSessionIfNeeded()
        continuePermissionSequence()
        AiOverlayService.refresh(this)
        intent.getStringExtra(EXTRA_AI_TRANSACTION_ID)?.let(::openAiTransaction)
    }

    /** Immersive camera UI: status bar hidden so the viewfinder is not framed by system chrome. */
    private fun hideStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        val controller = WindowInsetsControllerCompat(window, window.decorView)
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
        aiResultPreview = findViewById(R.id.aiResultPreview)
        aiPromptEditText = findViewById(R.id.aiPromptEditText)
        aiMetadataText = findViewById(R.id.aiMetadataText)
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
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            navBarInsetBottom = bars.bottom
            val side = (16 * density).roundToInt()
            // Floating tools on preview; shutter padding stays inside the 9:16 stage
            // (nav-bar inset is absorbed by the outer letterbox, not the stage).
            cameraControls.setPadding(side, 0, side, (12 * density).roundToInt())
            val pad = (6 * density).roundToInt()
            captureControls.setPadding(side / 2, pad, side / 2, pad)
            editorControls.setPadding(side / 2, pad, side / 2, pad)
            root.post { layoutViewfinder() }
            insets
        }
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
     * [ black letterbox ]             ← equal top/bottom, outside the stage
     * [ 9:16 content stage ]          ← centered on screen, full width
     *     [ topBar: 🌸 / ⚡ ]         ← outside FOV
     *     [ 3:4 viewfinder ]          ← capture FOV only (所见即所得)
     *     [ bottom chrome ]           ← shutter / editor
     * [ black letterbox ]
     * ```
     */
    private fun layoutViewfinder() {
        val rootW = root.width
        val rootH = root.height
        if (rootW <= 0 || rootH <= 0) return

        // --- 9:16 stage, full width, vertically centered ---
        val stageW = rootW
        val stageHIdeal = (stageW * 16f / 9f).roundToInt()
        val stageH = stageHIdeal.coerceAtMost(rootH)
        val stageTop = ((rootH - stageH) / 2f).roundToInt()

        contentStage.layoutParams = FrameLayout.LayoutParams(stageW, stageH).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = stageTop
        }

        // --- Top tool bar (outside FOV) ---
        val widthSpec = View.MeasureSpec.makeMeasureSpec(stageW, View.MeasureSpec.EXACTLY)
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
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
        val maxVfH = (stageH - topH - bottomH).coerceAtLeast(1)
        val vfH = idealVfH.coerceAtMost(maxVfH)

        cameraControls.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            topH
        ).apply {
            gravity = Gravity.TOP
        }

        viewfinderHost.layoutParams = FrameLayout.LayoutParams(vfW, vfH).apply {
            gravity = Gravity.TOP
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
            bottomH
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
            aiPhotoPicker.launch(
                androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        recentPhotoView.setOnClickListener { openRecentPhoto() }
        selectWatermarkButton.setOnClickListener { showWatermarkChooser() }
        aiModeButton.setOnClickListener { requestEnterAiMode() }
        aboutButton.setOnClickListener { showAboutDialog() }
        aiBackButton.setOnClickListener { requestExitAiMode() }
        aiSettingsButton.setOnClickListener { showAiSettingsHome() }
        aiPageBackButton.setOnClickListener { requestExitAiMode() }
        aiPageSettingsButton.setOnClickListener { showAiSettingsHome() }
        findViewById<MaterialButton>(R.id.aiRetakeButton).setOnClickListener { returnToAiCapture() }
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
        aiTimeEditText.setOnClickListener { editAiMetadata(time = true) }
        aiLocationEditText.setOnClickListener { editAiMetadata(time = false) }
        findViewById<MaterialButton>(R.id.uploadButton).setOnClickListener {
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
                    requestExitAiMode()
                } else if (editing) {
                    cancelEditor()
                } else {
                    finish()
                }
            }
        })
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
        aiStage = AiStage.CAPTURE
        editing = false
        aiPageHost.visibility = View.GONE
        cameraControls.visibility = View.VISIBLE
        viewfinderHost.visibility = View.VISIBLE
        bottomChrome.visibility = View.VISIBLE
        captureControls.visibility = View.VISIBLE
        editorControls.visibility = View.GONE
        normalLeftTools.visibility = View.GONE
        aiLeftTools.visibility = View.VISIBLE
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
        updateAiMetadataLabel()
        updateAiWorkflow(AiStage.PROMPT)
        persistAiSession(stage = "PROMPT", prompt = aiPromptEditText.text.toString())
    }

    private fun startAiGeneration() {
        val session = aiSession ?: return toast("请先拍摄照片")
        val settings = aiSettingsStore.load()
        val profile = settings.activeProfile
        if (!profile.preset.supportsCurrentProtocol) {
            toast("${profile.preset.displayName} 预设需要专用接口适配，当前版本暂不能直接生成")
            showAiApiProfilesDialog()
            return
        }
        if (settings.apiKey.isBlank()) {
            toast("请先设置 API Key")
            showAiSettingsHome()
            return
        }
        val taskId = ensureAiTransaction(session)
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
                resultSaved = false
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
        val prompt = session.transactionId?.let { aiTransactionStore.get(it)?.prompt }.orEmpty()
        aiGenerationStatusText.text = "正在准备新的提示词任务"
        aiExecutor.execute {
            try {
                val copy = aiSessionStore.newFile("miku_ai_original_", ".jpg")
                session.originalFile.copyTo(copy, overwrite = true)
                runOnUiThread {
                    // The original task keeps its own file and continues in the background.
                    aiSessionStore.clear(deleteFiles = false)
                    aiSession = AiSession(copy, session.captureTime, session.captureLocation)
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
        aiSessionStore.clear(deleteFiles = false)
        aiSession = null
        aiPromptEditText.text?.clear()
        enterAiCaptureStage()
    }

    private fun editPromptFromResult() {
        val session = aiSession ?: return
        setAiResultButtonsEnabled(false)
        aiExecutor.execute {
            try {
                val originalCopy = aiSessionStore.newFile("miku_ai_original_", ".jpg")
                session.originalFile.copyTo(originalCopy, overwrite = true)
                val task = AiTransaction(
                    originalPath = originalCopy.absolutePath,
                    captureTime = session.captureTime,
                    captureLocation = session.captureLocation,
                    prompt = aiPromptEditText.text.toString().ifBlank { aiSettingsStore.load().defaultPrompt },
                    includeTimeWatermark = aiTimeWatermarkSwitch.isChecked,
                    includeLocationWatermark = aiLocationWatermarkSwitch.isChecked
                )
                aiTransactionStore.create(task)
                runOnUiThread {
                    session.transactionId?.let { aiTransactionStore.remove(it) }
                    deleteAiSessionFiles(session)
                    aiSessionStore.clear(deleteFiles = false)
                    aiSession = AiSession(originalCopy, session.captureTime, session.captureLocation, transactionId = task.id)
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
            session.transactionId?.let { aiTransactionStore.remove(it) }
            deleteAiSessionFiles(session)
        }
        aiSessionStore.clear()
        aiSession = null
        AiOverlayService.refresh(this)
        aiPromptEditText.text?.clear()
        enterAiCaptureStage()
    }

    private fun saveAiSelection(saveOriginal: Boolean, saveResult: Boolean) {
        val session = aiSession ?: return toast("没有可保存的照片")
        if (saveResult && session.resultFile == null) return toast("AI 图片尚未生成")
        setAiResultButtonsEnabled(false)
        aiPageBackButton.isEnabled = false
        aiPageSettingsButton.isEnabled = false
        aiPageSettingsButton.alpha = 0.4f
        aiExecutor.execute {
            try {
                var latest: Uri? = null
                if (saveOriginal) {
                    latest = PhotoComposer.saveFileToGallery(
                        this, session.originalFile, "miku_original", "image/jpeg"
                    )
                }
                if (saveResult) {
                    latest = PhotoComposer.saveFileToGallery(
                        this,
                        session.resultFile!!,
                        "miku_ai",
                        AiImageClient.detectImageMime(session.resultFile!!)
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
                    aiPageSettingsButton.isEnabled = true
                    aiPageSettingsButton.alpha = 1f
                    toast("AI 图片已保存")
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    setAiResultButtonsEnabled(true)
                    aiPageBackButton.isEnabled = true
                    aiPageSettingsButton.isEnabled = true
                    aiPageSettingsButton.alpha = 1f
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
    }

    override fun onStop() {
        unregisterReceiver(transactionReceiver)
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
        aiPageSettingsButton.isEnabled = false
        aiPageSettingsButton.alpha = 0.4f
        updateAiWorkflow(AiStage.GENERATING)
    }

    private fun ensureAiTransaction(session: AiSession): String {
        session.transactionId?.let { return it }
        val settings = aiSettingsStore.load()
        return aiTransactionStore.create(
            AiTransaction(
                originalPath = session.originalFile.absolutePath,
                captureTime = session.captureTime,
                captureLocation = session.captureLocation,
                prompt = aiPromptEditText.text.toString().ifBlank { settings.defaultPrompt },
                includeTimeWatermark = aiTimeWatermarkSwitch.isChecked,
                includeLocationWatermark = aiLocationWatermarkSwitch.isChecked
            )
        ).id.also { session.transactionId = it }
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

    private fun updateAiResultActions() {
        aiResultSaveActions.visibility = View.VISIBLE
        aiResultActions.visibility = View.GONE
        aiResultPostSaveActions.visibility = View.GONE
    }

    private fun requestExitAiMode() {
        when (aiStage) {
            AiStage.CAPTURE -> exitAiMode(deleteSession = true)
            AiStage.GENERATING -> AlertDialog.Builder(this)
                .setTitle("退出 AI mode")
                .setMessage("是否停止等待并放弃本次照片？服务端若已开始生成，本次请求仍可能产生费用。")
                .setNegativeButton("继续等待", null)
                .setPositiveButton("退出") { _, _ ->
                    aiGenerationId++
                    aiImageClient.cancel()
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
            aiSession?.let { session ->
                session.transactionId?.let { aiTransactionStore.remove(it) }
                deleteAiSessionFiles(session)
            }
            aiSessionStore.clear()
            AiOverlayService.refresh(this)
        }
        aiSession = null
        aiGenerationId++
        cameraMode = CameraMode.NORMAL
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
            view.setTextColor(if (active || complete) Color.parseColor("#62D5C6") else Color.parseColor("#77FFFFFF"))
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
            if (generatingOrResult) Color.parseColor("#62D5C6") else Color.parseColor("#55FFFFFF")
        )
    }

    private fun showAiSettingsHome() {
        showAiImageSettingsDialog()
    }

    private fun showAiImageSettingsDialog() {
        val current = aiSettingsStore.load()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).roundToInt(), 0, (20 * density).roundToInt(), 0)
        }
        val visualStyleGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
        }
        val animeButton = RadioButton(this).apply {
            id = View.generateViewId()
            text = AiVisualStyle.ANIME.displayName
        }
        val realisticButton = RadioButton(this).apply {
            id = View.generateViewId()
            text = AiVisualStyle.REALISTIC.displayName
        }
        visualStyleGroup.addView(animeButton)
        visualStyleGroup.addView(realisticButton)
        visualStyleGroup.check(if (current.visualStyle == AiVisualStyle.ANIME) animeButton.id else realisticButton.id)

        val outfitStyleGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
        }
        val officialOutfitButton = RadioButton(this).apply {
            id = View.generateViewId()
            text = AiOutfitStyle.OFFICIAL.displayName
        }
        val adaptiveOutfitButton = RadioButton(this).apply {
            id = View.generateViewId()
            text = AiOutfitStyle.SCENE_ADAPTIVE.displayName
        }
        outfitStyleGroup.addView(officialOutfitButton)
        outfitStyleGroup.addView(adaptiveOutfitButton)
        outfitStyleGroup.check(
            if (current.outfitStyle == AiOutfitStyle.OFFICIAL) officialOutfitButton.id else adaptiveOutfitButton.id
        )
        val promptInput = EditText(this).apply {
            hint = "默认提示词"
            setText(current.defaultPrompt)
            gravity = Gravity.TOP or Gravity.START
            minLines = 4
            maxLines = 8
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        container.addView(TextView(this).apply { text = "形象风格"; setPadding(0, 20, 0, 0) })
        container.addView(visualStyleGroup)
        container.addView(TextView(this).apply { text = "服装风格"; setPadding(0, 16, 0, 0) })
        container.addView(outfitStyleGroup)
        container.addView(TextView(this).apply { text = "默认提示词"; setPadding(0, 20, 0, 0) })
        container.addView(promptInput)
        val timeSwitch = SwitchMaterial(this).apply { text = "生成时间水印"; isChecked = current.includeTimeWatermark }
        val locationSwitch = SwitchMaterial(this).apply { text = "生成地点水印"; isChecked = current.includeLocationWatermark }
        container.addView(TextView(this).apply { text = "AI 水印"; setPadding(0, 20, 0, 0) })
        container.addView(timeSwitch)
        container.addView(locationSwitch)
        container.addView(TextView(this).apply {
            text = "当前 API 配置：${current.activeProfile.name} · ${current.activeProfile.model}"
            textSize = 12f
            setPadding(0, 16, 0, 0)
        })
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            addView(container)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("图像参数")
            .setView(scrollView)
            .setNegativeButton("取消", null)
            .setNeutralButton("API 配置", null)
            .setPositiveButton("保存", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val prompt = promptInput.text.toString().trim().ifBlank { AiSettingsStore.DEFAULT_PROMPT }
                val visualStyle = if (visualStyleGroup.checkedRadioButtonId == realisticButton.id) {
                    AiVisualStyle.REALISTIC
                } else AiVisualStyle.ANIME
                val outfitStyle = if (outfitStyleGroup.checkedRadioButtonId == officialOutfitButton.id) {
                    AiOutfitStyle.OFFICIAL
                } else AiOutfitStyle.SCENE_ADAPTIVE
                runCatching { aiSettingsStore.saveImageSettings(visualStyle, outfitStyle, prompt, timeSwitch.isChecked, locationSwitch.isChecked) }
                    .onSuccess {
                        updateAiMetadataLabel()
                        toast("AI 设置已保存")
                        dialog.dismiss()
                    }
                    .onFailure { toast("设置保存失败: ${it.message ?: "未知错误"}") }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { dialog.dismiss(); showAiApiProfilesDialog() }
        }
        dialog.show()
    }

    private fun showAiApiProfilesDialog() {
        val settings = aiSettingsStore.load()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).roundToInt(), 0, (20 * density).roundToInt(), 0)
        }
        container.addView(TextView(this).apply {
            text = "点击圆圈切换当前使用的 API 配置；点击右侧名称区域进入编辑。API Key 会使用 Android Keystore 加密保存。"
            textSize = 13f
            setPadding(0, 0, 0, (12 * density).roundToInt())
        })
        lateinit var dialog: AlertDialog
        settings.profiles.forEach { profile ->
            val isActive = profile.id == settings.activeProfileId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * density).roundToInt(), (10 * density).roundToInt(), (12 * density).roundToInt(), (10 * density).roundToInt())
                setBackgroundColor(if (isActive) Color.parseColor("#183F776E") else Color.TRANSPARENT)
            }
            val checkbox = TextView(this).apply {
                text = if (isActive) "✓" else "○"
                textSize = 18f
                setPadding(0, 0, (10 * density).roundToInt(), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (isActive) return@setOnClickListener
                    aiSettingsStore.saveProfiles(settings.profiles, profile.id)
                    updateAiMetadataLabel()
                    dialog.dismiss()
                    showAiApiProfilesDialog()
                }
            }
            val textBlock = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                isClickable = true
                isFocusable = true
                setOnClickListener { showAiApiProfileEditor(profile) }
            }
            val nameView = TextView(this).apply {
                text = profile.name
                textSize = 16f
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val subView = TextView(this).apply {
                text = profile.preset.displayName + " · " + profile.model
                textSize = 13f
                setTextColor(Color.parseColor("#888888"))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            textBlock.addView(nameView)
            textBlock.addView(subView)
            row.addView(checkbox)
            row.addView(textBlock, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            container.addView(row)
        }
        val scroll = ScrollView(this).apply { addView(container) }
        dialog = AlertDialog.Builder(this)
            .setTitle("API 配置")
            .setView(scroll)
            .setNegativeButton("返回图像参数") { _, _ -> showAiImageSettingsDialog() }
            .setNeutralButton("新增配置") { _, _ -> showAiApiProfileEditor(null) }
            .show()
    }

    private fun showAiApiProfileEditor(existing: AiApiProfile?) {
        val selected = existing ?: AiApiProfile(name = "OpenAI", preset = AiServicePreset.OPENAI,
            baseUrl = AiServicePreset.OPENAI.defaultBaseUrl, endpoint = AiServicePreset.OPENAI.defaultEndpoint, apiKey = "", model = AiServicePreset.OPENAI.defaultModel)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).roundToInt(), 0, (20 * density).roundToInt(), 0)
        }
        fun input(value: String, hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText = EditText(this).apply {
            setText(value); this.hint = hint; inputType = type; setSingleLine(true); setSelection(text.length)
        }
        val nameInput = input(selected.name, "例如：OpenAI 主账号")
        val presetSpinner = Spinner(this)
        val presets = AiServicePreset.entries.toList()
        presetSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, presets.map { it.displayName })
        presetSpinner.setSelection(presets.indexOf(selected.preset).coerceAtLeast(0))
        val baseUrlInput = input(selected.baseUrl, "https://...", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val endpointInput = input(selected.endpoint, "/images/edits")
        val keyInput = input(selected.apiKey, "API Key", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val modelInput = input(selected.model, "模型名称")
        val modelHint = TextView(this).apply { textSize = 12f; setTextColor(Color.parseColor("#888888")); setPadding(0, (4 * density).roundToInt(), 0, 0) }
        val hint = TextView(this).apply { textSize = 12f; setPadding(0, (12 * density).roundToInt(), 0, 0) }
        fun updatePresetHint(fillDefaults: Boolean) {
            val preset = presets[presetSpinner.selectedItemPosition]
            if (fillDefaults) {
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
        container.addView(TextView(this).apply { text = "配置名称" }); container.addView(nameInput)
        container.addView(TextView(this).apply { text = "服务类型 / 预设"; setPadding(0, 16, 0, 0) }); container.addView(presetSpinner)
        container.addView(TextView(this).apply { text = "Base URL"; setPadding(0, 16, 0, 0) }); container.addView(baseUrlInput)
        container.addView(TextView(this).apply { text = "接口"; setPadding(0, 16, 0, 0) }); container.addView(endpointInput)
        container.addView(TextView(this).apply { text = "API Key"; setPadding(0, 16, 0, 0) }); container.addView(keyInput)
        container.addView(TextView(this).apply { text = "模型"; setPadding(0, 16, 0, 0) }); container.addView(modelInput); container.addView(modelHint); container.addView(hint)
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "新增 API 配置" else "编辑 API 配置")
            .setView(ScrollView(this).apply { addView(container) }).setNegativeButton("取消", null)
            .setNeutralButton(if (existing == null) "" else "删除", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val preset = presets[presetSpinner.selectedItemPosition]
                val updated = selected.copy(name = nameInput.text.toString(), preset = preset, baseUrl = baseUrlInput.text.toString(),
                    endpoint = endpointInput.text.toString(), apiKey = keyInput.text.toString(), model = modelInput.text.toString())
                val current = aiSettingsStore.load()
                val profiles = if (existing == null) current.profiles + updated else current.profiles.map { if (it.id == updated.id) updated else it }
                runCatching { aiSettingsStore.saveProfiles(profiles, updated.id) }.onSuccess {
                    updateAiMetadataLabel(); toast("API 配置已保存"); dialog.dismiss(); showAiApiProfilesDialog()
                }.onFailure { toast(it.message ?: "API 配置无效") }
            }
            if (existing != null) dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val current = aiSettingsStore.load()
                if (current.profiles.size == 1) { toast("至少保留一组 API 配置"); return@setOnClickListener }
                val profiles = current.profiles.filterNot { it.id == existing.id }
                aiSettingsStore.saveProfiles(profiles, profiles.first().id)
                updateAiMetadataLabel(); dialog.dismiss(); showAiApiProfilesDialog()
            } else dialog.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = View.GONE
        }
        dialog.show()
    }

    private fun showAboutDialog() {
        val primaryTextColor = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val secondaryTextColor = Color.argb(190, Color.red(primaryTextColor), Color.green(primaryTextColor), Color.blue(primaryTextColor))
        val mutedTextColor = Color.argb(130, Color.red(primaryTextColor), Color.green(primaryTextColor), Color.blue(primaryTextColor))
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
            setTextColor(primaryTextColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        container.addView(TextView(this).apply {
            text = "作者：zakee"
            textSize = 15f
            setTextColor(secondaryTextColor)
            setPadding(0, (8 * density).roundToInt(), 0, 0)
        })
        container.addView(TextView(this).apply {
            text = "miku camera 是一款以 Miku 为主角的 Android 相机。普通模式可将 Miku 水印、时间与地点自然叠加在照片中；AI mode 会结合人物或风景重新创作，让 Miku 与现实画面互动融合，并生成有趣可爱的专属时间地点水印。"
            textSize = 14f
            setTextColor(secondaryTextColor)
            setLineSpacing(0f, 1.15f)
            setPadding(0, (14 * density).roundToInt(), 0, (22 * density).roundToInt())
        })
        container.addView(aboutLinkButton("个人主页", "https://zakee.fun"))
        container.addView(aboutLinkButton("Buy me a coffee", "https://ifdian.net/a/zakee/plan"))
        container.addView(TextView(this).apply {
            val versionName = packageManager.getPackageInfo(packageName, 0).versionName
            text = "版本 $versionName"
            textSize = 12f
            gravity = Gravity.CENTER_HORIZONTAL
            setTextColor(mutedTextColor)
            setPadding(0, (10 * density).roundToInt(), 0, 0)
        })

        AlertDialog.Builder(this)
            .setTitle("关于")
            .setView(container)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun aboutLinkButton(label: String, url: String): View {
        return MaterialButton(this).apply {
            text = label
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener { openExternalUrl(url) }
        }
    }

    private fun resolveThemeColor(attribute: Int): Int {
        val value = TypedValue()
        if (!theme.resolveAttribute(attribute, value, true)) return Color.WHITE
        return if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
    }

    private fun openExternalUrl(url: String) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure { toast("没有可打开网页的应用") }
    }

    private fun updateAiMetadataLabel() {
        if (!::aiMetadataText.isInitialized) return
        val session = aiSession ?: return
        val settings = aiSettingsStore.load()
        aiTimeEditText.text = "时间：${session.captureTime}  ✎"
        aiLocationEditText.text = "地点：${session.captureLocation.ifBlank { "未获取到地点" }}  ✎"
        aiMetadataText.text = "形象：${settings.visualStyle.displayName}\n" +
                "服装：${settings.outfitStyle.displayName}"
    }

    private fun editAiMetadata(time: Boolean) {
        val session = aiSession ?: return
        val input = EditText(this).apply { setText(if (time) session.captureTime else session.captureLocation) }
        AlertDialog.Builder(this)
            .setTitle(if (time) "编辑拍摄时间" else "编辑拍摄地点")
            .setView(input)
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
                transactionId = session.transactionId
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
            transactionId = snapshot.transactionId
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
        } else {
            locationPermission.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    private fun continuePermissionSequence() {
        when {
            !hasLocationPermission() && !locationPermissionRequested -> {
                locationPermissionRequested = true
                locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
            !hasCameraPermission() && !cameraPermissionRequested -> {
                cameraPermissionRequested = true
                cameraPermission.launch(requiredCameraPermissions())
            }
            !hasPhotoPermission() && !photoPermissionRequested -> {
                photoPermissionRequested = true
                photoPermission.launch(photoPermissionName())
            }
            !Settings.canDrawOverlays(this) && !overlayPromptShown -> {
                overlayPromptShown = true
                AlertDialog.Builder(this)
                    .setTitle("开启悬浮窗功能")
                    .setMessage("AI 事务可在后台继续处理。开启悬浮窗后可随时查看进行中、成功和失败的任务。")
                    .setNegativeButton("暂不") { _, _ -> startCamera(); startLocationWarmup() }
                    .setPositiveButton("去开启") { _, _ ->
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
        captureButton.isEnabled = false
        cameraExecutor.execute {
            try {
                val imported = aiSessionStore.newFile("miku_ai_original_", ".jpg")
                contentResolver.openInputStream(uri)?.use { input -> imported.outputStream().use { input.copyTo(it) } }
                    ?: throw IllegalArgumentException("无法读取照片")
                val time = formatAiCaptureTime()
                val location = formatLocationLabel(includePoi = true).orEmpty()
                val settings = aiSettingsStore.load()
                val task = aiTransactionStore.create(AiTransaction(
                    originalPath = imported.absolutePath, captureTime = time, captureLocation = location,
                    prompt = settings.defaultPrompt, includeTimeWatermark = settings.includeTimeWatermark,
                    includeLocationWatermark = settings.includeLocationWatermark
                ))
                runOnUiThread {
                    aiSession?.let { old -> old.transactionId?.let { aiTransactionStore.remove(it) }; deleteAiSessionFiles(old) }
                    aiSession = AiSession(imported, time, location, transactionId = task.id)
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

    private fun openAiTransaction(id: String) {
        val task = aiTransactionStore.get(id) ?: return toast("该事务已被清除")
        aiSession = AiSession(task.originalFile, task.captureTime, task.captureLocation, task.resultFile, task.resultSaved, task.id)
        cameraMode = CameraMode.AI
        aiPromptEditText.setText(task.prompt)
        aiTimeWatermarkSwitch.isChecked = task.includeTimeWatermark
        aiLocationWatermarkSwitch.isChecked = task.includeLocationWatermark
        when (task.state) {
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
        flashButton.alpha = if (hasFlash) 1f else 0.4f
        flashIcon.text = "⚡"
        // Badge is layout-tied to the ⚡ glyph (not the 44dp circle). Nudge so it touches.
        fun placeBadge(touchDxDp: Float, touchDyDp: Float, sizeSp: Float) {
            flashBadge.translationX = touchDxDp * density
            flashBadge.translationY = touchDyDp * density
            flashBadge.textSize = sizeSp
            val lp = (flashBadge.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = 0
                bottomMargin = 0
            }
            flashBadge.layoutParams = lp
        }
        when {
            !hasFlash || flashMode == ImageCapture.FLASH_MODE_OFF -> {
                flashBadge.visibility = View.VISIBLE
                flashBadge.text = "❌"
                flashBadge.setTextColor(Color.parseColor("#FF5252"))
                // Sit on the lower-right tip of the bolt.
                placeBadge(touchDxDp = 1f, touchDyDp = 0f, sizeSp = 10f)
            }
            flashMode == ImageCapture.FLASH_MODE_ON -> {
                flashBadge.visibility = View.GONE
            }
            else -> {
                flashBadge.visibility = View.VISIBLE
                flashBadge.text = "A"
                flashBadge.setTextColor(Color.WHITE)
                placeBadge(touchDxDp = 2f, touchDyDp = 1f, sizeSp = 10f)
            }
        }
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
                        val clean = aiSessionStore.newFile("miku_ai_original_", ".jpg")
                        PhotoComposer.prepareCleanPhoto(file, spec, clean)
                        val originalUri = PhotoComposer.saveFileToGallery(
                            this@MainActivity, clean, "miku_original", "image/jpeg"
                        )
                        val task = aiTransactionStore.create(AiTransaction(
                            originalPath = clean.absolutePath,
                            captureTime = aiCaptureTimeAtShutter,
                            captureLocation = aiLocationAtShutter,
                            prompt = aiSettingsStore.load().defaultPrompt,
                            includeTimeWatermark = aiSettingsStore.load().includeTimeWatermark,
                            includeLocationWatermark = aiSettingsStore.load().includeLocationWatermark
                        ))
                        val session = AiSession(
                            originalFile = clean,
                            captureTime = aiCaptureTimeAtShutter,
                            captureLocation = aiLocationAtShutter,
                            transactionId = task.id
                        )
                        runOnUiThread {
                            if (cameraMode != CameraMode.AI) {
                                deleteAiSessionFiles(session)
                                return@runOnUiThread
                            }
                            aiSession?.let { previous ->
                                previous.transactionId?.let { aiTransactionStore.remove(it) }
                                deleteAiSessionFiles(previous)
                            }
                            aiSession = session
                            captureButton.isEnabled = true
                            updateRecentPhoto(originalUri)
                            persistAiSession(stage = "PROMPT", prompt = aiSettingsStore.load().defaultPrompt)
                            showAiPromptStage(resetPrompt = true)
                            // CameraX is still unwinding its callback. Delay system-window work.
                            scheduleAiOverlayRefresh()
                        }
                    } else {
                        val uri = PhotoComposer.composeAndSave(this@MainActivity, file, spec)
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
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        }.onFailure { toast("无法打开相册") }
    }

    private fun showWatermarkChooser() {
        val dialog = BottomSheetDialog(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).roundToInt(), (12 * density).roundToInt(), (20 * density).roundToInt(), (20 * density).roundToInt())
        }
        container.addView(TextView(this).apply {
            text = "水印"
            textSize = 20f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, (12 * density).roundToInt())
        })
        val newButton = MaterialButton(this).apply {
            text = "+ 新建水印"
            setOnClickListener { dialog.dismiss(); startEditor(WatermarkPreset.newDraft()) }
        }
        container.addView(newButton)
        store.loadAll().forEach { preset ->
            container.addView(buildWatermarkRow(preset, dialog))
        }
        // Bottom sheet content often sits on a light surface; force a dark panel.
        container.setBackgroundColor(Color.parseColor("#FF1C1C1C"))
        dialog.setContentView(container)
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
                setColor(Color.parseColor("#33FFFFFF"))
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
            text = preset.name.ifBlank { "未命名水印" }
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
