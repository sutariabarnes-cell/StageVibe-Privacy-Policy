package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.StageStatus
import com.example.model.SynthAudioType
import com.example.model.VisualizerTheme
import com.example.model.VoteTier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LiveStageVotingServiceRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun hostCreatesStage_spectatorsVoteRevokeAndCheer_updatesStageCountersAndEnforcesRules() = runBlocking {
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8)
        val aliceUid = signInTestUser("alice_stage_$suffix@versepulse.stage")
        val service = LiveStageVotingService(firestore = firestore, auth = auth)

        val stageId = "stage_arena_$suffix"
        val alicePerformerId = "performer_alice_$suffix"
        val lyricPerformerId = "performer_lyric_$suffix"

        val initialPerformers = listOf(
            Performance(
                id = "perf_alice_$suffix",
                title = "Midnight Reverie",
                artistId = alicePerformerId,
                artistName = "Alice Ray",
                avatarInitials = "AR",
                category = PerformanceCategory.MUSIC,
                genre = "Neo-Soul",
                durationSec = 210,
                lyricsOrScript = "Golden embers on the stage",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.ACOUSTIC_SOUL,
                viewsCount = 10,
                likesCount = 3,
                votesCount = 0,
                cheersCount = 0
            ),
            Performance(
                id = "perf_lyric_$suffix",
                title = "City Pulse Cipher",
                artistId = lyricPerformerId,
                artistName = "Lyric Vance",
                avatarInitials = "LV",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Spoken Word",
                durationSec = 180,
                lyricsOrScript = "Echoes through the avenue",
                visualizerTheme = VisualizerTheme.PULSE_ORB,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                viewsCount = 8,
                likesCount = 2,
                votesCount = 0,
                cheersCount = 0
            )
        )

        // 1. Host Alice creates a LIVE performance stage with two performer voting counters
        val createRes = service.createStage(
            title = "Grand Finals Stage",
            category = PerformanceCategory.MUSIC,
            genre = "Neo-Soul & Poetry",
            description = "Live tournament stage with spectator voting counters",
            hostId = alicePerformerId,
            hostName = "Alice Ray",
            status = StageStatus.LIVE,
            initialSpectatorCount = 10,
            initialPerformers = initialPerformers,
            stageId = stageId
        )
        assertTrue("Expected createStage to succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)

        val liveStages = withTimeout(5_000L) {
            service.observeLiveStages().first { list -> list.any { it.id == stageId } }
        }
        assertEquals("Grand Finals Stage", liveStages.first { it.id == stageId }.title)

        // 2. Spectator Bob joins the stage and casts multi-tier votes + cheers
        signInTestUser("bob_stage_$suffix@versepulse.stage")

        val joinRes = service.joinStageAsSpectator(stageId)
        assertTrue("Expected spectator join to succeed: ${joinRes.exceptionOrNull()}", joinRes.isSuccess)
        assertEquals(11L, joinRes.getOrThrow())

        val superVoteRes = service.castSpectatorVote(
            stageId = stageId,
            performerId = alicePerformerId,
            performerName = "Alice Ray",
            category = PerformanceCategory.MUSIC,
            tier = VoteTier.SUPER_VOTE,
            performanceId = "perf_alice_$suffix",
            performanceTitle = "Midnight Reverie",
            voterName = "Bob Fan"
        )
        assertTrue("Expected +5 SUPER_VOTE to succeed: ${superVoteRes.exceptionOrNull()}", superVoteRes.isSuccess)

        val ovationVoteRes = service.castSpectatorVote(
            stageId = stageId,
            performerId = alicePerformerId,
            performerName = "Alice Ray",
            category = PerformanceCategory.MUSIC,
            tier = VoteTier.STANDING_OVATION,
            performanceId = "perf_alice_$suffix",
            performanceTitle = "Midnight Reverie",
            voterName = "Bob Fan"
        )
        assertTrue("Expected +10 STANDING_OVATION to succeed: ${ovationVoteRes.exceptionOrNull()}", ovationVoteRes.isSuccess)

        val lyricStandardVoteRes = service.castSpectatorVote(
            stageId = stageId,
            performerId = lyricPerformerId,
            performerName = "Lyric Vance",
            category = PerformanceCategory.SPOKEN_WORD,
            tier = VoteTier.STANDARD,
            performanceId = "perf_lyric_$suffix",
            performanceTitle = "City Pulse Cipher",
            voterName = "Bob Fan"
        )
        assertTrue("Expected +1 STANDARD vote to succeed: ${lyricStandardVoteRes.exceptionOrNull()}", lyricStandardVoteRes.isSuccess)
        val lyricVoteId = lyricStandardVoteRes.getOrThrow().id

        val cheerRes = service.recordStageCheer(
            stageId = stageId,
            performerId = alicePerformerId,
            performerName = "Alice Ray",
            category = PerformanceCategory.MUSIC,
            cheerDelta = 3
        )
        assertTrue("Expected recordStageCheer to succeed: ${cheerRes.exceptionOrNull()}", cheerRes.isSuccess)
        assertEquals(3L, cheerRes.getOrThrow())

        // 3. Observe real-time StageVotingSnapshot and verify atomic counter totals & tier breakdowns
        val snapshot = withTimeout(5_000L) {
            service.observeStageSnapshot(stageId).first { snap ->
                snap.totalStageVotes == 16 && snap.totalBallotsCast == 3 && snap.totalCheersCount == 3
            }
        }
        assertEquals(alicePerformerId, snapshot.leadingPerformerId)
        assertEquals(11, snapshot.spectatorCount)

        val aliceCounter = snapshot.counterFor(alicePerformerId)
        assertNotNull(aliceCounter)
        assertEquals(15L, aliceCounter!!.totalVotes)
        assertEquals(0L, aliceCounter.standardVotesCount)
        assertEquals(1L, aliceCounter.superVotesCount)
        assertEquals(1L, aliceCounter.ovationVotesCount)
        assertEquals(2L, aliceCounter.ballotsCast)
        assertEquals(1L, aliceCounter.uniqueVoterCount)
        assertEquals(3L, aliceCounter.cheersCount)

        val lyricCounter = snapshot.counterFor(lyricPerformerId)
        assertNotNull(lyricCounter)
        assertEquals(1L, lyricCounter!!.totalVotes)
        assertEquals(1L, lyricCounter.standardVotesCount)
        assertEquals(1L, lyricCounter.uniqueVoterCount)

        // 4. Bob revokes his +1 vote on Lyric Vance -> counter and stage totals decrement atomically
        val revokeRes = service.revokeSpectatorVote(stageId, lyricVoteId)
        assertTrue("Expected revokeSpectatorVote to succeed: ${revokeRes.exceptionOrNull()}", revokeRes.isSuccess)

        val postRevokeSnapshot = withTimeout(5_000L) {
            service.observeStageSnapshot(stageId).first { snap ->
                snap.totalStageVotes == 15 && snap.totalBallotsCast == 2
            }
        }
        assertEquals(0L, postRevokeSnapshot.counterFor(lyricPerformerId)?.totalVotes)
        assertEquals(0L, postRevokeSnapshot.counterFor(lyricPerformerId)?.uniqueVoterCount)

        // 5. Cross-user protection: Spectator Bob CANNOT spotlight performers, end, or delete Alice's stage
        assertTrue(
            "Expected cross-user setActivePerformer to fail",
            service.setActivePerformer(stageId, lyricPerformerId, "Lyric Vance").isFailure
        )
        assertTrue("Expected cross-user endStage to fail", service.endStage(stageId).isFailure)
        assertTrue("Expected cross-user deleteStage to fail", service.deleteStage(stageId).isFailure)

        // 6. Host Alice spotlights Lyric Vance, then ends the stage; subsequent votes are rejected
        val reloginUid = signInTestUser("alice_stage_$suffix@versepulse.stage")
        assertEquals(aliceUid, reloginUid)

        val spotlightRes = service.setActivePerformer(
            stageId = stageId,
            performerId = lyricPerformerId,
            performerName = "Lyric Vance",
            category = PerformanceCategory.SPOKEN_WORD,
            performanceId = "perf_lyric_$suffix",
            performanceTitle = "City Pulse Cipher"
        )
        assertTrue("Expected host setActivePerformer to succeed: ${spotlightRes.exceptionOrNull()}", spotlightRes.isSuccess)

        val endRes = service.endStage(stageId)
        assertTrue("Expected host endStage to succeed: ${endRes.exceptionOrNull()}", endRes.isSuccess)

        val voteAfterEnded = service.castSpectatorVote(
            stageId = stageId,
            performerId = alicePerformerId,
            performerName = "Alice Ray",
            tier = VoteTier.STANDARD
        )
        assertTrue("Expected voting on an ENDED stage to fail", voteAfterEnded.isFailure)
    }

    @Test
    fun unauthenticatedCaller_isRejectedForStageCreationVotingAndQueries() = runBlocking {
        auth.signOut()
        val service = LiveStageVotingService(firestore = firestore, auth = auth)

        val createFailure = service.createStage(
            title = "Unauth Stage",
            category = PerformanceCategory.MUSIC
        )
        assertTrue("Expected unauthenticated createStage to fail", createFailure.isFailure)

        val voteFailure = service.castSpectatorVote(
            stageId = "stage_any",
            performerId = "performer_any",
            performerName = "Any Performer"
        )
        assertTrue("Expected unauthenticated castSpectatorVote to fail", voteFailure.isFailure)

        val queryFailure = runCatching {
            withTimeout(5_000L) {
                service.observeLiveStages().first()
            }
        }
        assertTrue("Expected unauthenticated observeLiveStages query to fail with PERMISSION_DENIED", queryFailure.isFailure)
    }
}
