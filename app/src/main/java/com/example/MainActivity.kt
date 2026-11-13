package com.example

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ActiveVotingState
import com.example.model.AppNavDestination
import com.example.model.AuthAccountProvider
import com.example.model.AuthSessionState
import com.example.model.LiveSession
import com.example.model.Performance
import com.example.model.PerformanceVoteCount
import com.example.model.UserRole
import com.example.model.toLiveSession
import com.example.ui.MainViewModel
import com.example.ui.components.EncryptionVaultDialog
import com.example.ui.components.UserProfile
import com.example.ui.components.VersePulseScaffold
import com.example.ui.screens.AccountRoleSheetDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CreatorProfileScreen
import com.example.ui.screens.CreatorStudioScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LeaderboardScreen
import com.example.ui.screens.LiveStreamScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.PollsScreen
import com.example.ui.theme.MyApplicationTheme

import com.example.security.configureAppCheck

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            MyApplicationTheme {
                VersePulseApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun VersePulseApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val userAccount by viewModel.userAccount.collectAsStateWithLifecycle()
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val authStatusMessage by viewModel.authStatusMessage.collectAsStateWithLifecycle()

    // Root Composable Auth Gate: supports both Google Credential Manager sessions and
    // AES-256-GCM encrypted Standalone Creator / Spectator sessions without cold-start window blocking.
    val authenticatedSession = sessionState as? AuthSessionState.Authenticated
    if (authenticatedSession == null) {
        AuthScreen(
            sessionState = sessionState,
            isLoading = isAuthLoading,
            errorMessage = authError ?: (sessionState as? AuthSessionState.Unauthenticated)?.errorMessage,
            onDismissError = { viewModel.dismissAuthError() },
            onSignInWithGoogle = { preferredRole ->
                activity?.let { act ->
                    viewModel.signInWithGoogle(act, preferredRoleIfNew = preferredRole)
                }
            },
            onSignInWithGoogleExplicit = { preferredRole, isExplicitRoleSelection ->
                activity?.let { act ->
                    viewModel.signInWithGoogle(
                        activity = act,
                        preferredRoleIfNew = preferredRole,
                        applyExplicitRoleSelection = isExplicitRoleSelection
                    )
                }
            },
            onRegisterWithGoogle = { draft ->
                activity?.let { act ->
                    viewModel.registerWithGoogle(act, draft = draft)
                }
            },
            onCompleteRegistration = { draft ->
                viewModel.completeRegistration(draft)
            },
            onSignOut = { viewModel.signOut() },
            onSignInStandalone = { identifier, password, explicitRole ->
                viewModel.signInStandalone(
                    identifier = identifier,
                    password = password,
                    explicitRole = explicitRole
                )
            },
            onRegisterStandalone = { draft, identifier, password ->
                viewModel.registerStandalone(
                    draft = draft,
                    identifier = identifier,
                    password = password
                )
            },
            statusMessage = authStatusMessage,
            onDismissStatus = { viewModel.dismissAuthStatus() }
        )
        return
    }

    val activeAccount = userAccount ?: authenticatedSession.account
    val authenticatedUid = currentUser?.uid ?: activeAccount.uid.ifBlank { "standalone_arena_member" }
    var showAccountSheet by remember(authenticatedUid) { mutableStateOf(false) }
    var showEncryptionVaultDialog by remember(authenticatedUid) { mutableStateOf(false) }

    key(authenticatedUid) {
        val currentDestination by viewModel.currentDestination.collectAsStateWithLifecycle()
        val userRole by viewModel.userRole.collectAsStateWithLifecycle()
        val activePerformance by viewModel.activePerformance.collectAsStateWithLifecycle()
        val isPlayerExpanded by viewModel.isPlayerExpanded.collectAsStateWithLifecycle()
        val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
        val snackbarMessage by viewModel.scaffoldSnackbarMessage.collectAsStateWithLifecycle()

        val liveSessions by viewModel.liveSessions.collectAsStateWithLifecycle()
        val activeLiveSession by viewModel.activeLiveSession.collectAsStateWithLifecycle()
        val viewingProfileId by viewModel.viewingProfileId.collectAsStateWithLifecycle()
        val activeVotingState by viewModel.activeVotingState.collectAsStateWithLifecycle()
        val activePlayerVoteState by viewModel.activePlayerVoteState.collectAsStateWithLifecycle()
        val isSoundEffectsEnabled by viewModel.isSoundEffectsEnabled.collectAsStateWithLifecycle()
        val isEncryptionEnabled by viewModel.isEncryptionEnabled.collectAsStateWithLifecycle()
        val encryptionStatus by viewModel.encryptionStatusSnapshot.collectAsStateWithLifecycle()

        // Predictive / clean Back navigation across overlays and secondary destinations
        BackHandler(
            enabled = (activePerformance != null && isPlayerExpanded) ||
                viewingProfileId != null ||
                activeLiveSession != null ||
                currentDestination != AppNavDestination.DISCOVER
        ) {
            when {
                activePerformance != null && isPlayerExpanded -> {
                    viewModel.minimizePlayer()
                }
                activeLiveSession != null -> {
                    viewModel.leaveLiveSession()
                }
                viewingProfileId != null -> {
                    viewModel.clearViewingProfile()
                    viewModel.navigateTo(AppNavDestination.DISCOVER)
                }
                currentDestination != AppNavDestination.DISCOVER -> {
                    viewModel.navigateTo(AppNavDestination.DISCOVER)
                }
            }
        }

        val resolvedAccountName = activeAccount.effectiveName
            .ifBlank { currentUser?.displayName.orEmpty() }
        val resolvedAccountPhotoUrl = activeAccount.photoUrl
            ?: currentUser?.photoUrl?.toString()
        val resolvedAccountEmail = activeAccount.email
            ?: currentUser?.email

        VersePulseScaffold(
            currentDestination = currentDestination,
            userRole = userRole,
            accountDisplayName = resolvedAccountName,
            activeLiveCount = liveSessions.count { it.isLive },
            accountPhotoUrl = resolvedAccountPhotoUrl,
            totalActiveVotes = activeVotingState.totalActiveVotes,
            isSoundEffectsEnabled = isSoundEffectsEnabled,
            isEncryptionEnabled = isEncryptionEnabled,
            activePerformance = activePerformance,
            activePerformanceVoteState = activePlayerVoteState,
            isPlaying = isPlaying,
            isPlayerExpanded = isPlayerExpanded,
            snackbarMessage = snackbarMessage,
            onSnackbarConsumed = { viewModel.consumeScaffoldMessage() },
            onNavigate = { viewModel.navigateTo(it) },
            onProfileTabSelected = {
                viewModel.viewCreatorProfile(viewModel.repository.activeProfileId)
            },
            onToggleSoundEffects = { viewModel.toggleSoundEffects() },
            onOpenEncryptionVault = { showEncryptionVaultDialog = true },
            onToggleUserRole = {
                val nextRole = if (userRole == UserRole.SPECTATOR) UserRole.CREATOR else UserRole.SPECTATOR
                viewModel.setUserRole(nextRole)
            },
            onOpenAccountSheet = { showAccountSheet = true },
            onCreateTrackClicked = { viewModel.navigateTo(AppNavDestination.STUDIO) },
            onMiniPlayerTogglePlayPause = { viewModel.togglePlayPause() },
            onMiniPlayerVote = { perfId -> viewModel.voteForPerformance(perfId) },
            onMiniPlayerExpand = { viewModel.expandPlayer() },
            onMiniPlayerClose = { viewModel.closePlayer() },
            fullscreenOverlay = {
                AnimatedVisibility(
                    visible = activePerformance != null && isPlayerExpanded,
                    enter = slideInVertically(initialOffsetY = { it }),
                    exit = slideOutVertically(targetOffsetY = { it })
                ) {
                    activePerformance?.let { perf ->
                        PlayerOverlayContent(
                            viewModel = viewModel,
                            performance = perf,
                            isPlaying = isPlaying,
                            activePlayerVoteState = activePlayerVoteState
                        )
                    }
                }
            }
        ) {
            when (currentDestination) {
                AppNavDestination.DISCOVER -> {
                    DiscoverTabContent(
                        viewModel = viewModel,
                        liveSessions = liveSessions,
                        activePerformance = activePerformance,
                        isPlayerExpanded = isPlayerExpanded,
                        isPlaying = isPlaying,
                        activeVotingState = activeVotingState
                    )
                }

                AppNavDestination.LIVE -> {
                    LiveTabContent(
                        viewModel = viewModel,
                        liveSessions = liveSessions,
                        activeLiveSession = activeLiveSession,
                        isPlaying = isPlaying,
                        userRole = userRole
                    )
                }

                AppNavDestination.POLLS -> {
                    PollsTabContent(viewModel = viewModel)
                }

                AppNavDestination.LEADERBOARD -> {
                    LeaderboardTabContent(
                        viewModel = viewModel,
                        activity = activity,
                        isAuthenticated = true
                    )
                }

                AppNavDestination.STUDIO -> {
                    StudioTabContent(
                        viewModel = viewModel,
                        isPlaying = isPlaying,
                        isEncryptionEnabled = isEncryptionEnabled,
                        onOpenEncryptionVault = { showEncryptionVaultDialog = true }
                    )
                }

                AppNavDestination.PROFILE -> {
                    ProfileTabContent(
                        viewModel = viewModel,
                        activity = activity,
                        viewingProfileId = viewingProfileId,
                        liveSessions = liveSessions,
                        resolvedAccountEmail = resolvedAccountEmail,
                        isStandaloneAccount = activeAccount.authProvider == AuthAccountProvider.STANDALONE,
                        accountRole = userRole,
                        isDeletingAccount = isAuthLoading,
                        encryptionStatus = encryptionStatus,
                        onOpenEncryptionVault = { showEncryptionVaultDialog = true }
                    )
                }
            }
        }

        if (showAccountSheet) {
            AccountRoleSheetDialog(
                account = activeAccount,
                emailFallback = resolvedAccountEmail,
                displayNameFallback = currentUser?.displayName,
                currentRole = userRole,
                onSwitchRole = { nextRole -> viewModel.setUserRole(nextRole) },
                onViewMyProfile = {
                    viewModel.viewCreatorProfile(viewModel.repository.activeProfileId)
                },
                onSignOut = { viewModel.signOut() },
                onDismiss = { showAccountSheet = false },
                onSwitchGoogleAccount = {
                    activity?.let { act -> viewModel.switchGoogleAccount(act) }
                },
                isEncryptionEnabled = isEncryptionEnabled,
                onToggleEncryption = { enabled -> viewModel.setEncryptionEnabled(enabled) },
                onOpenEncryptionVault = { showEncryptionVaultDialog = true },
                isDeletingAccount = isAuthLoading,
                onDeleteAccount = { viewModel.deleteAccount(activity) }
            )
        }

        if (showEncryptionVaultDialog) {
            EncryptionVaultDialog(
                status = encryptionStatus,
                onToggleEncryption = { enabled -> viewModel.setEncryptionEnabled(enabled) },
                onVerifyAndEncryptVault = { viewModel.verifyAndEncryptVault() },
                onAdvanceRolloutStage = { viewModel.advanceAppCheckRolloutStage() },
                onDismiss = { showEncryptionVaultDialog = false }
            )
        }
    }
}

