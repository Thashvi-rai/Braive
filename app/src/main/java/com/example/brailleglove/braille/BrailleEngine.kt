package com.example.brailleglove.braille
import com.example.brailleglove.utils.Constants
import kotlinx.coroutines.delay

/**
 * BrailleEngine converts Braille patterns into vibration timing sequences
 * and validates user glove-tap answers.
 *
 * Vibration packet format sent to ESP32 (6 bytes):
 *   Byte[i] = 1 if dot i+1 is active, 0 otherwise
 *   e.g., Letter A → [1, 0, 0, 0, 0, 0]
 */
object BrailleEngine {

    /**
     * Maps how Android's SpeechRecognizer typically transcribes a spoken
     * single letter to the letter it actually means. STT almost never
     * returns the bare literal character for these — it returns the
     * phonetic/spelling-alphabet word instead (e.g. saying "B" comes back
     * as "bee", not "B"). Each entry lists the common transcriptions seen
     * in practice; multiple variants are listed since STT output isn't
     * perfectly consistent across devices/accents.
     *
     * Keys are uppercase, whitespace-collapsed phrases with punctuation
     * already stripped (see [normalizeForMatching]).
     */
    private val PHONETIC_ALIASES: Map<String, Char> = buildMap {
        val simple = mapOf(
            'A' to listOf("AY", "EH"),
            'B' to listOf("BEE", "BE"),
            'C' to listOf("SEE", "SEA", "SI"),
            'D' to listOf("DEE"),
            'E' to listOf("EE"),
            'F' to listOf("EFF", "EF"),
            'G' to listOf("GEE", "JEE"),
            'H' to listOf("AITCH", "HAITCH"),
            'I' to listOf("EYE", "AI"),
            'J' to listOf("JAY"),
            'K' to listOf("KAY"),
            'L' to listOf("ELL", "EL"),
            'M' to listOf("EM", "EMM"),
            'N' to listOf("EN", "ENN"),
            'O' to listOf("OH"),
            'P' to listOf("PEE"),
            'Q' to listOf("QUEUE", "CUE", "KEW"),
            'R' to listOf("AR", "ARE"),
            'S' to listOf("ESS", "ES"),
            'T' to listOf("TEE"),
            'U' to listOf("YOU", "YEW"),
            'V' to listOf("VEE"),
            'W' to listOf("DOUBLE U", "DOUBLE YOU", "DUBYA"),
            'X' to listOf("EX", "EKS"),
            'Y' to listOf("WHY"),
            'Z' to listOf("ZEE", "ZED"),
        )
        simple.forEach { (letter, aliases) ->
            aliases.forEach { alias -> put(alias, letter) }
        }
    }

    /** Uppercases, trims, and strips everything except letters/spaces. */
    private fun normalizeForMatching(text: String): String =
        text.trim().uppercase().replace(Regex("[^A-Z ]"), "").trim()

    /**
     * Best-effort extraction of a single intended letter from raw STT
     * output. Tries, in order: an exact single-letter match, the phonetic
     * alias table (handles "bee" -> B, "double u" -> W, etc.), and the
     * "LETTER X" / "THE LETTER X" spoken forms. Returns null if nothing
     * matches confidently — callers should treat that as "try again"
     * rather than guessing.
     */
    fun recognizeLetter(spokenText: String): Char? {
        val cleaned = normalizeForMatching(spokenText)
        if (cleaned.isEmpty()) return null

        // Already a bare letter, e.g. "B"
        if (cleaned.length == 1 && cleaned[0] in 'A'..'Z') return cleaned[0]

        // Phonetic alphabet form, e.g. "BEE", "DOUBLE U"
        PHONETIC_ALIASES[cleaned]?.let { return it }

        // Spoken forms like "LETTER B" or "THE LETTER B"
        val withoutPrefix = cleaned
            .removePrefix("THE LETTER ")
            .removePrefix("LETTER ")
            .trim()
        if (withoutPrefix.length == 1 && withoutPrefix[0] in 'A'..'Z') {
            return withoutPrefix[0]
        }
        PHONETIC_ALIASES[withoutPrefix]?.let { return it }

        return null
    }

