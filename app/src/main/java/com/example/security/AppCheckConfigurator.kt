package com.example.security

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.example.StageVibeApplication
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.AppCheckProvider
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "AppCheckInit"

// App Check contract keys. These strings are defined by the Firebase SDK and
// the runtime harness, so do not rename them.
internal const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
internal const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
internal const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
internal const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

/**
 * Production score-based reCAPTCHA v3 / Enterprise [AppCheckProviderFactory] singleton factory
 * for Android, backed by Firebase's native [RecaptchaAppCheckProviderFactory] (`firebase-appcheck-recaptcha`).
 *
 * Resolves and validates the provisioned `recaptcha_site_key` resource from [FirebaseApp.getApplicationContext]
 * (or an explicit site key override) to enforce production attestation requirements.
 */
class RecaptchaV3ProviderFactory private constructor(
    val explicitSiteKey: String? = null
) : AppCheckProviderFactory {

    override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
        val resolvedKey = explicitSiteKey?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolveProductionRecaptchaSiteKey(firebaseApp.applicationContext)
        require(StageVibeApplication.isValidProductionSiteKey(resolvedKey)) {
            "Production reCAPTCHA site key must be a valid non-placeholder key."
        }
        return RecaptchaAppCheckProviderFactory
            .getInstance(checkNotNull(resolvedKey).trim())
            .create(firebaseApp)
    }

    companion object {
        @Volatile
        private var defaultInstance: RecaptchaV3ProviderFactory? = null

        @JvmStatic
        fun getInstance(): RecaptchaV3ProviderFactory {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: RecaptchaV3ProviderFactory().also { defaultInstance = it }
            }
        }

        @JvmStatic
        fun getInstance(siteKey: String): RecaptchaV3ProviderFactory {
            require(StageVibeApplication.isValidProductionSiteKey(siteKey)) {
                "Production reCAPTCHA site key must be a valid non-placeholder key."
            }
            return RecaptchaV3ProviderFactory(siteKey.trim())
        }
    }
}

/**
 * Production score-based reCAPTCHA v3 / Enterprise [AppCheckProviderFactory] wrapper
 * accepting an explicit validated [siteKey].
 */
class RecaptchaV3Provider(
    val siteKey: String
) : AppCheckProviderFactory {

    private val delegate: RecaptchaV3ProviderFactory =
        RecaptchaV3ProviderFactory.getInstance(siteKey)

    override fun create(firebaseApp: FirebaseApp): AppCheckProvider {
        return delegate.create(firebaseApp)
    }
}

enum class AppCheckRolloutStage(val stepNumber: Int) {
    CLIENT_ATTESTATION_ACTIVE(1),
    CLOUD_METRICS_MONITORING(2),
    STRICT_ENFORCEMENT_READY(3);

    fun next(): AppCheckRolloutStage = when (this) {
        CLIENT_ATTESTATION_ACTIVE -> CLOUD_METRICS_MONITORING
        CLOUD_METRICS_MONITORING -> STRICT_ENFORCEMENT_READY
        STRICT_ENFORCEMENT_READY -> CLIENT_ATTESTATION_ACTIVE
    }
}

data class AppCheckStatusSnapshot(
    val isConfigured: Boolean = false,
    val isProduction: Boolean = false,
    val providerName: String = "Unconfigured",
    val tokenAutoRefreshEnabled: Boolean = false,
    val siteKeyValid: Boolean = false,
    val rolloutStage: AppCheckRolloutStage = AppCheckRolloutStage.CLIENT_ATTESTATION_ACTIVE,
    val lastVerifiedAtMillis: Long = 0L
)

object AppCheckTelemetry {
    private const val PREF_KEY_ROLLOUT_STAGE = "appcheck_rollout_stage_v1"

    private val _status = MutableStateFlow(AppCheckStatusSnapshot())
    val status: StateFlow<AppCheckStatusSnapshot> = _status.asStateFlow()

