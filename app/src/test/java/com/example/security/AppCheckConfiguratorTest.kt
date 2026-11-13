package com.example.security

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.example.StageVibeApplication
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppCheckConfiguratorTest {

    private lateinit var appContext: Application

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }
    }

    @Test
    fun stageVibeApplication_onCreate_automaticallyInitializesRecaptchaV3ProviderWithProductionSettings() {
        assertTrue(
            "AndroidManifest must instantiate StageVibeApplication on app launch",
            appContext is StageVibeApplication
        )

        val stageVibeApp = appContext as StageVibeApplication
        // Re-run onCreate()-equivalent production setup and verify telemetry immediately reflects production settings
        assertTrue(stageVibeApp.initializeProductionAppCheck())

        val status = AppCheckTelemetry.status.value
        assertTrue(status.isConfigured)
        assertTrue(status.isProduction)
        assertTrue(status.tokenAutoRefreshEnabled)
        assertTrue(status.providerName.contains("RecaptchaV3Provider"))
    }

    @Test
    fun stageVibeApplication_rejectsBlankOrPlaceholderProductionSiteKeys() {
        val stageVibeApp = appContext as StageVibeApplication

        assertFalse(stageVibeApp.initializeProductionAppCheck(siteKeyOverride = "YOUR_RECAPTCHA_ENTERPRISE_SITE_KEY"))
        assertFalse(AppCheckTelemetry.status.value.isConfigured)

        assertFalse(StageVibeApplication.isValidProductionSiteKey(""))
        assertFalse(StageVibeApplication.isValidProductionSiteKey("short_key"))
        assertFalse(StageVibeApplication.isValidProductionSiteKey("YOUR_RECAPTCHA_ENTERPRISE_SITE_KEY"))
        assertTrue(StageVibeApplication.isValidProductionSiteKey(stageVibeApp.getString(R.string.recaptcha_site_key)))
    }

    @Test
    fun recaptchaV3Provider_createsNativeFirebaseAppCheckProviderAndRejectsInvalidKeys() {
        val singletonFactory = RecaptchaV3ProviderFactory.getInstance()
        assertNotNull(singletonFactory)
        assertEquals(singletonFactory, RecaptchaV3ProviderFactory.getInstance())
        assertNotNull(singletonFactory.create(FirebaseApp.getInstance()))

        val siteKey = appContext.getString(R.string.recaptcha_site_key)
        val providerFactory = RecaptchaV3Provider(siteKey)
        assertEquals(siteKey, providerFactory.siteKey)

        val provider = providerFactory.create(FirebaseApp.getInstance())
        assertNotNull(provider)

        var threwOnPlaceholder = false
        try {
            RecaptchaV3ProviderFactory.getInstance("YOUR_RECAPTCHA_ENTERPRISE_SITE_KEY")
        } catch (_: IllegalArgumentException) {
            threwOnPlaceholder = true
        }
        assertTrue("RecaptchaV3ProviderFactory must reject placeholder site keys", threwOnPlaceholder)
    }

    @Test
    fun productionLaunch_preservesRecaptchaV3ProviderAcrossMainActivityStart() {
        val stageVibeApp = appContext as StageVibeApplication
        assertTrue(stageVibeApp.initializeProductionAppCheck())

        configureAppCheckInternal(
            context = appContext,
            intent = Intent(),
            isDebugBuild = false
        )

        val postActivityStatus = AppCheckTelemetry.status.value
        assertTrue(postActivityStatus.isConfigured)
        assertTrue(postActivityStatus.isProduction)
        assertTrue(postActivityStatus.tokenAutoRefreshEnabled)
        assertTrue(postActivityStatus.providerName.contains("RecaptchaV3Provider"))
    }

    @Test
    fun configureAppCheck_debugBuildWithTokenExtra_persistsDebugSecretSynchronouslyAndInstallsProvider() {
        val expectedToken = "debug-token-versepulse-987654"
        val launchIntent = Intent().apply {
            putExtra(DEBUG_TOKEN_EXTRA, expectedToken)
        }

        configureAppCheckInternal(
            context = appContext,
            intent = launchIntent,
            isDebugBuild = true
        )

        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val prefs = appContext.getSharedPreferences(
            "$DEBUG_STORE_PREFIX$persistenceKey",
            Context.MODE_PRIVATE
        )
        assertEquals(expectedToken, prefs.getString(DEBUG_SECRET_KEY, null))

        val status = AppCheckTelemetry.status.value
        assertTrue(status.isConfigured)
        assertFalse(status.isProduction)
        assertTrue(status.tokenAutoRefreshEnabled)
        assertTrue(status.providerName.contains("DebugAppCheckProvider"))
    }
}