@Composable
private fun PlayerOverlayContent(
    viewModel: MainViewModel,
    performance: Performance,
    isPlaying: Boolean,
    activePlayerVoteState: PerformanceVoteCount?
) {
    val playbackProgress by viewModel.playbackProgressSec.collectAsStateWithLifecycle()
    val audioAmplitude by viewModel.audioAmplitude.collectAsStateWithLifecycle()
    val pinnedPerformanceIds by viewModel.pinnedPerformanceIds.collectAsStateWithLifecycle()

    PlayerScreen(
        performance = performance,
        isPlaying = isPlaying,
        progressSeconds = playbackProgress,
        audioAmplitude = audioAmplitude,
        attachedPoll = performance.pollId?.let { viewModel.repository.getPollById(it) },
        performanceVoteState = activePlayerVoteState,
        isPinnedOffline = pinnedPerformanceIds.contains(performance.id),
        onToggleOfflinePin = { viewModel.toggleOfflinePin(performance) },
        onClose = { viewModel.minimizePlayer() },
        onTogglePlayPause = { viewModel.togglePlayPause() },
        onToggleLike = { viewModel.toggleLike(it) },
        onToggleVote = { viewModel.voteForPerformance(it) },
        onCastTierVote = { perfId, tier ->
            viewModel.castPerformanceTierVote(perfId, tier)
        },
        onCheerReaction = { emoji -> viewModel.cheerPerformance(performance.id, emoji) },
        onVoteInPoll = { pollId, candidateId ->
            viewModel.voteInPoll(pollId, candidateId)
        },
        onCreatorClicked = { creatorId ->
            viewModel.minimizePlayer()
            viewModel.viewCreatorProfile(creatorId)
        },
        onVideoProgressChanged = { sec ->
            viewModel.onVideoProgressUpdated(sec)
        }
    )
}

