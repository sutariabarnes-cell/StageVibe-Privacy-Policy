package com.example.data

import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.SynthAudioType
import com.example.model.VisualizerTheme
import com.example.model.VoteRecord
import com.example.model.VoteTier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivePerformanceVotingManagerTest {

    private fun createSamplePerformance(
        id: String,
        title: String,
        artistId: String,
        artistName: String,
        votesCount: Int,
        category: PerformanceCategory = PerformanceCategory.MUSIC
    ): Performance = Performance(
        id = id,
        title = title,
        artistId = artistId,
        artistName = artistName,
        avatarInitials = artistName.take(2).uppercase(),
        category = category,
        genre = "Neo-Soul",
        durationSec = 180,
        lyricsOrScript = "Sample lyrics",
        visualizerTheme = VisualizerTheme.NEON_WAVES,
        audioType = SynthAudioType.ACOUSTIC_SOUL,
        viewsCount = 100,
        likesCount = 20,
        votesCount = votesCount,
        cheersCount = 5
    )

    @Test
    fun syncActivePerformances_initializesStateFlowCountsSharesAndLeader() = runBlocking {
        val manager = ActivePerformanceVotingManager()
        val perfA = createSamplePerformance("perf_a", "Midnight Echo", "artist_1", "Maya", 40)
        val perfB = createSamplePerformance("perf_b", "Solar Verse", "artist_2", "Leo", 60, PerformanceCategory.SPOKEN_WORD)

        manager.syncActivePerformances(listOf(perfA, perfB), currentUserId = "voter_1")

        val state = manager.votingState.value
        val counts = manager.voteCounts.value

        assertEquals(2, state.activePerformanceCount)
        assertEquals(100, state.totalActiveVotes)
        assertEquals("perf_b", state.leadingPerformanceId)
        assertEquals(40, counts["perf_a"])
        assertEquals(60, counts["perf_b"])
        assertEquals(40f, state.getVoteSharePercent("perf_a"), 0.01f)
        assertEquals(60f, state.getVoteSharePercent("perf_b"), 0.01f)
        assertTrue(state.isLeading("perf_b"))
        assertFalse(state.isLeading("perf_a"))
        assertEquals(60, manager.observeVoteCount("perf_b").first())
    }

    @Test
    fun castVote_updatesStateFlowAcrossTiersAndShiftsLeader() {
        val manager = ActivePerformanceVotingManager()
        val perfA = createSamplePerformance("perf_a", "Midnight Echo", "artist_1", "Maya", 50)
        val perfB = createSamplePerformance("perf_b", "Solar Verse", "artist_2", "Leo", 54)
        manager.syncActivePerformances(listOf(perfA, perfB), currentUserId = "voter_1")

        // Cast +5 Super Vote on perf_a -> 55 vs 54 (perf_a becomes leader)
        val cast = manager.castVote(
            performanceId = "perf_a",
            voterId = "voter_1",
            voterName = "Alex",
            tier = VoteTier.SUPER_VOTE,
            voteId = "vote_super_1"
        )
        assertNotNull(cast)

        val stateAfterSuper = manager.votingState.value
        assertEquals(55, stateAfterSuper.getVoteCount("perf_a"))
        assertEquals(55, manager.voteCounts.value["perf_a"])
        assertEquals("perf_a", stateAfterSuper.leadingPerformanceId)
        assertTrue(stateAfterSuper.isVotedByCurrentUser("perf_a"))
        assertEquals(5, stateAfterSuper.getPerformanceVoteState("perf_a")?.currentUserVoteTotal)
        assertEquals(VoteTier.SUPER_VOTE, stateAfterSuper.recentVote?.resolvedTier)

        // Cast +10 Standing Ovation from another audience member on perf_b -> 55 vs 64 (perf_b retakes lead)
        manager.castVote(
            performanceId = "perf_b",
            voterId = "voter_2",
            voterName = "Sam",
            tier = VoteTier.STANDING_OVATION,
            voteId = "vote_ovation_1"
        )

        val stateAfterOvation = manager.votingState.value
        assertEquals(64, stateAfterOvation.getVoteCount("perf_b"))
        assertEquals(119, stateAfterOvation.totalActiveVotes)
        assertEquals("perf_b", stateAfterOvation.leadingPerformanceId)
        assertEquals(1, stateAfterOvation.getPerformanceVoteState("perf_b")?.uniqueVotersCount)
    }

    @Test
    fun toggleVote_incrementsAndDecrementsSingleUserVoteCleanly() {
        val manager = ActivePerformanceVotingManager()
        val perfA = createSamplePerformance("perf_a", "Midnight Echo", "artist_1", "Maya", 10)
        manager.syncActivePerformances(listOf(perfA), currentUserId = "voter_1")

        val (votedOn, deltaOn) = manager.toggleVote("perf_a", voterId = "voter_1", voterName = "Alex")
        assertTrue(votedOn)
        assertEquals(1, deltaOn)
        assertEquals(11, manager.voteCounts.value["perf_a"])
        assertTrue(manager.votingState.value.isVotedByCurrentUser("perf_a"))

        val (votedOff, deltaOff) = manager.toggleVote("perf_a", voterId = "voter_1", voterName = "Alex")
        assertFalse(votedOff)
        assertEquals(-1, deltaOff)
        assertEquals(10, manager.voteCounts.value["perf_a"])
        assertFalse(manager.votingState.value.isVotedByCurrentUser("perf_a"))
    }

    @Test
    fun reconcileCloudVotes_deduplicatesOptimisticVoteAndAppliesRemoteVotes() {
        val manager = ActivePerformanceVotingManager()
        val perfA = createSamplePerformance("perf_a", "Midnight Echo", "artist_1", "Maya", 20)
        manager.syncActivePerformances(listOf(perfA), currentUserId = "voter_1")

        // Optimistic local vote with deterministic ID "vote_opt_123"
        manager.castVote(
            performanceId = "perf_a",
            voterId = "voter_1",
            voterName = "Alex",
            tier = VoteTier.STANDARD,
            voteId = "vote_opt_123"
        )
        assertEquals(21, manager.voteCounts.value["perf_a"])

        // Firestore snapshot arrives containing confirmed "vote_opt_123" + a remote "+5" vote from "voter_remote"
        val cloudRecords = listOf(
            VoteRecord(
                id = "vote_opt_123",
                performerId = "artist_1",
                performerName = "Maya",
                targetId = "perf_a",
                targetType = "PERFORMANCE",
                performanceId = "perf_a",
                userId = "voter_1",
                userName = "Alex",
                voteDelta = 1,
                voteTier = "VOTE",
                status = "COUNTED"
            ),
            VoteRecord(
                id = "vote_remote_456",
                performerId = "artist_1",
                performerName = "Maya",
                targetId = "perf_a",
                targetType = "PERFORMANCE",
                performanceId = "perf_a",
                userId = "voter_remote",
                userName = "Taylor",
                voteDelta = 5,
                voteTier = "SUPER_VOTE",
                status = "COUNTED"
            )
        )

        manager.reconcileCloudVotes(cloudRecords, currentUserId = "voter_1", isCloudSynced = true)

        val state = manager.votingState.value
        assertTrue(state.isCloudSynced)
        // Baseline 20 + confirmed 1 (not double-counted!) + remote 5 = 26
        assertEquals(26, state.getVoteCount("perf_a"))
        assertEquals(2, state.getPerformanceVoteState("perf_a")?.uniqueVotersCount)
    }

    @Test
    fun setPerformanceActive_excludesInactivePerformancesFromActiveTallies() {
        val manager = ActivePerformanceVotingManager()
        val perfA = createSamplePerformance("perf_a", "Midnight Echo", "artist_1", "Maya", 30)
        val perfB = createSamplePerformance("perf_b", "Solar Verse", "artist_2", "Leo", 50)
        manager.syncActivePerformances(listOf(perfA, perfB), currentUserId = "voter_1")

        manager.setPerformanceActive("perf_b", isActive = false)

        val state = manager.votingState.value
        assertEquals(1, state.activePerformanceCount)
        assertEquals(30, state.totalActiveVotes)
        assertEquals("perf_a", state.leadingPerformanceId)
        assertFalse(manager.voteCounts.value.containsKey("perf_b"))
        assertEquals(30, manager.voteCounts.value["perf_a"])
    }

    @Test
    fun stageVotingSnapshot_aggregatesPerformerCountersLeaderAndLiveSessionMapping() {
        val stage = com.example.model.PerformanceStage(
            id = "stage_main_1",
            ownerUid = "host_uid_1",
            hostId = "creator_1",
            hostName = "Maya Lin",
            title = "Midnight Cypher Stage",
            category = PerformanceCategory.MUSIC.name,
            genre = "Neo-Soul",
            status = com.example.model.StageStatus.LIVE.name,
            spectatorCount = 42L,
            totalStageVotes = 25L,
            totalBallotsCast = 5L,
            totalCheersCount = 12L
        )
        val counters = listOf(
            com.example.model.StageVotingCounter(
                id = "creator_1",
                stageId = "stage_main_1",
                performerId = "creator_1",
                performerName = "Maya Lin",
                performanceId = "perf_1",
                totalVotes = 18L,
                standardVotesCount = 3L,
                superVotesCount = 1L,
                ovationVotesCount = 1L,
                ballotsCast = 5L,
                uniqueVoterCount = 3L,
                cheersCount = 8L
            ),
            com.example.model.StageVotingCounter(
                id = "creator_2",
                stageId = "stage_main_1",
                performerId = "creator_2",
                performerName = "Leo Verse",
                performanceId = "perf_2",
                totalVotes = 9L,
                standardVotesCount = 4L,
                superVotesCount = 1L,
                ovationVotesCount = 0L,
                ballotsCast = 5L,
                uniqueVoterCount = 4L,
                cheersCount = 6L
            )
        )

        val snapshot = LiveStageVotingService.buildStageSnapshot(
            stageId = "stage_main_1",
            stage = stage,
            counters = counters,
            isCloudSynced = true
        )

        assertEquals("creator_1", snapshot.leadingPerformerId)
        assertEquals(27, snapshot.totalStageVotes) // max(stage 25, sum 27)
        assertEquals(10, snapshot.totalBallotsCast) // max(stage 5, sum 10)
        assertEquals(14, snapshot.totalCheersCount) // max(stage 12, sum 14)
        assertEquals(42, snapshot.spectatorCount)
        assertTrue(snapshot.isCloudSynced)
        assertEquals(18L, snapshot.counterFor("creator_1")?.totalVotes)
        assertEquals(9L, snapshot.counterFor("perf_2")?.totalVotes)
        assertEquals(0.666f, snapshot.counterFor("creator_1")!!.voteShareFraction(27L), 0.01f)

        val mappedSession = stage.toLiveSession(currentUserId = "host_uid_1")
        assertTrue(mappedSession.isLive)
        assertTrue(mappedSession.isCreatorMe)
        assertEquals(42, mappedSession.spectatorCount)

        val ballotIdA = LiveStageVotingService.buildFirstBallotId("stage_main_1", "creator_1", "voter_99")
        val ballotIdB = LiveStageVotingService.buildFirstBallotId("stage_main_1", "creator_1", "voter_99")
        assertEquals(ballotIdA, ballotIdB)
    }
}
