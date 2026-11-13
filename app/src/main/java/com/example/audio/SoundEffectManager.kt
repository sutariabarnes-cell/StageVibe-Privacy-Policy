package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * SoundEffectManager
 * Provides procedural, low-latency sound synthesis and playback for app interactions:
 * - Real-time audience voting chimes
 * - Live crowd cheer fanfares
 * - Melodic favorite / like pops
 * - Battle poll victory chimes
 * - Studio recording cues
 */
class SoundEffectManager {

    private val sampleRate = 22050
    private val scope = CoroutineScope(Dispatchers.Default)

    var isSoundEnabled: Boolean = true

    // Pre-synthesized PCM audio buffers for instant zero-latency playback
    private val voteSoundBuffer: ShortArray by lazy { synthesizeVoteChime() }
    private val cheerSoundBuffer: ShortArray by lazy { synthesizeCheerFanfare() }
    private val likeSoundBuffer: ShortArray by lazy { synthesizeLikePop() }
    private val pollVoteSoundBuffer: ShortArray by lazy { synthesizePollVoteFanfare() }
    private val cueBeepSoundBuffer: ShortArray by lazy { synthesizeCueBeep() }

    fun playVoteSound() {
        if (!isSoundEnabled) return
        playStaticPcm(voteSoundBuffer)
    }

    fun playCheerSound() {
        if (!isSoundEnabled) return
        playStaticPcm(cheerSoundBuffer)
    }

    fun playLikeSound() {
        if (!isSoundEnabled) return
        playStaticPcm(likeSoundBuffer)
    }

    fun playPollVoteSound() {
        if (!isSoundEnabled) return
        playStaticPcm(pollVoteSoundBuffer)
    }

    fun playCueBeep() {
        if (!isSoundEnabled) return
        playStaticPcm(cueBeepSoundBuffer)
    }

    private fun playStaticPcm(buffer: ShortArray) {
        scope.launch {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()

                // Release track after playback
                val durationMs = (buffer.size.toDouble() / sampleRate * 1000).toLong() + 100
                kotlinx.coroutines.delay(durationMs)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            } catch (e: Throwable) {
                try {
                    Log.w("SoundEffectManager", "Error playing sound effect: ${e.message}")
                } catch (_: Throwable) {
                    // Suppress unmocked Android Log in local JVM unit test environment
                }
            }
        }
    }

    /**
     * Bright ascending 3-note chime (C5 -> E5 -> G5) for real-time audience voting.
     */
    private fun synthesizeVoteChime(): ShortArray {
        val durationSec = 0.38
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        val note1Duration = 0.08
        val note2Duration = 0.08
        val f1 = 523.25 // C5
        val f2 = 659.25 // E5
        val f3 = 783.99 // G5

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val sample = when {
                t < note1Duration -> {
                    val env = 1.0 - (t / note1Duration) * 0.4
                    0.6 * sin(2.0 * PI * f1 * t) * env
                }
                t < (note1Duration + note2Duration) -> {
                    val noteTime = t - note1Duration
                    val env = 1.0 - (noteTime / note2Duration) * 0.4
                    0.65 * sin(2.0 * PI * f2 * noteTime) * env
                }
                else -> {
                    val noteTime = t - note1Duration - note2Duration
                    val decay = exp(-noteTime * 6.5)
                    // Fundamental + subtle octave overtone for bell shimmer
                    (0.7 * sin(2.0 * PI * f3 * noteTime) + 0.2 * sin(4.0 * PI * f3 * noteTime)) * decay
                }
            }
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return buffer
    }

    /**
     * Resonant brass crowd fanfare with cheering warmth for live cheers.
     */
    private fun synthesizeCheerFanfare(): ShortArray {
        val durationSec = 0.45
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val decay = exp(-t * 5.0)

            // Warm chord: D4 (293.66Hz) + F#4 (369.99Hz) + A4 (440.0Hz)
            val tone1 = sin(2.0 * PI * 293.66 * t)
            val tone2 = sin(2.0 * PI * 369.99 * t)
            val tone3 = sin(2.0 * PI * 440.00 * t)
            val chord = (tone1 * 0.35 + tone2 * 0.35 + tone3 * 0.3)

            // Crowd shimmer noise
            val noise = (((i * 1103515245L + 12345) and 0x7FFFFFFF) % 1000).toDouble() / 500.0 - 1.0
            val crowd = noise * 0.15 * exp(-t * 3.5)

            val sample = (chord * 0.85 + crowd) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return buffer
    }

    /**
     * Water-drop / bubble pop chime for favorites and likes.
     */
    private fun synthesizeLikePop(): ShortArray {
        val durationSec = 0.22
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val pitch = 620.0 + 340.0 * (t / durationSec) // rising frequency sweep
            val env = exp(-t * 14.0)
            val sample = 0.8 * sin(2.0 * PI * pitch * t) * env
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return buffer
    }

    /**
     * Triumphant major chord fanfare for battle poll votes.
     */
    private fun synthesizePollVoteFanfare(): ShortArray {
        val durationSec = 0.42
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        val f1 = 392.00 // G4
        val f2 = 493.88 // B4
        val f3 = 587.33 // D5
        val f4 = 783.99 // G5

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val decay = exp(-t * 5.5)
            val chord = 0.25 * sin(2.0 * PI * f1 * t) +
                    0.25 * sin(2.0 * PI * f2 * t) +
                    0.25 * sin(2.0 * PI * f3 * t) +
                    0.35 * sin(2.0 * PI * f4 * t)
            val sample = chord * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return buffer
    }

    /**
     * Quick studio countdown / recording cue beep.
     */
    private fun synthesizeCueBeep(): ShortArray {
        val durationSec = 0.12
        val totalSamples = (sampleRate * durationSec).toInt()
        val buffer = ShortArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / sampleRate
            val env = if (t < 0.01) t / 0.01 else exp(-(t - 0.01) * 20.0)
            val sample = 0.6 * sin(2.0 * PI * 880.0 * t) * env
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }
        return buffer
    }
}
