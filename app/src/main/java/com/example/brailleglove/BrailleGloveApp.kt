package com.example.brailleglove

import android.app.Application
import com.example.brailleglove.audio.SoundEffectsPlayer
import com.example.brailleglove.ble.BleManager
import com.example.brailleglove.data.db.AppDatabase
import com.example.brailleglove.data.repository.BrailleRepository
import com.example.brailleglove.speech.SpeechManager

class BrailleGloveApp : Application() {

    // Lazy so they're only created when first accessed
    val database by lazy { AppDatabase.getInstance(this) }  // ← was getDatabase()

    val repository by lazy {
        BrailleRepository(
            database.brailleLetterDao(),
            database.levelDao()
        )
    }

    // Singleton BLE — one instance for the whole app lifetime
    val bleManager by lazy { BleManager(this) }

    // SpeechManager needs context
    val speechManager by lazy { SpeechManager(this) }

    // Correct/wrong feedback tones — one shared SoundPool for the app
    val soundEffects by lazy { SoundEffectsPlayer(this) }
}