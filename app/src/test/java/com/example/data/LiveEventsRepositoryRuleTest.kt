package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.PerformanceCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class LiveEventsRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createObserveAndJoinLiveMusicAndSpokenWordEvents_updatesParticipantCountsInRealtime() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(8)
        val aliceUid = signInTestUser("alice_events_$suffix@versepulse.stage")
        val repository = LiveEventsRepository(db = firestore, auth = auth)

        val musicEventId = "evt_music_$suffix"
        val spokenEventId = "evt_spoken_$suffix"

        val musicRes = repository.createLiveEvent(
            title = "Neon Acoustic Cipher",
            category = PerformanceCategory.MUSIC,
            genre = "Neo-Soul",
            description = "Live rooftop acoustic cipher",
            hostName = "Alice Ray",
            initialParticipants = 18,
            eventId = musicEventId
        )
        assertTrue("Expected Music live event creation to succeed: ${musicRes.exceptionOrNull()}", musicRes.isSuccess)

        val spokenRes = repository.createLiveEvent(
            title = "Midnight Verse Slam",
            category = PerformanceCategory.SPOKEN_WORD,
            genre = "Spoken Word",
            description = "Open-mic spoken word showcase",
            hostName = "Alice Ray",
            initialParticipants = 11,
            eventId = spokenEventId
        )
        assertTrue("Expected Spoken Word live event creation to succeed: ${spokenRes.exceptionOrNull()}", spokenRes.isSuccess)

        // Observe live events and verify both Music & Spoken Word events appear with titles and participant counts
        val liveEvents = withTimeout(5_000L) {
            repository.observeLiveEvents().first { list ->
                list.any { it.id == musicEventId } && list.any { it.id == spokenEventId }
            }
        }
        val fetchedMusic = liveEvents.first { it.id == musicEventId }
        val fetchedSpoken = liveEvents.first { it.id == spokenEventId }
        assertEquals("Neon Acoustic Cipher", fetchedMusic.title)
        assertEquals(18, fetchedMusic.currentParticipants)
        assertEquals(PerformanceCategory.MUSIC, fetchedMusic.performanceCategory)
        assertEquals("Midnight Verse Slam", fetchedSpoken.title)
        assertEquals(11, fetchedSpoken.currentParticipants)
        assertEquals(PerformanceCategory.SPOKEN_WORD, fetchedSpoken.performanceCategory)

        // Category filter returns only Spoken Word events matching spokenEventId
        val spokenOnly = withTimeout(5_000L) {
            repository.observeLiveEvents(categoryFilter = PerformanceCategory.SPOKEN_WORD).first { list ->
                list.any { it.id == spokenEventId }
            }
        }
        assertTrue(spokenOnly.all { it.performanceCategory == PerformanceCategory.SPOKEN_WORD })

        // Switch to Bob (another spectator) -> Bob joins Alice's live Music event, incrementing participantCount to 19
        signInTestUser("bob_events_$suffix@versepulse.stage")
        val joinRes = repository.joinLiveEvent(musicEventId)
        assertTrue("Expected Bob joining live event to succeed: ${joinRes.exceptionOrNull()}", joinRes.isSuccess)
        assertEquals(19L, joinRes.getOrThrow())

        // Cross-user isolation: Bob CANNOT end Alice's live event
        val unauthorizedEnd = repository.endLiveEvent(musicEventId)
        assertTrue("Expected cross-user endLiveEvent to be rejected by rules", unauthorizedEnd.isFailure)

        // Switch back to Alice -> Alice ends her live event, removing it from active isLive == true queries
        val reloginUid = signInTestUser("alice_events_$suffix@versepulse.stage")
        assertEquals(aliceUid, reloginUid)
        val ownerEnd = repository.endLiveEvent(musicEventId)
        assertTrue("Expected host endLiveEvent to succeed: ${ownerEnd.exceptionOrNull()}", ownerEnd.isSuccess)

        val remainingLive = withTimeout(5_000L) {
            repository.observeLiveEvents().first { list ->
                list.none { it.id == musicEventId } && list.any { it.id == spokenEventId }
            }
        }
        assertTrue(remainingLive.none { it.id == musicEventId })
    }

    @Test
    fun unauthenticatedCaller_isRejectedWhenCreatingOrObservingLiveEvents() = runBlocking {
        auth.signOut()
        val repository = LiveEventsRepository(db = firestore, auth = auth)

        val unauthCreate = repository.createLiveEvent(
            title = "Unauthorized Event",
            category = PerformanceCategory.MUSIC
        )
        assertTrue("Expected unauthenticated createLiveEvent to fail", unauthCreate.isFailure)

        val unauthFlowFailure = runCatching {
            withTimeout(5_000L) {
                repository.observeLiveEvents().first()
            }
        }
        assertTrue("Expected unauthenticated observeLiveEvents query to fail with PERMISSION_DENIED", unauthFlowFailure.isFailure)
    }
}