@Composable
private fun DiscoverTabContent(
    viewModel: MainViewModel,
    liveSessions: List<LiveSession>,
    activePerformance: Performance?,
    isPlayerExpanded: Boolean,
    isPlaying: Boolean,
    activeVotingState: ActiveVotingState
) {
    val performances by viewModel.performances.collectAsStateWithLifecycle()
    val polls by viewModel.polls.collectAsStateWithLifecycle()
    val pinnedPerformanceIds by viewModel.pinnedPerformanceIds.collectAsStateWithLifecycle()
    val activeLive = remember(liveSessions) { liveSessions.filter { it.isLive } }

    DiscoverScreen(
        performances = performances,
        featuredPoll = polls.firstOrNull(),
        activeLiveSessions = activeLive,
        activePerformanceId = activePerformance?.id,
        isPlaying = isPlaying,
        activeVotingState = activeVotingState,
        pinnedPerformanceIds = pinnedPerformanceIds,
        onToggleOfflinePin = { viewModel.toggleOfflinePin(it) },
        onPlayPerformance = { perfId ->
            if (activePerformance?.id == perfId) {
                if (!isPlayerExpanded) {
                    viewModel.expandPlayer()
                } else {
                    viewModel.togglePlayPause()
                }
            } else {
                viewModel.playPerformance(perfId)
            }
        },
        onToggleLike = { viewModel.toggleLike(it) },
        onVoteInPoll = { pollId, candidateId -> viewModel.voteInPoll(pollId, candidateId) },
        onVoteForPerformance = { viewModel.voteForPerformance(it) },
        onCastTierVote = { perfId, tier -> viewModel.castPerformanceTierVote(perfId, tier) },
        onCheerPerformance = { viewModel.cheerPerformance(it, "🔥") },
        onCreatorProfileClicked = { viewModel.viewCreatorProfile(it) },
        onNavigateToPolls = { viewModel.navigateTo(AppNavDestination.POLLS) },
        onNavigateToLive = { viewModel.navigateTo(AppNavDestination.LIVE) },
        onJoinLiveEvent = { event ->
            viewModel.joinLiveSession(event.toLiveSession())
        },
        onJoinLiveSession = { session ->
            viewModel.joinLiveSession(session)
        }
    )
}

