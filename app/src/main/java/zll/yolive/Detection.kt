package zll.yolive

import android.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.JsonReader
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile



data class DetectionResult(
    val boundingBox: RectF,
    val label: String,
    val score: Float
)


object Detection {

    val YOLO_H = 640
    val YOLO_W = 640



    lateinit var model: CompiledModel
    lateinit var labels:List<String>



    fun loadModelLabels(ctx: Context, modelPath: String){
        model =
            CompiledModel.create(
                ctx.assets,
                modelPath,
                CompiledModel.Options(Accelerator.NPU)
            )
//        val modelFile = File(ctx.cacheDir, modelPath)
        try {
//            // 步骤 1: 将模型文件从 assets 复制到缓存目录，以便 ZipFile 可以访问
//            // 确保目标目录存在
//            modelFile.parentFile?.mkdirs()
//            ctx.assets.open(modelPath).use { inputStream ->
//                modelFile.outputStream().use { outputStream ->
//                    inputStream.copyTo(outputStream)
//                }
//            }
//
//            // 步骤 2: 将 TFLite 文件作为 Zip 文件打开并读取 metadata.json
//            val zipFile = ZipFile(modelFile)
//
//            val metadataEntry = zipFile.getEntry("metadata.json")
//                ?: run {
//                    println("错误：在模型 '$modelPath' 中找不到 metadata.json 文件。")
//                    return
//                }
//
//            val metadataJsonString = zipFile.getInputStream(metadataEntry)
//                .bufferedReader()
//                .use { it.readText() }

            val metadataJsonString=ctx.assets.open("metadata.json").bufferedReader().use {
                it.readText()
            }
            // 步骤 3: 解析 JSON 并从 "names" 对象中提取标签
            val metadataJson = JSONObject(metadataJsonString)
            if (!metadataJson.has("names")) {
                println("错误：metadata.json 文件中不包含 'names' 对象。")
                return
            }

            val namesObject = metadataJson.getJSONObject("names")
            val labels = mutableListOf<String>()

            // "names" 对象的键是 "0", "1", "2", ... 我们需要按此顺序提取值
            // 我们假设键是从 0 开始的连续整数
            for (i in 0 until namesObject.length()) {
                val key = i.toString()
                if (namesObject.has(key)) {
                    labels.add(namesObject.getString(key))
                } else {
                    // 如果键不连续，这是一个警告
                    println("警告：在 'names' 对象中找不到索引为 '$key' 的标签。")
                }
            }

            Detection.labels=labels


        } catch (e: Exception) {
            // 捕获所有可能的异常，如文件未找到、JSON 解析错误等
            e.printStackTrace()
            return
        }
    }


    fun runObjectDetection(ctx: Context,bb:Bitmap): List<DetectionResult> {
        try {
            val now= System.nanoTime()

            val tensorImageMaker = TensorImage(DataType.FLOAT32)
            tensorImageMaker.load(bb)

            val imageProcessor = ImageProcessor.Builder()
                .add(Rot90Op(-1))
                .add(ResizeOp(YOLO_H, YOLO_W, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0.0f, 255.0f))
                .build()

            val processedImg = imageProcessor.process(tensorImageMaker)
//            var showimg=processedImg.bitmap

            val inputFeature = processedImg.tensorBuffer.floatArray

            val inputBuffers = model.createInputBuffers()
            val outputBuffers = model.createOutputBuffers()

            inputBuffers[0].writeFloat(inputFeature)
            model.run(inputBuffers, outputBuffers)
            val outputFloatArray = outputBuffers[0].readFloat()
            Log.d(
                "YOLO_INFERENCE",
                "Inference successful.}"
            )
            val re = YoloPostProcessor.process(ctx, outputFloatArray)
            if(re.size>0){
                val inferFPS= String.format("%.2f",1000.0f/(System.nanoTime()-now)*1000000.0f).toString()
                Log.d(
                    "YOLO_INF_FPS", " ${inferFPS}fps"
                )
            }

            re.forEach { r ->
                Log.d(
                    "YOLO_RESULT", "Label: ${r.label}, " +
                            "score: ${r.score}, " +
                            "Position: ${r.boundingBox}"
                )
            }

            inputBuffers.forEach { it.close() }
            outputBuffers.forEach { it.close() }
//            model.close()
            return re

        } catch (e: Exception) {
            Log.e("YOLO_INFERENCE", "Error during model inference", e)
        }
        return emptyList()
    }





}