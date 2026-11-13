package com.example.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.model.LiveEvent
import com.example.model.LiveEventsUiState
import com.example.model.PerformanceCategory
import com.example.ui.components.LiveEventsListContent
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
class LiveEventsListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMusicEvent = LiveEvent(
        id = "evt_music_1",
        ownerUid = "uid_1",
        hostId = "creator_1",
        hostName = "Maya Vance",
        title = "Rooftop Neo-Soul Session",
        category = PerformanceCategory.MUSIC.name,
        genre = "Neo-Soul",
        participantCount = 42L,
        isLive = true
    )

    private val sampleSpokenWordEvent = LiveEvent(
        id = "evt_spoken_1",
        ownerUid = "uid_2",
        hostId = "creator_2",
        hostName = "Julian Verse",
        title = "Midnight Poetry Open Mic",
        category = PerformanceCategory.SPOKEN_WORD.name,
        genre = "Spoken Word",
        participantCount = 1L,
        isLive = true
    )

    @Test
    fun liveEventsList_displaysMusicAndSpokenWordTitlesAndParticipantCounts_andSupportsJoinAndFilters() {
        val allEvents = listOf(sampleMusicEvent, sampleSpokenWordEvent)
        var selectedCategory by mutableStateOf<PerformanceCategory?>(null)
        var joinedIds by mutableStateOf<Set<String>>(emptySet())
        var eventCounts by mutableStateOf(
            mapOf(
                sampleMusicEvent.id to sampleMusicEvent.participantCount,
                sampleSpokenWordEvent.id to sampleSpokenWordEvent.participantCount
            )
        )

        composeTestRule.setContent {
            val currentList = allEvents
                .map { it.copy(participantCount = eventCounts[it.id] ?: it.participantCount) }
                .filter { selectedCategory == null || it.performanceCategory == selectedCategory }

            MyApplicationTheme {
                LiveEventsListContent(
                    uiState = LiveEventsUiState.Success(
                        events = currentList,
                        joinedEventIds = joinedIds
                    ),
                    selectedCategory = selectedCategory,
                    onSelectCategory = { selectedCategory = it },
                    onToggleJoinEvent = { evt ->
                        if (evt.id in joinedIds) {
                            joinedIds = joinedIds - evt.id
                            eventCounts = eventCounts + (evt.id to ((eventCounts[evt.id] ?: 1L) - 1L))
                        } else {
                            joinedIds = joinedIds + evt.id
                            eventCounts = eventCounts + (evt.id to ((eventCounts[evt.id] ?: 0L) + 1L))
                        }
                    },
                    onCreateEvent = { _, _, _ -> },
                    onRetry = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify both Music and Spoken Word event titles and participant counts are displayed
        composeTestRule.onNodeWithTag("live_event_title_evt_music_1", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Rooftop Neo-Soul Session")
        composeTestRule.onNodeWithTag("live_event_participants_evt_music_1", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("42 participants")

        composeTestRule.onNodeWithTag("live_event_title_evt_spoken_1", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Midnight Poetry Open Mic")
        composeTestRule.onNodeWithTag("live_event_participants_evt_spoken_1", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("1 participant")

        // Join Music event -> participant count updates to 43 participants
        composeTestRule.onNodeWithTag("join_live_event_button_evt_music_1").performClick()
        composeTestRule.waitForIdle()

        assertTrue("evt_music_1" in joinedIds)
        composeTestRule.onNodeWithTag("live_event_participants_evt_music_1", useUnmergedTree = true)
            .assertTextContains("43 participants")

        // Filter to Spoken Word only
        composeTestRule.onNodeWithTag("live_events_filter_spoken_word").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("live_event_title_evt_spoken_1", useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_events_summary_stats", useUnmergedTree = true)
            .assertTextContains("1 active • 1 participants")
    }

    @Test
    fun liveEventsList_supportsHostingNewEventEmptyStateAndErrorRetry() {
        var createdTitle: String? = null
        var createdCategory: PerformanceCategory? = null
        var retried = false
        var uiState by mutableStateOf<LiveEventsUiState>(
            LiveEventsUiState.Error("Temporary Firestore sync interruption")
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                LiveEventsListContent(
                    uiState = uiState,
                    selectedCategory = null,
                    onSelectCategory = {},
                    onToggleJoinEvent = {},
                    onCreateEvent = { title, cat, _ ->
                        createdTitle = title
                        createdCategory = cat
                    },
                    onRetry = {
                        retried = true
                        uiState = LiveEventsUiState.Success(emptyList())
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Error state + Retry transitions to Empty state
        composeTestRule.onNodeWithTag("live_events_error_state").assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_events_retry_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(retried)
        composeTestRule.onNodeWithTag("live_events_empty_state").assertIsDisplayed()

        // Open Host Live Event dialog and submit Spoken Word event
        composeTestRule.onNodeWithTag("create_live_event_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("live_event_title_input").performTextInput("Downtown Slam Finals")
        composeTestRule.onNodeWithTag("dialog_category_spoken_word").performClick()
        composeTestRule.onNodeWithTag("confirm_create_live_event_button").performClick()
        composeTestRule.waitForIdle()

        assertEquals("Downtown Slam Finals", createdTitle)
        assertEquals(PerformanceCategory.SPOKEN_WORD, createdCategory)
    }
}
