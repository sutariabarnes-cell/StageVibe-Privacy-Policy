package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.VersePulseApp
import com.example.auth.StandaloneAccountStore
import com.example.auth.UserAuthManager
import com.example.model.AppNavDestination
import com.example.model.AuthAccountProvider
import com.example.model.AuthSessionState
import com.example.model.PerformanceCategory
import com.example.model.RegistrationDraft
import com.example.model.UserAccount
import com.example.model.UserRole
import com.example.ui.screens.AccountRoleSheetDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StandaloneAuthRegistrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var appContext: Context

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
        Firebase.auth.signOut()
        StandaloneAccountStore.clearAllAccounts(appContext)
    }

    @After
    fun tearDown() {
        StandaloneAccountStore.clearAllAccounts(appContext)
        Firebase.auth.signOut()
    }

    @Test
    fun standaloneAccountStore_registersAndSignsInBothCreatorAndSpectatorWithEncryptedVault() {
        val rawCreatorPassword = "CreatorStage#2026"
        val creatorResult = StandaloneAccountStore.registerAccount(
            context = appContext,
            identifier = "maya@versepulse.stage",
            password = rawCreatorPassword,
            draft = RegistrationDraft(
                role = UserRole.CREATOR,
                stageName = "Maya Vance",
                stageTitle = "Spoken Word Poet",
                bio = "Performing live poetry in the arena.",
                primaryCategory = PerformanceCategory.SPOKEN_WORD
            )
        )
        assertTrue(creatorResult.isSuccess)
        val creatorAccount = creatorResult.getOrThrow()
        assertEquals(UserRole.CREATOR, creatorAccount.role)
        assertEquals(AuthAccountProvider.STANDALONE, creatorAccount.authProvider)
        assertEquals("Maya Vance", creatorAccount.effectiveName)
        assertEquals(PerformanceCategory.SPOKEN_WORD, creatorAccount.primaryCategory)

        // Verify raw password is NEVER persisted anywhere in SharedPreferences
        val rawPrefs = appContext.getSharedPreferences(
            "versepulse_encrypted_settings_prefs",
            Context.MODE_PRIVATE
        )
        val serializedVault = rawPrefs.getString("standalone_arena_accounts_v1", "") ?: ""
        assertTrue("Vault record must use vp_enc1 AES-256-GCM envelope", serializedVault.startsWith("vp_enc1:"))
        assertFalse("Raw password must never appear in preferences", serializedVault.contains(rawCreatorPassword))

        // Register a standalone Spectator account as well
        val rawSpectatorPassword = "SpectatorPass!99"
        val spectatorResult = StandaloneAccountStore.registerAccount(
            context = appContext,
            identifier = "@arena_fan_99",
            password = rawSpectatorPassword,
            draft = RegistrationDraft(
                role = UserRole.SPECTATOR,
                stageName = "Arena Fan 99",
                primaryCategory = PerformanceCategory.MUSIC
            )
        )
        assertTrue(spectatorResult.isSuccess)
        val spectatorAccount = spectatorResult.getOrThrow()
        assertEquals(UserRole.SPECTATOR, spectatorAccount.role)
        assertEquals(AuthAccountProvider.STANDALONE, spectatorAccount.authProvider)

        // Duplicate registration for existing identifier is rejected cleanly
        val duplicateAttempt = StandaloneAccountStore.registerAccount(
            context = appContext,
            identifier = "MAYA@VERSEPULSE.STAGE",
            password = "AnotherPassword123",
            draft = RegistrationDraft(role = UserRole.CREATOR)
        )
        assertTrue(duplicateAttempt.isFailure)

        // Wrong password sign-in is rejected cleanly
        val wrongPassAttempt = StandaloneAccountStore.signIn(
            context = appContext,
            identifier = "maya@versepulse.stage",
            password = "WrongPassword999"
        )
        assertTrue(wrongPassAttempt.isFailure)

        // Sign back in as Creator -> restores Creator role
        StandaloneAccountStore.clearActiveSession(appContext)
        assertNull(StandaloneAccountStore.getActiveSessionAccount(appContext))

        val creatorSignIn = StandaloneAccountStore.signIn(
            context = appContext,
            identifier = "maya@versepulse.stage",
            password = rawCreatorPassword
        )
        assertTrue(creatorSignIn.isSuccess)
        assertEquals(UserRole.CREATOR, creatorSignIn.getOrThrow().role)
        assertEquals(creatorAccount.uid, StandaloneAccountStore.getActiveSessionAccount(appContext)?.uid)

        // Sign back in as Spectator -> restores Spectator role
        val spectatorSignIn = StandaloneAccountStore.signIn(
            context = appContext,
            identifier = "arena_fan_99",
            password = rawSpectatorPassword
        )
        assertTrue(spectatorSignIn.isSuccess)
        assertEquals(UserRole.SPECTATOR, spectatorSignIn.getOrThrow().role)
    }

    @Test
    fun authScreen_supportsStandaloneRegistrationAndSignInOptionsForBothCreatorsAndSpectators() {
        var capturedStandaloneRegDraft: RegistrationDraft? = null
        var capturedStandaloneRegId: String? = null
        var capturedStandaloneRegPass: String? = null

        var capturedSignInId: String? = null
        var capturedSignInPass: String? = null
        var capturedSignInRole: UserRole? = null

        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(
                    sessionState = AuthSessionState.Unauthenticated(),
                    isLoading = false,
                    errorMessage = null,
                    onDismissError = {},
                    onSignInWithGoogle = {},
                    onRegisterWithGoogle = {},
                    onCompleteRegistration = {},
                    onSignOut = {},
                    onSignInStandalone = { id, pass, role ->
                        capturedSignInId = id
                        capturedSignInPass = pass
                        capturedSignInRole = role
                    },
                    onRegisterStandalone = { draft, id, pass ->
                        capturedStandaloneRegDraft = draft
                        capturedStandaloneRegId = id
                        capturedStandaloneRegPass = pass
                    }
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Switch to Register tab and register a Standalone CREATOR account
        composeTestRule.onNodeWithTag("auth_tab_register").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("register_role_performer").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("register_stage_name_input").performScrollTo().performTextReplacement("Lyric Nova")
        composeTestRule.onNodeWithTag("register_standalone_identifier_input")
            .performScrollTo()
            .performTextReplacement("nova@versepulse.stage")
        composeTestRule.onNodeWithTag("register_standalone_password_input")
            .performScrollTo()
            .performTextReplacement("StageCreator123")

        composeTestRule.onNodeWithTag("standalone_register_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(UserRole.CREATOR, capturedStandaloneRegDraft?.role)
        assertEquals("Lyric Nova", capturedStandaloneRegDraft?.stageName)
        assertEquals("nova@versepulse.stage", capturedStandaloneRegId)
        assertEquals("StageCreator123", capturedStandaloneRegPass)

        // 2. Switch role to SPECTATOR in Register tab and register a Standalone SPECTATOR account
        composeTestRule.onNodeWithTag("register_role_spectator").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("register_stage_name_input").performScrollTo().performTextReplacement("FrontRowSam")
        composeTestRule.onNodeWithTag("register_standalone_identifier_input")
            .performScrollTo()
            .performTextReplacement("sam@spectator.stage")
        composeTestRule.onNodeWithTag("register_standalone_password_input")
            .performScrollTo()
            .performTextReplacement("SpectatorVote77")

        composeTestRule.onNodeWithTag("standalone_register_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(UserRole.SPECTATOR, capturedStandaloneRegDraft?.role)
        assertEquals("FrontRowSam", capturedStandaloneRegDraft?.stageName)
        assertEquals("sam@spectator.stage", capturedStandaloneRegId)
        assertEquals("SpectatorVote77", capturedStandaloneRegPass)

        // 3. Test inline Standalone Sign-In sub-mode directly inside Register card for Creators
        composeTestRule.onNodeWithTag("register_role_performer").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("register_standalone_mode_signin").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("register_inline_standalone_signin_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals("sam@spectator.stage", capturedSignInId)
        assertEquals("SpectatorVote77", capturedSignInPass)
        assertEquals(UserRole.CREATOR, capturedSignInRole)

        // 4. Switch to Sign-In tab and sign in standalone as Spectator
        composeTestRule.onNodeWithTag("auth_tab_signin").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("register_role_spectator").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("standalone_signin_identifier_input")
            .performScrollTo()
            .performTextReplacement("sam@spectator.stage")
        composeTestRule.onNodeWithTag("standalone_signin_password_input")
            .performScrollTo()
            .performTextReplacement("SpectatorVote77")
        composeTestRule.onNodeWithTag("standalone_signin_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals("sam@spectator.stage", capturedSignInId)
        assertEquals("SpectatorVote77", capturedSignInPass)
        assertEquals(UserRole.SPECTATOR, capturedSignInRole)
    }

    @Test
    fun userAuthManager_andAccountSheet_manageStandaloneCreatorAndSpectatorLifecycle() {
        val unconfined = UnconfinedTestDispatcher()
        val testScope = TestScope(unconfined)
        val manager = UserAuthManager(
            context = appContext,
            auth = Firebase.auth,
            scope = testScope,
            ioDispatcher = unconfined
        )

        try {
            var registeredCreator: UserAccount? = null
            manager.registerStandalone(
                draft = RegistrationDraft(
                    role = UserRole.CREATOR,
                    stageName = "Kai Rhythm",
                    primaryCategory = PerformanceCategory.MUSIC
                ),
                identifier = "kai@creator.stage",
                password = "KaiPassword2026",
                onSuccess = { registeredCreator = it }
            )

            assertNotNull(registeredCreator)
            assertTrue(manager.isAuthenticated)
            assertTrue(manager.isStandaloneSession)
            assertEquals(UserRole.CREATOR, manager.userAccount.value?.role)
            assertTrue(manager.sessionState.value is AuthSessionState.Authenticated)

            // Switch role from Creator to Spectator while in standalone session
            manager.updateAccountRole(newRole = UserRole.SPECTATOR)
            assertEquals(UserRole.SPECTATOR, manager.userAccount.value?.role)
            assertEquals(
                UserRole.SPECTATOR,
                StandaloneAccountStore.getActiveSessionAccount(appContext)?.role
            )

            // Sign out clears active standalone session
            manager.signOut()
            assertFalse(manager.isAuthenticated)
            assertTrue(manager.sessionState.value is AuthSessionState.Unauthenticated)
            assertNull(StandaloneAccountStore.getActiveSessionAccount(appContext))

            // Sign back in via standalone credentials
            manager.signInStandalone(
                identifier = "kai@creator.stage",
                password = "KaiPassword2026",
                explicitRole = UserRole.CREATOR
            )
            assertTrue(manager.isAuthenticated)
            assertEquals(UserRole.CREATOR, manager.userAccount.value?.role)

            // Verify AccountRoleSheetDialog renders Standalone Arena Account badge
            val activeStandalone = manager.userAccount.value!!
            composeTestRule.setContent {
                MyApplicationTheme {
                    AccountRoleSheetDialog(
                        account = activeStandalone,
                        emailFallback = activeStandalone.email,
                        displayNameFallback = activeStandalone.displayName,
                        currentRole = activeStandalone.role,
                        onSwitchRole = {},
                        onViewMyProfile = {},
                        onSignOut = {},
                        onDismiss = {},
                        onSwitchGoogleAccount = {}
                    )
                }
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("account_auth_provider_badge").assertIsDisplayed()
        } finally {
            manager.dispose()
        }
    }

    private fun awaitMainLooperCondition(
        timeoutMs: Long = 5_000L,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        shadowOf(Looper.getMainLooper()).idle()
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(15L)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertTrue("Condition was not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun mainViewModel_routesStandaloneCreatorToStudioAndSpectatorToDiscoverInApp() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        try {
            val viewModel = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory(application)
            )[MainViewModel::class.java]
            shadowOf(Looper.getMainLooper()).idle()

            composeTestRule.setContent {
                MyApplicationTheme {
                    VersePulseApp(viewModel = viewModel)
                }
            }
            composeTestRule.waitForIdle()

            // Initially unauthenticated -> AuthScreen displayed
            composeTestRule.onNodeWithTag("auth_screen_root").assertIsDisplayed()

            // 1. Register standalone Creator -> enters app and routes to Creator Studio
            viewModel.registerStandalone(
                draft = RegistrationDraft(
                    role = UserRole.CREATOR,
                    stageName = "Aria Verse",
                    primaryCategory = PerformanceCategory.SPOKEN_WORD
                ),
                identifier = "aria@versepulse.stage",
                password = "AriaCreatorPass88"
            )
            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Authenticated
            }
            composeTestRule.waitForIdle()

            assertTrue(viewModel.sessionState.value is AuthSessionState.Authenticated)
            assertEquals(UserRole.CREATOR, viewModel.userRole.value)
            assertEquals(AppNavDestination.STUDIO, viewModel.currentDestination.value)
            composeTestRule.onNodeWithTag("auth_screen_root").assertDoesNotExist()

            // 2. Sign out -> returns to AuthScreen
            viewModel.signOut()
            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Unauthenticated
            }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("auth_screen_root").assertIsDisplayed()

            // 3. Register standalone Spectator -> enters app and routes to Spectator Discover arena
            viewModel.registerStandalone(
                draft = RegistrationDraft(
                    role = UserRole.SPECTATOR,
                    stageName = "Vibe Watcher",
                    primaryCategory = PerformanceCategory.MUSIC
                ),
                identifier = "vibe@spectator.stage",
                password = "SpectatorPass88"
            )
            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Authenticated
            }
            composeTestRule.waitForIdle()

            assertTrue(viewModel.sessionState.value is AuthSessionState.Authenticated)
            assertEquals(UserRole.SPECTATOR, viewModel.userRole.value)
            assertEquals(AppNavDestination.DISCOVER, viewModel.currentDestination.value)

            // 4. Sign out and sign back in as standalone Creator -> restores Creator Studio
            viewModel.signOut()
            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Unauthenticated
            }
            viewModel.signInStandalone(
                identifier = "aria@versepulse.stage",
                password = "AriaCreatorPass88"
            )
            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Authenticated
            }
            composeTestRule.waitForIdle()
            assertEquals(UserRole.CREATOR, viewModel.userRole.value)
            assertEquals(AppNavDestination.STUDIO, viewModel.currentDestination.value)
        } finally {
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun mainViewModel_andStandaloneAccountStore_deleteCreatorAndSpectatorAccountsCleanly() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        try {
            val viewModel = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory(application)
            )[MainViewModel::class.java]
            shadowOf(Looper.getMainLooper()).idle()

            composeTestRule.setContent {
                MyApplicationTheme {
                    VersePulseApp(viewModel = viewModel)
                }
            }
            composeTestRule.waitForIdle()

            // Register Creator first
            viewModel.registerStandalone(
                draft = RegistrationDraft(
                    role = UserRole.CREATOR,
                    stageName = "Lyric Keep",
                    primaryCategory = PerformanceCategory.MUSIC
                ),
                identifier = "lyric@creator.stage",
                password = "CreatorPass#1"
            )
            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Authenticated
            }

            // Register Spectator second (becomes active session)
            viewModel.registerStandalone(
                draft = RegistrationDraft(
                    role = UserRole.SPECTATOR,
                    stageName = "Fan To Delete",
                    primaryCategory = PerformanceCategory.SPOKEN_WORD
                ),
                identifier = "fan_delete@spectator.stage",
                password = "SpectatorPass#1"
            )
            awaitMainLooperCondition {
                (viewModel.sessionState.value as? AuthSessionState.Authenticated)?.account?.email == "fan_delete@spectator.stage"
            }
            assertEquals(2, StandaloneAccountStore.listRegisteredSummaries(appContext).size)

            // Delete active Spectator account -> returns to AuthScreen with confirmation banner
            viewModel.deleteAccount()
            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Unauthenticated
            }
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithTag("auth_screen_root").assertIsDisplayed()
            composeTestRule.onNodeWithTag("auth_status_banner").performScrollTo().assertIsDisplayed()
            assertNull(StandaloneAccountStore.getActiveSessionAccount(appContext))

            // Only Creator account remains in the vault
            val remaining = StandaloneAccountStore.listRegisteredSummaries(appContext)
            assertEquals(1, remaining.size)
            assertEquals("lyric@creator.stage", remaining.single().identifier)

            // Deleted Spectator cannot sign in anymore
            val deletedSignIn = StandaloneAccountStore.signIn(
                context = appContext,
                identifier = "fan_delete@spectator.stage",
                password = "SpectatorPass#1"
            )
            assertTrue(deletedSignIn.isFailure)

            // Creator signs back in, then deletes Creator account too
            viewModel.signInStandalone("lyric@creator.stage", "CreatorPass#1")
            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Authenticated
            }
            viewModel.deleteAccount()
            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Unauthenticated
            }
            composeTestRule.waitForIdle()
            assertEquals(0, StandaloneAccountStore.listRegisteredSummaries(appContext).size)
        } finally {
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun uiConfirmDeleteAccount_removesAllAssociatedUserContentMediaFilesVotesAndSessions() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val store = ViewModelStore()
        try {
            val viewModel = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory(application)
            )[MainViewModel::class.java]
            shadowOf(Looper.getMainLooper()).idle()

            composeTestRule.setContent {
                MyApplicationTheme {
                    VersePulseApp(viewModel = viewModel)
                }
            }
            composeTestRule.waitForIdle()

            // 1. Register standalone Creator in the app
            viewModel.registerStandalone(
                draft = RegistrationDraft(
                    role = UserRole.CREATOR,
                    stageName = "Nova DeleteAll",
                    stageTitle = "Hip-Hop & Poetry",
                    primaryCategory = PerformanceCategory.MUSIC
                ),
                identifier = "nova_content@versepulse.stage",
                password = "NovaDelete#2026"
            )
            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Authenticated
            }
            composeTestRule.waitForIdle()

            val creatorUid = viewModel.authManager.userId!!

            // 2. Create real uploaded media files in app storage, publish a performance into a poll,
            //    start a live session, and cast stage + poll + creator ratings.
            val uploadsDir = File(appContext.filesDir, "uploads").apply { mkdirs() }
            val publishedAudioFile = File(uploadsDir, "music_999001_nova_track.mp3.vpenc").apply {
                writeText("encrypted-audio-bytes")
            }
            val publishedVideoFile = File(uploadsDir, "video_999002_nova_clip.mp4.vpenc").apply {
                writeText("encrypted-video-bytes")
            }
            val linkedVideoThumbFile = File(uploadsDir, "thumb_999002.jpg.vpenc").apply {
                writeText("encrypted-thumb-bytes")
            }

            val publishedPerf = viewModel.repository.publishPerformance(
                title = "Nova Sunset Freestyle",
                category = PerformanceCategory.MUSIC,
                genre = "Hip-Hop",
                lyricsOrScript = "Deleting everything cleanly on request",
                visualizerTheme = com.example.model.VisualizerTheme.COSMIC_VINYL,
                audioType = com.example.model.SynthAudioType.HIPHOP_BEAT,
                customAudioPath = publishedAudioFile.absolutePath,
                enrollInPollId = "poll_weekly_showcase",
                durationSec = 155,
                customVideoPath = publishedVideoFile.absolutePath
            )
            val createdSession = viewModel.repository.startLiveSession(
                title = "Nova Live Cypher",
                category = PerformanceCategory.MUSIC,
                genre = "Hip-Hop",
                visualizerTheme = com.example.model.VisualizerTheme.STAGE_LIGHTS,
                audioType = com.example.model.SynthAudioType.HIPHOP_BEAT,
                pollId = "poll_weekly_showcase"
            )
            viewModel.voteInPoll("poll_weekly_showcase", "cand_${publishedPerf.id}")
            viewModel.castPerformanceTierVote(publishedPerf.id, com.example.model.VoteTier.SUPER_VOTE)
            viewModel.castSessionVote(createdSession.id, creatorUid, "Nova DeleteAll", voteDelta = 5, voteType = "SUPER_VOTE")
            viewModel.rateCreator("creator_1", 5f)
            viewModel.playPerformance(publishedPerf.id, expandFullscreen = false)

            assertTrue(viewModel.performances.value.any { it.id == publishedPerf.id })
            assertTrue(viewModel.liveSessions.value.any { it.id == createdSession.id })
            assertTrue(publishedAudioFile.exists())
            assertTrue(publishedVideoFile.exists())
            assertTrue(linkedVideoThumbFile.exists())
            assertTrue(viewModel.repository.localVoteRecords.value.any { it.userId == creatorUid })
            assertTrue(viewModel.userSubmittedRatings.value.isNotEmpty())

            // 3. Establish deletion in the UI: open Account Sheet -> Delete Account -> Confirm Permanently
            composeTestRule.onNodeWithTag("auth_user_button").performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("account_dialog_root").assertIsDisplayed()
            composeTestRule.onNodeWithTag("account_delete_button").performScrollTo().performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag("delete_account_confirmation_dialog").assertIsDisplayed()
            composeTestRule.onNodeWithTag("confirm_delete_account_button").performClick()

            awaitMainLooperCondition {
                viewModel.sessionState.value is AuthSessionState.Unauthenticated
            }
            composeTestRule.waitForIdle()

            // 4. Verify UI returned to AuthScreen AND all user content & media files are deleted
            composeTestRule.onNodeWithTag("auth_screen_root").assertIsDisplayed()
            assertNull(viewModel.activePerformance.value)
            assertNull(viewModel.activeLiveSession.value)
            assertFalse(
                "Published user performance must be removed after deletion",
                viewModel.performances.value.any { it.id == publishedPerf.id || it.artistId == creatorUid }
            )
            assertTrue(
                "Seeded community performances must remain available",
                viewModel.performances.value.any { it.artistId == "creator_1" }
            )
            assertFalse(
                "User-hosted live session must be removed after deletion",
                viewModel.liveSessions.value.any { it.id == createdSession.id || it.creatorId == creatorUid }
            )
            assertFalse("Uploaded audio file must be deleted from disk", publishedAudioFile.exists())
            assertFalse("Uploaded video file must be deleted from disk", publishedVideoFile.exists())
            assertFalse("Associated video thumbnail file must be deleted from disk", linkedVideoThumbFile.exists())
            assertFalse(
                "Enrolled poll candidate must be removed from poll",
                viewModel.polls.value.flatMap { it.candidates }.any { it.performanceId == publishedPerf.id }
            )
            assertTrue(
                "User poll vote selection must be cleared",
                viewModel.polls.value.all { it.userVotedCandidateId == null }
            )
            assertTrue(
                "User vote records must be removed",
                viewModel.repository.localVoteRecords.value.none { it.userId == creatorUid || it.performanceId == publishedPerf.id }
            )
            assertFalse(
                "Deleted performance must be removed from ActivePerformanceVotingManager",
                viewModel.activeVotingState.value.activePerformances.containsKey(publishedPerf.id)
            )
            assertTrue(
                "User submitted ratings must be cleared",
                viewModel.userSubmittedRatings.value.isEmpty()
            )
            assertFalse(
                "Deleted creator profile must be removed",
                viewModel.creatorProfiles.value.containsKey(creatorUid)
            )
            assertEquals(0, StandaloneAccountStore.listRegisteredSummaries(appContext).size)
        } finally {
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
