package com.example.iris

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat
import java.util.Locale

class CaretakerManager(
    private val context: Context,
    private val tts: TextToSpeech?
) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("caretaker_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val PRIMARY = "caretaker_primary"
        private const val BACKUP = "caretaker_backup"
    }

    /* ---------------- SAVE NUMBERS ---------------- */

    fun savePrimary(number: String) {
        prefs.edit().putString(PRIMARY, number).apply()
        speak("Primary caretaker saved")
    }

    fun saveBackup(number: String) {
        prefs.edit().putString(BACKUP, number).apply()
        speak("Backup caretaker saved")
    }

    /* ---------------- GET NUMBERS ---------------- */

    private fun getPrimary(): String? = prefs.getString(PRIMARY, null)
    private fun getBackup(): String? = prefs.getString(BACKUP, null)

    /* ---------------- EMERGENCY CALL ---------------- */

    fun callCaretaker() {
        val number = getPrimary() ?: getBackup()

        if (number == null) {
            speak("No caretaker number saved")
            return
        }

        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            speak("Call permission not granted")
            return
        }

        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$number")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(intent)
    }

    /* ---------------- VOICE COMMAND ---------------- */

    fun handleVoiceCommand(command: String): Boolean {
        if (command.contains("help")) {
            speak("Calling caretaker")
            callCaretaker()
            return true
        }
        return false
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}
