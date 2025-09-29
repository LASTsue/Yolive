package zll.yolive

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import zll.yolive.ml.BestFloat32


data class DetectionResult(
    val boundingBox: RectF,
    val label: String,
    val score: Float
)


object Detection {

    val YOLO_H = 640
    val YOLO_W = 640

    fun runObjectDetection(ctx: Context,bb:Bitmap): List<DetectionResult> {
        try {
            // 'this' refers to the MainActivity instance, which is a Context.
            val model = BestFloat32.newInstance(ctx)

            // --- Your existing code ---

            // Note: You need to get the ByteBuffer from an actual image (e.g., a Bitmap).
            // This is just a placeholder.

            val tensorImageMaker = TensorImage(DataType.FLOAT32)
            tensorImageMaker.load(bb)
            val originW = tensorImageMaker.width
            val originH = tensorImageMaker.height

            val imageProcessor = ImageProcessor.Builder()
                .add(Rot90Op(-1))
                .add(ResizeOp(YOLO_H, YOLO_W, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0.0f, 255.0f))
                .build()

            val processedImg = imageProcessor.process(tensorImageMaker)
//            var showimg=processedImg.bitmap


//             Creates inputs for reference.
//            val inputFeature0 = TensorBuffer.createFixedSize(intArrayOf(1, 320, 320, 3), DataType.FLOAT32)
            val inputFeature = processedImg.tensorBuffer

            // Runs model inference and gets result.
            val outputs = model.process(inputFeature)
            val outputFeature0 = outputs.outputFeature0AsTensorBuffer

            // You can now process the outputFeature0 to get bounding boxes, scores, etc.
            Log.d(
                "YOLO_INFERENCE",
                "Inference successful. Output shape: ${outputFeature0.shape.joinToString()}"
            )
            val re = YoloPostProcessor.process(ctx, outputFeature0)

            re.forEach { r ->
                Log.d(
                    "YOLO_RESULT", "物价: ${r.label}, " +
                            "置信度: ${r.score}, " +
                            "位置: ${r.boundingBox}"
                )
            }

            // Releases model resources if no longer used.
            model.close()
            return re

        } catch (e: Exception) {
            // Handle exceptions, e.g., model file not found
            Log.e("YOLO_INFERENCE", "Error during model inference", e)
        }
        return emptyList()
    }


}