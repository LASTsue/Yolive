package zll.yolive

import android.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
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





object Detection {

    val YOLO_H = 640
    val YOLO_W = 640



    lateinit var model: CompiledModel
    lateinit var labels:List<String>


    fun loadModelLabels(ctx: Context, modelPath: String,device:String){
        if (this::model.isInitialized){
            model.close()
        }
        var dev=Accelerator.CPU
        if(device=="GPU"){
            dev=Accelerator.GPU
        }
        else if (device=="NPU"){
            dev= Accelerator.NPU
        }
        else{
            dev= Accelerator.CPU
        }
        try {
            model =
                CompiledModel.create(
                    ctx.assets,
                    modelPath,
                    CompiledModel.Options(dev)
                )
        }catch (e: Exception){
            Log.e("ModelInit", "GPU compilation failed: ${e.message}", e)
        }
        try {
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
        if(!this::model.isInitialized){return emptyList()}
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