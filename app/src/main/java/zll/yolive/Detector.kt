package zll.yolive

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

object Detector{

    private var fps: Int=5
    private var lastRunNs:Long=0L
    var modelName="yolo11n_float16.tflite"
    lateinit var colormap: Map<String, Color>

    fun cameraDetect(ctx: Context, bb: Bitmap):List<DetectionResult>{
        val minIntervalNs = 1_000_000_000L / fps
        val now = System.nanoTime()
        if (now - lastRunNs < minIntervalNs) return emptyList()
        lastRunNs = now
        return Detection.runObjectDetection(ctx,bb)
    }

    fun setFps(f: Int){
        fps=f
    }

    fun initModel(ctx: Context){
        Detection.loadModelLabels(ctx,modelName)
        colormap=generateComposeColorMap(Detection.labels)

    }

    fun generateComposeColorMap(
        labels: List<String>,
        saturation: Float = 0.85f,
        value: Float = 0.95f
    ): Map<String, Color> {
        if (labels.isEmpty()) {
            return emptyMap()
        }

        val colorMap = mutableMapOf<String, Color>()
        val numLabels = labels.size

        labels.forEachIndexed { index, label ->
            // 在 360 度的色相环上均匀分布
            val hue = (index.toFloat() * 360f) / numLabels.toFloat()

            // 直接使用 androidx.compose.ui.graphics.Color.hsv 方法创建 Color 对象
            val color = Color.hsv(hue, saturation, value)
            colorMap[label] = color
        }
        return colorMap
    }

}