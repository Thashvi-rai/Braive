package com.example.brailleglove.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.brailleglove.R

/**
 * Plays the short "correct" / "wrong" feedback tones.
 *
 * The two clips (res/raw/correct_chime.wav, res/raw/wrong_tone.wav) are
 * originally synthesized sine-wave tones generated for this project —
 * a bright ascending C5-E5-G5 arpeggio for correct, a soft descending
 * A4-F4 interval for wrong. No external audio library or sample pack
 * was used.
 *
 * Uses SoundPool (not MediaPlayer) since these are short, low-latency
 * UI cues that may need to fire in quick succession.
 *
 * SoundPool.load() decodes asynchronously. If play() is called before
 * decoding finishes, the call is silently a no-op — the very first
 * correct/wrong answer in a session could be silent. This is tracked
 * via setOnLoadCompleteListener: if a play request comes in before a
 * clip has finished loading, it's queued and fired the moment loading
 * completes instead of being dropped.
 */
class SoundEffectsPlayer(context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val correctSoundId = soundPool.load(context, R.raw.correct_chime, 1)
    private val wrongSoundId = soundPool.load(context, R.raw.wrong_tone, 1)

    private val loadedSoundIds = mutableSetOf<Int>()
    private val pendingPlays = mutableSetOf<Int>()

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                loadedSoundIds.add(sampleId)
                if (pendingPlays.remove(sampleId)) {
                    playNow(sampleId)
                }
            }
        }
    }

    private fun playNow(soundId: Int) {
        soundPool.play(soundId, 0.9f, 0.9f, 1, 0, 1f)
    }

    private fun playWhenReady(soundId: Int) {
        if (soundId in loadedSoundIds) {
            playNow(soundId)
        } else {
            // Not decoded yet — play the instant load finishes instead of dropping it
            pendingPlays.add(soundId)
        }
    }

    /** Plays the bright ascending "correct" chime. */
    fun playCorrect() = playWhenReady(correctSoundId)

    /** Plays the soft descending "wrong" tone. */
    fun playWrong() = playWhenReady(wrongSoundId)

    /** Call from Application.onTerminate() or when no longer needed. */
    fun release() {
        soundPool.release()
    }
}
