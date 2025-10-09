package zll.yolive

import android.R
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.model.Model
import java.nio.ByteBuffer
import java.nio.FloatBuffer


data class DetectionResult(
    val boundingBox: RectF,
    val label: String,
    val score: Float
)


object Detection {

    val YOLO_H = 640
    val YOLO_W = 640
    var modelName="best_float32.tflite"
    var k=0

    lateinit var model: Model

    fun loadModel(ctx:Context){
//        val opts= Model.Options.Builder()
//            .setDevice(Model.Device.CPU)
//            .build()
        model=Model.createModel(ctx,modelName)
        k=k+1

    }


    fun runObjectDetection(ctx: Context,bb:Bitmap): List<DetectionResult> {
        if(k==0){loadModel(ctx)}
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

            val inputFeature = processedImg.tensorBuffer.buffer
            val outputsArray = Array(1) { Array(8400) { FloatArray(7) } }
            val outputs = mapOf<Int, Any>(0 to outputsArray)

            // Runs model inference and gets result.
            model.run(inputFeature as Array<out Any>?,outputs)
//            val outputFeature0 = outputs
//
//            // You can now process the outputFeature0 to get bounding boxes, scores, etc.
//            Log.d(
//                "YOLO_INFERENCE",
//                "Inference successful. Output shape: ${outputFeature0.shape.joinToString()}"
//            )
//            val re = YoloPostProcessor.process(ctx, outputFeature0)
//            if(re.size>0){
//                val inferFPS= String.format("%.2f",1000.0f/(System.nanoTime()-now)*1000000.0f).toString()
//                Log.d(
//                    "YOLO_INF_FPS", " ${inferFPS}fps"
//                )
//            }
//
//            re.forEach { r ->
//                Log.d(
//                    "YOLO_RESULT", "Label: ${r.label}, " +
//                            "score: ${r.score}, " +
//                            "Position: ${r.boundingBox}"
//                )
//            }

            model.close()
//            return re

        } catch (e: Exception) {
            Log.e("YOLO_INFERENCE", "Error during model inference", e)
        }
        return emptyList()
    }


}