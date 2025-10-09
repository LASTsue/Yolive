package zll.yolive

import android.content.Context
import android.graphics.RectF
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.PriorityQueue

object YoloPostProcessor {

    /**
     * 对YOLO模型的输出张量进行后处理。
     *
     * @param context Android上下文，用于访问assets。
     * @param outputBuffer 模型的原始输出TensorBuffer，shape为[1, 7, 8400]。
     * @param imageWidth 输入图片的原始宽度。
     * @param imageHeight 输入图片的原始高度。
     * @return 返回一个包含最终检测结果的列表 (List<DetectionResult>)。
     */
    fun process(
        context: Context,
        outputBuffer: TensorBuffer,
    ): List<DetectionResult> {

        val CONFIDENCE_THRESHOLD = 0.4f // 置信度阈值
        val NMS_IOU_THRESHOLD = 0.5f   // NMS算法的IoU阈值
        val NUM_BOXES = 8400           // 检测框总数
        val NUM_CLASSES = 3            // 类别总数

        // 从 assets/labels.txt 加载类别标签
        val labels = FileUtil.loadLabels(context, "labels.txt")
        val outputArray = outputBuffer.floatArray

        // --- 2. 解析原始输出并筛选 ---
        val candidateDetections = mutableListOf<DetectionResult>()

        for (i in 0 until NUM_BOXES) {
            // 提取类别分数
            val classScores = FloatArray(NUM_CLASSES)
            for (j in 0 until NUM_CLASSES) {
                classScores[j] = outputArray[(4 + j) * NUM_BOXES + i]
            }

            // 找出分数最高的类别
            var maxScore = -1.0f
            var maxScoreIndex = -1
            classScores.forEachIndexed { index, score ->
                if (score > maxScore) {
                    maxScore = score
                    maxScoreIndex = index
                }
            }

            // 应用置信度阈值
            if (maxScore > CONFIDENCE_THRESHOLD) {
                // 提取归一化的边界框坐标
                val centerX = outputArray[0 * NUM_BOXES + i]
                val centerY = outputArray[1 * NUM_BOXES + i]
                val width = outputArray[2 * NUM_BOXES + i]
                val height = outputArray[3 * NUM_BOXES + i]

                // 将 center-width-height 格式转换为 left-top-right-bottom 格式
                val left = centerX - width / 2
                val top = centerY - height / 2
                val right = centerX + width / 2
                val bottom = centerY + height / 2

                val boundingBox = RectF(left, top, right, bottom)
                val label = labels[maxScoreIndex]

                candidateDetections.add(
                    DetectionResult(boundingBox, label, maxScore)
                )
            }
        }

        // --- 3. 执行非极大值抑制 (NMS) ---
        return nonMaxSuppression(candidateDetections, NMS_IOU_THRESHOLD)
    }

    /**
     * 非极大值抑制 (NMS) 算法，用于消除重叠的检测框。
     */
    private fun nonMaxSuppression(
        detections: List<DetectionResult>,
        iouThreshold: Float
    ): List<DetectionResult> {
        val finalDetections = mutableListOf<DetectionResult>()
        // 按类别对检测结果进行分组
        val detectionsByLabel = detections.groupBy { it.label }

        for ((_, detectionGroup) in detectionsByLabel) {
            // 使用优先队列按分数降序排序
            val priorityQueue = PriorityQueue<DetectionResult>(detectionGroup.size, compareByDescending { it.score })
            priorityQueue.addAll(detectionGroup)

            while (priorityQueue.isNotEmpty()) {
                // 取出分数最高的检测框
                val bestDetection = priorityQueue.poll() ?: continue
                finalDetections.add(bestDetection)

                // 移除与当前最高分框 IoU 过高的其他框
                val iterator = priorityQueue.iterator()
                while (iterator.hasNext()) {
                    val nextDetection = iterator.next()
                    val iou = calculateIoU(bestDetection.boundingBox, nextDetection.boundingBox)
                    if (iou > iouThreshold) {
                        iterator.remove()
                    }
                }
            }
        }
        return finalDetections
    }

    /**
     * 计算两个边界框的交并比 (Intersection over Union, IoU)。
     */
    private fun calculateIoU(box1: RectF, box2: RectF): Float {
        val xA = maxOf(box1.left, box2.left)
        val yA = maxOf(box1.top, box2.top)
        val xB = minOf(box1.right, box2.right)
        val yB = minOf(box1.bottom, box2.bottom)

        val intersectionArea = maxOf(0f, xB - xA) * maxOf(0f, yB - yA)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }
}