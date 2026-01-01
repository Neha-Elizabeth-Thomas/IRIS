package com.example.iris

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.iris.databinding.ActivitySettingsBinding
import java.util.Locale
import android.content.Intent


class SettingsActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var caretakerManager: CaretakerManager
    private lateinit var tts: TextToSpeech

    // Flags for edit mode
    private var isEditingBackup = false
    private var isEditingPrimary = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1️⃣ Bind layout
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2️⃣ Initialize TTS
        tts = TextToSpeech(this, this)

        // 3️⃣ Initialize CaretakerManager
        caretakerManager = CaretakerManager(this, tts)





        /* ---------------- ADD CARETAKER ---------------- */
        // Primary → Backup → Stop
        binding.addCaretakerButton.setOnClickListener {
            val number = binding.caretakerPhone.text.toString().trim()

            if (number.isNotEmpty()) {
                caretakerManager.addCaretakerNumber(number)
                binding.caretakerPhone.text.clear()
            } else {
                Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
            }
        }

        /* ---------------- EDIT BACKUP (SHORT PRESS) ---------------- */
        binding.editCaretakerButton.setOnClickListener {

            if (!isEditingBackup) {
                val savedBackup = caretakerManager.getBackupNumber()

                if (savedBackup != null) {
                    binding.caretakerPhone.setText(savedBackup)
                    binding.caretakerPhone.setSelection(savedBackup.length)
                    tts.speak(
                        "Editing backup caretaker. Press edit again to save.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                    isEditingBackup = true
                } else {
                    tts.speak(
                        "No backup caretaker saved",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                }

            } else {
                val updated = binding.caretakerPhone.text.toString().trim()

                if (updated.isNotEmpty()) {
                    caretakerManager.updateBackup(updated)
                    binding.caretakerPhone.text.clear()
                    isEditingBackup = false
                } else {
                    Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /* ---------------- EDIT PRIMARY (LONG PRESS) ---------------- */
        binding.editCaretakerButton.setOnLongClickListener {

            if (!isEditingPrimary) {
                val savedPrimary = caretakerManager.getPrimaryNumber()

                if (savedPrimary != null) {
                    binding.caretakerPhone.setText(savedPrimary)
                    binding.caretakerPhone.setSelection(savedPrimary.length)
                    tts.speak(
                        "Editing primary caretaker. Press edit again to save.",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                    isEditingPrimary = true
                } else {
                    tts.speak(
                        "No primary caretaker saved",
                        TextToSpeech.QUEUE_FLUSH,
                        null,
                        null
                    )
                }

            } else {
                val updated = binding.caretakerPhone.text.toString().trim()

                if (updated.isNotEmpty()) {
                    caretakerManager.updatePrimary(updated)
                    binding.caretakerPhone.text.clear()
                    isEditingPrimary = false
                } else {
                    Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
                }
            }

            true // consume long press
        }

        /* ---------------- ABOUT ---------------- */
        binding.aboutButton.setOnClickListener {
            val intent = Intent(this, About::class.java)
            startActivity(intent)
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
