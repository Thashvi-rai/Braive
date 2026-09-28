package com.example.brailleglove.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.brailleglove.utils.Constants
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Wraps Android TTS and STT in a coroutine-friendly API.
 *
 * Usage in a ViewModel (inside viewModelScope):
 *   speechManager.speak("Letter B")
 *   val heard = speechManager.listen()   // returns "B", "Letter B", etc.
 *
 * Lifecycle:
 *   - Call initTts() once (e.g. in ViewModel init or Fragment.onViewCreated).
 *   - Call destroy() in ViewModel.onCleared() or Fragment.onDestroyView().
 *
 * Permissions required by the caller:
 *   RECORD_AUDIO must be granted before calling listen().
 */
class SpeechManager(private val context: Context) {

    // ── TTS ───────────────────────────────────────────────────────────────────

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    /**
     * Initialise the TTS engine. Safe to call multiple times (no-ops if already ready).
     * @param onReady Optional callback fired on the thread TTS initialises on.
     */
    fun initTts(onReady: (() -> Unit)? = null) {
        if (ttsReady) { onReady?.invoke(); return }
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(Constants.TTS_SPEECH_RATE)
                ttsReady = true
                Log.d(TAG, "TTS ready")
                onReady?.invoke()
            } else {
                Log.e(TAG, "TTS initialisation failed: $status")
            }
        }
    }

    /**
     * Speaks [text] and suspends until the utterance finishes (or is cancelled).
     * Safe to call before TTS is ready — it will resume immediately with a warning.
     */
    suspend fun speak(text: String) = suspendCancellableCoroutine { cont ->
        if (!ttsReady) {
            Log.w(TAG, "speak() called before TTS ready, skipping: $text")
            cont.resume(Unit)
            return@suspendCancellableCoroutine
        }

        val utteranceId = UUID.randomUUID().toString()

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {}
            override fun onDone(id: String) {
                if (id == utteranceId && cont.isActive) cont.resume(Unit)
            }
            @Deprecated("Deprecated in API 21", ReplaceWith("onError(utteranceId, errorCode)"))
            override fun onError(id: String) {
                if (id == utteranceId && cont.isActive) cont.resume(Unit)
            }
        })

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)

        cont.invokeOnCancellation { tts?.stop() }
    }

    /** Stops any in-progress TTS immediately. */
    fun stopSpeaking() = tts?.stop()

    // ── STT ───────────────────────────────────────────────────────────────────

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Listens for one utterance and returns the best recognised string,
     * or null if recognition fails, times out, or the coroutine is cancelled.
     *
     * The raw result is untrimmed — use BrailleEngine.validateSpeech() to
     * normalise it ("Letter B" → 'B').
     *
     * Must be called from the main thread (SpeechRecognizer is main-thread only).
     */
    suspend fun listen(): String? = suspendCancellableCoroutine { cont ->
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {

            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()
                Log.d(TAG, "STT result: $best")
                cleanup(recognizer)
                if (cont.isActive) cont.resume(best)
            }

            override fun onError(error: Int) {
                Log.w(TAG, "STT error code: $error")
                cleanup(recognizer)
                if (cont.isActive) cont.resume(null)
            }

            // ── Unused but required by the interface ──
            override fun onReadyForSpeech(params: Bundle) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partial: Bundle) {}
            override fun onEvent(eventType: Int, params: Bundle) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Shorter silence cutoff feels snappier for single-letter responses
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1500L
            )
        }
        recognizer.startListening(intent)

        cont.invokeOnCancellation {
            recognizer.stopListening()
            cleanup(recognizer)
        }
    }

    /** Stops an in-progress listen without destroying the recognizer. */
    fun stopListening() = speechRecognizer?.stopListening()

    private fun cleanup(recognizer: SpeechRecognizer) {
        recognizer.destroy()
        if (speechRecognizer === recognizer) speechRecognizer = null
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Call this from ViewModel.onCleared() or Fragment.onDestroyView(). */
    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    companion object {
        private const val TAG = "SpeechManager"
    }
}