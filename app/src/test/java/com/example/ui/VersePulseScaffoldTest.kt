package com.example.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.model.AppNavDestination
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.PerformanceVoteCount
import com.example.model.SynthAudioType
import com.example.model.UserRole
import com.example.model.VisualizerTheme
import com.example.ui.components.VersePulseScaffold
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
class VersePulseScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val samplePerformance = Performance(
        id = "perf_stage_1",
        title = "Neon Horizon",
        artistId = "creator_1",
        artistName = "Maya Vance",
        avatarInitials = "MV",
        category = PerformanceCategory.MUSIC,
        genre = "Neo-Soul",
        durationSec = 180,
        lyricsOrScript = "Test lyrics",
        visualizerTheme = VisualizerTheme.NEON_WAVES,
        audioType = SynthAudioType.ACOUSTIC_SOUL,
        viewsCount = 120,
        likesCount = 45,
        votesCount = 88,
        cheersCount = 12
    )

    @Test
    fun scaffold_rendersTopBarNavigationAndSwitchesRolesAndTabs() {
        var currentDestination by mutableStateOf(AppNavDestination.DISCOVER)
        var currentRole by mutableStateOf(UserRole.SPECTATOR)
        var soundEnabled by mutableStateOf(true)
        var accountClicked = false
        var createTrackClicked = false

        composeTestRule.setContent {
            MyApplicationTheme {
                VersePulseScaffold(
                    currentDestination = currentDestination,
                    userRole = currentRole,
                    accountDisplayName = "Alex Stage",
                    activeLiveCount = 2,
                    totalActiveVotes = 420,
                    isSoundEffectsEnabled = soundEnabled,
                    activePerformance = null,
                    activePerformanceVoteState = null,
                    isPlaying = false,
                    isPlayerExpanded = false,
                    onNavigate = { currentDestination = it },
                    onProfileTabSelected = { currentDestination = AppNavDestination.PROFILE },
                    onToggleSoundEffects = { soundEnabled = !soundEnabled },
                    onToggleUserRole = {
                        currentRole = if (currentRole == UserRole.SPECTATOR) UserRole.CREATOR else UserRole.SPECTATOR
                    },
                    onOpenAccountSheet = { accountClicked = true },
                    onCreateTrackClicked = { createTrackClicked = true },
                    onMiniPlayerTogglePlayPause = {},
                    onMiniPlayerVote = {},
                    onMiniPlayerExpand = {},
                    onMiniPlayerClose = {}
                ) {
                    Text(
                        text = "Screen: ${currentDestination.name}",
                        modifier = Modifier.testTag("active_screen_content")
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("app_scaffold_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_top_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("sound_toggle_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, soundEnabled)

        composeTestRule.onNodeWithTag("auth_user_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(accountClicked)

        // Switch from Spectator to Creator -> Studio tab and FAB become visible
        composeTestRule.onNodeWithTag("role_switcher_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(UserRole.CREATOR, currentRole)
        composeTestRule.onNodeWithTag("nav_studio").assertIsDisplayed()
        composeTestRule.onNodeWithTag("scaffold_fab_studio").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(createTrackClicked)

        // Navigate across tabs
        composeTestRule.onNodeWithTag("nav_leaderboard").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppNavDestination.LEADERBOARD, currentDestination)
        composeTestRule.onNodeWithTag("active_screen_content").assertTextContains("Screen: LEADERBOARD")
    }

    @Test
    fun scaffold_rendersDockedMiniPlayerWithLiveVoteCountAndTriggersActions() {
        var votedPerfId: String? = null
        var expanded = false

        val voteState = PerformanceVoteCount(
            performanceId = samplePerformance.id,
            performanceTitle = samplePerformance.title,
            performerId = samplePerformance.artistId,
            performerName = samplePerformance.artistName,
            baselineVotes = 88,
            realtimeVotesDelta = 12,
            totalVotes = 100,
            isVotedByCurrentUser = true
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                VersePulseScaffold(
                    currentDestination = AppNavDestination.DISCOVER,
                    userRole = UserRole.SPECTATOR,
                    accountDisplayName = "Spectator One",
                    activeLiveCount = 1,
                    totalActiveVotes = 100,
                    isSoundEffectsEnabled = true,
                    activePerformance = samplePerformance,
                    activePerformanceVoteState = voteState,
                    isPlaying = true,
                    isPlayerExpanded = false,
                    onNavigate = {},
                    onProfileTabSelected = {},
                    onToggleSoundEffects = {},
                    onToggleUserRole = {},
                    onOpenAccountSheet = {},
                    onCreateTrackClicked = {},
                    onMiniPlayerTogglePlayPause = {},
                    onMiniPlayerVote = { votedPerfId = it },
                    onMiniPlayerExpand = { expanded = true },
                    onMiniPlayerClose = {}
                ) {
                    Text("Discover Content")
                }
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mini_player_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mini_player_vote_tally", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("100 votes", substring = true)

        composeTestRule.onNodeWithTag("mini_player_vote_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals("perf_stage_1", votedPerfId)

        composeTestRule.onNodeWithTag("mini_player_expand_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(expanded)
    }
}
