package com.example.audio

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.UploadedVideoInfo
import com.example.security.UploadEncryptionManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream

object VideoUploadHelper {

    private const val TAG = "VideoUploadHelper"

    fun processVideoUri(context: Context, uri: Uri): UploadedVideoInfo? {
        return try {
            UploadEncryptionManager.bindContext(context)
            val contentResolver = context.contentResolver
            val nowMs = System.currentTimeMillis()

            var displayName = "stage_video_$nowMs.mp4"
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
                    "video_${nowMs}_${safeFileName}${UploadEncryptionManager.ENCRYPTED_FILE_EXTENSION}"
                } else {
                    "video_${nowMs}_${safeFileName}"
                }
            )

            var isEncrypted = false
            var encryptionAlgo = "Unencrypted"
            var shaShort = ""

            if (shouldEncrypt) {
                // Stream-encrypt uploaded video directly to internal storage via AES-256-GCM
                val encryptionResult = contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
                    UploadEncryptionManager.encryptStreamToFile(
                        context = context,
                        inputStream = inputStream,
                        destinationFile = destinationFile
                    )
                } ?: run {
                    Log.e(TAG, "Failed to open input stream for video URI: $uri")
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
                    Log.e(TAG, "Failed to open input stream for video URI: $uri")
                    return null
                }
                if (fileSizeBytes <= 0) {
                    fileSizeBytes = destinationFile.length()
                }
            }

            var durationSec = 120
            val baseStem = displayName.substringBeforeLast('.', displayName)
            var detectedTitle = baseStem
                .replace(Regex("^video_\\d+_"), "")
                .replace("_", " ")
                .trim()
                .ifBlank { "Stage Video" }
            var widthPx = 0
            var heightPx = 0
            var thumbnailPath: String? = null

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

                    widthPx = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull() ?: 0
                    heightPx = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull() ?: 0

                    val frameBitmap = retriever.getFrameAtTime(
                        500_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )
                    if (frameBitmap != null) {
                        val jpegBytes = ByteArrayOutputStream().use { thumbOut ->
                            frameBitmap.compress(Bitmap.CompressFormat.JPEG, 82, thumbOut)
                            thumbOut.toByteArray()
                        }
                        frameBitmap.recycle()
                        if (jpegBytes.isNotEmpty()) {
                            val thumbFile = File(
                                uploadsDir,
                                if (shouldEncrypt) {
                                    "thumb_${nowMs}.jpg${UploadEncryptionManager.ENCRYPTED_FILE_EXTENSION}"
                                } else {
                                    "thumb_${nowMs}.jpg"
                                }
                            )
                            if (shouldEncrypt) {
                                UploadEncryptionManager.encryptBytesToFile(context, jpegBytes, thumbFile)
                            } else {
                                thumbFile.writeBytes(jpegBytes)
                            }
                            if (thumbFile.exists() && thumbFile.length() > 0L) {
                                thumbnailPath = thumbFile.absolutePath
                            }
                        }
                    }
                } finally {
                    runCatching { encryptedMediaDataSource?.close() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract full video metadata", e)
            } finally {
                runCatching { retriever.release() }
            }

            UploadEncryptionManager.refreshVaultStatus(context)

            UploadedVideoInfo(
                localFilePath = destinationFile.absolutePath,
                thumbnailPath = thumbnailPath,
                fileName = displayName,
                detectedTitle = detectedTitle,
                durationSec = durationSec,
                widthPx = widthPx,
                heightPx = heightPx,
                fileSizeBytes = fileSizeBytes,
                isEncrypted = isEncrypted,
                encryptionAlgorithm = encryptionAlgo,
                contentSha256Short = shaShort
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing uploaded video URI", e)
            null
        }
    }
}
