package com.example.mikucamera

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.BitmapFactory
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.OrientationEventListener
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
import androidx.camera.core.AspectRatio
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.mikucamera.camera.PhotoComposer
import com.example.mikucamera.data.PresetStore
import com.example.mikucamera.location.LocationProvider
import com.example.mikucamera.model.WatermarkPreset
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
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var root: View
    private lateinit var previewView: PreviewView
    private lateinit var overlay: WatermarkOverlayView
    private lateinit var cameraControls: View
    private lateinit var captureControls: View
    private lateinit var editorControls: View
    private lateinit var flashButton: MaterialButton
    private lateinit var switchCameraButton: ImageButton
    private lateinit var captureButton: ImageButton
    private lateinit var selectWatermarkButton: MaterialButton
    private lateinit var recentPhotoView: ImageView
    private lateinit var timeSwitch: SwitchMaterial
    private lateinit var locationSwitch: SwitchMaterial
    private lateinit var outlineSeekBar: android.widget.SeekBar
    private lateinit var brightnessSeekBar: android.widget.SeekBar
    private lateinit var focusIndicator: View
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
        setContentView(R.layout.activity_main)
        bindViews()
        bindActions()
        enterCameraMode()
        loadLatestPhoto()
        if (!hasCameraPermission()) cameraPermission.launch(requiredCameraPermissions()) else startCamera()
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        previewView = findViewById(R.id.previewView)
        overlay = findViewById(R.id.watermarkOverlay)
        cameraControls = findViewById(R.id.cameraControls)
        captureControls = findViewById(R.id.captureControls)
        editorControls = findViewById(R.id.editorControls)
        flashButton = findViewById(R.id.flashButton)
        switchCameraButton = findViewById(R.id.switchCameraButton)
        captureButton = findViewById(R.id.captureButton)
        selectWatermarkButton = findViewById(R.id.selectWatermarkButton)
        recentPhotoView = findViewById(R.id.recentPhotoView)
        timeSwitch = findViewById(R.id.timeSwitch)
        locationSwitch = findViewById(R.id.locationSwitch)
        outlineSeekBar = findViewById(R.id.outlineSeekBar)
        brightnessSeekBar = findViewById(R.id.brightnessSeekBar)
        createFocusIndicator()

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val horizontal = (16 * density).roundToInt()
            cameraControls.setPadding(horizontal, bars.top + (8 * density).roundToInt(), horizontal, (4 * density).roundToInt())
            captureControls.setPadding(horizontal, (8 * density).roundToInt(), horizontal, bars.bottom + (12 * density).roundToInt())
            editorControls.setPadding(horizontal, (10 * density).roundToInt(), horizontal, bars.bottom + (10 * density).roundToInt())
            insets
        }
        ViewCompat.requestApplyInsets(root)
        overlay.onChanged = {
            if (editing) persistCurrentPresetIfSaved()
        }
        root.post {
            updateViewfinderBounds()
            applyPhysicalOrientation(physicalRotationDegrees)
        }
    }

    private fun createFocusIndicator() {
        focusIndicator = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke((2 * density).roundToInt(), Color.WHITE)
                cornerRadius = 8 * density
            }
            isClickable = false
            visibility = View.GONE
            alpha = 1f
        }
        val params = FrameLayout.LayoutParams(
            (64 * density).roundToInt(),
            (64 * density).roundToInt()
        )
        (root as FrameLayout).addView(focusIndicator, params)
    }

    override fun onResume() {
        super.onResume()
        orientationListener.enable()
    }

    override fun onPause() {
        orientationListener.disable()
        super.onPause()
    }

    private fun bindActions() {
        previewView.setOnTouchListener { _, event ->
            if (editing) return@setOnTouchListener false
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                focusAt(event.x, event.y)
            }
            true
        }
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
        brightnessSeekBar.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, value: Int, fromUser: Boolean) {
                if (!fromUser) return
                val exposure = camera?.cameraInfo?.exposureState ?: return
                if (!exposure.isExposureCompensationSupported) return
                camera?.cameraControl?.setExposureCompensationIndex(
                    exposure.exposureCompensationRange.lower + value
                )
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

    private fun focusAt(x: Float, y: Float) {
        val currentCamera = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        currentCamera.cameraControl.startFocusAndMetering(action)
        showFocusIndicator(x, y)
    }

    private fun showFocusIndicator(x: Float, y: Float) {
        val size = focusIndicator.layoutParams.width.coerceAtLeast((64 * density).roundToInt())
        val params = (focusIndicator.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = previewView.left + x.roundToInt() - size / 2
            topMargin = previewView.top + y.roundToInt() - size / 2
        }
        focusIndicator.layoutParams = params
        focusIndicator.visibility = View.VISIBLE
        focusIndicator.alpha = 1f
        focusIndicator.animate().cancel()
        focusIndicator.postDelayed({
            focusIndicator.animate()
                .alpha(0f)
                .setDuration(300L)
                .withEndAction { focusIndicator.visibility = View.GONE }
                .start()
        }, 700L)
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
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            // Keep the preview target fixed: the viewfinder and its camera
            // content must not rotate with the locked screen.
            .setTargetRotation(Surface.ROTATION_0)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setTargetRotation(currentPhysicalSurfaceRotation())
            .build()
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, capture)
            imageCapture = capture
            updateFlashControl()
            updateExposureControl()
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
        flashButton.text = "⚡ " + when (flashMode) {
            ImageCapture.FLASH_MODE_ON -> "开"
            ImageCapture.FLASH_MODE_AUTO -> "自动"
            else -> "关"
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

    private fun updateExposureControl() {
        val exposure = camera?.cameraInfo?.exposureState
        if (exposure == null || !exposure.isExposureCompensationSupported) {
            brightnessSeekBar.isEnabled = false
            brightnessSeekBar.progress = brightnessSeekBar.max / 2
            return
        }
        val range = exposure.exposureCompensationRange
        brightnessSeekBar.isEnabled = true
        brightnessSeekBar.max = range.upper - range.lower
        brightnessSeekBar.progress = (exposure.exposureCompensationIndex - range.lower)
            .coerceIn(0, brightnessSeekBar.max)
    }

    private fun updateViewfinderBounds() {
        val width = root.width
        val rootHeight = root.height
        if (width <= 0 || rootHeight <= 0) return
        val landscape = width > rootHeight
        val topMargin = if (landscape) 0 else (88 * density).roundToInt()
        val height: Int
        val frameWidth: Int
        val leftMargin: Int
        if (landscape) {
            height = rootHeight
            frameWidth = (height * 16f / 9f).roundToInt().coerceAtMost(width)
            leftMargin = (width - frameWidth) / 2
        } else {
            frameWidth = width
            height = (width * 16f / 9f).roundToInt()
            leftMargin = 0
        }
        listOf(previewView, overlay).forEach { view ->
            val params = (view.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                this.width = frameWidth
                this.height = height
                gravity = Gravity.TOP
                this.leftMargin = leftMargin
                this.topMargin = topMargin
            }
            view.layoutParams = params
        }
    }

    private fun currentPhysicalSurfaceRotation(): Int = when (physicalRotationDegrees) {
        // OrientationEventListener reports the device's physical turn, while
        // CameraX targetRotation is the correction that must be applied to
        // the camera buffer. Those two horizontal values are opposite.
        90 -> Surface.ROTATION_270
        180 -> Surface.ROTATION_180
        270 -> Surface.ROTATION_90
        else -> Surface.ROTATION_0
    }

    private fun applyPhysicalOrientation(degrees: Int) {
        physicalRotationDegrees = degrees
        // The viewfinder stays fixed. The UI/watermark visual rotation is the
        // inverse of the sensor posture, while the capture orientation keeps
        // the original posture for the saved image.
        val visualRotation = ((360 - degrees) % 360)
        val generated = overlay.setPhysicalRotation(visualRotation, degrees)
        applyControlRotation(degrees)
        imageCapture?.targetRotation = currentPhysicalSurfaceRotation()
        if (generated && !editing) persistCurrentPresetIfSaved()
    }

    private fun applyControlRotation(degrees: Int) {
        val rotation = ((360 - degrees) % 360).toFloat()
        listOf<View>(
            selectWatermarkButton,
            flashButton,
            recentPhotoView,
            captureButton,
            switchCameraButton,
            brightnessSeekBar,
            findViewById(R.id.uploadButton),
            timeSwitch,
            locationSwitch,
            findViewById(R.id.cancelEditButton),
            findViewById(R.id.saveWatermarkButton)
        ).forEach { it.rotation = rotation }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        root.post {
            updateViewfinderBounds()
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
            setPadding(24, 16, 24, 24)
        }
        container.addView(TextView(this).apply {
            text = "选择水印"
            textSize = 20f
            setPadding(0, 0, 0, 12)
        })
        val newButton = MaterialButton(this).apply {
            text = "+ 新建水印"
            setOnClickListener { dialog.dismiss(); startEditor(WatermarkPreset()) }
        }
        container.addView(newButton)
        store.loadAll().forEach { preset ->
            val button = MaterialButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                text = preset.name.ifBlank { "未命名水印" }
                setOnClickListener {
                    dialog.dismiss()
                    selectPreset(preset)
                }
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val editButton = MaterialButton(this).apply {
                text = "\u7F16\u8F91"
                setOnClickListener {
                    dialog.dismiss()
                    startEditor(preset)
                }
            }
            row.addView(button)
            row.addView(editButton)
            container.addView(row)
        }
        dialog.setContentView(container)
        dialog.show()
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
        cameraControls.visibility = View.GONE
        captureControls.visibility = View.GONE
        editorControls.visibility = View.VISIBLE
        loadBitmap(preset.imageUri) { bitmap ->
            overlay.setPreset(preset, bitmap)
            overlay.setEditingEnabled(true)
            timeSwitch.isChecked = preset.showTime
            locationSwitch.isChecked = preset.showLocation
            outlineSeekBar.progress = preset.outlinePx.toInt()
        }
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
