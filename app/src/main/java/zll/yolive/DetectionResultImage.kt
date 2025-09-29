package zll.yolive

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

@Composable
fun DetectionResultImage(
    bitmap: Bitmap,
    detections: List<DetectionResult>
) {
    // 为不同类别的框定义不同的颜色
    val labelToColorMap = mapOf(
        "fire" to Color(0xFF, 0x66, 0x66), // Light Red
        "smoke" to Color(0x66, 0xFF, 0x66), // Light Green
        "human" to Color(0x66, 0x66, 0xFF)  // Light Blue
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. 将图片作为背景，并让它适应屏幕（Fit模式）
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Detection source image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // 2. 在图片上层覆盖一个透明的 Canvas 用于绘制
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 计算图片在 Fit 模式下缩放后的实际尺寸和位置
            val scale = ContentScale.Fit.computeScaleFactor(
                srcSize = Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
                dstSize = size // 'size' 是 Canvas 的尺寸
            )
            val scaledWidth = bitmap.width * scale.scaleX
            val scaledHeight = bitmap.height * scale.scaleY
            val offsetX = (size.width - scaledWidth) / 2
            val offsetY = (size.height - scaledHeight) / 2

            // 3. 遍历所有检测结果并在 Canvas 上绘制
            detections.forEach { detection ->
                val box = detection.boundingBox // 这是归一化的 RectF (0.0-1.0)
                val color = labelToColorMap[detection.label] ?: Color.White

                // 将归一化的坐标转换为在屏幕上缩放后的实际像素坐标
                val screenRect = androidx.compose.ui.geometry.Rect(
                    left = box.left * scaledWidth + offsetX,
                    top = box.top * scaledHeight + offsetY,
                    right = box.right * scaledWidth + offsetX,
                    bottom = box.bottom * scaledHeight + offsetY
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
}