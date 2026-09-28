package com.example.brailleglove.ui.test

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.brailleglove.audio.SoundEffectsPlayer
import com.example.brailleglove.ble.BleManager
import com.example.brailleglove.braille.BrailleEngine
import com.example.brailleglove.data.repository.BrailleRepository
import com.example.brailleglove.speech.SpeechManager
import com.example.brailleglove.utils.Constants
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TestViewModel(
    private val bleManager: BleManager,
    private val speechManager: SpeechManager,
    private val repository: BrailleRepository,
    private val soundEffects: SoundEffectsPlayer
) : ViewModel() {

    val statusMessage = MutableLiveData<String>()
    val currentWord = MutableLiveData<String>()
    val currentLetterIndex = MutableLiveData(-1)  // which letter in the word we're on
    val typedSoFar = MutableLiveData("")
    val testPhase = MutableLiveData(TestPhase.IDLE)
    val finalAccuracy = MutableLiveData<Float?>(null)

    enum class TestPhase { IDLE, RUNNING, COMPLETE }

    // Sample test words + a few short phrases — you can expand this list or load from Room.
    // Keep everything A-Z only (Grade 1 alphabet has no digits/punctuation mapped).
    private val testSentences = listOf(
        // 3-letter
        "CAT", "DOG", "SUN", "PIG", "RUN", "BAT", "HAT", "RED", "BIG", "PEN",
        // 4-letter
        "BALL", "FISH", "BIRD", "BOOK", "STAR", "MOON", "TREE", "FROG", "DUCK", "CAKE", "MILK", "JUMP", "LAMP",
        // 5+ letter
        "BEE", "HELLO", "WORLD", "HAPPY", "SMILE", "APPLE", "HORSE", "WATER", "HOUSE",
        // short phrases (a space is auto-skipped — no tap needed for it)
        "HI MOM", "GO CAT GO", "BIG RED DOG", "I CAN READ", "GOOD JOB", "WE ARE HAPPY", "HOW ARE YOU", "GOOD LUCK"
    )
    private var currentSentence = ""
    private var letterResults = mutableListOf<Boolean>()

    fun initSpeech() { speechManager.initTts() }

    fun startTest() {
        currentSentence = testSentences.random().uppercase()
        currentWord.value = currentSentence
        typedSoFar.value = ""
        letterResults.clear()
        finalAccuracy.value = null
        testPhase.value = TestPhase.RUNNING
        runTestLoop()
    }

    private fun runTestLoop() {
        viewModelScope.launch {
            speechManager.speak("Spell the word: $currentSentence")
            delay(500)

            currentSentence.forEachIndexed { index, letter ->
                currentLetterIndex.value = index

                if (letter == ' ') {
                    // Word boundary, not a letter to tap — no dot pattern represents
                    // "space" in this glove's vocabulary, so don't make the child
                    // guess at one. Just mark it and move on.
                    typedSoFar.value = (typedSoFar.value ?: "") + " "
                    statusMessage.value = "Next word..."
                    speechManager.speak("next word")
                    delay(400)
                    return@forEachIndexed
                }

                statusMessage.value = "Tap the dots for letter $letter"

                // Start listening for taps BEFORE speaking the letter, not after —
                // a child who taps while the letter is still being announced (very
                // common) would otherwise land in a dead window where nothing is
                // listening yet, get silently dropped, and then get wrongly
                // attributed to whichever letter comes next.
                val tappedPattern = coroutineScope {
                    val tapDeferred = async {
                        bleManager.collectTapAnswer(
                            onProgress = { partial ->
                                val dotsSoFar = partial.indices.filter { partial[it] }.map { it + 1 }
                                if (dotsSoFar.isNotEmpty()) {
                                    statusMessage.value = "Dot${if (dotsSoFar.size > 1) "s" else ""} ${dotsSoFar.joinToString(", ")} tapped — keep going or pause when done"
                                }
                            }
                        )
                    }
                    speechManager.speak(letter.toString())
                    tapDeferred.await()
                }

                val correct = tappedPattern != null &&
                        BrailleEngine.validateTap(letter, tappedPattern)
                letterResults.add(correct)
                if (correct) soundEffects.playCorrect() else soundEffects.playWrong()

                val typed = typedSoFar.value ?: ""
                typedSoFar.value = typed + if (correct) letter.toString() else "_"

                repository.recordAttempt(letter, correct)

                delay(300) // brief pause between letters
            }

            // Calculate accuracy
            val accuracy = if (letterResults.isEmpty()) 0f
            else letterResults.count { it }.toFloat() / letterResults.size * 100f
            finalAccuracy.value = accuracy
            statusMessage.value = "Done! Accuracy: ${accuracy.toInt()}%"
            testPhase.value = TestPhase.COMPLETE
            speechManager.speak("Test complete. Your accuracy was ${accuracy.toInt()} percent.")

            // Auto-advance to a new word after a short pause, instead of
            // waiting for another tap on "Try Again" — long enough to see
            // and hear the result, short enough not to feel like a stall.
            // Phase re-check guards against a race: if the child manually
            // hit "Try Again" during this pause, testPhase is already back
            // to RUNNING and this should NOT also fire, or two rounds
            // would end up running on top of each other.
            delay(Constants.TEST_AUTO_ADVANCE_DELAY_MS)
            if (testPhase.value == TestPhase.COMPLETE) {
                startTest()
            }
        }
    }

    private fun BrailleRepository.recordAttempt(letter: Char, correct: Boolean) {
        viewModelScope.launch { recordAttempt(letter, correct) }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.onTapReceived = null
        speechManager.destroy()
    }
}