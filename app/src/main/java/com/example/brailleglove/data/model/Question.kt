package com.example.brailleglove.data.model

import kotlin.random.Random

/**
 * Represents a single question in Practice mode or Test mode.
 *
 * Two question types exist in Practice mode:
 *  - [Type.VIBRATE_TO_SPEECH] — App vibrates a pattern, user speaks the letter.
 *  - [Type.SPEECH_TO_TAP]    — App speaks a letter, user taps the dot buttons.
 *
 * Test mode uses sentences broken into individual letter questions.
 */
data class Question(

    /** The correct answer letter (A–Z) */
    val targetLetter: Char,

    /** The type of interaction required from the user */
    val type: Type,

    /** Which level this question belongs to (matches Level.id) */
    val levelId: Int = 0,

    /** Position within the current session (1-based, for display) */
    val questionNumber: Int = 1
) {
    enum class Type {
        /** App vibrates → user answers audibly via STT */
        VIBRATE_TO_SPEECH,

        /** App speaks letter via TTS → user taps glove dot buttons */
        SPEECH_TO_TAP
    }

    /** TTS prompt for SPEECH_TO_TAP: "Please tap the letter B" */
    val spokenPrompt: String
        get() = when (type) {
            Type.SPEECH_TO_TAP    -> "Please tap the letter $targetLetter"
            Type.VIBRATE_TO_SPEECH -> "Feel the vibration, then say the letter"
        }

    /** Short UI label for question type */
    val typeLabel: String
        get() = when (type) {
            Type.VIBRATE_TO_SPEECH -> "Feel & Say"
            Type.SPEECH_TO_TAP     -> "Hear & Tap"
        }

    companion object {
        /**
         * Generates an adaptive set of ~[Level.QUESTIONS_PER_LEVEL] questions for the
         * given level. Three ideas from spaced-repetition / mastery-learning drive it:
         *
         * 1. **Weak letters repeat more.** [letterStats] (accuracy so far) weights how
         *    often each letter in this level is asked — a letter under 50% accuracy
         *    shows up up to 3x, a mastered one (≥80%) may show up just once.
         * 2. **Recognition before production.** Feeling a vibration and naming it
         *    (VIBRATE_TO_SPEECH) is easier than hearing a name and reproducing the
         *    dot pattern from memory (SPEECH_TO_TAP). New/shaky letters lean toward
         *    the easier recognition type, and each session eases from recognition
         *    into more production as it goes on.
         * 3. **Old letters get revisited.** A couple of slots are reserved for
         *    letters from already-completed levels ([reviewPool]), so earlier
         *    letters don't fade from memory while new ones are being learned.
         *
         * @param letterStats accuracy/attempt data for letters, keyed by letter (may be empty)
         * @param reviewPool  letters from earlier completed levels, eligible for review slots
         */
        fun generateForLevel(
            level: Level,
            letterStats: Map<Char, BrailleLetter> = emptyMap(),
            reviewPool: List<Char> = emptyList()
        ): List<Question> {
            val newLetters = level.letterList
            if (newLetters.isEmpty()) return emptyList()

            val totalSlots = Level.QUESTIONS_PER_LEVEL
            val reviewSlots = if (reviewPool.isEmpty()) 0 else minOf(2, totalSlots / 5)
            val newSlots = totalSlots - reviewSlots

            fun repetitionsFor(letter: Char): Int {
                val stat = letterStats[letter]
                return when {
                    stat == null || stat.attemptCount == 0 -> 3 // never attempted yet
                    stat.accuracyPercent < 50 -> 3
                    stat.accuracyPercent < 80 -> 2
                    else -> 1
                }
            }

            // Guarantee every new letter appears at least once, then fill remaining
            // slots by weighted repetition (weaker letters more likely to be picked).
            val weightedPool = newLetters.flatMap { letter -> List(repetitionsFor(letter)) { letter } }
            val sequence = newLetters.toMutableList()
            while (sequence.size < newSlots) {
                sequence.add(weightedPool.random())
            }
            while (sequence.size > newSlots) {
                sequence.removeAt(sequence.lastIndex)
            }

            if (reviewSlots > 0) {
                sequence.addAll(reviewPool.shuffled().take(reviewSlots))
            }
            sequence.shuffle()

            val n = sequence.size
            return sequence.mapIndexed { i, letter ->
                val stat = letterStats[letter]
                val seenEnough = stat != null && stat.attemptCount >= 2
                val baseRecognitionChance = if (seenEnough) 0.5 else 0.75
                // Ease from recognition toward production as the session progresses.
                val positionFactor = 1.0 - (i.toDouble() / n) * 0.4
                val recognitionChance = (baseRecognitionChance * positionFactor).coerceIn(0.15, 0.9)
                val type = if (Random.nextDouble() < recognitionChance)
                    Type.VIBRATE_TO_SPEECH else Type.SPEECH_TO_TAP

                Question(
                    targetLetter = letter,
                    type = type,
                    levelId = level.id,
                    questionNumber = i + 1
                )
            }
        }
    }
}