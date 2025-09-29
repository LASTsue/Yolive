package zll.yolive

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MainScreen()
        }
    }
}

//
//class MainActivity : ComponentActivity() {
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContent {
//            // MaterialTheme 是推荐的样式主题包装器
//            MaterialTheme {
//                Surface(modifier = Modifier.fillMaxSize()) {
//                    DetectionScreen()
//                }
//            }
//        }
//    }
//}
//
//@Composable
//fun DetectionScreen() {
//    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
//    var detections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
//    var isLoading by remember { mutableStateOf(true) }
//
//    val context = LocalContext.current
//
//    LaunchedEffect(Unit) {
//        isLoading = true
//        withContext(Dispatchers.IO) {
//            val imageFileName = "ii.jpg"
//            val originalBitmap = context.assets.open(imageFileName).use { BitmapFactory.decodeStream(it) }
//
//            val detectionResults = Detection.runObjectDetection(context,originalBitmap)
//
//            // 更新状态
//            bitmap = originalBitmap
//            detections = detectionResults
//            isLoading = false
//        }
//    }
//
//    Box(
//        modifier = Modifier.fillMaxSize(),
//        contentAlignment = Alignment.Center
//    ) {
//        if (isLoading) {
//            CircularProgressIndicator()
//        } else {
//            bitmap?.let {
//                DetectionResultImage(bitmap = it, detections = detections)
//            }
//        }
//    }
//}