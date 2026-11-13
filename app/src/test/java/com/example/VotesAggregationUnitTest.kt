package com.example

import com.example.data.PerformanceRepository
import com.example.data.VotesLeaderboardRepository
import com.example.model.LeaderboardCategoryFilter
import com.example.model.LeaderboardTimePeriod
import com.example.model.PerformanceCategory
import com.example.model.VoteRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VotesAggregationUnitTest {

    @Test
    fun aggregateVotes_sumsCountedVotesExcludesRevokedAndRanksDescending() {
        val now = 1_800_000_000_000L
        val twoDaysAgo = now - 2L * 24L * 60L * 60L * 1000L
        val twentyDaysAgo = now - 20L * 24L * 60L * 60L * 1000L

        val records = listOf(
            VoteRecord(
                id = "v1",
                performerId = "creator_maya",
                performerName = "Maya Vance",
                targetId = "creator_maya",
                category = PerformanceCategory.SPOKEN_WORD,
                userId = "user_a",
                userName = "User A",
                voteDelta = 10,
                status = "COUNTED",
                createdAtMillis = twoDaysAgo
            ),
            VoteRecord(
                id = "v2",
                performerId = "creator_alicia",
                performerName = "Alicia Ray",
                targetId = "creator_alicia",
                category = PerformanceCategory.MUSIC,
                userId = "user_b",
                userName = "User B",
                voteDelta = 6,
                status = "COUNTED",
                createdAtMillis = twoDaysAgo
            ),
            VoteRecord(
                id = "v3",
                performerId = "creator_alicia",
                performerName = "Alicia Ray",
                targetId = "creator_alicia",
                category = PerformanceCategory.MUSIC,
                userId = "user_c",
                userName = "User C",
                voteDelta = 8,
                status = "COUNTED",
                createdAtMillis = twentyDaysAgo
            ),
            VoteRecord(
                id = "v4_revoked",
                performerId = "creator_maya",
                performerName = "Maya Vance",
                targetId = "creator_maya",
                category = PerformanceCategory.SPOKEN_WORD,
                userId = "user_d",
                userName = "User D",
                voteDelta = 50,
                status = "REVOKED",
                createdAtMillis = twoDaysAgo
            )
        )

        // Weekly window: Maya has 10, Alicia has 6 (20-day-old vote excluded from Weekly, REVOKED excluded)
        val weekly = VotesLeaderboardRepository.aggregateVotes(
            votes = records,
            timePeriod = LeaderboardTimePeriod.WEEKLY,
            currentUserId = "user_a",
            nowMillis = now
        )
        assertEquals(16, weekly.totalCountedVotes)
        assertEquals("creator_maya", weekly.performerSummaries[0].performerId)
        assertEquals(10, weekly.performerSummaries[0].aggregatedVotes)
        assertTrue(weekly.performerSummaries[0].votedByCurrentUser)
        assertEquals("creator_alicia", weekly.performerSummaries[1].performerId)
        assertEquals(6, weekly.performerSummaries[1].aggregatedVotes)
        assertFalse(weekly.performerSummaries[1].votedByCurrentUser)

        // Monthly window: Alicia has 6 + 8 = 14, Maya has 10 -> Alicia overtakes #1
        val monthly = VotesLeaderboardRepository.aggregateVotes(
            votes = records,
            timePeriod = LeaderboardTimePeriod.MONTHLY,
            currentUserId = "user_a",
            nowMillis = now
        )
        assertEquals(24, monthly.totalCountedVotes)
        assertEquals("creator_alicia", monthly.performerSummaries[0].performerId)
        assertEquals(14, monthly.performerSummaries[0].aggregatedVotes)
        assertEquals(2, monthly.performerSummaries[0].uniqueVoterCount)
        assertEquals("creator_maya", monthly.performerSummaries[1].performerId)
        assertEquals(10, monthly.performerSummaries[1].aggregatedVotes)
    }

    @Test
    fun computeLeaderboard_castingVotesReordersTopPerformersInRealTime() {
        val repository = PerformanceRepository(context = null)
        val initial = repository.computeLeaderboard(
            categoryFilter = LeaderboardCategoryFilter.ALL,
            timePeriod = LeaderboardTimePeriod.ALL_TIME
        )
        assertTrue(initial.isNotEmpty())
        val runnerUp = initial[1]

        // Cast high-delta votes for the runner-up so they overtake #1
        repeat(4) {
            repository.castLeaderboardVote(runnerUp, voteDelta = 100, voteTier = "STANDING_OVATION")
        }

        val updated = repository.computeLeaderboard(
            categoryFilter = LeaderboardCategoryFilter.ALL,
            timePeriod = LeaderboardTimePeriod.ALL_TIME
        )
        assertEquals(runnerUp.creatorId, updated.first().creatorId)
        assertEquals(1, updated.first().rank)
        assertTrue(updated.first().cloudVotesCount >= 400)
    }
}
