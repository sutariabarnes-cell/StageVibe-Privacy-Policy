package com.example.data

import com.example.model.ActiveVotingState
import com.example.model.CreatorCompetitiveTier
import com.example.model.CreatorProfile
import com.example.model.CreatorRanking
import com.example.model.CreatorSessionStats
import com.example.model.GlobalRatingsArenaSummary
import com.example.model.LeaderboardCategoryFilter
import com.example.model.LeaderboardRankingMode
import com.example.model.LeaderboardTimePeriod
import com.example.model.LiveChatMessage
import com.example.model.LiveSession
import com.example.model.Performance
import com.example.model.PerformanceAnalyticsEngine
import com.example.model.PerformanceCategory
import com.example.model.PerformanceStage
import com.example.model.PerformanceVoteCount
import com.example.model.Performer
import com.example.model.Poll
import com.example.model.PollCandidate
import com.example.model.StageVotingCounter
import com.example.model.StageVotingSnapshot
import com.example.model.SynthAudioType
import com.example.model.UserAccount
import com.example.model.VisualizerTheme
import com.example.model.VoteRecord
import com.example.model.VoteTier
import com.example.model.VotesAggregationSnapshot
import com.example.model.toPerformer
import kotlin.math.roundToInt
import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.data.local.OfflinePlaybackCacheRepository
import com.example.model.StageReactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

class PerformanceRepository(private val context: Context? = null) {

    private val firestoreDataSource: FirestoreDataSource? by lazy {
        context?.let {
            try {
                FirestoreDataSource(it.applicationContext)
            } catch (_: Exception) {
                null
            }
        }
    }
    val firestoreService: FirestoreService? by lazy {
        context?.let {
            try {
                FirestoreService(it.applicationContext)
            } catch (_: Exception) {
                null
            }
        }
    }
    val votesRepository: VotesLeaderboardRepository? by lazy {
        context?.let {
            try {
                VotesLeaderboardRepository(it.applicationContext)
            } catch (_: Exception) {
                null
            }
        }
    }
    val liveEventsRepository: LiveEventsRepository? by lazy {
        context?.let {
            try {
                LiveEventsRepository(it.applicationContext)
            } catch (_: Exception) {
                null
            }
        }
    }
    val liveStageVotingService: LiveStageVotingService? by lazy {
        context?.let {
            try {
                LiveStageVotingService(it.applicationContext)
            } catch (_: Exception) {
                null
            }
        }
    }
    val offlineCacheRepository: OfflinePlaybackCacheRepository? by lazy {
        context?.let {
            try {
                OfflinePlaybackCacheRepository(it.applicationContext)
            } catch (_: Exception) {
                null
            }
        }
    }
    private val repoScope = CoroutineScope(Dispatchers.Default)

    private val _performances = MutableStateFlow<List<Performance>>(getInitialPerformances())
    val performances: StateFlow<List<Performance>> = _performances.asStateFlow()

    private val _performers = MutableStateFlow<Map<String, Performer>>(emptyMap())
    val performers: StateFlow<Map<String, Performer>> = _performers.asStateFlow()

    private val _polls = MutableStateFlow<List<Poll>>(getInitialPolls())
    val polls: StateFlow<List<Poll>> = _polls.asStateFlow()

    private val _liveSessions = MutableStateFlow<List<LiveSession>>(getInitialLiveSessions())
    val liveSessions: StateFlow<List<LiveSession>> = _liveSessions.asStateFlow()

    private val _creatorProfiles = MutableStateFlow<Map<String, CreatorProfile>>(getInitialCreatorProfiles())
    val creatorProfiles: StateFlow<Map<String, CreatorProfile>> = _creatorProfiles.asStateFlow()

