package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Abstraction over Jetpack [CredentialManager] for Google ID Token retrieval and session state
 * cleanup. Keeps interactive (`GetSignInWithGoogleOption`) and silent (`GetGoogleIdOption`)
 * credential requests strictly separated into single-option requests for Android 16+ (API 36)
 * bottom-sheet responsiveness, while allowing deterministic JVM/Robolectric unit testing.
 */
interface GoogleCredentialClient {
    fun getServerClientId(): String?
    suspend fun fetchGoogleIdTokenInteractive(activity: Activity): String
    suspend fun fetchGoogleIdTokenSilent(activity: Activity): String
    suspend fun clearCredentialState()
}

class JetpackGoogleCredentialClient(
    context: Context,
    credentialManager: CredentialManager? = null
) : GoogleCredentialClient {

    private val appContext: Context = context.applicationContext
    private val resolvedCredentialManager: CredentialManager by lazy {
        credentialManager ?: CredentialManager.create(appContext)
    }

    override fun getServerClientId(): String? {
        return try {
            appContext.getString(R.string.default_web_client_id).takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In configuration missing: default_web_client_id not found", e)
            null
        }
    }

    override suspend fun fetchGoogleIdTokenInteractive(activity: Activity): String {
        val clientId = getServerClientId()
            ?: throw IllegalStateException("Google Sign-In configuration missing: default_web_client_id not found.")

        // Single-option request exclusively using GetSignInWithGoogleOption for interactive sign-in
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        val result = resolvedCredentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type: ${credential::class.java.name}")
    }

    override suspend fun fetchGoogleIdTokenSilent(activity: Activity): String {
        val clientId = getServerClientId()
            ?: throw IllegalStateException("Google Sign-In configuration missing: default_web_client_id not found.")

        // Separate single-option request exclusively using GetGoogleIdOption for silent auto sign-in
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = resolvedCredentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type: ${credential::class.java.name}")
    }

    override suspend fun clearCredentialState() {
        resolvedCredentialManager.clearCredentialState(ClearCredentialStateRequest())
    }

    private companion object {
        const val TAG = "GoogleCredentialClient"
    }
}

/**
 * Standalone interactive Google Sign-In helper using Jetpack [CredentialManager] and [FirebaseAuth].
 */
fun onGoogleSignInClicked(
    activity: Activity,
    credentialManager: CredentialManager = CredentialManager.create(activity.applicationContext),
    auth: FirebaseAuth = Firebase.auth,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: () -> Unit = {}
) {
    val client = JetpackGoogleCredentialClient(activity.applicationContext, credentialManager)
    if (client.getServerClientId() == null) {
        onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
        return
    }

    scope.launch {
        try {
            val googleIdToken = client.fetchGoogleIdTokenInteractive(activity)
            val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            auth.signInWithCredential(authCredential).await()
            onAuthSuccess()
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            onAuthCancelled()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            onAuthError(e.localizedMessage ?: "Sign in failed")
        }
    }
}

/**
 * Clears [CredentialManager] state BEFORE signing out of [FirebaseAuth] inside [NonCancellable].
 */
fun signOutWithCredentialManager(
    credentialManager: CredentialManager,
    auth: FirebaseAuth = Firebase.auth,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
