package zll.yolive

import android.content.Context
import android.graphics.Bitmap

object Detector{

    private var fps: Int=5
    private var lastRunNs:Long=0L

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

}