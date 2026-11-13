package com.example.security

import android.content.Context
import android.media.MediaDataSource
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Provides authenticated at-rest encryption (AES-256-GCM) for all files and sensitive upload
 * metadata ingested into VersePulse (uploaded music tracks, stage videos, video thumbnails,
 * studio vocal recordings, and cloud-synced local media path descriptors).
 *
 * File Format ("VPENC1" chunked AEAD container):
 * - Bytes 0..5   (6B) : ASCII magic "VPENC1"
 * - Byte  6      (1B) : Container format version (0x01)
 * - Byte  7      (1B) : Chunk shift exponent (16 -> 65,536-byte plaintext chunks)
 * - Bytes 8..19  (12B): Per-file CSPRNG base IV
 * - Bytes 20..27 (8B) : Original plaintext byte length (big-endian Int64)
 * - Chunks 0..N       : Each chunk encrypts up to 64 KB of plaintext using AES/GCM/NoPadding
 *                       with a unique per-chunk derived 96-bit nonce and 128-bit GCM auth tag,
 *                       binding chunk index and final-chunk flag in AAD to prevent reordering
 *                       or truncation attacks while enabling O(1) random-access MediaDataSource
 *                       playback without writing decrypted plaintext back to disk.
 */
object UploadEncryptionManager {

    private const val TAG = "UploadEncryptionMgr"

    const val ALGORITHM_DISPLAY_NAME = "AES-256-GCM"
    const val ENCRYPTED_FILE_EXTENSION = ".vpenc"
    const val FIELD_CIPHERTEXT_PREFIX = "vp_enc1:"

    private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEYSTORE_KEY_ALIAS = "versepulse_upload_master_key_v1"
    private const val FALLBACK_PREFS_NAME = "versepulse_upload_key_vault_prefs"
    private const val FALLBACK_PREFS_KEY = "fallback_aes256_key_b64"
    private const val ENCRYPTED_SETTINGS_PREFS_NAME = "versepulse_encrypted_settings_prefs"
    private const val PREF_KEY_ENCRYPTION_ENABLED = "vault_encryption_enabled_v1"

    private const val AES_ALGORITHM = "AES"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val AES_KEY_SIZE_BITS = 256
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8

    private val MAGIC_BYTES = "VPENC1".toByteArray(StandardCharsets.US_ASCII) // 6 bytes
    private const val CONTAINER_VERSION: Byte = 1
    private const val DEFAULT_CHUNK_SHIFT: Int = 16 // 1 << 16 = 65,536 bytes (64 KB)
    private const val DEFAULT_CHUNK_SIZE: Int = 1 shl DEFAULT_CHUNK_SHIFT
    internal const val HEADER_SIZE_BYTES: Int = 28 // 6 + 1 + 1 + 12 + 8

    private val FIELD_AAD = "VPFIELD1".toByteArray(StandardCharsets.US_ASCII)

    private val secureRandom = SecureRandom()
    private val keyLock = Any()
    private val settingsLock = Any()

    @Volatile
    private var boundAppContext: Context? = null

    @Volatile
    private var cachedFallbackSecretKey: SecretKey? = null

    @Volatile
    private var keystoreHardwareActive: Boolean = false

    @Volatile
    private var settingsInitialized: Boolean = false

    private val _encryptionEnabledFlow = kotlinx.coroutines.flow.MutableStateFlow(true)
    val encryptionEnabledFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _encryptionEnabledFlow

    data class EncryptionStatusSnapshot(
        val isEnabled: Boolean = true,
        val algorithm: String = ALGORITHM_DISPLAY_NAME,
        val isKeystoreBacked: Boolean = false,
        val keyBackingLabel: String = "Android Keystore AES-256",
        val tlsTransportEnforced: Boolean = true,
        val encryptedFilesCount: Int = 0,
        val unencryptedFilesCount: Int = 0,
        val encryptedBytesTotal: Long = 0L,
        val lastMigratedFilesCount: Int = 0
    )

    private val _statusSnapshotFlow = kotlinx.coroutines.flow.MutableStateFlow(EncryptionStatusSnapshot())
    val statusSnapshotFlow: kotlinx.coroutines.flow.StateFlow<EncryptionStatusSnapshot> = _statusSnapshotFlow

