package com.example

import android.app.Application
import android.util.Log
import com.example.security.AppCheckTelemetry
import com.example.security.RecaptchaV3ProviderFactory
import com.example.security.UploadEncryptionManager
import com.example.security.resolveProductionRecaptchaSiteKey
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Custom Android [Application] class registered in `AndroidManifest.xml` (`android:name=".StageVibeApplication"`).
 *
 * Initializes [FirebaseApp] and configures [FirebaseAppCheck] in [onCreate] using
 * `FirebaseAppCheck.getInstance().installAppCheckProviderFactory(RecaptchaV3ProviderFactory.getInstance())`
 * while enforcing production attestation requirements (automatic token rotation and non-placeholder
 * `recaptcha_site_key` verification).
 */
class StageVibeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        UploadEncryptionManager.bindContext(this)

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }

            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.setTokenAutoRefreshEnabled(true)

            val resolvedSiteKey = resolveProductionRecaptchaSiteKey(this)
            if (isValidProductionSiteKey(resolvedSiteKey)) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    RecaptchaV3ProviderFactory.getInstance()
                )
                AppCheckTelemetry.update(
                    isConfigured = true,
                    isProduction = true,
                    providerName = "RecaptchaV3ProviderFactory • Production (Auth & Firestore Streams)",
                    tokenAutoRefreshEnabled = true,
                    siteKeyValid = true,
                    context = this
                )
                Log.i(
                    TAG,
                    "FirebaseAppCheck initialized with RecaptchaV3ProviderFactory.getInstance() for production."
                )
            } else {
                AppCheckTelemetry.update(
                    isConfigured = false,
                    isProduction = true,
                    providerName = "Missing or Invalid Production reCAPTCHA Site Key",
                    tokenAutoRefreshEnabled = true,
                    siteKeyValid = false,
                    context = this
                )
                Log.e(TAG, "Production recaptcha_site_key is missing, blank, or set to a placeholder.")
            }
        } catch (e: Exception) {
            AppCheckTelemetry.update(
                isConfigured = false,
                isProduction = true,
                providerName = "Production App Check Initialization Error",
                tokenAutoRefreshEnabled = false,
                siteKeyValid = false,
                context = this
            )
            Log.e(TAG, "Failed to initialize FirebaseAppCheck in StageVibeApplication.onCreate", e)
        }
    }

    /**
     * Re-runs or verifies production [FirebaseAppCheck] setup with [RecaptchaV3ProviderFactory],
     * optionally validating a custom [siteKeyOverride] for diagnostics and unit tests.
     */
    fun initializeProductionAppCheck(siteKeyOverride: String? = null): Boolean {
        return try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }

            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.setTokenAutoRefreshEnabled(true)

            val resolvedSiteKey = siteKeyOverride?.trim()?.takeIf { it.isNotEmpty() }
                ?: resolveProductionRecaptchaSiteKey(this)

            if (isValidProductionSiteKey(resolvedSiteKey)) {
                val factory = if (siteKeyOverride == null) {
                    RecaptchaV3ProviderFactory.getInstance()
                } else {
                    RecaptchaV3ProviderFactory.getInstance(checkNotNull(resolvedSiteKey))
                }
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(factory)
                AppCheckTelemetry.update(
                    isConfigured = true,
                    isProduction = true,
                    providerName = "RecaptchaV3ProviderFactory • Production (Auth & Firestore Streams)",
                    tokenAutoRefreshEnabled = true,
                    siteKeyValid = true,
                    context = this
                )
                Log.i(TAG, "FirebaseAppCheck configured with RecaptchaV3ProviderFactory for production.")
                true
            } else {
                AppCheckTelemetry.update(
                    isConfigured = false,
                    isProduction = true,
                    providerName = "Missing or Invalid Production reCAPTCHA Site Key",
                    tokenAutoRefreshEnabled = true,
                    siteKeyValid = false,
                    context = this
                )
                Log.e(TAG, "Production recaptcha_site_key is missing, blank, or still set to a placeholder.")
                false
            }
        } catch (e: Exception) {
            AppCheckTelemetry.update(
                isConfigured = false,
                isProduction = true,
                providerName = "Production App Check Initialization Error",
                tokenAutoRefreshEnabled = false,
                siteKeyValid = false,
                context = this
            )
            Log.e(TAG, "Failed to initialize FirebaseAppCheck in StageVibeApplication", e)
            false
        }
    }

    companion object {
        private const val TAG = "StageVibeApplication"
        private const val PLACEHOLDER_SITE_KEY = "YOUR_RECAPTCHA_ENTERPRISE_SITE_KEY"
        private const val MIN_RECAPTCHA_SITE_KEY_LENGTH = 20

        internal fun isValidProductionSiteKey(siteKey: String?): Boolean {
            if (siteKey.isNullOrBlank()) return false
            val trimmed = siteKey.trim()
            return trimmed.length >= MIN_RECAPTCHA_SITE_KEY_LENGTH &&
                !trimmed.equals(PLACEHOLDER_SITE_KEY, ignoreCase = true)
        }
    }
}