@Composable
private fun LiveTabContent(
    viewModel: MainViewModel,
    liveSessions: List<LiveSession>,
    activeLiveSession: LiveSession?,
    isPlaying: Boolean,
    userRole: UserRole
) {
    val performances by viewModel.performances.collectAsStateWithLifecycle()
    val audioAmplitude by viewModel.audioAmplitude.collectAsStateWithLifecycle()
    val chatMessagesFlow = remember(activeLiveSession?.id) {
        activeLiveSession?.let { viewModel.observeLiveChatMessages(it) }
    }

    LiveStreamScreen(
        liveSessions = liveSessions,
        activeSession = activeLiveSession,
        attachedPoll = activeLiveSession?.activePollId?.let { viewModel.repository.getPollById(it) },
        audioAmplitude = audioAmplitude,
        isPlayingLiveAudio = isPlaying,
        userRole = userRole,
        performers = performances,
        sessionVotesFlow = activeLiveSession?.id?.let { viewModel.observeSessionVotes(it) },
        stageVotingSnapshotFlow = activeLiveSession?.id?.let { viewModel.observeStageVotingSnapshot(it) },
        chatMessagesFlow = chatMessagesFlow,
        onJoinSession = { viewModel.joinLiveSession(it) },
        onLeaveSession = { viewModel.leaveLiveSession() },
        onStartNewLive = { title, cat, genre, viz, audio, pollId ->
            viewModel.startLiveSession(title, cat, genre, viz, audio, pollId)
        },
        onStopLiveSession = { viewModel.stopLiveSession(it) },
        onSendChatMessage = { sessionId, text, isCheer ->
            viewModel.sendLiveChatMessage(sessionId, text, isCheer)
        },
        onVoteInPoll = { pollId, candidateId -> viewModel.voteInPoll(pollId, candidateId) },
        onCastSessionVote = { sessionId, performerId, performerName, delta, type ->
            viewModel.castSessionVote(sessionId, performerId, performerName, delta, type)
        },
        onPlayCandidatePreview = { viewModel.playPerformance(it) },
        onSendReaction = { reaction ->
            activeLiveSession?.id?.let { sessionId ->
                viewModel.sendStageReaction(sessionId, reaction)
            }
        },
        onToggleSlowMode = {
            activeLiveSession?.id?.let { viewModel.toggleStageSlowMode(it) }
        },
        onToggleChatLock = {
            activeLiveSession?.id?.let { viewModel.toggleStageChatLock(it) }
        },
        onPinAnnouncement = { announcement ->
            activeLiveSession?.id?.let { viewModel.pinStageAnnouncement(it, announcement) }
        },
        onToggleHideMessage = { messageId, hidden ->
            activeLiveSession?.id?.let { viewModel.toggleHideChatMessage(it, messageId, hidden) }
        }
    )
}

