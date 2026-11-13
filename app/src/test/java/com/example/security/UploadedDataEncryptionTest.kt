package com.example.security

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.audio.MusicUploadHelper
import com.example.audio.VideoUploadHelper
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.SynthAudioType
import com.example.model.UploadedAudioInfo
import com.example.model.UploadedVideoInfo
import com.example.model.VisualizerTheme
import com.example.ui.screens.CreatorStudioScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UploadedDataEncryptionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private val tempFiles = mutableListOf<File>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        UploadEncryptionManager.bindContext(context)
    }

    @After
    fun tearDown() {
        tempFiles.forEach { file ->
            runCatching { if (file.exists()) file.delete() }
        }
        tempFiles.clear()
    }

    @Test
    fun multiChunkStreamEncryption_hidesPlaintextOnDisk_andSupportsRandomAccessMediaDataSource() {
        val marker = "VERSEPULSE_SECRET_STAGE_UPLOAD_PAYLOAD_MARKER_2026".toByteArray(StandardCharsets.UTF_8)
        // 150 KB payload spanning 3 x 64 KB chunks
        val originalPlaintext = ByteArray(150_000) { idx ->
            if (idx < marker.size) marker[idx] else ((idx * 31 + 7) and 0xFF).toByte()
        }

        val encryptedFile = File.createTempFile("vp_multi_chunk_", ".vpenc").also { tempFiles.add(it) }
        val result = ByteArrayInputStream(originalPlaintext).use { input ->
            UploadEncryptionManager.encryptStreamToFile(context, input, encryptedFile)
        }

        assertEquals(originalPlaintext.size.toLong(), result.plaintextSizeBytes)
        assertTrue(UploadEncryptionManager.isEncryptedFile(encryptedFile))
        assertTrue(result.encryptedSizeBytes > result.plaintextSizeBytes)

        // Verify raw bytes on disk do NOT contain the plaintext marker
        val rawCipherBytes = encryptedFile.readBytes()
        val rawAsIsoString = String(rawCipherBytes, StandardCharsets.ISO_8859_1)
        val markerIsoString = String(marker, StandardCharsets.ISO_8859_1)
        assertFalse(
            "Plaintext marker must never appear in encrypted file on disk",
            rawAsIsoString.contains(markerIsoString)
        )

        // Full round-trip decryption
        val decryptedAll = UploadEncryptionManager.decryptFileToBytes(context, encryptedFile)
        assertArrayEquals(originalPlaintext, decryptedAll)

        // Random-access unaligned reads across 64 KB chunk boundaries (65536 bytes)
        UploadEncryptionManager.createMediaDataSource(context, encryptedFile).use { mds ->
            assertEquals(originalPlaintext.size.toLong(), mds.size)

            val slicePos = 65_500L // Straddles chunk 0 -> chunk 1 boundary at 65,536
            val sliceLen = 200
            val readBuf = ByteArray(sliceLen)
            val bytesRead = mds.readAt(slicePos, readBuf, 0, sliceLen)

            assertEquals(sliceLen, bytesRead)
            val expectedSlice = originalPlaintext.copyOfRange(slicePos.toInt(), slicePos.toInt() + sliceLen)
            assertArrayEquals(expectedSlice, readBuf)
        }
    }

    @Test
    fun tamperedCiphertext_failsAesGcmAuthentication() {
        val original = "Confidential unreleased studio vocal take".repeat(64).toByteArray(StandardCharsets.UTF_8)
        val encryptedFile = File.createTempFile("vp_tamper_", ".vpenc").also { tempFiles.add(it) }

        UploadEncryptionManager.encryptBytesToFile(context, original, encryptedFile)

        // Flip 1 bit inside the ciphertext body
        val tampered = encryptedFile.readBytes()
        val targetIndex = UploadEncryptionManager.HEADER_SIZE_BYTES + 10
        tampered[targetIndex] = (tampered[targetIndex].toInt() xor 0x01).toByte()
        encryptedFile.writeBytes(tampered)

        try {
            UploadEncryptionManager.decryptFileToBytes(context, encryptedFile)
            fail("Expected tampered AES-GCM upload container to throw IOException")
        } catch (expected: IOException) {
            assertTrue(expected.message?.contains("authentication", ignoreCase = true) == true || expected.cause != null)
        }
    }

    @Test
    fun musicAndVideoUploadHelpers_encryptUploadedFilesAtRest_andWipeStagingVocalFiles() {
        // 1. Audio URI upload
        val rawAudioBytes = "ID3_SYNTHETIC_AUDIO_STREAM_PAYLOAD_".repeat(250).toByteArray(StandardCharsets.UTF_8)
        val sourceAudioFile = File.createTempFile("source_song_", ".mp3").also {
            it.writeBytes(rawAudioBytes)
            tempFiles.add(it)
        }

        val audioInfo = MusicUploadHelper.processAudioUri(context, Uri.fromFile(sourceAudioFile))
        assertNotNull("Expected MusicUploadHelper to return UploadedAudioInfo", audioInfo)
        val storedAudioFile = File(audioInfo!!.localFilePath).also { tempFiles.add(it) }

        assertTrue(audioInfo.isEncrypted)
        assertEquals("AES-256-GCM", audioInfo.encryptionAlgorithm)
        assertTrue(storedAudioFile.name.endsWith(".vpenc"))
        assertTrue(UploadEncryptionManager.isEncryptedFile(storedAudioFile))
        assertFalse(storedAudioFile.readBytes().contentEquals(rawAudioBytes))
        assertArrayEquals(
            rawAudioBytes,
            UploadEncryptionManager.decryptFileToBytes(context, storedAudioFile)
        )

        // 2. Video URI upload
        val rawVideoBytes = "FTYP_ISOM_STAGE_VIDEO_PAYLOAD_".repeat(300).toByteArray(StandardCharsets.UTF_8)
        val sourceVideoFile = File.createTempFile("source_stage_", ".mp4").also {
            it.writeBytes(rawVideoBytes)
            tempFiles.add(it)
        }

        val videoInfo = VideoUploadHelper.processVideoUri(context, Uri.fromFile(sourceVideoFile))
        assertNotNull("Expected VideoUploadHelper to return UploadedVideoInfo", videoInfo)
        val storedVideoFile = File(videoInfo!!.localFilePath).also { tempFiles.add(it) }

        assertTrue(videoInfo.isEncrypted)
        assertEquals("AES-256-GCM", videoInfo.encryptionAlgorithm)
        assertTrue(storedVideoFile.name.endsWith(".vpenc"))
        assertTrue(UploadEncryptionManager.isEncryptedFile(storedVideoFile))
        assertArrayEquals(
            rawVideoBytes,
            UploadEncryptionManager.decryptFileToBytes(context, storedVideoFile)
        )

        // 3. Staging vocal file encryption + secure source wipe
        val rawVocalBytes = "M4A_STUDIO_VOCAL_RECORDING_DATA".repeat(120).toByteArray(StandardCharsets.UTF_8)
        val stagingVocalFile = File.createTempFile("staging_rec_", ".m4a").apply {
            writeBytes(rawVocalBytes)
        }
        val destVocalEncrypted = File.createTempFile("recording_final_", ".m4a.vpenc").also { tempFiles.add(it) }

        UploadEncryptionManager.encryptFileAndDeleteSource(context, stagingVocalFile, destVocalEncrypted)

        assertFalse("Staging plaintext vocal file must be deleted after encryption", stagingVocalFile.exists())
        assertTrue(UploadEncryptionManager.isEncryptedFile(destVocalEncrypted))
        assertArrayEquals(
            rawVocalBytes,
            UploadEncryptionManager.decryptFileToBytes(context, destVocalEncrypted)
        )
    }

    @Test
    fun firestoreFieldEncryption_usesRandomIv_fitsSchemaLimit_andRoundTrips() {
        val localPath = "/data/user/0/com.aistudio.versepulse.kxmyp/files/uploads/music_1731450000_track.mp3.vpenc"

        val cipherA = UploadEncryptionManager.encryptField(context, localPath)
        val cipherB = UploadEncryptionManager.encryptField(context, localPath)

        assertNotNull(cipherA)
        assertNotNull(cipherB)
        assertTrue(cipherA!!.startsWith(UploadEncryptionManager.FIELD_CIPHERTEXT_PREFIX))
        assertNotEquals("Randomized IV must produce distinct ciphertexts", cipherA, cipherB)
        assertTrue("Encrypted path must fit within 500-char Firestore rule limit", cipherA.length <= 500)
        assertFalse("Ciphertext must not expose plaintext path", cipherA.contains("uploads/music_"))

        assertEquals(localPath, UploadEncryptionManager.decryptField(context, cipherA))
        assertEquals(localPath, UploadEncryptionManager.decryptField(context, cipherB))
        assertEquals("legacy/plain/path.mp3", UploadEncryptionManager.decryptField(context, "legacy/plain/path.mp3"))
    }

    @Test
    fun creatorStudioAndPlayerScreens_displayEncryptionBadgesForEncryptedUploads() {
        val encryptedAudioFile = File.createTempFile("studio_badge_audio_", ".mp3.vpenc").also {
            UploadEncryptionManager.encryptBytesToFile(context, ByteArray(1024) { 0x2A }, it)
            tempFiles.add(it)
        }
        val encryptedVideoFile = File.createTempFile("studio_badge_video_", ".mp4.vpenc").also {
            UploadEncryptionManager.encryptBytesToFile(context, ByteArray(2048) { 0x55 }, it)
            tempFiles.add(it)
        }

        val audioInfo = UploadedAudioInfo(
            localFilePath = encryptedAudioFile.absolutePath,
            fileName = "cipher_anthem.mp3",
            detectedTitle = "Cipher Anthem",
            detectedArtist = "Nova",
            detectedGenre = "Rap",
            durationSec = 140,
            fileSizeBytes = 1024L,
            isEncrypted = true,
            contentSha256Short = "a1b2c3d4e5f6"
        )
        val videoInfo = UploadedVideoInfo(
            localFilePath = encryptedVideoFile.absolutePath,
            fileName = "stage_cam.mp4",
            detectedTitle = "Stage Cam",
            durationSec = 95,
            widthPx = 1920,
            heightPx = 1080,
            fileSizeBytes = 2048L,
            isEncrypted = true,
            contentSha256Short = "f6e5d4c3b2a1"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                CreatorStudioScreen(
                    availablePolls = emptyList(),
                    isRecordingAudio = false,
                    recordAmplitude = 0f,
                    recordedFilePath = null,
                    uploadedAudio = audioInfo,
                    uploadedVideo = videoInfo,
                    onStartRecording = {},
                    onStopRecording = {},
                    onPreviewSynthAudio = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("studio_encryption_banner").assertIsDisplayed()

        composeTestRule.onNodeWithTag("studio_lazy_column")
            .performScrollToNode(hasTestTag("uploaded_track_preview_card"))
        composeTestRule.onNodeWithTag("uploaded_audio_encrypted_badge").assertIsDisplayed()

        composeTestRule.onNodeWithTag("tab_upload_video").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("uploaded_video_encrypted_badge").assertIsDisplayed()
    }

    @Test
    fun playerScreen_displaysEncryptedMediaBadgeForEncryptedPerformance() {
        val encryptedAudioFile = File.createTempFile("player_badge_audio_", ".mp3.vpenc").also {
            UploadEncryptionManager.encryptBytesToFile(context, ByteArray(512) { 0x11 }, it)
            tempFiles.add(it)
        }
        val perf = Performance(
            id = "perf_enc_1",
            title = "Vaulted Verse",
            artistId = "creator_1",
            artistName = "Aria Vance",
            avatarInitials = "AV",
            category = PerformanceCategory.MUSIC,
            genre = "Neo-Soul",
            durationSec = 120,
            lyricsOrScript = "Encrypted stage upload test lyrics.",
            visualizerTheme = VisualizerTheme.NEON_WAVES,
            audioType = SynthAudioType.UPLOADED_MUSIC,
            customAudioPath = encryptedAudioFile.absolutePath,
            viewsCount = 10,
            likesCount = 4,
            votesCount = 7,
            cheersCount = 2
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                PlayerScreen(
                    performance = perf,
                    isPlaying = false,
                    progressSeconds = 0,
                    audioAmplitude = 0.2f,
                    attachedPoll = null,
                    onClose = {},
                    onTogglePlayPause = {},
                    onToggleLike = {},
                    onCheerReaction = {},
                    onVoteInPoll = { _, _ -> },
                    onCreatorClicked = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("player_encrypted_media_badge").assertIsDisplayed()
    }

    @Test
    fun encryptionIsEnabledByDefault_persistsEncryptedPrefs_andMigratesUnencryptedUploads() {
        // Ensure default / re-enabled state is true
        UploadEncryptionManager.setEncryptionEnabled(context, true)
        assertTrue(UploadEncryptionManager.isEncryptionEnabled(context))

        // Verify custom encrypted preferences store vp_enc1: ciphertext on disk, never plaintext
        UploadEncryptionManager.putEncryptedPref(context, "secret_stage_token", "SENSITIVE_TOKEN_9988")
        val rawPrefs = context.getSharedPreferences("versepulse_encrypted_settings_prefs", Context.MODE_PRIVATE)
        val rawStored = rawPrefs.getString("secret_stage_token", null)
        assertNotNull(rawStored)
        assertTrue(rawStored!!.startsWith(UploadEncryptionManager.FIELD_CIPHERTEXT_PREFIX))
        assertFalse(rawStored.contains("SENSITIVE_TOKEN_9988"))
        assertEquals(
            "SENSITIVE_TOKEN_9988",
            UploadEncryptionManager.getEncryptedPref(context, "secret_stage_token")
        )

        // Seed a legacy unencrypted file in filesDir/uploads and verify ensureExistingUploadsEncrypted encrypts it in-place
        val uploadsDir = File(context.filesDir, "uploads").apply { if (!exists()) mkdirs() }
        val legacyPlainBytes = "LEGACY_UNENCRYPTED_AUDIO_FILE_CONTENT_".repeat(80).toByteArray(StandardCharsets.UTF_8)
        val legacyFile = File(uploadsDir, "legacy_unencrypted_${System.currentTimeMillis()}.mp3").also {
            it.writeBytes(legacyPlainBytes)
            tempFiles.add(it)
        }
        assertFalse(UploadEncryptionManager.isEncryptedFile(legacyFile))

        val migratedCount = UploadEncryptionManager.ensureExistingUploadsEncrypted(context)
        assertTrue("Expected at least 1 legacy file to be encrypted", migratedCount >= 1)
        assertTrue("Legacy file must now be a VPENC1 AES-256-GCM container", UploadEncryptionManager.isEncryptedFile(legacyFile))
        assertFalse("Encrypted bytes on disk must not match plaintext", legacyFile.readBytes().contentEquals(legacyPlainBytes))
        assertArrayEquals(
            legacyPlainBytes,
            UploadEncryptionManager.decryptFileToBytes(context, legacyFile)
        )
    }

    @Test
    fun encryptionVaultDialog_andControlCard_renderStatusAndSupportInteractiveToggle() {
        var enabledState = true
        var verifyClicked = false
        var dismissed = false

        composeTestRule.setContent {
            val stateHolder = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(true)
            }
            MyApplicationTheme {
                com.example.ui.components.EncryptionVaultDialog(
                    status = UploadEncryptionManager.EncryptionStatusSnapshot(
                        isEnabled = stateHolder.value,
                        algorithm = "AES-256-GCM",
                        isKeystoreBacked = true,
                        keyBackingLabel = "Android Hardware Keystore",
                        tlsTransportEnforced = true,
                        encryptedFilesCount = 4,
                        encryptedBytesTotal = 262_144L
                    ),
                    onToggleEncryption = { next ->
                        stateHolder.value = next
                        enabledState = next
                    },
                    onVerifyAndEncryptVault = { verifyClicked = true },
                    onDismiss = { dismissed = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("encryption_vault_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vault_encryption_master_switch").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertFalse(enabledState)

        composeTestRule.onNodeWithTag("vault_verify_encrypt_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(verifyClicked)

        composeTestRule.onNodeWithTag("vault_close_button")
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        assertTrue(dismissed)
    }
}
