package com.example.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import com.example.model.CreatorProfile
import com.example.model.CreatorSessionStats
import com.example.model.Performance
import com.example.model.PerformanceAnalyticsEngine
import com.example.model.PerformanceCategory
import com.example.model.RatingTrendDirection
import com.example.model.SynthAudioType
import com.example.model.VisualizerTheme
import com.example.ui.components.PerformanceAnalyticsChart
import com.example.ui.screens.CreatorProfileScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PerformanceAnalyticsChartTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildChronologicalCatalog(count: Int = 12): List<Performance> {
        val baseTime = 1_750_000_000_000L
        return (1..count).map { index ->
            // Ratings climb from 4.00 up to 4.99 across 12 releases
            val rating = 4.00f + ((index - 1) * 0.09f)
            Performance(
                id = "perf_track_$index",
                title = "Stage Track #$index",
                artistId = "creator_maya",
                artistName = "Maya Vance",
                avatarInitials = "MV",
                category = if (index % 2 == 0) PerformanceCategory.SPOKEN_WORD else PerformanceCategory.MUSIC,
                genre = if (index % 2 == 0) "Slam Poetry" else "Rap & Hip-Hop",
                durationSec = 160 + index,
                lyricsOrScript = "Verse $index under stage lights",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                viewsCount = 400 + index * 50,
                likesCount = 100 + index * 20,
                votesCount = 60 + index * 15,
                cheersCount = 15 + index * 4,
                createdAtTimestamp = baseTime + index * 86_400_000L,
                audienceRating = rating,
                ratingsCount = 25 + index * 5
            )
        }
    }

    @Test
    fun performanceAnalyticsEngine_slicesLastTenPerformancesChronologicallyAndComputesTrend() {
        // Pass 12 performances in reverse order to verify chronological sorting + last-10 windowing
        val twelvePerformances = buildChronologicalCatalog(12).reversed()

        val summary = PerformanceAnalyticsEngine.computeLastTenTrend(
            performances = twelvePerformances,
            creatorBaselineRating = 4.85f
        )

        assertEquals(12, summary.totalCatalogCount)
        assertEquals(10, summary.totalAnalyzedCount)
        assertEquals(10, summary.points.size)

        // Oldest 2 tracks (#1 and #2) must be excluded; P1 is Track #3 (4.18) and P10 is Track #12 (4.99)
        assertEquals("perf_track_3", summary.points.first().performanceId)
        assertEquals("P1", summary.points.first().slotLabel)
        assertEquals(4.18f, summary.points.first().audienceRating, 0.01f)

        assertEquals("perf_track_12", summary.points.last().performanceId)
        assertEquals("P10", summary.points.last().slotLabel)
        assertEquals(4.99f, summary.points.last().audienceRating, 0.01f)

        assertEquals(4.99f, summary.peakRating, 0.01f)
        assertEquals(4.18f, summary.lowestRating, 0.01f)
        assertEquals(0.81f, summary.netTrendDelta, 0.01f)
        assertEquals(RatingTrendDirection.RISING, summary.trendDirection)
    }

    @Test
    fun performanceAnalyticsChart_rendersLastTenPointsSwitchesModesAndInspectsTooltips() {
        val twelvePerformances = buildChronologicalCatalog(12)
        var clickedPerformanceId: String? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.verticalScroll(
                        androidx.compose.foundation.rememberScrollState()
                    )
                ) {
                    PerformanceAnalyticsChart(
                        performances = twelvePerformances,
                        creatorBaselineRating = 4.9f,
                        onPerformancePointClick = { id -> clickedPerformanceId = id }
                    )
                }
            }
        }

        composeTestRule.waitForIdle()

        // Chart header, trend delta badge (+0.81 ★), and KPI strip are displayed
        composeTestRule.onNodeWithTag("performance_analytics_chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("analytics_trend_delta_badge", useUnmergedTree = true)
            .assertTextContains("+0.81 ★")
        composeTestRule.onNodeWithTag("analytics_peak_rating_value", useUnmergedTree = true)
            .assertTextContains("4.99 ★")
        composeTestRule.onNodeWithTag("analytics_latest_rating_value", useUnmergedTree = true)
            .assertTextContains("4.99 ★")

        // Switch chart mode to Bar View and back to Area Trend
        composeTestRule.onNodeWithTag("analytics_mode_bars").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("analytics_plot_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("analytics_mode_area").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Scroll down to tooltip & default selected point P10 (Stage Track #12)
        composeTestRule.onNodeWithTag("analytics_selected_tooltip").performScrollTo()
        composeTestRule.onNodeWithTag("analytics_tooltip_title", useUnmergedTree = true)
            .assertTextContains("Stage Track #12")
        composeTestRule.onNodeWithTag("analytics_tooltip_rating", useUnmergedTree = true)
            .assertTextContains("4.99 ★")

        // Select P1 chip -> updates tooltip to Stage Track #3 (4.18 ★)
        composeTestRule.onNodeWithTag("analytics_point_selector_row")
            .performScrollTo()
            .performScrollToNode(hasTestTag("analytics_point_chip_1"))
        composeTestRule.onNodeWithTag("analytics_point_chip_1").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("analytics_selected_tooltip").performScrollTo()
        composeTestRule.onNodeWithTag("analytics_tooltip_title", useUnmergedTree = true)
            .assertTextContains("Stage Track #3")
        composeTestRule.onNodeWithTag("analytics_tooltip_rating", useUnmergedTree = true)
            .assertTextContains("4.18 ★")

        // Tap Play on tooltip -> dispatches perf_track_3
        composeTestRule.onNodeWithTag("analytics_tooltip_play_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("perf_track_3", clickedPerformanceId)
    }

    @Test
    fun creatorProfileScreen_rendersPerformanceAnalyticsChartAndHandlesEmptyCatalog() {
        val newCreatorProfile = CreatorProfile(
            creatorId = "creator_new",
            artistName = "Sora Echo",
            stageTitle = "Emerging Lyricist",
            bio = "Preparing debut spoken word EP.",
            primaryCategory = PerformanceCategory.SPOKEN_WORD,
            audienceRating = 5.0f,
            totalAudienceRatingsCount = 1,
            isCurrentUser = true
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                CreatorProfileScreen(
                    profile = newCreatorProfile,
                    portfolio = emptyList(),
                    leaderboardRank = null,
                    sessionStats = CreatorSessionStats(),
                    onPlayPerformance = {},
                    onRateCreator = {},
                    onSaveProfile = { _, _, _, _, _, _, _, _ -> },
                    onNavigateToStudio = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("creator_profile_list")
            .performScrollToNode(hasTestTag("performance_analytics_chart"))
        composeTestRule.onNodeWithTag("performance_analytics_chart").assertIsDisplayed()
        composeTestRule.onNodeWithTag("analytics_chart_empty_state").assertIsDisplayed()
    }
}
