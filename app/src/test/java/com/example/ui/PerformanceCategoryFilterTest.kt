package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.model.LiveEvent
import com.example.model.LiveEventsUiState
import com.example.model.LiveSession
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.PerformanceCategoryCatalog
import com.example.model.PerformanceStyle
import com.example.model.SynthAudioType
import com.example.model.VisualizerTheme
import com.example.model.performanceStyle
import com.example.ui.components.LiveEventsListContent
import com.example.ui.screens.CreatorStudioScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceCategoryFilterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun performanceCategoryCatalog_resolvesRapPoetryJazzAndSpokenWordAccurately() {
        val rapStyle = PerformanceCategoryCatalog.resolveStyle(
            category = PerformanceCategory.MUSIC,
            genre = "Rap & Boom-Bap",
            title = "Rooftop Freestyle Cipher"
        )
        val poetryStyle = PerformanceCategoryCatalog.resolveStyle(
            category = PerformanceCategory.SPOKEN_WORD,
            genre = "Slam Poetry",
            title = "Concrete Roots"
        )
        val jazzStyle = PerformanceCategoryCatalog.resolveStyle(
            category = PerformanceCategory.MUSIC,
            genre = "Vocal & Nu-Jazz",
            title = "Blue Note Improv"
        )
        val spokenWordStyle = PerformanceCategoryCatalog.resolveStyle(
            category = PerformanceCategory.SPOKEN_WORD,
            genre = "Spoken Word",
            title = "Unplugged Griot Circle"
        )

        assertEquals(PerformanceStyle.RAP, rapStyle)
        assertEquals(PerformanceStyle.POETRY, poetryStyle)
        assertEquals(PerformanceStyle.JAZZ, jazzStyle)
        assertEquals(PerformanceStyle.SPOKEN_WORD, spokenWordStyle)
        assertEquals(PerformanceCategory.MUSIC, rapStyle.parentCategory)
        assertEquals(PerformanceCategory.SPOKEN_WORD, poetryStyle.parentCategory)
        assertEquals(PerformanceCategory.MUSIC, jazzStyle.parentCategory)
        assertEquals(PerformanceCategory.SPOKEN_WORD, spokenWordStyle.parentCategory)

        val hipHopStyle = PerformanceCategoryCatalog.resolveStyle(
            category = PerformanceCategory.MUSIC,
            genre = "Hip-Hop",
            title = "808 Skyline & Golden Era Cuts"
        )
        val trapStyle = PerformanceCategoryCatalog.resolveStyle(
            category = PerformanceCategory.MUSIC,
            genre = "Trap & 808s",
            title = "Midnight Turntablism"
        )
        assertEquals(PerformanceStyle.HIP_HOP, hipHopStyle)
        assertEquals(PerformanceStyle.HIP_HOP, trapStyle)
        assertEquals(PerformanceCategory.MUSIC, hipHopStyle.parentCategory)
        assertTrue(PerformanceStyle.HIP_HOP in PerformanceCategoryCatalog.stylesForCategory(PerformanceCategory.MUSIC))
        assertTrue(PerformanceCategoryCatalog.filterTagsForCategory(PerformanceCategory.MUSIC).any { it.style == PerformanceStyle.HIP_HOP })
    }

    @Test
    fun liveEventsList_filterChipBar_filtersByRapPoetryJazzAndSpokenWord() {
        val events = listOf(
            LiveEvent(
                id = "evt_rap",
                ownerUid = "u1",
                hostId = "c1",
                hostName = "Kaelen Flux",
                title = "5th Ave Rap Cipher",
                category = PerformanceCategory.MUSIC.name,
                genre = "Rap & Hip-Hop",
                participantCount = 34L,
                isLive = true
            ),
            LiveEvent(
                id = "evt_poetry",
                ownerUid = "u2",
                hostId = "c2",
                hostName = "Maya Vance",
                title = "Midnight Slam Poetry",
                category = PerformanceCategory.SPOKEN_WORD.name,
                genre = "Slam Poetry",
                participantCount = 28L,
                isLive = true
            ),
            LiveEvent(
                id = "evt_jazz",
                ownerUid = "u3",
                hostId = "c3",
                hostName = "Julian Miles",
                title = "Blue Note Jazz Quartet",
                category = PerformanceCategory.MUSIC.name,
                genre = "Vocal & Nu-Jazz",
                participantCount = 51L,
                isLive = true
            ),
            LiveEvent(
                id = "evt_spoken",
                ownerUid = "u4",
                hostId = "c4",
                hostName = "Darnell Cruz",
                title = "Griot Spoken Word Circle",
                category = PerformanceCategory.SPOKEN_WORD.name,
                genre = "Spoken Word",
                participantCount = 19L,
                isLive = true
            )
        )

        var selectedStyle by mutableStateOf<PerformanceStyle?>(null)

        composeTestRule.setContent {
            MyApplicationTheme {
                LiveEventsListContent(
                    uiState = LiveEventsUiState.Success(events = events),
                    selectedCategory = null,
                    onSelectCategory = {},
                    onToggleJoinEvent = {},
                    onCreateEvent = { _, _, _ -> },
                    onRetry = {},
                    selectedStyle = selectedStyle,
                    onSelectStyle = { selectedStyle = it }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Initially All 4 events are active
        composeTestRule.onNodeWithTag("live_events_summary_stats", useUnmergedTree = true)
            .assertTextContains("4 active", substring = true)

        // Filter by Rap
        composeTestRule.onNodeWithTag("live_events_filter_rap").performClick()
        composeTestRule.waitForIdle()
        assertEquals(PerformanceStyle.RAP, selectedStyle)
        composeTestRule.onNodeWithTag("live_event_title_evt_rap", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("5th Ave Rap Cipher")
        composeTestRule.onNodeWithTag("live_event_style_evt_rap", useUnmergedTree = true)
            .assertTextContains("Rap")
        composeTestRule.onNodeWithTag("live_event_title_evt_jazz", useUnmergedTree = true)
            .assertDoesNotExist()

        // Filter by Poetry
        composeTestRule.onNodeWithTag("live_events_filter_poetry").performClick()
        composeTestRule.waitForIdle()
        assertEquals(PerformanceStyle.POETRY, selectedStyle)
        composeTestRule.onNodeWithTag("live_event_title_evt_poetry", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Midnight Slam Poetry")
        composeTestRule.onNodeWithTag("live_event_title_evt_rap", useUnmergedTree = true)
            .assertDoesNotExist()

        // Filter by Jazz
        composeTestRule.onNodeWithTag("live_events_filter_jazz").performClick()
        composeTestRule.waitForIdle()
        assertEquals(PerformanceStyle.JAZZ, selectedStyle)
        composeTestRule.onNodeWithTag("live_event_title_evt_jazz", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Blue Note Jazz Quartet")
        composeTestRule.onNodeWithTag("live_event_participants_evt_jazz", useUnmergedTree = true)
            .assertTextContains("51 participants")

        // Filter by Spoken Word
        composeTestRule.onNodeWithTag("live_events_filter_row")
            .performScrollToNode(hasTestTag("live_events_filter_spoken_word"))
        composeTestRule.onNodeWithTag("live_events_filter_spoken_word").performClick()
        composeTestRule.waitForIdle()
        assertEquals(PerformanceStyle.SPOKEN_WORD, selectedStyle)
        composeTestRule.onNodeWithTag("live_event_title_evt_spoken", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Griot Spoken Word Circle")

        // Reset back to All
        composeTestRule.onNodeWithTag("live_events_filter_row")
            .performScrollToNode(hasTestTag("live_events_filter_all"))
        composeTestRule.onNodeWithTag("live_events_filter_all").performClick()
        composeTestRule.waitForIdle()
        assertEquals(null, selectedStyle)
        composeTestRule.onNodeWithTag("live_events_summary_stats", useUnmergedTree = true)
            .assertTextContains("4 active", substring = true)
    }

    @Test
    fun discoverScreen_mainEventListAndPerformers_filterByCategoryChipBar() {
        val sessions = listOf(
            LiveSession(
                id = "sess_rap",
                creatorId = "c1",
                creatorName = "Kaelen Flux",
                title = "Boom-Bap Rap Battle",
                category = PerformanceCategory.MUSIC,
                genre = "Rap & Hip-Hop",
                visualizerTheme = VisualizerTheme.COSMIC_VINYL,
                audioType = SynthAudioType.HIPHOP_BEAT,
                activePollId = null,
                spectatorCount = 120,
                isLive = true,
                styleId = "rap"
            ),
            LiveSession(
                id = "sess_jazz",
                creatorId = "c2",
                creatorName = "Julian Miles",
                title = "Midnight Jazz Lounge",
                category = PerformanceCategory.MUSIC,
                genre = "Vocal & Nu-Jazz",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.PIANO_WARMTH,
                activePollId = null,
                spectatorCount = 85,
                isLive = true,
                styleId = "jazz"
            )
        )

        val performances = listOf(
            Performance(
                id = "perf_poetry",
                title = "Asphalt Sonnets",
                artistId = "c3",
                artistName = "Maya Vance",
                avatarInitials = "MV",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Slam Poetry",
                durationSec = 150,
                lyricsOrScript = "Verse in the neon rain",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                viewsCount = 500,
                likesCount = 120,
                votesCount = 95,
                cheersCount = 30,
                styleId = "poetry"
            ),
            Performance(
                id = "perf_jazz",
                title = " Brass & Velvet",
                artistId = "c2",
                artistName = "Julian Miles",
                avatarInitials = "JM",
                category = PerformanceCategory.MUSIC,
                genre = "Vocal & Nu-Jazz",
                durationSec = 180,
                lyricsOrScript = "Improvised midnight chords",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.PIANO_WARMTH,
                viewsCount = 640,
                likesCount = 180,
                votesCount = 140,
                cheersCount = 45,
                styleId = "jazz"
            )
        )

        assertEquals(PerformanceStyle.RAP, sessions[0].performanceStyle)
        assertEquals(PerformanceStyle.JAZZ, sessions[1].performanceStyle)

        composeTestRule.setContent {
            MyApplicationTheme {
                DiscoverScreen(
                    performances = performances,
                    featuredPoll = null,
                    activeLiveSessions = sessions,
                    onPlayPerformance = {},
                    onToggleLike = {},
                    onVoteInPoll = { _, _ -> },
                    onCreatorProfileClicked = {},
                    onNavigateToPolls = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Scroll to the main category filter bar on DiscoverScreen
        composeTestRule.onNodeWithTag("active_performers_screen")
            .performScrollToNode(hasTestTag("main_category_filter_bar"))
        composeTestRule.onNodeWithTag("main_category_filter_bar").assertIsDisplayed()

        // Both live sessions are initially visible (2 Live)
        composeTestRule.onNodeWithTag("active_performers_screen")
            .performScrollToNode(hasTestTag("main_stage_events_list"))
        composeTestRule.onNodeWithTag("main_stage_events_count", useUnmergedTree = true)
            .assertTextContains("2 Live")

        // Tap Jazz chip -> filters main screen event list to Jazz session only (1 Live)
        composeTestRule.onNodeWithTag("active_performers_screen")
            .performScrollToNode(hasTestTag("main_category_filter_bar"))
        composeTestRule.onNodeWithTag("category_chip_jazz").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("active_performers_screen")
            .performScrollToNode(hasTestTag("main_stage_events_list"))
        composeTestRule.onNodeWithTag("main_stage_events_count", useUnmergedTree = true)
            .assertTextContains("1 Live")
        composeTestRule.onNodeWithTag("stage_event_title_sess_jazz", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Midnight Jazz Lounge")
        composeTestRule.onNodeWithTag("stage_event_title_sess_rap", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun discoverScreen_musicSection_showsHipHopCategoryAndFiltersPerformancesAndSessions() {
        val sessions = listOf(
            LiveSession(
                id = "sess_hip_hop",
                creatorId = "c1",
                creatorName = "Kaelen Flux",
                title = "808 Turntable Sessions",
                category = PerformanceCategory.MUSIC,
                genre = "Hip-Hop",
                visualizerTheme = VisualizerTheme.COSMIC_VINYL,
                audioType = SynthAudioType.HIPHOP_BEAT,
                activePollId = null,
                spectatorCount = 148,
                isLive = true,
                styleId = "hip_hop"
            ),
            LiveSession(
                id = "sess_jazz",
                creatorId = "c2",
                creatorName = "Julian Miles",
                title = "Midnight Jazz Lounge",
                category = PerformanceCategory.MUSIC,
                genre = "Vocal & Nu-Jazz",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.PIANO_WARMTH,
                activePollId = null,
                spectatorCount = 85,
                isLive = true,
                styleId = "jazz"
            ),
            LiveSession(
                id = "sess_poetry",
                creatorId = "c3",
                creatorName = "Maya Vance",
                title = "Midnight Slam Poetry",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Slam Poetry",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                activePollId = null,
                spectatorCount = 92,
                isLive = true,
                styleId = "poetry"
            )
        )

        val performances = listOf(
            Performance(
                id = "perf_hip_hop",
                title = "Golden Era Cuts",
                artistId = "c1",
                artistName = "Kaelen Flux",
                avatarInitials = "KF",
                category = PerformanceCategory.MUSIC,
                genre = "Hip-Hop",
                durationSec = 175,
                lyricsOrScript = "808 sub bass rattling the avenue",
                visualizerTheme = VisualizerTheme.COSMIC_VINYL,
                audioType = SynthAudioType.HIPHOP_BEAT,
                viewsCount = 800,
                likesCount = 240,
                votesCount = 190,
                cheersCount = 60,
                styleId = "hip_hop"
            ),
            Performance(
                id = "perf_jazz",
                title = "Brass & Velvet",
                artistId = "c2",
                artistName = "Julian Miles",
                avatarInitials = "JM",
                category = PerformanceCategory.MUSIC,
                genre = "Vocal & Nu-Jazz",
                durationSec = 180,
                lyricsOrScript = "Improvised midnight chords",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.PIANO_WARMTH,
                viewsCount = 640,
                likesCount = 180,
                votesCount = 140,
                cheersCount = 45,
                styleId = "jazz"
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                DiscoverScreen(
                    performances = performances,
                    featuredPoll = null,
                    activeLiveSessions = sessions,
                    onPlayPerformance = {},
                    onToggleLike = {},
                    onVoteInPoll = { _, _ -> },
                    onCreatorProfileClicked = {},
                    onNavigateToPolls = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Switch to the Music section
        composeTestRule.onNodeWithTag("active_performers_screen")
            .performScrollToNode(hasTestTag("discover_section_bar"))
        composeTestRule.onNodeWithTag("discover_section_music").performClick()
        composeTestRule.waitForIdle()

        // Spoken Word session is excluded from the Music section (only 2 Music sessions remain)
        composeTestRule.onNodeWithTag("active_performers_screen")
            .performScrollToNode(hasTestTag("main_stage_events_list"))
        composeTestRule.onNodeWithTag("main_stage_events_count", useUnmergedTree = true)
            .assertTextContains("2 Live")

        // Tap Hip-Hop category chip inside the Music section
        composeTestRule.onNodeWithTag("active_performers_screen")
            .performScrollToNode(hasTestTag("main_category_filter_bar"))
        composeTestRule.onNodeWithTag("main_category_filter_bar")
            .performScrollToNode(hasTestTag("category_chip_hip_hop"))
        composeTestRule.onNodeWithTag("category_chip_hip_hop").performClick()
        composeTestRule.waitForIdle()

        // Only the Hip-Hop live session and Hip-Hop performance remain visible
        composeTestRule.onNodeWithTag("active_performers_screen")
            .performScrollToNode(hasTestTag("main_stage_events_list"))
        composeTestRule.onNodeWithTag("main_stage_events_count", useUnmergedTree = true)
            .assertTextContains("1 Live")
        composeTestRule.onNodeWithTag("stage_event_title_sess_hip_hop", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("808 Turntable Sessions")
        composeTestRule.onNodeWithTag("stage_event_title_sess_jazz", useUnmergedTree = true)
            .assertDoesNotExist()

        composeTestRule.onNodeWithTag("active_performers_screen")
            .performScrollToNode(hasTestTag("performer_card_perf_hip_hop"))
        composeTestRule.onNodeWithTag("performer_style_perf_hip_hop", useUnmergedTree = true)
            .assertTextContains("Hip-Hop")
    }

    @Test
    fun creatorStudioScreen_musicSection_exposesHipHopStyleAndSetsGenre() {
        composeTestRule.setContent {
            MyApplicationTheme {
                CreatorStudioScreen(
                    availablePolls = emptyList(),
                    isRecordingAudio = false,
                    recordAmplitude = 0f,
                    recordedFilePath = null,
                    onStartRecording = {},
                    onStopRecording = {},
                    onPreviewSynthAudio = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Music is selected by default; Hip-Hop chip is available in the Music style bar
        composeTestRule.onNodeWithTag("select_music_category").performClick()
        composeTestRule.onNodeWithTag("studio_style_chip_row")
            .performScrollToNode(hasTestTag("studio_style_hip_hop"))
        composeTestRule.onNodeWithTag("studio_style_hip_hop").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("studio_genre_input")
            .assertTextContains("Hip-Hop")
    }
}
