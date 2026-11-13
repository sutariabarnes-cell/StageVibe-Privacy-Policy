package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.auth.UserAuthManager
import com.example.base.FirestoreEmulatorTestBase
import com.example.model.AuthSessionState
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.RegistrationDraft
import com.example.model.SynthAudioType
import com.example.model.UserRole
import com.example.model.VisualizerTheme
import com.example.model.VoteTier
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class UserAccountRuleTest : FirestoreEmulatorTestBase() {

    private lateinit var service: FirestoreService
    private lateinit var testScope: CoroutineScope
    private lateinit var authManager: UserAuthManager

    @Before
    fun setUpAuthAndService() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        service = FirestoreService(firestore, auth)
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        authManager = UserAuthManager(
            context = context,
            firestoreService = service,
            auth = auth,
            scope = testScope
        )
    }

    @After
    fun tearDownScope() {
        authManager.dispose()
        testScope.cancel()
    }

    @Test
    fun registerPerformer_createsUserAccountAndSyncsPublicPerformerProfile() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(10)
        val aliceUid = signInTestUser("alice_performer_$runId@versepulse.test")

        val draft = RegistrationDraft(
            role = UserRole.CREATOR,
            stageName = "Aria Pulse",
            stageTitle = "Synthwave Vocalist",
            bio = "Live electronic soul on stage.",
            primaryCategory = PerformanceCategory.MUSIC
        )

        val completed = kotlinx.coroutines.CompletableDeferred<com.example.model.UserAccount>()
        authManager.completeRegistration(
            draft = draft,
            onSuccess = { completed.complete(it) },
            onError = { msg -> completed.completeExceptionally(AssertionError("Registration failed: $msg")) }
        )

        val registeredAccount = withTimeout(5000L) { completed.await() }
        assertEquals(UserRole.CREATOR, registeredAccount.role)
        assertEquals("Aria Pulse", registeredAccount.stageName)
        assertEquals("Synthwave Vocalist", registeredAccount.stageTitle)

        val authenticatedState = withTimeout(5000L) {
            authManager.sessionState
                .first { it is AuthSessionState.Authenticated && (it as AuthSessionState.Authenticated).account.uid == FirestoreService.sanitizeId(aliceUid) }
                as AuthSessionState.Authenticated
        }
        assertEquals(UserRole.CREATOR, authenticatedState.account.role)

        // Verify /users/{aliceUid} persisted in Firestore
        val storedAccount = withTimeout(5000L) { service.getUserAccount(aliceUid) }.getOrNull()
        assertNotNull(storedAccount)
        assertEquals(UserRole.CREATOR, storedAccount!!.role)

        // Verify /performers/{aliceUid} was automatically provisioned for the Performer
        val storedPerformer = withTimeout(5000L) { service.getPerformer(aliceUid) }.getOrNull()
        assertNotNull(storedPerformer)
        assertEquals("Aria Pulse", storedPerformer!!.name)
        assertEquals(PerformanceCategory.MUSIC, storedPerformer.category)
    }

    @Test
    fun registerSpectator_and_upgradeRoleToPerformer_persistsRoleAndProvisionsPerformer() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(10)
        val bobUid = signInTestUser("bob_spectator_$runId@versepulse.test")

        authManager.completeRegistration(
            RegistrationDraft(
                role = UserRole.SPECTATOR,
                stageName = "BobStageFan",
                primaryCategory = PerformanceCategory.SPOKEN_WORD
            )
        )

        val spectatorState = withTimeout(5000L) {
            authManager.sessionState
                .first { it is AuthSessionState.Authenticated && (it as AuthSessionState.Authenticated).account.role == UserRole.SPECTATOR }
                as AuthSessionState.Authenticated
        }
        assertEquals("BobStageFan", spectatorState.account.stageName)
        assertEquals(UserRole.SPECTATOR, spectatorState.account.role)

        // Upgrade Spectator to Performer (CREATOR)
        authManager.updateAccountRole(
            newRole = UserRole.CREATOR,
            stageName = "Bob Verse",
            stageTitle = "Spoken Word Poet",
            bio = "From audience to microphone.",
            category = PerformanceCategory.SPOKEN_WORD
        )

        val upgradedAccount = withTimeout(5000L) {
            service.observeUserAccount(bobUid).first { it?.role == UserRole.CREATOR }
        }
        assertNotNull(upgradedAccount)
        assertEquals(UserRole.CREATOR, upgradedAccount!!.role)
        assertEquals("Bob Verse", upgradedAccount.stageName)

        val provisionedPerformer = withTimeout(5000L) {
            service.observePerformer(bobUid).first { it != null }
        }
        assertNotNull(provisionedPerformer)
        assertEquals("Bob Verse", provisionedPerformer!!.name)
    }

    @Test
    fun crossUserIsolation_and_unauthenticatedRejection_onUsersCollection() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(10)
        val aliceUid = signInTestUser("alice_priv_$runId@versepulse.test")

        authManager.completeRegistration(
            RegistrationDraft(
                role = UserRole.SPECTATOR,
                stageName = "AlicePrivateFan",
                primaryCategory = PerformanceCategory.MUSIC
            )
        )

        withTimeout(5000L) {
            service.observeUserAccount(aliceUid).first { it != null }
        }

        // Bob signs in and tries to read Alice's /users/{aliceUid} account -> PERMISSION_DENIED
        signInTestUser("bob_intruder_$runId@versepulse.test")
        val bobReadAlice = withTimeout(5000L) { service.getUserAccount(aliceUid) }
        assertTrue("Expected cross-user read on /users/{aliceUid} to fail", bobReadAlice.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            bobReadAlice.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Unauthenticated caller tries to read /users/{aliceUid} -> PERMISSION_DENIED
        auth.signOut()
        val unauthRead = withTimeout(5000L) { service.getUserAccount(aliceUid) }
        assertTrue("Expected unauthenticated read on /users/{aliceUid} to fail", unauthRead.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            unauthRead.exceptionOrNull()?.unwrapFirestoreException()?.code
        )
    }

    @Test
    fun deleteUserAccount_removesCreatorAndSpectatorDocs_andRejectsCrossUserDelete() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(10)
        val aliceEmail = "alice_delete_$runId@versepulse.test"
        val aliceUid = signInTestUser(aliceEmail)

        val regDeferred = kotlinx.coroutines.CompletableDeferred<com.example.model.UserAccount>()
        authManager.completeRegistration(
            draft = RegistrationDraft(
                role = UserRole.CREATOR,
                stageName = "Alice DeleteTest",
                primaryCategory = PerformanceCategory.MUSIC
            ),
            onSuccess = { regDeferred.complete(it) },
            onError = { msg -> regDeferred.completeExceptionally(AssertionError("Reg failed: $msg")) }
        )
        withTimeout(5000L) { regDeferred.await() }

        // Confirm both /users/{aliceUid} and /performers/{aliceUid} exist
        assertNotNull(withTimeout(5000L) { service.getUserAccount(aliceUid) }.getOrNull())
        assertNotNull(withTimeout(5000L) { service.getPerformer(aliceUid) }.getOrNull())

        // Cross-user deletion attempt by Bob -> PERMISSION_DENIED
        val bobEmail = "bob_spectator_del_$runId@versepulse.test"
        val bobUid = signInTestUser(bobEmail)
        val crossDelete = withTimeout(5000L) { service.deleteUserAccount(aliceUid) }
        assertTrue("Expected cross-user deleteUserAccount to fail", crossDelete.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            crossDelete.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        // Bob registers as Spectator (no /performers/{bobUid} doc) and deletes own account cleanly
        val bobReg = kotlinx.coroutines.CompletableDeferred<com.example.model.UserAccount>()
        authManager.completeRegistration(
            draft = RegistrationDraft(
                role = UserRole.SPECTATOR,
                stageName = "Bob Spectator Del",
                primaryCategory = PerformanceCategory.SPOKEN_WORD
            ),
            onSuccess = { bobReg.complete(it) },
            onError = { msg -> bobReg.completeExceptionally(AssertionError("Bob reg failed: $msg")) }
        )
        withTimeout(5000L) { bobReg.await() }
        assertNotNull(withTimeout(5000L) { service.getUserAccount(bobUid) }.getOrNull())

        val bobDelete = withTimeout(5000L) { service.deleteUserAccount(bobUid) }
        assertTrue(
            "Spectator owner should be able to delete own account: ${bobDelete.exceptionOrNull()}",
            bobDelete.isSuccess
        )
        org.junit.Assert.assertNull(withTimeout(5000L) { service.getUserAccount(bobUid) }.getOrNull())

        // Alice signs back in and deletes her Creator Firebase Auth + Firestore account cleanly
        signInTestUser(aliceEmail)
        val aliceDeleteDone = kotlinx.coroutines.CompletableDeferred<Unit>()
        authManager.deleteAccount(
            onSuccess = { aliceDeleteDone.complete(Unit) },
            onError = { msg -> aliceDeleteDone.completeExceptionally(AssertionError("Alice delete failed: $msg")) }
        )
        withTimeout(5000L) { aliceDeleteDone.await() }

        assertTrue(authManager.sessionState.value is AuthSessionState.Unauthenticated)
        // Verify Alice's /performers/{aliceUid} public doc was removed (readable when signed back in)
        signInTestUser(bobEmail)
        val deletedPerformer = withTimeout(5000L) { service.getPerformer(aliceUid) }.getOrNull()
        assertNull("Expected Alice performer profile to be deleted", deletedPerformer)
    }

    @Test
    fun deleteUserAccount_removesAllAssociatedFirestoreUserContentWhilePreservingOtherUsers() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(10)
        val aliceEmail = "alice_full_cleanup_$runId@versepulse.test"
        val bobEmail = "bob_preserve_$runId@versepulse.test"

        // 1. Bob creates a stage and performance that MUST remain untouched
        val bobUid = signInTestUser(bobEmail)
        val bobReg = kotlinx.coroutines.CompletableDeferred<com.example.model.UserAccount>()
        authManager.completeRegistration(
            draft = RegistrationDraft(
                role = UserRole.CREATOR,
                stageName = "Bob Keeper",
                primaryCategory = PerformanceCategory.MUSIC
            ),
            onSuccess = { bobReg.complete(it) },
            onError = { msg -> bobReg.completeExceptionally(AssertionError(msg)) }
        )
        withTimeout(5000L) { bobReg.await() }

        val stageService = LiveStageVotingService(firestore, auth)
        val bobStageId = "stage_bob_$runId"
        val bobStageRes = withTimeout(5000L) {
            stageService.createStage(
                title = "Bob Live Stage",
                category = PerformanceCategory.MUSIC,
                stageId = bobStageId
            )
        }
        assertTrue(bobStageRes.isSuccess)
        val bobPerfId = "perf_bob_$runId"
        withTimeout(5000L) {
            service.savePerformance(
                Performance(
                    id = bobPerfId,
                    title = "Bob Original",
                    artistId = bobUid,
                    artistName = "Bob Keeper",
                    avatarInitials = "BK",
                    category = PerformanceCategory.MUSIC,
                    genre = "Jazz",
                    durationSec = 180,
                    lyricsOrScript = "Keep me safe",
                    visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                    audioType = SynthAudioType.ACOUSTIC_SOUL,
                    viewsCount = 10,
                    likesCount = 2,
                    votesCount = 1,
                    cheersCount = 1
                )
            )
        }

        // 2. Alice registers as Creator and populates content across every user-owned collection
        val aliceUid = signInTestUser(aliceEmail)
        val aliceReg = kotlinx.coroutines.CompletableDeferred<com.example.model.UserAccount>()
        authManager.completeRegistration(
            draft = RegistrationDraft(
                role = UserRole.CREATOR,
                stageName = "Alice Everything",
                primaryCategory = PerformanceCategory.MUSIC
            ),
            onSuccess = { aliceReg.complete(it) },
            onError = { msg -> aliceReg.completeExceptionally(AssertionError(msg)) }
        )
        withTimeout(5000L) { aliceReg.await() }

        val alicePerfId = "perf_alice_$runId"
        val aliceProfileId = "creator_alice_$runId"
        val aliceSessionId = "session_alice_$runId"
        val aliceEventId = "event_alice_$runId"
        val aliceStageId = "stage_alice_$runId"
        val aliceVoteId = "vote_alice_$runId"
        val alicePollVoteId = "pollvote_alice_$runId"
        val aliceSessionVoteId = "sessvote_alice_$runId"

        withTimeout(5000L) {
            service.savePerformance(
                Performance(
                    id = alicePerfId,
                    title = "Alice Track",
                    artistId = aliceUid,
                    artistName = "Alice Everything",
                    avatarInitials = "AE",
                    category = PerformanceCategory.MUSIC,
                    genre = "Hip-Hop",
                    durationSec = 165,
                    lyricsOrScript = "My original bars",
                    visualizerTheme = VisualizerTheme.COSMIC_VINYL,
                    audioType = SynthAudioType.HIPHOP_BEAT,
                    viewsCount = 15,
                    likesCount = 3,
                    votesCount = 2,
                    cheersCount = 1
                )
            ).getOrThrow()

            firestore.collection(FirestoreService.COLLECTION_CREATOR_PROFILES)
                .document(aliceProfileId)
                .set(
                    mapOf(
                        "creatorId" to aliceProfileId,
                        "ownerUid" to aliceUid,
                        "artistName" to "Alice Everything",
                        "bio" to "Artist bio",
                        "visibility" to "PUBLIC",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            firestore.collection(FirestoreService.COLLECTION_LIVE_SESSIONS)
                .document(aliceSessionId)
                .set(
                    mapOf(
                        "id" to aliceSessionId,
                        "creatorId" to aliceUid,
                        "ownerUid" to aliceUid,
                        "title" to "Alice Broadcast",
                        "isLive" to true,
                        "visibility" to "PUBLIC",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            val eventsRepo = LiveEventsRepository(firestore, auth)
            eventsRepo.createLiveEvent(
                title = "Alice Showcase",
                category = PerformanceCategory.MUSIC,
                eventId = aliceEventId
            ).getOrThrow()

            stageService.createStage(
                title = "Alice Main Stage",
                category = PerformanceCategory.MUSIC,
                stageId = aliceStageId
            ).getOrThrow()

            // Alice casts vote on Bob's stage, plus leaderboard vote, poll vote, and session vote
            stageService.castSpectatorVote(
                stageId = bobStageId,
                performerId = bobUid,
                performerName = "Bob Keeper",
                tier = VoteTier.SUPER_VOTE
            ).getOrThrow()

            service.castVote(
                performerId = bobUid,
                performerName = "Bob Keeper",
                category = PerformanceCategory.MUSIC,
                voteDelta = 5,
                voteTier = "SUPER_VOTE",
                voteId = aliceVoteId
            ).getOrThrow()

            firestore.collection(FirestoreService.COLLECTION_POLL_VOTES)
                .document(alicePollVoteId)
                .set(
                    mapOf(
                        "pollId" to "poll_weekly_showcase",
                        "candidateId" to "cand_1",
                        "userId" to aliceUid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()

            firestore.collection(FirestoreService.COLLECTION_SESSION_VOTES)
                .document(aliceSessionVoteId)
                .set(
                    mapOf(
                        "voteId" to aliceSessionVoteId,
                        "sessionId" to aliceSessionId,
                        "performerId" to aliceUid,
                        "performerName" to "Alice Everything",
                        "userId" to aliceUid,
                        "voteDelta" to 5,
                        "voteType" to "SUPER_VOTE",
                        "status" to "COUNTED",
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        }

        // Bob also casts a spectator vote inside Alice's stage to prove subcollection cleanup works cleanly
        signInTestUser(bobEmail)
        withTimeout(5000L) {
            stageService.castSpectatorVote(
                stageId = aliceStageId,
                performerId = aliceUid,
                performerName = "Alice Everything",
                tier = VoteTier.STANDARD
            ).getOrThrow()
        }

        // 3. Alice signs back in and establishes account deletion
        signInTestUser(aliceEmail)
        val aliceDeleteDone = kotlinx.coroutines.CompletableDeferred<Unit>()
        authManager.deleteAccount(
            onSuccess = { aliceDeleteDone.complete(Unit) },
            onError = { msg -> aliceDeleteDone.completeExceptionally(AssertionError("Delete failed: $msg")) }
        )
        withTimeout(10_000L) { aliceDeleteDone.await() }
        assertTrue(authManager.sessionState.value is AuthSessionState.Unauthenticated)

        // 4. Sign in as Bob and assert EVERY Alice doc is deleted while Bob's content remains
        signInTestUser(bobEmail)
        withTimeout(5000L) {
            assertFalse(firestore.collection(FirestoreService.COLLECTION_PERFORMANCES).document(alicePerfId).get().await().exists())
            assertFalse(firestore.collection(FirestoreService.COLLECTION_CREATOR_PROFILES).document(aliceProfileId).get().await().exists())
            assertFalse(firestore.collection(FirestoreService.COLLECTION_LIVE_SESSIONS).document(aliceSessionId).get().await().exists())
            assertFalse(firestore.collection(FirestoreService.COLLECTION_EVENTS).document(aliceEventId).get().await().exists())
            assertFalse(firestore.collection(FirestoreService.COLLECTION_STAGES).document(aliceStageId).get().await().exists())
            assertTrue(
                firestore.collection(FirestoreService.COLLECTION_STAGES).document(aliceStageId)
                    .collection(FirestoreService.SUBCOLLECTION_VOTING_COUNTERS)
                    .whereEqualTo("visibility", "PUBLIC")
                    .get().await().isEmpty
            )
            assertTrue(
                firestore.collection(FirestoreService.COLLECTION_STAGES).document(aliceStageId)
                    .collection(FirestoreService.SUBCOLLECTION_SPECTATOR_VOTES)
                    .whereEqualTo("status", "COUNTED")
                    .get().await().isEmpty
            )
            assertTrue(
                firestore.collection(FirestoreService.COLLECTION_STAGES).document(bobStageId)
                    .collection(FirestoreService.SUBCOLLECTION_SPECTATOR_VOTES)
                    .whereEqualTo("status", "COUNTED")
                    .get().await().none { it.getString("voterUid") == aliceUid }
            )
            assertFalse(firestore.collection(FirestoreService.COLLECTION_VOTES).document(aliceVoteId).get().await().exists())
            assertFalse(firestore.collection(FirestoreService.COLLECTION_SESSION_VOTES).document(aliceSessionVoteId).get().await().exists())
            assertNull(service.getPerformer(aliceUid).getOrNull())

            // Bob's content is untouched
            assertTrue(firestore.collection(FirestoreService.COLLECTION_PERFORMANCES).document(bobPerfId).get().await().exists())
            assertTrue(firestore.collection(FirestoreService.COLLECTION_STAGES).document(bobStageId).get().await().exists())
            assertNotNull(service.getUserAccount(bobUid).getOrNull())
        }
    }

    @Test
    fun liveStageModeration_spectatorReactions_and_chatCleanup_enforcedByRules() = runBlocking {
        val runId = UUID.randomUUID().toString().replace("-", "").take(10)
        val hostEmail = "host_mod_$runId@versepulse.test"
        val specEmail = "spec_mod_$runId@versepulse.test"
        val sessionId = "live_mod_sess_$runId"

        // 1. Host creates a live session
        val hostUid = signInTestUser(hostEmail)
        val sessionRef = firestore.collection(FirestoreService.COLLECTION_LIVE_SESSIONS).document(sessionId)
        withTimeout(5000L) {
            sessionRef.set(
                mapOf(
                    "id" to sessionId,
                    "creatorId" to hostUid,
                    "ownerUid" to hostUid,
                    "creatorName" to "DJ Nova",
                    "title" to "Midnight Synth Cipher",
                    "category" to "MUSIC",
                    "isLive" to true,
                    "slowModeSeconds" to 0,
                    "isChatLocked" to false,
                    "fireCount" to 0,
                    "visibility" to "PUBLIC",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        // 2. Spectator signs in: can increment reaction within bounds (+1..+25), CANNOT lock chat, CAN post chat while unlocked
        val specUid = signInTestUser(specEmail)
        withTimeout(5000L) {
            sessionRef.update(
                mapOf(
                    "fireCount" to 3,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val specTryLock = runCatching {
            withTimeout(5000L) {
                sessionRef.update(
                    mapOf(
                        "isChatLocked" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        assertTrue("Spectator must not lock host chat", specTryLock.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            specTryLock.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        val msg1Id = "msg_spec_$runId"
        val msg1Ref = sessionRef.collection(FirestoreService.SUBCOLLECTION_CHAT_MESSAGES).document(msg1Id)
        withTimeout(5000L) {
            msg1Ref.set(
                mapOf(
                    "id" to msg1Id,
                    "sessionId" to sessionId,
                    "sessionOwnerUid" to hostUid,
                    "senderUid" to specUid,
                    "senderName" to "FanOne",
                    "text" to "Incredible bassline!",
                    "isCheer" to false,
                    "isHiddenByHost" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        // 3. Host signs in, hides spectator message, pins announcement, and locks chat
        signInTestUser(hostEmail)
        withTimeout(5000L) {
            msg1Ref.update(
                mapOf(
                    "isHiddenByHost" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            sessionRef.update(
                mapOf(
                    "slowModeSeconds" to 5,
                    "isChatLocked" to true,
                    "pinnedAnnouncement" to "Drop your +10 Ovations for the finale!",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        // 4. Spectator cannot post new chat while locked, and deleting spectator account removes their chat message
        signInTestUser(specEmail)
        val msgLockedRef = sessionRef.collection(FirestoreService.SUBCOLLECTION_CHAT_MESSAGES).document("msg_locked_$runId")
        val specPostWhileLocked = runCatching {
            withTimeout(5000L) {
                msgLockedRef.set(
                    mapOf(
                        "id" to "msg_locked_$runId",
                        "sessionId" to sessionId,
                        "sessionOwnerUid" to hostUid,
                        "senderUid" to specUid,
                        "senderName" to "FanOne",
                        "text" to "Trying while locked",
                        "isCheer" to false,
                        "isHiddenByHost" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
        }
        assertTrue("Spectator chat post must fail when isChatLocked == true", specPostWhileLocked.isFailure)
        assertEquals(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            specPostWhileLocked.exceptionOrNull()?.unwrapFirestoreException()?.code
        )

        val specCleanup = withTimeout(5000L) { service.deleteUserAccount(specUid) }
        assertTrue("Spectator deleteUserAccount should clean up chat_messages: ${specCleanup.exceptionOrNull()}", specCleanup.isSuccess)

        signInTestUser(hostEmail)
        val deletedMsgSnap = withTimeout(5000L) { msg1Ref.get().await() }
        assertFalse("Expected spectator chat message to be deleted on account deletion", deletedMsgSnap.exists())
    }
}
