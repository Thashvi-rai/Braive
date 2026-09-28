package com.example.brailleglove.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "braille_letters")
data class BrailleLetter(

    @PrimaryKey
    val letter: String,              // ← Changed: Char → String (e.g. "A")

    val dotDescription: String,

    val dotPatternString: String,    // e.g. "100000"

    val isLearned: Boolean = false,

    val correctCount: Int = 0,

    val attemptCount: Int = 0
) {
    val accuracyPercent: Int
        get() = if (attemptCount == 0) 0
        else ((correctCount.toFloat() / attemptCount) * 100).toInt()

    val dotPattern: BooleanArray
        get() = BooleanArray(6) { i -> dotPatternString.getOrNull(i) == '1' }

    /** Convenience to get the letter as a Char */
    val letterChar: Char
        get() = letter.firstOrNull() ?: ' '
}