    internal fun update(
        isConfigured: Boolean,
        isProduction: Boolean,
        providerName: String,
        tokenAutoRefreshEnabled: Boolean,
        siteKeyValid: Boolean = _status.value.siteKeyValid,
        context: Context? = null
    ) {
        val persistedStage = resolvePersistedStage(context)
        _status.value = _status.value.copy(
            isConfigured = isConfigured,
            isProduction = isProduction,
            providerName = providerName,
            tokenAutoRefreshEnabled = tokenAutoRefreshEnabled,
            siteKeyValid = siteKeyValid,
            rolloutStage = persistedStage,
            lastVerifiedAtMillis = System.currentTimeMillis()
        )
    }

    fun setRolloutStage(context: Context?, stage: AppCheckRolloutStage): AppCheckStatusSnapshot {
        UploadEncryptionManager.putEncryptedPref(context, PREF_KEY_ROLLOUT_STAGE, stage.name)
        val next = _status.value.copy(
            rolloutStage = stage,
            lastVerifiedAtMillis = System.currentTimeMillis()
        )
        _status.value = next
        return next
    }

    fun advanceRolloutStage(context: Context?): AppCheckStatusSnapshot {
        val nextStage = _status.value.rolloutStage.next()
        return setRolloutStage(context, nextStage)
    }

    fun verifyAttestationReadiness(context: Context): AppCheckStatusSnapshot {
        val appContext = context.applicationContext ?: context
        val siteKey = resolveProductionRecaptchaSiteKey(appContext)
        val validKey = StageVibeApplication.isValidProductionSiteKey(siteKey)
        val persistedStage = resolvePersistedStage(appContext)
        val current = _status.value

        val verified = current.copy(
            siteKeyValid = validKey,
            rolloutStage = persistedStage,
            lastVerifiedAtMillis = System.currentTimeMillis()
        )
        _status.value = verified
        Log.i(
            TAG,
            "AppCheck readiness verified: configured=${verified.isConfigured}, siteKeyValid=$validKey, stage=${verified.rolloutStage}"
        )
        return verified
    }

    private fun resolvePersistedStage(context: Context?): AppCheckRolloutStage {
        val raw = UploadEncryptionManager.getEncryptedPref(context, PREF_KEY_ROLLOUT_STAGE, null)
        return if (!raw.isNullOrBlank()) {
            runCatching { AppCheckRolloutStage.valueOf(raw) }
                .getOrDefault(_status.value.rolloutStage)
        } else {
            _status.value.rolloutStage
        }
    }
}

/**
 * Resolves the configured production reCAPTCHA site key safely from application resources
 * without throwing `Resources.NotFoundException`.
 */
fun resolveProductionRecaptchaSiteKey(context: Context): String? {
    val resId = context.resources.getIdentifier(RECAPTCHA_SITE_KEY_RES, "string", context.packageName)
    if (resId == 0) return null
    return context.getString(resId).trim().takeIf { it.isNotEmpty() }
}

/**
 * Initializes [FirebaseAppCheck] with [RecaptchaV3Provider] for the production environment.
 */