@Composable
private fun PollsTabContent(viewModel: MainViewModel) {
    val polls by viewModel.polls.collectAsStateWithLifecycle()

    PollsScreen(
        polls = polls,
        onVote = { pollId, candidateId -> viewModel.voteInPoll(pollId, candidateId) },
        onPlayPerformance = { viewModel.playPerformance(it) }
    )
}

@Composable
private fun LeaderboardTabContent(
    viewModel: MainViewModel,
    activity: Activity?,
    isAuthenticated: Boolean
) {
    val leaderboardRankings by viewModel.leaderboardRankings.collectAsStateWithLifecycle()
    val votesAggregation by viewModel.votesAggregation.collectAsStateWithLifecycle()
    val leaderboardUiState by viewModel.leaderboardUiState.collectAsStateWithLifecycle()
    val leaderboardRankingMode by viewModel.leaderboardRankingMode.collectAsStateWithLifecycle()
    val globalRatingsArenaSummary by viewModel.globalRatingsArenaSummary.collectAsStateWithLifecycle()
    val userSubmittedRatings by viewModel.userSubmittedRatings.collectAsStateWithLifecycle()
    val leaderboardCategory by viewModel.leaderboardCategory.collectAsStateWithLifecycle()
    val leaderboardPeriod by viewModel.leaderboardTimePeriod.collectAsStateWithLifecycle()
    val creatorProfiles by viewModel.creatorProfiles.collectAsStateWithLifecycle()

    LeaderboardScreen(
        rankings = leaderboardRankings,
        selectedCategory = leaderboardCategory,
        selectedTimePeriod = leaderboardPeriod,
        onCategoryChanged = { viewModel.setLeaderboardCategory(it) },
        onTimePeriodChanged = { viewModel.setLeaderboardTimePeriod(it) },
        onPlayPerformance = { viewModel.playPerformance(it) },
        onCreatorClicked = { creatorId, _ -> viewModel.viewCreatorProfile(creatorId) },
        selectedRankingMode = leaderboardRankingMode,
        onRankingModeChanged = { viewModel.setLeaderboardRankingMode(it) },
        arenaSummary = globalRatingsArenaSummary,
        userSubmittedRatings = userSubmittedRatings,
        onRateCreator = { creatorId, stars ->
            viewModel.rateCreator(creatorId, stars)
            val ratedName = creatorProfiles[creatorId]?.artistName ?: "Creator"
            viewModel.showScaffoldMessage("Rated $ratedName ${stars.toInt()}★ — Global cumulative standings updated!")
        },
        votesAggregation = votesAggregation,
        uiState = leaderboardUiState,
        isAuthenticated = isAuthenticated,
        onCastVoteForCreator = { ranking -> viewModel.castLeaderboardVote(ranking, 1) },
        onSignInClick = {
            activity?.let { act -> viewModel.signInWithGoogle(act) }
        }
    )
}

