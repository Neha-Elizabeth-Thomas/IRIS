package com.example.iris

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions

class ObjectDetectorAnalyzer(private val vibrator: Vibrator) : ImageAnalysis.Analyzer {

    private var lastVibrationTime: Long = 0

    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .enableClassification() // We need this to get the object's label
        .build()

    private val objectDetector = ObjectDetection.getClient(options)

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    // **NEW**: Loop through all detected objects
                    for (detectedObject in detectedObjects) {
                        // Loop through the labels for each object
                        for (label in detectedObject.labels) {
                            // Check if the label is in our list of obstacles
                            if (OBSTACLE_LABELS.contains(label.text)) {
                                // If it is, check the cooldown and vibrate
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastVibrationTime > COOLDOWN_PERIOD) {
                                    triggerVibration()
                                    lastVibrationTime = currentTime
                                    // Once we find one obstacle, we don't need to check others in this frame
                                    return@addOnSuccessListener
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ObjectDetector", "Object detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun triggerVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrationEffect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
            vibrator.vibrate(vibrationEffect)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }

    companion object {
        private const val COOLDOWN_PERIOD = 1000L // 1 second

        // **NEW**: A set of labels we consider to be obstacles.
        // Using a Set provides a fast "contains" check.
        private val OBSTACLE_LABELS = setOf(
            "Person", "Car", "Bicycle", "Motorcycle", "Bus", "Train", "Truck",
            "Chair", "Couch", "Bed", "Dining Table", "Desk", "Door", "Stairs"
            // We can add more common obstacles here later.
        )
    }
}