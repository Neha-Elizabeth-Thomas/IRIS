package com.example.iris

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.iris.databinding.ActivitySettingsBinding
import java.util.Locale

class SettingsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var caretakerManager: CaretakerManager
    private lateinit var tts: TextToSpeech

    // 🔁 Flag to know whether user is editing backup
    private var isEditingBackup = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1️⃣ Bind layout
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2️⃣ Initialize TTS
        tts = TextToSpeech(this, this)

        // 3️⃣ Initialize CaretakerManager
        caretakerManager = CaretakerManager(this, tts)

        // 4️⃣ ADD CARETAKER (Primary → Backup → Stop)
        binding.addCaretakerButton.setOnClickListener {
            val number = binding.caretakerPhone.text.toString().trim()

            if (number.isNotEmpty()) {
                caretakerManager.addCaretakerNumber(number)
                binding.caretakerPhone.text.clear()
            } else {
                Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
            }
        }

        // 5️⃣ EDIT CARETAKER (BACKUP ONLY)
        binding.editCaretakerButton.setOnClickListener {

            if (!isEditingBackup) {
                // First press → load saved backup number
                val savedNumber = caretakerManager.getBackupNumber()

                if (savedNumber != null) {
                    binding.caretakerPhone.setText(savedNumber)
                    binding.caretakerPhone.setSelection(savedNumber.length)
                    tts.speak(
                        "Edit the backup number and press edit again",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                    isEditingBackup = true
                } else {
                    tts.speak(
                        "No backup number saved yet",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                }

            } else {
                // Second press → save edited backup
                val updatedNumber = binding.caretakerPhone.text.toString().trim()

                if (updatedNumber.isNotEmpty()) {
                    caretakerManager.updateBackup(updatedNumber)

                    binding.caretakerPhone.text.clear()
                    tts.speak(
                        "Backup caretaker updated",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                    isEditingBackup = false
                } else {
                    Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 6️⃣ About button
        binding.aboutButton.setOnClickListener {
            Toast.makeText(
                this,
                "IRIS – Assistive app for visually impaired",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.speak(
                "Settings screen opened",
                TextToSpeech.QUEUE_FLUSH,
                null,
                null
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }
}
