package com.example.iris

import android.content.Context
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.support.image.TensorImage

class ObjectDetectorAnalyzer(
    private val context: Context,
    private val vibrator: Vibrator
) : ImageAnalysis.Analyzer {

    private var detector: ObjectDetector? = null

    init {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setMaxResults(5)
            .setScoreThreshold(0.5f)
            .build()

        try {
            // Make sure this string matches your exact filename in assets!
            detector = ObjectDetector.createFromFileAndOptions(
                context,
                "efficientdet.tflite",
                options
            )
            Log.e("IrisDetector", "SUCCESS: Model loaded correctly!")
        } catch (e: Exception) {
            // This is likely the error you are missing
            Log.e("IrisDetector", "CRITICAL ERROR: Model failed to load. Check filename!", e)
        }
    }

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image

        if (mediaImage != null && detector != null) {
            // CODE IS WORKING
            val bitmap = imageProxy.toBitmap()
            val image = TensorImage.fromBitmap(bitmap)
            val results = detector!!.detect(image)

            // Log how many items we see (even if 0)
            Log.d("IrisDetector", "Frame Processed. Objects found: ${results.size}")

            var maxObstacleSize = 0f

            for (detection in results) {
                val boundingBox = detection.boundingBox
                val objectHeight = boundingBox.height()
                val screenHeight = image.height.toFloat()
                val heightRatio = objectHeight / screenHeight

                if (heightRatio > maxObstacleSize) {
                    maxObstacleSize = heightRatio
                }
            }

            if (maxObstacleSize > 0.25f) { // 25% threshold
                triggerVibration(maxObstacleSize)
            }
        }
        else {
            // SILENT FAILURE DIAGNOSIS
            if (detector == null) {
                Log.e("IrisDetector", "SKIPPING FRAME: Detector is null! (Model didn't load)")
            } else if (mediaImage == null) {
                Log.e("IrisDetector", "SKIPPING FRAME: Camera image is null!")
            }
        }

        imageProxy.close()
    }

    private fun triggerVibration(intensity: Float) {
        val duration = (intensity * 200).toLong()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}