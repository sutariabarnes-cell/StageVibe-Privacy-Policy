package com.example.data

import android.content.Context
import com.example.model.LiveSession
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.PerformanceStage
import com.example.model.StageSpectatorVote
import com.example.model.StageStatus
import com.example.model.StageVotingCounter
import com.example.model.StageVotingSnapshot
import com.example.model.SynthAudioType
import com.example.model.VisualizerTheme
import com.example.model.VoteTier
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Cloud Firestore service managing live performance stages (`/stages/{stageId}`),
 * real-time per-performer spectator voting counters (`/stages/{stageId}/voting_counters/{performerId}`),
 * and authenticated spectator vote receipts (`/stages/{stageId}/spectator_votes/{voteId}`).
 *
 * Always connects to the provisioned named database configured in `R.string.firestore_database_id`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveStageVotingService(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    constructor(context: Context) : this(
        firestore = FirestoreService.createFirestore(context),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        checkNotNull(auth.currentUser?.uid) {
            "User must be signed in before executing live stage or spectator voting writes"
        }

    private fun currentUserDisplayName(fallback: String = "Stage Spectator"): String =
        auth.currentUser?.displayName?.takeIf { it.isNotBlank() }?.take(120) ?: fallback

    private fun observeAuthUid(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    // =========================================================================
    // 1. Live Performance Stage Management (/stages/{stageId})
    // =========================================================================

    /**
     * Creates a new live performance stage in `/stages/{stageId}` and optionally initializes
     * per-performer spectator voting counters in a single atomic batch.
     */
    suspend fun createStage(
        title: String,
        category: PerformanceCategory,
        genre: String = category.displayName,
        description: String = "",
        hostId: String? = null,
        hostName: String? = null,
        status: StageStatus = StageStatus.LIVE,
        activePerformerId: String? = null,
        activePerformerName: String? = null,
        activePerformanceId: String? = null,
        activePerformanceTitle: String? = null,
        visualizerTheme: VisualizerTheme = VisualizerTheme.STAGE_LIGHTS,
        audioType: SynthAudioType = SynthAudioType.ACOUSTIC_SOUL,
        activePollId: String? = null,
        sessionId: String? = null,
        initialSpectatorCount: Int = 1,
        initialPerformers: List<Performance> = emptyList(),
        stageId: String? = null
    ): Result<PerformanceStage> {
        val rawStageId = stageId?.takeIf { it.isNotBlank() }
            ?: "stage_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val sanitizedStageId = sanitizeId(rawStageId)
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizedStageId)

        return try {
            require(status != StageStatus.ENDED) { "Cannot create a stage directly in ENDED status" }
            val uid = requireUserId()
            val resolvedHostId = sanitizeId(hostId?.takeIf { it.isNotBlank() } ?: uid)
            val resolvedHostName = (hostName?.takeIf { it.isNotBlank() } ?: currentUserDisplayName("Stage Host")).take(120)
            val resolvedTitle = title.trim().ifBlank { "Live ${category.displayName} Stage" }.take(160)
            val resolvedGenre = genre.trim().ifBlank { category.displayName }.take(80)
            val resolvedDesc = description.trim().take(500)
            val resolvedSpectators = initialSpectatorCount.coerceIn(0, 1_000_000).toLong()

            val primaryPerf = initialPerformers.firstOrNull()
            val resolvedActivePerformerId = activePerformerId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }
                ?: primaryPerf?.artistId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }
                ?: resolvedHostId
            val resolvedActivePerformerName = (
                activePerformerName?.takeIf { it.isNotBlank() }
                    ?: primaryPerf?.artistName?.takeIf { it.isNotBlank() }
                    ?: resolvedHostName
                ).take(120)
            val resolvedActivePerfId = activePerformanceId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }
                ?: primaryPerf?.id?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }
            val resolvedActivePerfTitle = (
                activePerformanceTitle?.takeIf { it.isNotBlank() }
                    ?: primaryPerf?.title?.takeIf { it.isNotBlank() }
                )?.take(160)
            val sanitizedPollId = activePollId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }
            val sanitizedSessionId = sessionId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }

            val stagePayload = mapOf<String, Any?>(
                "ownerUid" to uid,
                "hostId" to resolvedHostId,
                "hostName" to resolvedHostName,
                "title" to resolvedTitle,
                "category" to category.name,
                "genre" to resolvedGenre,
                "description" to resolvedDesc.takeIf { it.isNotBlank() },
                "status" to status.name,
                "activePerformerId" to resolvedActivePerformerId,
                "activePerformerName" to resolvedActivePerformerName,
                "activePerformanceId" to resolvedActivePerfId,
                "activePerformanceTitle" to resolvedActivePerfTitle,
                "visualizerTheme" to visualizerTheme.name,
                "audioType" to audioType.name,
                "activePollId" to sanitizedPollId,
                "sessionId" to sanitizedSessionId,
                "spectatorCount" to resolvedSpectators,
                "totalStageVotes" to 0L,
                "totalBallotsCast" to 0L,
                "totalCheersCount" to 0L,
                "visibility" to VISIBILITY_PUBLIC,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val batch = firestore.batch()
            batch.set(stageRef, stagePayload)

            if (initialPerformers.isNotEmpty()) {
                val distinctPerformers = initialPerformers.distinctBy { sanitizeId(it.artistId.ifBlank { it.id }) }
                distinctPerformers.forEachIndexed { index, perf ->
                    val perfCounterId = sanitizeId(perf.artistId.ifBlank { perf.id })
                    val counterRef = stageRef.collection(SUBCOLLECTION_VOTING_COUNTERS).document(perfCounterId)
                    val counterPayload = buildInitialCounterPayload(
                        stageId = sanitizedStageId,
                        performerId = perfCounterId,
                        performerName = perf.artistName.ifBlank { resolvedHostName },
                        category = perf.category,
                        performanceId = perf.id,
                        performanceTitle = perf.title,
                        genre = perf.genre,
                        avatarInitials = perf.avatarInitials,
                        isActiveOnStage = index == 0
                    )
                    batch.set(counterRef, counterPayload)
                }
            } else {
                val hostCounterRef = stageRef.collection(SUBCOLLECTION_VOTING_COUNTERS)
                    .document(resolvedActivePerformerId)
                val hostCounterPayload = buildInitialCounterPayload(
                    stageId = sanitizedStageId,
                    performerId = resolvedActivePerformerId,
                    performerName = resolvedActivePerformerName,
                    category = category,
                    performanceId = resolvedActivePerfId,
                    performanceTitle = resolvedActivePerfTitle ?: resolvedTitle,
                    genre = resolvedGenre,
                    avatarInitials = resolvedActivePerformerName.take(2).uppercase().ifBlank { "SH" },
                    isActiveOnStage = true
                )
                batch.set(hostCounterRef, hostCounterPayload)
            }

            batch.commit().await()

            Result.success(
                PerformanceStage(
                    id = sanitizedStageId,
                    ownerUid = uid,
                    hostId = resolvedHostId,
                    hostName = resolvedHostName,
                    title = resolvedTitle,
                    category = category.name,
                    genre = resolvedGenre,
                    description = resolvedDesc,
                    status = status.name,
                    activePerformerId = resolvedActivePerformerId,
                    activePerformerName = resolvedActivePerformerName,
                    activePerformanceId = resolvedActivePerfId,
                    activePerformanceTitle = resolvedActivePerfTitle,
                    visualizerTheme = visualizerTheme.name,
                    audioType = audioType.name,
                    activePollId = sanitizedPollId,
                    sessionId = sanitizedSessionId,
                    spectatorCount = resolvedSpectators,
                    totalStageVotes = 0L,
                    totalBallotsCast = 0L,
                    totalCheersCount = 0L,
                    visibility = VISIBILITY_PUBLIC
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, stageRef.path)
            Result.failure(e)
        }
    }

    /**
     * Synchronizes a [LiveSession] with `/stages/{sessionId}` so live broadcasts
     * maintain an authoritative stage document and spectator voting counter subcollection.
     */
    suspend fun syncLiveSessionStage(
        session: LiveSession,
        stagePerformers: List<Performance> = emptyList()
    ): Result<PerformanceStage> {
        val sanitizedStageId = sanitizeId(session.id)
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizedStageId)
        return try {
            val uid = requireUserId()
            val existingSnap = stageRef.get().await()
            if (!existingSnap.exists()) {
                if (!session.isLive) {
                    return Result.failure(IllegalStateException("Stage $sanitizedStageId is already inactive"))
                }
                createStage(
                    title = session.title,
                    category = session.category,
                    genre = session.genre,
                    hostId = session.creatorId.ifBlank { uid },
                    hostName = session.creatorName,
                    status = StageStatus.LIVE,
                    visualizerTheme = session.visualizerTheme,
                    audioType = session.audioType,
                    activePollId = session.activePollId,
                    sessionId = sanitizedStageId,
                    initialSpectatorCount = session.spectatorCount,
                    initialPerformers = stagePerformers,
                    stageId = sanitizedStageId
                )
            } else {
                val currentStatus = existingSnap.getString("status") ?: StageStatus.LIVE.name
                if (currentStatus != StageStatus.ENDED.name && existingSnap.getString("ownerUid") == uid) {
                    val targetStatus = if (session.isLive) StageStatus.LIVE.name else StageStatus.ENDED.name
                    stageRef.update(
                        mapOf(
                            "title" to session.title.ifBlank { "Live Stage" }.take(160),
                            "genre" to session.genre.ifBlank { session.category.displayName }.take(80),
                            "status" to targetStatus,
                            "spectatorCount" to session.spectatorCount.coerceIn(0, 1_000_000).toLong(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }
                val updated = stageRef.get().await().toPerformanceStageOrNull()
                    ?: throw IllegalStateException("Failed to read synced stage $sanitizedStageId")
                Result.success(updated)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, stageRef.path)
            Result.failure(e)
        }
    }

    /**
     * Updates the lifecycle status (`WAITING -> LIVE` or `LIVE -> ENDED`) of a stage.
     * Restricted to the stage host (`ownerUid`).
     */
    suspend fun setStageStatus(stageId: String, status: StageStatus): Result<Unit> {
        val sanitizedStageId = sanitizeId(stageId)
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizedStageId)
        return try {
            requireUserId()
            stageRef.update(
                mapOf(
                    "status" to status.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, stageRef.path)
            Result.failure(e)
        }
    }

    suspend fun startStage(stageId: String): Result<Unit> = setStageStatus(stageId, StageStatus.LIVE)

    suspend fun endStage(stageId: String): Result<Unit> = setStageStatus(stageId, StageStatus.ENDED)

    /**
     * Updates the spotlighted performer on stage and ensures their voting counter document exists.
     * Restricted to the stage host (`ownerUid`).
     */
    suspend fun setActivePerformer(
        stageId: String,
        performerId: String,
        performerName: String,
        category: PerformanceCategory = PerformanceCategory.MUSIC,
        performanceId: String? = null,
        performanceTitle: String? = null,
        genre: String? = null
    ): Result<Unit> {
        val sanitizedStageId = sanitizeId(stageId)
        val sanitizedPerformerId = sanitizeId(performerId)
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizedStageId)
        val counterRef = stageRef.collection(SUBCOLLECTION_VOTING_COUNTERS).document(sanitizedPerformerId)

        return try {
            requireUserId()
            val cleanName = performerName.trim().ifBlank { "Stage Performer" }.take(120)
            val cleanPerfId = performanceId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }
            val cleanPerfTitle = performanceTitle?.trim()?.takeIf { it.isNotBlank() }?.take(160)
            val cleanGenre = genre?.trim()?.takeIf { it.isNotBlank() }?.take(80)

            firestore.runTransaction { transaction ->
                val stageSnap = transaction.get(stageRef)
                if (!stageSnap.exists()) {
                    throw IllegalArgumentException("Stage not found: $sanitizedStageId")
                }
                val counterSnap = transaction.get(counterRef)

                val stageUpdates = mapOf<String, Any?>(
                    "activePerformerId" to sanitizedPerformerId,
                    "activePerformerName" to cleanName,
                    "activePerformanceId" to cleanPerfId,
                    "activePerformanceTitle" to cleanPerfTitle,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                transaction.update(stageRef, stageUpdates)

                if (counterSnap.exists()) {
                    val counterUpdates = mapOf<String, Any?>(
                        "performerName" to cleanName,
                        "performanceId" to cleanPerfId,
                        "performanceTitle" to cleanPerfTitle,
                        "genre" to cleanGenre,
                        "isActiveOnStage" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    transaction.update(counterRef, counterUpdates)
                } else {
                    val createCounter = buildInitialCounterPayload(
                        stageId = sanitizedStageId,
                        performerId = sanitizedPerformerId,
                        performerName = cleanName,
                        category = category,
                        performanceId = cleanPerfId,
                        performanceTitle = cleanPerfTitle,
                        genre = cleanGenre ?: category.displayName,
                        avatarInitials = cleanName.take(2).uppercase().ifBlank { "SP" },
                        isActiveOnStage = true
                    )
                    transaction.set(counterRef, createCounter)
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, stageRef.path)
            Result.failure(e)
        }
    }

    /**
     * Atomically increments `spectatorCount` when a spectator joins a live stage.
     */
    suspend fun joinStageAsSpectator(stageId: String): Result<Long> =
        adjustSpectatorPresence(stageId = stageId, delta = 1L)

    /**
     * Atomically decrements `spectatorCount` (bounded at 0) when a spectator leaves a live stage.
     */
    suspend fun leaveStageAsSpectator(stageId: String): Result<Long> =
        adjustSpectatorPresence(stageId = stageId, delta = -1L)

    private suspend fun adjustSpectatorPresence(stageId: String, delta: Long): Result<Long> {
        val sanitizedStageId = sanitizeId(stageId)
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizedStageId)
        return try {
            requireUserId()
            val boundedDelta = delta.coerceIn(-1L, 5L)
            val nextCount = firestore.runTransaction { transaction ->
                val snap = transaction.get(stageRef)
                if (!snap.exists()) {
                    throw IllegalArgumentException("Stage not found: $sanitizedStageId")
                }
                if (snap.getString("status") != StageStatus.LIVE.name) {
                    return@runTransaction (snap.getLong("spectatorCount") ?: 0L).coerceAtLeast(0L)
                }
                val current = (snap.getLong("spectatorCount") ?: 0L).coerceAtLeast(0L)
                val updated = (current + boundedDelta).coerceIn(0L, 1_000_000L)
                transaction.update(
                    stageRef,
                    mapOf(
                        "spectatorCount" to updated,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                updated
            }.await()
            Result.success(nextCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, stageRef.path)
            Result.failure(e)
        }
    }

    suspend fun getStage(stageId: String): Result<PerformanceStage?> {
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizeId(stageId))
        return try {
            val snap = stageRef.get().await()
            Result.success(if (snap.exists()) snap.toPerformanceStageOrNull() else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, stageRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStage(stageId: String): Result<Unit> {
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizeId(stageId))
        return try {
            requireUserId()
            stageRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, stageRef.path)
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of active `LIVE` performance stages from `/stages`.
     * Includes `.whereEqualTo("status", "LIVE")` to satisfy security rule query alignment.
     */
    fun observeLiveStages(category: PerformanceCategory? = null): Flow<List<PerformanceStage>> {
        var query: Query = firestore.collection(COLLECTION_STAGES)
            .whereEqualTo("status", StageStatus.LIVE.name)
        if (category != null) {
            query = query.whereEqualTo("category", category.name)
        }
        return query.snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { it.toPerformanceStageOrNull() }
                    .sortedWith(
                        compareByDescending<PerformanceStage> { it.spectatorCount }
                            .thenByDescending { it.totalStageVotes }
                    )
            }
            .catch { throwable ->
                logFlowError(throwable, OperationType.LIST, COLLECTION_STAGES)
                throw throwable
            }
    }

    fun observeLiveStagesAuthenticated(category: PerformanceCategory? = null): Flow<List<PerformanceStage>> =
        observeAuthUid().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                observeLiveStages(category).catch { emit(emptyList()) }
            }
        }

    fun observeStage(stageId: String): Flow<PerformanceStage?> {
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizeId(stageId))
        return stageRef.snapshots()
            .map { snap -> if (snap.exists()) snap.toPerformanceStageOrNull() else null }
            .catch { throwable ->
                logFlowError(throwable, OperationType.GET, stageRef.path)
                throw throwable
            }
    }

    fun observeStageAuthenticated(stageId: String): Flow<PerformanceStage?> =
        observeAuthUid().flatMapLatest { uid ->
            if (uid == null) flowOf(null) else observeStage(stageId).catch { emit(null) }
        }

    // =========================================================================
    // 2. Spectator Voting Counters & Atomic Ballots (/stages/{stageId}/...)
    // =========================================================================

    /**
     * Initializes or updates a performer's voting counter document in
     * `/stages/{stageId}/voting_counters/{performerId}` (host operation).
     */
    suspend fun initializePerformerCounter(
        stageId: String,
        performerId: String,
        performerName: String,
        category: PerformanceCategory = PerformanceCategory.MUSIC,
        performanceId: String? = null,
        performanceTitle: String? = null,
        genre: String = category.displayName,
        avatarInitials: String? = null,
        avatarColorHex: Long? = null,
        isActiveOnStage: Boolean = true
    ): Result<StageVotingCounter> {
        val sanitizedStageId = sanitizeId(stageId)
        val sanitizedPerformerId = sanitizeId(performerId)
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizedStageId)
        val counterRef = stageRef.collection(SUBCOLLECTION_VOTING_COUNTERS).document(sanitizedPerformerId)

        return try {
            requireUserId()
            val cleanName = performerName.trim().ifBlank { "Stage Performer" }.take(120)
            val cleanInitials = (avatarInitials?.takeIf { it.isNotBlank() } ?: cleanName.take(2).uppercase()).take(8)
            val resolvedColor = avatarColorHex
                ?: if (category == PerformanceCategory.MUSIC) 0xFF9D4EDD else 0xFF00B4D8
            val cleanPerfId = performanceId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }
            val cleanPerfTitle = performanceTitle?.trim()?.takeIf { it.isNotBlank() }?.take(160)
            val cleanGenre = genre.trim().ifBlank { category.displayName }.take(80)

            firestore.runTransaction { transaction ->
                val existing = transaction.get(counterRef)
                if (existing.exists()) {
                    val updates = mapOf<String, Any?>(
                        "performerName" to cleanName,
                        "category" to category.name,
                        "performanceId" to cleanPerfId,
                        "performanceTitle" to cleanPerfTitle,
                        "genre" to cleanGenre,
                        "avatarInitials" to cleanInitials,
                        "avatarColorHex" to resolvedColor,
                        "isActiveOnStage" to isActiveOnStage,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    transaction.update(counterRef, updates)
                } else {
                    val createPayload = buildInitialCounterPayload(
                        stageId = sanitizedStageId,
                        performerId = sanitizedPerformerId,
                        performerName = cleanName,
                        category = category,
                        performanceId = cleanPerfId,
                        performanceTitle = cleanPerfTitle,
                        genre = cleanGenre,
                        avatarInitials = cleanInitials,
                        avatarColorHex = resolvedColor,
                        isActiveOnStage = isActiveOnStage
                    )
                    transaction.set(counterRef, createPayload)
                }
            }.await()

            val snap = counterRef.get().await()
            val counter = snap.toStageVotingCounterOrNull() ?: StageVotingCounter(
                id = sanitizedPerformerId,
                stageId = sanitizedStageId,
                performerId = sanitizedPerformerId,
                performerName = cleanName,
                category = category.name,
                performanceId = cleanPerfId,
                performanceTitle = cleanPerfTitle,
                genre = cleanGenre,
                avatarInitials = cleanInitials,
                avatarColorHex = resolvedColor,
                isActiveOnStage = isActiveOnStage
            )
            Result.success(counter)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, counterRef.path)
            Result.failure(e)
        }
    }

    /**
     * Atomically casts an authenticated spectator vote on a `LIVE` performance stage.
     *
     * Executes a single Firestore transaction that:
     * 1. Verifies `/stages/{stageId}` exists and `status == 'LIVE'`.
     * 2. Writes an authenticated ballot receipt in `/stages/{stageId}/spectator_votes/{voteId}`.
     * 3. Atomically increments `/stages/{stageId}/voting_counters/{performerId}` (`totalVotes`,
     *    tier counter, `ballotsCast`, and `uniqueVoterCount`).
     * 4. Atomically increments `/stages/{stageId}` (`totalStageVotes` and `totalBallotsCast`).
     */
    suspend fun castSpectatorVote(
        stageId: String,
        performerId: String,
        performerName: String,
        category: PerformanceCategory = PerformanceCategory.MUSIC,
        tier: VoteTier = VoteTier.STANDARD,
        performanceId: String? = null,
        performanceTitle: String? = null,
        voterName: String? = null,
        voteId: String? = null
    ): Result<StageSpectatorVote> {
        val sanitizedStageId = sanitizeId(stageId)
        val sanitizedPerformerId = sanitizeId(performerId)
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizedStageId)
        val counterRef = stageRef.collection(SUBCOLLECTION_VOTING_COUNTERS).document(sanitizedPerformerId)

        return try {
            val uid = requireUserId()
            val normalizedTier = when (tier) {
                VoteTier.STANDING_OVATION -> VoteTier.STANDING_OVATION
                VoteTier.SUPER_VOTE -> VoteTier.SUPER_VOTE
                VoteTier.STANDARD, VoteTier.POLL_VOTE -> VoteTier.STANDARD
            }
            val weight = normalizedTier.weight.coerceIn(1, 100).toLong()
            val stdDelta = if (normalizedTier == VoteTier.STANDARD) 1L else 0L
            val superDelta = if (normalizedTier == VoteTier.SUPER_VOTE) 1L else 0L
            val ovationDelta = if (normalizedTier == VoteTier.STANDING_OVATION) 1L else 0L

            val cleanPerformerName = performerName.trim().ifBlank { "Stage Performer" }.take(120)
            val cleanVoterName = (voterName?.trim()?.takeIf { it.isNotBlank() } ?: currentUserDisplayName()).take(120)
            val cleanPerfId = performanceId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }
            val cleanPerfTitle = performanceTitle?.trim()?.takeIf { it.isNotBlank() }?.take(160)

            val firstBallotDocId = buildFirstBallotId(sanitizedStageId, sanitizedPerformerId, uid)
            val firstBallotRef = stageRef.collection(SUBCOLLECTION_SPECTATOR_VOTES).document(firstBallotDocId)
            val explicitVoteId = voteId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }

            val committedVoteId = firestore.runTransaction { transaction ->
                // All reads MUST execute before any writes in Firestore transactions
                val stageSnap = transaction.get(stageRef)
                if (!stageSnap.exists()) {
                    throw IllegalStateException("Stage $sanitizedStageId does not exist")
                }
                val stageStatus = stageSnap.getString("status") ?: StageStatus.ENDED.name
                if (stageStatus != StageStatus.LIVE.name) {
                    throw IllegalStateException("Stage $sanitizedStageId is not LIVE (current: $stageStatus)")
                }

                val counterSnap = transaction.get(counterRef)
                val firstBallotSnap = transaction.get(firstBallotRef)

                val hasCountedFirstBallot = firstBallotSnap.exists() &&
                    firstBallotSnap.getString("status") == StageSpectatorVote.STATUS_COUNTED

                val targetVoteId = when {
                    explicitVoteId != null -> explicitVoteId
                    !firstBallotSnap.exists() -> firstBallotDocId
                    else -> "sv_${UUID.randomUUID().toString().replace("-", "").take(20)}"
                }
                val targetVoteRef = stageRef.collection(SUBCOLLECTION_SPECTATOR_VOTES).document(targetVoteId)
                val uniqueDelta = if (!hasCountedFirstBallot) 1L else 0L

                val votePayload = mapOf<String, Any?>(
                    "stageId" to sanitizedStageId,
                    "performerId" to sanitizedPerformerId,
                    "performerName" to cleanPerformerName,
                    "category" to category.name,
                    "performanceId" to cleanPerfId,
                    "performanceTitle" to cleanPerfTitle,
                    "voterUid" to uid,
                    "voterName" to cleanVoterName,
                    "voteWeight" to weight,
                    "voteTier" to normalizedTier.firestoreKey,
                    "status" to StageSpectatorVote.STATUS_COUNTED,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }
                transaction.set(targetVoteRef, votePayload)

                if (counterSnap.exists()) {
                    val curTotal = (counterSnap.getLong("totalVotes") ?: 0L).coerceAtLeast(0L)
                    val curStd = (counterSnap.getLong("standardVotesCount") ?: 0L).coerceAtLeast(0L)
                    val curSuper = (counterSnap.getLong("superVotesCount") ?: 0L).coerceAtLeast(0L)
                    val curOvation = (counterSnap.getLong("ovationVotesCount") ?: 0L).coerceAtLeast(0L)
                    val curBallots = (counterSnap.getLong("ballotsCast") ?: 0L).coerceAtLeast(0L)
                    val curUnique = (counterSnap.getLong("uniqueVoterCount") ?: 0L).coerceAtLeast(0L)

                    transaction.update(
                        counterRef,
                        mapOf(
                            "totalVotes" to (curTotal + weight),
                            "standardVotesCount" to (curStd + stdDelta),
                            "superVotesCount" to (curSuper + superDelta),
                            "ovationVotesCount" to (curOvation + ovationDelta),
                            "ballotsCast" to (curBallots + 1L),
                            "uniqueVoterCount" to (curUnique + uniqueDelta),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    val counterCreatePayload = mapOf<String, Any?>(
                        "stageId" to sanitizedStageId,
                        "performerId" to sanitizedPerformerId,
                        "performerName" to cleanPerformerName,
                        "category" to category.name,
                        "performanceId" to cleanPerfId,
                        "performanceTitle" to cleanPerfTitle,
                        "genre" to category.displayName,
                        "avatarInitials" to cleanPerformerName.take(2).uppercase().ifBlank { "SP" },
                        "avatarColorHex" to if (category == PerformanceCategory.MUSIC) 0xFF9D4EDD else 0xFF00B4D8,
                        "isActiveOnStage" to true,
                        "totalVotes" to weight,
                        "standardVotesCount" to stdDelta,
                        "superVotesCount" to superDelta,
                        "ovationVotesCount" to ovationDelta,
                        "ballotsCast" to 1L,
                        "uniqueVoterCount" to 1L,
                        "cheersCount" to 0L,
                        "visibility" to VISIBILITY_PUBLIC,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    transaction.set(counterRef, counterCreatePayload)
                }

                val curStageVotes = (stageSnap.getLong("totalStageVotes") ?: 0L).coerceAtLeast(0L)
                val curStageBallots = (stageSnap.getLong("totalBallotsCast") ?: 0L).coerceAtLeast(0L)
                transaction.update(
                    stageRef,
                    mapOf(
                        "totalStageVotes" to (curStageVotes + weight),
                        "totalBallotsCast" to (curStageBallots + 1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                targetVoteId
            }.await()

            Result.success(
                StageSpectatorVote(
                    id = committedVoteId,
                    stageId = sanitizedStageId,
                    performerId = sanitizedPerformerId,
                    performerName = cleanPerformerName,
                    category = category.name,
                    performanceId = cleanPerfId,
                    performanceTitle = cleanPerfTitle,
                    voterUid = uid,
                    voterName = cleanVoterName,
                    voteWeight = weight,
                    voteTier = normalizedTier.firestoreKey,
                    status = StageSpectatorVote.STATUS_COUNTED
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "${stageRef.path}/$SUBCOLLECTION_SPECTATOR_VOTES")
            Result.failure(e)
        }
    }

    /**
     * Atomically revokes an authenticated spectator's vote (`COUNTED -> REVOKED`) on a `LIVE` stage
     * and decrements both the performer's voting counter and the parent stage totals.
     */
    suspend fun revokeSpectatorVote(stageId: String, voteId: String): Result<Unit> {
        val sanitizedStageId = sanitizeId(stageId)
        val sanitizedVoteId = sanitizeId(voteId)
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizedStageId)
        val voteRef = stageRef.collection(SUBCOLLECTION_SPECTATOR_VOTES).document(sanitizedVoteId)

        return try {
            val uid = requireUserId()
            firestore.runTransaction { transaction ->
                val stageSnap = transaction.get(stageRef)
                val voteSnap = transaction.get(voteRef)
                if (!voteSnap.exists()) {
                    throw IllegalArgumentException("Spectator vote not found: $sanitizedVoteId")
                }
                if (voteSnap.getString("status") != StageSpectatorVote.STATUS_COUNTED) {
                    throw IllegalStateException("Spectator vote $sanitizedVoteId is already revoked")
                }

                val performerId = sanitizeId(voteSnap.getString("performerId") ?: "")
                val weight = (voteSnap.getLong("voteWeight") ?: 1L).coerceIn(1L, 100L)
                val tier = VoteTier.fromKeyOrDelta(voteSnap.getString("voteTier"), weight.toInt())
                val counterRef = stageRef.collection(SUBCOLLECTION_VOTING_COUNTERS).document(performerId)
                val counterSnap = transaction.get(counterRef)

                transaction.update(
                    voteRef,
                    mapOf(
                        "status" to StageSpectatorVote.STATUS_REVOKED,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )

                if (counterSnap.exists()) {
                    val stdDec = if (tier == VoteTier.STANDARD) 1L else 0L
                    val superDec = if (tier == VoteTier.SUPER_VOTE) 1L else 0L
                    val ovationDec = if (tier == VoteTier.STANDING_OVATION) 1L else 0L
                    val firstBallotId = buildFirstBallotId(sanitizedStageId, performerId, uid)
                    val uniqueDec = if (sanitizedVoteId == firstBallotId) 1L else 0L

                    transaction.update(
                        counterRef,
                        mapOf(
                            "totalVotes" to ((counterSnap.getLong("totalVotes") ?: 0L) - weight).coerceAtLeast(0L),
                            "standardVotesCount" to ((counterSnap.getLong("standardVotesCount") ?: 0L) - stdDec).coerceAtLeast(0L),
                            "superVotesCount" to ((counterSnap.getLong("superVotesCount") ?: 0L) - superDec).coerceAtLeast(0L),
                            "ovationVotesCount" to ((counterSnap.getLong("ovationVotesCount") ?: 0L) - ovationDec).coerceAtLeast(0L),
                            "ballotsCast" to ((counterSnap.getLong("ballotsCast") ?: 0L) - 1L).coerceAtLeast(0L),
                            "uniqueVoterCount" to ((counterSnap.getLong("uniqueVoterCount") ?: 0L) - uniqueDec).coerceAtLeast(0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                if (stageSnap.exists()) {
                    transaction.update(
                        stageRef,
                        mapOf(
                            "totalStageVotes" to ((stageSnap.getLong("totalStageVotes") ?: 0L) - weight).coerceAtLeast(0L),
                            "totalBallotsCast" to ((stageSnap.getLong("totalBallotsCast") ?: 0L) - 1L).coerceAtLeast(0L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, voteRef.path)
            Result.failure(e)
        }
    }

    /**
     * Atomically increments spectator cheer counters for both the target performer
     * (`/stages/{stageId}/voting_counters/{performerId}`) and the stage (`/stages/{stageId}`).
     */
    suspend fun recordStageCheer(
        stageId: String,
        performerId: String,
        performerName: String,
        category: PerformanceCategory = PerformanceCategory.MUSIC,
        cheerDelta: Int = 1
    ): Result<Long> {
        val sanitizedStageId = sanitizeId(stageId)
        val sanitizedPerformerId = sanitizeId(performerId)
        val stageRef = firestore.collection(COLLECTION_STAGES).document(sanitizedStageId)
        val counterRef = stageRef.collection(SUBCOLLECTION_VOTING_COUNTERS).document(sanitizedPerformerId)

        return try {
            requireUserId()
            val delta = cheerDelta.coerceIn(1, 50).toLong()
            val cleanName = performerName.trim().ifBlank { "Stage Performer" }.take(120)

            val updatedCheers = firestore.runTransaction { transaction ->
                val stageSnap = transaction.get(stageRef)
                if (!stageSnap.exists() || stageSnap.getString("status") != StageStatus.LIVE.name) {
                    throw IllegalStateException("Stage $sanitizedStageId is not LIVE")
                }
                val counterSnap = transaction.get(counterRef)

                val nextPerformerCheers = if (counterSnap.exists()) {
                    val next = ((counterSnap.getLong("cheersCount") ?: 0L) + delta).coerceAtLeast(0L)
                    transaction.update(
                        counterRef,
                        mapOf(
                            "cheersCount" to next,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    next
                } else {
                    val createCounter = buildInitialCounterPayload(
                        stageId = sanitizedStageId,
                        performerId = sanitizedPerformerId,
                        performerName = cleanName,
                        category = category,
                        cheersCount = delta
                    )
                    transaction.set(counterRef, createCounter)
                    delta
                }

                val nextStageCheers = ((stageSnap.getLong("totalCheersCount") ?: 0L) + delta).coerceAtLeast(0L)
                transaction.update(
                    stageRef,
                    mapOf(
                        "totalCheersCount" to nextStageCheers,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                nextPerformerCheers
            }.await()
            Result.success(updatedCheers)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, counterRef.path)
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of per-performer spectator voting counters for [stageId].
     * Filtered by `visibility == 'PUBLIC'` to satisfy flat security rule list evaluation.
     */
    fun observeStageVotingCounters(stageId: String): Flow<List<StageVotingCounter>> {
        val sanitizedStageId = sanitizeId(stageId)
        val countersPath = "$COLLECTION_STAGES/$sanitizedStageId/$SUBCOLLECTION_VOTING_COUNTERS"
        return firestore.collection(COLLECTION_STAGES)
            .document(sanitizedStageId)
            .collection(SUBCOLLECTION_VOTING_COUNTERS)
            .whereEqualTo("visibility", VISIBILITY_PUBLIC)
            .snapshots()
            .map { snapshot ->
                snapshot.documents
                    .mapNotNull { it.toStageVotingCounterOrNull() }
                    .sortedWith(
                        compareByDescending<StageVotingCounter> { it.totalVotes }
                            .thenByDescending { it.cheersCount }
                            .thenBy { it.performerName }
                    )
            }
            .catch { throwable ->
                logFlowError(throwable, OperationType.LIST, countersPath)
                throw throwable
            }
    }

    fun observeStageVotingCountersAuthenticated(stageId: String): Flow<List<StageVotingCounter>> =
        observeAuthUid().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                observeStageVotingCounters(stageId).catch { emit(emptyList()) }
            }
        }

    /**
     * Convenience map stream (`performerId / performanceId -> totalVotes`) for real-time stage UI bars.
     */
    fun observeStageVotingMapAuthenticated(stageId: String): Flow<Map<String, Int>> =
        observeStageVotingCountersAuthenticated(stageId).map { counters ->
            buildMap {
                counters.forEach { counter ->
                    val votesInt = counter.totalVotes.coerceAtLeast(0L).toInt()
                    put(counter.performerId, votesInt)
                    counter.performanceId?.takeIf { it.isNotBlank() }?.let { perfId ->
                        put(perfId, votesInt)
                    }
                }
            }
        }

    /**
     * Real-time stream of all `COUNTED` spectator vote receipts on [stageId].
     */
    fun observeStageSpectatorVotes(stageId: String): Flow<List<StageSpectatorVote>> {
        val sanitizedStageId = sanitizeId(stageId)
        val votesPath = "$COLLECTION_STAGES/$sanitizedStageId/$SUBCOLLECTION_SPECTATOR_VOTES"
        return firestore.collection(COLLECTION_STAGES)
            .document(sanitizedStageId)
            .collection(SUBCOLLECTION_SPECTATOR_VOTES)
            .whereEqualTo("status", StageSpectatorVote.STATUS_COUNTED)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toStageSpectatorVoteOrNull() }
            }
            .catch { throwable ->
                logFlowError(throwable, OperationType.LIST, votesPath)
                throw throwable
            }
    }

    /**
     * Real-time stream of the current authenticated spectator's own vote receipts on [stageId].
     */
    fun observeMyStageVotes(stageId: String): Flow<List<StageSpectatorVote>> {
        val uid = requireUserId()
        val sanitizedStageId = sanitizeId(stageId)
        val votesPath = "$COLLECTION_STAGES/$sanitizedStageId/$SUBCOLLECTION_SPECTATOR_VOTES"
        return firestore.collection(COLLECTION_STAGES)
            .document(sanitizedStageId)
            .collection(SUBCOLLECTION_SPECTATOR_VOTES)
            .whereEqualTo("voterUid", uid)
            .snapshots()
            .map { snapshot ->
                snapshot.documents.mapNotNull { it.toStageSpectatorVoteOrNull() }
            }
            .catch { throwable ->
                logFlowError(throwable, OperationType.LIST, votesPath)
                throw throwable
            }
    }

    /**
     * Combined real-time [StageVotingSnapshot] uniting stage metadata and ranked performer voting counters.
     */
    fun observeStageSnapshot(stageId: String): Flow<StageVotingSnapshot> {
        val sanitizedStageId = sanitizeId(stageId)
        return combine(
            observeStage(sanitizedStageId),
            observeStageVotingCounters(sanitizedStageId)
        ) { stage, counters ->
            buildStageSnapshot(sanitizedStageId, stage, counters, isCloudSynced = stage != null)
        }
    }

    fun observeStageSnapshotAuthenticated(stageId: String): Flow<StageVotingSnapshot> =
        observeAuthUid().flatMapLatest { uid ->
            val sanitizedStageId = sanitizeId(stageId)
            if (uid == null) {
                flowOf(StageVotingSnapshot(stageId = sanitizedStageId, isCloudSynced = false))
            } else {
                observeStageSnapshot(sanitizedStageId).catch {
                    emit(StageVotingSnapshot(stageId = sanitizedStageId, isCloudSynced = false))
                }
            }
        }

    private fun buildInitialCounterPayload(
        stageId: String,
        performerId: String,
        performerName: String,
        category: PerformanceCategory,
        performanceId: String? = null,
        performanceTitle: String? = null,
        genre: String = category.displayName,
        avatarInitials: String = "SP",
        avatarColorHex: Long = if (category == PerformanceCategory.MUSIC) 0xFF9D4EDD else 0xFF00B4D8,
        isActiveOnStage: Boolean = true,
        cheersCount: Long = 0L
    ): Map<String, Any> = mapOf<String, Any?>(
        "stageId" to stageId,
        "performerId" to performerId,
        "performerName" to performerName.trim().ifBlank { "Stage Performer" }.take(120),
        "category" to category.name,
        "performanceId" to performanceId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) },
        "performanceTitle" to performanceTitle?.trim()?.takeIf { it.isNotBlank() }?.take(160),
        "genre" to genre.trim().ifBlank { category.displayName }.take(80),
        "avatarInitials" to avatarInitials.trim().ifBlank { "SP" }.take(8),
        "avatarColorHex" to avatarColorHex,
        "isActiveOnStage" to isActiveOnStage,
        "totalVotes" to 0L,
        "standardVotesCount" to 0L,
        "superVotesCount" to 0L,
        "ovationVotesCount" to 0L,
        "ballotsCast" to 0L,
        "uniqueVoterCount" to 0L,
        "cheersCount" to cheersCount.coerceAtLeast(0L),
        "visibility" to VISIBILITY_PUBLIC,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null }.mapValues { it.value!! }

    private fun logFlowError(throwable: Throwable, op: OperationType, path: String) {
        val firestoreError = generateSequence(throwable) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && throwable is CancellationException) return
        val err = firestoreError ?: (throwable as? Exception)
        if (err != null) {
            handleFirestoreError(err, op, path)
        }
    }

    companion object {
        const val COLLECTION_STAGES = "stages"
        const val SUBCOLLECTION_VOTING_COUNTERS = "voting_counters"
        const val SUBCOLLECTION_SPECTATOR_VOTES = "spectator_votes"
        const val VISIBILITY_PUBLIC = "PUBLIC"

        fun sanitizeId(raw: String): String = VotesLeaderboardRepository.sanitizeId(raw)

        internal fun buildFirstBallotId(stageId: String, performerId: String, voterUid: String): String {
            val raw = "sv1_${stageId.take(32)}_${performerId.take(32)}_${voterUid.take(40)}"
            return sanitizeId(raw)
        }

        internal fun buildStageSnapshot(
            stageId: String,
            stage: PerformanceStage?,
            counters: List<StageVotingCounter>,
            isCloudSynced: Boolean
        ): StageVotingSnapshot {
            val countersTotalVotes = counters.sumOf { it.totalVotes }.coerceAtLeast(0L)
            val countersTotalBallots = counters.sumOf { it.ballotsCast }.coerceAtLeast(0L)
            val countersTotalCheers = counters.sumOf { it.cheersCount }.coerceAtLeast(0L)

            val resolvedTotalVotes = maxOf(stage?.totalStageVotes ?: 0L, countersTotalVotes).toInt()
            val resolvedBallots = maxOf(stage?.totalBallotsCast ?: 0L, countersTotalBallots).toInt()
            val resolvedCheers = maxOf(stage?.totalCheersCount ?: 0L, countersTotalCheers).toInt()
            val leader = counters.filter { it.totalVotes > 0L }.maxByOrNull { it.totalVotes }?.performerId

            return StageVotingSnapshot(
                stageId = stageId,
                stage = stage,
                counters = counters,
                leadingPerformerId = leader,
                totalStageVotes = resolvedTotalVotes,
                totalBallotsCast = resolvedBallots,
                totalCheersCount = resolvedCheers,
                spectatorCount = (stage?.spectatorCount ?: 0L).coerceAtLeast(0L).toInt(),
                isCloudSynced = isCloudSynced
            )
        }

        internal fun DocumentSnapshot.toPerformanceStageOrNull(): PerformanceStage? {
            return try {
                toObject(PerformanceStage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.takeIf { it.title.isNotBlank() || it.ownerUid.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }

        internal fun DocumentSnapshot.toStageVotingCounterOrNull(): StageVotingCounter? {
            return try {
                val parsed = toObject(StageVotingCounter::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: return null
                val resolvedPerformerId = parsed.performerId.ifBlank { id }
                parsed.copy(id = id, performerId = resolvedPerformerId)
            } catch (_: Exception) {
                null
            }
        }

        internal fun DocumentSnapshot.toStageSpectatorVoteOrNull(): StageSpectatorVote? {
            return try {
                toObject(StageSpectatorVote::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?.takeIf { it.voterUid.isNotBlank() && it.performerId.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }
}
