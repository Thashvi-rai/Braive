package com.example.brailleglove.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single Practice mode level.
 *
 * Each level contains exactly [QUESTIONS_PER_LEVEL] questions,
 * grants [MAX_LIVES] lives, and must be completed to unlock the next.
 */
@Entity(tableName = "levels")
data class Level(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /** Display number shown to the user (1-based) */
    val levelNumber: Int,

    /** Descriptive name, e.g. "A to E", "Letters F–J" */
    val title: String,

    /** Letters covered in this level, e.g. ['A','B','C','D','E'] */
    val letters: String, // stored as comma-separated, e.g. "A,B,C,D,E"

    /** Whether the user has fully completed this level */
    val isCompleted: Boolean = false,

    /** Whether the user can access this level (first level always true) */
    val isUnlocked: Boolean = false,

    /** Best score the user has achieved (0–10) */
    val bestScore: Int = 0,

    /** Number of stars earned: 0, 1, 2, or 3 */
    val stars: Int = 0
) {
    /** Parses the stored comma-separated letters into a list of Chars */
    val letterList: List<Char>
        get() = letters.split(",").mapNotNull { it.trim().firstOrNull() }

    companion object {
        const val QUESTIONS_PER_LEVEL = 10
        const val MAX_LIVES = 3
        const val STARS_THREE = 10   // Perfect score
        const val STARS_TWO = 7      // 7–9 correct
        const val STARS_ONE = 4      // 4–6 correct

        // Percentage-based equivalents. Sessions can now grow past 10 questions
        // (a missed letter gets one requeued retry), so pass/star grading is done
        // on accuracy % rather than a raw score tied to a fixed question count.
        const val STARS_THREE_PCT = 100
        const val STARS_TWO_PCT = 70
        const val STARS_ONE_PCT = 40
        const val PASS_THRESHOLD_PCT = 60

        /** Calculates stars from a raw score (legacy, assumes exactly 10 questions) */
        fun starsForScore(score: Int): Int = when {
            score >= STARS_THREE -> 3
            score >= STARS_TWO   -> 2
            score >= STARS_ONE   -> 1
            else                 -> 0
        }

        /** Calculates stars from an accuracy percentage (0–100) */
        fun starsForAccuracy(percent: Int): Int = when {
            percent >= STARS_THREE_PCT -> 3
            percent >= STARS_TWO_PCT   -> 2
            percent >= STARS_ONE_PCT   -> 1
            else                       -> 0
        }

        /**
         * Generates the default 5 practice levels for letters A–Z
         * (5 letters per level, Level 1 unlocked by default).
         */
        fun defaultLevels(): List<Level> {
            val groups = listOf(
                "A,B,C,D,E" to "A to E",
                "F,G,H,I,J" to "F to J",
                "K,L,M,N,O" to "K to O",
                "P,Q,R,S,T" to "P to T",
                "U,V,W,X,Y,Z" to "U to Z"
            )
            return groups.mapIndexed { index, (letters, title) ->
                Level(
                    levelNumber = index + 1,
                    title = title,
                    letters = letters,
                    isUnlocked = index == 0 // Only Level 1 starts unlocked
                )
            }
        }
    }
}