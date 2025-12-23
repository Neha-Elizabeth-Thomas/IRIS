package com.example.iris

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.example.iris.databinding.ActivityMainBinding
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private var tts: TextToSpeech? = null
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var speechRecognizerIntent: Intent
    private lateinit var vibrator: Vibrator
    private var imageCapture: ImageCapture? = null

    // **NEW**: The Gemini Generative Model
    private lateinit var generativeModel: GenerativeModel

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            var allPermissionsGranted = true
            for (isGranted in permissions.values) {
                if (!isGranted) {
                    allPermissionsGranted = false
                    break
                }
            }
            if (allPermissionsGranted) {
                startCamera()
                setupSpeechRecognizer()
            } else {
                Toast.makeText(this, "You must grant all permissions to use IRIS", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(this, this)
        vibrator = getSystemService(Vibrator::class.java)

        // **NEW**: Initialize Gemini
        // We use "gemini-1.5-flash" because it is fast and cheap/free
        generativeModel = GenerativeModel(
            modelName = "gemini-1.5-flash-001",
            apiKey = BuildConfig.GEMINI_API_KEY
        )

        if (allPermissionsGranted()) {
            startCamera()
            setupSpeechRecognizer()
        } else {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            // We keep the ObjectDetectorAnalyzer for offline obstacle detection
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ObjectDetectorAnalyzer(vibrator))
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer
                )
                binding.statusTextView.text = getString(R.string.status_navigation)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                binding.statusTextView.text = getString(R.string.error_camera)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // **NEW**: Function to capture image and send to Gemini
    private fun describeSceneWithGemini() {
        val imageCapture = imageCapture ?: return

        // 1. Capture the image
        val photoFile = File(externalMediaDirs.firstOrNull(), "gemini_temp.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // 2. Load the image into a Bitmap
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)

                    // 3. Send to Gemini (Network call must be in a coroutine)
                    lifecycleScope.launch {
                        try {
                            speak("Thinking...") // Let the user know we are processing

                            val inputContent = content {
                                image(bitmap)
                                text("Describe this scene in one short sentence for a visually impaired person. Focus on the main obstacles or objects.")
                            }

                            val response = generativeModel.generateContent(inputContent)

                            // 4. Speak the result
                            response.text?.let { description ->
                                Log.i(TAG, "Gemini description: $description")
                                speak(description)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Gemini API Error", e)
                            speak("Sorry, I couldn't connect to the AI.")
                        } finally {
                            photoFile.delete() // Cleanup
                        }
                    }
                }

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                    speak("Camera error.")
                }
            }
        )
    }

    private fun runTextRecognition() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(externalMediaDirs.firstOrNull(), "ocr_temp.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val image = InputImage.fromBitmap(bitmap, 0)
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

                    recognizer.process(image)
                        .addOnSuccessListener { visionText ->
                            if (visionText.text.isNotBlank()) {
                                speak(visionText.text)
                            } else {
                                speak("I don't see any text.")
                            }
                            photoFile.delete()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Text recognition failed", e)
                            speak("Sorry, I couldn't read the text.")
                            photoFile.delete()
                        }
                }

                override fun onError(exc: ImageCaptureException) {
                    speak("Camera error.")
                }
            }
        )
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                binding.statusTextView.text = getString(R.string.status_listening)
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null && matches.isNotEmpty()) {
                    val recognizedText = matches[0].lowercase(Locale.getDefault())
                    processVoiceCommand(recognizedText)
                }
            }
            override fun onError(error: Int) {
                startListening()
            }
            override fun onEndOfSpeech() {
                startListening()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        startListening()
    }

    private fun processVoiceCommand(command: String) {
        if (command.contains("describe") || command.contains("what do you see")) {
            // **UPDATED**: Now calls Gemini
            speak("Let me see...")
            describeSceneWithGemini()
        } else if (command.contains("read text") || command.contains("read this")) {
            speak("Reading text...")
            runTextRecognition()
        } else if (command.contains("navigate to") || command.contains("go to")) {
            val destination = command.substringAfter("navigate to ").substringAfter("go to ")
            if (destination.isNotBlank()) {
                speak("Getting walking directions to $destination")
                val gmmIntentUri = "google.navigation:q=$destination&mode=w".toUri()
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")

                if (mapIntent.resolveActivity(packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    speak("Sorry, I can't open Google Maps.")
                }
            }
        }
    }

    private fun startListening() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer.startListening(speechRecognizerIntent)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "TTS Language not supported")
            } else {
                speak("IRIS system ready.")
            }
        }
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts?.stop()
        tts?.shutdown()
        speechRecognizer.destroy()
    }

    companion object {
        private const val TAG = "IRIS_APP"
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).toTypedArray()
    }
}