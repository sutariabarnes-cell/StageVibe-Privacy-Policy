package com.example

import com.example.data.PerformanceRepository
import com.example.model.LeaderboardCategoryFilter
import com.example.model.LeaderboardTimePeriod
import com.example.model.PerformanceCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExampleUnitTest {

    private lateinit var repository: PerformanceRepository

    @Before
    fun setup() {
        repository = PerformanceRepository()
    }

    @Test
    fun testLeaderboardRankingComputation() {
        val topWeekly = repository.computeLeaderboard(
            categoryFilter = LeaderboardCategoryFilter.ALL,
            timePeriod = LeaderboardTimePeriod.WEEKLY
        )

        assertTrue("Leaderboard should contain creators", topWeekly.isNotEmpty())
        assertTrue("Leaderboard should not exceed top 10", topWeekly.size <= 10)

        // Verify sorted descending by totalScore
        for (i in 0 until topWeekly.size - 1) {
            assertTrue(
                "Rankings should be sorted descending by score",
                topWeekly[i].totalScore >= topWeekly[i + 1].totalScore
            )
        }
        assertEquals("Top creator should be rank 1", 1, topWeekly[0].rank)
    }

    @Test
    fun testLeaderboardCategoryFiltering() {
        val musicOnly = repository.computeLeaderboard(
            categoryFilter = LeaderboardCategoryFilter.MUSIC,
            timePeriod = LeaderboardTimePeriod.WEEKLY
        )
        assertTrue(musicOnly.all { it.category == PerformanceCategory.MUSIC })

        val spokenOnly = repository.computeLeaderboard(
            categoryFilter = LeaderboardCategoryFilter.SPOKEN_WORD,
            timePeriod = LeaderboardTimePeriod.WEEKLY
        )
        assertTrue(spokenOnly.all { it.category == PerformanceCategory.SPOKEN_WORD })
    }

    @Test
    fun testVotingInPoll() {
        val initialPoll = repository.polls.value.first()
        val candidate = initialPoll.candidates.first()
        val initialVotes = candidate.votesCount

        repository.voteInPoll(initialPoll.id, candidate.candidateId)

        val updatedPoll = repository.getPollById(initialPoll.id)
        assertNotNull(updatedPoll)
        val updatedCandidate = updatedPoll!!.candidates.find { it.candidateId == candidate.candidateId }
        assertNotNull(updatedCandidate)
        assertEquals(initialVotes + 1, updatedCandidate!!.votesCount)
        assertEquals(candidate.candidateId, updatedPoll.userVotedCandidateId)
    }

    @Test
    fun testCreatorProfileUpdate() {
        repository.updateCurrentUserProfile(
            artistName = "Echo Poet",
            stageTitle = "National Slam Champion",
            bio = "Crafting verse from city whispers.",
            primaryCategory = PerformanceCategory.SPOKEN_WORD,
            socialSpotify = "@spotify/echopoet",
            socialInstagram = "@echopoet",
            socialYoutube = "youtube.com/@echopoet",
            socialWebsite = "https://echopoet.art"
        )

        val profile = repository.getCreatorProfile(repository.currentUserId)
        assertEquals("Echo Poet", profile.artistName)
        assertEquals("National Slam Champion", profile.stageTitle)
        assertEquals("Crafting verse from city whispers.", profile.bio)
        assertEquals(PerformanceCategory.SPOKEN_WORD, profile.primaryCategory)
        assertEquals("@echopoet", profile.socialInstagram)
    }

    @Test
    fun testAudienceRatingCalculation() {
        val creatorId = "creator_1"
        val initialProfile = repository.getCreatorProfile(creatorId)
        val initialCount = initialProfile.totalAudienceRatingsCount

        repository.rateCreator(creatorId, 5.0f)

        val updatedProfile = repository.getCreatorProfile(creatorId)
        assertEquals(initialCount + 1, updatedProfile.totalAudienceRatingsCount)
        assertTrue(updatedProfile.audienceRating in 1.0f..5.0f)
    }

    @Test
    fun testUploadedAudioInfoFormatting() {
        val info = com.example.model.UploadedAudioInfo(
            localFilePath = "/data/user/0/com.example/files/uploads/track_1.mp3",
            fileName = "my_original_song.mp3",
            detectedTitle = "Midnight Echoes",
            detectedArtist = "Stage Artist",
            detectedGenre = "Neo-Soul",
            durationSec = 215, // 3 mins 35 secs
            fileSizeBytes = 4500000L // ~4.3 MB
        )

        assertEquals("3:35", info.formattedDuration)
        assertTrue(info.formattedFileSize.contains("MB"))
    }

    @Test
    fun testPublishUploadedPerformance() {
        val uploadedPath = "/data/user/0/com.example/files/uploads/test_track.mp3"
        val published = repository.publishPerformance(
            title = "Midnight Echoes",
            category = PerformanceCategory.MUSIC,
            genre = "Neo-Soul",
            lyricsOrScript = "Midnight falls softly across the stage...",
            visualizerTheme = com.example.model.VisualizerTheme.NEON_WAVES,
            audioType = com.example.model.SynthAudioType.UPLOADED_MUSIC,
            customAudioPath = uploadedPath,
            enrollInPollId = null,
            durationSec = 215
        )

        assertNotNull(published)
        assertEquals("Midnight Echoes", published.title)
        assertEquals(com.example.model.SynthAudioType.UPLOADED_MUSIC, published.audioType)
        assertEquals(uploadedPath, published.customAudioPath)
        assertEquals(215, published.durationSec)
        assertTrue(published.isUserCreated)

        val retrieved = repository.getPerformanceById(published.id)
        assertNotNull(retrieved)
        assertEquals(published.id, retrieved!!.id)
    }

    @Test
    fun testRealtimeAudienceVotingOnPerformance() {
        val initialPerf = repository.performances.value.first()
        val initialVotes = initialPerf.votesCount
        val initialIsVoted = initialPerf.isVotedByMe

        // Audience casts a real-time vote
        repository.voteForPerformance(initialPerf.id)

        val votedPerf = repository.getPerformanceById(initialPerf.id)
        assertNotNull(votedPerf)
        assertEquals(initialVotes + 1, votedPerf!!.votesCount)
        assertTrue(votedPerf.isVotedByMe)

        // Audience toggles/un-votes
        repository.voteForPerformance(initialPerf.id)
        val unvotedPerf = repository.getPerformanceById(initialPerf.id)
        assertNotNull(unvotedPerf)
        assertEquals(initialVotes, unvotedPerf!!.votesCount)
        assertEquals(initialIsVoted, unvotedPerf.isVotedByMe)
    }

    @Test
    fun testSoundEffectManagerSynthesisAndToggles() {
        val soundManager = com.example.audio.SoundEffectManager()
        assertTrue("Sound should be enabled by default", soundManager.isSoundEnabled)

        // Test playing sounds without exceptions
        soundManager.playVoteSound()
        soundManager.playCheerSound()
        soundManager.playLikeSound()
        soundManager.playPollVoteSound()
        soundManager.playCueBeep()

        // Test mute toggle
        soundManager.isSoundEnabled = false
        assertEquals(false, soundManager.isSoundEnabled)
        soundManager.playVoteSound()
    }

    @Test
    fun testLiveVisualizerModesAndThemeMapping() {
        val modes = com.example.ui.components.LiveVisualizerMode.values()
        assertEquals(5, modes.size)
        assertTrue(modes.contains(com.example.ui.components.LiveVisualizerMode.SPECTRUM_EQ))
        assertTrue(modes.contains(com.example.ui.components.LiveVisualizerMode.NEON_WAVES))
        assertTrue(modes.contains(com.example.ui.components.LiveVisualizerMode.PULSE_ORB))
        assertTrue(modes.contains(com.example.ui.components.LiveVisualizerMode.STAGE_LASERS))
        assertTrue(modes.contains(com.example.ui.components.LiveVisualizerMode.COSMIC_VORTEX))

        // Verify all modes have non-blank labels and descriptions
        for (mode in modes) {
            assertTrue(mode.label.isNotBlank())
            assertTrue(mode.description.isNotBlank())
        }
    }

    @Test
    fun testRealtimeSessionVotingDataLayer() {
        val targetPerf = repository.performances.value.first()
        val initialPerfVotes = targetPerf.votesCount
        val sessionId = "live_session_test_42"

        // Cast standard +1 vote
        repository.castSessionVote(
            sessionId = sessionId,
            performerId = targetPerf.id,
            performerName = targetPerf.artistName,
            voteDelta = 1,
            voteType = "VOTE"
        )

        var sessionMap = repository.sessionVotes.value[sessionId]
        assertNotNull(sessionMap)
        assertEquals(1, sessionMap!![targetPerf.id])

        // Cast +5 Super Vote
        repository.castSessionVote(
            sessionId = sessionId,
            performerId = targetPerf.id,
            performerName = targetPerf.artistName,
            voteDelta = 5,
            voteType = "SUPER_VOTE"
        )

        sessionMap = repository.sessionVotes.value[sessionId]
        assertEquals(6, sessionMap!![targetPerf.id])

        // Cast +10 Standing Ovation
        repository.castSessionVote(
            sessionId = sessionId,
            performerId = targetPerf.id,
            performerName = targetPerf.artistName,
            voteDelta = 10,
            voteType = "STANDING_OVATION"
        )

        sessionMap = repository.sessionVotes.value[sessionId]
        assertEquals(16, sessionMap!![targetPerf.id])

        // Check that performance votesCount also incremented
        val updatedPerf = repository.getPerformanceById(targetPerf.id)
        assertNotNull(updatedPerf)
        assertEquals(initialPerfVotes + 16, updatedPerf!!.votesCount)
    }

    @Test
    fun testCreatorSessionStatsAndProfileData() {
        val creatorId = "creator_1"
        val profile = repository.getCreatorProfile(creatorId)
        assertNotNull(profile)
        assertEquals("Maya Vance", profile.artistName)
        assertTrue("Bio should be non-empty", profile.bio.isNotBlank())
        assertTrue("Avatar color should be non-zero", profile.avatarColorHex != 0L)

        val stats = repository.getCreatorSessionStats(creatorId)
        assertNotNull(stats)
        assertTrue("Total sessions should be at least 1", stats.totalSessions >= 1)
        assertTrue("Total spectators should be greater than 0", stats.totalSpectatorsReached > 0)
        assertTrue("Peak viewers should be greater than 0", stats.peakViewers > 0)
        assertTrue("Past sessions should be listed", stats.pastSessions.isNotEmpty())
        assertEquals(stats.totalSessions, stats.pastSessions.size)
    }
}
