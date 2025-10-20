# liteRT Kotlin deployment
[简体中文](../README.md) / [English](README_EN.md)

## Introduction to liteRT
LiteRT (abbreviation of Lite Runtime), formerly known as TensorFlow Lite, is a high-performance device-side AI running environment launched by Google:
- **On-Device Optimization:** TFLite is optimized for on-device ML, reducing latency by processing data locally, enhancing privacy by not transmitting personal data, and saving space by minimizing model size.
- **Multi-Platform Support:** TFLite offers broad platform compatibility, supporting Android, iOS, embedded Linux and microcontrollers.
- **Diverse Language Support:** TFLite is compatible with a variety of programming languages, including Java, Swift, Objective-C, C++, and Python.
- **High Performance:** Achieve superior performance through hardware acceleration (supports CPU, GPU, TPU) and model optimization.

## Related links
[liteRT Documentation](https://ai.google.dev/edge/litert) \\ [liteRT API](https://ai.google.dev/edge/api) \\ [Source Code Repository](https://github.com/google-ai-edge/LiteRT) \\ [Official Sample](https://github.com/google-ai-edge/litert-samples/tree/main/v2/image_segmentation)

## Process
### 1. Environment preparation

System: Ubuntu (if you need GPU acceleration, install the Nvidia driver first)


**(Optional) Install CUDA**

- Check the CUDA version in the terminal

```shell
$nvidia-smi
```

![alt text](imgs/image.png)

- Install the corresponding version of CUDA [Download & Installation Guide Address] (https://developer.nvidia.com/cuda-toolkit-archive)

- Install cudnn according to the CUDA version [Download & Installation Guide Address] (https://developer.nvidia.com/cudnn-archive)
*It should be noted that the version of cudnn must correspond to cuda*

**Configure miniconda (convenient management environment)**
- Install miniconda [Download & Installation Guide Address](https://www.anaconda.com/docs/getting-started/miniconda/install#linux-2)
- Add domestic mirror [guidance address](https://mirrors.tuna.tsinghua.edu.cn/help/anaconda/)
- Create model training and conversion environment (python version 3.10 is recommended):
```shell
$ conda create -n yolo python=3.10
```
- Activate the environment
```shell
$ conda activate yolo
```

### 2. Model format conversion
> liteRT only supports its own tflite format when deployed at the edge, so other companies' model formats need to be converted into tflite.

**Environment Deployment**

- Install torch [Download & Installation Guide Address](https://pytorch.org/get-started/previous-versions/)

**Method 1: Use Ultralyrics’ open source tool library, suitable for Ultralyrics’ own models [Reference link](https://docs.ultralytics.com/zh/integrations/tflite/)**
- Install Ultralyrics
```shell
$ pip install ultralytics
```
- If the download is too slow, you can download it through the mirror:
```shell
$ pip install -i https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple/ ultralytics
```
- Or download by setting a proxy:
```shell
$ pip install ultralytics --proxy=http:127.0.0.1:7890
```

- Create `trans.py` to convert the pre-trained model (for more models, please refer to the Ultralyrics documentation)
```python
from ultralytics import YOLO

# Load the pre-trained model, or the model after local parameter adjustment
model = YOLO("yolo11n.pt")

#Convert to tflite format
model.export(format="tflite")

#Load the converted model
tflite_model = YOLO("yolo11n_float32.tflite")

# Test, run inference
results = tflite_model("https://ultralytics.com/images/bus.jpg")
```

- Get a new folder after conversion:
```
yolo11n_saved_model
├── assets
├── fingerprint.pb
├── metadata.yaml
├── saved_model.pb
├── variables
│ ├── variables.data-00000-of-00001
│ └── variables.index
├──yolo11n_float16.tflite
└── yolo11n_float32.tflite
```
- Among them, `yolo11n_float16.tflite` and `yolo11n_float32.tflite` are half-precision and single-precision models respectively, and both can be called.
`metadata.yaml` contains basic information of the model:
```yaml
description: Ultralytics YOLO11n model trained on /usr/src/ultralytics/ultralytics/cfg/datasets/coco.yaml
author: Ultralytics
date: '2025-10-14T10:47:03.453225'
version: 8.3.203
license: AGPL-3.0 License (https://ultralytics.com/license)
docs: https://docs.ultralytics.com
stride: 32
task: detect
batch: 1
imgsz:
- 640
- 640
names:
0: person
1: bicycle
2: car
3: motorcycle
4: airplane
5: bus
6: train
7: truck
  ...
args:
batch: 1
fraction: 1.0
half: false
int8: false
nms: false
channels: 3
```

- The main ones we need to use later are `imgsz` and `names`, which respectively describe the input shape and label name of the model. Model information can also be directly obtained from the json file by decompressing the tflite file via zip:

```json
metadata.json
{
"description": "Ultralytics YOLO11n model trained on /usr/src/ultralytics/ultralytics/cfg/datasets/coco.yaml",
"author": "Ultralytics",
"date": "2025-10-11T11:02:29.194943",
"version": "8.3.203",
"license": "AGPL-3.0 License (https://ultralytics.com/license)",
"docs": "https://docs.ultralytics.com",
"stride": 32,
"task": "detect",
"batch": 1,
"imgsz": [
    640,
    640
  ],
"names": {
"0": "person",
"1": "bicycle",
"2": "car",
"3": "motorcycle",
"4": "airplane",
"5": "bus",
"6": "train",
"7": "truck",
    ...
  },
"args": {
"batch": 1,
"fraction": 1.0,
"half": false,
"int8": false,
"nms": false
  },
"channels": 3
}
```

**Method 2: Convert through liteRT official library (to be tested)**

### 3. Add dependencies
```kts
implementation("com.google.ai.edge.litert:litert:2.0.2")
implementation("com.google.ai.edge.litert:litert-gpu:1.4.0")
implementation("com.google.ai.edge.litert:litert-support:1.4.0")
implementation("com.google.ai.edge.litert:litert-metadata:1.4.0")
```

### 4. Initialize model and labels

- Write a post-processor for YOLO model output
```kotlin

import android.content.Context
import android.graphics.RectF
import org.tensorflow.lite.support.common.FileUtil
import java.util.PriorityQueue

data class DetectionResult(
val boundingBox: RectF,
val label: String,
val score: Float
)

fun process(
context: Context,
outputArray: FloatArray,
labels:List<String>
): List<DetectionResult> {

val CONFIDENCE_THRESHOLD = 0.4f // Confidence threshold
val NMS_IOU_THRESHOLD = 0.5f //IoU threshold of NMS algorithm
val NUM_BOXES = 8400 //Total number of detection boxes
val NUM_CLASSES = Detection.labels.size // Total number of categories


// Parse raw output and filter
val candidateDetections = mutableListOf<DetectionResult>()

for (i in 0 until NUM_BOXES) {
//Extract category scores
val classScores = FloatArray(NUM_CLASSES)
for (j in 0 until NUM_CLASSES) {
classScores[j] = outputArray[(4 + j) * NUM_BOXES + i]
        }

// Find the category with the highest score
var maxScore = -1.0f
var maxScoreIndex = -1
classScores.forEachIndexed { index, score ->
if (score > maxScore) {
maxScore = score
maxScoreIndex = index
            }
        }

//Apply confidence threshold
if (maxScore > CONFIDENCE_THRESHOLD) {
//Extract normalized bounding box coordinates
val centerX = outputArray[0 * NUM_BOXES + i]
val centerY = outputArray[1 * NUM_BOXES + i]
val width = outputArray[2 * NUM_BOXES + i]
val height = outputArray[3 * NUM_BOXES + i]

// Convert center-width-height format to left-top-right-bottom format
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

//Perform non-maximum suppression (NMS)
return nonMaxSuppression(candidateDetections, NMS_IOU_THRESHOLD)
}

// Non-maximum suppression (NMS) algorithm to eliminate overlapping detection boxes.
fun nonMaxSuppression(
detections: List<DetectionResult>,
iouThreshold: Float
): List<DetectionResult> {
val finalDetections = mutableListOf<DetectionResult>()
//Group detection results by category
val detectionsByLabel = detections.groupBy { it.label }

for ((_, detectionGroup) in detectionsByLabel) {
// Sort by score in descending order using priority queue
val priorityQueue = PriorityQueue<DetectionResult>(detectionGroup.size, compareByDescending { it.score })
priorityQueue.addAll(detectionGroup)

while (priorityQueue.isNotEmpty()) {
//Get the detection frame with the highest score
val bestDetection = priorityQueue.poll() ?: continue
finalDetections.add(bestDetection)

// Remove other boxes that have too high IoU with the current highest score box
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

// Calculate the Intersection over Union (IoU) of two bounding boxes.
fun calculateIoU(box1: RectF, box2: RectF): Float {
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
```

- Initialize the model

```kotlin

import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.json.JSONObject

var modelPath="yolo11n_float32.tflite"
var model =
CompiledModel.create(
this,
modelPath,
CompiledModel.Options(Accelerator.NPU) //CPU or GPU optional
    )
```

- Initialization labels

```kotlin
val metadataFile = File(labelPath)
val metadataJsonString = metadataFile.readText(Charsets.UTF_8)

val metadataJson = JSONObject(metadataJsonString)
if (!metadataJson.has("names")) {
println("Error: metadata.json file does not contain 'names' object.")
return
}

val namesObject = metadataJson.getJSONObject("names")
val labels = mutableListOf<String>()

// The keys of the "names" object are "0", "1", "2", ... we need to extract the values ​​in this order
for (i in 0 until namesObject.length()) {
val key = i.toString()
if (namesObject.has(key)) {
labels.add(namesObject.getString(key))
} else {
// This is a warning if the keys are not consecutive
println("Warning: Tag index '$key' not found in 'names' object.")
    }
}
```

- Model inference

```kotlin
val now= System.nanoTime()

//Load images
val tensorImageMaker = TensorImage(DataType.FLOAT32)
val imageFile = File(imagePath)
BitmapFactory.decodeFile(imageFile.absolutePath)
tensorImageMaker.load(bitmap)

//Load image processor
val imageProcessor = ImageProcessor.Builder()
.add(Rot90Op(-1))
.add(ResizeOp(YOLO_H, YOLO_W, ResizeOp.ResizeMethod.BILINEAR))
.add(NormalizeOp(0.0f, 255.0f))
.build()

//process pictures
val processedImg = imageProcessor.process(tensorImageMaker)

//Convert the format to model input format
val inputFeature = processedImg.tensorBuffer.floatArray

//Start reasoning
val inputBuffers = model.createInputBuffers()
val outputBuffers = model.createOutputBuffers()
inputBuffers[0].writeFloat(inputFeature)
model.run(inputBuffers, outputBuffers)
val outputFloatArray = outputBuffers[0].readFloat()
Log.d(
"YOLO_INFERENCE",
"Inference successful.}"
)

//Post-processing
val re = YoloPostProcessor.process(ctx, outputFloatArray)
if(re.size>0){
val inferFPS= String.format("%.2f",1000.0f/(System.nanoTime()-now)*1000000.0f).toString()
Log.d(
"YOLO_INF_FPS", " ${inferFPS}fps"
    )
}

//Output results
re.forEach { r ->
Log.d(
"YOLO_RESULT", "Label: ${r.label}, " +
"score: ${r.score}, " +
"Position: ${r.boundingBox}"
    )
}

//Close model
inputBuffers.forEach { it.close() }
outputBuffers.forEach { it.close() }
model.close()
```









