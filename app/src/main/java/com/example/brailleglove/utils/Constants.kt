package com.example.brailleglove.utils

/**
 * App-wide constants for BLE communication, vibration timing,
 * game settings, and shared preference keys.
 */
object Constants {

    // ─────────────────────────────────────────────
    // BLE (Bluetooth Low Energy) — ESP32 Glove
    // ─────────────────────────────────────────────

    /** Service UUID advertised by the ESP32 glove */
    const val BLE_SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"

    /** Characteristic UUID for sending vibration commands to the glove */
    const val BLE_VIBRATION_CHAR_UUID = "12345678-1234-1234-1234-123456789abd"

    /** Characteristic UUID for receiving dot-tap events from the glove */
    const val BLE_TAP_CHAR_UUID = "12345678-1234-1234-1234-123456789abe"

    /** Characteristic UUID for receiving the low-battery flag from the glove (1 byte: 0 = OK, 1 = low) */
    const val BLE_BATTERY_LOW_CHAR_UUID = "12345678-1234-1234-1234-123456789abf"

    /** Standard BLE Client Characteristic Configuration Descriptor UUID */
    const val BLE_CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    /** BLE scan timeout in milliseconds */
    const val BLE_SCAN_TIMEOUT_MS = 10_000L

    /** BLE connection retry attempts before surfacing an error */
    const val BLE_MAX_RETRY_ATTEMPTS = 3

    /** Delay between BLE reconnection attempts (ms) */
    const val BLE_RETRY_DELAY_MS = 2_000L

    // ─────────────────────────────────────────────
    // Vibration Timing
    // ─────────────────────────────────────────────

    /** How long each individual dot vibrates (ms) */
    const val VIBRATION_DOT_DURATION_MS = 400L

    /** Pause between sequential dots in teaching/step mode (ms) */
    const val VIBRATION_DOT_GAP_MS = 200L

    /** Pause between full letter patterns (ms) */
    const val VIBRATION_LETTER_GAP_MS = 800L

    /** How long the glove holds a full pattern vibration (ms) */
    const val VIBRATION_PATTERN_DURATION_MS = 1_000L

    /** Delay before TTS speaks after vibration ends (ms) */
    const val POST_VIBRATION_TTS_DELAY_MS = 600L

    // ─────────────────────────────────────────────
    // Game / Practice Mode
    // ─────────────────────────────────────────────

    /** Number of questions per practice level */
    const val QUESTIONS_PER_LEVEL = 10

    /** Number of lives per practice level */
    const val LIVES_PER_LEVEL = 3

    /** Time limit per question in Practice mode (seconds, 0 = no limit) */
    const val QUESTION_TIME_LIMIT_SECONDS = 0

    /** Time limit per question in Test mode (seconds) */
    const val TEST_TIME_LIMIT_SECONDS = 30

    /** Minimum correct answers to pass a level and unlock the next */
    const val PASS_THRESHOLD = 6

    // ─────────────────────────────────────────────
    // Tap Answer Collection (glove dot-tapping)
    // ─────────────────────────────────────────────

    /**
     * Total time we'll wait for a tap answer before giving up entirely
     * (counts as "no answer" if the child never touches a dot).
     * Kept well above TAP_ANSWER_SETTLE_MS as a safety net for a slow/
     * distracted child, but trimmed down from an earlier 15s — that felt
     * like the app had frozen when nothing was happening on screen for
     * that long. In the common case (child taps promptly) this ceiling
     * never gets hit at all — the answer locks in after SETTLE_MS instead.
     */
    const val TAP_ANSWER_MAX_WAIT_MS = 10_000L

    /**
     * How long to wait, after the most recent dot is pressed, before
     * treating the accumulated dots as the child's final answer. This is
     * what lets the child tap dots one at a time with pauses between —
     * we don't lock in an answer the instant the first dot goes down.
     */
    const val TAP_ANSWER_SETTLE_MS = 1_500L

    /**
     * Pause after a Test-mode word finishes (and its result is spoken)
     * before automatically starting a new word — long enough to see/hear
     * the result, short enough that it doesn't feel like a stall.
     */
    const val TEST_AUTO_ADVANCE_DELAY_MS = 2_500L

    // ─────────────────────────────────────────────
    // Speech (STT / TTS)
    // ─────────────────────────────────────────────

    /** Speech recognition silence timeout (ms) */
    const val STT_SILENCE_TIMEOUT_MS = 3_000L

    /** TTS speech rate (1.0 = normal, 0.8 = slightly slower for children) */
    const val TTS_SPEECH_RATE = 0.85f

    /** TTS pitch (1.0 = normal) */
    const val TTS_PITCH = 1.0f

    // ─────────────────────────────────────────────
    // SharedPreferences Keys
    // ─────────────────────────────────────────────

    const val PREFS_NAME              = "braille_glove_prefs"
    const val PREF_LAST_CONNECTED_MAC = "last_connected_mac"
    const val PREF_SOUND_ENABLED      = "sound_enabled"
    const val PREF_VIBRATION_SPEED    = "vibration_speed"
    const val PREF_ONBOARDING_DONE    = "onboarding_done"
    const val PREF_CHILD_NAME         = "child_name"

    // ─────────────────────────────────────────────
    // Navigation / Intent Extras
    // ─────────────────────────────────────────────

    const val EXTRA_LEVEL_ID     = "extra_level_id"
    const val EXTRA_LETTER       = "extra_letter"
    const val EXTRA_QUESTION_NUM = "extra_question_num"
}