package com.example.ui

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.test.core.app.ApplicationProvider
import com.example.auth.GoogleCredentialClient
import com.example.auth.UserAuthManager
import com.example.model.AuthSessionState
import com.example.model.CreatorProfile
import com.example.model.PerformanceCategory
import com.example.model.RegistrationDraft
import com.example.model.UserAccount
import com.example.model.UserRole
import com.example.ui.screens.AccountRoleSheetDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CreatorProfileScreen
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GoogleAuthCredentialManagerTest {

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
    }

    @Test
    fun authScreen_displaysGoogleSignInAndRegisterFlowsWithRoleSelection() {
        var clickedSignInRole: UserRole? = null
        var registeredDraft: RegistrationDraft? = null
        var errorBannerText by mutableStateOf<String?>("Sample diagnostic error")

        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(
                    sessionState = AuthSessionState.Unauthenticated(),
                    isLoading = false,
                    errorMessage = errorBannerText,
                    onDismissError = { errorBannerText = null },
                    onSignInWithGoogle = { role -> clickedSignInRole = role },
                    onRegisterWithGoogle = { draft -> registeredDraft = draft },
                    onCompleteRegistration = {},
                    onSignOut = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Error banner displays and is visible
        composeTestRule.onNodeWithTag("auth_error_banner").assertIsDisplayed()

        // Select Performer role and click Sign in with Google
        composeTestRule.onNodeWithTag("register_role_performer").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("google_signin_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(UserRole.CREATOR, clickedSignInRole)

        // Switch to Register tab and trigger Google Registration
        composeTestRule.onNodeWithTag("auth_tab_register").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_register_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_register_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(UserRole.CREATOR, registeredDraft?.role)
    }

    @Test
    fun userAuthManager_handlesCredentialManagerCancellationAndNoCredentialCleanly() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val testScope = TestScope(UnconfinedTestDispatcher())
        var shouldThrowCancellation = true
        var clearStateInvocations = 0

        val fakeCredentialClient = object : GoogleCredentialClient {
            override fun getServerClientId(): String = "test-web-client-id"

            override suspend fun fetchGoogleIdTokenInteractive(activity: Activity): String {
                if (shouldThrowCancellation) {
                    throw GetCredentialCancellationException("User dismissed account chooser")
                } else {
                    throw NoCredentialException("No Google Account configured")
                }
            }

            override suspend fun fetchGoogleIdTokenSilent(activity: Activity): String {
                throw NoCredentialException("Silent check found no authorized Google account")
            }

            override suspend fun clearCredentialState() {
                clearStateInvocations++
            }
        }

        val manager = UserAuthManager(
            context = appContext,
            auth = Firebase.auth,
            scope = testScope,
            googleCredentialClient = fakeCredentialClient
        )

        try {
            // 1. Silent auto sign-in skips cleanly when no authorized Google account exists
            var unauthCallbackFired = false
            manager.attemptAutoSignIn(
                activity = activity,
                onUnauthenticated = { unauthCallbackFired = true }
            )
            assertTrue(unauthCallbackFired)
            assertTrue(manager.sessionState.value is AuthSessionState.Unauthenticated)
            assertNull(manager.authError.value)

            // 2. Interactive Google Sign-In cancellation resets isLoading without setting an error banner
            var cancelledFired = false
            manager.signInWithGoogle(
                activity = activity,
                preferredRoleIfNew = UserRole.SPECTATOR,
                onCancelled = { cancelledFired = true }
            )
            assertTrue(cancelledFired)
            assertFalse(manager.isLoading.value)
            assertNull(manager.authError.value)

            // 3. Interactive Google Sign-In with NoCredentialException surfaces actionable guidance
            shouldThrowCancellation = false
            var surfacedError: String? = null
            manager.signInWithGoogle(
                activity = activity,
                preferredRoleIfNew = UserRole.CREATOR,
                onError = { surfacedError = it }
            )
            assertFalse(manager.isLoading.value)
            assertTrue(surfacedError?.contains("No Google Account found") == true)
            assertTrue(manager.authError.value?.contains("No Google Account found") == true)

            // 4. Sign-out clears CredentialManager state inside NonCancellable and resets session state
            var signOutDone = false
            manager.signOut { signOutDone = true }
            assertTrue(signOutDone)
            assertEquals(1, clearStateInvocations)
            assertTrue(manager.sessionState.value is AuthSessionState.Unauthenticated)
            assertNull(manager.authError.value)
        } finally {
            manager.dispose()
        }
    }

    @Test
    fun accountRoleSheetDialog_supportsSwitchGoogleAccountSwitchRoleAndSignOut() {
        var switchedAccount = false
        var signedOut = false
        var switchedToRole: UserRole? = null

        val sampleAccount = UserAccount(
            uid = "google_uid_1",
            email = "performer@gmail.com",
            displayName = "Maya Vance",
            role = UserRole.SPECTATOR,
            stageName = "Maya Vance"
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                AccountRoleSheetDialog(
                    account = sampleAccount,
                    emailFallback = sampleAccount.email,
                    displayNameFallback = sampleAccount.displayName,
                    currentRole = UserRole.SPECTATOR,
                    onSwitchRole = { switchedToRole = it },
                    onViewMyProfile = {},
                    onSignOut = { signedOut = true },
                    onDismiss = {},
                    onSwitchGoogleAccount = { switchedAccount = true }
                )
            }
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("account_dialog_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("account_switch_google_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertTrue(switchedAccount)

        composeTestRule.onNodeWithTag("account_switch_role_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(UserRole.CREATOR, switchedToRole)

        composeTestRule.onNodeWithTag("confirm_signout_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(signedOut)
    }

    @Test
    fun authScreen_providesDistinctCreatorAndSpectatorFirebaseLoginAndRegistrationFlows() {
        val capturedSignIns = mutableListOf<Pair<UserRole, Boolean>>()
        val capturedRegistrations = mutableListOf<RegistrationDraft>()
        val capturedCompletions = mutableListOf<RegistrationDraft>()
        var currentSessionState by mutableStateOf<AuthSessionState>(AuthSessionState.Unauthenticated())

        composeTestRule.setContent {
            MyApplicationTheme {
                AuthScreen(
                    sessionState = currentSessionState,
                    isLoading = false,
                    errorMessage = null,
                    onDismissError = {},
                    onSignInWithGoogle = { role -> capturedSignIns.add(role to false) },
                    onSignInWithGoogleExplicit = { role, isExplicit ->
                        capturedSignIns.add(role to isExplicit)
                    },
                    onRegisterWithGoogle = { draft -> capturedRegistrations.add(draft) },
                    onCompleteRegistration = { draft -> capturedCompletions.add(draft) },
                    onSignOut = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Default Sign-In starts in Spectator Arena flow
        composeTestRule.onNodeWithTag("spectator_flow_highlights").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("spectator_login_flow_panel").assertExists()
        composeTestRule.onNodeWithTag("firebase_signin_route_pill").performScrollTo().assertIsDisplayed()

        // 2. Switching to Creator transforms Login flow to Creator Studio portal
        composeTestRule.onNodeWithTag("register_role_performer").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("creator_flow_highlights").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("creator_login_flow_panel").assertExists()
        composeTestRule.onNodeWithTag("firebase_signin_route_pill").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("google_signin_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(UserRole.CREATOR to true), capturedSignIns)

        // 3. Switch to Register tab -> Creator Registration flow shows moniker presets, bio, and Creator pass preview
        composeTestRule.onNodeWithTag("auth_tab_register").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("creator_registration_flow_panel").assertExists()
        composeTestRule.onNodeWithTag("register_bio_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("creator_pass_preview_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("creator_moniker_chip_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("google_register_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, capturedRegistrations.size)
        assertEquals(UserRole.CREATOR, capturedRegistrations.last().role)
        assertEquals("Beatmaker & MC", capturedRegistrations.last().stageTitle)

        // 4. Switch to Spectator in Register tab -> Spectator Registration flow replaces artist bio with voting persona chips
        composeTestRule.onNodeWithTag("register_role_spectator").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("spectator_registration_flow_panel").assertExists()
        composeTestRule.onNodeWithTag("register_bio_input").assertDoesNotExist()
        composeTestRule.onNodeWithTag("spectator_pass_preview_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("spectator_persona_chip_1").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("google_register_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, capturedRegistrations.size)
        assertEquals(UserRole.SPECTATOR, capturedRegistrations.last().role)
        assertEquals("Live Battle Judge", capturedRegistrations.last().stageTitle)

        // 5. Firebase NeedsRegistration completion adapts dynamically between Spectator and Creator
        currentSessionState = AuthSessionState.NeedsRegistration(
            uid = "firebase_uid_77",
            email = "artist@versepulse.stage",
            suggestedName = "Zara Echo",
            photoUrl = null,
            initialRole = UserRole.CREATOR
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("auth_complete_registration_card").assertExists()
        composeTestRule.onNodeWithTag("creator_registration_flow_panel").assertExists()
        composeTestRule.onNodeWithTag("register_stage_name_input").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("complete_registration_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, capturedCompletions.size)
        assertEquals(UserRole.CREATOR, capturedCompletions.single().role)
        assertEquals("Zara Echo", capturedCompletions.single().stageName)
    }

    @Test
    fun accountRoleSheetDialog_andCreatorProfile_requireConfirmationBeforeDeletingAccount() {
        var accountSheetDeleteCalls = 0
        var showAccountSheet by mutableStateOf(true)
        var profileDeleteCalls = 0

        val sampleAccount = UserAccount(
            uid = "google_uid_del",
            email = "creator@gmail.com",
            displayName = "Aria Stage",
            role = UserRole.CREATOR,
            stageName = "Aria Stage"
        )
        val sampleProfile = CreatorProfile(
            creatorId = "google_uid_del",
            artistName = "Aria Stage",
            stageTitle = "Synth Vocalist",
            bio = "Original tracks",
            primaryCategory = PerformanceCategory.MUSIC,
            isCurrentUser = true
        )

        composeTestRule.setContent {
            MyApplicationTheme {
                if (showAccountSheet) {
                    AccountRoleSheetDialog(
                        account = sampleAccount,
                        emailFallback = sampleAccount.email,
                        displayNameFallback = sampleAccount.displayName,
                        currentRole = UserRole.CREATOR,
                        onSwitchRole = {},
                        onViewMyProfile = {},
                        onSignOut = {},
                        onDismiss = {},
                        onDeleteAccount = { accountSheetDeleteCalls++ }
                    )
                } else {
                    CreatorProfileScreen(
                        profile = sampleProfile,
                        portfolio = emptyList(),
                        leaderboardRank = 1,
                        onPlayPerformance = {},
                        onRateCreator = {},
                        onSaveProfile = { _, _, _, _, _, _, _, _ -> },
                        onNavigateToStudio = {},
                        accountEmail = sampleAccount.email,
                        isStandaloneAccount = false,
                        accountRole = UserRole.CREATOR,
                        onSignOut = {},
                        onDeleteAccount = { profileDeleteCalls++ }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. AccountRoleSheetDialog -> click Delete Account -> cancel does NOT delete
        composeTestRule.onNodeWithTag("account_delete_button").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("delete_account_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("cancel_delete_account_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, accountSheetDeleteCalls)
        composeTestRule.onNodeWithTag("delete_account_confirmation_dialog").assertDoesNotExist()

        // 2. Click Delete Account again -> Confirm permanently triggers onDeleteAccount
        composeTestRule.onNodeWithTag("account_delete_button").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_delete_account_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, accountSheetDeleteCalls)

        // 3. CreatorProfileScreen -> Delete Account button opens confirmation modal and confirms
        showAccountSheet = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("creator_profile_list")
            .performScrollToNode(hasTestTag("profile_delete_account_button"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("profile_delete_account_button").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("delete_account_confirmation_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("confirm_delete_account_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, profileDeleteCalls)
    }

    @Test
    fun registeredUser_canParticipateInPollPerformanceAndLeaderboardVoting() {
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val viewModel = androidx.lifecycle.ViewModelProvider(
                store,
                androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(
                    appContext as android.app.Application
                )
            )[MainViewModel::class.java]

            val uniqueEmail = "voter_${System.currentTimeMillis()}@versepulse.stage"
            val draft = RegistrationDraft(
                role = UserRole.SPECTATOR,
                stageName = "Jordan Pulse",
                stageTitle = "Live Battle Judge",
                primaryCategory = PerformanceCategory.MUSIC,
                identifier = uniqueEmail,
                password = "VersePulse#2026"
            )

            var registeredAccount: UserAccount? = null
            viewModel.registerStandalone(
                draft = draft,
                identifier = uniqueEmail,
                password = "VersePulse#2026",
                onSuccess = { registeredAccount = it }
            )
            composeTestRule.waitForIdle()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            // Wait briefly for IO registration coroutine to complete
            val deadline = System.currentTimeMillis() + 3_000L
            while (registeredAccount == null && System.currentTimeMillis() < deadline) {
                Thread.sleep(20L)
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            }

            assertTrue(viewModel.sessionState.value is AuthSessionState.Authenticated)
            assertEquals("Jordan Pulse", registeredAccount?.effectiveName)

            // 1. Participate in Poll Voting
            val targetPoll = viewModel.polls.value.first()
            val targetCandidate = targetPoll.candidates.first()
            val initialCandidateVotes = targetCandidate.votesCount
            viewModel.voteInPoll(targetPoll.id, targetCandidate.candidateId)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            val updatedPoll = viewModel.polls.value.first { it.id == targetPoll.id }
            assertEquals(targetCandidate.candidateId, updatedPoll.userVotedCandidateId)
            assertEquals(
                initialCandidateVotes + 1,
                updatedPoll.candidates.first { it.candidateId == targetCandidate.candidateId }.votesCount
            )

            // 2. Participate in Active Performance Tier Voting (Super Vote +5)
            val targetPerf = viewModel.performances.value.first { it.id != targetCandidate.performanceId }
            val initialPerfVotes = targetPerf.votesCount
            viewModel.castPerformanceTierVote(targetPerf.id, com.example.model.VoteTier.SUPER_VOTE)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            val updatedPerf = viewModel.performances.value.first { it.id == targetPerf.id }
            assertTrue(updatedPerf.isVotedByMe)
            assertEquals(initialPerfVotes + com.example.model.VoteTier.SUPER_VOTE.weight, updatedPerf.votesCount)

            // 3. Participate in Leaderboard Voting & verify vote record carries registered user's UID
            val rankings = viewModel.repository.computeLeaderboard(
                categoryFilter = com.example.model.LeaderboardCategoryFilter.ALL,
                timePeriod = com.example.model.LeaderboardTimePeriod.ALL_TIME
            )
            val topCreator = rankings.first()
            val beforeLocalRecords = viewModel.repository.localVoteRecords.value.size
            viewModel.castLeaderboardVote(topCreator, voteDelta = 5)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

            val afterLocalRecords = viewModel.repository.localVoteRecords.value
            assertEquals(beforeLocalRecords + 1, afterLocalRecords.size)
            assertEquals(registeredAccount?.uid, afterLocalRecords.last().userId)

            // Clean up standalone account created in test
            viewModel.deleteAccount()
            val cleanupDeadline = System.currentTimeMillis() + 2_000L
            while (viewModel.sessionState.value is AuthSessionState.Authenticated &&
                System.currentTimeMillis() < cleanupDeadline
            ) {
                Thread.sleep(20L)
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            }
        } finally {
            store.clear()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }
}
