package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.SynthAudioType
import com.example.model.UploadedVideoInfo
import com.example.model.VisualizerTheme
import com.example.ui.screens.CreatorStudioScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreatorVideoUploadTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var tempVideoFile: File

    @Before
    fun setUp() {
        tempVideoFile = File.createTempFile("stage_upload_test_", ".mp4").apply {
            writeBytes(ByteArray(2048) { (it % 256).toByte() })
        }
    }

    @After
    fun tearDown() {
        if (::tempVideoFile.isInitialized && tempVideoFile.exists()) {
            tempVideoFile.delete()
        }
    }

    @Test
    fun creatorStudio_videoTabUploadsAndPublishesVideoPerformance() {
        data class PublishedCapture(
            val title: String,
            val category: PerformanceCategory,
            val audioType: SynthAudioType,
            val customAudioPath: String?,
            val customVideoPath: String?,
            val durationSec: Int
        )

        var uploadedVideoState by mutableStateOf<UploadedVideoInfo?>(null)
        var publishedCapture: PublishedCapture? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                CreatorStudioScreen(
                    availablePolls = emptyList(),
                    isRecordingAudio = false,
                    recordAmplitude = 0f,
                    recordedFilePath = null,
                    uploadedVideo = uploadedVideoState,
                    onStartRecording = {},
                    onStopRecording = {},
                    onClearUploadedVideo = { uploadedVideoState = null },
                    onPreviewSynthAudio = {},
                    onPublishWithVideo = { title, category, _, _, _, audioType, customAudioPath, customVideoPath, _, durationSec ->
                        publishedCapture = PublishedCapture(
                            title = title,
                            category = category,
                            audioType = audioType,
                            customAudioPath = customAudioPath,
                            customVideoPath = customVideoPath,
                            durationSec = durationSec
                        )
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Scroll to the Media & Sound Source section and switch to the Video tab
        composeTestRule.onNodeWithTag("studio_lazy_column")
            .performScrollToNode(hasTestTag("tab_upload_video"))
        composeTestRule.onNodeWithTag("tab_upload_video").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("upload_video_button").assertIsDisplayed()

        // Simulate completing a video selection from the Android Photo Picker
        uploadedVideoState = UploadedVideoInfo(
            localFilePath = tempVideoFile.absolutePath,
            fileName = "rooftop_freestyle.mp4",
            detectedTitle = "Rooftop Freestyle Live",
            durationSec = 94,
            widthPx = 1920,
            heightPx = 1080,
            fileSizeBytes = 8_400_000L
        )
        composeTestRule.waitForIdle()

        // Verify the studio video preview card renders
        composeTestRule.onNodeWithTag("uploaded_video_preview_card").assertIsDisplayed()

        // Scroll down to Publish button and publish the video performance
        composeTestRule.onNodeWithTag("studio_lazy_column")
            .performScrollToNode(hasTestTag("publish_performance_button"))
        composeTestRule.onNodeWithTag("publish_performance_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertNotNull("Expected onPublishWithVideo to be invoked", publishedCapture)
        assertEquals("Rooftop Freestyle Live", publishedCapture!!.title)
        assertEquals(PerformanceCategory.MUSIC, publishedCapture!!.category)
        assertEquals(SynthAudioType.UPLOADED_VIDEO, publishedCapture!!.audioType)
        assertNull(publishedCapture!!.customAudioPath)
        assertEquals(tempVideoFile.absolutePath, publishedCapture!!.customVideoPath)
        assertEquals(94, publishedCapture!!.durationSec)
    }

    @Test
    fun creatorStudio_beatsWithCompanionStageVideo_publishesBothBeatAndVideo() {
        var capturedAudioType: SynthAudioType? = null
        var capturedVideoPath: String? = null

        val companionVideo = UploadedVideoInfo(
            localFilePath = tempVideoFile.absolutePath,
            fileName = "slam_stage_cam.mp4",
            detectedTitle = "City Pulse Visuals",
            durationSec = 112,
            widthPx = 1280,
            heightPx = 720,
            fileSizeBytes = 4_200_000L
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                CreatorStudioScreen(
                    availablePolls = emptyList(),
                    isRecordingAudio = false,
                    recordAmplitude = 0f,
                    recordedFilePath = null,
                    uploadedVideo = companionVideo,
                    onStartRecording = {},
                    onStopRecording = {},
                    onPreviewSynthAudio = {},
                    onPublishWithVideo = { _, _, _, _, _, audioType, _, customVideoPath, _, _ ->
                        capturedAudioType = audioType
                        capturedVideoPath = customVideoPath
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Select Backing Beats while keeping the companion stage video attached
        composeTestRule.onNodeWithTag("studio_lazy_column")
            .performScrollToNode(hasTestTag("tab_synth_beats"))
        composeTestRule.onNodeWithTag("tab_synth_beats").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("studio_lazy_column")
            .performScrollToNode(hasTestTag("attached_companion_video_row"))
        composeTestRule.onNodeWithTag("attached_companion_video_row").assertIsDisplayed()

        composeTestRule.onNodeWithTag("studio_lazy_column")
            .performScrollToNode(hasTestTag("publish_performance_button"))
        composeTestRule.onNodeWithTag("publish_performance_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals(SynthAudioType.ACOUSTIC_SOUL, capturedAudioType)
        assertEquals(tempVideoFile.absolutePath, capturedVideoPath)
    }

    @Test
    fun playerScreen_withUploadedVideo_rendersVideoStageAndTogglesVisualizer() {
        val videoPerformance = Performance(
            id = "perf_video_stage_1",
            title = "Midnight Cypher",
            artistId = "creator_99",
            artistName = "Kaelen Brooks",
            avatarInitials = "KB",
            category = PerformanceCategory.SPOKEN_WORD,
            genre = "Slam Poetry",
            durationSec = 110,
            lyricsOrScript = "Underneath the sodium streetlights...",
            visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
            audioType = SynthAudioType.UPLOADED_VIDEO,
            customVideoPath = tempVideoFile.absolutePath,
            viewsCount = 42,
            likesCount = 15,
            votesCount = 24,
            cheersCount = 0
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                PlayerScreen(
                    performance = videoPerformance,
                    isPlaying = false,
                    progressSeconds = 0,
                    audioAmplitude = 0.25f,
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

        composeTestRule.onNodeWithTag("player_video_stage_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stage_video_player").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_visual_mode_toggle").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // After toggling, the reactive canvas visualizer replaces the video player
        composeTestRule.onNodeWithTag("live_music_visualizer_container").assertIsDisplayed()

        // Toggle back to video stage
        composeTestRule.onNodeWithTag("player_visual_mode_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("stage_video_player").assertIsDisplayed()
    }
}
