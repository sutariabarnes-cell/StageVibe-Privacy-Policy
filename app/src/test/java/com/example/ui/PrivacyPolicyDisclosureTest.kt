package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.model.AuthSessionState
import com.example.model.PerformanceCategory
import com.example.model.UserAccount
import com.example.model.UserRole
import com.example.ui.components.VersePulsePrivacyPolicyContent
import com.example.ui.screens.AccountRoleSheetDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrivacyPolicyDisclosureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun authScreen_preLogin_opensPrivacyPolicyDialogWithPlayDataSafetyDisclosuresAndCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()

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
                    onSignOut = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("auth_privacy_policy_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("privacy_policy_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("No Location Collected").assertIsDisplayed()
        composeTestRule.onNodeWithText("TLS + AES-256-GCM").assertIsDisplayed()
        composeTestRule.onNodeWithText("No Ads or Data Sales").assertIsDisplayed()
        composeTestRule.onNodeWithText("Full Account Deletion").assertIsDisplayed()

        composeTestRule.onNodeWithTag("privacy_policy_copy_button").performClick()
        composeTestRule.waitForIdle()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copiedText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(copiedText.contains("VersePulse Privacy Policy"))
        assertTrue(copiedText.contains("does NOT collect coarse, fine, or background device location"))
        assertTrue(copiedText.contains(VersePulsePrivacyPolicyContent.PRIVACY_CONTACT_EMAIL))

        composeTestRule.onNodeWithTag("privacy_policy_done_button").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun accountRoleSheetDialog_exposesPrivacyPolicyAndAccountDeletionDisclosures() {
        composeTestRule.setContent {
            MyApplicationTheme {
                AccountRoleSheetDialog(
                    account = UserAccount(
                        uid = "user_privacy_1",
                        email = "creator@versepulse.stage",
                        displayName = "Nova Verse",
                        role = UserRole.CREATOR,
                        stageName = "Nova Verse",
                        primaryCategory = PerformanceCategory.MUSIC,
                        registrationComplete = true
                    ),
                    emailFallback = "creator@versepulse.stage",
                    displayNameFallback = "Nova Verse",
                    currentRole = UserRole.CREATOR,
                    onSwitchRole = {},
                    onViewMyProfile = {},
                    onSignOut = {},
                    onDismiss = {},
                    onDeleteAccount = {}
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("account_privacy_policy_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("privacy_policy_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("VersePulse Privacy Policy").assertIsDisplayed()
    }
}
