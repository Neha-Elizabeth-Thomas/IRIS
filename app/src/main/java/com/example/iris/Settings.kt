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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1️⃣ Bind layout
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 2️⃣ Initialize TTS
        tts = TextToSpeech(this, this)

        // 3️⃣ Initialize CaretakerManager
        caretakerManager = CaretakerManager(this, tts)

        // 4️⃣ Add Caretaker (PRIMARY)
        binding.addCaretakerButton.setOnClickListener {
            val number = binding.caretakerPhone.text.toString().trim()

            if (number.isNotEmpty()) {
                caretakerManager.savePrimary(number)
                binding.caretakerPhone.text.clear()
            } else {
                Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
            }
        }

        // 5️⃣ Edit Caretaker (BACKUP)
        binding.editCaretakerButton.setOnClickListener {
            val number = binding.caretakerPhone.text.toString().trim()

            if (number.isNotEmpty()) {
                caretakerManager.saveBackup(number)
                binding.caretakerPhone.text.clear()
            } else {
                Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
            }
        }

        // 6️⃣ About button (keep your existing logic)
        binding.aboutButton.setOnClickListener {
            Toast.makeText(this, "IRIS – Assistive app for visually impaired", Toast.LENGTH_LONG).show()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            tts.speak("Settings screen opened", TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }
}