@Composable
private fun StudioTabContent(
    viewModel: MainViewModel,
    isPlaying: Boolean,
    isEncryptionEnabled: Boolean,
    onOpenEncryptionVault: () -> Unit
) {
    val polls by viewModel.polls.collectAsStateWithLifecycle()
    val isRecordingAudio by viewModel.isRecordingAudio.collectAsStateWithLifecycle()
    val recordAmplitude by viewModel.recordAmplitude.collectAsStateWithLifecycle()
    val recordedFilePath by viewModel.recordedFilePath.collectAsStateWithLifecycle()
    val uploadedAudio by viewModel.uploadedAudioInfo.collectAsStateWithLifecycle()
    val isUploadingAudio by viewModel.isUploadingAudio.collectAsStateWithLifecycle()
    val uploadError by viewModel.uploadError.collectAsStateWithLifecycle()
    val uploadedVideo by viewModel.uploadedVideoInfo.collectAsStateWithLifecycle()
    val isUploadingVideo by viewModel.isUploadingVideo.collectAsStateWithLifecycle()
    val videoUploadError by viewModel.videoUploadError.collectAsStateWithLifecycle()
    val audioAmplitude by viewModel.audioAmplitude.collectAsStateWithLifecycle()

    CreatorStudioScreen(
        availablePolls = polls,
        isRecordingAudio = isRecordingAudio,
        recordAmplitude = recordAmplitude,
        recordedFilePath = recordedFilePath,
        uploadedAudio = uploadedAudio,
        isUploadingAudio = isUploadingAudio,
        uploadError = uploadError,
        uploadedVideo = uploadedVideo,
        isUploadingVideo = isUploadingVideo,
        videoUploadError = videoUploadError,
        previewAudioAmplitude = audioAmplitude,
        isPlayingPreview = isPlaying,
        isEncryptionEnabled = isEncryptionEnabled,
        onToggleEncryption = { enabled -> viewModel.setEncryptionEnabled(enabled) },
        onOpenEncryptionVault = onOpenEncryptionVault,
        onStartRecording = { viewModel.startRecordingVoice() },
        onStopRecording = { viewModel.stopRecordingVoice() },
        onAudioFileSelected = { viewModel.processUploadedAudio(it) },
        onClearUploadedAudio = { viewModel.clearUploadedAudio() },
        onVideoFileSelected = { viewModel.processUploadedVideo(it) },
        onClearUploadedVideo = { viewModel.clearUploadedVideo() },
        onPreviewAudioFile = { viewModel.previewAudioFile(it) },
        onStopAudioPreview = { viewModel.stopAudioPreview() },
        onPreviewSynthAudio = { viewModel.previewSynthAudio(it) },
        onPublishWithVideo = { title, cat, genre, lyrics, viz, audio, audioPath, videoPath, pollId, duration ->
            viewModel.publishPerformance(
                title = title,
                category = cat,
                genre = genre,
                lyrics = lyrics,
                visualTheme = viz,
                audioType = audio,
                customAudioPath = audioPath,
                enrollInPollId = pollId,
                durationSec = duration,
                customVideoPath = videoPath
            )
        }
    )
}

