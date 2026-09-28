package com.example.brailleglove.ui.practice

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.brailleglove.audio.SoundEffectsPlayer
import com.example.brailleglove.ble.BleManager
import com.example.brailleglove.braille.BrailleEngine
import com.example.brailleglove.data.model.Level
import com.example.brailleglove.data.model.Question
import com.example.brailleglove.data.repository.BrailleRepository
import com.example.brailleglove.speech.SpeechManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class PracticeViewModel(
    private val bleManager: BleManager,
    private val speechManager: SpeechManager,
    private val repository: BrailleRepository,
    private val soundEffects: SoundEffectsPlayer
) : ViewModel() {

    val currentLevel = MutableLiveData<Level?>()
    val currentQuestion = MutableLiveData<Question?>()
    val questionNumber = MutableLiveData(0)
    val totalQuestions = MutableLiveData(Level.QUESTIONS_PER_LEVEL)
    val livesRemaining = MutableLiveData(Level.MAX_LIVES)
    val score = MutableLiveData(0)
    val statusMessage = MutableLiveData<String>()
    val gamePhase = MutableLiveData(GamePhase.LEVEL_SELECT)

    val allLevels = repository.allLevels

    enum class GamePhase {
        LEVEL_SELECT,
        WAITING,
        VIBRATING,
        SPEAKING,
        RESULT_CORRECT,
        RESULT_WRONG,
        LEVEL_COMPLETE,
        GAME_OVER
    }

    private var questions: MutableList<Question> = mutableListOf()
    private var currentQuestionIndex = 0

    /** Letters already requeued once this session, so a letter only gets one extra retry. */
    private val requeuedLetters = mutableSetOf<Char>()

    fun initSpeech() { speechManager.initTts() }

    fun selectLevel(level: Level) {
        currentLevel.value = level
        currentQuestionIndex = 0
        livesRemaining.value = Level.MAX_LIVES
        score.value = 0
        requeuedLetters.clear()
        gamePhase.value = GamePhase.WAITING
        statusMessage.value = "Preparing your questions..."

        viewModelScope.launch {
            val letterStats = repository.getLetterStatsMap()
            val reviewPool = repository.getCompletedLevels()
                .filter { it.levelNumber < level.levelNumber }
                .flatMap { it.letterList }
                .distinct()

            questions = Question.generateForLevel(level, letterStats, reviewPool).toMutableList()
            totalQuestions.value = questions.size
            speechManager.speak("Level ${level.levelNumber}: letters ${level.title}. Get ready!")
            loadNextQuestion()
        }
    }

    private fun loadNextQuestion() {
        if (currentQuestionIndex >= questions.size) {
            finishLevel()
            return
        }
        val q = questions[currentQuestionIndex]
        currentQuestion.value = q
        questionNumber.value = currentQuestionIndex + 1
        totalQuestions.value = questions.size
        gamePhase.value = GamePhase.WAITING
    }

    fun startQuestion() {
        val q = currentQuestion.value ?: return
        viewModelScope.launch {
            when (q.type) {
                Question.Type.VIBRATE_TO_SPEECH -> doVibrateToSpeech(q)
                Question.Type.SPEECH_TO_TAP -> doSpeechToTap(q)
            }
        }
    }

    private suspend fun doVibrateToSpeech(q: Question) {
        gamePhase.value = GamePhase.VIBRATING
        statusMessage.value = "Feel the vibration and say the letter!"
        val payload = BrailleEngine.buildVibrationPayload(q.targetLetter)
        payload?.let { bleManager.sendVibration(it) }  // ← safe unwrap of ByteArray?

        val spoken = speechManager.listen()
        val correct = spoken != null &&
                BrailleEngine.validateSpeech(q.targetLetter, spoken)
        handleAnswer(q, correct)
    }

    private suspend fun doSpeechToTap(q: Question) {
        gamePhase.value = GamePhase.SPEAKING
        statusMessage.value = "Listen and tap the dots on the glove!"

        // Start listening before/during the prompt, not after — see the same
        // fix + rationale in TestViewModel.runTestLoop().
        val tappedPattern = coroutineScope {
            val tapDeferred = async {
                bleManager.collectTapAnswer(
                    onProgress = { partial ->
                        val dotsSoFar = partial.indices.filter { partial[it] }.map { it + 1 }
                        statusMessage.value = if (dotsSoFar.isEmpty()) {
                            "Listen and tap the dots on the glove!"
                        } else {
                            "Dot${if (dotsSoFar.size > 1) "s" else ""} ${dotsSoFar.joinToString(", ")} tapped — keep going or pause when done"
                        }
                    }
                )
            }
            speechManager.speak("Tap the dots for letter ${q.targetLetter}")
            tapDeferred.await()
        }

        val correct = tappedPattern != null &&
                BrailleEngine.validateTap(q.targetLetter, tappedPattern)
        handleAnswer(q, correct)
    }

    private suspend fun handleAnswer(q: Question, correct: Boolean) {
        viewModelScope.launch {
            repository.recordAttempt(q.targetLetter, correct)
        }

        if (correct) {
            score.value = (score.value ?: 0) + 1
            gamePhase.value = GamePhase.RESULT_CORRECT
            statusMessage.value = "✓ Correct! Great job!"
            soundEffects.playCorrect()
            speechManager.speak("Correct!")
        } else {
            livesRemaining.value = (livesRemaining.value ?: 0) - 1
            gamePhase.value = GamePhase.RESULT_WRONG
            statusMessage.value = "✗ Wrong — the letter was ${q.targetLetter}"
            soundEffects.playWrong()
            speechManager.speak("Not quite. The letter was ${q.targetLetter}.")

            // Give this letter one more shot later in the same session, in the
            // easier recognition mode, instead of just moving on from a miss.
            if (q.targetLetter !in requeuedLetters && (livesRemaining.value ?: 0) > 0) {
                requeuedLetters.add(q.targetLetter)
                val insertAt = minOf(currentQuestionIndex + 4, questions.size)
                questions.add(
                    insertAt,
                    Question(
                        targetLetter = q.targetLetter,
                        type = Question.Type.VIBRATE_TO_SPEECH,
                        levelId = q.levelId,
                        questionNumber = insertAt + 1
                    )
                )
                totalQuestions.value = questions.size
            }

            if ((livesRemaining.value ?: 0) <= 0) {
                gamePhase.value = GamePhase.GAME_OVER
                statusMessage.value = "Game over — out of lives. Try this level again!"
                speechManager.speak("Game over. You're out of lives. Let's try this level again.")
                return
            }
        }
        currentQuestionIndex++
    }

    fun nextQuestion() {
        loadNextQuestion()
    }

    private fun finishLevel() {
        val finalScore = score.value ?: 0
        val totalAnswered = currentQuestionIndex.coerceAtLeast(1)
        val accuracyPercent = ((finalScore.toFloat() / totalAnswered) * 100).toInt()
        val level = currentLevel.value ?: return
        val passed = accuracyPercent >= Level.PASS_THRESHOLD_PCT

        gamePhase.value = GamePhase.LEVEL_COMPLETE
        statusMessage.value = if (passed) "Level Complete! 🎉" else "Try again to pass!"

        viewModelScope.launch {
            if (passed) {
                val stars = Level.starsForAccuracy(accuracyPercent)
                repository.markLevelCompleted(level.id, finalScore, stars)
                repository.unlockLevel(level.levelNumber + 1)
                speechManager.speak("Level complete! You got $accuracyPercent percent correct. Great work!")
            } else {
                speechManager.speak("You got $accuracyPercent percent this time. Let's try this level again!")
            }
        }
    }

    /** Call from UI to simulate a correct glove tap — useful for emulator demos */
    fun simulateTap() {
        val q = currentQuestion.value ?: return
        val correctPattern = com.example.brailleglove.braille.BrailleAlphabet.patternFor(q.targetLetter)
        correctPattern?.let { bleManager.onTapReceived?.invoke(it) }
    }

    fun resetToLevelSelect() {
        gamePhase.value = GamePhase.LEVEL_SELECT
        currentLevel.value = null
        currentQuestion.value = null
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.onTapReceived = null
        speechManager.destroy()
    }
}