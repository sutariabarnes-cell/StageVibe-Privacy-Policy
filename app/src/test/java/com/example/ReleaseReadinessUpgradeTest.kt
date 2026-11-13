package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.PerformanceRepository
import com.example.data.local.OfflinePlaybackCacheRepository
import com.example.data.local.OfflineTrackCacheDatabase
import com.example.model.Performance
import com.example.model.PerformanceAnalyticsEngine
import com.example.model.PerformanceCategory
import com.example.model.StageReactionType
import com.example.model.SynthAudioType
import com.example.model.VisualizerTheme
import com.example.security.AppCheckRolloutStage
import com.example.security.AppCheckTelemetry
import com.example.security.UploadEncryptionManager
import com.example.ui.components.PlayTesterEmailValidator
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReleaseReadinessUpgradeTest {

    private lateinit var context: Context
    private lateinit var inMemoryCacheDb: OfflineTrackCacheDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        inMemoryCacheDb = Room.inMemoryDatabaseBuilder(
            context,
            OfflineTrackCacheDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        inMemoryCacheDb.close()
    }

    @Test
    fun `play tester email validator accepts Google accounts and flags consumer webmail`() {
        val rawInput = """
            creator.one@gmail.com, ARTIST.TWO@GOOGLEMAIL.COM
            studio@versepulse.io
            creator.one@gmail.com
            fan@yahoo.com; listener@outlook.com
            not-an-email
        """.trimIndent()

        val result = PlayTesterEmailValidator.validateTesterInput(rawInput)

        assertEquals(
            listOf("creator.one@gmail.com", "artist.two@googlemail.com", "studio@versepulse.io"),
            result.validEmails
        )
        assertEquals(3, result.invalidEntries.size)
        assertTrue(result.invalidEntries.any { it.raw == "fan@yahoo.com" })
        assertTrue(result.invalidEntries.any { it.raw == "listener@outlook.com" })
        assertTrue(result.invalidEntries.any { it.raw == "not-an-email" })
        assertEquals(
            "creator.one@gmail.com, artist.two@googlemail.com, studio@versepulse.io",
            result.formattedCsv
        )
    }

    @Test
    fun `offline playback cache pins encrypted vpenc track and clears cleanly`() = runBlocking {
        val repo = OfflinePlaybackCacheRepository(context, databaseOverride = inMemoryCacheDb)
        val perf = Performance(
            id = "perf_offline_test_1",
            title = "Neon Midnight Cypher",
            artistId = "creator_1",
            artistName = "Lyric Nova",
            avatarInitials = "LN",
            category = PerformanceCategory.SPOKEN_WORD,
            genre = "Spoken Word",
            durationSec = 145,
            lyricsOrScript = "Sparks over midnight frequencies, stage lights rising.",
            visualizerTheme = VisualizerTheme.NEON_WAVES,
            audioType = SynthAudioType.POETRY_AMBIENCE,
            viewsCount = 120,
            likesCount = 18,
            votesCount = 30,
            cheersCount = 12
        )

        val isNowPinned = repo.togglePinPerformance(perf)
        assertTrue(isNowPinned)

        val cached = repo.getCachedTrack(perf.id)
        assertNotNull(cached)
        assertEquals("Neon Midnight Cypher", cached?.title)
        assertTrue((cached?.cachedBytes ?: 0L) > 0L)

        val observedList = repo.observeCachedTracks().first()
        assertEquals(1, observedList.size)

        // Verify manifest envelope on disk uses VPENC1 encryption
        val cacheDir = File(context.filesDir, "offline_cache")
        val vpencFiles = cacheDir.listFiles()?.filter {
            it.name.endsWith(UploadEncryptionManager.ENCRYPTED_FILE_EXTENSION)
        }.orEmpty()
        assertTrue(vpencFiles.isNotEmpty())
        assertTrue(UploadEncryptionManager.isEncryptedFile(vpencFiles.first()))

        // Unpin via toggle
        val afterToggle = repo.togglePinPerformance(perf)
        assertFalse(afterToggle)
        assertNull(repo.getCachedTrack(perf.id))

        // Re-pin and clear all
        repo.pinPerformance(perf)
        assertNotNull(repo.getCachedTrack(perf.id))
        repo.clearAllCache()
        assertTrue(repo.observeCachedTracks().first().isEmpty())
    }

    @Test
    fun `analytics engine computes vote tier breakdown and momentum velocity`() {
        val samplePerf = Performance(
            id = "perf_analytics_1",
            title = "Crown Finale",
            artistId = "creator_me",
            artistName = "Aria Pulse",
            avatarInitials = "AP",
            category = PerformanceCategory.MUSIC,
            genre = "Hip-Hop",
            durationSec = 180,
            lyricsOrScript = "Rising through the bassline",
            visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
            audioType = SynthAudioType.HIPHOP_BEAT,
            viewsCount = 200,
            likesCount = 40,
            votesCount = 100,
            cheersCount = 25,
            createdAtTimestamp = 1_700_000_000_000L
        )

        val (stdVotes, superVotes, ovationVotes) =
            PerformanceAnalyticsEngine.computeVoteTierBreakdown(samplePerf)
        assertTrue(ovationVotes > 0)
        assertTrue(superVotes > 0)
        assertEquals(100, stdVotes + (superVotes * 5) + (ovationVotes * 10))

        val momentum = PerformanceAnalyticsEngine.computeMomentumPer100Views(samplePerf)
        assertTrue(momentum > 50f)

        val summary = PerformanceAnalyticsEngine.computeLastTenTrend(listOf(samplePerf))
        assertEquals(1, summary.points.size)
        assertEquals(stdVotes, summary.totalStandardVotesEst)
        assertEquals(superVotes, summary.totalSuperVotesEst)
        assertEquals(ovationVotes, summary.totalOvationVotesEst)
        assertEquals(momentum, summary.averageMomentumScore, 0.05f)
    }

    @Test
    fun `app check telemetry cycles phased rollout stages and verifies readiness`() {
        AppCheckTelemetry.setRolloutStage(context, AppCheckRolloutStage.CLIENT_ATTESTATION_ACTIVE)
        assertEquals(
            AppCheckRolloutStage.CLIENT_ATTESTATION_ACTIVE,
            AppCheckTelemetry.status.value.rolloutStage
        )

        val stage2 = AppCheckTelemetry.advanceRolloutStage(context)
        assertEquals(AppCheckRolloutStage.CLOUD_METRICS_MONITORING, stage2.rolloutStage)

        val stage3 = AppCheckTelemetry.advanceRolloutStage(context)
        assertEquals(AppCheckRolloutStage.STRICT_ENFORCEMENT_READY, stage3.rolloutStage)

        val wrapped = AppCheckTelemetry.advanceRolloutStage(context)
        assertEquals(AppCheckRolloutStage.CLIENT_ATTESTATION_ACTIVE, wrapped.rolloutStage)

        val snapshot = AppCheckTelemetry.verifyAttestationReadiness(context)
        assertTrue(snapshot.lastVerifiedAtMillis > 0L)
        assertTrue(snapshot.siteKeyValid)
    }

    @Test
    fun `performance repository enforces live stage moderation and crowd reactions`() {
        val repo = PerformanceRepository(context = null)
        val hostSession = repo.startLiveSession(
            title = "Midnight Rooftop Set",
            category = PerformanceCategory.MUSIC,
            genre = "Jazz",
            visualizerTheme = VisualizerTheme.COSMIC_VINYL,
            audioType = SynthAudioType.SYNTH_CHILL,
            pollId = null
        )

        // Pin announcement & enable slow mode + chat lock
        repo.updateLiveModeration(
            sessionId = hostSession.id,
            slowModeSeconds = 5,
            isChatLocked = true,
            pinnedAnnouncement = "Drop 🔥 for an acoustic encore!"
        )

        val moderated = repo.liveSessions.value.first { it.id == hostSession.id }
        assertEquals(5, moderated.slowModeSeconds)
        assertTrue(moderated.isChatLocked)
        assertEquals("Drop 🔥 for an acoustic encore!", moderated.pinnedAnnouncement)

        // Host can still post while chat is locked
        val hostSent = repo.sendLiveChatMessage(
            sessionId = hostSession.id,
            senderName = "Stage Host",
            text = "Host check-in!",
            isCheer = false
        )
        assertTrue(hostSent)

        // Hide the newly added message
        val latestMsgId = repo.liveSessions.value.first { it.id == hostSession.id }
            .chatMessages.last().id
        repo.toggleHideLiveChatMessage(hostSession.id, latestMsgId, hidden = true)
        val hiddenMsg = repo.liveSessions.value.first { it.id == hostSession.id }
            .chatMessages.first { it.id == latestMsgId }
        assertTrue(hiddenMsg.isHiddenByHost)

        // Trigger crowd reactions
        repo.sendLiveReaction(hostSession.id, StageReactionType.FIRE, delta = 1)
        repo.sendLiveReaction(hostSession.id, StageReactionType.OVATION, delta = 2)

        val afterReactions = repo.liveSessions.value.first { it.id == hostSession.id }
        assertTrue((afterReactions.reactionCounts[StageReactionType.FIRE] ?: 0) >= 1)
        assertTrue((afterReactions.reactionCounts[StageReactionType.OVATION] ?: 0) >= 2)
        assertTrue(afterReactions.totalReactionsCount >= 3)
    }
}