    private val _sessionVotes = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())
    val sessionVotes: StateFlow<Map<String, Map<String, Int>>> = _sessionVotes.asStateFlow()

    private val _cloudStages = MutableStateFlow<Map<String, PerformanceStage>>(emptyMap())
    val cloudStages: StateFlow<Map<String, PerformanceStage>> = _cloudStages.asStateFlow()

    private val _localStageCounters = MutableStateFlow<Map<String, Map<String, StageVotingCounter>>>(emptyMap())
    val localStageCounters: StateFlow<Map<String, Map<String, StageVotingCounter>>> = _localStageCounters.asStateFlow()

    // Real-time stream of documents from the Firestore 'votes' collection
    private val _cloudVoteRecords = MutableStateFlow<List<VoteRecord>>(emptyList())
    val cloudVoteRecords: StateFlow<List<VoteRecord>> = _cloudVoteRecords.asStateFlow()

    // Session-local votes recorded while signed out or optimistic pending confirmation
    private val _localVoteRecords = MutableStateFlow<List<VoteRecord>>(emptyList())
    val localVoteRecords: StateFlow<List<VoteRecord>> = _localVoteRecords.asStateFlow()

    private val _isVotesStreamActive = MutableStateFlow(false)
    val isVotesStreamActive: StateFlow<Boolean> = _isVotesStreamActive.asStateFlow()

    private val _leaderboardError = MutableStateFlow<String?>(null)
    val leaderboardError: StateFlow<String?> = _leaderboardError.asStateFlow()

    // Tracks audience star ratings submitted by the current user during the session (creatorId -> 1..5 stars)
    private val _userSubmittedRatings = MutableStateFlow<Map<String, Int>>(emptyMap())
    val userSubmittedRatings: StateFlow<Map<String, Int>> = _userSubmittedRatings.asStateFlow()

    private val _sessionRatingStarsDelta = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val _sessionRatingCountDelta = MutableStateFlow<Map<String, Int>>(emptyMap())

    val activeVotingManager = ActivePerformanceVotingManager()
    val activeVotingState: StateFlow<ActiveVotingState> = activeVotingManager.votingState
    val activePerformanceVoteCounts: StateFlow<Map<String, Int>> = activeVotingManager.voteCounts

    private var cloudStreamsJob: Job? = null
    private var votesListenerJob: Job? = null
    private val userMediaPathsLock = Any()
    private val userUploadedMediaPaths = HashMap<String, MutableSet<String>>()

    // Fallback local creator ID when unauthenticated; dynamically resolves to Firebase UID when signed in
    val currentUserId = "creator_user_me"
    val currentUserName = "You (Stage Master)"

    private val _authenticatedAccount = MutableStateFlow<UserAccount?>(null)
    val authenticatedAccount: StateFlow<UserAccount?> = _authenticatedAccount.asStateFlow()

    val activeProfileId: String
        get() = _authenticatedAccount.value?.uid?.takeIf { it.isNotBlank() } ?: activeVoterUid()

    val activeDisplayName: String
        get() = _authenticatedAccount.value?.effectiveName?.takeIf { it.isNotBlank() } ?: activeVoterDisplayName()

    init {
        // Purely in-memory initialization with zero synchronous Firebase/IPC calls on the UI thread
        activeVotingManager.syncActivePerformances(_performances.value, currentUserId)
        repoScope.launch {
            activeVotingState.collect { votingSnapshot ->
                if (votingSnapshot.activePerformances.isNotEmpty()) {
                    _performances.update { list ->
                        list.map { perf ->
                            val voteEntry = votingSnapshot.activePerformances[perf.id]
                            if (voteEntry != null &&
                                (perf.votesCount != voteEntry.totalVotes || perf.isVotedByMe != voteEntry.isVotedByCurrentUser)
                            ) {
                                perf.copy(
                                    votesCount = voteEntry.totalVotes,
                                    isVotedByMe = voteEntry.isVotedByCurrentUser
                                )
                            } else {
                                perf
                            }
                        }
                    }
                }
            }
        }
        // Defer Firebase Auth listener registration and strictly auth-gate Firestore instantiation
        // until after cold-start frame rendering & SurfaceSyncGroup handoff complete.
        if (context != null) {
            repoScope.launch {
                kotlinx.coroutines.delay(400L)
                try {
                    val firebaseAuth = Firebase.auth
                    firebaseAuth.addAuthStateListener(
                        FirebaseAuth.AuthStateListener { authInstance ->
                            val signedInUser = authInstance.currentUser
                            cloudStreamsJob?.cancel()
                            votesListenerJob?.cancel()
                            if (signedInUser != null) {
                                val uid = signedInUser.uid
                                activeVotingManager.setCurrentUser(uid)
                                _leaderboardError.value = null
                                cloudStreamsJob = repoScope.launch {
                                    firestoreDataSource?.let { ds ->
                                        launch {
                                            ds.observeCreatorProfiles().collect { cloudProfiles ->
                                                if (cloudProfiles.isNotEmpty()) {
                                                    _creatorProfiles.update { current -> current + cloudProfiles }
                                                }
                                            }
                                        }
                                        launch {
                                            ds.observeLiveSessions().collect { cloudSessions ->
                                                if (cloudSessions.isNotEmpty()) {
                                                    _liveSessions.update { current ->
                                                        val localMap = current.associateBy { it.id }.toMutableMap()
                                                        cloudSessions.forEach { cloudSession ->
                                                            val existing = localMap[cloudSession.id]
                                                            val merged = if (existing != null) {
                                                                val mergedReactions = if (cloudSession.reactionCounts.isNotEmpty()) {
                                                                    cloudSession.reactionCounts
                                                                } else {
                                                                    existing.reactionCounts
                                                                }
                                                                cloudSession.copy(
                                                                    chatMessages = existing.chatMessages,
                                                                    isCreatorMe = cloudSession.isCreatorMe ||
                                                                        cloudSession.ownerUid == uid ||
                                                                        cloudSession.creatorId == uid ||
                                                                        existing.isCreatorMe,
                                                                    reactionCounts = mergedReactions
                                                                )
                                                            } else {
                                                                cloudSession
                                                            }
                                                            localMap[cloudSession.id] = merged
                                                        }
                                                        localMap.values.toList()
                                                    }
                                                }
                                            }
                                        }
                                        launch {
                                            ds.observeUserPollVotes().collect { cloudPollVotes ->
                                                if (cloudPollVotes.isNotEmpty()) {
                                                    _polls.update { pollsList ->
                                                        pollsList.map { poll ->
                                                            val sanitizedPollId = VotesLeaderboardRepository.sanitizeId(poll.id)
                                                            val cloudCandidateKey = cloudPollVotes[poll.id]
                                                                ?: cloudPollVotes[sanitizedPollId]
                                                            if (cloudCandidateKey == null) {
                                                                poll
                                                            } else {
                                                                val matchedCandidate = poll.candidates.find { cand ->
                                                                    cand.candidateId == cloudCandidateKey ||
                                                                        VotesLeaderboardRepository.sanitizeId(cand.candidateId) == cloudCandidateKey
                                                                }
                                                                val resolvedCandId = matchedCandidate?.candidateId ?: cloudCandidateKey
                                                                if (poll.userVotedCandidateId == resolvedCandId) {
                                                                    poll
                                                                } else {
                                                                    val prevVoted = poll.userVotedCandidateId
                                                                    val updatedCandidates = poll.candidates.map { cand ->
                                                                        var count = cand.votesCount
                                                                        if (cand.candidateId == resolvedCandId) {
                                                                            count += 1
                                                                        } else if (cand.candidateId == prevVoted) {
                                                                            count = (count - 1).coerceAtLeast(0)
                                                                        }
                                                                        cand.copy(votesCount = count)
                                                                    }
                                                                    poll.copy(
                                                                        candidates = updatedCandidates,
                                                                        userVotedCandidateId = resolvedCandId
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    firestoreService?.let { service ->
                                        launch {
                                            service.observePerformersAuthenticated().collect { cloudPerformers ->
                                                if (cloudPerformers.isNotEmpty()) {
                                                    val performersMap = cloudPerformers.associateBy { it.creatorId }
                                                    _performers.update { current -> current + performersMap }
                                                    val mappedProfiles = cloudPerformers.associate { performer ->
                                                        performer.creatorId to performer.toCreatorProfile(uid)
                                                    }
                                                    _creatorProfiles.update { current -> current + mappedProfiles }
                                                }
                                            }
                                        }
                                        launch {
                                            service.observePerformancesAuthenticated().collect { cloudPerfs ->
                                                if (cloudPerfs.isNotEmpty()) {
                                                    _performances.update { current ->
                                                        val localMap = current.associateBy { it.id }
                                                        val cloudMap = cloudPerfs.associateBy { it.id }
                                                        (cloudMap + localMap).values.toList().sortedByDescending { it.createdAtTimestamp }
                                                    }
                                                    activeVotingManager.syncActivePerformances(_performances.value, uid)
                                                }
                                            }
                                        }
                                    }

                                    liveStageVotingService?.let { stageService ->
                                        launch {
                                            stageService.observeLiveStagesAuthenticated().collect { activeStages ->
                                                _cloudStages.value = activeStages.associateBy { it.id }
                                                if (activeStages.isNotEmpty()) {
                                                    val mappedSessions = activeStages.map { it.toLiveSession(uid) }
                                                    _liveSessions.update { current ->
                                                        val currentById = current.associateBy { it.id }
                                                        val updatedFromStages = mappedSessions.associate { stageSession ->
                                                            val existing = currentById[stageSession.id]
                                                            stageSession.id to if (existing != null) {
                                                                existing.copy(
                                                                    spectatorCount = maxOf(existing.spectatorCount, stageSession.spectatorCount),
                                                                    isLive = stageSession.isLive
                                                                )
                                                            } else {
                                                                stageSession
                                                            }
                                                        }
                                                        (currentById + updatedFromStages).values.toList()
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                votesListenerJob = repoScope.launch {
                                    val votesRepo = votesRepository ?: return@launch
                                    try {
                                        votesRepo.observeCountedVotes().collect { records ->
                                            _cloudVoteRecords.value = records
                                            _isVotesStreamActive.value = true
                                            _leaderboardError.value = null
                                            activeVotingManager.reconcileCloudVotes(
                                                records = records,
                                                currentUserId = uid,
                                                isCloudSynced = true
                                            )
                                        }
                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        _isVotesStreamActive.value = false
                                        val msg = e.localizedMessage ?: "Unable to sync real-time votes"
                                        _leaderboardError.value = msg
                                        activeVotingManager.setError(msg)
                                    }
                                }
                            } else {
                                _isVotesStreamActive.value = false
                                _cloudVoteRecords.value = emptyList()
                                activeVotingManager.reconcileCloudVotes(
                                    records = emptyList(),
                                    currentUserId = _authenticatedAccount.value?.uid?.takeIf { it.isNotBlank() } ?: currentUserId,
                                    isCloudSynced = false
                                )
                            }
                        }
                    )
                } catch (_: Exception) {
                    // Unit test environments without initialized FirebaseApp
                }
            }
        }
    }

    fun syncAuthenticatedUserAccount(account: UserAccount?) {
        _authenticatedAccount.value = account
        activeVotingManager.setCurrentUser(account?.uid?.takeIf { it.isNotBlank() } ?: activeVoterUid())
        if (account != null && account.uid.isNotBlank()) {
            val mapped = account.toCreatorProfile()
            _creatorProfiles.update { current ->
                val existing = current[account.uid]
                val merged = if (existing != null) {
                    existing.copy(
                        artistName = mapped.artistName,
                        stageTitle = mapped.stageTitle,
                        bio = mapped.bio.ifBlank { existing.bio },
                        primaryCategory = mapped.primaryCategory,
                        avatarUrl = mapped.avatarUrl ?: existing.avatarUrl,
                        isCurrentUser = true
                    )
                } else {
                    mapped
                }
                current + (account.uid to merged) + (currentUserId to merged.copy(creatorId = currentUserId))
            }
        } else if (!isSignedInFirebase()) {
            _polls.update { list ->
                list.map { poll ->
                    if (poll.userVotedCandidateId != null) {
                        poll.copy(userVotedCandidateId = null)
                    } else {
                        poll
                    }
                }
            }
        }
    }

    fun trackUserMediaFiles(paths: Collection<String>, ownerUid: String = activeProfileId) {
        val cleanPaths = paths.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanPaths.isEmpty()) return
        val resolvedOwner = ownerUid.ifBlank { currentUserId }
        synchronized(userMediaPathsLock) {
            userUploadedMediaPaths.getOrPut(resolvedOwner) { LinkedHashSet() }.addAll(cleanPaths)
        }
    }

    fun removeDeletedUserProfile(deletedUid: String) {
        removeDeletedUserContent(deletedUid = deletedUid)
    }

    fun removeDeletedUserContent(
        deletedUid: String,
        extraFilePaths: Collection<String> = emptyList()
    ) {
        _authenticatedAccount.value = null
        activeVotingManager.setCurrentUser(currentUserId)

        val sanitizedUid = deletedUid.takeIf { it.isNotBlank() }?.let {
            VotesLeaderboardRepository.sanitizeId(it)
        }
        val ownedIds = buildSet {
            if (deletedUid.isNotBlank()) add(deletedUid)
            if (!sanitizedUid.isNullOrBlank()) add(sanitizedUid)
            add(currentUserId)
        }

        // 1. Identify and remove all performances owned/created by the deleted account
        val removedPerformances = _performances.value.filter { perf ->
            perf.artistId == deletedUid ||
                (sanitizedUid != null && perf.artistId == sanitizedUid) ||
                (perf.artistId == currentUserId && perf.isUserCreated)
        }
        val removedPerfIds = removedPerformances.map { it.id }.toSet()

        _performances.update { current ->
            current
                .filterNot { it.id in removedPerfIds }
                .map { it.copy(isLikedByMe = false, isVotedByMe = false) }
        }

        // 2. Remove deleted performances from poll candidates and clear user's poll vote selections
        _polls.update { pollsList ->
            pollsList.map { poll ->
                poll.copy(
                    candidates = poll.candidates.filterNot { it.performanceId in removedPerfIds },
                    userVotedCandidateId = null
                )
            }
        }

        // 3. Remove all live sessions created/hosted by the deleted account
        val removedSessions = _liveSessions.value.filter { session ->
            session.isCreatorMe || session.creatorId in ownedIds
        }
        val removedSessionIds = removedSessions.flatMap { session ->
            listOf(session.id, VotesLeaderboardRepository.sanitizeId(session.id))
        }.toSet()

        _liveSessions.update { current ->
            current.filterNot { session ->
                session.isCreatorMe || session.creatorId in ownedIds
            }
        }

        // 4. Remove stages, local stage counters, and session votes belonging to the deleted account
        val removedStageIds = _cloudStages.value.values
            .filter { stage ->
                stage.ownerUid in ownedIds ||
                    stage.hostId in ownedIds ||
                    stage.id in removedSessionIds ||
                    stage.sessionId in removedSessionIds
            }
            .map { it.id }
            .toSet() + removedSessionIds

        _cloudStages.update { stagesMap ->
            stagesMap.filterNot { (stageKey, stage) ->
                stageKey in removedStageIds ||
                    stage.ownerUid in ownedIds ||
                    stage.hostId in ownedIds
            }
        }
        _localStageCounters.update { stagesMap ->
            stagesMap
                .filterKeys { stageKey -> stageKey !in removedStageIds }
                .mapValues { (_, counters) ->
                    counters.filterKeys { performerId -> performerId !in ownedIds }
                }
        }
        _sessionVotes.update { votesMap ->
            votesMap.filterKeys { sessionKey -> sessionKey !in removedSessionIds }
        }

        // 5. Remove leaderboard & real-time performance vote records cast by/for the deleted account
        _localVoteRecords.update { records ->
            records.filterNot { record ->
                record.userId in ownedIds ||
                    record.performerId in ownedIds ||
                    record.performanceId in removedPerfIds ||
                    record.sessionId in removedSessionIds
            }
        }
        _cloudVoteRecords.update { records ->
            records.filterNot { record ->
                record.userId in ownedIds ||
                    record.performerId in ownedIds ||
                    record.performanceId in removedPerfIds ||
                    record.sessionId in removedSessionIds
            }
        }

        activeVotingManager.removeUserContent(
            deletedUserIds = ownedIds,
            deletedPerformanceIds = removedPerfIds,
            fallbackUserId = currentUserId
        )
        activeVotingManager.syncActivePerformances(_performances.value, currentUserId)

        // 6. Clear submitted ratings & session rating deltas
        _userSubmittedRatings.value = emptyMap()
        _sessionRatingStarsDelta.value = emptyMap()
        _sessionRatingCountDelta.value = emptyMap()

        // 7. Prune performer & creator profiles and restore default guest creator slot
        _performers.update { current ->
            current - ownedIds
        }
        val defaultSelfProfile = getInitialCreatorProfiles()[currentUserId]
        _creatorProfiles.update { current ->
            var pruned = current - deletedUid - currentUserId
            if (!sanitizedUid.isNullOrBlank()) {
                pruned = pruned - sanitizedUid
            }
            if (defaultSelfProfile != null) {
                pruned + (currentUserId to defaultSelfProfile)
            } else {
                pruned
            }
        }

        // 8. Delete all local audio/video/thumbnail/recording files associated with the deleted account
        val trackedPaths = synchronized(userMediaPathsLock) {
            buildSet {
                ownedIds.forEach { ownerKey ->
                    userUploadedMediaPaths.remove(ownerKey)?.let { addAll(it) }
                }
            }
        }
        val perfMediaPaths = removedPerformances.flatMap { perf ->
            listOfNotNull(perf.customAudioPath, perf.customVideoPath)
        }
        val allPathsToDelete = (trackedPaths + perfMediaPaths + extraFilePaths)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        deleteUserMediaFilesFromDisk(allPathsToDelete)
        repoScope.launch {
            offlineCacheRepository?.clearAllCache()
        }
    }

    private fun deleteUserMediaFilesFromDisk(paths: Set<String>) {
        val videoTimestampRegex = Regex("^video_(\\d+)_.*")
        val uploadsDir = context?.let { java.io.File(it.filesDir, "uploads") }

        paths.forEach { path ->
            runCatching {
                val file = java.io.File(path)
                val match = videoTimestampRegex.matchEntire(file.name)
                if (match != null && uploadsDir != null && uploadsDir.exists()) {
                    val ts = match.groupValues[1]
                    uploadsDir.listFiles()?.forEach { candidate ->
                        if (candidate.isFile && candidate.name.startsWith("thumb_${ts}.jpg")) {
                            deleteLocalMediaFile(candidate)
                        }
                    }
                }
                deleteLocalMediaFile(file)
            }
        }

        context?.let { ctx ->
            runCatching {
                val stagingDir = java.io.File(ctx.filesDir, "secure_recording_staging")
                if (stagingDir.exists() && stagingDir.isDirectory) {
                    stagingDir.listFiles()?.forEach { stagingFile ->
                        if (stagingFile.isFile) {
                            com.example.security.UploadEncryptionManager.secureDelete(stagingFile)
                        }
                    }
                }
            }
            com.example.security.UploadEncryptionManager.refreshVaultStatus(ctx)
        }
    }

    private fun deleteLocalMediaFile(file: java.io.File) {
        if (!file.exists() || !file.isFile) return
        if (com.example.security.UploadEncryptionManager.isEncryptedFile(file) || file.length() > 262_144L) {
            file.delete()
        } else {
            com.example.security.UploadEncryptionManager.secureDelete(file)
        }
    }

    fun getCreatorProfile(creatorId: String): CreatorProfile {
        return _creatorProfiles.value[creatorId] ?: CreatorProfile(
            creatorId = creatorId,
            artistName = if (creatorId == activeProfileId) activeDisplayName else "Stage Artist",
            stageTitle = _authenticatedAccount.value?.takeIf { it.uid == creatorId }?.effectiveTitle ?: "Content Creator",
            bio = _authenticatedAccount.value?.takeIf { it.uid == creatorId }?.bio?.takeIf { it.isNotBlank() }
                ?: "Crafting sonic melodies and poetic verses for the stage.",
            primaryCategory = _authenticatedAccount.value?.takeIf { it.uid == creatorId }?.primaryCategory
                ?: PerformanceCategory.MUSIC,
            avatarUrl = _authenticatedAccount.value?.takeIf { it.uid == creatorId }?.photoUrl,
            isCurrentUser = (creatorId == currentUserId || creatorId == activeProfileId)
        )
    }

    fun updateCurrentUserProfile(
        artistName: String,
        stageTitle: String,
        bio: String,
        primaryCategory: PerformanceCategory,
        socialSpotify: String,
        socialInstagram: String,
        socialYoutube: String,
        socialWebsite: String
    ) {
        val targetId = activeProfileId
        val existing = _creatorProfiles.value[targetId] ?: getCreatorProfile(targetId)
        val updated = existing.copy(
            creatorId = targetId,
            artistName = artistName,
            stageTitle = stageTitle,
            bio = bio,
            primaryCategory = primaryCategory,
            socialSpotify = socialSpotify,
            socialInstagram = socialInstagram,
            socialYoutube = socialYoutube,
            socialWebsite = socialWebsite,
            isCurrentUser = true
        )
        _creatorProfiles.update { map ->
            map + (targetId to updated) + (currentUserId to updated.copy(creatorId = currentUserId))
        }
        firestoreDataSource?.saveCreatorProfile(updated)
        if (isSignedInFirebase()) {
            val ownerUid = activeVoterUid()
            val performerDoc = updated.copy(creatorId = ownerUid).toPerformer(
                ownerUid = ownerUid,
                totalPerformances = getPerformancesForCreator(ownerUid).size,
                totalSessions = _liveSessions.value.count { it.creatorId == ownerUid }
            )
            repoScope.launch {
                firestoreService?.savePerformer(performerDoc)
            }
        }
    }

    fun rateCreator(creatorId: String, newRating: Float) {
        val clampedRating = newRating.coerceIn(1.0f, 5.0f)
        val starInt = clampedRating.roundToInt().coerceIn(1, 5)
        val existing = _creatorProfiles.value[creatorId] ?: getCreatorProfile(creatorId)
        val totalCount = existing.totalAudienceRatingsCount + 1
        val updatedRating = ((existing.audienceRating * existing.totalAudienceRatingsCount) + clampedRating) / totalCount
        val updated = existing.copy(
            audienceRating = updatedRating.coerceIn(1.0f, 5.0f),
            totalAudienceRatingsCount = totalCount
        )
        _creatorProfiles.update { map -> map + (creatorId to updated) }
        _userSubmittedRatings.update { map -> map + (creatorId to starInt) }
        _sessionRatingStarsDelta.update { map -> map + (creatorId to ((map[creatorId] ?: 0f) + clampedRating)) }
        _sessionRatingCountDelta.update { map -> map + (creatorId to ((map[creatorId] ?: 0) + 1)) }

        // Update the creator's most recent performance rating so PerformanceAnalyticsChart reflects live feedback
        val latestPerfId = _performances.value
            .filter { it.artistId == creatorId }
            .maxByOrNull { it.createdAtTimestamp }
            ?.id
        if (latestPerfId != null) {
            _performances.update { list ->
                list.map { perf ->
                    if (perf.id == latestPerfId) {
                        val baseRating = perf.audienceRating ?: existing.audienceRating
                        val baseCount = perf.ratingsCount.coerceAtLeast(1)
                        val nextCount = baseCount + 1
                        val nextAvg = ((baseRating * baseCount) + clampedRating) / nextCount
                        perf.copy(
                            audienceRating = String.format(java.util.Locale.US, "%.2f", nextAvg).toFloat(),
                            ratingsCount = nextCount
                        )
                    } else {
                        perf
                    }
                }
            }
        }

        if (creatorId == activeProfileId || creatorId == currentUserId) {
            firestoreDataSource?.saveCreatorProfile(updated)
        } else {
            firestoreDataSource?.updateCreatorAudienceRating(creatorId, updated.audienceRating, totalCount)
        }
        if (isSignedInFirebase()) {
            repoScope.launch {
                firestoreService?.ratePerformer(creatorId, clampedRating)
            }
        }
    }

    fun ratePerformance(performanceId: String, newRating: Float) {
        val clamped = newRating.coerceIn(1.0f, 5.0f)
        var targetArtistId: String? = null
        _performances.update { list ->
            list.map { perf ->
                if (perf.id == performanceId) {
                    targetArtistId = perf.artistId
                    val currentAvg = perf.audienceRating ?: 4.8f
                    val currentCount = perf.ratingsCount.coerceAtLeast(1)
                    val nextCount = currentCount + 1
                    val nextAvg = ((currentAvg * currentCount) + clamped) / nextCount
                    perf.copy(
                        audienceRating = String.format(java.util.Locale.US, "%.2f", nextAvg).toFloat(),
                        ratingsCount = nextCount
                    )
                } else {
                    perf
                }
            }
        }
        targetArtistId?.let { artistId ->
            val existing = _creatorProfiles.value[artistId] ?: return@let
            val totalCount = existing.totalAudienceRatingsCount + 1
            val updatedRating = ((existing.audienceRating * existing.totalAudienceRatingsCount) + clamped) / totalCount
            val updated = existing.copy(
                audienceRating = updatedRating.coerceIn(1.0f, 5.0f),
                totalAudienceRatingsCount = totalCount
            )
            _creatorProfiles.update { map -> map + (artistId to updated) }
            _userSubmittedRatings.update { map -> map + (artistId to clamped.roundToInt().coerceIn(1, 5)) }
            _sessionRatingStarsDelta.update { map -> map + (artistId to ((map[artistId] ?: 0f) + clamped)) }
            _sessionRatingCountDelta.update { map -> map + (artistId to ((map[artistId] ?: 0) + 1)) }
        }
    }

    fun getPerformancesForCreator(creatorId: String): List<Performance> {
        return _performances.value.filter { it.artistId == creatorId }
    }

    fun getCreatorSessionStats(creatorId: String): CreatorSessionStats {
        val sessions = _liveSessions.value.filter { it.creatorId == creatorId }
        val totalSpectators = sessions.sumOf { it.spectatorCount }
        val peak = sessions.maxOfOrNull { it.spectatorCount } ?: 0
        val totalHours = (sessions.size * 45) / 60f
        return CreatorSessionStats(
            totalSessions = sessions.size,
            totalSpectatorsReached = totalSpectators,
            peakViewers = peak,
            totalLiveHours = totalHours,
            totalSessionVotes = totalSpectators * 2,
            pastSessions = sessions.sortedByDescending { it.startedAt }
        )
    }

    fun observeCreatorSessions(creatorId: String): Flow<List<LiveSession>> {
        val cloudFlow = firestoreDataSource?.observeCreatorSessions(creatorId)
        return if (cloudFlow != null) {
            cloudFlow
        } else {
            flow {
                emit(_liveSessions.value.filter { it.creatorId == creatorId })
            }
        }
    }

    fun getPerformanceById(id: String): Performance? {
        return _performances.value.find { it.id == id }
    }

    fun getPollById(id: String): Poll? {
        return _polls.value.find { it.id == id }
    }

    fun getLiveSessionById(id: String): LiveSession? {
        return _liveSessions.value.find { it.id == id }
    }

    fun recordView(performanceId: String) {
        _performances.update { list ->
            list.map {
                if (it.id == performanceId) it.copy(viewsCount = it.viewsCount + 1) else it
            }
        }
        if (isSignedInFirebase()) {
            repoScope.launch {
                firestoreService?.updatePerformanceEngagement(performanceId, viewsDelta = 1L)
            }
        }
    }

    fun toggleLike(performanceId: String) {
        var appliedDelta = 0L
        _performances.update { list ->
            list.map {
                if (it.id == performanceId) {
                    val newLiked = !it.isLikedByMe
                    val delta = if (newLiked) 1 else -1
                    appliedDelta = delta.toLong()
                    it.copy(
                        isLikedByMe = newLiked,
                        likesCount = (it.likesCount + delta).coerceAtLeast(0)
                    )
                } else it
            }
        }
        if (appliedDelta != 0L && isSignedInFirebase()) {
            repoScope.launch {
                firestoreService?.updatePerformanceEngagement(performanceId, likesDelta = appliedDelta)
            }
        }
    }

    fun addCheer(performanceId: String) {
        _performances.update { list ->
            list.map {
                if (it.id == performanceId) it.copy(cheersCount = it.cheersCount + 1) else it
            }
        }
        if (isSignedInFirebase()) {
            repoScope.launch {
                firestoreService?.updatePerformanceEngagement(performanceId, cheersDelta = 1L)
            }
        }
    }

    fun voteInPoll(pollId: String, candidateId: String) {
        var votedPerf: Performance? = null
        _polls.update { list ->
            list.map { poll ->
                if (poll.id == pollId) {
                    if (poll.userVotedCandidateId == candidateId) {
                        poll
                    } else {
                        val prevVoted = poll.userVotedCandidateId
                        val updatedCandidates = poll.candidates.map { candidate ->
                            var count = candidate.votesCount
                            if (candidate.candidateId == candidateId) {
                                count += 1
                            } else if (candidate.candidateId == prevVoted) {
                                count = (count - 1).coerceAtLeast(0)
                            }
                            candidate.copy(votesCount = count)
                        }

                        val matchedCandidate = updatedCandidates.find { it.candidateId == candidateId }
                        if (matchedCandidate != null) {
                            updatePerformanceVotes(matchedCandidate.performanceId, 1)
                            votedPerf = _performances.value.find { it.id == matchedCandidate.performanceId }
                        }

                        poll.copy(
                            candidates = updatedCandidates,
                            userVotedCandidateId = candidateId
                        )
                    }
                } else poll
            }
        }
        firestoreDataSource?.recordVote(pollId, candidateId, activeVoterUid())
        votedPerf?.let { perf ->
            recordPerformerVoteInVotesCollection(
                performerId = perf.artistId,
                performerName = perf.artistName,
                category = perf.category,
                genre = perf.genre,
                stageTitle = getCreatorStageTitle(perf.artistId, perf.category),
                performanceId = perf.id,
                topPerformanceTitle = perf.title,
                voteDelta = 1,
                voteTier = "POLL_VOTE",
                targetId = perf.id,
                targetType = "POLL"
            )
        }
    }

    fun voteForPerformance(performanceId: String) {
        val deterministicVoteId = "vote_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val (isNowVoted, voteDelta) = activeVotingManager.toggleVote(
            performanceId = performanceId,
            voterId = activeVoterUid(),
            voterName = activeDisplayName,
            voteIdForCreate = deterministicVoteId
        )
        if (voteDelta == 0) return

        syncPerformanceFromVotingState(performanceId)

        val perf = _performances.value.find { it.id == performanceId }
        if (perf != null && voteDelta > 0) {
            recordPerformerVoteInVotesCollection(
                performerId = perf.artistId,
                performerName = perf.artistName,
                category = perf.category,
                genre = perf.genre,
                stageTitle = getCreatorStageTitle(perf.artistId, perf.category),
                performanceId = perf.id,
                topPerformanceTitle = perf.title,
                voteDelta = voteDelta,
                voteTier = VoteTier.STANDARD.firestoreKey,
                targetId = perf.id,
                targetType = "PERFORMANCE",
                voteId = deterministicVoteId
            )
        } else if (voteDelta < 0 && isSignedInFirebase()) {
            repoScope.launch {
                firestoreService?.updatePerformanceEngagement(performanceId, votesDelta = voteDelta.toLong())
            }
        }
        if (perf?.pollId != null) {
            _polls.update { list ->
                list.map { poll ->
                    if (poll.id == perf.pollId) {
                        val updatedCandidates = poll.candidates.map { cand ->
                            if (cand.performanceId == performanceId) {
                                cand.copy(votesCount = (cand.votesCount + voteDelta).coerceAtLeast(0))
                            } else cand
                        }
                        val matchingCandId = poll.candidates.find { it.performanceId == performanceId }?.candidateId
                        poll.copy(
                            candidates = updatedCandidates,
                            userVotedCandidateId = if (isNowVoted) matchingCandId else null
                        )
                    } else poll
                }
            }
        }
    }

    private fun syncPerformanceFromVotingState(performanceId: String) {
        val voteEntry = activeVotingManager.votingState.value.activePerformances[performanceId] ?: return
        _performances.update { list ->
            list.map { item ->
                if (item.id == performanceId &&
                    (item.votesCount != voteEntry.totalVotes || item.isVotedByMe != voteEntry.isVotedByCurrentUser)
                ) {
                    item.copy(
                        votesCount = voteEntry.totalVotes,
                        isVotedByMe = voteEntry.isVotedByCurrentUser
                    )
                } else {
                    item
                }
            }
        }
    }

    /**
     * Casts a multi-tier real-time vote ([VoteTier.STANDARD], [VoteTier.SUPER_VOTE], or
     * [VoteTier.STANDING_OVATION]) for an active performance via [activeVotingManager] StateFlow
     * and persists it to Firestore `/votes/{voteId}`.
     */
    fun castActivePerformanceVote(
        performanceId: String,
        tier: VoteTier = VoteTier.STANDARD,
        sessionId: String? = null
    ) {
        val perf = _performances.value.find { it.id == performanceId } ?: return
        val deterministicVoteId = "vote_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        activeVotingManager.castVote(
            performanceId = performanceId,
            voterId = activeVoterUid(),
            voterName = activeDisplayName,
            tier = tier,
            sessionId = sessionId,
            voteId = deterministicVoteId
        )
        syncPerformanceFromVotingState(performanceId)

        recordPerformerVoteInVotesCollection(
            performerId = perf.artistId,
            performerName = perf.artistName,
            category = perf.category,
            genre = perf.genre,
            stageTitle = getCreatorStageTitle(perf.artistId, perf.category),
            performanceId = perf.id,
            topPerformanceTitle = perf.title,
            sessionId = sessionId,
            voteDelta = tier.weight,
            voteTier = tier.firestoreKey,
            targetId = perf.id,
            targetType = "PERFORMANCE",
            voteId = deterministicVoteId
        )

        if (perf.pollId != null) {
            _polls.update { list ->
                list.map { poll ->
                    if (poll.id == perf.pollId) {
                        val matchingCandId = poll.candidates.find { it.performanceId == performanceId }?.candidateId
                        val updatedCandidates = poll.candidates.map { cand ->
                            if (cand.performanceId == performanceId) {
                                cand.copy(votesCount = (cand.votesCount + tier.weight).coerceAtLeast(0))
                            } else cand
                        }
                        poll.copy(
                            candidates = updatedCandidates,
                            userVotedCandidateId = matchingCandId ?: poll.userVotedCandidateId
                        )
                    } else poll
                }
            }
        }
    }

    fun getPerformanceVoteCountState(performanceId: String): PerformanceVoteCount? =
        activeVotingState.value.getPerformanceVoteState(performanceId)

    private fun updatePerformanceVotes(perfId: String, delta: Int) {
        if (delta > 0) {
            val tier = VoteTier.fromKeyOrDelta(null, delta)
            activeVotingManager.castVote(
                performanceId = perfId,
                voterId = activeVoterUid(),
                voterName = activeDisplayName,
                tier = tier
            )
        }
        _performances.update { list ->
            list.map {
                if (it.id == perfId) {
                    val syncedCount = activeVotingState.value.voteCounts[perfId]
                    it.copy(
                        isVotedByMe = delta > 0,
                        votesCount = syncedCount ?: (it.votesCount + delta).coerceAtLeast(0)
                    )
                } else it
            }
        }
    }

    fun observeSessionVotes(sessionId: String): Flow<Map<String, Int>> {
        val localFlow = _sessionVotes.map { it[sessionId] ?: emptyMap() }
        val sessionCloudFlow = firestoreDataSource?.observeSessionVotes(sessionId)
        val stageCounterFlow = liveStageVotingService?.observeStageVotingMapAuthenticated(sessionId)

        return when {
            sessionCloudFlow != null && stageCounterFlow != null -> {
                combine(localFlow, sessionCloudFlow, stageCounterFlow) { local, sessionCloud, stageMap ->
                    mergeVoteMaps(local, mergeVoteMaps(sessionCloud, stageMap))
                }
            }
            sessionCloudFlow != null -> {
                combine(localFlow, sessionCloudFlow) { local, sessionCloud ->
                    mergeVoteMaps(local, sessionCloud)
                }
            }
            else -> localFlow
        }
    }

    fun observeStageVotingSnapshot(stageId: String): Flow<StageVotingSnapshot> {
        val sanitizedId = VotesLeaderboardRepository.sanitizeId(stageId)
        val localFlow = _localStageCounters.map { it[sanitizedId] ?: it[stageId] ?: emptyMap() }
        val cloudSnapshotFlow = liveStageVotingService?.observeStageSnapshotAuthenticated(sanitizedId)

        return if (cloudSnapshotFlow != null) {
            combine(cloudSnapshotFlow, localFlow, _liveSessions) { cloudSnap, localMap, sessions ->
                val session = sessions.find { it.id == stageId || VotesLeaderboardRepository.sanitizeId(it.id) == sanitizedId }
                val mergedCounters = mergeStageCounters(cloudSnap.counters, localMap.values.toList())
                val resolvedTotalVotes = maxOf(
                    cloudSnap.totalStageVotes,
                    mergedCounters.sumOf { it.totalVotes }.toInt()
                )
                val resolvedBallots = maxOf(
                    cloudSnap.totalBallotsCast,
                    mergedCounters.sumOf { it.ballotsCast }.toInt()
                )
                val resolvedCheers = maxOf(
                    cloudSnap.totalCheersCount,
                    mergedCounters.sumOf { it.cheersCount }.toInt()
                )
                val resolvedSpectators = maxOf(
                    cloudSnap.spectatorCount,
                    session?.spectatorCount ?: 0
                )
                val leader = mergedCounters.filter { it.totalVotes > 0L }
                    .maxByOrNull { it.totalVotes }
                    ?.performerId
                cloudSnap.copy(
                    stageId = sanitizedId,
                    counters = mergedCounters,
                    leadingPerformerId = leader,
                    totalStageVotes = resolvedTotalVotes,
                    totalBallotsCast = resolvedBallots,
                    totalCheersCount = resolvedCheers,
                    spectatorCount = resolvedSpectators
                )
            }
        } else {
            combine(localFlow, _liveSessions) { localMap, sessions ->
                val session = sessions.find { it.id == stageId || VotesLeaderboardRepository.sanitizeId(it.id) == sanitizedId }
                val counters = localMap.values.sortedWith(
                    compareByDescending<StageVotingCounter> { it.totalVotes }
                        .thenByDescending { it.cheersCount }
                )
                LiveStageVotingService.buildStageSnapshot(
                    stageId = sanitizedId,
                    stage = null,
                    counters = counters,
                    isCloudSynced = false
                ).copy(spectatorCount = session?.spectatorCount ?: 0)
            }
        }
    }

    private fun mergeVoteMaps(primary: Map<String, Int>, secondary: Map<String, Int>): Map<String, Int> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary
        val keys = primary.keys + secondary.keys
        return keys.associateWith { key -> maxOf(primary[key] ?: 0, secondary[key] ?: 0) }
    }

    private fun mergeStageCounters(
        cloudCounters: List<StageVotingCounter>,
        localCounters: List<StageVotingCounter>
    ): List<StageVotingCounter> {
        if (cloudCounters.isEmpty()) {
            return localCounters.sortedByDescending { it.totalVotes }
        }
        if (localCounters.isEmpty()) {
            return cloudCounters
        }
        val byKey = LinkedHashMap<String, StageVotingCounter>()
        cloudCounters.forEach { c -> byKey[c.performerId.ifBlank { c.id }] = c }
        localCounters.forEach { local ->
            val key = local.performerId.ifBlank { local.id }
            val existing = byKey[key]
            byKey[key] = if (existing == null) {
                local
            } else {
                existing.copy(
                    totalVotes = maxOf(existing.totalVotes, local.totalVotes),
                    standardVotesCount = maxOf(existing.standardVotesCount, local.standardVotesCount),
                    superVotesCount = maxOf(existing.superVotesCount, local.superVotesCount),
                    ovationVotesCount = maxOf(existing.ovationVotesCount, local.ovationVotesCount),
                    ballotsCast = maxOf(existing.ballotsCast, local.ballotsCast),
                    uniqueVoterCount = maxOf(existing.uniqueVoterCount, local.uniqueVoterCount),
                    cheersCount = maxOf(existing.cheersCount, local.cheersCount)
                )
            }
        }
        return byKey.values.sortedWith(
            compareByDescending<StageVotingCounter> { it.totalVotes }
                .thenByDescending { it.cheersCount }
        )
    }

    fun castSessionVote(
        sessionId: String,
        performerId: String,
        performerName: String,
        voteDelta: Int = 1,
        voteType: String = "VOTE"
    ) {
        _sessionVotes.update { map ->
            val sessionMap = (map[sessionId] ?: emptyMap()).toMutableMap()
            sessionMap[performerId] = (sessionMap[performerId] ?: 0) + voteDelta
            map + (sessionId to sessionMap)
        }
        updatePerformanceVotes(performerId, voteDelta)
        firestoreDataSource?.recordSessionVote(
            sessionId = sessionId,
            performerId = performerId,
            performerName = performerName,
            userId = activeVoterUid(),
            voteDelta = voteDelta,
            voteType = voteType
        )

        val matchedPerf = _performances.value.find { it.id == performerId || it.artistId == performerId }
        val resolvedPerformerId = matchedPerf?.artistId ?: performerId
        val resolvedPerformerName = matchedPerf?.artistName ?: performerName
        val resolvedCategory = matchedPerf?.category ?: PerformanceCategory.MUSIC
        val resolvedVoteTier = VoteTier.fromKeyOrDelta(voteType, voteDelta)
        val tier = resolvedVoteTier.firestoreKey

        recordLocalStageCounterVote(
            stageId = sessionId,
            performerId = resolvedPerformerId,
            performerName = resolvedPerformerName,
            category = resolvedCategory,
            performanceId = matchedPerf?.id ?: performerId,
            performanceTitle = matchedPerf?.title,
            tier = resolvedVoteTier
        )

        if (isSignedInFirebase()) {
            val activeSession = _liveSessions.value.find { it.id == sessionId }
            repoScope.launch {
                val stageService = liveStageVotingService ?: return@launch
                if (activeSession != null && !_cloudStages.value.containsKey(VotesLeaderboardRepository.sanitizeId(sessionId))) {
                    val stagePerformers = resolveStagePerformers(activeSession)
                    stageService.syncLiveSessionStage(activeSession, stagePerformers)
                }
                stageService.castSpectatorVote(
                    stageId = sessionId,
                    performerId = resolvedPerformerId,
                    performerName = resolvedPerformerName,
                    category = resolvedCategory,
                    tier = resolvedVoteTier,
                    performanceId = matchedPerf?.id ?: performerId,
                    performanceTitle = matchedPerf?.title,
                    voterName = activeDisplayName
                )
            }
        }

        recordPerformerVoteInVotesCollection(
            performerId = resolvedPerformerId,
            performerName = resolvedPerformerName,
            category = resolvedCategory,
            genre = matchedPerf?.genre ?: "Live Stage",
            stageTitle = getCreatorStageTitle(resolvedPerformerId, resolvedCategory),
            performanceId = matchedPerf?.id,
            topPerformanceTitle = matchedPerf?.title,
            sessionId = sessionId,
            voteDelta = voteDelta,
            voteTier = tier,
            targetId = resolvedPerformerId,
            targetType = "SESSION"
        )
    }

    private fun recordLocalStageCounterVote(
        stageId: String,
        performerId: String,
        performerName: String,
        category: PerformanceCategory,
        performanceId: String?,
        performanceTitle: String?,
        tier: VoteTier
    ) {
        val sanitizedStageId = VotesLeaderboardRepository.sanitizeId(stageId)
        val sanitizedPerformerId = VotesLeaderboardRepository.sanitizeId(performerId)
        val weight = tier.weight.coerceIn(1, 100).toLong()
        val std = if (tier == VoteTier.STANDARD || tier == VoteTier.POLL_VOTE) 1L else 0L
        val sup = if (tier == VoteTier.SUPER_VOTE) 1L else 0L
        val ova = if (tier == VoteTier.STANDING_OVATION) 1L else 0L

        _localStageCounters.update { stagesMap ->
            val currentStageMap = (stagesMap[sanitizedStageId] ?: emptyMap()).toMutableMap()
            val existing = currentStageMap[sanitizedPerformerId]
            val updated = if (existing != null) {
                existing.copy(
                    totalVotes = existing.totalVotes + weight,
                    standardVotesCount = existing.standardVotesCount + std,
                    superVotesCount = existing.superVotesCount + sup,
                    ovationVotesCount = existing.ovationVotesCount + ova,
                    ballotsCast = existing.ballotsCast + 1L,
                    uniqueVoterCount = existing.uniqueVoterCount.coerceAtLeast(1L)
                )
            } else {
                StageVotingCounter(
                    id = sanitizedPerformerId,
                    stageId = sanitizedStageId,
                    performerId = sanitizedPerformerId,
                    performerName = performerName,
                    category = category.name,
                    performanceId = performanceId,
                    performanceTitle = performanceTitle,
                    avatarInitials = performerName.take(2).uppercase().ifBlank { "SP" },
                    totalVotes = weight,
                    standardVotesCount = std,
                    superVotesCount = sup,
                    ovationVotesCount = ova,
                    ballotsCast = 1L,
                    uniqueVoterCount = 1L,
                    cheersCount = 0L
                )
            }
            currentStageMap[sanitizedPerformerId] = updated
            stagesMap + (sanitizedStageId to currentStageMap)
        }
    }

    private fun recordLocalStageCheer(
        stageId: String,
        performerId: String,
        performerName: String,
        category: PerformanceCategory
    ) {
        val sanitizedStageId = VotesLeaderboardRepository.sanitizeId(stageId)
        val sanitizedPerformerId = VotesLeaderboardRepository.sanitizeId(performerId)
        _localStageCounters.update { stagesMap ->
            val currentStageMap = (stagesMap[sanitizedStageId] ?: emptyMap()).toMutableMap()
            val existing = currentStageMap[sanitizedPerformerId]
            val updated = if (existing != null) {
                existing.copy(cheersCount = existing.cheersCount + 1L)
            } else {
                StageVotingCounter(
                    id = sanitizedPerformerId,
                    stageId = sanitizedStageId,
                    performerId = sanitizedPerformerId,
                    performerName = performerName,
                    category = category.name,
                    avatarInitials = performerName.take(2).uppercase().ifBlank { "SP" },
                    cheersCount = 1L
                )
            }
            currentStageMap[sanitizedPerformerId] = updated
            stagesMap + (sanitizedStageId to currentStageMap)
        }
    }

    private fun resolveStagePerformers(session: LiveSession): List<Performance> {
        val all = _performances.value
        val matching = all.filter { it.artistId == session.creatorId || it.category == session.category }
        return if (matching.isNotEmpty()) matching.take(3) else all.take(3)
    }

    /**
     * Direct audience vote cast from the Leaderboard UI into the `'votes'` collection.
     */
    fun castLeaderboardVote(
        ranking: CreatorRanking,
        voteDelta: Int = 1,
        voteTier: String = "VOTE"
    ) {
        recordPerformerVoteInVotesCollection(
            performerId = ranking.creatorId,
            performerName = ranking.artistName,
            category = ranking.category,
            genre = ranking.genre,
            stageTitle = ranking.stageTitle,
            performanceId = ranking.topPerformanceId,
            topPerformanceTitle = ranking.topPerformanceTitle,
            voteDelta = voteDelta,
            voteTier = voteTier,
            targetId = ranking.creatorId,
            targetType = "PERFORMER",
            avatarColorHex = ranking.avatarColorHex
        )
    }

    private fun isSignedInFirebase(): Boolean {
        return try {
            Firebase.auth.currentUser != null
        } catch (_: Exception) {
            false
        }
    }

    private fun activeVoterUid(): String {
        val firebaseUid = try {
            Firebase.auth.currentUser?.uid?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        return firebaseUid
            ?: _authenticatedAccount.value?.uid?.takeIf { it.isNotBlank() }
            ?: currentUserId
    }

    private fun activeVoterDisplayName(): String {
        val firebaseName = try {
            Firebase.auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        return _authenticatedAccount.value?.effectiveName?.takeIf { it.isNotBlank() }
            ?: firebaseName
            ?: currentUserName
    }

    private fun recordPerformerVoteInVotesCollection(
        performerId: String,
        performerName: String,
        category: PerformanceCategory,
        genre: String,
        stageTitle: String,
        performanceId: String? = null,
        topPerformanceTitle: String? = null,
        sessionId: String? = null,
        voteDelta: Int = 1,
        voteTier: String = "VOTE",
        targetId: String = performerId,
        targetType: String = "PERFORMER",
        avatarColorHex: Long? = null,
        voteId: String? = null
    ) {
        val isSignedIn = try {
            Firebase.auth.currentUser != null
        } catch (_: Exception) {
            false
        }
        val color = avatarColorHex ?: if (category == PerformanceCategory.MUSIC) 0xFF9D4EDD else 0xFF00B4D8

        val activeVotesRepo = if (isSignedIn) votesRepository else null
        if (activeVotesRepo != null) {
            repoScope.launch {
                val res = activeVotesRepo.castVote(
                    performerId = performerId,
                    performerName = performerName,
                    category = category,
                    voteDelta = voteDelta,
                    voteTier = voteTier,
                    targetId = targetId,
                    targetType = targetType,
                    genre = genre,
                    stageTitle = stageTitle,
                    performanceId = performanceId,
                    topPerformanceTitle = topPerformanceTitle,
                    sessionId = sessionId,
                    avatarColorHex = color,
                    voteId = voteId
                )
                if (res.isFailure) {
                    val errMsg = res.exceptionOrNull()?.localizedMessage
                    _leaderboardError.value = errMsg
                    activeVotingManager.setError(errMsg)
                }
            }
        } else {
            val localRecord = VoteRecord(
                id = voteId ?: "local_${UUID.randomUUID().toString().take(12)}",
                performerId = performerId,
                performerName = performerName,
                targetId = targetId,
                targetType = targetType,
                category = category,
                genre = genre,
                stageTitle = stageTitle,
                performanceId = performanceId,
                topPerformanceTitle = topPerformanceTitle,
                sessionId = sessionId,
                userId = activeVoterUid(),
                userName = activeVoterDisplayName(),
                voteDelta = voteDelta.coerceIn(1, 100),
                voteTier = voteTier,
                status = VotesLeaderboardRepository.STATUS_COUNTED,
                avatarColorHex = color,
                createdAtMillis = System.currentTimeMillis(),
                updatedAtMillis = System.currentTimeMillis()
            )
            _localVoteRecords.update { it + localRecord }
        }
    }

    fun getCurrentVotesAggregation(timePeriod: LeaderboardTimePeriod): VotesAggregationSnapshot {
        val mergedRecords = _cloudVoteRecords.value + _localVoteRecords.value
        val snapshot = VotesLeaderboardRepository.aggregateVotes(
            votes = mergedRecords,
            timePeriod = timePeriod,
            currentUserId = activeVoterUid()
        )
        return snapshot.copy(isLiveCloudSynced = _isVotesStreamActive.value)
    }

    fun publishPerformance(
        title: String,
        category: PerformanceCategory,
        genre: String,
        lyricsOrScript: String,
        visualizerTheme: VisualizerTheme,
        audioType: SynthAudioType,
        customAudioPath: String?,
        enrollInPollId: String?,
        durationSec: Int = 180,
        customVideoPath: String? = null
    ): Performance {
        val resolvedArtistId = activeProfileId
        val resolvedArtistName = activeDisplayName
        val initials = resolvedArtistName.trim().take(2).uppercase().ifBlank { "ME" }
        val newPerf = Performance(
            id = "perf_${UUID.randomUUID().toString().take(8)}",
            title = title,
            artistId = resolvedArtistId,
            artistName = resolvedArtistName,
            avatarInitials = initials,
            category = category,
            genre = genre,
            durationSec = durationSec,
            lyricsOrScript = lyricsOrScript,
            visualizerTheme = visualizerTheme,
            audioType = audioType,
            customAudioPath = customAudioPath,
            customVideoPath = customVideoPath,
            viewsCount = 1,
            likesCount = 0,
            votesCount = 0,
            cheersCount = 0,
            pollId = enrollInPollId,
            isUserCreated = true
        )

        trackUserMediaFiles(
            paths = listOfNotNull(customAudioPath, customVideoPath),
            ownerUid = resolvedArtistId
        )

        _performances.update { listOf(newPerf) + it }
        activeVotingManager.syncActivePerformances(_performances.value, activeVoterUid())
        val activeService = if (isSignedInFirebase()) firestoreService else null
        if (activeService != null) {
            repoScope.launch {
                activeService.savePerformance(newPerf)
            }
        } else {
            firestoreDataSource?.savePerformance(newPerf)
        }

        if (!enrollInPollId.isNullOrEmpty()) {
            _polls.update { pollsList ->
                pollsList.map { poll ->
                    if (poll.id == enrollInPollId) {
                        val newCandidate = PollCandidate(
                            candidateId = "cand_${newPerf.id}",
                            performanceId = newPerf.id,
                            title = newPerf.title,
                            artistName = newPerf.artistName,
                            category = newPerf.category,
                            votesCount = 0,
                            avatarColorHex = 0xFF9D4EDD
                        )
                        poll.copy(candidates = poll.candidates + newCandidate)
                    } else poll
                }
            }
        }

        return newPerf
    }

    fun startLiveSession(
        title: String,
        category: PerformanceCategory,
        genre: String,
        visualizerTheme: VisualizerTheme,
        audioType: SynthAudioType,
        pollId: String?
    ): LiveSession {
        val newSession = LiveSession(
            id = "live_${UUID.randomUUID().toString().take(8)}",
            creatorId = activeProfileId,
            creatorName = activeDisplayName,
            title = title,
            category = category,
            genre = genre,
            visualizerTheme = visualizerTheme,
            audioType = audioType,
            activePollId = pollId,
            spectatorCount = 1,
            isLive = true,
            isCreatorMe = true,
            startedAt = System.currentTimeMillis(),
            ownerUid = activeProfileId,
            reactionCounts = mapOf(StageReactionType.FIRE to 1),
            chatMessages = listOf(
                LiveChatMessage(
                    id = "msg_init",
                    senderName = "Stage Bot",
                    text = "Welcome to the live session! Cheer the creator and cast your votes!",
                    badge = "STAGE BOT"
                )
            )
        )
        _liveSessions.update { listOf(newSession) + it }
        firestoreDataSource?.saveLiveSession(newSession)
        if (isSignedInFirebase()) {
            val stagePerformers = resolveStagePerformers(newSession)
            repoScope.launch {
                liveEventsRepository?.syncLiveSessionToEvent(newSession)
                liveStageVotingService?.createStage(
                    title = newSession.title,
                    category = newSession.category,
                    genre = newSession.genre,
                    hostId = newSession.creatorId,
                    hostName = newSession.creatorName,
                    visualizerTheme = newSession.visualizerTheme,
                    audioType = newSession.audioType,
                    activePollId = newSession.activePollId,
                    sessionId = newSession.id,
                    initialSpectatorCount = newSession.spectatorCount,
                    initialPerformers = stagePerformers,
                    stageId = newSession.id
                )
            }
        }
        return newSession
    }

    fun joinLiveStageSession(session: LiveSession) {
        if (!session.isCreatorMe) {
            _liveSessions.update { list ->
                list.map { item ->
                    if (item.id == session.id) item.copy(spectatorCount = item.spectatorCount + 1) else item
                }
            }
        }
        if (isSignedInFirebase()) {
            val stagePerformers = resolveStagePerformers(session)
            repoScope.launch {
                val stageService = liveStageVotingService ?: return@launch
                val sanitizedId = VotesLeaderboardRepository.sanitizeId(session.id)
                if (!_cloudStages.value.containsKey(sanitizedId)) {
                    stageService.syncLiveSessionStage(session, stagePerformers)
                }
                if (!session.isCreatorMe) {
                    stageService.joinStageAsSpectator(session.id)
                }
            }
        }
    }

    fun leaveLiveStageSession(sessionId: String, isHost: Boolean = false) {
        if (!isHost) {
            _liveSessions.update { list ->
                list.map { item ->
                    if (item.id == sessionId) {
                        item.copy(spectatorCount = (item.spectatorCount - 1).coerceAtLeast(1))
                    } else item
                }
            }
        }
        if (!isHost && isSignedInFirebase()) {
            repoScope.launch {
                liveStageVotingService?.leaveStageAsSpectator(sessionId)
            }
        }
    }

    fun stopLiveSession(sessionId: String) {
        _liveSessions.update { list ->
            list.map {
                if (it.id == sessionId) {
                    val stopped = it.copy(isLive = false)
                    firestoreDataSource?.saveLiveSession(stopped)
                    if (isSignedInFirebase()) {
                        repoScope.launch {
                            liveEventsRepository?.endLiveEvent(sessionId)
                            liveStageVotingService?.endStage(sessionId)
                        }
                    }
                    stopped
                } else it
            }
        }
    }

    fun sendLiveChatMessage(sessionId: String, senderName: String, text: String, isCheer: Boolean = false): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        var targetSession: LiveSession? = null
        var createdMessage: LiveChatMessage? = null
        val senderUid = activeVoterUid()
        _liveSessions.update { list ->
            list.map { session ->
                if (session.id == sessionId) {
                    val isHostSender = session.isCreatorMe || session.ownerUid == senderUid || session.creatorId == senderUid
                    if (session.isChatLocked && !isHostSender) {
                        return@map session
                    }
                    val newMsg = LiveChatMessage(
                        id = "msg_${UUID.randomUUID().toString().take(8)}",
                        senderName = senderName,
                        text = trimmed.take(400),
                        isCheer = isCheer,
                        badge = if (isHostSender) "CREATOR" else null,
                        senderUid = senderUid,
                        isHiddenByHost = false
                    )
                    val updated = session.copy(
                        chatMessages = session.chatMessages + newMsg,
                        spectatorCount = session.spectatorCount + if (isCheer) 2 else 0
                    )
                    targetSession = updated
                    createdMessage = newMsg
                    updated
                } else session
            }
        }
        val sentMsg = createdMessage ?: return false
        val session = targetSession
        if (isSignedInFirebase() && session != null) {
            repoScope.launch {
                firestoreDataSource?.sendStageChatMessage(
                    sessionId = sessionId,
                    sessionOwnerUid = session.ownerUid.ifBlank { session.creatorId },
                    message = sentMsg
                )
            }
        }
        if (isCheer) {
            val performerId = session?.creatorId ?: currentUserId
            val performerName = session?.creatorName ?: "Stage Host"
            val category = session?.category ?: PerformanceCategory.MUSIC
            recordLocalStageCheer(sessionId, performerId, performerName, category)
            if (isSignedInFirebase()) {
                repoScope.launch {
                    liveStageVotingService?.recordStageCheer(
                        stageId = sessionId,
                        performerId = performerId,
                        performerName = performerName,
                        category = category,
                        cheerDelta = 1
                    )
                }
            }
        }
        return true
    }

    fun updateLiveModeration(
        sessionId: String,
        slowModeSeconds: Int? = null,
        isChatLocked: Boolean? = null,
        pinnedAnnouncement: String? = null,
        clearPinnedAnnouncement: Boolean = false
    ) {
        val callerUid = activeVoterUid()
        var updatedSession: LiveSession? = null
        _liveSessions.update { list ->
            list.map { session ->
                if (session.id == sessionId) {
                    val isHost = session.isCreatorMe || session.ownerUid == callerUid || session.creatorId == callerUid
                    if (!isHost) return@map session
                    val resolvedPin = when {
                        clearPinnedAnnouncement -> null
                        pinnedAnnouncement != null -> pinnedAnnouncement.trim().take(240).ifBlank { null }
                        else -> session.pinnedAnnouncement
                    }
                    val next = session.copy(
                        slowModeSeconds = slowModeSeconds?.coerceIn(0, 120) ?: session.slowModeSeconds,
                        isChatLocked = isChatLocked ?: session.isChatLocked,
                        pinnedAnnouncement = resolvedPin
                    )
                    updatedSession = next
                    next
                } else session
            }
        }
        if (updatedSession != null && isSignedInFirebase()) {
            repoScope.launch {
                firestoreDataSource?.updateLiveSessionModeration(
                    sessionId = sessionId,
                    slowModeSeconds = slowModeSeconds,
                    isChatLocked = isChatLocked,
                    pinnedAnnouncement = pinnedAnnouncement,
                    clearPinnedAnnouncement = clearPinnedAnnouncement
                )
            }
        }
    }

    fun toggleHideLiveChatMessage(sessionId: String, messageId: String, hidden: Boolean) {
        val callerUid = activeVoterUid()
        var authorized = false
        _liveSessions.update { list ->
            list.map { session ->
                if (session.id == sessionId) {
                    val isHost = session.isCreatorMe || session.ownerUid == callerUid || session.creatorId == callerUid
                    if (!isHost) return@map session
                    authorized = true
                    session.copy(
                        chatMessages = session.chatMessages.map { msg ->
                            if (msg.id == messageId) msg.copy(isHiddenByHost = hidden) else msg
                        }
                    )
                } else session
            }
        }
        if (authorized && isSignedInFirebase()) {
            repoScope.launch {
                firestoreDataSource?.setStageChatMessageHidden(
                    sessionId = sessionId,
                    messageId = messageId,
                    hidden = hidden
                )
            }
        }
    }

    fun sendLiveReaction(sessionId: String, reactionType: StageReactionType, delta: Int = 1) {
        val boundedDelta = delta.coerceIn(1, 25)
        var targetSession: LiveSession? = null
        _liveSessions.update { list ->
            list.map { session ->
                if (session.id == sessionId) {
                    val currentCount = session.reactionCounts[reactionType] ?: 0
                    val updatedCounts = session.reactionCounts + (reactionType to (currentCount + boundedDelta))
                    val next = session.copy(reactionCounts = updatedCounts)
                    targetSession = next
                    next
                } else session
            }
        }
        val session = targetSession
        val performerId = session?.creatorId ?: currentUserId
        val performerName = session?.creatorName ?: "Stage Host"
        val category = session?.category ?: PerformanceCategory.MUSIC
        recordLocalStageCheer(sessionId, performerId, performerName, category)
        if (isSignedInFirebase()) {
            repoScope.launch {
                firestoreDataSource?.incrementLiveSessionReaction(sessionId, reactionType, boundedDelta)
                liveStageVotingService?.recordStageCheer(
                    stageId = sessionId,
                    performerId = performerId,
                    performerName = performerName,
                    category = category,
                    cheerDelta = boundedDelta
                )
            }
        }
    }

    fun observeStageChatMessages(session: LiveSession): Flow<List<LiveChatMessage>> {
        val callerUid = activeVoterUid()
        val isHost = session.isCreatorMe || session.ownerUid == callerUid || session.creatorId == callerUid
        val localFlow = _liveSessions.map { list ->
            list.find { it.id == session.id }?.chatMessages.orEmpty()
        }
        val cloudFlow = firestoreDataSource?.observeStageChatMessages(
            sessionId = session.id,
            isHost = isHost,
            sessionOwnerUid = session.ownerUid.ifBlank { session.creatorId }
        ) ?: flowOf(emptyList())

        return combine(localFlow, cloudFlow) { localMsgs, cloudMsgs ->
            val mergedById = LinkedHashMap<String, LiveChatMessage>()
            localMsgs.forEach { msg -> mergedById[msg.id] = msg }
            cloudMsgs.forEach { cloudMsg ->
                mergedById[cloudMsg.id] = cloudMsg
            }
            mergedById.values
                .filter { isHost || !it.isHiddenByHost }
                .sortedBy { it.timestamp }
        }
    }

    /**
     * Computes the Global Cumulative Audience Ratings Leaderboard ranking creators from highest
     * to lowest cumulative audience star points (`averageAudienceRating * totalAudienceRatingsCount`).
     */
    fun computeGlobalRatingsLeaderboard(
        categoryFilter: LeaderboardCategoryFilter = LeaderboardCategoryFilter.ALL,
        timePeriod: LeaderboardTimePeriod = LeaderboardTimePeriod.ALL_TIME
    ): List<CreatorRanking> = computeLeaderboard(
        categoryFilter = categoryFilter,
        timePeriod = timePeriod,
        rankingMode = LeaderboardRankingMode.CUMULATIVE_RATINGS
    )

    fun computeGlobalRatingsArenaSummary(rankings: List<CreatorRanking>): GlobalRatingsArenaSummary {
        if (rankings.isEmpty()) return GlobalRatingsArenaSummary()
        val totalStars = rankings.sumOf { it.cumulativeRatingPoints.toDouble() }.toFloat()
        val totalRatings = rankings.sumOf { it.totalAudienceRatingsCount }
        val weightedAvg = if (totalRatings > 0) {
            (totalStars / totalRatings.toFloat()).coerceIn(1.0f, 5.0f)
        } else {
            rankings.map { it.averageAudienceRating }.average().toFloat().coerceIn(0f, 5f)
        }
        return GlobalRatingsArenaSummary(
            totalCumulativeRatingStars = totalStars,
            globalWeightedAverageRating = weightedAvg,
            totalAudienceRatingsCount = totalRatings,
            totalRankedCreators = rankings.size
        )
    }

    // Global Leaderboard Aggregation Engine:
    // Supports both CUMULATIVE_RATINGS (ranks creators globally by cumulative audience star ratings)
    // and STAGE_VOTES (aggregates real-time vote counts from the Firestore 'votes' collection).
    fun computeLeaderboard(
        categoryFilter: LeaderboardCategoryFilter,
        timePeriod: LeaderboardTimePeriod,
        rankingMode: LeaderboardRankingMode = LeaderboardRankingMode.STAGE_VOTES
    ): List<CreatorRanking> {
        val allPerfs = _performances.value
        val creatorsMap = allPerfs.groupBy { it.artistId }
        val votesAgg = getCurrentVotesAggregation(timePeriod)
        val cloudSummariesByPerformer = votesAgg.performerSummaries.associateBy { it.performerId }

        val profileIds = if (rankingMode == LeaderboardRankingMode.CUMULATIVE_RATINGS) {
            _creatorProfiles.value.keys.filter { id ->
                // Avoid duplicate alias when signed in as a real Firebase UID alongside fallback creator_user_me
                !(id == currentUserId && activeProfileId != currentUserId && _creatorProfiles.value.containsKey(activeProfileId))
            }
        } else {
            emptyList()
        }

        val allPerformerIds = (creatorsMap.keys + cloudSummariesByPerformer.keys + _performers.value.keys + profileIds).distinct()
        val rankings = mutableListOf<CreatorRanking>()

        val (voteWeight, likeWeight, viewWeight, periodScale) = when (timePeriod) {
            LeaderboardTimePeriod.WEEKLY -> Tuple4(12, 4, 1, 0.45f)
            LeaderboardTimePeriod.MONTHLY -> Tuple4(10, 3, 1, 0.75f)
            LeaderboardTimePeriod.ALL_TIME -> Tuple4(8, 3, 2, 1.0f)
        }

        val ratingPeriodScale = when (timePeriod) {
            LeaderboardTimePeriod.WEEKLY -> 0.35f
            LeaderboardTimePeriod.MONTHLY -> 0.65f
            LeaderboardTimePeriod.ALL_TIME -> 1.0f
        }

        allPerformerIds.forEach { artistId ->
            val perfList = creatorsMap[artistId].orEmpty()
            val voteSummary = cloudSummariesByPerformer[artistId]
            val profile = _creatorProfiles.value[artistId]
            val performerDoc = _performers.value[artistId]

            val topPerf = perfList.maxByOrNull { it.votesCount * 3 + it.likesCount }
            val primaryCategory = voteSummary?.category
                ?: topPerf?.category
                ?: profile?.primaryCategory
                ?: performerDoc?.category
                ?: PerformanceCategory.MUSIC

            val matchesCategory = when (categoryFilter) {
                LeaderboardCategoryFilter.ALL -> true
                LeaderboardCategoryFilter.MUSIC -> primaryCategory == PerformanceCategory.MUSIC
                LeaderboardCategoryFilter.SPOKEN_WORD -> primaryCategory == PerformanceCategory.SPOKEN_WORD
            }

            if (matchesCategory) {
                val activeVotesMap = activeVotingState.value.activePerformances
                val basePerfVotes = if (perfList.isNotEmpty()) {
                    val raw = perfList.sumOf { p -> activeVotesMap[p.id]?.baselineVotes ?: p.votesCount }
                    (raw * periodScale).toInt().coerceAtLeast(if (raw > 0) 1 else 0)
                } else {
                    0
                }
                val aggregatedVotesFromCollection = voteSummary?.aggregatedVotes ?: 0
                val totalVotes = basePerfVotes + aggregatedVotesFromCollection

                val rawViews = perfList.sumOf { it.viewsCount }
                val rawLikes = perfList.sumOf { it.likesCount }
                val totalViews = if (rawViews > 0) (rawViews * periodScale).toInt().coerceAtLeast(1) else aggregatedVotesFromCollection * 3
                val totalLikes = if (rawLikes > 0) (rawLikes * periodScale).toInt().coerceAtLeast(1) else aggregatedVotesFromCollection

                // Resolve authentic creator audience ratings and cumulative star points
                val derivedPerfRatingsCount = perfList.sumOf { PerformanceAnalyticsEngine.resolvePerformanceRatingsCount(it) }
                val derivedPerfAvgRating = if (perfList.isNotEmpty()) {
                    perfList.map { PerformanceAnalyticsEngine.resolvePerformanceRating(it) }.average().toFloat()
                } else {
                    4.8f
                }

                val rawAvgRating = (profile?.audienceRating ?: performerDoc?.rating ?: derivedPerfAvgRating).coerceIn(1.0f, 5.0f)
                val rawRatingsCount = when {
                    profile != null && profile.totalAudienceRatingsCount > 0 -> profile.totalAudienceRatingsCount
                    performerDoc != null && performerDoc.ratingCount > 0 -> performerDoc.ratingCount
                    derivedPerfRatingsCount > 0 -> derivedPerfRatingsCount
                    totalVotes > 0 -> (totalVotes / 3).coerceAtLeast(1)
                    else -> 0
                }

                val sessionStarsDelta = _sessionRatingStarsDelta.value[artistId] ?: 0f
                val sessionCountDelta = _sessionRatingCountDelta.value[artistId] ?: 0

                val baselineCount = (rawRatingsCount - sessionCountDelta).coerceAtLeast(0)
                val baselineStars = ((rawAvgRating * rawRatingsCount.toFloat()) - sessionStarsDelta).coerceAtLeast(0f)

                val scaledCount = if (baselineCount > 0) {
                    (baselineCount * ratingPeriodScale).roundToInt().coerceAtLeast(1)
                } else {
                    0
                }
                val effectiveRatingsCount = scaledCount + sessionCountDelta
                val rawCumulativeStars = (baselineStars * ratingPeriodScale) + sessionStarsDelta
                val cumulativeRatingPoints = ((rawCumulativeStars * 10f).roundToInt() / 10f).coerceAtLeast(0f)
                val effectiveAvgRating = if (effectiveRatingsCount > 0 && cumulativeRatingPoints > 0f) {
                    (cumulativeRatingPoints / effectiveRatingsCount.toFloat()).coerceIn(1.0f, 5.0f)
                } else {
                    rawAvgRating
                }

                val voteScore = (totalVotes * voteWeight) + (totalLikes * likeWeight) + (totalViews * viewWeight)
                val score = if (rankingMode == LeaderboardRankingMode.CUMULATIVE_RATINGS) {
                    (cumulativeRatingPoints * 10f).roundToInt()
                } else {
                    voteScore
                }

                val isCurrent = (artistId == currentUserId || artistId == activeProfileId || artistId == activeVoterUid())

                val avatarColor = profile?.avatarColorHex
                    ?: performerDoc?.avatarColorHex
                    ?: voteSummary?.avatarColorHex
                    ?: when (primaryCategory) {
                        PerformanceCategory.MUSIC -> 0xFF9D4EDD
                        PerformanceCategory.SPOKEN_WORD -> 0xFF00B4D8
                    }

                val artistName = profile?.artistName
                    ?: performerDoc?.name
                    ?: topPerf?.artistName
                    ?: voteSummary?.performerName
                    ?: "Stage Performer"

                val stageTitle = profile?.stageTitle
                    ?: performerDoc?.stageTitle?.takeIf { it.isNotBlank() }
                    ?: voteSummary?.stageTitle?.takeIf { it.isNotBlank() }
                    ?: getCreatorStageTitle(artistId, primaryCategory)

                val genre = topPerf?.genre
                    ?: voteSummary?.genre
                    ?: primaryCategory.displayName

                val topPerfTitle = topPerf?.title
                    ?: voteSummary?.topPerformanceTitle
                    ?: "$artistName Live Showcase"

                val topPerfId = topPerf?.id
                    ?: voteSummary?.topPerformanceId
                    ?: ""

                rankings.add(
                    CreatorRanking(
                        rank = 0,
                        previousRank = 0,
                        creatorId = artistId,
                        artistName = artistName,
                        stageTitle = stageTitle,
                        category = primaryCategory,
                        genre = genre,
                        topPerformanceTitle = topPerfTitle,
                        topPerformanceId = topPerfId,
                        totalVotes = totalVotes,
                        totalViews = totalViews,
                        totalLikes = totalLikes,
                        totalScore = score,
                        rankChange = 0,
                        isCurrentUser = isCurrent,
                        verifiedBadge = true,
                        avatarColorHex = avatarColor,
                        cloudVotesCount = aggregatedVotesFromCollection,
                        uniqueVotersCount = voteSummary?.uniqueVoterCount ?: 0,
                        voteSharePercent = 0f,
                        isVotedByCurrentUser = voteSummary?.votedByCurrentUser ?: perfList.any { it.isVotedByMe },
                        averageAudienceRating = effectiveAvgRating,
                        totalAudienceRatingsCount = effectiveRatingsCount,
                        cumulativeRatingPoints = cumulativeRatingPoints,
                        ratingSharePercent = 0f,
                        pointsToNextRank = 0f,
                        nextRankArtistName = null,
                        competitiveTier = CreatorCompetitiveTier.fromCumulativeStars(cumulativeRatingPoints),
                        totalPerformancesCount = maxOf(perfList.size, performerDoc?.totalPerformances ?: 0)
                    )
                )
            }
        }

        val sorted = if (rankingMode == LeaderboardRankingMode.CUMULATIVE_RATINGS) {
            rankings.sortedWith(
                compareByDescending<CreatorRanking> { it.cumulativeRatingPoints }
                    .thenByDescending { it.averageAudienceRating }
                    .thenByDescending { it.totalAudienceRatingsCount }
                    .thenByDescending { it.totalVotes }
                    .thenBy { it.creatorId }
            )
        } else {
            rankings.sortedWith(
                compareByDescending<CreatorRanking> { it.totalVotes }
                    .thenByDescending { it.cloudVotesCount }
                    .thenByDescending { it.totalScore }
                    .thenBy { it.creatorId }
            )
        }

        val maxLimit = if (rankingMode == LeaderboardRankingMode.CUMULATIVE_RATINGS) 50 else 10
        val displayed = sorted.take(maxLimit)
        val totalDisplayedVotes = displayed.sumOf { it.totalVotes }.coerceAtLeast(1)
        val leaderCumulativeStars = displayed.firstOrNull()?.cumulativeRatingPoints?.coerceAtLeast(1f) ?: 1f

        return displayed.mapIndexed { index, item ->
            val rank = index + 1
            val sessionCountBoost = _sessionRatingCountDelta.value[item.creatorId] ?: 0
            val delta = when {
                rankingMode == LeaderboardRankingMode.CUMULATIVE_RATINGS && sessionCountBoost > 0 ->
                    sessionCountBoost.coerceIn(1, 5)
                item.cloudVotesCount > 0 -> (item.cloudVotesCount.coerceAtMost(5)).coerceAtLeast(1)
                item.isCurrentUser -> 999
                (item.creatorId.hashCode() % 3 == 0) -> 1
                (item.creatorId.hashCode() % 4 == 0) -> 2
                (item.creatorId.hashCode() % 5 == 0) -> -1
                else -> 0
            }
            val voteShare = ((item.totalVotes.toFloat() / totalDisplayedVotes.toFloat()) * 100f).coerceIn(0f, 100f)
            val ratingShare = ((item.cumulativeRatingPoints / leaderCumulativeStars) * 100f).coerceIn(0f, 100f)
            val prevAbove = if (index > 0) displayed[index - 1] else null
            val pointsGap = if (prevAbove != null) {
                (prevAbove.cumulativeRatingPoints - item.cumulativeRatingPoints).coerceAtLeast(0f)
            } else {
                0f
            }

            item.copy(
                rank = rank,
                previousRank = (rank - delta).coerceAtLeast(1),
                rankChange = delta,
                voteSharePercent = voteShare,
                ratingSharePercent = ratingShare,
                pointsToNextRank = pointsGap,
                nextRankArtistName = prevAbove?.artistName
            )
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    private fun getCreatorStageTitle(creatorId: String, category: PerformanceCategory): String {
        return when (creatorId) {
            "creator_1" -> "Grand Slam Poet & Storyteller"
            "creator_2" -> "Neo-Soul Vocalist & Producer"
            "creator_3" -> "Spoken Word Griot & Activist"
            "creator_4" -> "Acoustic Balladeer & Songsmith"
            "creator_5" -> "Lo-Fi Emcee & Vinyl Selector"
            "creator_6" -> "Cinematic Monologist"
            "creator_7" -> "Synthwave Dreamer"
            "creator_user_me" -> "Rising Stage Virtuoso"
            else -> if (category == PerformanceCategory.MUSIC) "Music Composer & Performer" else "Spoken Word Artist"
        }
    }

    private fun getInitialPerformances(): List<Performance> {
        val now = System.currentTimeMillis()
        val dayMs = 86_400_000L
        return listOf(
            Performance(
                id = "perf_spoken_1",
                title = "Concrete Roots & Starlit Canopies",
                artistId = "creator_1",
                artistName = "Maya Vance",
                avatarInitials = "MV",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Slam Poetry",
                durationSec = 175,
                lyricsOrScript = """
                    They tell you the asphalt is dead,
                    that nothing breathes beneath the weight of yellow cabs and glass towers.
                    
                    But at 2 AM, when the sirens swallow their own echoes,
                    the concrete exhales.
                    
                    We are the dandelion seeds pushing through the cracks,
                    we are the rhythm in the subway grates,
                    humming an unsung gospel:
                    
                    You cannot pave over a heartbeat.
                    You cannot censor the dawn.
                    
                    Every brick was once clay,
                    every bridge was once an architect's prayer.
                    Listen close—the city is reciting our names back to the stars.
                """.trimIndent(),
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                viewsCount = 3840,
                likesCount = 1420,
                votesCount = 892,
                cheersCount = 310,
                pollId = "poll_slam_finals",
                createdAtTimestamp = now - dayMs * 1,
                audienceRating = 4.94f,
                ratingsCount = 412
            ),
            Performance(
                id = "perf_music_1",
                title = "Midnight Reverie (Acoustic Soul)",
                artistId = "creator_2",
                artistName = "Alicia Ray",
                avatarInitials = "AR",
                category = PerformanceCategory.MUSIC,
                genre = "Neo-Soul & R&B",
                durationSec = 210,
                lyricsOrScript = """
                    Midnight shadows on the windowsill,
                    waiting for the restless world to still.
                    Golden embers of a melody,
                    falling softly like a memory.
                    
                    Hold the note, let it breathe,
                    there is beauty in what we leave.
                    Through the haze and neon blue,
                    every chord returns to you.
                    
                    Ooh, darling in the quiet hour,
                    music has a sacred power.
                """.trimIndent(),
                visualizerTheme = VisualizerTheme.NEON_WAVES,
                audioType = SynthAudioType.ACOUSTIC_SOUL,
                viewsCount = 4210,
                likesCount = 1890,
                votesCount = 1140,
                cheersCount = 480,
                pollId = "poll_soul_clash",
                createdAtTimestamp = now - dayMs * 1,
                audienceRating = 4.97f,
                ratingsCount = 530
            ),
            Performance(
                id = "perf_spoken_2",
                title = "The Clockmaker's Last Confession",
                artistId = "creator_3",
                artistName = "Darnell Cruz",
                avatarInitials = "DC",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Rhythmic Monologue",
                durationSec = 195,
                lyricsOrScript = """
                    Tick. Tock. 
                    I spent forty years measuring moments in brass gears and sapphire balance wheels.
                    
                    Men rushed through my door begging for more seconds,
                    as if eternity could be bought with a winding key.
                    
                    I told them:
                    Time isn't currency.
                    Time is the space between two open palms,
                    waiting to hold what matters.
                    
                    Do not ask how much time you have left.
                    Ask how deeply you dare to live the cadence you have now.
                """.trimIndent(),
                visualizerTheme = VisualizerTheme.PULSE_ORB,
                audioType = SynthAudioType.PIANO_WARMTH,
                viewsCount = 2950,
                likesCount = 1130,
                votesCount = 745,
                cheersCount = 220,
                pollId = "poll_slam_finals"
            ),
            Performance(
                id = "perf_music_2",
                title = "Golden Horizon",
                artistId = "creator_4",
                artistName = "Marcus Cole",
                avatarInitials = "MC",
                category = PerformanceCategory.MUSIC,
                genre = "Acoustic Ballad",
                durationSec = 185,
                lyricsOrScript = """
                    Dawn breaks upon the canyon wall,
                    and in the silence, hear the call.
                    Strings vibrating in the morning air,
                    lifting heavy burdens we used to bear.
                    
                    Walk with me into the sun,
                    the hardest battles have been won.
                    Just one acoustic song to heal,
                    everything we learned to feel.
                """.trimIndent(),
                visualizerTheme = VisualizerTheme.CYAN_SPECTRUM,
                audioType = SynthAudioType.ACOUSTIC_SOUL,
                viewsCount = 3120,
                likesCount = 1260,
                votesCount = 680,
                cheersCount = 290,
                pollId = "poll_soul_clash"
            ),
            Performance(
                id = "perf_music_3",
                title = "Cipher on 5th & Vinyl Dust",
                artistId = "creator_5",
                artistName = "Kaelen Flux",
                avatarInitials = "KF",
                category = PerformanceCategory.MUSIC,
                genre = "Rap & Boom-Bap",
                durationSec = 160,
                lyricsOrScript = """
                    Needle drops on a warm 45,
                    coffee steaming, feeling so alive.
                    Snare snaps tight on the second beat,
                    boom-bap bars flowing down the street.
                    
                    Rhodes keys floating in the morning light,
                    freestyle cadence cutting through the night.
                """.trimIndent(),
                visualizerTheme = VisualizerTheme.COSMIC_VINYL,
                audioType = SynthAudioType.HIPHOP_BEAT,
                viewsCount = 2640,
                likesCount = 980,
                votesCount = 540,
                cheersCount = 190,
                pollId = "poll_weekly_showcase",
                styleId = "rap"
            ),
            Performance(
                id = "perf_hip_hop_1",
                title = "808 Skyline & Golden Era Cuts",
                artistId = "creator_5",
                artistName = "Kaelen Flux",
                avatarInitials = "KF",
                category = PerformanceCategory.MUSIC,
                genre = "Hip-Hop",
                durationSec = 178,
                lyricsOrScript = """
                    808 sub bass rattling the avenue,
                    turntable needle dropping crisp and true.
                    Brass chops layered on a breakbeat groove,
                    golden-era hip-hop making the arena move.
                    
                    Scratch the chorus back in stereo flight,
                    crowd hands waving under stage spotlight.
                """.trimIndent(),
                visualizerTheme = VisualizerTheme.COSMIC_VINYL,
                audioType = SynthAudioType.HIPHOP_BEAT,
                viewsCount = 240,
                likesCount = 180,
                votesCount = 65,
                cheersCount = 45,
                pollId = "poll_weekly_showcase",
                createdAtTimestamp = now - dayMs * 2,
                styleId = "hip_hop",
                audienceRating = 4.86f,
                ratingsCount = 198
            ),
            Performance(
                id = "perf_jazz_1",
                title = "Blue Note Midnight Improv",
                artistId = "creator_8",
                artistName = "Julian Miles",
                avatarInitials = "JM",
                category = PerformanceCategory.MUSIC,
                genre = "Vocal & Nu-Jazz",
                durationSec = 205,
                lyricsOrScript = """
                    Brass reflections on rain-slicked avenues,
                    tenor sax trading fours with midnight blues.
                    Syncopated brushwork whispers on the snare,
                    velvet chords suspended in the smoky air.
                    
                    Every solo bends the measure free,
                    improvising where tomorrow wants to be.
                """.trimIndent(),
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.PIANO_WARMTH,
                viewsCount = 2890,
                likesCount = 1190,
                votesCount = 635,
                cheersCount = 245,
                pollId = "poll_weekly_showcase",
                styleId = "jazz"
            ),
            Performance(
                id = "perf_spoken_3",
                title = "The Architecture of a Mother's Lullaby",
                artistId = "creator_6",
                artistName = "Sora Chen",
                avatarInitials = "SC",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Cinematic Spoken Word",
                durationSec = 180,
                lyricsOrScript = """
                    Before there were blueprints,
                    there were lullabies.
                    
                    Soft melodies that built walls thicker than stone,
                    woven out of humming and steady breaths.
                    
                    She sang in a language older than borders,
                    a melody that told fear:
                    'You have no jurisdiction here.'
                """.trimIndent(),
                visualizerTheme = VisualizerTheme.NEON_WAVES,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                viewsCount = 2100,
                likesCount = 890,
                votesCount = 490,
                cheersCount = 175,
                pollId = "poll_weekly_showcase"
            ),
            Performance(
                id = "perf_music_4",
                title = "Neon Echoes",
                artistId = "creator_7",
                artistName = "Elena Vance",
                avatarInitials = "EV",
                category = PerformanceCategory.MUSIC,
                genre = "Synthwave Chill",
                durationSec = 170,
                lyricsOrScript = """
                    Electric skyline in magenta hues,
                    nothing left between us now to lose.
                    Arpeggios driving through the purple rain,
                    synthesizer washes away the pain.
                    
                    Speed through the midnight grid,
                    remembering everything we did.
                """.trimIndent(),
                visualizerTheme = VisualizerTheme.CYAN_SPECTRUM,
                audioType = SynthAudioType.SYNTH_CHILL,
                viewsCount = 1980,
                likesCount = 810,
                votesCount = 410,
                cheersCount = 140,
                pollId = "poll_weekly_showcase",
                createdAtTimestamp = now - dayMs * 3,
                audienceRating = 4.78f,
                ratingsCount = 145
            ),
            // Chronological release history for featured creators to power multi-release rating trends
            Performance(
                id = "perf_maya_hist_1",
                title = "Subway Lanterns at Dusk",
                artistId = "creator_1",
                artistName = "Maya Vance",
                avatarInitials = "MV",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Spoken Word",
                durationSec = 142,
                lyricsOrScript = "Lanterns swinging beneath iron trestles, carrying quiet promises home.",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                viewsCount = 620,
                likesCount = 180,
                votesCount = 40,
                cheersCount = 24,
                createdAtTimestamp = now - dayMs * 18,
                styleId = "spoken_word",
                audienceRating = 4.58f,
                ratingsCount = 64
            ),
            Performance(
                id = "perf_maya_hist_2",
                title = "Paper Cranes on Fire Escapes",
                artistId = "creator_1",
                artistName = "Maya Vance",
                avatarInitials = "MV",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Slam Poetry",
                durationSec = 156,
                lyricsOrScript = "Origami wings folded from yesterday's headlines, learning wind.",
                visualizerTheme = VisualizerTheme.COSMIC_VINYL,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                viewsCount = 790,
                likesCount = 225,
                votesCount = 50,
                cheersCount = 31,
                createdAtTimestamp = now - dayMs * 13,
                styleId = "poetry",
                audienceRating = 4.72f,
                ratingsCount = 88
            ),
            Performance(
                id = "perf_maya_hist_3",
                title = "Rainwater Gospel",
                artistId = "creator_1",
                artistName = "Maya Vance",
                avatarInitials = "MV",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Spoken Word",
                durationSec = 164,
                lyricsOrScript = "Storm drains singing in three-part harmony down Lenox Avenue.",
                visualizerTheme = VisualizerTheme.NEON_WAVES,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                viewsCount = 910,
                likesCount = 260,
                votesCount = 60,
                cheersCount = 38,
                createdAtTimestamp = now - dayMs * 8,
                styleId = "spoken_word",
                audienceRating = 4.68f,
                ratingsCount = 102
            ),
            Performance(
                id = "perf_maya_hist_4",
                title = "Brass & Bone Sonnet",
                artistId = "creator_1",
                artistName = "Maya Vance",
                avatarInitials = "MV",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Slam Poetry",
                durationSec = 168,
                lyricsOrScript = "Every syllable forged where storefront neon meets midnight jazz.",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                viewsCount = 1150,
                likesCount = 340,
                votesCount = 70,
                cheersCount = 49,
                createdAtTimestamp = now - dayMs * 4,
                styleId = "poetry",
                audienceRating = 4.86f,
                ratingsCount = 136
            ),
            Performance(
                id = "perf_alicia_hist_1",
                title = "Amber Cassette Tape",
                artistId = "creator_2",
                artistName = "Alicia Ray",
                avatarInitials = "AR",
                category = PerformanceCategory.MUSIC,
                genre = "Neo-Soul & R&B",
                durationSec = 184,
                lyricsOrScript = "Warm tape hiss and Fender Rhodes chords at golden hour.",
                visualizerTheme = VisualizerTheme.NEON_WAVES,
                audioType = SynthAudioType.ACOUSTIC_SOUL,
                viewsCount = 680,
                likesCount = 210,
                votesCount = 40,
                cheersCount = 28,
                createdAtTimestamp = now - dayMs * 19,
                styleId = "soul_rnb",
                audienceRating = 4.62f,
                ratingsCount = 74
            ),
            Performance(
                id = "perf_alicia_hist_2",
                title = "Velvet Windowpane",
                artistId = "creator_2",
                artistName = "Alicia Ray",
                avatarInitials = "AR",
                category = PerformanceCategory.MUSIC,
                genre = "Acoustic Ballad",
                durationSec = 192,
                lyricsOrScript = "Fingerpicked six-string whispers against the evening rain.",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.ACOUSTIC_SOUL,
                viewsCount = 830,
                likesCount = 255,
                votesCount = 50,
                cheersCount = 35,
                createdAtTimestamp = now - dayMs * 14,
                styleId = "acoustic",
                audienceRating = 4.76f,
                ratingsCount = 95
            ),
            Performance(
                id = "perf_alicia_hist_3",
                title = "Blue Hour Falsetto",
                artistId = "creator_2",
                artistName = "Alicia Ray",
                avatarInitials = "AR",
                category = PerformanceCategory.MUSIC,
                genre = "Vocal & Nu-Jazz",
                durationSec = 198,
                lyricsOrScript = "Floating above upright basslines in twilight blue.",
                visualizerTheme = VisualizerTheme.CYAN_SPECTRUM,
                audioType = SynthAudioType.PIANO_WARMTH,
                viewsCount = 990,
                likesCount = 310,
                votesCount = 60,
                cheersCount = 44,
                createdAtTimestamp = now - dayMs * 9,
                styleId = "jazz",
                audienceRating = 4.84f,
                ratingsCount = 118
            ),
            Performance(
                id = "perf_alicia_hist_4",
                title = "Starlight Harmonies",
                artistId = "creator_2",
                artistName = "Alicia Ray",
                avatarInitials = "AR",
                category = PerformanceCategory.MUSIC,
                genre = "Neo-Soul & R&B",
                durationSec = 204,
                lyricsOrScript = "Stacked vocal harmonies rising into the amphitheater dome.",
                visualizerTheme = VisualizerTheme.NEON_WAVES,
                audioType = SynthAudioType.ACOUSTIC_SOUL,
                viewsCount = 1240,
                likesCount = 390,
                votesCount = 70,
                cheersCount = 56,
                createdAtTimestamp = now - dayMs * 4,
                styleId = "soul_rnb",
                audienceRating = 4.91f,
                ratingsCount = 152
            )
        )
    }

    private fun getInitialPolls(): List<Poll> {
        return listOf(
            Poll(
                id = "poll_slam_finals",
                title = "Grand Slam Poetry Finals: Who Speaks Truth to Power?",
                description = "Vote for the ultimate spoken word champion of the 2026 National Poetry Arena.",
                category = PerformanceCategory.SPOKEN_WORD,
                badge = "LIVE CHAMPIONSHIP",
                endsInHours = 14,
                candidates = listOf(
                    PollCandidate(
                        candidateId = "cand_maya",
                        performanceId = "perf_spoken_1",
                        title = "Concrete Roots & Starlit Canopies",
                        artistName = "Maya Vance",
                        category = PerformanceCategory.SPOKEN_WORD,
                        votesCount = 892,
                        avatarColorHex = 0xFF00F5D4
                    ),
                    PollCandidate(
                        candidateId = "cand_darnell",
                        performanceId = "perf_spoken_2",
                        title = "The Clockmaker's Last Confession",
                        artistName = "Darnell Cruz",
                        category = PerformanceCategory.SPOKEN_WORD,
                        votesCount = 745,
                        avatarColorHex = 0xFF9D4EDD
                    )
                )
            ),
            Poll(
                id = "poll_soul_clash",
                title = "Acoustic Soul & R&B Face-Off: Best Vocal Artistry",
                description = "Spectators decide which soul artist brought the deepest emotional resonance to the stage.",
                category = PerformanceCategory.MUSIC,
                badge = "HEAD-TO-HEAD",
                endsInHours = 22,
                candidates = listOf(
                    PollCandidate(
                        candidateId = "cand_alicia",
                        performanceId = "perf_music_1",
                        title = "Midnight Reverie",
                        artistName = "Alicia Ray",
                        category = PerformanceCategory.MUSIC,
                        votesCount = 1140,
                        avatarColorHex = 0xFFF72585
                    ),
                    PollCandidate(
                        candidateId = "cand_marcus",
                        performanceId = "perf_music_2",
                        title = "Golden Horizon",
                        artistName = "Marcus Cole",
                        category = PerformanceCategory.MUSIC,
                        votesCount = 680,
                        avatarColorHex = 0xFFFFB703
                    )
                )
            ),
            Poll(
                id = "poll_weekly_showcase",
                title = "Audience Choice: Best Breakout Creator of the Week",
                description = "Multi-genre talent showdown celebrating new music and lyrical storytelling.",
                category = PerformanceCategory.MUSIC,
                badge = "COMMUNITY POLL",
                endsInHours = 48,
                candidates = listOf(
                    PollCandidate(
                        candidateId = "cand_kaelen",
                        performanceId = "perf_music_3",
                        title = "Coffee & Vinyl Dust",
                        artistName = "Kaelen Flux",
                        category = PerformanceCategory.MUSIC,
                        votesCount = 540,
                        avatarColorHex = 0xFF00BBF9
                    ),
                    PollCandidate(
                        candidateId = "cand_sora",
                        performanceId = "perf_spoken_3",
                        title = "Architecture of a Mother's Lullaby",
                        artistName = "Sora Chen",
                        category = PerformanceCategory.SPOKEN_WORD,
                        votesCount = 490,
                        avatarColorHex = 0xFF9D4EDD
                    ),
                    PollCandidate(
                        candidateId = "cand_elena",
                        performanceId = "perf_music_4",
                        title = "Neon Echoes",
                        artistName = "Elena Vance",
                        category = PerformanceCategory.MUSIC,
                        votesCount = 410,
                        avatarColorHex = 0xFFF72585
                    )
                )
            )
        )
    }

    private fun getInitialLiveSessions(): List<LiveSession> {
        return listOf(
            LiveSession(
                id = "live_maya_01",
                creatorId = "creator_1",
                creatorName = "Maya Vance",
                title = "Midnight Slam: Live Spoken Word & City Echoes",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Live Slam Poetry",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                activePollId = "poll_slam_finals",
                spectatorCount = 284,
                isLive = true,
                pinnedAnnouncement = "Round 2 Slam Cipher is open — drop 👏 Ovation or 🔥 Fire for your top metaphor!",
                reactionCounts = mapOf(
                    StageReactionType.FIRE to 142,
                    StageReactionType.MIC_DROP to 64,
                    StageReactionType.OVATION to 98,
                    StageReactionType.HEART to 53,
                    StageReactionType.ENCORE to 31
                ),
                chatMessages = listOf(
                    LiveChatMessage("msg_1", "Marcus J.", "That opening metaphor gave me goosebumps! 🔥", isCheer = true, senderAvatarColorHex = 0xFFFFB703),
                    LiveChatMessage("msg_2", "LyricalSoul", "Voted for Maya! Outstanding delivery 👏", senderAvatarColorHex = 0xFF00F5D4),
                    LiveChatMessage("msg_3", "Devon_Beats", "The ambient backing track fits this so perfectly", senderAvatarColorHex = 0xFF9D4EDD),
                    LiveChatMessage("msg_4", "Elena V.", "Encore please!! Best in spoken word tonight 🙌", isCheer = true, senderAvatarColorHex = 0xFFF72585)
                )
            ),
            LiveSession(
                id = "live_alicia_02",
                creatorId = "creator_2",
                creatorName = "Alicia Ray",
                title = "Soul Sessions: Live Acoustic Jam & Vocal Improv",
                category = PerformanceCategory.MUSIC,
                genre = "Neo-Soul & R&B",
                visualizerTheme = VisualizerTheme.NEON_WAVES,
                audioType = SynthAudioType.ACOUSTIC_SOUL,
                activePollId = "poll_soul_clash",
                spectatorCount = 412,
                isLive = true,
                styleId = "soul_rnb",
                reactionCounts = mapOf(
                    StageReactionType.FIRE to 188,
                    StageReactionType.MIC_DROP to 72,
                    StageReactionType.OVATION to 134,
                    StageReactionType.HEART to 165,
                    StageReactionType.ENCORE to 59
                ),
                chatMessages = listOf(
                    LiveChatMessage("msg_a1", "Chloe_K", "Those vocal runs are unreal 💖", isCheer = true, senderAvatarColorHex = 0xFFF72585),
                    LiveChatMessage("msg_a2", "JazzFanatic", "Just voted in the poll! Alicia has my vote 100%", senderAvatarColorHex = 0xFF00BBF9),
                    LiveChatMessage("msg_a3", "T-Rock", "The groove is unmatched tonight 🎵", senderAvatarColorHex = 0xFFFFB703)
                )
            ),
            LiveSession(
                id = "live_kaelen_rap_03",
                creatorId = "creator_5",
                creatorName = "Kaelen Flux",
                title = "Rooftop Freestyle Rap Cipher & Boom-Bap Beats",
                category = PerformanceCategory.MUSIC,
                genre = "Rap & Hip-Hop",
                visualizerTheme = VisualizerTheme.COSMIC_VINYL,
                audioType = SynthAudioType.HIPHOP_BEAT,
                activePollId = "poll_weekly_showcase",
                spectatorCount = 318,
                isLive = true,
                styleId = "rap",
                chatMessages = listOf(
                    LiveChatMessage("msg_r1", "VerseKing", "Double-time flow on that second verse was crazy 🔥", isCheer = true, senderAvatarColorHex = 0xFFF72585),
                    LiveChatMessage("msg_r2", "CrateDigger", "SP-404 drums hitting crisp tonight!", senderAvatarColorHex = 0xFF00F5D4)
                )
            ),
            LiveSession(
                id = "live_julian_jazz_04",
                creatorId = "creator_8",
                creatorName = "Julian Miles",
                title = "Velvet Blue Note: Live Nu-Jazz Quartet & Brass Solos",
                category = PerformanceCategory.MUSIC,
                genre = "Vocal & Nu-Jazz",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.PIANO_WARMTH,
                activePollId = "poll_weekly_showcase",
                spectatorCount = 246,
                isLive = true,
                styleId = "jazz",
                chatMessages = listOf(
                    LiveChatMessage("msg_j1", "MilesAhead", "That piano & sax modulation gave me chills 🎷", isCheer = true, senderAvatarColorHex = 0xFFFFB703),
                    LiveChatMessage("msg_j2", "Nora_Sings", "Pure late-night jazz magic!", senderAvatarColorHex = 0xFF9D4EDD)
                )
            ),
            LiveSession(
                id = "live_darnell_spoken_05",
                creatorId = "creator_3",
                creatorName = "Darnell Cruz",
                title = "Unplugged Griot Circle: Live Spoken Word & Storytelling",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Spoken Word",
                visualizerTheme = VisualizerTheme.PULSE_ORB,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                activePollId = "poll_slam_finals",
                spectatorCount = 195,
                isLive = true,
                styleId = "spoken_word",
                chatMessages = listOf(
                    LiveChatMessage("msg_s1", "PoeticJustice", "Every line lands with weight 👏", isCheer = true, senderAvatarColorHex = 0xFF00B4D8)
                )
            ),
            LiveSession(
                id = "live_hip_hop_06",
                creatorId = "creator_5",
                creatorName = "Kaelen Flux",
                title = "808 Turntable Sessions: Live Hip-Hop & Breakbeats",
                category = PerformanceCategory.MUSIC,
                genre = "Hip-Hop",
                visualizerTheme = VisualizerTheme.COSMIC_VINYL,
                audioType = SynthAudioType.HIPHOP_BEAT,
                activePollId = "poll_weekly_showcase",
                spectatorCount = 294,
                isLive = true,
                styleId = "hip_hop",
                chatMessages = listOf(
                    LiveChatMessage("msg_hh1", "BreakBeatKid", "Those 808 chops and scratches are unreal! 🎧", isCheer = true, senderAvatarColorHex = 0xFFFF6B35),
                    LiveChatMessage("msg_hh2", "GoldEraFan", "Pure hip-hop energy on stage tonight!", senderAvatarColorHex = 0xFF00F5D4)
                )
            ),
            // Past Ended Sessions for Performer History & Statistics
            LiveSession(
                id = "past_maya_01",
                creatorId = "creator_1",
                creatorName = "Maya Vance",
                title = "Concrete Epics: Poetry Under the Streetlights",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Urban Spoken Word",
                visualizerTheme = VisualizerTheme.COSMIC_VINYL,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                activePollId = null,
                spectatorCount = 540,
                isLive = false,
                startedAt = System.currentTimeMillis() - 86400000L * 2
            ),
            LiveSession(
                id = "past_maya_02",
                creatorId = "creator_1",
                creatorName = "Maya Vance",
                title = "Ancestral Echoes: Live Storytelling & Drum Rhythm",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Cultural Griot",
                visualizerTheme = VisualizerTheme.STAGE_LIGHTS,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                activePollId = null,
                spectatorCount = 680,
                isLive = false,
                startedAt = System.currentTimeMillis() - 86400000L * 5
            ),
            LiveSession(
                id = "past_alicia_01",
                creatorId = "creator_2",
                creatorName = "Alicia Ray",
                title = "Velvet Harmonies: Late Night Jazz & Vocal Solos",
                category = PerformanceCategory.MUSIC,
                genre = "Soul & Jazz",
                visualizerTheme = VisualizerTheme.CYAN_SPECTRUM,
                audioType = SynthAudioType.ACOUSTIC_SOUL,
                activePollId = null,
                spectatorCount = 720,
                isLive = false,
                startedAt = System.currentTimeMillis() - 86400000L * 3
            ),
            LiveSession(
                id = "past_darnell_01",
                creatorId = "creator_3",
                creatorName = "Darnell Cruz",
                title = "Voices Unchained: Rhythmic Street Verse",
                category = PerformanceCategory.SPOKEN_WORD,
                genre = "Political Poetry",
                visualizerTheme = VisualizerTheme.PULSE_ORB,
                audioType = SynthAudioType.POETRY_AMBIENCE,
                activePollId = null,
                spectatorCount = 390,
                isLive = false,
                startedAt = System.currentTimeMillis() - 86400000L * 4
            ),
            LiveSession(
                id = "past_user_me_01",
                creatorId = "creator_user_me",
                creatorName = "You (Stage Master)",
                title = "Studio Debut: First Live Stream & Music Improv",
                category = PerformanceCategory.MUSIC,
                genre = "Original Showcase",
                visualizerTheme = VisualizerTheme.NEON_WAVES,
                audioType = SynthAudioType.SYNTH_CHILL,
                activePollId = null,
                spectatorCount = 210,
                isLive = false,
                startedAt = System.currentTimeMillis() - 86400000L * 1
            )
        )
    }

    private fun getInitialCreatorProfiles(): Map<String, CreatorProfile> {
        return mapOf(
            "creator_1" to CreatorProfile(
                creatorId = "creator_1",
                artistName = "Maya Vance",
                stageTitle = "Grand Slam Poet & Storyteller",
                bio = "2x National Poetry Slam Finalist. Weaving urban realism, ancestral memory, and lyrical jazz into spoken word tapestries.",
                primaryCategory = PerformanceCategory.SPOKEN_WORD,
                avatarColorHex = 0xFF00F5D4,
                audienceRating = 4.9f,
                totalAudienceRatingsCount = 1840,
                socialSpotify = "open.spotify.com/artist/mayavance",
                socialInstagram = "@mayavance_poetry",
                socialYoutube = "youtube.com/@mayavancestage",
                socialWebsite = "https://mayavance.art"
            ),
            "creator_2" to CreatorProfile(
                creatorId = "creator_2",
                artistName = "Alicia Ray",
                stageTitle = "Neo-Soul Vocalist & Producer",
                bio = "Blending acoustic warmth with lush R&B chords and heartfelt falsettos. Believer that music heals what words cannot reach.",
                primaryCategory = PerformanceCategory.MUSIC,
                avatarColorHex = 0xFFF72585,
                audienceRating = 4.95f,
                totalAudienceRatingsCount = 2210,
                socialSpotify = "open.spotify.com/artist/aliciaraysoul",
                socialInstagram = "@aliciaraysounds",
                socialYoutube = "youtube.com/@aliciaraylive",
                socialWebsite = "https://aliciaraymusic.com"
            ),
            "creator_3" to CreatorProfile(
                creatorId = "creator_3",
                artistName = "Darnell Cruz",
                stageTitle = "Spoken Word Griot & Activist",
                bio = "Voices from the street corner to the auditorium. Exploring time, resilience, and brotherhood through rhythmic verse.",
                primaryCategory = PerformanceCategory.SPOKEN_WORD,
                avatarColorHex = 0xFF9D4EDD,
                audienceRating = 4.8f,
                totalAudienceRatingsCount = 980,
                socialSpotify = "open.spotify.com/artist/darnellcruz",
                socialInstagram = "@darnellcruz_words",
                socialYoutube = "youtube.com/@darnellcruzofficial",
                socialWebsite = "https://darnellcruz.org"
            ),
            "creator_4" to CreatorProfile(
                creatorId = "creator_4",
                artistName = "Marcus Cole",
                stageTitle = "Acoustic Balladeer & Songsmith",
                bio = "Fingerstyle guitar and honest storytelling for late night drives and quiet reflections.",
                primaryCategory = PerformanceCategory.MUSIC,
                avatarColorHex = 0xFFFFB703,
                audienceRating = 4.75f,
                totalAudienceRatingsCount = 890,
                socialSpotify = "open.spotify.com/artist/marcuscole",
                socialInstagram = "@marcuscole_acoustic",
                socialYoutube = "youtube.com/@marcuscolelive",
                socialWebsite = "https://marcuscole.net"
            ),
            "creator_5" to CreatorProfile(
                creatorId = "creator_5",
                artistName = "Kaelen Flux",
                stageTitle = "Rap Lyricist & Boom-Bap Producer",
                bio = "Crisp boom-bap drums, warm SP-404 chops, and razor-sharp freestyle bars.",
                primaryCategory = PerformanceCategory.MUSIC,
                avatarColorHex = 0xFF00BBF9,
                audienceRating = 4.85f,
                totalAudienceRatingsCount = 1120,
                socialSpotify = "open.spotify.com/artist/kaelenflux",
                socialInstagram = "@kaelenfluxbeats",
                socialYoutube = "youtube.com/@kaelenfluxmusic",
                socialWebsite = "https://kaelenflux.io"
            ),
            "creator_6" to CreatorProfile(
                creatorId = "creator_6",
                artistName = "Sora Chen",
                stageTitle = "Cinematic Monologist",
                bio = "Crafting intimate cinematic monologues and lyrical spoken word narratives.",
                primaryCategory = PerformanceCategory.SPOKEN_WORD,
                avatarColorHex = 0xFF9D4EDD,
                audienceRating = 4.88f,
                totalAudienceRatingsCount = 940,
                socialSpotify = "open.spotify.com/artist/sorachen",
                socialInstagram = "@sorachen_words",
                socialYoutube = "youtube.com/@sorachenstage",
                socialWebsite = "https://sorachen.art"
            ),
            "creator_7" to CreatorProfile(
                creatorId = "creator_7",
                artistName = "Elena Vance",
                stageTitle = "Synthwave Dreamer",
                bio = "Analog synthesizers, midnight neon arpeggios, and dream-pop vocal atmospheres.",
                primaryCategory = PerformanceCategory.MUSIC,
                avatarColorHex = 0xFFF72585,
                audienceRating = 4.78f,
                totalAudienceRatingsCount = 760,
                socialSpotify = "open.spotify.com/artist/elenavance",
                socialInstagram = "@elenavance_synth",
                socialYoutube = "youtube.com/@elenavancelive",
                socialWebsite = "https://elenavance.io"
            ),
            "creator_8" to CreatorProfile(
                creatorId = "creator_8",
                artistName = "Julian Miles",
                stageTitle = "Nu-Jazz Saxophonist & Bandleader",
                bio = "Modern modal jazz meets midnight soul. Leading live brass & Rhodes improvisations on the VersePulse stage.",
                primaryCategory = PerformanceCategory.MUSIC,
                avatarColorHex = 0xFFFFB703,
                audienceRating = 4.92f,
                totalAudienceRatingsCount = 1360,
                socialSpotify = "open.spotify.com/artist/julianmilesjazz",
                socialInstagram = "@julianmiles_quartet",
                socialYoutube = "youtube.com/@julianmileslive",
                socialWebsite = "https://julianmiles.jazz"
            ),
            "creator_user_me" to CreatorProfile(
                creatorId = "creator_user_me",
                artistName = "You (Stage Master)",
                stageTitle = "Rising Multi-Genre Creator",
                bio = "Creating fresh music tracks and heartfelt spoken word performances. Live on the VersePulse arena stage!",
                primaryCategory = PerformanceCategory.MUSIC,
                avatarColorHex = 0xFF9D4EDD,
                audienceRating = 5.0f,
                totalAudienceRatingsCount = 42,
                socialSpotify = "@spotify/yourstage",
                socialInstagram = "@your_artist_handle",
                socialYoutube = "youtube.com/@yourchannel",
                socialWebsite = "https://versepulse.app/me",
                isCurrentUser = true
            )
        )
    }
}
