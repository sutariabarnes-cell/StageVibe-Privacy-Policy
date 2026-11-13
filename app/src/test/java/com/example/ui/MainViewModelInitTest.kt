package com.example.ui

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.MainActivity
import com.example.model.AuthSessionState
import com.google.firebase.FirebaseApp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MainViewModelInitTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_coldStartRendersFirstFrameImmediatelyWithoutBlockingAuthSpinner() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        if (FirebaseApp.getApps(app).isEmpty()) {
            FirebaseApp.initializeApp(app)
        }
        Firebase.auth.signOut()

        composeRule.waitForIdle()

        // Verify MainActivity renders AuthScreen and interactive Google Sign-In button immediately
        // on initial frame without getting stuck in Loading or stalling SurfaceSyncGroup.
        composeRule.onNodeWithTag("auth_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_loading_card").assertDoesNotExist()
        composeRule.onNodeWithTag("auth_signin_card").assertExists()
        composeRule.onNodeWithTag("google_signin_button").performScrollTo().assertIsDisplayed()

        // Advance past the 1000ms SurfaceSyncGroup window and verify no HiddenActivity / background
        // credential picker activity was auto-launched to steal window focus from MainActivity.
        composeRule.mainClock.advanceTimeBy(1500L)
        composeRule.waitForIdle()
        assertNull(shadowOf(app).nextStartedActivity)
        composeRule.onNodeWithTag("google_signin_button").assertIsDisplayed()
    }

    @Test
    fun mainViewModel_initializesOnMainThreadAndUpdatesActivePerformanceWithoutNpe() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        if (FirebaseApp.getApps(application).isEmpty()) {
            FirebaseApp.initializeApp(application)
        }
        Firebase.auth.signOut()

        val store = ViewModelStore()
        try {
            val viewModel = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            )[MainViewModel::class.java]

            shadowOf(Looper.getMainLooper()).idle()

            assertTrue(viewModel.sessionState.value is AuthSessionState.Unauthenticated)
            assertNull(viewModel.activePerformance.value)
            assertTrue(viewModel.performances.value.isNotEmpty())
            assertEquals("creator_user_me", viewModel.repository.currentUserId)

            val firstPerf = viewModel.performances.value.first()
            val initialVotes = firstPerf.votesCount

            viewModel.playPerformance(firstPerf.id, expandFullscreen = false)
            shadowOf(Looper.getMainLooper()).idle()

            val activeBeforeVote = viewModel.activePerformance.value
            assertNotNull(activeBeforeVote)
            assertEquals(firstPerf.id, activeBeforeVote?.id)

            // Trigger a real-time performance vote update and verify _activePerformance syncs cleanly
            viewModel.voteForPerformance(firstPerf.id)
            shadowOf(Looper.getMainLooper()).idle()

            val activeAfterVote = viewModel.activePerformance.value
            assertNotNull(activeAfterVote)
            assertEquals(initialVotes + 1, activeAfterVote?.votesCount)

            viewModel.closePlayer()
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(viewModel.activePerformance.value)
        } finally {
            store.clear()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }
}
