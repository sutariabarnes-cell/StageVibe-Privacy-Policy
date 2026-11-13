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
import com.example.data.PerformanceRepository
import com.example.model.CreatorCompetitiveTier
import com.example.model.LeaderboardCategoryFilter
import com.example.model.LeaderboardRankingMode
import com.example.model.LeaderboardTimePeriod
import com.example.model.PerformanceCategory
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.theme.MyApplicationTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GlobalRatingsLeaderboardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun computeGlobalRatingsLeaderboard_ordersDescendingByCumulativeRatingPointsAndAssignsTiersAndGaps() {
        val repository = PerformanceRepository()

        val rankings = repository.computeGlobalRatingsLeaderboard(
            categoryFilter = LeaderboardCategoryFilter.ALL,
            timePeriod = LeaderboardTimePeriod.ALL_TIME
        )

        // All 9 seeded creators should be ranked globally
        assertEquals(9, rankings.size)

        // Verify strict descending order by cumulativeRatingPoints and sequential 1..N ranks
        for (i in rankings.indices) {
            assertEquals(i + 1, rankings[i].rank)
            if (i > 0) {
                assertTrue(
                    "Expected rank ${i} (${rankings[i - 1].cumulativeRatingPoints}) >= rank ${i + 1} (${rankings[i].cumulativeRatingPoints})",
                    rankings[i - 1].cumulativeRatingPoints >= rankings[i].cumulativeRatingPoints
                )
            }
        }

        // #1 Leader: Alicia Ray (4.95 * 2210 = 10,939.5 cumulative stars -> Stage Legend)
        val leader = rankings.first()
        assertEquals("creator_2", leader.creatorId)
        assertEquals("Alicia Ray", leader.artistName)
        assertEquals(CreatorCompetitiveTier.LEGEND, leader.competitiveTier)
        assertEquals(0f, leader.pointsToNextRank, 0.01f)
        assertNull(leader.nextRankArtistName)
        assertEquals(100f, leader.ratingSharePercent, 0.01f)

        // #2 Runner-Up: Maya Vance (4.90 * 1840 = 9,016.0 cumulative stars -> Stage Legend)
        val runnerUp = rankings[1]
        assertEquals("creator_1", runnerUp.creatorId)
        assertEquals(CreatorCompetitiveTier.LEGEND, runnerUp.competitiveTier)
        assertEquals("Alicia Ray", runnerUp.nextRankArtistName)
        assertTrue(runnerUp.pointsToNextRank > 1900f)

        // Current User competitive chase standing (#9 chasing #8 Elena Vance)
        val myStanding = rankings.find { it.isCurrentUser }
        assertNotNull(myStanding)
        assertEquals("creator_user_me", myStanding!!.creatorId)
        assertEquals(9, myStanding.rank)
        assertEquals(CreatorCompetitiveTier.RISING_STAR, myStanding.competitiveTier)
        assertEquals("Elena Vance", myStanding.nextRankArtistName)
        assertTrue(myStanding.pointsToNextRank > 3000f)
        assertTrue(myStanding.fiveStarRatingsNeededToAdvance > 600)

        // Category filter: Spoken Word only
        val spokenWordRankings = repository.computeGlobalRatingsLeaderboard(
            categoryFilter = LeaderboardCategoryFilter.SPOKEN_WORD,
            timePeriod = LeaderboardTimePeriod.ALL_TIME
        )
        assertTrue(spokenWordRankings.isNotEmpty())
        assertTrue(spokenWordRankings.all { it.category == PerformanceCategory.SPOKEN_WORD })
        assertEquals("creator_1", spokenWordRankings[0].creatorId)
        assertEquals("creator_3", spokenWordRankings[1].creatorId)
        assertEquals("creator_6", spokenWordRankings[2].creatorId)

        // Global Arena Summary aggregates cumulative stars & weighted average accurately
        val summary = repository.computeGlobalRatingsArenaSummary(rankings)
        assertEquals(9, summary.totalRankedCreators)
        val expectedStars = rankings.sumOf { it.cumulativeRatingPoints.toDouble() }.toFloat()
        assertTrue(abs(summary.totalCumulativeRatingStars - expectedStars) < 1.0f)
        assertTrue(summary.globalWeightedAverageRating in 4.7f..5.0f)
    }

    @Test
    fun rateCreator_updatesCumulativeStarsAndDynamicallyOvertakesHigherRankedCreator() {
        val repository = PerformanceRepository()

        val initial = repository.computeGlobalRatingsLeaderboard(
            categoryFilter = LeaderboardCategoryFilter.ALL,
            timePeriod = LeaderboardTimePeriod.ALL_TIME
        )
        val initialSora = initial.first { it.creatorId == "creator_6" }
        val initialDarnell = initial.first { it.creatorId == "creator_3" }

        // Initially Darnell Cruz (4,704.0 ★) ranks ahead of Sora Chen (4,587.2 ★)
        assertTrue(initialDarnell.rank < initialSora.rank)
        val gap = initialDarnell.cumulativeRatingPoints - initialSora.cumulativeRatingPoints
        assertTrue(gap in 110f..125f)

        // Award 25 five-star audience ratings (+125 cumulative star points) to Sora Chen
        repeat(25) {
            repository.rateCreator("creator_6", 5.0f)
        }

        assertEquals(5, repository.userSubmittedRatings.value["creator_6"])

        val updated = repository.computeGlobalRatingsLeaderboard(
            categoryFilter = LeaderboardCategoryFilter.ALL,
            timePeriod = LeaderboardTimePeriod.ALL_TIME
        )
        val updatedSora = updated.first { it.creatorId == "creator_6" }
        val updatedDarnell = updated.first { it.creatorId == "creator_3" }

        assertEquals(initialSora.totalAudienceRatingsCount + 25, updatedSora.totalAudienceRatingsCount)
        assertTrue(updatedSora.cumulativeRatingPoints > updatedDarnell.cumulativeRatingPoints)
        assertTrue(
            "Expected Sora Chen (rank ${updatedSora.rank}) to overtake Darnell Cruz (rank ${updatedDarnell.rank})",
            updatedSora.rank < updatedDarnell.rank
        )
    }

    @Test
    fun leaderboardScreen_displaysCumulativeRatingsArenaPodiumStandingAndSupportsInlineQuickRateAndModeToggle() {
        val repository = PerformanceRepository()
        var currentMode by mutableStateOf(LeaderboardRankingMode.CUMULATIVE_RATINGS)
        var currentRankings by mutableStateOf(
            repository.computeLeaderboard(
                categoryFilter = LeaderboardCategoryFilter.ALL,
                timePeriod = LeaderboardTimePeriod.ALL_TIME,
                rankingMode = LeaderboardRankingMode.CUMULATIVE_RATINGS
            )
        )
        var currentSummary by mutableStateOf(repository.computeGlobalRatingsArenaSummary(currentRankings))
        var submittedRatings by mutableStateOf<Map<String, Int>>(emptyMap())

        composeTestRule.setContent {
            MyApplicationTheme {
                LeaderboardScreen(
                    rankings = currentRankings,
                    selectedCategory = LeaderboardCategoryFilter.ALL,
                    selectedTimePeriod = LeaderboardTimePeriod.ALL_TIME,
                    selectedRankingMode = currentMode,
                    onRankingModeChanged = { newMode ->
                        currentMode = newMode
                        currentRankings = repository.computeLeaderboard(
                            categoryFilter = LeaderboardCategoryFilter.ALL,
                            timePeriod = LeaderboardTimePeriod.ALL_TIME,
                            rankingMode = newMode
                        )
                    },
                    arenaSummary = currentSummary,
                    userSubmittedRatings = submittedRatings,
                    onCategoryChanged = {},
                    onTimePeriodChanged = {},
                    onPlayPerformance = {},
                    onCreatorClicked = { _, _ -> },
                    onRateCreator = { creatorId, stars ->
                        repository.rateCreator(creatorId, stars)
                        submittedRatings = submittedRatings + (creatorId to stars.roundToInt())
                        val refreshed = repository.computeLeaderboard(
                            categoryFilter = LeaderboardCategoryFilter.ALL,
                            timePeriod = LeaderboardTimePeriod.ALL_TIME,
                            rankingMode = currentMode
                        )
                        currentRankings = refreshed
                        currentSummary = repository.computeGlobalRatingsArenaSummary(refreshed)
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // 1. Verify Global Leaderboard Header & Cumulative Ratings Arena Banner
        composeTestRule.onNodeWithTag("leaderboard_header_title", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Global Creator Leaderboard")
        composeTestRule.onNodeWithTag("global_ratings_arena_banner")
            .assertIsDisplayed()

        // 2. Scroll to Personal Competitive Standing Card and verify chase engagement telemetry
        composeTestRule.onNodeWithTag("leaderboard_rankings_list")
            .performScrollToNode(hasTestTag("competitive_standing_card"))
        composeTestRule.onNodeWithTag("competitive_standing_card")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("competitive_standing_chase_text", useUnmergedTree = true)
            .assertTextContains("Elena Vance", substring = true)

        // 3. Scroll to Top-3 Cumulative Ratings Podium
        composeTestRule.onNodeWithTag("leaderboard_rankings_list")
            .performScrollToNode(hasTestTag("global_ratings_podium_card"))
        composeTestRule.onNodeWithTag("global_ratings_podium_card")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("podium_cumulative_stars_1", useUnmergedTree = true)
            .assertTextContains("10,940", substring = true)

        // 4. Scroll to #1 Ranked Creator Card (creator_2 = Alicia Ray) and submit a 5-star quick rating
        composeTestRule.onNodeWithTag("leaderboard_rankings_list")
            .performScrollToNode(hasTestTag("creator_ranking_card_1"))
        composeTestRule.onNodeWithTag("leaderboard_rate_button_creator_2")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("leaderboard_quick_star_creator_2_5")
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(5, submittedRatings["creator_2"])
        composeTestRule.onNodeWithTag("leaderboard_rate_button_creator_2")
            .assertTextContains("Rated 5★")
        composeTestRule.onNodeWithTag("creator_cumulative_stars_creator_2", useUnmergedTree = true)
            .assertTextContains("10,945", substring = true)

        // 5. Scroll back to top and switch mode to Stage Votes
        composeTestRule.onNodeWithTag("leaderboard_rankings_list")
            .performScrollToNode(hasTestTag("leaderboard_mode_toggle_bar"))
        composeTestRule.onNodeWithTag("leaderboard_mode_STAGE_VOTES")
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(LeaderboardRankingMode.STAGE_VOTES, currentMode)
        composeTestRule.onNodeWithTag("votes_aggregation_banner")
            .assertIsDisplayed()
    }
}