    data class EncryptionResult(
        val outputFile: File,
        val plaintextSizeBytes: Long,
        val encryptedSizeBytes: Long,
        val sha256ShortHex: String,
        val algorithm: String = ALGORITHM_DISPLAY_NAME
    )

    /**
     * Binds the application [Context] so static deserializers and media engines can resolve
     * the same key material when AndroidKeyStore is unavailable (e.g. local JVM / Robolectric).
     */
    fun bindContext(context: Context?) {
        val appCtx = context?.applicationContext ?: context ?: return
        val wasUnbound = boundAppContext == null
        boundAppContext = appCtx
        if (wasUnbound || !settingsInitialized) {
            synchronized(settingsLock) {
                if (!settingsInitialized) {
                    settingsInitialized = true
                    val persisted = getEncryptedPref(appCtx, PREF_KEY_ENCRYPTION_ENABLED, null)
                    val resolvedEnabled = if (persisted == null) {
                        putEncryptedPref(appCtx, PREF_KEY_ENCRYPTION_ENABLED, "true")
                        true
                    } else {
                        persisted.equals("true", ignoreCase = true)
                    }
                    _encryptionEnabledFlow.value = resolvedEnabled
                    _statusSnapshotFlow.value = _statusSnapshotFlow.value.copy(isEnabled = resolvedEnabled)
                }
            }
        }
    }

    /**
     * Returns whether app-wide upload & media encryption is enabled. Defaults to `true`.
     */
    fun isEncryptionEnabled(context: Context? = null): Boolean {
        if (context != null) {
            bindContext(context)
        }
        return _encryptionEnabledFlow.value
    }

    /**
     * Enables or pauses app-wide upload & field encryption and persists the choice encrypted at rest.
     */
    fun setEncryptionEnabled(context: Context?, enabled: Boolean) {
        val resolvedCtx = context?.applicationContext ?: context ?: boundAppContext
        if (resolvedCtx != null) {
            bindContext(resolvedCtx)
            synchronized(settingsLock) {
                _encryptionEnabledFlow.value = enabled
                putEncryptedPref(resolvedCtx, PREF_KEY_ENCRYPTION_ENABLED, enabled.toString())
            }
            refreshVaultStatus(resolvedCtx)
        } else {
            _encryptionEnabledFlow.value = enabled
            _statusSnapshotFlow.value = _statusSnapshotFlow.value.copy(isEnabled = enabled)
        }
    }

