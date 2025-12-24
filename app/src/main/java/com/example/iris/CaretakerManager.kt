package com.example.iris

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.core.app.ActivityCompat

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

    /* ---------------- ADD NUMBER (SMART LOGIC) ---------------- */

    fun addCaretakerNumber(number: String) {

        val primary = getPrimary()
        val backup = getBackup()

        when {
            primary == null -> {
                // 1️⃣ Save primary
                prefs.edit().putString(PRIMARY, number).apply()
                speak("Primary caretaker saved. Now save secondary number.")
            }

            backup == null -> {
                // 2️⃣ Save backup
                prefs.edit().putString(BACKUP, number).apply()
                speak("Secondary caretaker saved successfully.")
            }

            else -> {
                // 3️⃣ Both already saved
                speak("You already added both caretaker numbers.")
            }
        }
    }

    fun updateBackup(number: String) {
        if (getBackup() != null) {
            prefs.edit().putString(BACKUP, number).apply()
            speak("Backup caretaker updated")
        } else {
            speak("No backup caretaker to update")
        }
    }


    /* ---------------- GET NUMBERS ---------------- */

    private fun getPrimary(): String? = prefs.getString(PRIMARY, null)
    private fun getBackup(): String? = prefs.getString(BACKUP, null)

    fun getPrimaryNumber(): String? = getPrimary()
    fun getBackupNumber(): String? = getBackup()

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

    /* ---------------- SPEAK ---------------- */

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
}
