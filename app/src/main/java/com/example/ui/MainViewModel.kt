package com.example.ui

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.UserAuthManager
import com.example.audio.AudioSynthEngine
import com.example.audio.SoundEffectManager
import com.example.audio.VoiceRecorderManager
import com.example.data.PerformanceRepository
import com.example.model.ActiveVotingState
import com.example.model.AppNavDestination
import com.example.model.AuthSessionState
import com.example.model.CreatorProfile
import com.example.model.CreatorRanking
import com.example.model.GlobalRatingsArenaSummary
import com.example.model.LeaderboardCategoryFilter
import com.example.model.LeaderboardRankingMode
import com.example.model.LeaderboardTimePeriod
import com.example.model.LeaderboardUiState
import com.example.model.LiveSession
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.PerformanceVoteCount
import com.example.model.Poll
import com.example.model.RegistrationDraft
import com.example.model.StageVotingSnapshot
import com.example.model.SynthAudioType
import com.example.model.UserAccount
import com.example.model.UserRole
import com.example.model.VisualizerTheme
import com.example.model.VoteTier
import com.example.model.VotesAggregationSnapshot
import android.net.Uri
import com.example.audio.MusicUploadHelper
import com.example.audio.VideoUploadHelper
import com.example.data.local.OfflineCachedTrackEntity
import com.example.model.LiveChatMessage
import com.example.model.StageReactionType
import com.example.model.UploadedAudioInfo
import com.example.model.UploadedVideoInfo
import com.example.security.AppCheckRolloutStage
import com.example.security.AppCheckStatusSnapshot
import com.example.security.AppCheckTelemetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val repository = PerformanceRepository(application)
    val authManager = UserAuthManager(
        context = application,
        scope = viewModelScope,
        firestoreServiceProvider = {
            repository.firestoreService ?: com.example.data.FirestoreService(application)
        }
    )

    val currentUser = authManager.currentUser
    val userAccount: StateFlow<UserAccount?> = authManager.userAccount
    val sessionState: StateFlow<AuthSessionState> = authManager.sessionState
    val isAuthLoading: StateFlow<Boolean> = authManager.isLoading
    val authError: StateFlow<String?> = authManager.authError

    // Navigation & View Modes
    private val _currentDestination = MutableStateFlow(AppNavDestination.DISCOVER)
    val currentDestination: StateFlow<AppNavDestination> = _currentDestination.asStateFlow()

    private val _userRole = MutableStateFlow(UserRole.SPECTATOR)
    val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

    // Active Performance in Player Screen (null = no track loaded; isPlayerExpanded controls fullscreen vs docked mini-player)
    private val _activePerformance = MutableStateFlow<Performance?>(null)
    val activePerformance: StateFlow<Performance?> = _activePerformance.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded: StateFlow<Boolean> = _isPlayerExpanded.asStateFlow()

    private val _scaffoldSnackbarMessage = MutableStateFlow<String?>(null)
    val scaffoldSnackbarMessage: StateFlow<String?> = _scaffoldSnackbarMessage.asStateFlow()

    private val _authStatusMessage = MutableStateFlow<String?>(null)
    val authStatusMessage: StateFlow<String?> = _authStatusMessage.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgressSec = MutableStateFlow(0)
    val playbackProgressSec: StateFlow<Int> = _playbackProgressSec.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0.15f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    // Leaderboard state (defaults to Global All-Time Cumulative Audience Ratings)
    private val _leaderboardRankingMode = MutableStateFlow(LeaderboardRankingMode.CUMULATIVE_RATINGS)
    val leaderboardRankingMode: StateFlow<LeaderboardRankingMode> = _leaderboardRankingMode.asStateFlow()

    private val _leaderboardCategory = MutableStateFlow(LeaderboardCategoryFilter.ALL)
    val leaderboardCategory: StateFlow<LeaderboardCategoryFilter> = _leaderboardCategory.asStateFlow()

    private val _leaderboardTimePeriod = MutableStateFlow(LeaderboardTimePeriod.ALL_TIME)
    val leaderboardTimePeriod: StateFlow<LeaderboardTimePeriod> = _leaderboardTimePeriod.asStateFlow()

    // Live Streaming state
    private val _activeLiveSession = MutableStateFlow<LiveSession?>(null)
    val activeLiveSession: StateFlow<LiveSession?> = _activeLiveSession.asStateFlow()

    // Profile viewing state
    private val _viewingProfileId = MutableStateFlow<String?>(null)
    val viewingProfileId: StateFlow<String?> = _viewingProfileId.asStateFlow()

    // Studio Recording state
    private val _isRecordingAudio = MutableStateFlow(false)
    val isRecordingAudio: StateFlow<Boolean> = _isRecordingAudio.asStateFlow()

    private val _recordAmplitude = MutableStateFlow(0.05f)
    val recordAmplitude: StateFlow<Float> = _recordAmplitude.asStateFlow()

    private val _recordedFilePath = MutableStateFlow<String?>(null)
    val recordedFilePath: StateFlow<String?> = _recordedFilePath.asStateFlow()

    // Uploaded Music State
    private val _uploadedAudioInfo = MutableStateFlow<UploadedAudioInfo?>(null)
    val uploadedAudioInfo: StateFlow<UploadedAudioInfo?> = _uploadedAudioInfo.asStateFlow()

    private val _isUploadingAudio = MutableStateFlow(false)
    val isUploadingAudio: StateFlow<Boolean> = _isUploadingAudio.asStateFlow()

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError.asStateFlow()

    // Uploaded Video State (declared above init {})
    private val _uploadedVideoInfo = MutableStateFlow<UploadedVideoInfo?>(null)
    val uploadedVideoInfo: StateFlow<UploadedVideoInfo?> = _uploadedVideoInfo.asStateFlow()

    private val _isUploadingVideo = MutableStateFlow(false)
    val isUploadingVideo: StateFlow<Boolean> = _isUploadingVideo.asStateFlow()

    private val _videoUploadError = MutableStateFlow<String?>(null)
    val videoUploadError: StateFlow<String?> = _videoUploadError.asStateFlow()

    // Audio Engines (lazily initialized off the cold-start main-thread path)
    val soundEffects: SoundEffectManager by lazy {
        SoundEffectManager().apply {
            isSoundEnabled = _isSoundEffectsEnabled.value
        }
    }

    private val _isSoundEffectsEnabled = MutableStateFlow(true)
    val isSoundEffectsEnabled: StateFlow<Boolean> = _isSoundEffectsEnabled.asStateFlow()

    val isEncryptionEnabled: StateFlow<Boolean> =
        com.example.security.UploadEncryptionManager.encryptionEnabledFlow
    val encryptionStatusSnapshot: StateFlow<com.example.security.UploadEncryptionManager.EncryptionStatusSnapshot> =
        com.example.security.UploadEncryptionManager.statusSnapshotFlow
    val appCheckStatus: StateFlow<AppCheckStatusSnapshot> = AppCheckTelemetry.status

    val offlineCachedTracks: StateFlow<List<OfflineCachedTrackEntity>> =
        (repository.offlineCacheRepository?.observeCachedTracks() ?: flowOf(emptyList()))
            .flowOn(Dispatchers.IO)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                emptyList()
            )

    val pinnedPerformanceIds: StateFlow<Set<String>> = offlineCachedTracks
        .map { list -> list.map { it.performanceId }.toSet() }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptySet()
        )

    private val lastSpectatorChatSentAtMs = HashMap<String, Long>()

    private var isSynthInitialized = false
    private val synthAudioPlayer: AudioSynthEngine by lazy {
        isSynthInitialized = true
        AudioSynthEngine(application) { amp ->
            _audioAmplitude.value = amp
        }
    }

    private var isRecorderInitialized = false
    private val voiceRecorderManager: VoiceRecorderManager by lazy {
        isRecorderInitialized = true
        VoiceRecorderManager(application) { amp ->
            _recordAmplitude.value = amp
        }
    }

    private fun stopSynthIfRunning() {
        if (isSynthInitialized) {
            synthAudioPlayer.stop()
        }
    }

    // Repository flows
    val performances: StateFlow<List<Performance>> = repository.performances
    val polls: StateFlow<List<Poll>> = repository.polls
    val liveSessions: StateFlow<List<LiveSession>> = repository.liveSessions
    val creatorProfiles: StateFlow<Map<String, CreatorProfile>> = repository.creatorProfiles

    // Real-time Active Performance Voting StateFlow system
    val activeVotingState: StateFlow<ActiveVotingState> = repository.activeVotingState
    val activePerformanceVoteCounts: StateFlow<Map<String, Int>> = repository.activePerformanceVoteCounts

    val activePlayerVoteState: StateFlow<PerformanceVoteCount?> = combine(
        _activePerformance,
        activeVotingState
    ) { perf, votingSnapshot ->
        perf?.let { votingSnapshot.getPerformanceVoteState(it.id) }
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        null
    )

    private val votesSignal = combine(
        repository.cloudVoteRecords,
        repository.localVoteRecords,
        repository.isVotesStreamActive,
        repository.leaderboardError
    ) { cloud, local, active, err ->
         VotesStreamSignal(cloud.size, local.size, active, err)
    }

    val votesAggregation: StateFlow<VotesAggregationSnapshot> = combine(
        votesSignal,
        _leaderboardTimePeriod
    ) { _, period ->
        repository.getCurrentVotesAggregation(period)
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        VotesAggregationSnapshot()
    )

    val userSubmittedRatings: StateFlow<Map<String, Int>> = repository.userSubmittedRatings

    private val leaderboardFilterSignal = combine(
        votesSignal,
        _leaderboardCategory,
        _leaderboardTimePeriod,
        _leaderboardRankingMode
    ) { signal, category, timePeriod, rankingMode ->
        LeaderboardFilterConfig(signal, category, timePeriod, rankingMode)
    }

    // Dynamic Global Leaderboard rankings derived in real time from cumulative audience ratings & stage votes
    val leaderboardRankings: StateFlow<List<CreatorRanking>> = combine(
        performances,
        creatorProfiles,
        repository.performers,
        leaderboardFilterSignal
    ) { _, _, _, config ->
        repository.computeLeaderboard(
            categoryFilter = config.category,
            timePeriod = config.timePeriod,
            rankingMode = config.rankingMode
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val globalRatingsArenaSummary: StateFlow<GlobalRatingsArenaSummary> = combine(
        leaderboardRankings,
        creatorProfiles
    ) { rankings, _ ->
        repository.computeGlobalRatingsArenaSummary(rankings)
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        GlobalRatingsArenaSummary()
    )

    val leaderboardUiState: StateFlow<LeaderboardUiState> = combine(
        leaderboardRankings,
        votesAggregation,
        sessionState,
        repository.leaderboardError,
        _leaderboardRankingMode
    ) { rankings, agg, session, error, mode ->
        if (error != null && rankings.isEmpty()) {
            LeaderboardUiState.Error(message = error)
        } else {
            LeaderboardUiState.Success(
                rankings = rankings,
                aggregationSnapshot = agg,
                isAuthenticated = session is AuthSessionState.Authenticated,
                rankingMode = mode,
                arenaSummary = repository.computeGlobalRatingsArenaSummary(rankings)
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        LeaderboardUiState.Loading
    )

    private data class VotesStreamSignal(
        val cloudCount: Int,
        val localCount: Int,
        val isStreamActive: Boolean,
        val error: String?
    )

    private data class LeaderboardFilterConfig(
        val votesSignal: VotesStreamSignal,
        val category: LeaderboardCategoryFilter,
        val timePeriod: LeaderboardTimePeriod,
        val rankingMode: LeaderboardRankingMode
    )

    init {
        com.example.security.UploadEncryptionManager.bindContext(application)
        viewModelScope.launch(Dispatchers.IO) {
            if (com.example.security.UploadEncryptionManager.isEncryptionEnabled(application)) {
                com.example.security.UploadEncryptionManager.ensureExistingUploadsEncrypted(application)
            } else {
                com.example.security.UploadEncryptionManager.refreshVaultStatus(application)
            }
        }
        viewModelScope.launch {
            authManager.userAccount.collect { account ->
                repository.syncAuthenticatedUserAccount(account)
                if (account != null) {
                    _userRole.value = account.role
                }
            }
        }
        // Keep _activePerformance synchronized with real-time StateFlow vote/like updates
        viewModelScope.launch {
            repository.performances.collect { updatedList ->
                val currentActive = _activePerformance.value ?: return@collect
                val refreshed = updatedList.find { it.id == currentActive.id }
                if (refreshed != null && refreshed != currentActive) {
                    _activePerformance.value = refreshed
                }
            }
        }
        // Keep _activeLiveSession synchronized with real-time stage spectator & status updates
        viewModelScope.launch {
            repository.liveSessions.collect { sessions ->
                val currentSession = _activeLiveSession.value ?: return@collect
                val refreshed = sessions.find { it.id == currentSession.id }
                if (refreshed != null && refreshed != currentSession) {
                    _activeLiveSession.value = refreshed
                }
            }
        }
    }

    fun setEncryptionEnabled(enabled: Boolean) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val migrated = withContext(Dispatchers.IO) {
                com.example.security.UploadEncryptionManager.setEncryptionEnabled(app, enabled)
                if (enabled) {
                    com.example.security.UploadEncryptionManager.ensureExistingUploadsEncrypted(app)
                } else {
                    0
                }
            }
            if (enabled) {
                val suffix = if (migrated > 0) " ($migrated file(s) encrypted)" else ""
                showScaffoldMessage("AES-256-GCM Encryption Enabled$suffix")
            } else {
                showScaffoldMessage("Upload encryption paused for new files")
            }
        }
    }

    fun verifyAndEncryptVault() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val (migrated, snapshot, appCheck) = withContext(Dispatchers.IO) {
                com.example.security.UploadEncryptionManager.setEncryptionEnabled(app, true)
                val count = com.example.security.UploadEncryptionManager.ensureExistingUploadsEncrypted(app)
                val verifiedAppCheck = AppCheckTelemetry.verifyAttestationReadiness(app)
                Triple(count, com.example.security.UploadEncryptionManager.statusSnapshotFlow.value, verifiedAppCheck)
            }
            val stageShort = when (appCheck.rolloutStage) {
                AppCheckRolloutStage.CLIENT_ATTESTATION_ACTIVE -> "Stage 1 Client Active"
                AppCheckRolloutStage.CLOUD_METRICS_MONITORING -> "Stage 2 Cloud Monitoring"
                AppCheckRolloutStage.STRICT_ENFORCEMENT_READY -> "Stage 3 Strict Ready"
            }
            val msg = if (migrated > 0) {
                "Encrypted $migrated file(s) • ${snapshot.encryptedFilesCount} vault file(s) • App Check ($stageShort)"
            } else {
                "Vault & App Check verified ($stageShort • ${snapshot.encryptedFilesCount} encrypted file(s))"
            }
            showScaffoldMessage(msg)
        }
    }

    fun advanceAppCheckRolloutStage() {
        val app = getApplication<Application>()
        val updated = AppCheckTelemetry.advanceRolloutStage(app)
        val stageTitle = when (updated.rolloutStage) {
            AppCheckRolloutStage.CLIENT_ATTESTATION_ACTIVE ->
                app.getString(com.example.R.string.appcheck_rollout_stage_1_title)
            AppCheckRolloutStage.CLOUD_METRICS_MONITORING ->
                app.getString(com.example.R.string.appcheck_rollout_stage_2_title)
            AppCheckRolloutStage.STRICT_ENFORCEMENT_READY ->
                app.getString(com.example.R.string.appcheck_rollout_stage_3_title)
        }
        showScaffoldMessage("App Check Rollout: $stageTitle")
    }

    fun toggleOfflinePin(performance: Performance) {
        val cacheRepo = repository.offlineCacheRepository ?: return
        viewModelScope.launch {
            val pinnedNow = cacheRepo.togglePinPerformance(performance)
            if (pinnedNow) {
                soundEffects.playLikeSound()
                showScaffoldMessage("Pinned \"${performance.title}\" to encrypted offline vault")
            } else {
                showScaffoldMessage("Removed offline copy of \"${performance.title}\"")
            }
        }
    }

    fun unpinOfflineTrack(performanceId: String) {
        val cacheRepo = repository.offlineCacheRepository ?: return
        viewModelScope.launch {
            cacheRepo.unpinPerformance(performanceId)
            showScaffoldMessage("Removed cached offline replay")
        }
    }

    fun clearOfflineCache() {
        val cacheRepo = repository.offlineCacheRepository ?: return
        viewModelScope.launch {
            cacheRepo.clearAllCache()
            showScaffoldMessage("Cleared all encrypted offline replay files")
        }
    }

    fun attemptAutoSignInIfNeeded(activity: Activity) {
        authManager.attemptAutoSignIn(activity = activity)
    }

    fun dismissAuthError() {
        authManager.clearError()
    }

    fun dismissAuthStatus() {
        _authStatusMessage.value = null
    }

    fun signInWithGoogle(
        activity: Activity,
        preferredRoleIfNew: UserRole = UserRole.SPECTATOR,
        applyExplicitRoleSelection: Boolean = false,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        _authStatusMessage.value = null
        authManager.signInWithGoogle(
            activity = activity,
            preferredRoleIfNew = preferredRoleIfNew,
            applyExplicitRoleSelection = applyExplicitRoleSelection,
            onSuccess = { _, account ->
                val resolvedRole = account?.role ?: preferredRoleIfNew
                _userRole.value = resolvedRole
                if (account != null) {
                    repository.syncAuthenticatedUserAccount(account)
                }
                _currentDestination.value = if (resolvedRole == UserRole.CREATOR) {
                    AppNavDestination.STUDIO
                } else {
                    AppNavDestination.DISCOVER
                }
                val roleLabel = if (resolvedRole == UserRole.CREATOR) "Creator" else "Spectator"
                showScaffoldMessage(
                    "Signed in with Firebase Auth as ${account?.effectiveName ?: authManager.displayName ?: roleLabel} ($roleLabel)"
                )
                onSuccess()
            },
            onError = onError
        )
    }

    fun switchGoogleAccount(
        activity: Activity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        authManager.switchGoogleAccount(
            activity = activity,
            preferredRoleIfNew = _userRole.value,
            onSuccess = { _, account ->
                if (account != null) {
                    _userRole.value = account.role
                    repository.syncAuthenticatedUserAccount(account)
                }
                showScaffoldMessage("Signed in as ${account?.effectiveName ?: authManager.displayName ?: "Google Account"}")
                onSuccess()
            },
            onError = onError
        )
    }

    fun registerWithGoogle(
        activity: Activity,
        draft: RegistrationDraft,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        _authStatusMessage.value = null
        authManager.registerWithGoogle(
            activity = activity,
            draft = draft,
            onSuccess = { account ->
                _userRole.value = account.role
                repository.syncAuthenticatedUserAccount(account)
                if (account.role == UserRole.CREATOR) {
                    _currentDestination.value = AppNavDestination.STUDIO
                } else {
                    _currentDestination.value = AppNavDestination.DISCOVER
                }
                onSuccess()
            },
            onError = onError
        )
    }

    fun registerStandalone(
        draft: RegistrationDraft,
        identifier: String = draft.identifier,
        password: String = draft.password,
        activateSession: Boolean = true,
        onSuccess: (UserAccount) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        _authStatusMessage.value = null
        authManager.registerStandalone(
            draft = draft,
            identifier = identifier,
            password = password,
            activateSession = activateSession,
            onSuccess = { account ->
                if (activateSession) {
                    _userRole.value = account.role
                    repository.syncAuthenticatedUserAccount(account)
                    if (account.role == UserRole.CREATOR) {
                        _currentDestination.value = AppNavDestination.STUDIO
                    } else {
                        _currentDestination.value = AppNavDestination.DISCOVER
                    }
                    showScaffoldMessage("Welcome ${account.effectiveName} — Standalone ${if (account.role == UserRole.CREATOR) "Creator" else "Spectator"} pass active")
                }
                onSuccess(account)
            },
            onError = onError
        )
    }

    fun signInStandalone(
        identifier: String,
        password: String,
        explicitRole: UserRole? = null,
        onSuccess: (UserAccount) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        _authStatusMessage.value = null
        authManager.signInStandalone(
            identifier = identifier,
            password = password,
            explicitRole = explicitRole,
            onSuccess = { account ->
                _userRole.value = account.role
                repository.syncAuthenticatedUserAccount(account)
                if (account.role == UserRole.CREATOR) {
                    _currentDestination.value = AppNavDestination.STUDIO
                } else {
                    _currentDestination.value = AppNavDestination.DISCOVER
                }
                showScaffoldMessage("Signed in as ${account.effectiveName} (${if (account.role == UserRole.CREATOR) "Creator" else "Spectator"})")
                onSuccess(account)
            },
            onError = onError
        )
    }

    fun completeRegistration(
        draft: RegistrationDraft,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        _authStatusMessage.value = null
        authManager.completeRegistration(
            draft = draft,
            onSuccess = { account ->
                _userRole.value = account.role
                repository.syncAuthenticatedUserAccount(account)
                if (account.role == UserRole.CREATOR) {
                    _currentDestination.value = AppNavDestination.STUDIO
                } else {
                    _currentDestination.value = AppNavDestination.DISCOVER
                }
                onSuccess()
            },
            onError = onError
        )
    }

    fun signOut(onComplete: () -> Unit = {}) {
        closePlayer()
        leaveLiveSession()
        _viewingProfileId.value = null
        _currentDestination.value = AppNavDestination.DISCOVER
        authManager.signOut {
            repository.syncAuthenticatedUserAccount(null)
            onComplete()
        }
    }

    fun deleteAccount(
        activity: Activity? = null,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val deletingUid = authManager.userId?.takeIf { it.isNotBlank() }
            ?: repository.authenticatedAccount.value?.uid?.takeIf { it.isNotBlank() }
            ?: repository.activeProfileId
        authManager.deleteAccount(
            activity = activity,
            onSuccess = {
                closePlayer()
                leaveLiveSession()
                if (isRecorderInitialized) {
                    voiceRecorderManager.stopRecording()
                }
                _isRecordingAudio.value = false
                val draftPaths = listOfNotNull(
                    _recordedFilePath.value,
                    _uploadedAudioInfo.value?.localFilePath,
                    _uploadedVideoInfo.value?.localFilePath,
                    _uploadedVideoInfo.value?.thumbnailPath
                )
                _recordedFilePath.value = null
                clearUploadedAudio()
                clearUploadedVideo()
                _viewingProfileId.value = null
                _currentDestination.value = AppNavDestination.DISCOVER
                _userRole.value = UserRole.SPECTATOR
                repository.removeDeletedUserContent(
                    deletedUid = deletingUid,
                    extraFilePaths = draftPaths
                )
                val deletedMsg = getApplication<Application>().getString(com.example.R.string.auth_account_deleted_snackbar)
                _authStatusMessage.value = deletedMsg
                showScaffoldMessage(deletedMsg)
                onSuccess()
            },
            onError = { msg ->
                showScaffoldMessage(msg)
                onError(msg)
            }
        )
    }

    fun toggleSoundEffects() {
        val next = !_isSoundEffectsEnabled.value
        _isSoundEffectsEnabled.value = next
        soundEffects.isSoundEnabled = next
    }

    fun navigateTo(destination: AppNavDestination) {
        _currentDestination.value = destination
    }

    fun setUserRole(role: UserRole) {
        _userRole.value = role
        if (authManager.isAuthenticated) {
            authManager.updateAccountRole(newRole = role)
        }
        if (role == UserRole.CREATOR && _currentDestination.value == AppNavDestination.DISCOVER) {
            _currentDestination.value = AppNavDestination.STUDIO
        } else if (role == UserRole.SPECTATOR && _currentDestination.value == AppNavDestination.STUDIO) {
            _currentDestination.value = AppNavDestination.DISCOVER
        }
    }

    private fun resolveEffectivePerformanceForPlayback(perf: Performance): Performance {
        val cached = offlineCachedTracks.value.find { it.performanceId == perf.id } ?: return perf
        val resolvedAudio = if (!perf.customAudioPath.isNullOrBlank() && !File(perf.customAudioPath).exists()) {
            cached.encryptedLocalAudioPath?.takeIf { File(it).exists() } ?: perf.customAudioPath
        } else {
            perf.customAudioPath
        }
        val resolvedVideo = if (!perf.customVideoPath.isNullOrBlank() && !File(perf.customVideoPath).exists()) {
            cached.encryptedLocalVideoPath?.takeIf { File(it).exists() } ?: perf.customVideoPath
        } else {
            perf.customVideoPath
        }
        return if (resolvedAudio != perf.customAudioPath || resolvedVideo != perf.customVideoPath) {
            perf.copy(customAudioPath = resolvedAudio, customVideoPath = resolvedVideo)
        } else {
            perf
        }
    }

    private fun shouldUseNativeVideoAudioOnly(perf: Performance): Boolean {
        val videoPath = perf.customVideoPath?.takeIf { it.isNotBlank() } ?: return false
        return perf.audioType == SynthAudioType.UPLOADED_VIDEO &&
            perf.customAudioPath.isNullOrBlank() &&
            File(videoPath).exists()
    }

    fun playPerformance(performanceId: String, expandFullscreen: Boolean = true) {
        val rawPerf = repository.getPerformanceById(performanceId) ?: return
        val perf = resolveEffectivePerformanceForPlayback(rawPerf)
        _activePerformance.value = perf
        _isPlayerExpanded.value = expandFullscreen
        repository.recordView(performanceId)
        if (pinnedPerformanceIds.value.contains(performanceId)) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.offlineCacheRepository?.recordOfflinePlayback(performanceId)
            }
        }
        _isPlaying.value = true
        _playbackProgressSec.value = 0

        if (shouldUseNativeVideoAudioOnly(perf)) {
            stopSynthIfRunning()
            _audioAmplitude.value = 0.35f
        } else {
            synthAudioPlayer.play(perf.audioType, perf.customAudioPath) { sec ->
                _playbackProgressSec.value = sec
            }
        }
    }

    fun onVideoProgressUpdated(progressSec: Int) {
        val perf = _activePerformance.value ?: return
        if (shouldUseNativeVideoAudioOnly(perf)) {
            _playbackProgressSec.value = progressSec.coerceAtLeast(0)
        }
    }

    fun expandPlayer() {
        if (_activePerformance.value != null) {
            _isPlayerExpanded.value = true
        }
    }

    fun minimizePlayer() {
        _isPlayerExpanded.value = false
    }

    fun showScaffoldMessage(message: String) {
        _scaffoldSnackbarMessage.value = message
    }

    fun consumeScaffoldMessage() {
        _scaffoldSnackbarMessage.value = null
    }

    fun togglePlayPause() {
        val rawPerf = _activePerformance.value ?: return
        val perf = resolveEffectivePerformanceForPlayback(rawPerf)
        if (_isPlaying.value) {
            stopSynthIfRunning()
            _isPlaying.value = false
        } else {
            _isPlaying.value = true
            if (shouldUseNativeVideoAudioOnly(perf)) {
                stopSynthIfRunning()
                _audioAmplitude.value = 0.35f
            } else {
                synthAudioPlayer.play(perf.audioType, perf.customAudioPath) { sec ->
                    _playbackProgressSec.value = sec
                }
            }
        }
    }

    fun closePlayer() {
        stopSynthIfRunning()
        _isPlaying.value = false
        _isPlayerExpanded.value = false
        _activePerformance.value = null
    }

    fun toggleLike(performanceId: String) {
        val wasLiked = repository.getPerformanceById(performanceId)?.isLikedByMe == true
        repository.toggleLike(performanceId)
        if (!wasLiked) {
            soundEffects.playLikeSound()
        }
        if (_activePerformance.value?.id == performanceId) {
            _activePerformance.value = repository.getPerformanceById(performanceId)
        }
    }

    fun cheerPerformance(performanceId: String, emoji: String) {
        repository.addCheer(performanceId)
        soundEffects.playCheerSound()
    }

    fun voteInPoll(pollId: String, candidateId: String) {
        repository.voteInPoll(pollId, candidateId)
        soundEffects.playPollVoteSound()
    }

    fun voteForPerformance(performanceId: String) {
        val wasVoted = activeVotingState.value.isVotedByCurrentUser(
            performanceId,
            fallback = repository.getPerformanceById(performanceId)?.isVotedByMe == true
        )
        repository.voteForPerformance(performanceId)
        if (!wasVoted) {
            soundEffects.playVoteSound()
        }
        if (_activePerformance.value?.id == performanceId) {
            _activePerformance.value = repository.getPerformanceById(performanceId)
        }
    }

    fun castPerformanceTierVote(performanceId: String, tier: VoteTier) {
        repository.castActivePerformanceVote(performanceId = performanceId, tier = tier)
        if (tier.weight >= VoteTier.SUPER_VOTE.weight) {
            soundEffects.playCheerSound()
        } else {
            soundEffects.playVoteSound()
        }
        if (_activePerformance.value?.id == performanceId) {
            _activePerformance.value = repository.getPerformanceById(performanceId)
        }
    }

    fun castSessionVote(
        sessionId: String,
        performerId: String,
        performerName: String,
        voteDelta: Int = 1,
        voteType: String = "VOTE"
    ) {
        repository.castSessionVote(
            sessionId = sessionId,
            performerId = performerId,
            performerName = performerName,
            voteDelta = voteDelta,
            voteType = voteType
        )
        if (voteDelta >= 5) {
            soundEffects.playCheerSound()
        } else {
            soundEffects.playVoteSound()
        }
    }

    fun observeSessionVotes(sessionId: String) = repository.observeSessionVotes(sessionId)

    fun observeStageVotingSnapshot(stageId: String) = repository.observeStageVotingSnapshot(stageId)

    fun castLeaderboardVote(ranking: CreatorRanking, voteDelta: Int = 1) {
        val tier = when {
            voteDelta >= 10 -> "STANDING_OVATION"
            voteDelta >= 5 -> "SUPER_VOTE"
            else -> "VOTE"
        }
        repository.castLeaderboardVote(ranking, voteDelta, tier)
        if (voteDelta >= 5) {
            soundEffects.playCheerSound()
        } else {
            soundEffects.playVoteSound()
        }
    }

    fun setLeaderboardRankingMode(mode: LeaderboardRankingMode) {
        _leaderboardRankingMode.value = mode
    }

    fun setLeaderboardCategory(category: LeaderboardCategoryFilter) {
        _leaderboardCategory.value = category
    }

    fun setLeaderboardTimePeriod(period: LeaderboardTimePeriod) {
        _leaderboardTimePeriod.value = period
    }

    // Profile navigation
    fun viewCreatorProfile(creatorId: String) {
        _viewingProfileId.value = creatorId
        _currentDestination.value = AppNavDestination.PROFILE
    }

    fun clearViewingProfile() {
        _viewingProfileId.value = null
    }

    fun updateMyProfile(
        artistName: String,
        stageTitle: String,
        bio: String,
        category: PerformanceCategory,
        spotify: String,
        instagram: String,
        youtube: String,
        website: String
    ) {
        repository.updateCurrentUserProfile(
            artistName = artistName,
            stageTitle = stageTitle,
            bio = bio,
            primaryCategory = category,
            socialSpotify = spotify,
            socialInstagram = instagram,
            socialYoutube = youtube,
            socialWebsite = website
        )
        if (authManager.isAuthenticated) {
            authManager.updateAccountRole(
                newRole = _userRole.value,
                stageName = artistName,
                stageTitle = stageTitle,
                bio = bio,
                category = category
            )
        }
    }

    fun rateCreator(creatorId: String, rating: Float) {
        repository.rateCreator(creatorId, rating)
        if (rating >= 4f) {
            soundEffects.playCheerSound()
        } else {
            soundEffects.playVoteSound()
        }
    }

    // Live Streaming
    fun joinLiveSession(session: LiveSession) {
        repository.joinLiveStageSession(session)
        val refreshed = repository.getLiveSessionById(session.id) ?: session
        _activeLiveSession.value = refreshed
        _currentDestination.value = AppNavDestination.LIVE
        // Play ambient live stream audio
        synthAudioPlayer.play(refreshed.audioType)
        _isPlaying.value = true
    }

    fun leaveLiveSession() {
        val prev = _activeLiveSession.value
        if (prev != null) {
            repository.leaveLiveStageSession(prev.id, isHost = prev.isCreatorMe)
        }
        stopSynthIfRunning()
        _isPlaying.value = false
        _activeLiveSession.value = null
    }

    fun startLiveSession(
        title: String,
        category: PerformanceCategory,
        genre: String,
        visualTheme: VisualizerTheme,
        audioType: SynthAudioType,
        pollId: String?
    ) {
        val session = repository.startLiveSession(title, category, genre, visualTheme, audioType, pollId)
        joinLiveSession(session)
    }

    fun stopLiveSession(sessionId: String) {
        repository.stopLiveSession(sessionId)
        stopSynthIfRunning()
        _isPlaying.value = false
        _activeLiveSession.value = null
    }

    fun sendLiveChatMessage(sessionId: String, text: String, isCheer: Boolean) {
        val session = repository.getLiveSessionById(sessionId) ?: _activeLiveSession.value
        val isHost = session?.isCreatorMe == true || session?.ownerUid == repository.activeProfileId
        if (session != null && session.isChatLocked && !isHost) {
            showScaffoldMessage("Stage host has paused spectator chat — use crowd reactions or voting!")
            return
        }
        if (!isCheer && session != null && session.slowModeSeconds > 0 && !isHost) {
            val now = System.currentTimeMillis()
            val prev = lastSpectatorChatSentAtMs[sessionId] ?: 0L
            val cooldownMs = session.slowModeSeconds * 1000L
            val remainingSec = ((cooldownMs - (now - prev) + 999L) / 1000L).toInt()
            if (now - prev < cooldownMs) {
                showScaffoldMessage("Slow Mode active — wait ${remainingSec.coerceAtLeast(1)}s before sending another message")
                return
            }
            lastSpectatorChatSentAtMs[sessionId] = now
        }
        val sender = repository.activeDisplayName
        val sent = repository.sendLiveChatMessage(sessionId, sender, text, isCheer)
        if (sent && isCheer) {
            soundEffects.playCheerSound()
        }
        _activeLiveSession.value = repository.getLiveSessionById(sessionId)
    }

    fun sendStageReaction(sessionId: String, reactionType: StageReactionType) {
        repository.sendLiveReaction(sessionId, reactionType, 1)
        soundEffects.playCheerSound()
        _activeLiveSession.value = repository.getLiveSessionById(sessionId)
    }

    fun toggleStageSlowMode(sessionId: String) {
        val session = repository.getLiveSessionById(sessionId) ?: _activeLiveSession.value ?: return
        val nextSeconds = if (session.slowModeSeconds > 0) 0 else 5
        repository.updateLiveModeration(sessionId = sessionId, slowModeSeconds = nextSeconds)
        _activeLiveSession.value = repository.getLiveSessionById(sessionId)
        showScaffoldMessage(
            if (nextSeconds > 0) "Stage Slow Mode enabled (${nextSeconds}s cooldown)"
            else "Stage Slow Mode turned off"
        )
    }

    fun toggleStageChatLock(sessionId: String) {
        val session = repository.getLiveSessionById(sessionId) ?: _activeLiveSession.value ?: return
        val nextLocked = !session.isChatLocked
        repository.updateLiveModeration(sessionId = sessionId, isChatLocked = nextLocked)
        _activeLiveSession.value = repository.getLiveSessionById(sessionId)
        showScaffoldMessage(
            if (nextLocked) "Spectator chat paused by host"
            else "Spectator chat unlocked"
        )
    }

    fun pinStageAnnouncement(sessionId: String, announcement: String?) {
        val clean = announcement?.trim().orEmpty()
        if (clean.isBlank()) {
            repository.updateLiveModeration(sessionId = sessionId, clearPinnedAnnouncement = true)
            showScaffoldMessage("Cleared pinned stage announcement")
        } else {
            repository.updateLiveModeration(sessionId = sessionId, pinnedAnnouncement = clean.take(240))
            showScaffoldMessage("Pinned announcement to live stage")
        }
        _activeLiveSession.value = repository.getLiveSessionById(sessionId)
    }

    fun toggleHideChatMessage(sessionId: String, messageId: String, hidden: Boolean) {
        repository.toggleHideLiveChatMessage(sessionId = sessionId, messageId = messageId, hidden = hidden)
        _activeLiveSession.value = repository.getLiveSessionById(sessionId)
        showScaffoldMessage(if (hidden) "Chat message hidden from spectators" else "Chat message restored")
    }

    fun observeLiveChatMessages(session: LiveSession): Flow<List<LiveChatMessage>> =
        repository.observeStageChatMessages(session)

    // Studio & Recording
    fun startRecordingVoice() {
        val path = voiceRecorderManager.startRecording()
        _isRecordingAudio.value = voiceRecorderManager.isRecording
        _recordedFilePath.value = path
        if (!path.isNullOrBlank()) {
            repository.trackUserMediaFiles(listOf(path))
        }
    }

    fun stopRecordingVoice() {
        val path = voiceRecorderManager.stopRecording()
        _isRecordingAudio.value = false
        _recordedFilePath.value = path
        if (!path.isNullOrBlank()) {
            repository.trackUserMediaFiles(listOf(path))
        }
    }

    fun previewSynthAudio(audioType: SynthAudioType) {
        synthAudioPlayer.play(audioType)
    }

    fun processUploadedAudio(uri: Uri, onProcessed: (UploadedAudioInfo) -> Unit = {}) {
        viewModelScope.launch {
            _isUploadingAudio.value = true
            _uploadError.value = null
            val info = withContext(Dispatchers.IO) {
                MusicUploadHelper.processAudioUri(getApplication(), uri)
            }
            _isUploadingAudio.value = false
            if (info != null) {
                repository.trackUserMediaFiles(listOf(info.localFilePath))
                _uploadedAudioInfo.value = info
                onProcessed(info)
            } else {
                _uploadError.value = "Unable to process selected audio file. Please try another format (MP3, WAV, M4A, FLAC)."
            }
        }
    }

    fun clearUploadedAudio() {
        stopSynthIfRunning()
        _uploadedAudioInfo.value = null
        _uploadError.value = null
    }

    fun processUploadedVideo(uri: Uri, onProcessed: (UploadedVideoInfo) -> Unit = {}) {
        viewModelScope.launch {
            _isUploadingVideo.value = true
            _videoUploadError.value = null
            val info = withContext(Dispatchers.IO) {
                VideoUploadHelper.processVideoUri(getApplication(), uri)
            }
            _isUploadingVideo.value = false
            if (info != null) {
                repository.trackUserMediaFiles(listOfNotNull(info.localFilePath, info.thumbnailPath))
                _uploadedVideoInfo.value = info
                onProcessed(info)
            } else {
                _videoUploadError.value = "Unable to process selected video file. Please try another MP4, MOV, or WebM file."
            }
        }
    }

    fun clearUploadedVideo() {
        _uploadedVideoInfo.value = null
        _videoUploadError.value = null
    }

    fun previewAudioFile(filePath: String) {
        synthAudioPlayer.play(SynthAudioType.UPLOADED_MUSIC, filePath)
    }

    fun stopAudioPreview() {
        stopSynthIfRunning()
    }

    fun publishPerformance(
        title: String,
        category: PerformanceCategory,
        genre: String,
        lyrics: String,
        visualTheme: VisualizerTheme,
        audioType: SynthAudioType,
        customAudioPath: String?,
        enrollInPollId: String?,
        durationSec: Int = 180,
        customVideoPath: String? = null
    ) {
        val created = repository.publishPerformance(
            title = title,
            category = category,
            genre = genre,
            lyricsOrScript = lyrics,
            visualizerTheme = visualTheme,
            audioType = audioType,
            customAudioPath = customAudioPath,
            enrollInPollId = enrollInPollId,
            durationSec = durationSec,
            customVideoPath = customVideoPath
        )
        // Reset studio states
        _recordedFilePath.value = null
        _uploadedAudioInfo.value = null
        _uploadedVideoInfo.value = null
        // Navigate to discover or profile to see newly published masterpiece
        playPerformance(created.id)
    }

    override fun onCleared() {
        super.onCleared()
        authManager.dispose()
        stopSynthIfRunning()
        if (isRecorderInitialized) {
            voiceRecorderManager.stopRecording()
        }
    }
}
