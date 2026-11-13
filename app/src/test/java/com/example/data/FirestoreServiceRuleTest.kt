package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.Performer
import com.example.model.SynthAudioType
import com.example.model.VisualizerTheme
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FirestoreServiceRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var service: FirestoreService

    @Before
    fun setUpService() {
        service = FirestoreService(firestore, auth)
    }

    @Test
    fun savePerformer_ratePerformer_and_crossUserDeleteProtection() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(10)
        val performerId = "performer_$runId"

        val aliceUid = signInTestUser("alice_perf_$runId@versepulse.test")

        val performer = Performer(
            id = performerId,
            creatorId = performerId,
            ownerUid = aliceUid,
            name = "Maya Vance",
            stageTitle = "Grand Slam Poet",
            bio = "Spoken word storyteller.",
            category = PerformanceCategory.SPOKEN_WORD,
            rating = 5.0f,
            ratingCount = 1,
            totalVotes = 0,
            totalPerformances = 1,
            totalSessions = 2,
            socialSpotify = "@spotify/mayavance"
        )

        val saveRes = withTimeout(5000L) {
            service.savePerformer(performer)
        }
        assertTrue("Expected savePerformer to succeed: ${saveRes.exceptionOrNull()}", saveRes.isSuccess)

        val observed = withTimeout(5000L) {
            service.observePerformers(PerformanceCategory.SPOKEN_WORD)
                .first { list -> list.any { it.creatorId == performerId } }
        }
        val fetched = observed.first { it.creatorId == performerId }
        assertEquals("Maya Vance", fetched.name)
        assertEquals("@spotify/mayavance", fetched.socialSpotify)

        // Bob signs in, rates Maya's public performer profile (allowed), and attempts to delete it (denied)
        signInTestUser("bob_perf_$runId@versepulse.test")

        val rateRes = withTimeout(5000L) {
            service.ratePerformer(performerId, 4.0f)
        }
        assertTrue("Expected Bob rating public performer to succeed: ${rateRes.exceptionOrNull()}", rateRes.isSuccess)

        val updatedPerformer = withTimeout(5000L) {
            service.getPerformer(performerId)
        }.getOrNull()
        assertNotNull(updatedPerformer)
        assertEquals(2, updatedPerformer!!.ratingCount)
        assertEquals(4.5f, updatedPerformer.rating, 0.05f)

        val bobDeleteRes = withTimeout(5000L) {
            service.deletePerformer(performerId)
        }
        assertTrue("Expected Bob deleting Alice's performer to fail", bobDeleteRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            bobDeleteRes.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun savePerformance_updateEngagement_and_crossUserDeleteProtection() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(10)
        val artistId = "artist_$runId"
        val perfId = "track_$runId"

        signInTestUser("alice_track_$runId@versepulse.test")

        val track = Performance(
            id = perfId,
            title = "Midnight Reverie",
            artistId = artistId,
            artistName = "Alicia Ray",
            avatarInitials = "AR",
            category = PerformanceCategory.MUSIC,
            genre = "Neo-Soul",
            durationSec = 200,
            lyricsOrScript = "Golden embers of a melody",
            visualizerTheme = VisualizerTheme.NEON_WAVES,
            audioType = SynthAudioType.ACOUSTIC_SOUL,
            viewsCount = 10,
            likesCount = 2,
            votesCount = 0,
            cheersCount = 1,
            isUserCreated = true
        )

        val saveRes = withTimeout(5000L) {
            service.savePerformance(track)
        }
        assertTrue("Expected savePerformance to succeed: ${saveRes.exceptionOrNull()}", saveRes.isSuccess)

        // Bob signs in, views/likes/cheers the public performance, and verifies engagement updates
        signInTestUser("bob_track_$runId@versepulse.test")

        val engageRes = withTimeout(5000L) {
            service.updatePerformanceEngagement(
                performanceId = perfId,
                viewsDelta = 3L,
                likesDelta = 1L,
                cheersDelta = 2L
            )
        }
        assertTrue("Expected audience engagement update to succeed: ${engageRes.exceptionOrNull()}", engageRes.isSuccess)

        val artistTracks = withTimeout(5000L) {
            service.observePerformancesByPerformer(artistId)
                .first { list -> list.any { it.id == perfId && it.viewsCount == 13 } }
        }
        val syncedTrack = artistTracks.first { it.id == perfId }
        assertEquals(13, syncedTrack.viewsCount)
        assertEquals(3, syncedTrack.likesCount)
        assertEquals(3, syncedTrack.cheersCount)

        // Bob cannot delete Alice's performance
        val deleteRes = withTimeout(5000L) {
            service.deletePerformance(perfId)
        }
        assertTrue("Expected cross-user performance delete to fail", deleteRes.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            deleteRes.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun castVote_syncsVotesCollection_and_incrementsPerformerAndPerformanceCounters() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(10)
        val performerId = "stage_star_$runId"
        val perfId = "stage_song_$runId"

        val aliceUid = signInTestUser("alice_sync_$runId@versepulse.test")

        // Alice creates her performer profile and performance
        assertTrue(
            withTimeout(5000L) {
                service.savePerformer(
                    Performer(
                        id = performerId,
                        creatorId = performerId,
                        ownerUid = aliceUid,
                        name = "Sora Chen",
                        category = PerformanceCategory.SPOKEN_WORD,
                        totalVotes = 5
                    )
                )
            }.isSuccess
        )

        assertTrue(
            withTimeout(5000L) {
                service.savePerformance(
                    Performance(
                        id = perfId,
                        title = "Mother's Lullaby",
                        artistId = performerId,
                        artistName = "Sora Chen",
                        avatarInitials = "SC",
                        category = PerformanceCategory.SPOKEN_WORD,
                        genre = "Cinematic",
                        durationSec = 180,
                        lyricsOrScript = "Before there were blueprints...",
                        visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                        audioType = SynthAudioType.POETRY_AMBIENCE,
                        viewsCount = 5,
                        likesCount = 2,
                        votesCount = 5,
                        cheersCount = 1,
                        isUserCreated = true
                    )
                )
            }.isSuccess
        )

        // Audience voter Bob casts a SUPER_VOTE (+5) via FirestoreService
        signInTestUser("bob_voter_$runId@versepulse.test")

        val voteRes = withTimeout(5000L) {
            service.castVote(
                performerId = performerId,
                performerName = "Sora Chen",
                category = PerformanceCategory.SPOKEN_WORD,
                voteDelta = 5,
                voteTier = "SUPER_VOTE",
                performanceId = perfId,
                topPerformanceTitle = "Mother's Lullaby"
            )
        }
        assertTrue("Expected castVote to succeed: ${voteRes.exceptionOrNull()}", voteRes.isSuccess)

        val votesForPerformer = withTimeout(5000L) {
            service.observeVotes(performerId).first { it.isNotEmpty() }
        }
        assertEquals(5, votesForPerformer.sumOf { it.voteDelta })

        val updatedPerformer = withTimeout(5000L) { service.getPerformer(performerId) }.getOrNull()
        assertNotNull(updatedPerformer)
        assertEquals(10, updatedPerformer!!.totalVotes)

        val updatedPerformance = withTimeout(5000L) { service.getPerformance(perfId) }.getOrNull()
        assertNotNull(updatedPerformance)
        assertEquals(10, updatedPerformance!!.votesCount)
    }

    @Test
    fun unauthenticatedCaller_rejectedAcrossPerformersPerformancesAndVotes() = runBlocking {
        auth.signOut()

        val performersErr = runCatching {
            withTimeout(5000L) { service.observePerformers().first() }
        }.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(performersErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, performersErr!!.code)

        val performancesErr = runCatching {
            withTimeout(5000L) { service.observePerformances().first() }
        }.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(performancesErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, performancesErr!!.code)

        val votesErr = runCatching {
            withTimeout(5000L) { service.observeVotes().first() }
        }.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(votesErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, votesErr!!.code)
    }

    @Test
    fun savePerformance_withUploadedVideo_persistsCustomVideoPathAndHasVideo() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(10)
        val artistId = "video_creator_$runId"
        val perfId = "video_perf_$runId"
        val videoPath = "/data/user/0/com.aistudio.versepulse.kxmyp/files/uploads/stage_video_$runId.mp4"

        signInTestUser("creator_video_$runId@versepulse.test")

        val videoPerf = Performance(
            id = perfId,
            title = "Live Rooftop Session",
            artistId = artistId,
            artistName = "Jordan Rivers",
            avatarInitials = "JR",
            category = PerformanceCategory.MUSIC,
            genre = "Acoustic Soul",
            durationSec = 145,
            lyricsOrScript = "Original stage video performance.",
            visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
            audioType = SynthAudioType.UPLOADED_VIDEO,
            customVideoPath = videoPath,
            viewsCount = 1,
            likesCount = 0,
            votesCount = 0,
            cheersCount = 0,
            isUserCreated = true
        )

        val saveRes = withTimeout(5000L) {
            service.savePerformance(videoPerf)
        }
        assertTrue("Expected video performance save to succeed: ${saveRes.exceptionOrNull()}", saveRes.isSuccess)

        val fetched = withTimeout(5000L) {
            service.getPerformance(perfId)
        }.getOrNull()

        assertNotNull(fetched)
        assertEquals(SynthAudioType.UPLOADED_VIDEO, fetched!!.audioType)
        assertEquals(videoPath, fetched.customVideoPath)
        assertTrue("Expected hasVideo to be true for uploaded video performance", fetched.hasVideo)
    }
}