fun initializeProductionAppCheckWithRecaptchaV3(
    context: Context,
    siteKeyOverride: String? = null
): Boolean {
    val appContext = context.applicationContext ?: context
    if (appContext is StageVibeApplication && siteKeyOverride == null) {
        val ok = appContext.initializeProductionAppCheck(null)
        AppCheckTelemetry.verifyAttestationReadiness(appContext)
        return ok
    } else if (appContext is StageVibeApplication) {
        val ok = appContext.initializeProductionAppCheck(siteKeyOverride)
        val overrideValid = StageVibeApplication.isValidProductionSiteKey(siteKeyOverride)
        AppCheckTelemetry.update(
            isConfigured = ok,
            isProduction = true,
            providerName = AppCheckTelemetry.status.value.providerName,
            tokenAutoRefreshEnabled = AppCheckTelemetry.status.value.tokenAutoRefreshEnabled,
            siteKeyValid = overrideValid,
            context = appContext
        )
        return ok
    }
    return try {
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.setTokenAutoRefreshEnabled(true)

        val resolvedSiteKey = siteKeyOverride?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolveProductionRecaptchaSiteKey(appContext)

        if (StageVibeApplication.isValidProductionSiteKey(resolvedSiteKey)) {
            val validKey = checkNotNull(resolvedSiteKey)
            val providerFactory = if (siteKeyOverride == null) {
                RecaptchaV3ProviderFactory.getInstance()
            } else {
                RecaptchaV3ProviderFactory.getInstance(validKey)
            }
            firebaseAppCheck.installAppCheckProviderFactory(providerFactory)
            AppCheckTelemetry.update(
                isConfigured = true,
                isProduction = true,
                providerName = "RecaptchaV3ProviderFactory • Production (Auth & Firestore Streams)",
                tokenAutoRefreshEnabled = true,
                siteKeyValid = true,
                context = appContext
            )
            Log.i(TAG, "Production RecaptchaV3ProviderFactory installed for FirebaseAppCheck.")
            true
        } else {
            AppCheckTelemetry.update(
                isConfigured = false,
                isProduction = true,
                providerName = "Missing or Invalid Production reCAPTCHA Site Key",
                tokenAutoRefreshEnabled = true,
                siteKeyValid = false,
                context = appContext
            )
            Log.e(TAG, "recaptcha_site_key string resource is missing, blank, or invalid.")
            false
        }
    } catch (e: Exception) {
        AppCheckTelemetry.update(
            isConfigured = false,
            isProduction = true,
            providerName = "Production App Check Initialization Error",
            tokenAutoRefreshEnabled = false,
            siteKeyValid = false,
            context = appContext
        )
        Log.e(TAG, "Failed to initialize production FirebaseAppCheck with RecaptchaV3Provider", e)
        false
    }
}

/**
 * Launch-intent App Check coordinator called in `MainActivity.onCreate()` prior to `setContent`,
 * ViewModels, or Firebase operations:
 * - In debug builds, if the launch [intent] carries `FIREBASE_APPCHECK_DEBUG_TOKEN` (or a debug
 *   secret was previously committed), synchronously persists it via `.commit()` and installs
 *   [DebugAppCheckProviderFactory] for emulator/CI attestation.
 * - In production builds (`!BuildConfig.DEBUG`), ensures the [RecaptchaV3Provider] initialized by
 *   [StageVibeApplication.onCreate] remains active (or installs it if not yet configured).
 */
fun configureAppCheck(context: Context, intent: Intent?) {
    configureAppCheckInternal(
        context = context,
        intent = intent,
        isDebugBuild = BuildConfig.DEBUG
    )
}

internal fun configureAppCheckInternal(
    context: Context,
    intent: Intent?,
    isDebugBuild: Boolean
) {
    val siteKeyValid = StageVibeApplication.isValidProductionSiteKey(resolveProductionRecaptchaSiteKey(context))
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        if (isDebugBuild) {
            val firebaseAppCheck = FirebaseAppCheck.getInstance()
            firebaseAppCheck.setTokenAutoRefreshEnabled(true)

            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val prefs = context.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)

            if (!debugToken.isNullOrEmpty()) {
                // CRITICAL: MUST use commit() instead of apply() so the secret is written
                // synchronously before DebugAppCheckProviderFactory reads it.
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                Log.d(TAG, "App Check debug secret configured successfully.")
            } else {
                Log.w(TAG, "No debug token found in Intent extra.")
            }

            firebaseAppCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            AppCheckTelemetry.update(
                isConfigured = true,
                isProduction = false,
                providerName = "DebugAppCheckProvider (Auth & Firestore Streams)",
                tokenAutoRefreshEnabled = true,
                siteKeyValid = siteKeyValid,
                context = context
            )
            Log.d(TAG, "DebugAppCheckProviderFactory installed.")
        } else {
            val currentStatus = AppCheckTelemetry.status.value
            if (!currentStatus.isConfigured || !currentStatus.isProduction) {
                initializeProductionAppCheckWithRecaptchaV3(context)
            } else {
                AppCheckTelemetry.verifyAttestationReadiness(context)
            }
        }
    } catch (e: Exception) {
        AppCheckTelemetry.update(
            isConfigured = false,
            isProduction = !isDebugBuild,
            providerName = "Initialization Error",
            tokenAutoRefreshEnabled = false,
            siteKeyValid = siteKeyValid,
            context = context
        )
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
