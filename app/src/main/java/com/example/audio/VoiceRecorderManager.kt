package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.security.UploadEncryptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

class VoiceRecorderManager(
    private val context: Context,
    private val onAmplitudeChanged: (Float) -> Unit = {}
) {
    private var recorder: MediaRecorder? = null
    private var stagingPlainFile: File? = null
    private var currentFilePath: String? = null
    private var meterJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    @Volatile
    var isRecording: Boolean = false
        private set

    fun startRecording(): String? {
        stopRecording()
        try {
            UploadEncryptionManager.bindContext(context)
            val stagingDir = File(context.filesDir, "secure_recording_staging")
            if (!stagingDir.exists()) {
                stagingDir.mkdirs()
            }
            val nowMs = System.currentTimeMillis()
            val stagingFile = File(stagingDir, "staging_rec_$nowMs.m4a")
            stagingPlainFile = stagingFile
            currentFilePath = stagingFile.absolutePath

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(stagingFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true

            meterJob = scope.launch {
                while (isActive && isRecording) {
                    val maxAmp = try {
                        recorder?.maxAmplitude ?: 0
                    } catch (_: Exception) {
                        0
                    }
                    val normalized = (maxAmp / 24000f).coerceIn(0.05f, 1f)
                    onAmplitudeChanged(normalized)
                    kotlinx.coroutines.delay(100)
                }
            }
            return currentFilePath
        } catch (e: Exception) {
            Log.e("VoiceRecorderManager", "Failed to start recording", e)
            recorder?.release()
            recorder = null
            UploadEncryptionManager.secureDelete(stagingPlainFile)
            stagingPlainFile = null
            currentFilePath = null
            isRecording = false
            return null
        }
    }

    fun stopRecording(): String? {
        if (!isRecording) return currentFilePath
        isRecording = false
        meterJob?.cancel()
        meterJob = null
        try {
            recorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.e("VoiceRecorderManager", "Error stopping recorder", e)
        }
        recorder = null
        onAmplitudeChanged(0f)

        val plainFile = stagingPlainFile
        stagingPlainFile = null
        if (plainFile != null && plainFile.exists() && plainFile.length() > 0L) {
            try {
                val uploadsDir = File(context.filesDir, "uploads")
                if (!uploadsDir.exists()) {
                    uploadsDir.mkdirs()
                }
                if (UploadEncryptionManager.isEncryptionEnabled(context)) {
                    val encryptedFile = File(
                        uploadsDir,
                        "recording_${System.currentTimeMillis()}.m4a${UploadEncryptionManager.ENCRYPTED_FILE_EXTENSION}"
                    )
                    UploadEncryptionManager.encryptFileAndDeleteSource(
                        context = context,
                        sourcePlainFile = plainFile,
                        destinationFile = encryptedFile
                    )
                    currentFilePath = encryptedFile.absolutePath
                } else {
                    val destPlain = File(uploadsDir, "recording_${System.currentTimeMillis()}.m4a")
                    plainFile.copyTo(destPlain, overwrite = true)
                    UploadEncryptionManager.secureDelete(plainFile)
                    currentFilePath = destPlain.absolutePath
                }
                UploadEncryptionManager.refreshVaultStatus(context)
            } catch (e: Exception) {
                Log.e("VoiceRecorderManager", "Failed to finalize recorded vocal file", e)
                UploadEncryptionManager.secureDelete(plainFile)
                currentFilePath = null
            }
        } else {
            UploadEncryptionManager.secureDelete(plainFile)
        }

        return currentFilePath
    }
}
