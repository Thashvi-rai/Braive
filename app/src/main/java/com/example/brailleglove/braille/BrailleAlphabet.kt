package com.example.brailleglove.braille
import kotlin.collections.mapIndexedNotNull

/**
 * Standard English Braille (Grade 1) dot patterns for A–Z.
 *
 * Dot layout on the glove (index 0–5):
 *   [0] [3]
 *   [1] [4]
 *   [2] [5]
 *
 * A BooleanArray of size 6 where `true` = dot raised/vibrated.
 */
object BrailleAlphabet {

    /**
     * Full A–Z Braille dot map.
     * Key   → uppercase letter character
     * Value → BooleanArray[6], index corresponds to dots 1–6
     */
    val alphabet: Map<Char, BooleanArray> = mapOf(
        'A' to booleanArrayOf(true,  false, false, false, false, false), // 1
        'B' to booleanArrayOf(true,  true,  false, false, false, false), // 1 2
        'C' to booleanArrayOf(true,  false, false, true,  false, false), // 1 4
        'D' to booleanArrayOf(true,  false, false, true,  true,  false), // 1 4 5
        'E' to booleanArrayOf(true,  false, false, false, true,  false), // 1 5
        'F' to booleanArrayOf(true,  true,  false, true,  false, false), // 1 2 4
        'G' to booleanArrayOf(true,  true,  false, true,  true,  false), // 1 2 4 5
        'H' to booleanArrayOf(true,  true,  false, false, true,  false), // 1 2 5
        'I' to booleanArrayOf(false, true,  false, true,  false, false), // 2 4
        'J' to booleanArrayOf(false, true,  false, true,  true,  false), // 2 4 5
        'K' to booleanArrayOf(true,  false, true,  false, false, false), // 1 3
        'L' to booleanArrayOf(true,  true,  true,  false, false, false), // 1 2 3
        'M' to booleanArrayOf(true,  false, true,  true,  false, false), // 1 3 4
        'N' to booleanArrayOf(true,  false, true,  true,  true,  false), // 1 3 4 5
        'O' to booleanArrayOf(true,  false, true,  false, true,  false), // 1 3 5
        'P' to booleanArrayOf(true,  true,  true,  true,  false, false), // 1 2 3 4
        'Q' to booleanArrayOf(true,  true,  true,  true,  true,  false), // 1 2 3 4 5
        'R' to booleanArrayOf(true,  true,  true,  false, true,  false), // 1 2 3 5
        'S' to booleanArrayOf(false, true,  true,  true,  false, false), // 2 3 4
        'T' to booleanArrayOf(false, true,  true,  true,  true,  false), // 2 3 4 5
        'U' to booleanArrayOf(true,  false, true,  false, false, true),  // 1 3 6
        'V' to booleanArrayOf(true,  true,  true,  false, false, true),  // 1 2 3 6
        'W' to booleanArrayOf(false, true,  false, true,  true,  true),  // 2 4 5 6
        'X' to booleanArrayOf(true,  false, true,  true,  false, true),  // 1 3 4 6
        'Y' to booleanArrayOf(true,  false, true,  true,  true,  true),  // 1 3 4 5 6
        'Z' to booleanArrayOf(true,  false, true,  false, true,  true),  // 1 3 5 6
    )

    /** Returns the dot pattern for the given letter, or null if not found. */
    fun patternFor(letter: Char): BooleanArray? = alphabet[letter.uppercaseChar()]

    /** Returns the letter that matches a given dot pattern, or null if not found. */
    fun letterFor(pattern: BooleanArray): Char? {
        return alphabet.entries.firstOrNull { (_, v) ->
            v.contentEquals(pattern)
        }?.key
    }

    /** Returns the list of active dot indices (0-based) for a given letter. */
    fun activeDotsFor(letter: Char): List<Int> {
        return patternFor(letter)
            ?.mapIndexed { index, active -> if (active) index else null }?.filterNotNull()
            ?: emptyList()
    }

    /** Checks whether a given pattern matches a given letter. */
    fun matches(letter: Char, pattern: BooleanArray): Boolean {
        return patternFor(letter)?.contentEquals(pattern) == true
    }
}