@Composable
private fun ProfileTabContent(
    viewModel: MainViewModel,
    activity: Activity?,
    viewingProfileId: String?,
    liveSessions: List<LiveSession>,
    resolvedAccountEmail: String?,
    isStandaloneAccount: Boolean,
    accountRole: UserRole,
    isDeletingAccount: Boolean,
    encryptionStatus: com.example.security.UploadEncryptionManager.EncryptionStatusSnapshot,
    onOpenEncryptionVault: () -> Unit
) {
    val leaderboardRankings by viewModel.leaderboardRankings.collectAsStateWithLifecycle()
    val creatorProfiles by viewModel.creatorProfiles.collectAsStateWithLifecycle()
    val performances by viewModel.performances.collectAsStateWithLifecycle()
    val offlineCachedTracks by viewModel.offlineCachedTracks.collectAsStateWithLifecycle()
    val pinnedPerformanceIds by viewModel.pinnedPerformanceIds.collectAsStateWithLifecycle()

    val myProfileId = viewModel.repository.activeProfileId
    val profileId = viewingProfileId ?: myProfileId
    val isOtherCreator = viewingProfileId != null &&
        viewingProfileId != myProfileId &&
        viewingProfileId != viewModel.repository.currentUserId
    val rank = leaderboardRankings.find { it.creatorId == profileId }?.rank

    if (isOtherCreator) {
        UserProfile(
            creatorId = profileId,
            leaderboardRank = rank,
            onBack = {
                viewModel.clearViewingProfile()
                viewModel.navigateTo(AppNavDestination.LEADERBOARD)
            },
            onPlayTrack = { viewModel.playPerformance(it) },
            onUploadClick = { viewModel.navigateTo(AppNavDestination.STUDIO) },
            onRateCreator = { viewModel.rateCreator(profileId, it) }
        )
    } else {
        val profile = creatorProfiles[myProfileId]
            ?: viewModel.repository.getCreatorProfile(myProfileId)
        val portfolio = remember(performances, myProfileId) {
            performances.filter { it.artistId == myProfileId }
        }
        val mySessionStats = remember(liveSessions, myProfileId) {
            viewModel.repository.getCreatorSessionStats(myProfileId)
        }

        CreatorProfileScreen(
            profile = profile,
            portfolio = portfolio,
            leaderboardRank = rank,
            sessionStats = mySessionStats,
            onBack = null,
            onPlayPerformance = { viewModel.playPerformance(it) },
            onRateCreator = { viewModel.rateCreator(myProfileId, it) },
            onSaveProfile = { name, title, bio, cat, spot, insta, yt, web ->
                viewModel.updateMyProfile(name, title, bio, cat, spot, insta, yt, web)
            },
            onNavigateToStudio = { viewModel.navigateTo(AppNavDestination.STUDIO) },
            accountEmail = resolvedAccountEmail,
            isStandaloneAccount = isStandaloneAccount,
            accountRole = accountRole,
            isDeletingAccount = isDeletingAccount,
            onSwitchGoogleAccount = {
                activity?.let { act -> viewModel.switchGoogleAccount(act) }
            },
            onSignOut = { viewModel.signOut() },
            onDeleteAccount = { viewModel.deleteAccount(activity) },
            encryptionStatus = encryptionStatus,
            onToggleEncryption = { enabled -> viewModel.setEncryptionEnabled(enabled) },
            onVerifyAndEncryptVault = { viewModel.verifyAndEncryptVault() },
            onOpenEncryptionVault = onOpenEncryptionVault,
            offlineCachedTracks = offlineCachedTracks,
            pinnedPerformanceIds = pinnedPerformanceIds,
            onToggleOfflinePin = { viewModel.toggleOfflinePin(it) },
            onUnpinOfflineTrack = { viewModel.unpinOfflineTrack(it) },
            onClearOfflineCache = { viewModel.clearOfflineCache() }
        )
    }
}
