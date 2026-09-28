package com.example.brailleglove.ui.learning

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.brailleglove.ble.BleManager
import com.example.brailleglove.braille.BrailleAlphabet
import com.example.brailleglove.braille.BrailleEngine
import com.example.brailleglove.data.repository.BrailleRepository
import com.example.brailleglove.speech.SpeechManager
import kotlinx.coroutines.launch

class LearningViewModel(
    private val bleManager: BleManager,
    private val speechManager: SpeechManager,
    private val repository: BrailleRepository
) : ViewModel() {

    val currentLetter = MutableLiveData<Char>()
    val dotPattern = MutableLiveData<BooleanArray>()
    val statusMessage = MutableLiveData<String>()
    val isListening = MutableLiveData(false)

    val learnedLetters = repository.learnedLetters

    fun initSpeech(onReady: () -> Unit) {
        speechManager.initTts(onReady)
    }

    // User taps a letter button on screen → vibrate and display pattern
    fun selectLetter(letter: Char) {
        currentLetter.value = letter
        val pattern = BrailleAlphabet.patternFor(letter) ?: return  // ← unwrap or bail
        dotPattern.value = pattern                                   // ← now non-nullable ✅
        val payload = BrailleEngine.buildVibrationPayload(letter)   // returns ByteArray?
        val sent = payload?.let { bleManager.sendVibration(it) } ?: false  // ← safe call ✅
        val description = BrailleEngine.describeDots(letter)
        statusMessage.value = if (sent) {
            "Vibrating: $description"
        } else {
            "Glove not connected — showing pattern only"
        }
        viewModelScope.launch {
            repository.markAsLearned(letter)
            // Speak the letter every time it's selected — this is the core
            // learning moment and must be audible, not just shown as text.
            speechManager.speak("Letter $letter. $description")
        }
    }

    // Microphone button — listen for a spoken letter then trigger it
    fun listenForLetter() {
        viewModelScope.launch {   // viewModelScope is Main — safe for listen()
            isListening.value = true
            statusMessage.value = "Listening... say a letter"
            val spoken = speechManager.listen()
            isListening.value = false
            if (spoken != null) {
                // Extract the intended letter, handling phonetic STT output
                // like "bee" or "double u" (see BrailleEngine.recognizeLetter)
                val letter = BrailleEngine.recognizeLetter(spoken)
                if (letter != null) {
                    selectLetter(letter)
                } else {
                    statusMessage.value = "Didn't catch that — try again"
                }
            } else {
                statusMessage.value = "No speech detected — try again"
            }
        }
    }

    // ← removed the broken local extension function entirely

    override fun onCleared() {
        super.onCleared()
        speechManager.destroy()
    }
}