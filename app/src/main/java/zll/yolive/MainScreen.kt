package zll.yolive

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis


import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
// 使用 CameraX 提供的 ImageProxy.toBitmap 扩展函数，无需手动转换

// 定义颜色以便复用
val unselectedTabColor = Color(0xFF3F51B5)
val selectedTabColor = Color(0xFF303F9F)
val lightBlueButtonColor = Color(0xFF448AFF)
val darkGrayColor = Color(0xFF616161)

val labelToColorMap = mapOf(
    "fire" to Color(0xFF, 0x66, 0x66), // Light Red
    "smoke" to Color(0x66, 0xFF, 0x66), // Light Green
    "human" to Color(0x66, 0x66, 0xFF)  // Light Blue
)
// 定义两个标签页的枚举
enum class Tab {
    CAMERA, SETTINGS
}

@Composable
fun MainScreen() {
    // 使用 a State 来追踪当前选中的标签页
    var selectedTab by remember { mutableStateOf(Tab.CAMERA) }

    // 全局配置状态：帧率与模型路径
    var selectedFrameRate by remember { mutableStateOf(5) }
    var modelName by remember { mutableStateOf("未加载") }
    var modelPath by remember { mutableStateOf<String?>(null) }

    Scaffold(
        // 底部导航栏
        bottomBar = {
            AppBottomNavigation(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        // 主内容区域
        Box(modifier = Modifier.padding(innerPadding)) {
            // 根据选中的标签页显示不同的界面内容
            when (selectedTab) {
                Tab.CAMERA -> CameraScreen()
                Tab.SETTINGS -> SettingsScreen(
                    selectedFrameRate = selectedFrameRate,
                    onFrameRateSelected = {
                        selectedFrameRate = it
                        Detector.setFps(it)
                    },
                    modelName = modelName,
                    onModelSelected = { name, path ->
                        modelName = name
                        modelPath = path
                    }
                )
            }
        }
    }
}

@Composable
fun AppBottomNavigation(selectedTab: Tab, onTabSelected: (Tab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        // “相机” 标签
        TabItem(
            text = "相机",
            isSelected = selectedTab == Tab.CAMERA,
            onClick = { onTabSelected(Tab.CAMERA) },
            modifier = Modifier.weight(1f)
        )
        // “配置” 标签
        TabItem(
            text = "配置",
            isSelected = selectedTab == Tab.SETTINGS,
            onClick = { onTabSelected(Tab.SETTINGS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TabItem(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val backgroundColor = if (isSelected) selectedTabColor else unselectedTabColor
    Box(
        modifier = modifier
            .fillMaxHeight()
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsScreen(
    selectedFrameRate: Int,
    onFrameRateSelected: (Int) -> Unit,
    modelName: String,
    onModelSelected: (String, String?) -> Unit
) {
    val context = LocalContext.current
    val frameRates = listOf(5, 15, 30,60)

    // 模型选择器
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                val name = queryDisplayName(context, uri) ?: "model.pte"
                val copiedPath = copyToCache(context, uri, name)
                if (copiedPath != null) {
//                    val loaded = Detection.loadModel( copiedPath)
//                    if (loaded) {
//                        onModelSelected(name, copiedPath)
//                    }
                }
            }
        }
    )



    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 帧率选择区域
        FrameRateSelector(
            title = "帧率",
            options = frameRates,
            selectedOption = selectedFrameRate,
            onOptionSelected = onFrameRateSelected
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 导入模型按钮
        Button(
            onClick = { openDocLauncher.launch(arrayOf("application/octet-stream")) },
            colors = ButtonDefaults.buttonColors(containerColor = lightBlueButtonColor),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        ) {
            Text("导入模型 ($modelName)", fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        }

        Spacer(modifier = Modifier.height(16.dp))

    }
}

@Composable
fun FrameRateSelector(
    title: String,
    options: List<Int>,
    selectedOption: Int,
    onOptionSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, fontSize = 18.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            options.forEach { option ->
                RadioButton(
                    selected = (option == selectedOption),
                    onClick = { onOptionSelected(option) },
                    colors = RadioButtonDefaults.colors(
                        selectedColor = Color(0xFF009688), // 绿色
                        unselectedColor = Color.Gray
                    )
                )
                Text(
                    text = "$option",
                    modifier = Modifier.padding(start = 2.dp, end = 12.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


@Composable
fun CameraScreen() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // 创建一个 ActivityResultLauncher 来请求权限
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    // 在 Composable 首次进入组合时请求权限
    LaunchedEffect(key1 = true) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            // 如果有权限，显示相机预览
            CameraPreview(modifier = Modifier.fillMaxSize())
        } else {
            // 如果没有权限，显示一个提示界面
            PermissionDeniedScreen {
                launcher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}

// [新增]：用于显示相机预览的 Composable
@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var detections by remember { mutableStateOf(emptyList<DetectionResult>()) }
    var frameWidth by remember { mutableStateOf(0) }
    var frameHeight by remember { mutableStateOf(0) }

    // AndroidView 用于将传统的 Android View 嵌入到 Compose 中
    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                // 创建 Preview 用例
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                // 图像分析
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    try {
                        // 使用 CameraX 最新版本的 ImageProxy.toBitmap() 获取 Bitmap
                        val bmp = imageProxy.toBitmap()
                        if (bmp != null) {
                            frameWidth = bmp.width
                            frameHeight = bmp.height
                            val result = Detector.cameraDetect(context,bmp)
                            if (result.isNotEmpty()) {
                                detections = result
                                // request redraw
                                previewView.invalidate()
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("CameraPreview", "analyze failed", t)
                    } finally {
                        imageProxy.close()
                    }
                }
                // 选择后置摄像头
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                try {
                    // 解绑所有之前的用例，然后重新绑定
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        analysis
                    )
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", e)
                }
            }, executor)
            previewView
        },
        modifier = modifier
    )

    // 叠加绘制检测框（将检测结果实时画在相机画面之上）
    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
        if (detections.isEmpty() || frameWidth == 0 || frameHeight == 0) return@Canvas
        val viewW = size.width
        val viewH = size.height
//        val scaleX = viewW / frameHeight
//        val scaleY = viewH / frameWidth
//
//        val paintStroke = Paint().apply {
//            color = Color(0xFFE91E63).toArgb()
//            style = Paint.Style.STROKE
//            strokeWidth = 4f
//        }
//        val paintFill = Paint().apply {
//            color = Color(0x6600E676).toArgb()
//            style = Paint.Style.FILL
//        }
//
//        val androidCanvas: Canvas = drawContext.canvas.nativeCanvas
//        detections.forEach { i ->
////            val det = i.boundingBox
////            val left = det.left * scaleX
////            val top = det.top * scaleY
////            val right = det.right* scaleX
////            val bottom = det.bottom * scaleY
//            val det = i.boundingBox
//            val left = det.left * viewW
//            val top = det.top * viewH
//            val right = det.right* viewW
//            val bottom = det.bottom * viewH
//            val rect = RectF(left, top, right, bottom)
//
//            androidCanvas.drawRect(rect, paintStroke)
//            androidCanvas.drawRect(left, top - 28f, right, top, paintFill)
        detections.forEach{ detection->
            val box = detection.boundingBox // 这是归一化的 RectF (0.0-1.0)
            val color = labelToColorMap[detection.label] ?: Color.White

            // 将归一化的坐标转换为在屏幕上缩放后的实际像素坐标
            val screenRect = androidx.compose.ui.geometry.Rect(
                left = box.left * viewW,
                top = box.top * viewH,
                right = box.right * viewW,
                bottom = box.bottom * viewH
            )

            // 绘制边界框，线条宽度使用 dp，在所有设备上看起来都一致
            drawRect(
                color = color,
                topLeft = screenRect.topLeft,
                size = screenRect.size,
                style = Stroke(width = 2.dp.toPx())
            )

            // 准备绘制文字
            val displayText = "${detection.label} ${String.format("%.2f", detection.score)}"
            val paint = android.graphics.Paint().apply {
                this.color = android.graphics.Color.RED
                textSize = 40f
                // this.typeface = ...
            }
            val textBgPaint = android.graphics.Paint().apply {
                this.color = color.copy(alpha = 0.7f).hashCode() // 背景半透明
            }
            val textBounds = android.graphics.Rect()
            paint.getTextBounds(displayText, 0, displayText.length, textBounds)

            // 绘制文字背景和文字 (使用 drawContext.canvas.nativeCanvas 访问底层 Canvas)
            drawContext.canvas.nativeCanvas.drawRect(
                screenRect.left,
                screenRect.top - textBounds.height() - 8.dp.toPx(),
                screenRect.left + textBounds.width() + 16.dp.toPx(),
                screenRect.top,
                textBgPaint
            )
            drawContext.canvas.nativeCanvas.drawText(
                displayText,
                screenRect.left + 8.dp.toPx(),
                screenRect.top - 8.dp.toPx(),
                paint
            )
        }
    }
}

// [新增]：当权限被拒绝时显示的界面
@Composable
fun PermissionDeniedScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "相机权限已被拒绝。",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "请授予相机权限以使用此功能。",
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("授予权限")
        }
    }
}

private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null) ?: return null
    cursor.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex >= 0) {
            return it.getString(nameIndex)
        }
    }
    return null
}

private fun copyToCache(context: android.content.Context, uri: Uri, fileName: String): String? {
    return try {
        val cacheFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri).use { input: InputStream? ->
            FileOutputStream(cacheFile).use { output ->
                if (input != null) {
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
        cacheFile.absolutePath
    } catch (t: Throwable) {
        Log.e("SettingsScreen", "copyToCache failed", t)
        null
    }
}
