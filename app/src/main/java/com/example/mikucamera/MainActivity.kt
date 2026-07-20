package com.example.mikucamera

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Rational
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import com.example.mikucamera.data.PresetStore
import com.example.mikucamera.location.LocationProvider
import com.example.mikucamera.model.WatermarkPreset
import com.example.mikucamera.ui.FocusExposureView
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
    private lateinit var selectWatermarkButton: TextView
    private lateinit var recentPhotoView: ImageView
    private lateinit var timeSwitch: SwitchMaterial
    private lateinit var locationSwitch: SwitchMaterial
    private lateinit var outlineSeekBar: android.widget.SeekBar
    private lateinit var focusExposure: FocusExposureView
    private val store by lazy { PresetStore(this) }
    private val locationProvider by lazy { LocationProvider(this) }
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var physicalRotationDegrees = 0
    private var currentLocation = ""
    private var editing = false
    private var editingExistingPreset = false
    private var editingOriginalName = ""
    private var presetBeforeEdit: WatermarkPreset? = null
    private var recentPhotoUri: Uri? = null
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
        if (grants[Manifest.permission.CAMERA] == true) startCamera() else toast("需要相机权限才能拍照")
    }
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) requestLocation() else {
            locationSwitch.isChecked = false
            toast("未授予定位权限，将不会记录地点")
        }
    }
    private val pngPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        cameraExecutor.execute {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            runOnUiThread {
                if (bitmap == null) toast("PNG 读取失败") else overlay.setUploadedImage(uri.toString(), bitmap)
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
        enterCameraMode()
        loadLatestPhoto()
        if (!hasCameraPermission()) cameraPermission.launch(requiredCameraPermissions()) else startCamera()
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
        selectWatermarkButton = findViewById(R.id.selectWatermarkButton)
        recentPhotoView = findViewById(R.id.recentPhotoView)
        timeSwitch = findViewById(R.id.timeSwitch)
        locationSwitch = findViewById(R.id.locationSwitch)
        outlineSeekBar = findViewById(R.id.outlineSeekBar)
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
            editorControls.measuredHeight.coerceAtMost((140 * density).roundToInt()),
            (88 * density).roundToInt()
        )
        lockedBottomHeight = bottomH

        // --- 3:4 viewfinder between topBar and bottomChrome (fills the middle) ---
        val vfW = stageW
        val idealVfH = (vfW * 4f / 3f).roundToInt()
        val maxVfH = (stageH - topH - bottomH).coerceAtLeast(1)
        // Prefer true 3:4; if stage is tight, use all remaining middle space.
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
        recentPhotoView.setOnClickListener { openRecentPhoto() }
        selectWatermarkButton.setOnClickListener { showWatermarkChooser() }
        findViewById<MaterialButton>(R.id.uploadButton).setOnClickListener {
            pngPicker.launch(arrayOf("image/png"))
        }
        findViewById<MaterialButton>(R.id.cancelEditButton).setOnClickListener { cancelEditor() }
        findViewById<MaterialButton>(R.id.saveWatermarkButton).setOnClickListener { saveCurrentPreset() }
        outlineSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                overlay.setOutlinePx(value.toFloat())
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
        })
        timeSwitch.setOnCheckedChangeListener { _, checked -> overlay.setShowTime(checked) }
        locationSwitch.setOnCheckedChangeListener { _, checked ->
            overlay.setShowLocation(checked)
            if (checked) {
                if (hasLocationPermission()) requestLocation() else locationPermission.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }
    }

    private fun handlePreviewTouch(event: MotionEvent): Boolean {
        if (editing) return false
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
        // Badge glued to bolt bottom-right: ❌ = off, A = auto, hidden = on.
        fun placeBadge() {
            val lp = (flashBadge.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                // Negative margins pull the badge onto the ⚡ glyph.
                marginEnd = (-2 * density).roundToInt()
                bottomMargin = (-1 * density).roundToInt()
            }
            flashBadge.layoutParams = lp
        }
        when {
            !hasFlash || flashMode == ImageCapture.FLASH_MODE_OFF -> {
                flashBadge.visibility = View.VISIBLE
                flashBadge.text = "❌"
                flashBadge.setTextColor(Color.parseColor("#FF5252"))
                flashBadge.textSize = 9f
                placeBadge()
            }
            flashMode == ImageCapture.FLASH_MODE_ON -> {
                flashBadge.visibility = View.GONE
            }
            else -> {
                flashBadge.visibility = View.VISIBLE
                flashBadge.text = "A"
                flashBadge.setTextColor(Color.WHITE)
                flashBadge.textSize = 9f
                placeBadge()
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
        val generated = overlay.setPhysicalRotation(visualRotation, degrees)
        applyControlRotation(degrees)
        // Capture stays on ROTATION_0 with the viewfinder ViewPort; orientation
        // is applied in PhotoComposer so FOV stays locked to the preview.
        if (focusExposure.visibility == View.VISIBLE) {
            focusExposure.rotation = visualRotation.toFloat()
        }
        if (generated && !editing) persistCurrentPresetIfSaved()
    }

    private fun applyControlRotation(degrees: Int) {
        val rotation = ((360 - degrees) % 360).toFloat()
        // Only camera chrome follows device orientation. Editor controls
        // (upload / switches / seekbar / save) stay upright for readability.
        listOf<View>(
            selectWatermarkButton,
            flashButton,
            recentPhotoView,
            captureButton,
            switchCameraButton
        ).forEach { it.rotation = rotation }

        listOf<View>(
            findViewById(R.id.uploadButton),
            timeSwitch,
            locationSwitch,
            outlineSeekBar,
            findViewById(R.id.cancelEditButton),
            findViewById(R.id.saveWatermarkButton)
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
        if (editingExistingPreset) {
            current.name = editingOriginalName.ifBlank { current.name }
            store.save(current)
            return
        }
        if (store.loadAll().any { it.id == current.id }) {
            store.save(current)
        }
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return toast("相机尚未准备好")
        captureButton.isEnabled = false
        playShutterApertureAnimation()
        val file = File.createTempFile("capture_", ".jpg", cacheDir)
        val spec = overlay.renderSpec()
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                try {
                    val uri = PhotoComposer.composeAndSave(this@MainActivity, file, spec)
                    runOnUiThread {
                        updateRecentPhoto(uri)
                        toast("已保存到相册")
                    }
                } catch (error: Throwable) {
                    runOnUiThread { toast("保存失败: ${error.message ?: "未知错误"}") }
                } finally {
                    file.delete()
                    runOnUiThread { captureButton.isEnabled = true }
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
                selectionArgs = arrayOf("${Environment.DIRECTORY_PICTURES}/水印相机/%")
            } else {
                selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
                selectionArgs = arrayOf("watermark_%")
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
            setOnClickListener { dialog.dismiss(); startEditor(WatermarkPreset()) }
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
        loadBitmap(preset.imageUri) { bitmap ->
            val generated = overlay.setPreset(preset, bitmap)
            enterCameraMode()
            if (generated) persistCurrentPresetIfSaved()
        }
    }

    private fun startEditor(preset: WatermarkPreset) {
        editing = true
        editingExistingPreset = store.loadAll().any { it.id == preset.id }
        editingOriginalName = preset.name
        presetBeforeEdit = overlay.currentPreset()
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
            locationSwitch.isChecked = preset.showLocation
            outlineSeekBar.progress = preset.outlinePx.toInt()
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
        val preset = overlay.currentPreset()
        if (editingExistingPreset) {
            // Editing an existing item updates the same stored id/name rather
            // than opening the new-watermark naming flow.
            preset.name = editingOriginalName.ifBlank { preset.name }
            store.save(preset)
            toast("水印已更新")
            enterCameraMode()
            return
        }
        val input = EditText(this).apply {
            hint = "例如：工作水印"
            setSingleLine(true)
        }
        AlertDialog.Builder(this)
            .setTitle("保存水印")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                preset.name = input.text.toString().trim().ifBlank {
                    "水印 " + SimpleDateFormat("MMdd-HHmm", Locale.getDefault()).format(Date())
                }
                store.save(preset)
                toast("水印已保存")
                presetBeforeEdit = null
                enterCameraMode()
            }.show()
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

    private fun requestLocation() {
        locationProvider.request { location ->
            if (location == null) {
                toast("暂时无法获取当前位置")
                return@request
            }
            cameraExecutor.execute {
                val label = runCatching {
                    if (Geocoder.isPresent()) {
                        @Suppress("DEPRECATION")
                        Geocoder(this, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 1)
                            ?.firstOrNull()?.getAddressLine(0)
                    } else null
                }.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: "%.5f, %.5f".format(Locale.US, location.latitude, location.longitude)
                runOnUiThread {
                    currentLocation = label
                    overlay.setLocationText(label)
                }
            }
        }
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun requiredCameraPermissions() = buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray()
    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
