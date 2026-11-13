package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.FirestoreService
import com.example.model.AuthAccountProvider
import com.example.model.AuthSessionState
import com.example.model.PerformanceCategory
import com.example.model.RegistrationDraft
import com.example.model.UserAccount
import com.example.model.UserRole
import com.example.security.UploadEncryptionManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manages authentication for both **Performers** ([UserRole.CREATOR]) and **Spectators**
 * ([UserRole.SPECTATOR]) via:
 * 1. Jetpack [CredentialManager] Google Sign-In (`GetSignInWithGoogleOption`) synced with
 *    Firebase Firestore (`/users/{uid}` and `/performers/{uid}`), AND
 * 2. Encrypted on-device **Standalone Accounts** ([StandaloneAccountStore] with PBKDF2-HMAC-SHA256
 *    + AES-256-GCM) for registering and signing in without requiring a Google Account.
 */
class UserAuthManager(
    context: Context,
    firestoreService: FirestoreService? = null,
    private val auth: FirebaseAuth = Firebase.auth,
    credentialManager: CredentialManager? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    firestoreServiceProvider: () -> FirestoreService = { FirestoreService(context.applicationContext) },
    googleCredentialClient: GoogleCredentialClient? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val appContext: Context = context.applicationContext ?: context
    private val resolvedFirestoreService: FirestoreService by lazy {
        firestoreService ?: firestoreServiceProvider()
    }
    private val resolvedCredentialClient: GoogleCredentialClient by lazy {
        googleCredentialClient ?: JetpackGoogleCredentialClient(appContext, credentialManager)
    }

    companion object {
        private const val TAG = "UserAuthManager"
    }

    private val initialStandaloneAccount: UserAccount? = run {
        UploadEncryptionManager.bindContext(appContext)
        if (auth.currentUser == null) {
            StandaloneAccountStore.getActiveSessionAccount(appContext)
        } else {
            null
        }
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _userAccount = MutableStateFlow<UserAccount?>(initialStandaloneAccount)
    val userAccount: StateFlow<UserAccount?> = _userAccount.asStateFlow()

    private val _sessionState = MutableStateFlow<AuthSessionState>(
        when {
            auth.currentUser != null -> AuthSessionState.Loading
            initialStandaloneAccount != null -> AuthSessionState.Authenticated(initialStandaloneAccount)
            else -> AuthSessionState.Unauthenticated()
        }
    )
    val sessionState: StateFlow<AuthSessionState> = _sessionState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private var accountStreamJob: Job? = null
    private var isHandlingExplicitAuthFlow = false
    private var hasAttemptedAutoSignIn = false

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _currentUser.value = user
        if (user == null) {
            accountStreamJob?.cancel()
            accountStreamJob = null
            val activeStandalone = _userAccount.value?.takeIf {
                it.authProvider == AuthAccountProvider.STANDALONE
            }
            if (activeStandalone != null) {
                _sessionState.value = AuthSessionState.Authenticated(activeStandalone)
            } else {
                _userAccount.value = null
                if (_sessionState.value !is AuthSessionState.Unauthenticated) {
                    _sessionState.value = AuthSessionState.Unauthenticated(_authError.value)
                }
            }
        } else if (!isHandlingExplicitAuthFlow) {
            StandaloneAccountStore.clearActiveSession(appContext)
            hydrateUserAccountForSession(user)
        }
    }

    init {
        auth.addAuthStateListener(authStateListener)
    }

    val userId: String?
        get() = _currentUser.value?.uid ?: _userAccount.value?.uid?.takeIf { it.isNotBlank() }
    val displayName: String?
        get() = _userAccount.value?.effectiveName ?: _currentUser.value?.displayName
    val email: String?
        get() = _userAccount.value?.email ?: _currentUser.value?.email
    val photoUrl: String?
        get() = _userAccount.value?.photoUrl ?: _currentUser.value?.photoUrl?.toString()
    val isAuthenticated: Boolean
        get() = _currentUser.value != null ||
            (_sessionState.value is AuthSessionState.Authenticated && _userAccount.value != null)
    val isStandaloneSession: Boolean
        get() = _currentUser.value == null &&
            _userAccount.value?.authProvider == AuthAccountProvider.STANDALONE

    fun clearError() {
        _authError.value = null
        val current = _sessionState.value
        if (current is AuthSessionState.Unauthenticated && current.errorMessage != null) {
            _sessionState.value = AuthSessionState.Unauthenticated(null)
        }
    }

    /**
     * Hydrates the signed-in [FirebaseUser]'s registered Performer or Spectator profile from
     * `/users/{uid}` and subscribes to real-time role/profile updates.
     * When [autoProvisionIfMissing] is true (interactive Google Sign-In), automatically creates
     * the user's profile from their Google Account metadata for frictionless one-tap entry.
     */
    private fun hydrateUserAccountForSession(
        user: FirebaseUser,
        preferredRoleOnMissing: UserRole = UserRole.SPECTATOR,
        autoProvisionIfMissing: Boolean = false,
        applyExplicitRoleOverride: Boolean = false,
        onResolved: (UserAccount?) -> Unit = {}
    ) {
        accountStreamJob?.cancel()
        accountStreamJob = scope.launch {
            val lookupResult = withContext(Dispatchers.IO) {
                resolvedFirestoreService.getUserAccount(user.uid)
            }
            val existing = lookupResult.getOrNull()
            if (existing != null && existing.registrationComplete) {
                if (applyExplicitRoleOverride && existing.role != preferredRoleOnMissing) {
                    val nextTitle = when {
                        preferredRoleOnMissing == UserRole.CREATOR &&
                            (existing.stageTitle.isBlank() || existing.stageTitle == "Stage Spectator") ->
                            "${existing.primaryCategory.displayName} Performer"
                        preferredRoleOnMissing == UserRole.SPECTATOR && existing.stageTitle.isBlank() ->
                            "Stage Spectator"
                        else -> existing.stageTitle
                    }
                    val switchedCandidate = existing.copy(
                        role = preferredRoleOnMissing,
                        stageTitle = nextTitle,
                        updatedAtMillis = System.currentTimeMillis()
                    )
                    val switched = withContext(Dispatchers.IO) {
                        resolvedFirestoreService.saveUserAccount(switchedCandidate)
                    }.getOrElse { switchedCandidate }
                    _userAccount.value = switched
                    _sessionState.value = AuthSessionState.Authenticated(switched)
                    onResolved(switched)
                } else {
                    _userAccount.value = existing
                    _sessionState.value = AuthSessionState.Authenticated(existing)
                    onResolved(existing)
                }
            } else if (autoProvisionIfMissing) {
                val defaultDraft = RegistrationDraft(
                    role = preferredRoleOnMissing,
                    stageName = user.displayName?.takeIf { it.isNotBlank() } ?: "",
                    primaryCategory = PerformanceCategory.MUSIC
                )
                persistRegistrationForUser(
                    user = user,
                    draft = defaultDraft,
                    reattachHydration = false,
                    onSuccess = { saved -> onResolved(saved) },
                    onError = {
                        _sessionState.value = AuthSessionState.NeedsRegistration(
                            uid = user.uid,
                            email = user.email,
                            suggestedName = user.displayName?.takeIf { it.isNotBlank() } ?: "Stage Member",
                            photoUrl = user.photoUrl?.toString(),
                            initialRole = preferredRoleOnMissing
                        )
                        onResolved(null)
                    }
                )
            } else if (lookupResult.isSuccess && existing == null) {
                _userAccount.value = null
                _sessionState.value = AuthSessionState.NeedsRegistration(
                    uid = user.uid,
                    email = user.email,
                    suggestedName = user.displayName?.takeIf { it.isNotBlank() } ?: "Stage Member",
                    photoUrl = user.photoUrl?.toString(),
                    initialRole = preferredRoleOnMissing
                )
                onResolved(null)
            } else {
                val message = lookupResult.exceptionOrNull()?.localizedMessage
                    ?: "Unable to load account profile. Complete registration to continue."
                _authError.value = message
                _sessionState.value = AuthSessionState.NeedsRegistration(
                    uid = user.uid,
                    email = user.email,
                    suggestedName = user.displayName?.takeIf { it.isNotBlank() } ?: "Stage Member",
                    photoUrl = user.photoUrl?.toString(),
                    initialRole = preferredRoleOnMissing
                )
                onResolved(null)
            }

            // Keep listening to real-time changes on /users/{uid}
            try {
                resolvedFirestoreService.observeUserAccount(user.uid).collect { liveAccount ->
                    if (liveAccount != null && liveAccount.registrationComplete) {
                        _userAccount.value = liveAccount
                        _sessionState.value = AuthSessionState.Authenticated(liveAccount)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "UserAccount snapshot stream closed: ${e.message}")
            }
        }
    }

    /**
     * Silent auto sign-in after the Activity's first frame has drawn using `GetGoogleIdOption`
     * (single-option `GetCredentialRequest` bound to an active [Activity] window).
     */
    fun attemptAutoSignIn(
        activity: Activity? = null,
        onSuccess: (FirebaseUser) -> Unit = {},
        onUnauthenticated: () -> Unit = {}
    ) {
        val existingUser = auth.currentUser
        if (existingUser != null) {
            hasAttemptedAutoSignIn = true
            _currentUser.value = existingUser
            hydrateUserAccountForSession(existingUser, autoProvisionIfMissing = true)
            onSuccess(existingUser)
            return
        }

        val standaloneSession = _userAccount.value?.takeIf {
            it.authProvider == AuthAccountProvider.STANDALONE
        } ?: StandaloneAccountStore.getActiveSessionAccount(appContext)
        if (standaloneSession != null) {
            hasAttemptedAutoSignIn = true
            _userAccount.value = standaloneSession
            _sessionState.value = AuthSessionState.Authenticated(standaloneSession)
            return
        }

        if (hasAttemptedAutoSignIn) {
            return
        }

        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            if (_sessionState.value is AuthSessionState.Loading) {
                _sessionState.value = AuthSessionState.Unauthenticated()
            }
            onUnauthenticated()
            return
        }

        if (resolvedCredentialClient.getServerClientId() == null) {
            hasAttemptedAutoSignIn = true
            _sessionState.value = AuthSessionState.Unauthenticated()
            onUnauthenticated()
            return
        }

        hasAttemptedAutoSignIn = true

        scope.launch {
            try {
                val googleIdToken = resolvedCredentialClient.fetchGoogleIdTokenSilent(activity)
                _sessionState.value = AuthSessionState.Loading
                isHandlingExplicitAuthFlow = true
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                isHandlingExplicitAuthFlow = false
                val user = authResult.user
                if (user != null) {
                    _currentUser.value = user
                    hydrateUserAccountForSession(user, autoProvisionIfMissing = true)
                    onSuccess(user)
                } else {
                    _sessionState.value = AuthSessionState.Unauthenticated()
                    onUnauthenticated()
                }
            } catch (e: CancellationException) {
                isHandlingExplicitAuthFlow = false
                throw e
            } catch (e: Exception) {
                isHandlingExplicitAuthFlow = false
                Log.d(TAG, "Silent auto-sign-in skipped: ${e.message}")
                if (auth.currentUser == null) {
                    _sessionState.value = AuthSessionState.Unauthenticated()
                }
                onUnauthenticated()
            }
        }
    }

    /**
     * Interactive Google Sign-In via Jetpack [CredentialManager] (`GetSignInWithGoogleOption`).
     * Restores returning Performers and Spectators automatically, or provisions a new Google
     * Account immediately using [preferredRoleIfNew] when [autoProvisionIfNew] is true.
     */
    fun signInWithGoogle(
        activity: Activity,
        preferredRoleIfNew: UserRole = UserRole.SPECTATOR,
        autoProvisionIfNew: Boolean = true,
        applyExplicitRoleSelection: Boolean = false,
        onSuccess: (FirebaseUser, UserAccount?) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {},
        onCancelled: () -> Unit = {}
    ) {
        if (resolvedCredentialClient.getServerClientId() == null) {
            val error = "Google Sign-In configuration missing: default_web_client_id not found."
            Log.e(TAG, error)
            _authError.value = error
            _sessionState.value = AuthSessionState.Unauthenticated(error)
            onError(error)
            return
        }

        _isLoading.value = true
        _authError.value = null

        scope.launch {
            try {
                val googleIdToken = resolvedCredentialClient.fetchGoogleIdTokenInteractive(activity)
                isHandlingExplicitAuthFlow = true
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                isHandlingExplicitAuthFlow = false
                val user = authResult.user

                _isLoading.value = false
                if (user != null) {
                    StandaloneAccountStore.clearActiveSession(appContext)
                    _currentUser.value = user
                    Log.i(TAG, "Google Sign-In succeeded for user: ${user.uid}")
                    hydrateUserAccountForSession(
                        user = user,
                        preferredRoleOnMissing = preferredRoleIfNew,
                        autoProvisionIfMissing = autoProvisionIfNew,
                        applyExplicitRoleOverride = applyExplicitRoleSelection
                    ) { resolvedAccount ->
                        onSuccess(user, resolvedAccount)
                    }
                } else {
                    val err = "Firebase user was null after Google Sign-In."
                    _authError.value = err
                    if (!isStandaloneSession) {
                        _sessionState.value = AuthSessionState.Unauthenticated(err)
                    }
                    onError(err)
                }
            } catch (e: GetCredentialCancellationException) {
                isHandlingExplicitAuthFlow = false
                _isLoading.value = false
                Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
                if (auth.currentUser == null && !isStandaloneSession) {
                    _sessionState.value = AuthSessionState.Unauthenticated()
                }
                onCancelled()
            } catch (e: NoCredentialException) {
                isHandlingExplicitAuthFlow = false
                _isLoading.value = false
                val msg = "No Google Account found on this device. Use Standalone Sign-In / Registration or add a Google Account in Android Settings."
                Log.w(TAG, "No Google credentials available: ${e.message}", e)
                _authError.value = msg
                if (auth.currentUser == null && !isStandaloneSession) {
                    _sessionState.value = AuthSessionState.Unauthenticated(msg)
                }
                onError(msg)
            } catch (e: CancellationException) {
                isHandlingExplicitAuthFlow = false
                _isLoading.value = false
                throw e
            } catch (e: Exception) {
                isHandlingExplicitAuthFlow = false
                _isLoading.value = false
                val errorMsg = e.localizedMessage ?: "Google Sign-In failed"
                Log.e(TAG, "Google Sign-In failure", e)
                _authError.value = errorMsg
                if (auth.currentUser == null && !isStandaloneSession) {
                    _sessionState.value = AuthSessionState.Unauthenticated(errorMsg)
                }
                onError(errorMsg)
            }
        }
    }

    /**
     * Clears cached Credential Manager selection and opens the Google Account chooser via
     * `GetSignInWithGoogleOption` so an authenticated user can switch Google accounts seamlessly
     * without losing their current session if they dismiss the sheet.
     */
    fun switchGoogleAccount(
        activity: Activity,
        preferredRoleIfNew: UserRole = _userAccount.value?.role ?: UserRole.SPECTATOR,
        onSuccess: (FirebaseUser, UserAccount?) -> Unit = { _, _ -> },
        onError: (String) -> Unit = {},
        onCancelled: () -> Unit = {}
    ) {
        scope.launch {
            try {
                resolvedCredentialClient.clearCredentialState()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state prior to account switch: ${e.message}")
            }
            signInWithGoogle(
                activity = activity,
                preferredRoleIfNew = preferredRoleIfNew,
                autoProvisionIfNew = true,
                onSuccess = onSuccess,
                onError = onError,
                onCancelled = onCancelled
            )
        }
    }

    /**
     * Interactive Registration flow via Jetpack [CredentialManager] (`GetSignInWithGoogleOption`)
     * for new **Performers** ([UserRole.CREATOR]) and **Spectators** ([UserRole.SPECTATOR]).
     */
    fun registerWithGoogle(
        activity: Activity,
        draft: RegistrationDraft,
        onSuccess: (UserAccount) -> Unit = {},
        onError: (String) -> Unit = {},
        onCancelled: () -> Unit = {}
    ) {
        val alreadySignedIn = auth.currentUser
        if (alreadySignedIn != null) {
            completeRegistration(draft, onSuccess, onError)
            return
        }

        if (resolvedCredentialClient.getServerClientId() == null) {
            val error = "Google Sign-In configuration missing: default_web_client_id not found."
            Log.e(TAG, error)
            _authError.value = error
            _sessionState.value = AuthSessionState.Unauthenticated(error)
            onError(error)
            return
        }

        _isLoading.value = true
        _authError.value = null
        isHandlingExplicitAuthFlow = true

        scope.launch {
            try {
                val googleIdToken = resolvedCredentialClient.fetchGoogleIdTokenInteractive(activity)
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                val user = authResult.user

                if (user != null) {
                    _currentUser.value = user
                    persistRegistrationForUser(
                        user = user,
                        draft = draft,
                        reattachHydration = true,
                        onSuccess = onSuccess,
                        onError = onError
                    )
                } else {
                    isHandlingExplicitAuthFlow = false
                    _isLoading.value = false
                    val err = "Firebase user was null after Google credential verification."
                    _authError.value = err
                    _sessionState.value = AuthSessionState.Unauthenticated(err)
                    onError(err)
                }
            } catch (e: GetCredentialCancellationException) {
                isHandlingExplicitAuthFlow = false
                _isLoading.value = false
                Log.w(TAG, "Google Registration cancelled or dismissed: ${e.message}", e)
                if (auth.currentUser == null) {
                    _sessionState.value = AuthSessionState.Unauthenticated()
                }
                onCancelled()
            } catch (e: NoCredentialException) {
                isHandlingExplicitAuthFlow = false
                _isLoading.value = false
                val msg = "No Google Account found on this device. Add a Google Account in Android Settings to register."
                Log.w(TAG, "No Google credentials available for registration: ${e.message}", e)
                _authError.value = msg
                _sessionState.value = AuthSessionState.Unauthenticated(msg)
                onError(msg)
            } catch (e: CancellationException) {
                isHandlingExplicitAuthFlow = false
                _isLoading.value = false
                throw e
            } catch (e: Exception) {
                isHandlingExplicitAuthFlow = false
                _isLoading.value = false
                val errorMsg = e.localizedMessage ?: "Registration failed"
                Log.e(TAG, "Google Registration failure", e)
                _authError.value = errorMsg
                _sessionState.value = AuthSessionState.Unauthenticated(errorMsg)
                onError(errorMsg)
            }
        }
    }

    /**
     * Registers a standalone **Creator** ([UserRole.CREATOR]) or **Spectator**
     * ([UserRole.SPECTATOR]) account in the AES-256-GCM encrypted on-device vault using
     * PBKDF2-HMAC-SHA256 password hashing, without requiring a Google Account on the device.
     */
    fun registerStandalone(
        draft: RegistrationDraft,
        identifier: String = draft.identifier,
        password: String = draft.password,
        activateSession: Boolean = true,
        onSuccess: (UserAccount) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        _isLoading.value = true
        _authError.value = null
        scope.launch {
            val result = withContext(ioDispatcher) {
                StandaloneAccountStore.registerAccount(
                    context = appContext,
                    identifier = identifier,
                    password = password,
                    draft = draft.copy(
                        identifier = identifier,
                        authProvider = AuthAccountProvider.STANDALONE
                    ),
                    activateSession = activateSession
                )
            }
            _isLoading.value = false
            result.fold(
                onSuccess = { created ->
                    if (activateSession) {
                        if (auth.currentUser != null) {
                            auth.signOut()
                            _currentUser.value = null
                        }
                        _userAccount.value = created
                        _sessionState.value = AuthSessionState.Authenticated(created)
                    }
                    onSuccess(created)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Unable to register standalone account."
                    _authError.value = msg
                    if (!isAuthenticated) {
                        _sessionState.value = AuthSessionState.Unauthenticated(msg)
                    }
                    onError(msg)
                }
            )
        }
    }

    /**
     * Signs in to a standalone **Creator** ([UserRole.CREATOR]) or **Spectator**
     * ([UserRole.SPECTATOR]) account verified against the AES-256-GCM encrypted vault.
     */
    fun signInStandalone(
        identifier: String,
        password: String,
        explicitRole: UserRole? = null,
        onSuccess: (UserAccount) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        _isLoading.value = true
        _authError.value = null
        scope.launch {
            val result = withContext(ioDispatcher) {
                StandaloneAccountStore.signIn(
                    context = appContext,
                    identifier = identifier,
                    password = password,
                    explicitRole = explicitRole
                )
            }
            _isLoading.value = false
            result.fold(
                onSuccess = { signedInAccount ->
                    if (auth.currentUser != null) {
                        auth.signOut()
                        _currentUser.value = null
                    }
                    _userAccount.value = signedInAccount
                    _sessionState.value = AuthSessionState.Authenticated(signedInAccount)
                    onSuccess(signedInAccount)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Standalone sign-in failed."
                    _authError.value = msg
                    if (!isAuthenticated) {
                        _sessionState.value = AuthSessionState.Unauthenticated(msg)
                    }
                    onError(msg)
                }
            )
        }
    }

    /**
     * Completes Performer or Spectator registration for a currently authenticated [FirebaseUser].
     */
    fun completeRegistration(
        draft: RegistrationDraft,
        onSuccess: (UserAccount) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val user = auth.currentUser
        if (user == null) {
            val err = "Please sign in with Google or use Standalone Registration."
            _authError.value = err
            onError(err)
            return
        }

        _isLoading.value = true
        _authError.value = null
        scope.launch {
            persistRegistrationForUser(
                user = user,
                draft = draft,
                reattachHydration = true,
                onSuccess = onSuccess,
                onError = onError
            )
        }
    }

    private suspend fun persistRegistrationForUser(
        user: FirebaseUser,
        draft: RegistrationDraft,
        reattachHydration: Boolean = true,
        onSuccess: (UserAccount) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val fallbackName = user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: if (draft.role == UserRole.CREATOR) "Stage Performer" else "Stage Spectator"

            val cleanStageName = draft.stageName.trim().ifBlank { fallbackName }
            val cleanStageTitle = draft.stageTitle.trim().ifBlank {
                if (draft.role == UserRole.CREATOR) "${draft.primaryCategory.displayName} Performer"
                else "Stage Spectator"
            }
            val cleanBio = draft.bio.trim().ifBlank {
                if (draft.role == UserRole.CREATOR) {
                    "Original ${draft.primaryCategory.displayName.lowercase()} artist performing on VersePulse."
                } else {
                    "Live music & spoken word supporter in VersePulse Arena."
                }
            }

            val accountToSave = UserAccount(
                uid = user.uid,
                email = user.email,
                displayName = fallbackName,
                photoUrl = user.photoUrl?.toString(),
                role = draft.role,
                stageName = cleanStageName,
                stageTitle = cleanStageTitle,
                bio = cleanBio,
                primaryCategory = draft.primaryCategory,
                registrationComplete = true
            )

            val saveResult = withContext(Dispatchers.IO) {
                resolvedFirestoreService.saveUserAccount(accountToSave)
            }
            isHandlingExplicitAuthFlow = false
            _isLoading.value = false

            saveResult.fold(
                onSuccess = { saved ->
                    _userAccount.value = saved
                    _sessionState.value = AuthSessionState.Authenticated(saved)
                    if (reattachHydration) {
                        hydrateUserAccountForSession(user, saved.role)
                    }
                    onSuccess(saved)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Failed to save account registration"
                    _authError.value = msg
                    onError(msg)
                }
            )
        } catch (e: CancellationException) {
            isHandlingExplicitAuthFlow = false
            _isLoading.value = false
            throw e
        } catch (e: Exception) {
            isHandlingExplicitAuthFlow = false
            _isLoading.value = false
            val msg = e.localizedMessage ?: "Failed to complete registration"
            _authError.value = msg
            onError(msg)
        }
    }

    /**
     * Switches or upgrades an authenticated user between **Spectator** ([UserRole.SPECTATOR])
     * and **Performer** ([UserRole.CREATOR]), persisting the change to `/users/{uid}` and
     * provisioning `/performers/{uid}` when switching to Performer.
     */
    fun updateAccountRole(
        newRole: UserRole,
        stageName: String? = null,
        stageTitle: String? = null,
        bio: String? = null,
        category: PerformanceCategory? = null,
        onSuccess: (UserAccount) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val firebaseUser = auth.currentUser
        val currentStandalone = _userAccount.value?.takeIf {
            it.authProvider == AuthAccountProvider.STANDALONE
        }
        if (firebaseUser == null && currentStandalone == null) return

        val base = _userAccount.value ?: UserAccount(
            uid = firebaseUser?.uid.orEmpty(),
            email = firebaseUser?.email,
            displayName = firebaseUser?.displayName ?: "Stage Member",
            photoUrl = firebaseUser?.photoUrl?.toString(),
            role = newRole
        )

        val nextCategory = category ?: base.primaryCategory
        val updated = base.copy(
            role = newRole,
            stageName = stageName?.takeIf { it.isNotBlank() } ?: base.stageName.ifBlank { base.displayName },
            stageTitle = stageTitle?.takeIf { it.isNotBlank() } ?: when {
                newRole == UserRole.CREATOR && (base.stageTitle.isBlank() || base.stageTitle == "Stage Spectator") ->
                    "${nextCategory.displayName} Performer"
                newRole == UserRole.SPECTATOR && base.stageTitle.isBlank() ->
                    "Stage Spectator"
                else -> base.stageTitle
            },
            bio = bio ?: base.bio,
            primaryCategory = nextCategory,
            registrationComplete = true,
            updatedAtMillis = System.currentTimeMillis()
        )

        _userAccount.value = updated
        _sessionState.value = AuthSessionState.Authenticated(updated)

        if (firebaseUser == null && currentStandalone != null) {
            scope.launch {
                val standaloneRes = withContext(ioDispatcher) {
                    StandaloneAccountStore.updateAccount(appContext, updated)
                }
                standaloneRes.fold(
                    onSuccess = { saved ->
                        _userAccount.value = saved
                        _sessionState.value = AuthSessionState.Authenticated(saved)
                        onSuccess(saved)
                    },
                    onFailure = { err ->
                        val msg = err.localizedMessage ?: "Unable to update standalone account role"
                        _authError.value = msg
                        onError(msg)
                    }
                )
            }
            return
        }

        scope.launch {
            val res = withContext(Dispatchers.IO) {
                resolvedFirestoreService.saveUserAccount(updated)
            }
            res.fold(
                onSuccess = { saved ->
                    _userAccount.value = saved
                    _sessionState.value = AuthSessionState.Authenticated(saved)
                    onSuccess(saved)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Unable to update account role"
                    _authError.value = msg
                    onError(msg)
                }
            )
        }
    }

    /**
     * Clears standalone session & [CredentialManager] state BEFORE signing out of [FirebaseAuth]
     * inside [NonCancellable] so unmounting the calling Composable never aborts credential cleanup.
     */
    fun signOut(onComplete: () -> Unit = {}) {
        val wasStandaloneOnly = isStandaloneSession
        scope.launch {
            withContext(NonCancellable) {
                accountStreamJob?.cancel()
                accountStreamJob = null
                hasAttemptedAutoSignIn = false
                StandaloneAccountStore.clearActiveSession(appContext)
                try {
                    if (!wasStandaloneOnly) {
                        withTimeoutOrNull(1_500L) {
                            resolvedCredentialClient.clearCredentialState()
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear credential state: ${e.message}")
                } finally {
                    auth.signOut()
                    _currentUser.value = null
                    _userAccount.value = null
                    _authError.value = null
                    _sessionState.value = AuthSessionState.Unauthenticated()
                    onComplete()
                }
            }
        }
    }

    /**
     * Permanently deletes the signed-in account:
     * - For Standalone sessions, removes the account record from [StandaloneAccountStore] and clears
     *   active session state.
     * - For Firebase Auth (Google) sessions, deletes `/performers/{uid}` and `/users/{uid}` in Firestore,
     *   deletes the [FirebaseUser] (attempting Credential Manager Google re-authentication if
     *   [FirebaseAuthRecentLoginRequiredException] is thrown), and clears credential state.
     */
    fun deleteAccount(
        activity: Activity? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val currentFirebaseUser = auth.currentUser
        val standaloneUid = _userAccount.value
            ?.takeIf { it.authProvider == AuthAccountProvider.STANDALONE }
            ?.uid
            ?.takeIf { it.isNotBlank() }
            ?: StandaloneAccountStore.getActiveSessionAccount(appContext)?.uid

        if (currentFirebaseUser == null && standaloneUid == null) {
            val msg = "No signed-in account found to delete."
            _authError.value = msg
            onError(msg)
            return
        }

        _isLoading.value = true
        _authError.value = null

        scope.launch {
            try {
                if (currentFirebaseUser == null && standaloneUid != null) {
                    val deleteResult = withContext(ioDispatcher) {
                        StandaloneAccountStore.deleteAccount(appContext, standaloneUid)
                    }
                    deleteResult.fold(
                        onSuccess = {
                            withContext(NonCancellable) {
                                accountStreamJob?.cancel()
                                accountStreamJob = null
                                hasAttemptedAutoSignIn = false
                                _isLoading.value = false
                                _currentUser.value = null
                                _userAccount.value = null
                                _authError.value = null
                                _sessionState.value = AuthSessionState.Unauthenticated()
                                onSuccess()
                            }
                        },
                        onFailure = { err ->
                            _isLoading.value = false
                            val msg = err.localizedMessage ?: "Failed to delete standalone account."
                            _authError.value = msg
                            onError(msg)
                        }
                    )
                    return@launch
                }

                val firebaseUser = currentFirebaseUser!!
                accountStreamJob?.cancel()
                accountStreamJob = null

                // Delete Firestore /performers/{uid} & /users/{uid} while the auth token is still valid.
                val firestoreDeleteResult = withContext(ioDispatcher) {
                    resolvedFirestoreService.deleteUserAccount(firebaseUser.uid)
                }
                if (firestoreDeleteResult.isFailure) {
                    Log.w(
                        TAG,
                        "Firestore cleanup during account deletion encountered an issue: ${firestoreDeleteResult.exceptionOrNull()?.message}"
                    )
                }

                try {
                    firebaseUser.delete().await()
                } catch (e: FirebaseAuthRecentLoginRequiredException) {
                    if (activity != null && !activity.isFinishing && !activity.isDestroyed &&
                        resolvedCredentialClient.getServerClientId() != null
                    ) {
                        val googleIdToken = resolvedCredentialClient.fetchGoogleIdTokenInteractive(activity)
                        val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
                        firebaseUser.reauthenticate(credential).await()
                        withContext(ioDispatcher) {
                            resolvedFirestoreService.deleteUserAccount(firebaseUser.uid)
                        }
                        firebaseUser.delete().await()
                    } else {
                        throw e
                    }
                }

                withContext(NonCancellable) {
                    hasAttemptedAutoSignIn = false
                    StandaloneAccountStore.clearActiveSession(appContext)
                    try {
                        withTimeoutOrNull(1_500L) {
                            resolvedCredentialClient.clearCredentialState()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clear credential state after deletion: ${e.message}")
                    } finally {
                        auth.signOut()
                        _isLoading.value = false
                        _currentUser.value = null
                        _userAccount.value = null
                        _authError.value = null
                        _sessionState.value = AuthSessionState.Unauthenticated()
                        onSuccess()
                    }
                }
            } catch (e: GetCredentialCancellationException) {
                _isLoading.value = false
                val msg = "Google verification was cancelled. Your account was not deleted."
                _authError.value = msg
                onError(msg)
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                _isLoading.value = false
                val msg = "Please sign out and sign in again with Google to delete your account."
                _authError.value = msg
                onError(msg)
            } catch (e: CancellationException) {
                _isLoading.value = false
                throw e
            } catch (e: Exception) {
                _isLoading.value = false
                val msg = e.localizedMessage ?: "Failed to delete account."
                Log.e(TAG, "Account deletion failure", e)
                _authError.value = msg
                onError(msg)
            }
        }
    }

    fun dispose() {
        accountStreamJob?.cancel()
        accountStreamJob = null
        auth.removeAuthStateListener(authStateListener)
    }
}