    /**
     * Stores an arbitrary preference value encrypted at rest using AES-256-GCM (`vp_enc1:` envelope).
     */
    fun putEncryptedPref(context: Context?, key: String, value: String) {
        val resolvedCtx = context?.applicationContext ?: context ?: boundAppContext ?: return
        runCatching {
            val cipherValue = encryptEnvelopeInternal(resolvedCtx, value) ?: return
            resolvedCtx.getSharedPreferences(ENCRYPTED_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(key, cipherValue)
                .apply()
        }
    }

    /**
     * Reads and authenticates an AES-256-GCM encrypted preference value.
     */
    fun getEncryptedPref(context: Context?, key: String, defaultValue: String? = null): String? {
        val resolvedCtx = context?.applicationContext ?: context ?: boundAppContext ?: return defaultValue
        return runCatching {
            val rawStored = resolvedCtx.getSharedPreferences(ENCRYPTED_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(key, null) ?: return defaultValue
            decryptField(resolvedCtx, rawStored) ?: defaultValue
        }.getOrDefault(defaultValue)
    }

    /**
     * Scans `filesDir/uploads` and encrypts any unencrypted files in place into authenticated
     * VPENC1 AES-256-GCM containers, securely wiping plaintext bytes while preserving file paths.
     */
    fun ensureExistingUploadsEncrypted(context: Context? = null): Int {
        val resolvedCtx = context?.applicationContext ?: context ?: boundAppContext ?: return 0
        bindContext(resolvedCtx)
        // Ensure master key is provisioned
        getOrCreateSecretKey(resolvedCtx)

        val uploadsDir = File(resolvedCtx.filesDir, "uploads")
        var migratedCount = 0
        if (uploadsDir.exists() && uploadsDir.isDirectory) {
            val candidates = uploadsDir.listFiles()?.filter { file ->
                file.isFile &&
                    !file.name.endsWith(".vpenc.tmp") &&
                    !isEncryptedFile(file)
            }.orEmpty()

            for (plainFile in candidates) {
                val tempEncrypted = File(uploadsDir, "${plainFile.name}.vpenc.tmp")
                try {
                    encryptFileAndDeleteSource(
                        context = resolvedCtx,
                        sourcePlainFile = plainFile,
                        destinationFile = tempEncrypted
                    )
                    if (!tempEncrypted.renameTo(plainFile)) {
                        tempEncrypted.copyTo(plainFile, overwrite = true)
                        runCatching { tempEncrypted.delete() }
                    }
                    migratedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping migration for ${plainFile.name}", e)
                    runCatching { tempEncrypted.delete() }
                }
            }
        }
        refreshVaultStatus(resolvedCtx, migratedCountDelta = migratedCount)
        return migratedCount
    }

    /**
     * Computes and emits the current [EncryptionStatusSnapshot] for UI telemetry.
     */
    fun refreshVaultStatus(context: Context? = null, migratedCountDelta: Int = 0): EncryptionStatusSnapshot {
        val resolvedCtx = context?.applicationContext ?: context ?: boundAppContext
        if (resolvedCtx != null) {
            getOrCreateSecretKey(resolvedCtx)
        }
        var encCount = 0
        var plainCount = 0
        var encBytes = 0L

        if (resolvedCtx != null) {
            val vaultDirs = listOf(
                File(resolvedCtx.filesDir, "uploads"),
                File(resolvedCtx.filesDir, "offline_cache")
            )
            vaultDirs.forEach { dir ->
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { f ->
                        if (f.isFile && !f.name.endsWith(".vpenc.tmp")) {
                            if (isEncryptedFile(f)) {
                                encCount++
                                encBytes += f.length()
                            } else {
                                plainCount++
                            }
                        }
                    }
                }
            }
        }

        val tlsEnforced = runCatching {
            !android.security.NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted
        }.getOrDefault(true)

        val snapshot = EncryptionStatusSnapshot(
            isEnabled = _encryptionEnabledFlow.value,
            algorithm = ALGORITHM_DISPLAY_NAME,
            isKeystoreBacked = keystoreHardwareActive,
            keyBackingLabel = if (keystoreHardwareActive) {
                "Android Hardware Keystore"
            } else {
                "App-Private AES-256 Vault"
            },
            tlsTransportEnforced = tlsEnforced,
            encryptedFilesCount = encCount,
            unencryptedFilesCount = plainCount,
            encryptedBytesTotal = encBytes,
            lastMigratedFilesCount = if (migratedCountDelta > 0) {
                migratedCountDelta
            } else {
                _statusSnapshotFlow.value.lastMigratedFilesCount
            }
        )
        _statusSnapshotFlow.value = snapshot
        return snapshot
    }

    /**
     * Resolves the 256-bit AES-GCM [SecretKey] from Android's hardware-backed Keystore,
     * falling back to an app-private 256-bit key in environments without AndroidKeyStore
     * (such as local JVM / Robolectric unit tests).
     */
    fun getOrCreateSecretKey(context: Context? = null): SecretKey {
        bindContext(context)
        synchronized(keyLock) {
            // 1. Prefer hardware-backed AndroidKeyStore on Android devices
            try {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER)
                keyStore.load(null)
                val existingEntry = keyStore.getEntry(KEYSTORE_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
                if (existingEntry != null) {
                    keystoreHardwareActive = true
                    return existingEntry.secretKey
                }

                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE_PROVIDER
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEYSTORE_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(AES_KEY_SIZE_BITS)
                    .setRandomizedEncryptionRequired(false) // Required so we can supply per-chunk derived nonces safely
                    .build()
                keyGenerator.init(spec)
                val generated = keyGenerator.generateKey()
                keystoreHardwareActive = true
                return generated
            } catch (_: Exception) {
                // AndroidKeyStore provider is unavailable in pure JVM / Robolectric environments
                keystoreHardwareActive = false
            }

            // 2. Fallback for JVM / Robolectric or restricted keystore environments
            cachedFallbackSecretKey?.let { return it }

            val resolvedCtx = context?.applicationContext ?: context ?: boundAppContext
            if (resolvedCtx != null) {
                try {
                    val prefs = resolvedCtx.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
                    val existingB64 = prefs.getString(FALLBACK_PREFS_KEY, null)
                    if (!existingB64.isNullOrBlank()) {
                        val decoded = Base64.getDecoder().decode(existingB64)
                        if (decoded.size == AES_KEY_SIZE_BITS / 8) {
                            return SecretKeySpec(decoded, AES_ALGORITHM).also {
                                cachedFallbackSecretKey = it
                            }
                        }
                    }
                    val freshBytes = ByteArray(AES_KEY_SIZE_BITS / 8).also { secureRandom.nextBytes(it) }
                    prefs.edit()
                        .putString(FALLBACK_PREFS_KEY, Base64.getEncoder().encodeToString(freshBytes))
                        .apply()
                    return SecretKeySpec(freshBytes, AES_ALGORITHM).also {
                        cachedFallbackSecretKey = it
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to persist fallback key in SharedPreferences, using process key", e)
                }
            }

            val ephemeralBytes = ByteArray(AES_KEY_SIZE_BITS / 8).also { secureRandom.nextBytes(it) }
            return SecretKeySpec(ephemeralBytes, AES_ALGORITHM).also {
                cachedFallbackSecretKey = it
            }
        }
    }

    /**
     * Streams arbitrary-length [inputStream] data directly into [destinationFile] encrypted with
     * chunked AES-256-GCM so plaintext bytes never touch persistent storage.
     */
    @Throws(IOException::class)
    fun encryptStreamToFile(
        context: Context?,
        inputStream: InputStream,
        destinationFile: File
    ): EncryptionResult {
        val secretKey = getOrCreateSecretKey(context)
        destinationFile.parentFile?.let { parent ->
            if (!parent.exists()) parent.mkdirs()
        }

        val baseIv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val digest = MessageDigest.getInstance("SHA-256")
        var totalPlaintextBytes = 0L

        try {
            RandomAccessFile(destinationFile, "rw").use { raf ->
                raf.setLength(0L)
                // Write placeholder header with plaintextSize = 0; updated once stream completes
                raf.write(buildHeader(baseIv = baseIv, plaintextSize = 0L))

                val currentBuffer = ByteArray(DEFAULT_CHUNK_SIZE)
                var currentLen = readUpToChunk(inputStream, currentBuffer)
                var chunkIndex = 0

                if (currentLen <= 0) {
                    // Authenticate empty 0-byte payload with a single final GCM tag
                    val emptyEncrypted = encryptChunk(
                        secretKey = secretKey,
                        baseIv = baseIv,
                        chunkIndex = 0,
                        isFinalChunk = true,
                        plaintext = currentBuffer,
                        plaintextLength = 0
                    )
                    raf.write(emptyEncrypted)
                } else {
                    val nextBuffer = ByteArray(DEFAULT_CHUNK_SIZE)
                    var workingBuf = currentBuffer
                    var lookaheadBuf = nextBuffer

                    while (currentLen > 0) {
                        val nextLen = readUpToChunk(inputStream, lookaheadBuf)
                        val isFinal = nextLen <= 0

                        digest.update(workingBuf, 0, currentLen)
                        totalPlaintextBytes += currentLen.toLong()

                        val encryptedChunk = encryptChunk(
                            secretKey = secretKey,
                            baseIv = baseIv,
                            chunkIndex = chunkIndex,
                            isFinalChunk = isFinal,
                            plaintext = workingBuf,
                            plaintextLength = currentLen
                        )
                        raf.write(encryptedChunk)
                        chunkIndex++

                        val swap = workingBuf
                        workingBuf = lookaheadBuf
                        lookaheadBuf = swap
                        currentLen = nextLen
                    }
                }

                // Patch exact plaintext byte length into header bytes 20..27
                raf.seek(0L)
                raf.write(buildHeader(baseIv = baseIv, plaintextSize = totalPlaintextBytes))
            }
        } catch (e: Exception) {
            runCatching { destinationFile.delete() }
            throw if (e is IOException) e else IOException("Failed to encrypt upload stream", e)
        }

        val shaHex = digest.digest().take(6).joinToString("") { "%02x".format(it) }
        return EncryptionResult(
            outputFile = destinationFile,
            plaintextSizeBytes = totalPlaintextBytes,
            encryptedSizeBytes = destinationFile.length(),
            sha256ShortHex = shaHex
        )
    }

    /**
     * Encrypts an in-memory [ByteArray] (e.g. an extracted video frame thumbnail) to [destinationFile].
     */
    @Throws(IOException::class)
    fun encryptBytesToFile(
        context: Context?,
        bytes: ByteArray,
        destinationFile: File
    ): EncryptionResult {
        return ByteArrayInputStream(bytes).use { bais ->
            encryptStreamToFile(context, bais, destinationFile)
        }
    }

    /**
     * Encrypts an existing temporary/staging file (such as a completed MediaRecorder .m4a capture)
     * into [destinationFile] and securely wipes + deletes [sourcePlainFile].
     */
    @Throws(IOException::class)
    fun encryptFileAndDeleteSource(
        context: Context?,
        sourcePlainFile: File,
        destinationFile: File
    ): EncryptionResult {
        if (!sourcePlainFile.exists()) {
            throw IOException("Source file does not exist: ${sourcePlainFile.absolutePath}")
        }
        val result = try {
            FileInputStream(sourcePlainFile).use { fis ->
                encryptStreamToFile(context, fis, destinationFile)
            }
        } finally {
            secureDelete(sourcePlainFile)
        }
        return result
    }

    /**
     * Overwrites a temporary plaintext file with zeroes before unlinking it from the filesystem.
     */
    fun secureDelete(file: File?) {
        if (file == null || !file.exists()) return
        runCatching {
            val len = file.length()
            if (len > 0L) {
                FileOutputStream(file).use { fos ->
                    val zeroes = ByteArray(minOf(len, 16_384L).toInt())
                    var remaining = len
                    while (remaining > 0L) {
                        val toWrite = minOf(remaining, zeroes.size.toLong()).toInt()
                        fos.write(zeroes, 0, toWrite)
                        remaining -= toWrite
                    }
                    fos.fd.sync()
                }
            }
        }
        runCatching { file.delete() }
    }

    /**
     * Decrypts an encrypted VPENC1 file into a [ByteArray]. If [file] is not a VPENC1 container,
     * transparently returns its raw bytes for backward compatibility.
     */
    @Throws(IOException::class)
    fun decryptFileToBytes(context: Context?, file: File): ByteArray {
        if (!file.exists()) {
            throw IOException("Encrypted file not found: ${file.absolutePath}")
        }
        if (!isEncryptedFile(file)) {
            return file.readBytes()
        }

        createMediaDataSource(context, file).use { dataSource ->
            val totalSize = dataSource.size
            if (totalSize > Int.MAX_VALUE - 8) {
                throw IOException("Encrypted file too large to load into a single ByteArray ($totalSize bytes)")
            }
            val output = ByteArrayOutputStream(totalSize.toInt().coerceAtLeast(0))
            val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
            var position = 0L
            while (position < totalSize) {
                val read = dataSource.readAt(position, buffer, 0, buffer.size)
                if (read <= 0) break
                output.write(buffer, 0, read)
                position += read.toLong()
            }
            return output.toByteArray()
        }
    }

    /**
     * Creates a random-access [MediaDataSource] that decrypts 64 KB AES-256-GCM chunks in memory
     * on demand for [android.media.MediaPlayer] or [android.media.MediaMetadataRetriever].
     */
    @Throws(IOException::class)
    fun createMediaDataSource(context: Context?, file: File): MediaDataSource {
        val secretKey = getOrCreateSecretKey(context)
        return EncryptedFileMediaDataSource(file = file, secretKey = secretKey)
    }

    /**
     * Checks whether [file] starts with the 6-byte `"VPENC1"` authenticated container header.
     */
    fun isEncryptedFile(file: File?): Boolean {
        if (file == null || !file.exists() || !file.isFile || file.length() < HEADER_SIZE_BYTES) {
            return false
        }
        return try {
            FileInputStream(file).use { fis ->
                val magic = ByteArray(MAGIC_BYTES.size)
                val read = fis.read(magic)
                read == MAGIC_BYTES.size && magic.contentEquals(MAGIC_BYTES)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks whether a local file path or Firestore field value represents an encrypted upload.
     */
    fun isEncryptedPath(pathOrField: String?): Boolean {
        if (pathOrField.isNullOrBlank()) return false
        if (pathOrField.startsWith(FIELD_CIPHERTEXT_PREFIX)) return true
        if (pathOrField.endsWith(ENCRYPTED_FILE_EXTENSION, ignoreCase = true)) return true
        return isEncryptedFile(File(pathOrField))
    }

    /**
     * Encrypts a sensitive string field (such as a local upload path) with AES-256-GCM before
     * storing in Cloud Firestore when app encryption is enabled. Idempotent if [plaintext] already
     * carries [FIELD_CIPHERTEXT_PREFIX].
     */
    fun encryptField(context: Context?, plaintext: String?): String? {
        if (plaintext.isNullOrBlank()) return plaintext
        if (plaintext.startsWith(FIELD_CIPHERTEXT_PREFIX)) return plaintext
        if (!isEncryptionEnabled(context)) return plaintext
        return encryptEnvelopeInternal(context, plaintext) ?: plaintext
    }

    internal fun encryptEnvelopeInternal(context: Context?, plaintext: String?): String? {
        if (plaintext.isNullOrBlank()) return plaintext
        if (plaintext.startsWith(FIELD_CIPHERTEXT_PREFIX)) return plaintext
        return try {
            val secretKey = getOrCreateSecretKey(context)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val actualIv = if (iv != null && iv.size == GCM_IV_LENGTH_BYTES) {
                iv
            } else {
                val generatedIv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    secretKey,
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, generatedIv)
                )
                generatedIv
            }
            cipher.updateAAD(FIELD_AAD)
            val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(actualIv.size + ciphertext.size)
            System.arraycopy(actualIv, 0, combined, 0, actualIv.size)
            System.arraycopy(ciphertext, 0, combined, actualIv.size, ciphertext.size)
            FIELD_CIPHERTEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(combined)
        } catch (e: Exception) {
            Log.e(TAG, "Field encryption failed", e)
            null
        }
    }

    /**
     * Decrypts a [FIELD_CIPHERTEXT_PREFIX]-encoded string field read from Cloud Firestore.
     * Legacy plaintext values without the prefix are returned unchanged. If an encrypted field
     * cannot be authenticated with this device's key (e.g. a device-local path uploaded from a
     * different device), returns `null` safely so unreadable ciphertext never leaks into File APIs.
     */
    fun decryptField(context: Context?, storedValue: String?): String? {
        if (storedValue.isNullOrBlank()) return storedValue
        if (!storedValue.startsWith(FIELD_CIPHERTEXT_PREFIX)) return storedValue
        return try {
            val encodedPayload = storedValue.removePrefix(FIELD_CIPHERTEXT_PREFIX)
            val combined = Base64.getUrlDecoder().decode(encodedPayload)
            if (combined.size < GCM_IV_LENGTH_BYTES + GCM_TAG_LENGTH_BYTES) {
                return null
            }
            val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)

            val secretKey = getOrCreateSecretKey(context)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            )
            cipher.updateAAD(FIELD_AAD)
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun readUpToChunk(input: InputStream, buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read < 0) break
            total += read
        }
        return total
    }

    private fun buildHeader(baseIv: ByteArray, plaintextSize: Long): ByteArray {
        val header = ByteBuffer.allocate(HEADER_SIZE_BYTES)
        header.put(MAGIC_BYTES)
        header.put(CONTAINER_VERSION)
        header.put(DEFAULT_CHUNK_SHIFT.toByte())
        header.put(baseIv, 0, GCM_IV_LENGTH_BYTES)
        header.putLong(plaintextSize)
        return header.array()
    }

    internal fun deriveChunkNonce(baseIv: ByteArray, chunkIndex: Int): ByteArray {
        val nonce = baseIv.copyOf(GCM_IV_LENGTH_BYTES)
        val tailInt = ByteBuffer.wrap(nonce, 8, 4).int xor chunkIndex
        ByteBuffer.wrap(nonce, 8, 4).putInt(tailInt)
        return nonce
    }

    internal fun buildChunkAad(chunkIndex: Int, isFinalChunk: Boolean, version: Byte = CONTAINER_VERSION): ByteArray {
        return ByteBuffer.allocate(14)
            .put(MAGIC_BYTES)
            .putInt(chunkIndex)
            .put(if (isFinalChunk) 1.toByte() else 0.toByte())
            .put(version)
            .putShort(0)
            .array()
    }

    @Throws(IOException::class)
    private fun encryptChunk(
        secretKey: SecretKey,
        baseIv: ByteArray,
        chunkIndex: Int,
        isFinalChunk: Boolean,
        plaintext: ByteArray,
        plaintextLength: Int
    ): ByteArray {
        return try {
            val chunkNonce = deriveChunkNonce(baseIv, chunkIndex)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(
                Cipher.ENCRYPT_MODE,
                secretKey,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, chunkNonce)
            )
            cipher.updateAAD(buildChunkAad(chunkIndex, isFinalChunk))
            cipher.doFinal(plaintext, 0, plaintextLength)
        } catch (e: Exception) {
            throw IOException("AES-GCM chunk encryption failed at chunk $chunkIndex", e)
        }
    }