    /**
     * Builds a 6-byte BLE payload for the given letter.
     * Returns null if the letter has no Braille mapping.
     */
    fun buildVibrationPayload(letter: Char): ByteArray? {
        val pattern = BrailleAlphabet.patternFor(letter) ?: return null
        return ByteArray(6) { i -> if (pattern[i]) 1 else 0 }
    }

    /**
     * Builds a vibration payload directly from a BooleanArray pattern.
     */
    fun buildVibrationPayload(pattern: BooleanArray): ByteArray {
        require(pattern.size == 6) { "Pattern must have exactly 6 dots" }
        return ByteArray(6) { i -> if (pattern[i]) 1 else 0 }
    }

    /**
     * Returns a human-readable dot description for accessibility / TTS.
     * Example: 'A' → "Dots 1"
     *          'B' → "Dots 1 and 2"
     *          'G' → "Dots 1, 2, 4, and 5"
     */
    fun describeDots(letter: Char): String {
        val activeDots = BrailleAlphabet.activeDotsFor(letter)
            .map { it + 1 } // Convert to 1-based dot numbers for speech
        return when (activeDots.size) {
            0    -> "No dots"
            1    -> "Dot ${activeDots[0]}"
            2    -> "Dots ${activeDots[0]} and ${activeDots[1]}"
            else -> {
                val allButLast = activeDots.dropLast(1).joinToString(", ")
                "Dots $allButLast, and ${activeDots.last()}"
            }
        }
    }

    /**
     * Validates whether a user's tapped dot pattern matches the expected letter.
     * @param expectedLetter The letter the user should have tapped.
     * @param tappedPattern  BooleanArray[6] of which dots the user pressed.
     */
    fun validateTap(expectedLetter: Char, tappedPattern: BooleanArray): Boolean {
        return BrailleAlphabet.matches(expectedLetter, tappedPattern)
    }

    /**
     * Validates whether spoken input matches the expected letter.
     * Handles the literal character, "letter B" style phrasing, and
     * phonetic transcriptions like "bee" or "double u" via
     * [recognizeLetter], since that's what STT engines actually return
     * for single spoken letters.
     * @param expectedLetter The letter the user should have spoken.
     * @param spokenText     Raw STT result string.
     */
    fun validateSpeech(expectedLetter: Char, spokenText: String): Boolean {
        return recognizeLetter(spokenText) == expectedLetter.uppercaseChar()
    }

    /**
     * Converts a spoken word into its individual letters for Test mode.
     * Returns null if the spoken text contains non-alphabetic characters.
     */
    fun parseSpokenWord(spokenText: String): List<Char>? {
        val cleaned = spokenText.trim().uppercase().replace(" ", "")
        return if (cleaned.all { it.isLetter() }) cleaned.toList() else null
    }

    /**
     * Returns a sequence of (dotIndex, durationMs) pairs for sequential
     * single-dot vibration playback — useful for teaching mode step-by-step.
     */
    fun dotSequence(letter: Char): List<Pair<Int, Long>> {
        return BrailleAlphabet.activeDotsFor(letter).map { dotIndex ->
            Pair(dotIndex, Constants.VIBRATION_DOT_DURATION_MS)
        }
    }

    /**
     * Suspending helper: produces delay intervals between sequential dot
     * vibrations, calling [onDot] for each active dot.
     * Must be called from a coroutine scope.
     */
    suspend fun playDotSequence(
        letter: Char,
        onDot: suspend (dotIndex: Int) -> Unit
    ) {
        val dots = BrailleAlphabet.activeDotsFor(letter)
        dots.forEachIndexed { i, dotIndex ->
            onDot(dotIndex)
            delay(Constants.VIBRATION_DOT_DURATION_MS)
            if (i < dots.lastIndex) delay(Constants.VIBRATION_DOT_GAP_MS)
        }
    }
}
