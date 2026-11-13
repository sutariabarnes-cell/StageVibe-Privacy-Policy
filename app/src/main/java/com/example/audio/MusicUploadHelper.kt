package com.example.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.UploadedAudioInfo
import com.example.security.UploadEncryptionManager
import java.io.File
import java.io.InputStream

object MusicUploadHelper {

    private const val TAG = "MusicUploadHelper"

    fun processAudioUri(context: Context, uri: Uri): UploadedAudioInfo? {
        return try {
            UploadEncryptionManager.bindContext(context)
            val contentResolver = context.contentResolver
            val nowMs = System.currentTimeMillis()

            // 1. Resolve Display Name and File Size
            var displayName = "uploaded_music_$nowMs.mp3"
            var fileSizeBytes: Long = 0

            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                        displayName = cursor.getString(nameIndex)
                    }
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        fileSizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }

            // Clean file name and keep bounded length so encrypted path stays well within 500 chars
            val safeFileName = displayName
                .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                .take(64)
            val uploadsDir = File(context.filesDir, "uploads")
            if (!uploadsDir.exists()) {
                uploadsDir.mkdirs()
            }

            val shouldEncrypt = UploadEncryptionManager.isEncryptionEnabled(context)
            val destinationFile = File(
                uploadsDir,
                if (shouldEncrypt) {
                    "music_${nowMs}_${safeFileName}${UploadEncryptionManager.ENCRYPTED_FILE_EXTENSION}"
                } else {
                    "music_${nowMs}_${safeFileName}"
                }
            )

            var isEncrypted = false
            var encryptionAlgo = "Unencrypted"
            var shaShort = ""

            if (shouldEncrypt) {
                // 2. Stream-encrypt uploaded audio directly to internal storage via AES-256-GCM
                val encryptionResult = contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
                    UploadEncryptionManager.encryptStreamToFile(
                        context = context,
                        inputStream = inputStream,
                        destinationFile = destinationFile
                    )
                } ?: run {
                    Log.e(TAG, "Failed to open input stream for URI: $uri")
                    return null
                }
                if (fileSizeBytes <= 0) {
                    fileSizeBytes = encryptionResult.plaintextSizeBytes
                }
                isEncrypted = true
                encryptionAlgo = encryptionResult.algorithm
                shaShort = encryptionResult.sha256ShortHex
            } else {
                val copied = contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
                    destinationFile.outputStream().use { out ->
                        inputStream.copyTo(out)
                    }
                    true
                } ?: false
                if (!copied) {
                    Log.e(TAG, "Failed to open input stream for URI: $uri")
                    return null
                }
                if (fileSizeBytes <= 0) {
                    fileSizeBytes = destinationFile.length()
                }
            }

            UploadEncryptionManager.refreshVaultStatus(context)

            // 3. Extract Audio Metadata via MediaMetadataRetriever without writing plaintext to disk
            var durationSec = 180
            val baseStem = displayName.substringBeforeLast('.', displayName)
            var detectedTitle = baseStem
                .replace(Regex("^music_\\d+_"), "")
                .replace("_", " ")
                .trim()
                .ifBlank { "Uploaded Track" }
            var detectedArtist: String? = null
            var detectedGenre: String? = null

            val retriever = MediaMetadataRetriever()
            try {
                var dataSourceConfigured = false
                try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        retriever.setDataSource(pfd.fileDescriptor)
                        dataSourceConfigured = true
                    }
                } catch (_: Exception) {
                    dataSourceConfigured = false
                }

                val encryptedMediaDataSource = if (!dataSourceConfigured && isEncrypted) {
                    UploadEncryptionManager.createMediaDataSource(context, destinationFile).also { mds ->
                        retriever.setDataSource(mds)
                    }
                } else if (!dataSourceConfigured) {
                    retriever.setDataSource(destinationFile.absolutePath)
                    null
                } else {
                    null
                }

                try {
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    if (!durationStr.isNullOrBlank()) {
                        val durationMs = durationStr.toLongOrNull() ?: 0L
                        if (durationMs > 0) {
                            durationSec = (durationMs / 1000).toInt().coerceAtLeast(1)
                        }
                    }

                    val titleMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    if (!titleMeta.isNullOrBlank()) {
                        detectedTitle = titleMeta.trim()
                    }

                    val artistMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    if (!artistMeta.isNullOrBlank()) {
                        detectedArtist = artistMeta.trim()
                    }

                    val genreMeta = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    if (!genreMeta.isNullOrBlank()) {
                        detectedGenre = genreMeta.trim()
                    }
                } finally {
                    runCatching { encryptedMediaDataSource?.close() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract full audio metadata", e)
            } finally {
                runCatching { retriever.release() }
            }

            UploadedAudioInfo(
                localFilePath = destinationFile.absolutePath,
                fileName = displayName,
                detectedTitle = detectedTitle,
                detectedArtist = detectedArtist,
                detectedGenre = detectedGenre,
                durationSec = durationSec,
                fileSizeBytes = fileSizeBytes,
                isEncrypted = isEncrypted,
                encryptionAlgorithm = encryptionAlgo,
                contentSha256Short = shaShort
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing uploaded audio URI", e)
            null
        }
    }
}
