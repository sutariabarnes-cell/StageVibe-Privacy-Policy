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
import com.example.model.CreatorAchievementEngine
import com.example.model.CreatorProfile
import com.example.model.CreatorSessionStats
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.SynthAudioType
import com.example.model.VisualizerTheme
import com.example.ui.components.CreatorStatsProfileView
import com.example.ui.screens.CreatorProfileScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CreatorProfileStatsAndBadgesTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun samplePerformances(): List<Performance> = listOf(
        Performance(
            id = "p_rap_1",
            title = "Neon Cipher Bars",
            artistId = "creator_maya",
            artistName = "Maya Vance",
            avatarInitials = "MV",
            category = PerformanceCategory.MUSIC,
            genre = "Rap & Hip-Hop",
            durationSec = 165,
            lyricsOrScript = "Spitting electric truth under stage lights",
            visualizerTheme = VisualizerTheme.COSMIC_VINYL,
            audioType = SynthAudioType.HIPHOP_BEAT,
            customVideoPath = "/storage/emulated/0/stage_rap.mp4",
            viewsCount = 1200,
            likesCount = 340,
            votesCount = 320,
            cheersCount = 85,
            styleId = "rap"
        ),
        Performance(
            id = "p_poetry_2",
            title = "Midnight Stanzas",
            artistId = "creator_maya",
            artistName = "Maya Vance",
            avatarInitials = "MV",
            category = PerformanceCategory.SPOKEN_WORD,
            genre = "Slam Poetry",
            durationSec = 140,
            lyricsOrScript = "Echoes across the concrete amphitheater",
            visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
            audioType = SynthAudioType.POETRY_AMBIENCE,
            viewsCount = 850,
            likesCount = 260,
            votesCount = 240,
            cheersCount = 60,
            styleId = "poetry"
        ),
        Performance(
            id = "p_jazz_3",
            title = "Velvet Brass Nocturne",
            artistId = "creator_maya",
            artistName = "Maya Vance",
            avatarInitials = "MV",
            category = PerformanceCategory.MUSIC,
            genre = "Vocal & Nu-Jazz",
            durationSec = 195,
            lyricsOrScript = "Blue note cadence in 5/4 time",
            visualizerTheme = VisualizerTheme.NEON_WAVES,
            audioType = SynthAudioType.PIANO_WARMTH,
            viewsCount = 640,
            likesCount = 190,
            votesCount = 150,
            cheersCount = 42,
            styleId = "jazz"
        )
    )

    @Test
    fun creatorAchievementEngine_computesTotalPerformancesRatingAndUnlocksBadgesDeterministically() {
        val profile = CreatorProfile(
            creatorId = "creator_maya",
            artistName = "Maya Vance",
            stageTitle = "Poet Laureate & Emcee",
            bio = "Blending slam poetry, rap ciphers, and nu-jazz.",
            primaryCategory = PerformanceCategory.SPOKEN_WORD,
            audienceRating = 4.9f,
            totalAudienceRatingsCount = 48,
            isCurrentUser = false
        )
        val performances = samplePerformances()
        val sessionStats = CreatorSessionStats(totalSessions = 0)

        val summary = CreatorAchievementEngine.computeSummary(
            profile = profile,
            performances = performances,
            sessionStats = sessionStats,
            leaderboardRank = 2
        )

        assertEquals(3, summary.totalPerformances)
        assertEquals("4.9", summary.formattedAverageRating)
        assertEquals(48, summary.totalAudienceRatingsCount)
        assertEquals(710, summary.totalVotes)
        assertEquals(2690, summary.totalViews)
        assertEquals(790, summary.totalLikes)
        assertEquals(3, summary.distinctStylesCount)
        assertTrue(summary.hasVideoPerformance)

        // 7 of 8 badges unlocked; only live_stage_host is still in progress (0 / 1 live session)
        assertEquals(7, summary.unlockedBadgesCount)
        assertEquals(8, summary.totalBadgesCount)

        val byId = summary.badges.associateBy { it.id }
        assertTrue(byId.getValue("stage_debut").isUnlocked)
        assertTrue(byId.getValue("prolific_performer").isUnlocked)
        assertTrue(byId.getValue("crowd_darling").isUnlocked)
        assertTrue(byId.getValue("vote_magnet").isUnlocked)
        assertTrue(byId.getValue("arena_headliner").isUnlocked)
        assertFalse(byId.getValue("live_stage_host").isUnlocked)
        assertTrue(byId.getValue("genre_chameleon").isUnlocked)
        assertTrue(byId.getValue("visual_visionary").isUnlocked)
    }

    @Test
    fun creatorStatsProfileView_displaysStatisticsRatingAndInteractiveBadgeGallery() {
        var currentProfile by mutableStateOf(
            CreatorProfile(
                creatorId = "creator_maya",
                artistName = "Maya Vance",
                stageTitle = "Poet Laureate & Emcee",
                bio = "Blending slam poetry and boom-bap rhythms.",
                primaryCategory = PerformanceCategory.SPOKEN_WORD,
                audienceRating = 4.9f,
                totalAudienceRatingsCount = 19,
                isCurrentUser = false
            )
        )
        val performances = samplePerformances().take(2) // 2 performances -> prolific_performer in progress (2/3)

        composeTestRule.setContent {
            MyApplicationTheme {
                CreatorStatsProfileView(
                    profile = currentProfile,
                    performances = performances,
                    sessionStats = CreatorSessionStats(totalSessions = 1),
                    leaderboardRank = 1,
                    onRateCreator = { stars ->
                        val nextCount = currentProfile.totalAudienceRatingsCount + 1
                        val updatedAvg = ((currentProfile.audienceRating * currentProfile.totalAudienceRatingsCount) + stars) / nextCount
                        currentProfile = currentProfile.copy(
                            audienceRating = updatedAvg,
                            totalAudienceRatingsCount = nextCount
                        )
                    }
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify artist identity, total performances (2), and average audience rating (4.9)
        composeTestRule.onNodeWithTag("creator_stats_artist_name", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Maya Vance")
        composeTestRule.onNodeWithTag("creator_stat_total_performances_value", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("2")
        composeTestRule.onNodeWithTag("creator_stat_average_rating_value", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("4.9")
        composeTestRule.onNodeWithTag("creator_stat_ratings_count", useUnmergedTree = true)
            .assertTextContains("19 audience ratings")

        // Scroll statistics card fully into viewport, submit a 5-star audience rating, and verify review count updates reactively to 20
        composeTestRule.onNodeWithTag("creator_stats_profile_list")
            .performScrollToNode(hasTestTag("creator_statistics_card"))
        composeTestRule.onNodeWithTag("rate_creator_star_5").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("creator_stat_ratings_count", useUnmergedTree = true)
            .assertTextContains("20 audience ratings")

        // Scroll to Achievement Badge Gallery and verify unlocked counter (7 / 8 Unlocked)
        composeTestRule.onNodeWithTag("creator_stats_profile_list")
            .performScrollToNode(hasTestTag("achievement_badge_gallery"))
        composeTestRule.onNodeWithTag("badge_gallery_unlocked_count", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("7 / 8 Unlocked")

        // Filter to In Progress -> only Prolific Performer (2 / 3 performances) remains visible
        composeTestRule.onNodeWithTag("badge_filter_row")
            .performScrollToNode(hasTestTag("badge_filter_in_progress"))
        composeTestRule.onNodeWithTag("badge_filter_in_progress").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("badge_title_prolific_performer", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextContains("Prolific Performer")
        composeTestRule.onNodeWithTag("badge_title_stage_debut", useUnmergedTree = true)
            .assertDoesNotExist()

        // Tap Prolific Performer badge card -> opens detail dialog and dismisses cleanly
        composeTestRule.onNodeWithTag("badge_card_prolific_performer").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("badge_detail_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("badge_dialog_title", useUnmergedTree = true)
            .assertTextContains("Prolific Performer")
        composeTestRule.onNodeWithTag("badge_dialog_close_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("badge_detail_dialog").assertDoesNotExist()
    }

    @Test
    fun creatorProfileScreen_integratesStatisticsAndBadgeGallerySeamlessWithPortfolio() {
        val myProfile = CreatorProfile(
            creatorId = "user_me",
            artistName = "Alicia Ray",
            stageTitle = "Soul & Spoken Word Artist",
            bio = "Original live arrangements from Studio.",
            primaryCategory = PerformanceCategory.MUSIC,
            audienceRating = 4.8f,
            totalAudienceRatingsCount = 25,
            isCurrentUser = true
        )
        val portfolio = samplePerformances()

        composeTestRule.setContent {
            MyApplicationTheme {
                CreatorProfileScreen(
                    profile = myProfile,
                    portfolio = portfolio,
                    leaderboardRank = 3,
                    sessionStats = CreatorSessionStats(totalSessions = 2),
                    onPlayPerformance = {},
                    onRateCreator = {},
                    onSaveProfile = { _, _, _, _, _, _, _, _ -> },
                    onNavigateToStudio = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("creator_profile_list")
            .performScrollToNode(hasTestTag("creator_statistics_card"))
        composeTestRule.onNodeWithTag("creator_stat_total_performances_value", useUnmergedTree = true)
            .assertTextContains("3")
        composeTestRule.onNodeWithTag("creator_stat_average_rating_value", useUnmergedTree = true)
            .assertTextContains("4.8")
        composeTestRule.onNodeWithTag("creator_stat_badges_unlocked_value", useUnmergedTree = true)
            .assertTextContains("8/8")

        composeTestRule.onNodeWithTag("creator_profile_list")
            .performScrollToNode(hasTestTag("achievement_badge_gallery"))
        composeTestRule.onNodeWithTag("badge_gallery_unlocked_count", useUnmergedTree = true)
            .assertTextContains("8 / 8 Unlocked")
    }
}