    /**
     * Thread-safe random-access [MediaDataSource] over a VPENC1 chunked AES-256-GCM file.
     */
    internal class EncryptedFileMediaDataSource(
        private val file: File,
        private val secretKey: SecretKey
    ) : MediaDataSource() {

        private val lock = Any()
        private val raf: RandomAccessFile = RandomAccessFile(file, "r")
        private val version: Byte
        private val plaintextChunkSize: Int
        private val encryptedChunkSize: Int
        private val baseIv: ByteArray
        private val plaintextSize: Long
        private val totalChunks: Int

        private var cachedChunkIndex: Int = -1
        private var cachedPlaintextChunk: ByteArray = ByteArray(0)
        private var isClosed: Boolean = false

        init {
            try {
                if (raf.length() < HEADER_SIZE_BYTES) {
                    throw IOException("Corrupted encrypted container: file shorter than header")
                }
                val headerBytes = ByteArray(HEADER_SIZE_BYTES)
                raf.seek(0L)
                raf.readFully(headerBytes)
                val headerBuf = ByteBuffer.wrap(headerBytes)

                val magic = ByteArray(MAGIC_BYTES.size)
                headerBuf.get(magic)
                if (!magic.contentEquals(MAGIC_BYTES)) {
                    throw IOException("Invalid encrypted container magic header")
                }

                version = headerBuf.get()
                if (version != CONTAINER_VERSION) {
                    throw IOException("Unsupported encrypted container version: $version")
                }

                val chunkShift = headerBuf.get().toInt()
                if (chunkShift < 10 || chunkShift > 22) {
                    throw IOException("Invalid chunk shift exponent: $chunkShift")
                }
                plaintextChunkSize = 1 shl chunkShift
                encryptedChunkSize = plaintextChunkSize + GCM_TAG_LENGTH_BYTES

                baseIv = ByteArray(GCM_IV_LENGTH_BYTES)
                headerBuf.get(baseIv)

                plaintextSize = headerBuf.long
                if (plaintextSize < 0L) {
                    throw IOException("Negative plaintext size in encrypted container")
                }

                totalChunks = if (plaintextSize == 0L) {
                    1
                } else {
                    ((plaintextSize + plaintextChunkSize - 1L) / plaintextChunkSize).toInt()
                }

                val expectedEncryptedBytes = if (plaintextSize == 0L) {
                    HEADER_SIZE_BYTES.toLong() + GCM_TAG_LENGTH_BYTES
                } else {
                    HEADER_SIZE_BYTES.toLong() + plaintextSize + (totalChunks.toLong() * GCM_TAG_LENGTH_BYTES)
                }
                if (raf.length() != expectedEncryptedBytes) {
                    throw IOException(
                        "Encrypted container truncated or tampered (expected $expectedEncryptedBytes bytes, found ${raf.length()})"
                    )
                }

                // Eagerly authenticate chunk 0 so tampered empty/small files fail fast if inspected
                if (plaintextSize == 0L) {
                    decryptChunkLocked(0)
                }
            } catch (e: Exception) {
                runCatching { raf.close() }
                throw if (e is IOException) e else IOException("Failed to open encrypted MediaDataSource", e)
            }
        }

        override fun getSize(): Long = plaintextSize

        @Throws(IOException::class)
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) return 0
            if (position < 0L || offset < 0 || size < 0 || offset + size > buffer.size) {
                throw IndexOutOfBoundsException("Invalid readAt args: pos=$position, offset=$offset, size=$size")
            }

