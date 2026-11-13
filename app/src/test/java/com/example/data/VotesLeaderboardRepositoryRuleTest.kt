package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.LeaderboardTimePeriod
import com.example.model.PerformanceCategory
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class VotesLeaderboardRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun castVote_authenticatedUser_createsVoteAndStreamsAggregatedLeaderboard() = runBlocking {
        val runSuffix = UUID.randomUUID().toString().take(8)
        val performerTop = "perf_top_$runSuffix"
        val performerRunnerUp = "perf_runner_$runSuffix"

        signInTestUser("alice_$runSuffix@test.com")
        val aliceRepo = VotesLeaderboardRepository(firestore, auth)

        val vote1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.castVote(
                performerId = performerTop,
                performerName = "Alicia Ray",
                category = PerformanceCategory.MUSIC,
                voteDelta = 5,
                voteTier = "SUPER_VOTE",
                genre = "Neo-Soul"
            )
        }
        assertTrue("Expected Alice's vote to succeed: ${vote1.exceptionOrNull()}", vote1.isSuccess)

        signInTestUser("bob_$runSuffix@test.com")
        val bobRepo = VotesLeaderboardRepository(firestore, auth)

        val vote2 = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.castVote(
                performerId = performerTop,
                performerName = "Alicia Ray",
                category = PerformanceCategory.MUSIC,
                voteDelta = 10,
                voteTier = "STANDING_OVATION",
                genre = "Neo-Soul"
            )
        }
        assertTrue("Expected Bob's vote for top performer to succeed", vote2.isSuccess)

        val vote3 = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.castVote(
                performerId = performerRunnerUp,
                performerName = "Maya Vance",
                category = PerformanceCategory.SPOKEN_WORD,
                voteDelta = 4,
                voteTier = "VOTE",
                genre = "Slam Poetry"
            )
        }
        assertTrue("Expected Bob's vote for runner-up performer to succeed", vote3.isSuccess)

        val snapshot = withTimeout(FLOW_TIMEOUT_MS) {
            bobRepo.observeLeaderboardAggregation(LeaderboardTimePeriod.ALL_TIME).first { agg ->
                val topSummary = agg.performerSummaries.find { it.performerId == performerTop }
                val runnerSummary = agg.performerSummaries.find { it.performerId == performerRunnerUp }
                topSummary?.aggregatedVotes == 15 && runnerSummary?.aggregatedVotes == 4
            }
        }

        val topSummary = snapshot.performerSummaries.first { it.performerId == performerTop }
        val runnerSummary = snapshot.performerSummaries.first { it.performerId == performerRunnerUp }
        assertEquals(15, topSummary.aggregatedVotes)
        assertEquals(2, topSummary.uniqueVoterCount)
        assertEquals(4, runnerSummary.aggregatedVotes)
        assertEquals(1, runnerSummary.uniqueVoterCount)

        val topIndex = snapshot.performerSummaries.indexOfFirst { it.performerId == performerTop }
        val runnerIndex = snapshot.performerSummaries.indexOfFirst { it.performerId == performerRunnerUp }
        assertTrue("Top performer must rank higher than runner-up", topIndex < runnerIndex)
    }

    @Test
    fun revokeVote_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val runSuffix = UUID.randomUUID().toString().take(8)
        signInTestUser("owner_$runSuffix@test.com")
        val ownerRepo = VotesLeaderboardRepository(firestore, auth)

        val voteId = withTimeout(DEFAULT_TIMEOUT_MS) {
            ownerRepo.castVote(
                performerId = "creator_owner_$runSuffix",
                performerName = "Darnell Cruz",
                category = PerformanceCategory.SPOKEN_WORD,
                voteDelta = 3
            ).getOrThrow()
        }

        signInTestUser("intruder_$runSuffix@test.com")
        val intruderRepo = VotesLeaderboardRepository(firestore, auth)
        val revokeAttempt = withTimeout(DEFAULT_TIMEOUT_MS) {
            intruderRepo.revokeVote(voteId)
        }

        assertTrue("Expected cross-user revoke to fail", revokeAttempt.isFailure)
        val firestoreEx = revokeAttempt.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeCountedVotes_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = VotesLeaderboardRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeCountedVotes().first()
            }
            fail("Expected unauthenticated observeCountedVotes() to fail with PERMISSION_DENIED")
        } catch (e: Exception) {
            val firestoreEx = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
