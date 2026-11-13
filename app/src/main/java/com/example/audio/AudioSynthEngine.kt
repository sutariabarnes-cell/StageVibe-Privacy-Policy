package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.util.Log
import com.example.model.SynthAudioType
import com.example.security.UploadEncryptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class AudioSynthEngine(
    private val context: Context? = null,
    private val onAmplitudeUpdated: (Float) -> Unit = {}
) {
    private val sampleRate = 22050
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var activeMediaDataSource: MediaDataSource? = null
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var isPlaying = false

    @Volatile
    private var currentPositionSeconds = 0

    fun play(audioType: SynthAudioType, customFilePath: String? = null, onProgress: (Int) -> Unit = {}) {
        stop()
        isPlaying = true

        if (customFilePath != null && File(customFilePath).exists()) {
            playRecordedFile(customFilePath, onProgress)
            return
        }

        playSynthesizedLoop(audioType, onProgress)
    }

    private fun playRecordedFile(filePath: String, onProgress: (Int) -> Unit) {
        try {
            val file = File(filePath)
            mediaPlayer = MediaPlayer().apply {
                if (UploadEncryptionManager.isEncryptedFile(file)) {
                    val dataSource = UploadEncryptionManager.createMediaDataSource(context, file)
                    activeMediaDataSource = dataSource
                    setDataSource(dataSource)
                } else {
                    setDataSource(filePath)
                }
                prepare()
                isLooping = true
                start()
            }
            playbackJob = scope.launch {
                var sec = 0
                while (isActive && isPlaying) {
                    val amp = (0.2f + (sin(System.currentTimeMillis() / 150.0).toFloat() * 0.15f)).coerceIn(0.1f, 0.9f)
                    onAmplitudeUpdated(amp)
                    onProgress(sec++)
                    kotlinx.coroutines.delay(200)
                }
            }
        } catch (e: Exception) {
            Log.e("AudioSynthEngine", "Error playing audio file", e)
            runCatching { activeMediaDataSource?.close() }
            activeMediaDataSource = null
            // Fallback to synthesized audio
            playSynthesizedLoop(SynthAudioType.POETRY_AMBIENCE, onProgress)
        }
    }

    private fun playSynthesizedLoop(type: SynthAudioType, onProgress: (Int) -> Unit) {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        playbackJob = scope.launch {
            val audioBuffer = ShortArray(1024)
            var sampleIndex = 0L
            var secCounter = 0
            var tickAccumulator = 0

            while (isActive && isPlaying) {
                var maxSampleAmp = 0f

                for (i in audioBuffer.indices) {
                    val t = sampleIndex.toDouble() / sampleRate
                    val sampleValue = generateSample(type, t, sampleIndex)
                    val shortVal = (sampleValue.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
                    audioBuffer[i] = shortVal

                    val absVal = kotlin.math.abs(sampleValue.toFloat())
                    if (absVal > maxSampleAmp) {
                        maxSampleAmp = absVal
                    }
                    sampleIndex++
                }

                audioTrack?.write(audioBuffer, 0, audioBuffer.size)

                // Push dynamic amplitude to visualizer
                onAmplitudeUpdated((maxSampleAmp * 1.2f).coerceIn(0.05f, 1f))

                tickAccumulator += audioBuffer.size
                if (tickAccumulator >= sampleRate) {
                    tickAccumulator = 0
                    secCounter++
                    currentPositionSeconds = secCounter
                    onProgress(secCounter)
                }
            }
        }
    }

    private fun generateSample(type: SynthAudioType, t: Double, sampleIndex: Long): Double {
        return when (type) {
            SynthAudioType.ACOUSTIC_SOUL -> {
                // Chord progression: Dm9 -> G13 -> Cmaj9 -> Am7 (2.0s per chord)
                val chordTime = t % 8.0
                val chordNotes = when {
                    chordTime < 2.0 -> doubleArrayOf(146.83, 220.0, 261.63, 329.63) // D3, A3, C4, E4
                    chordTime < 4.0 -> doubleArrayOf(98.0, 196.0, 246.94, 329.63)  // G2, G3, B3, E4
                    chordTime < 6.0 -> doubleArrayOf(130.81, 196.0, 246.94, 329.63) // C3, G3, B3, E4
                    else -> doubleArrayOf(110.0, 220.0, 261.63, 329.63)             // A2, A3, C4, E4
                }
                var chordOut = 0.0
                for (note in chordNotes) {
                    chordOut += 0.12 * sin(2.0 * PI * note * t)
                    chordOut += 0.04 * sin(4.0 * PI * note * t) // 2nd harmonic
                }
                // Gentle acoustic plucking flutter
                val pluck = (0.7 + 0.3 * sin(2.0 * PI * 2.0 * t))
                chordOut * pluck
            }

            SynthAudioType.HIPHOP_BEAT -> {
                // 85 BPM beat: beat duration ~ 0.705s
                val beatTime = t % 2.82 // 4 beats per bar
                val subBeat = (beatTime / 0.705)
                val beatIndex = subBeat.toInt()
                val fracBeat = subBeat - beatIndex

                // Kick on beats 0 and 2.5
                var kick = 0.0
                if (beatIndex == 0 || (beatIndex == 2 && fracBeat > 0.5)) {
                    val kTime = if (beatIndex == 0) fracBeat * 0.705 else (fracBeat - 0.5) * 0.705
                    if (kTime < 0.25) {
                        val pitch = 90.0 * exp(-kTime * 14.0) + 45.0
                        kick = 0.45 * sin(2.0 * PI * pitch * kTime) * exp(-kTime * 8.0)
                    }
                }

                // Snare on beats 1 and 3
                var snare = 0.0
                if (beatIndex == 1 || beatIndex == 3) {
                    val sTime = fracBeat * 0.705
                    if (sTime < 0.2) {
                        val noise = ((sampleIndex % 37).toDouble() / 18.5) - 1.0
                        val tone = sin(2.0 * PI * 185.0 * sTime)
                        snare = 0.3 * (noise * 0.7 + tone * 0.3) * exp(-sTime * 15.0)
                    }
                }

                // Hi-hat every 8th note
                val hatTime = (subBeat * 2.0) % 1.0
                var hat = 0.0
                if (hatTime < 0.08) {
                    val noise = ((sampleIndex % 23).toDouble() / 11.5) - 1.0
                    hat = 0.12 * noise * exp(-hatTime * 25.0)
                }

                // Rhodes background chord (Fm7 -> Bb7)
                val rhodesChord = if (beatTime < 1.41) 174.61 else 233.08
                val rhodes = 0.14 * sin(2.0 * PI * rhodesChord * t) + 0.06 * sin(2.0 * PI * (rhodesChord * 1.5) * t)

                kick + snare + hat + rhodes
            }

            SynthAudioType.POETRY_AMBIENCE -> {
                // Ethereal ambient pads for spoken word
                val baseFreq = 110.0 // A2
                val pad1 = sin(2.0 * PI * baseFreq * t) * 0.18
                val pad2 = sin(2.0 * PI * (baseFreq * 1.498) * t + 0.5) * 0.14 // Perfect fifth
                val pad3 = sin(2.0 * PI * (baseFreq * 2.24) * t) * 0.09
                val chime = sin(2.0 * PI * 880.0 * t) * (0.05 * sin(2.0 * PI * 0.3 * t).coerceAtLeast(0.0))
                val gentleBreathe = (0.75 + 0.25 * sin(2.0 * PI * 0.2 * t))
                (pad1 + pad2 + pad3 + chime) * gentleBreathe
            }

            SynthAudioType.SYNTH_CHILL -> {
                // Retro synth arpeggiator (120 BPM)
                val arpNotes = doubleArrayOf(220.0, 261.63, 329.63, 440.0, 392.0, 329.63, 261.63, 196.0)
                val noteIndex = ((t * 4.0).toInt()) % arpNotes.size
                val freq = arpNotes[noteIndex]
                val noteTime = (t * 4.0) % 1.0
                val env = exp(-noteTime * 3.5)
                val lead = 0.25 * (sin(2.0 * PI * freq * t) + 0.4 * sin(4.0 * PI * freq * t)) * env
                val bass = 0.18 * sin(2.0 * PI * 55.0 * t)
                lead + bass
            }

            SynthAudioType.PIANO_WARMTH -> {
                // Romantic piano chords with decaying harmonics
                val chordCycle = t % 6.0
                val root = if (chordCycle < 3.0) 130.81 else 146.83 // C3 or D3
                val third = root * 1.2599
                val fifth = root * 1.4983
                val noteTime = (chordCycle % 1.5)
                val decay = exp(-noteTime * 1.8)
                val p1 = sin(2.0 * PI * root * t) * 0.18
                val p2 = sin(2.0 * PI * third * t) * 0.14
                val p3 = sin(2.0 * PI * fifth * t) * 0.12
                (p1 + p2 + p3) * decay
            }

            SynthAudioType.CUSTOM_RECORDING -> {
                // Warm vocal resonance simulation with harmonic warmth
                0.22 * sin(2.0 * PI * 220.0 * t) + 0.12 * sin(2.0 * PI * 440.0 * t)
            }

            SynthAudioType.UPLOADED_MUSIC,
            SynthAudioType.UPLOADED_VIDEO -> {
                // Melodic indie-electronic groove loop
                val barTime = t % 4.0
                val chord = when {
                    barTime < 1.0 -> 261.63 // C4
                    barTime < 2.0 -> 329.63 // E4
                    barTime < 3.0 -> 392.00 // G4
                    else -> 220.00          // A3
                }
                val beat = (t * 2.0) % 1.0
                val env = exp(-beat * 4.0)
                val pulse = (0.2 * sin(2.0 * PI * chord * t) + 0.1 * sin(2.0 * PI * (chord * 0.5) * t)) * env
                val bass = 0.15 * sin(2.0 * PI * 65.41 * t)
                pulse + bass
            }
        }
    }

    fun stop() {
        isPlaying = false
        playbackJob?.cancel()
        playbackJob = null

        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("AudioSynthEngine", "Error stopping AudioTrack", e)
        }
        audioTrack = null

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("AudioSynthEngine", "Error stopping MediaPlayer", e)
        }
        mediaPlayer = null
        runCatching { activeMediaDataSource?.close() }
        activeMediaDataSource = null
        onAmplitudeUpdated(0f)
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying
}