            synchronized(lock) {
                if (isClosed) throw IOException("EncryptedFileMediaDataSource is closed")
                if (position >= plaintextSize) return -1

                val maxReadable = minOf(size.toLong(), plaintextSize - position).toInt()
                var totalCopied = 0
                var currPos = position
                var dstOffset = offset

                while (totalCopied < maxReadable) {
                    val chunkIndex = (currPos / plaintextChunkSize).toInt()
                    val offsetInChunk = (currPos % plaintextChunkSize).toInt()

                    val plainChunk = if (chunkIndex == cachedChunkIndex) {
                        cachedPlaintextChunk
                    } else {
                        decryptChunkLocked(chunkIndex).also {
                            cachedChunkIndex = chunkIndex
                            cachedPlaintextChunk = it
                        }
                    }

                    val availableInChunk = plainChunk.size - offsetInChunk
                    if (availableInChunk <= 0) break

                    val toCopy = minOf(maxReadable - totalCopied, availableInChunk)
                    System.arraycopy(plainChunk, offsetInChunk, buffer, dstOffset, toCopy)
                    totalCopied += toCopy
                    currPos += toCopy.toLong()
                    dstOffset += toCopy
                }

                return if (totalCopied == 0) -1 else totalCopied
            }
        }

        @Throws(IOException::class)
        private fun decryptChunkLocked(chunkIndex: Int): ByteArray {
            if (chunkIndex < 0 || chunkIndex >= totalChunks) {
                throw IOException("Chunk index out of bounds: $chunkIndex / $totalChunks")
            }

            val isFinalChunk = (chunkIndex == totalChunks - 1)
            val expectedPlainLen = if (plaintextSize == 0L) {
                0
            } else if (!isFinalChunk) {
                plaintextChunkSize
            } else {
                val rem = (plaintextSize % plaintextChunkSize).toInt()
                if (rem == 0) plaintextChunkSize else rem
            }

            val encryptedLen = expectedPlainLen + GCM_TAG_LENGTH_BYTES
            val diskOffset = HEADER_SIZE_BYTES.toLong() + chunkIndex.toLong() * encryptedChunkSize.toLong()

            val cipherBytes = ByteArray(encryptedLen)
            raf.seek(diskOffset)
            raf.readFully(cipherBytes)

            return try {
                val nonce = deriveChunkNonce(baseIv, chunkIndex)
                val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    secretKey,
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce)
                )
                cipher.updateAAD(buildChunkAad(chunkIndex, isFinalChunk, version))
                cipher.doFinal(cipherBytes)
            } catch (e: AEADBadTagException) {
                throw IOException("Encrypted upload failed AES-GCM authentication at chunk $chunkIndex", e)
            } catch (e: Exception) {
                throw IOException("Failed to decrypt upload chunk $chunkIndex", e)
            }
        }

        @Throws(IOException::class)
        override fun close() {
            synchronized(lock) {
                if (!isClosed) {
                    isClosed = true
                    cachedPlaintextChunk.fill(0)
                    cachedPlaintextChunk = ByteArray(0)
                    cachedChunkIndex = -1
                    raf.close()
                }
            }
        }
    }
}
