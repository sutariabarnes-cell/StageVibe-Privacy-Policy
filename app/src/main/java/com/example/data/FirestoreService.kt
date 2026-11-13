package com.example.data

import android.content.Context
import com.example.R
import com.example.model.LeaderboardTimePeriod
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.Performer
import com.example.model.SynthAudioType
import com.example.model.UserAccount
import com.example.model.UserRole
import com.example.model.VisualizerTheme
import com.example.model.VoteRecord
import com.example.model.VotesAggregationSnapshot
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

/**
 * Unified Cloud Firestore service managing the `'performers'`, `'performances'`, and `'votes'`
 * collections using the named Firestore database configured in `R.string.firestore_database_id`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreService(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    constructor(context: Context) : this(
        firestore = createFirestore(context),
        auth = Firebase.auth
    )

    private fun requireUserId(): String =
        checkNotNull(auth.currentUser?.uid) { "User must be signed in before executing Firestore writes" }

    private fun currentUserDisplayName(): String =
        auth.currentUser?.displayName?.takeIf { it.isNotBlank() }?.take(120) ?: "Stage Voter"

    private fun observeAuthUid(): Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    // =========================================================================
    // 1. 'performers' Collection Operations
    // =========================================================================

    /**
     * Creates or updates a performer document in `/performers/{performerId}`.
     * Preserves immutable `createdAt` and `ownerUid` on updates to satisfy hardened rules.
     */
    suspend fun savePerformer(performer: Performer): Result<Performer> {
        val sanitizedId = sanitizeId(performer.creatorId.ifBlank { performer.id })
        val docRef = firestore.collection(COLLECTION_PERFORMERS).document(sanitizedId)
        return try {
            val uid = requireUserId()
            firestore.runTransaction { transaction ->
                val existingSnap = transaction.get(docRef)
                val baseFields = mapOf<String, Any?>(
                    "name" to performer.name.ifBlank { "Stage Performer" }.take(120),
                    "stageTitle" to performer.stageTitle.takeIf { it.isNotBlank() }?.take(120),
                    "bio" to performer.bio.take(1000),
                    "category" to performer.category.name,
                    "avatarUrl" to performer.avatarUrl?.takeIf { it.isNotBlank() }?.take(500),
                    "avatarColorHex" to performer.avatarColorHex,
                    "rating" to performer.rating.toDouble().coerceIn(0.0, 5.0),
                    "ratingCount" to performer.ratingCount.toLong().coerceAtLeast(0L),
                    "totalVotes" to performer.totalVotes.toLong().coerceAtLeast(0L),
                    "totalPerformances" to performer.totalPerformances.toLong().coerceAtLeast(0L),
                    "totalSessions" to performer.totalSessions.toLong().coerceAtLeast(0L),
                    "socialSpotify" to performer.socialSpotify.takeIf { it.isNotBlank() }?.take(200),
                    "socialInstagram" to performer.socialInstagram.takeIf { it.isNotBlank() }?.take(200),
                    "socialYoutube" to performer.socialYoutube.takeIf { it.isNotBlank() }?.take(200),
                    "socialWebsite" to performer.socialWebsite.takeIf { it.isNotBlank() }?.take(200),
                    "visibility" to if (performer.visibility in ALLOWED_VISIBILITY) performer.visibility else VISIBILITY_PUBLIC,
                    "updatedAt" to FieldValue.serverTimestamp()
                ).filterValues { it != null }

                if (existingSnap.exists()) {
                    transaction.update(docRef, baseFields)
                } else {
                    val createPayload = baseFields + mapOf(
                        "creatorId" to sanitizedId,
                        "ownerUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    transaction.set(docRef, createPayload)
                }
            }.await()

            Result.success(performer.copy(id = sanitizedId, creatorId = sanitizedId, ownerUid = uid))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getPerformer(performerId: String): Result<Performer?> {
        val docRef = firestore.collection(COLLECTION_PERFORMERS).document(sanitizeId(performerId))
        return try {
            val snap = docRef.get().await()
            Result.success(if (snap.exists()) snap.toPerformerOrNull() else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observePerformers(category: PerformanceCategory? = null): Flow<List<Performer>> {
        var query: Query = firestore.collection(COLLECTION_PERFORMERS)
            .whereEqualTo("visibility", VISIBILITY_PUBLIC)
        if (category != null) {
            query = query.whereEqualTo("category", category.name)
        }
        return query.snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toPerformerOrNull() } }
            .catch { throwable ->
                if (throwable is Exception && throwable !is CancellationException) {
                    handleFirestoreError(throwable, OperationType.LIST, COLLECTION_PERFORMERS)
                }
                throw throwable
            }
    }

    fun observePerformersAuthenticated(category: PerformanceCategory? = null): Flow<List<Performer>> =
        observeAuthUid().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else observePerformers(category).catch { emit(emptyList()) }
        }

    fun observePerformer(performerId: String): Flow<Performer?> {
        val docRef = firestore.collection(COLLECTION_PERFORMERS).document(sanitizeId(performerId))
        return docRef.snapshots()
            .map { snap -> if (snap.exists()) snap.toPerformerOrNull() else null }
            .catch { throwable ->
                if (throwable is Exception && throwable !is CancellationException) {
                    handleFirestoreError(throwable, OperationType.GET, docRef.path)
                }
                throw throwable
            }
    }

    fun observePerformerAuthenticated(performerId: String): Flow<Performer?> =
        observeAuthUid().flatMapLatest { uid ->
            if (uid == null) flowOf(null) else observePerformer(performerId).catch { emit(null) }
        }

    suspend fun ratePerformer(performerId: String, ratingValue: Float): Result<Unit> {
        val docRef = firestore.collection(COLLECTION_PERFORMERS).document(sanitizeId(performerId))
        return try {
            requireUserId()
            val clampedRating = ratingValue.coerceIn(0f, 5f)
            firestore.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                if (snap.exists()) {
                    val currentRating = (snap.getDouble("rating") ?: 5.0).toFloat()
                    val currentCount = (snap.getLong("ratingCount") ?: 0L).toInt().coerceAtLeast(0)
                    val nextCount = currentCount + 1
                    val nextRating = ((currentRating * currentCount) + clampedRating) / nextCount
                    val roundedRating = String.format(Locale.US, "%.2f", nextRating).toDouble().coerceIn(0.0, 5.0)
                    transaction.update(
                        docRef,
                        mapOf(
                            "rating" to roundedRating,
                            "ratingCount" to nextCount.toLong(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun incrementPerformerVotes(performerId: String, delta: Long = 1L): Result<Unit> {
        val docRef = firestore.collection(COLLECTION_PERFORMERS).document(sanitizeId(performerId))
        return try {
            requireUserId()
            firestore.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                if (snap.exists()) {
                    val currentVotes = snap.getLong("totalVotes") ?: 0L
                    val nextVotes = (currentVotes + delta).coerceAtLeast(0L)
                    transaction.update(
                        docRef,
                        mapOf(
                            "totalVotes" to nextVotes,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePerformer(performerId: String): Result<Unit> {
        val docRef = firestore.collection(COLLECTION_PERFORMERS).document(sanitizeId(performerId))
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 2. 'performances' Collection Operations
    // =========================================================================

    suspend fun savePerformance(performance: Performance): Result<Performance> {
        val sanitizedPerfId = sanitizeId(performance.id)
        val sanitizedArtistId = sanitizeId(performance.artistId)
        val docRef = firestore.collection(COLLECTION_PERFORMANCES).document(sanitizedPerfId)
        return try {
            val uid = requireUserId()
            firestore.runTransaction { transaction ->
                val existingSnap = transaction.get(docRef)
                if (existingSnap.exists()) {
                    val updatePayload = mapOf<String, Any?>(
                        "title" to performance.title.ifBlank { "Untitled Track" }.take(160),
                        "genre" to performance.genre.take(80),
                        "lyricsOrScript" to performance.lyricsOrScript.take(10000),
                        "visualizerTheme" to performance.visualizerTheme.name,
                        "customAudioPath" to com.example.security.UploadEncryptionManager.encryptField(
                            null,
                            performance.customAudioPath?.takeIf { it.isNotBlank() }
                        )?.takeIf { it.length <= 500 },
                        "customVideoPath" to com.example.security.UploadEncryptionManager.encryptField(
                            null,
                            performance.customVideoPath?.takeIf { it.isNotBlank() }
                        )?.takeIf { it.length <= 500 },
                        "hasVideo" to performance.hasVideo,
                        "viewsCount" to performance.viewsCount.toLong().coerceAtLeast(0L),
                        "likesCount" to performance.likesCount.toLong().coerceAtLeast(0L),
                        "votesCount" to performance.votesCount.toLong().coerceAtLeast(0L),
                        "cheersCount" to performance.cheersCount.toLong().coerceAtLeast(0L),
                        "visibility" to VISIBILITY_PUBLIC,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    transaction.update(docRef, updatePayload)
                } else {
                    val createPayload = mapOf<String, Any?>(
                        "id" to sanitizedPerfId,
                        "ownerUid" to uid,
                        "title" to performance.title.ifBlank { "Untitled Track" }.take(160),
                        "artistId" to sanitizedArtistId,
                        "artistName" to performance.artistName.take(120),
                        "avatarInitials" to performance.avatarInitials.take(8),
                        "category" to performance.category.name,
                        "genre" to performance.genre.take(80),
                        "durationSec" to performance.durationSec.coerceIn(1, 7200),
                        "lyricsOrScript" to performance.lyricsOrScript.take(10000),
                        "visualizerTheme" to performance.visualizerTheme.name,
                        "audioType" to performance.audioType.name,
                        "customAudioPath" to com.example.security.UploadEncryptionManager.encryptField(
                            null,
                            performance.customAudioPath?.takeIf { it.isNotBlank() }
                        )?.takeIf { it.length <= 500 },
                        "customVideoPath" to com.example.security.UploadEncryptionManager.encryptField(
                            null,
                            performance.customVideoPath?.takeIf { it.isNotBlank() }
                        )?.takeIf { it.length <= 500 },
                        "hasVideo" to performance.hasVideo,
                        "viewsCount" to performance.viewsCount.toLong().coerceAtLeast(0L),
                        "likesCount" to performance.likesCount.toLong().coerceAtLeast(0L),
                        "votesCount" to performance.votesCount.toLong().coerceAtLeast(0L),
                        "cheersCount" to performance.cheersCount.toLong().coerceAtLeast(0L),
                        "pollId" to performance.pollId?.takeIf { it.isNotBlank() }?.take(128),
                        "createdAtTimestamp" to performance.createdAtTimestamp,
                        "visibility" to VISIBILITY_PUBLIC,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }
                    transaction.set(docRef, createPayload)
                }
            }.await()
            Result.success(performance.copy(id = sanitizedPerfId, artistId = sanitizedArtistId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getPerformance(performanceId: String): Result<Performance?> {
        val docRef = firestore.collection(COLLECTION_PERFORMANCES).document(sanitizeId(performanceId))
        return try {
            val snap = docRef.get().await()
            Result.success(if (snap.exists()) snap.toPerformanceOrNull() else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observePerformances(category: PerformanceCategory? = null): Flow<List<Performance>> {
        var query: Query = firestore.collection(COLLECTION_PERFORMANCES)
            .whereEqualTo("visibility", VISIBILITY_PUBLIC)
        if (category != null) {
            query = query.whereEqualTo("category", category.name)
        }
        return query.snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toPerformanceOrNull() } }
            .catch { throwable ->
                if (throwable is Exception && throwable !is CancellationException) {
                    handleFirestoreError(throwable, OperationType.LIST, COLLECTION_PERFORMANCES)
                }
                throw throwable
            }
    }

    fun observePerformancesAuthenticated(category: PerformanceCategory? = null): Flow<List<Performance>> =
        observeAuthUid().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else observePerformances(category).catch { emit(emptyList()) }
        }

    fun observePerformancesByPerformer(performerId: String): Flow<List<Performance>> {
        val sanitizedPerformerId = sanitizeId(performerId)
        return firestore.collection(COLLECTION_PERFORMANCES)
            .whereEqualTo("artistId", sanitizedPerformerId)
            .whereEqualTo("visibility", VISIBILITY_PUBLIC)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toPerformanceOrNull() } }
            .catch { throwable ->
                if (throwable is Exception && throwable !is CancellationException) {
                    handleFirestoreError(throwable, OperationType.LIST, COLLECTION_PERFORMANCES)
                }
                throw throwable
            }
    }

    fun observePerformancesByPerformerAuthenticated(performerId: String): Flow<List<Performance>> =
        observeAuthUid().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else observePerformancesByPerformer(performerId).catch { emit(emptyList()) }
        }

    suspend fun updatePerformanceEngagement(
        performanceId: String,
        viewsDelta: Long = 0L,
        likesDelta: Long = 0L,
        votesDelta: Long = 0L,
        cheersDelta: Long = 0L
    ): Result<Unit> {
        val docRef = firestore.collection(COLLECTION_PERFORMANCES).document(sanitizeId(performanceId))
        return try {
            requireUserId()
            firestore.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                if (snap.exists()) {
                    val updates = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
                    if (viewsDelta != 0L) {
                        updates["viewsCount"] = ((snap.getLong("viewsCount") ?: 0L) + viewsDelta).coerceAtLeast(0L)
                    }
                    if (likesDelta != 0L) {
                        updates["likesCount"] = ((snap.getLong("likesCount") ?: 0L) + likesDelta).coerceAtLeast(0L)
                    }
                    if (votesDelta != 0L) {
                        updates["votesCount"] = ((snap.getLong("votesCount") ?: 0L) + votesDelta).coerceAtLeast(0L)
                    }
                    if (cheersDelta != 0L) {
                        updates["cheersCount"] = ((snap.getLong("cheersCount") ?: 0L) + cheersDelta).coerceAtLeast(0L)
                    }
                    transaction.update(docRef, updates)
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deletePerformance(performanceId: String): Result<Unit> {
        val docRef = firestore.collection(COLLECTION_PERFORMANCES).document(sanitizeId(performanceId))
        return try {
            requireUserId()
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // =========================================================================
    // 3. 'votes' Collection Operations
    // =========================================================================

    suspend fun castVote(
        performerId: String,
        performerName: String,
        category: PerformanceCategory = PerformanceCategory.MUSIC,
        voteDelta: Int = 1,
        voteTier: String = "VOTE",
        targetId: String = performerId,
        targetType: String = "PERFORMER",
        genre: String? = null,
        stageTitle: String? = null,
        performanceId: String? = null,
        topPerformanceTitle: String? = null,
        sessionId: String? = null,
        avatarColorHex: Long? = null,
        voteId: String? = null
    ): Result<String> {
        val docId = voteId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) }
            ?: "vote_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = firestore.collection(COLLECTION_VOTES).document(docId)
        return try {
            val uid = requireUserId()
            val sanitizedPerformerId = sanitizeId(performerId)
            val sanitizedTargetId = sanitizeId(targetId)
            val sanitizedTier = if (voteTier in ALLOWED_TIERS) voteTier else "VOTE"
            val sanitizedTargetType = if (targetType in ALLOWED_TARGET_TYPES) targetType else "PERFORMER"
            val boundedDelta = voteDelta.coerceIn(1, 100)

            val payload = mapOf<String, Any?>(
                "performerId" to sanitizedPerformerId,
                "performerName" to performerName.ifBlank { "Stage Artist" }.take(120),
                "targetId" to sanitizedTargetId,
                "targetType" to sanitizedTargetType,
                "category" to category.name,
                "genre" to genre?.takeIf { it.isNotBlank() }?.take(80),
                "stageTitle" to stageTitle?.takeIf { it.isNotBlank() }?.take(120),
                "performanceId" to performanceId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) },
                "topPerformanceTitle" to topPerformanceTitle?.takeIf { it.isNotBlank() }?.take(160),
                "sessionId" to sessionId?.takeIf { it.isNotBlank() }?.let { sanitizeId(it) },
                "userId" to uid,
                "userName" to currentUserDisplayName(),
                "voteDelta" to boundedDelta,
                "voteTier" to sanitizedTier,
                "status" to STATUS_COUNTED,
                "avatarColorHex" to avatarColorHex,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()

            // Best-effort cached vote counter synchronization on existing performer & performance docs
            incrementPerformerVotes(sanitizedPerformerId, boundedDelta.toLong())
            performanceId?.takeIf { it.isNotBlank() }?.let { perfId ->
                updatePerformanceEngagement(perfId, votesDelta = boundedDelta.toLong())
            }

            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun revokeVote(voteId: String): Result<Unit> {
        val docRef = firestore.collection(COLLECTION_VOTES).document(sanitizeId(voteId))
        return try {
            requireUserId()
            docRef.update(
                mapOf(
                    "status" to STATUS_REVOKED,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getVoteById(voteId: String): Result<VoteRecord?> {
        val docRef = firestore.collection(COLLECTION_VOTES).document(sanitizeId(voteId))
        return try {
            val snap = docRef.get().await()
            Result.success(if (snap.exists()) snap.toVoteRecordOrNull() else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observeVotes(performerId: String? = null): Flow<List<VoteRecord>> {
        var query: Query = firestore.collection(COLLECTION_VOTES)
            .whereEqualTo("status", STATUS_COUNTED)
        if (!performerId.isNullOrBlank()) {
            query = query.whereEqualTo("performerId", sanitizeId(performerId))
        }
        return query.snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toVoteRecordOrNull() } }
            .catch { throwable ->
                if (throwable is Exception && throwable !is CancellationException) {
                    handleFirestoreError(throwable, OperationType.LIST, COLLECTION_VOTES)
                }
                throw throwable
            }
    }

    fun observeVotesAuthenticated(performerId: String? = null): Flow<List<VoteRecord>> =
        observeAuthUid().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else observeVotes(performerId)
        }

    fun observeUserVotes(): Flow<List<VoteRecord>> {
        val uid = requireUserId()
        return firestore.collection(COLLECTION_VOTES)
            .whereEqualTo("userId", uid)
            .snapshots()
            .map { snapshot -> snapshot.documents.mapNotNull { it.toVoteRecordOrNull() } }
            .catch { throwable ->
                if (throwable is Exception && throwable !is CancellationException) {
                    handleFirestoreError(throwable, OperationType.LIST, COLLECTION_VOTES)
                }
                throw throwable
            }
    }

    fun observeLeaderboardAggregation(
        timePeriod: LeaderboardTimePeriod = LeaderboardTimePeriod.ALL_TIME
    ): Flow<VotesAggregationSnapshot> = observeVotes().map { records ->
        VotesLeaderboardRepository.aggregateVotes(
            votes = records,
            timePeriod = timePeriod,
            currentUserId = auth.currentUser?.uid
        )
    }

    // =========================================================================
    // 4. 'users' Collection Operations (Performer & Spectator Registration)
    // =========================================================================

    /**
     * Creates or updates the authenticated user's registration account in `/users/{uid}`.
     * When [account.role] is [UserRole.CREATOR] (Performer), also synchronizes `/performers/{uid}`.
     */
    suspend fun saveUserAccount(account: UserAccount): Result<UserAccount> {
        return try {
            val uid = requireUserId()
            val sanitizedUid = sanitizeId(uid)
            val docRef = firestore.collection(COLLECTION_USERS).document(sanitizedUid)

            val resolvedDisplayName = account.displayName
                .ifBlank { account.stageName }
                .ifBlank { currentUserDisplayName() }
                .take(120)
            val resolvedStageName = account.stageName
                .ifBlank { resolvedDisplayName }
                .take(120)
            val resolvedStageTitle = account.stageTitle.ifBlank {
                if (account.role == UserRole.CREATOR) "${account.primaryCategory.displayName} Performer"
                else "Stage Spectator"
            }.take(120)
            val resolvedBio = account.bio.take(1000)

            try {
                firestore.runTransaction { transaction ->
                    val existingSnap = transaction.get(docRef)
                    val baseFields = mapOf<String, Any?>(
                        "email" to account.email?.takeIf { it.isNotBlank() }?.take(200),
                        "displayName" to resolvedDisplayName,
                        "photoUrl" to account.photoUrl?.takeIf { it.isNotBlank() }?.take(500),
                        "role" to account.role.name,
                        "stageName" to resolvedStageName,
                        "stageTitle" to resolvedStageTitle,
                        "bio" to resolvedBio.takeIf { it.isNotBlank() },
                        "primaryCategory" to account.primaryCategory.name,
                        "registrationComplete" to account.registrationComplete,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ).filterValues { it != null }

                    if (existingSnap.exists()) {
                        transaction.update(docRef, baseFields)
                    } else {
                        val createPayload = baseFields + mapOf(
                            "uid" to sanitizedUid,
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                        transaction.set(docRef, createPayload)
                    }
                }.await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, docRef.path)
                return Result.failure(e)
            }

            val savedAccount = account.copy(
                uid = sanitizedUid,
                displayName = resolvedDisplayName,
                stageName = resolvedStageName,
                stageTitle = resolvedStageTitle,
                bio = resolvedBio,
                registrationComplete = account.registrationComplete
            )

            // Automatically provision/update public Performer record when registered as Performer (CREATOR)
            if (savedAccount.role == UserRole.CREATOR) {
                val existingPerformer = getPerformer(sanitizedUid).getOrNull()
                val performerDoc = Performer(
                    id = sanitizedUid,
                    creatorId = sanitizedUid,
                    ownerUid = uid,
                    name = resolvedStageName,
                    stageTitle = resolvedStageTitle,
                    bio = resolvedBio.ifBlank { "Performing original ${savedAccount.primaryCategory.displayName.lowercase()} on VersePulse." },
                    category = savedAccount.primaryCategory,
                    avatarUrl = savedAccount.photoUrl ?: existingPerformer?.avatarUrl,
                    avatarColorHex = existingPerformer?.avatarColorHex
                        ?: if (savedAccount.primaryCategory == PerformanceCategory.MUSIC) 0xFF9D4EDD else 0xFF00B4D8,
                    rating = existingPerformer?.rating ?: 5.0f,
                    ratingCount = existingPerformer?.ratingCount ?: 1,
                    totalVotes = existingPerformer?.totalVotes ?: 0,
                    totalPerformances = existingPerformer?.totalPerformances ?: 0,
                    totalSessions = existingPerformer?.totalSessions ?: 0,
                    socialSpotify = existingPerformer?.socialSpotify ?: "",
                    socialInstagram = existingPerformer?.socialInstagram ?: "",
                    socialYoutube = existingPerformer?.socialYoutube ?: "",
                    socialWebsite = existingPerformer?.socialWebsite ?: "",
                    visibility = VISIBILITY_PUBLIC
                )
                val perfRes = savePerformer(performerDoc)
                if (perfRes.isFailure) {
                    return Result.failure(
                        perfRes.exceptionOrNull() ?: IllegalStateException("Failed to provision performer profile")
                    )
                }
            }

            Result.success(savedAccount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "$COLLECTION_USERS/current")
            Result.failure(e)
        }
    }

    suspend fun getUserAccount(userId: String): Result<UserAccount?> {
        val docRef = firestore.collection(COLLECTION_USERS).document(sanitizeId(userId))
        return try {
            val snap = docRef.get().await()
            Result.success(if (snap.exists()) snap.toUserAccountOrNull() else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserAccount(userId: String): Flow<UserAccount?> {
        val docRef = firestore.collection(COLLECTION_USERS).document(sanitizeId(userId))
        return docRef.snapshots()
            .map { snap -> if (snap.exists()) snap.toUserAccountOrNull() else null }
            .catch { throwable ->
                if (throwable is Exception && throwable !is CancellationException) {
                    handleFirestoreError(throwable, OperationType.GET, docRef.path)
                }
                throw throwable
            }
    }

    fun observeCurrentUserAccountAuthenticated(): Flow<UserAccount?> =
        observeAuthUid().flatMapLatest { uid ->
            if (uid == null) flowOf(null) else observeUserAccount(uid).catch { emit(null) }
        }

    /**
     * Permanently deletes the signed-in user's `/users/{uid}` account document and ALL associated
     * user content across Firestore:
     * - Owned `/performances`, `/live_sessions`, `/events`, `/creator_profiles`, `/performers`
     * - Owned `/stages` along with their `voting_counters` and `spectator_votes` subcollections
     * - Spectator votes (`/stages/{stageId}/spectator_votes`), `/votes`, `/poll_votes`, and `/session_votes`
     *
     * Verifies `/users/{targetId}` access first so cross-user deletion attempts reject immediately
     * with `PERMISSION_DENIED` without mutating any records.
     */
    suspend fun deleteUserAccount(userId: String = ""): Result<Unit> {
        return try {
            val uid = requireUserId()
            val targetId = sanitizeId(userId.ifBlank { uid })
            val userRef = firestore.collection(COLLECTION_USERS).document(targetId)
            val performerRef = firestore.collection(COLLECTION_PERFORMERS).document(targetId)

            // Gate on /users/{targetId} read first so cross-user targets fail fast with PERMISSION_DENIED.
            val userSnap = userRef.get().await()

            val deletedPaths = HashSet<String>()

            // 1. Delete user-created performances, live sessions (including chat_messages subcollection),
            //    events, and creator profiles
            deleteQueryDocs(
                firestore.collection(COLLECTION_PERFORMANCES).whereEqualTo("ownerUid", uid),
                deletedPaths
            )
            val ownedSessionsSnap = firestore.collection(COLLECTION_LIVE_SESSIONS)
                .whereEqualTo("ownerUid", uid)
                .get()
                .await()
            val ownedSessionRefs = mutableListOf<DocumentReference>()
            for (sessionDoc in ownedSessionsSnap.documents) {
                deleteQueryDocs(
                    sessionDoc.reference
                        .collection(SUBCOLLECTION_CHAT_MESSAGES)
                        .whereEqualTo("sessionOwnerUid", uid),
                    deletedPaths
                )
                if (sessionDoc.reference.path !in deletedPaths) {
                    ownedSessionRefs.add(sessionDoc.reference)
                }
            }
            deleteRefsInBatches(ownedSessionRefs, deletedPaths)
            deleteQueryDocs(
                firestore.collection(COLLECTION_EVENTS).whereEqualTo("ownerUid", uid),
                deletedPaths
            )
            deleteQueryDocs(
                firestore.collection(COLLECTION_CREATOR_PROFILES).whereEqualTo("ownerUid", uid),
                deletedPaths
            )

            // 2. Delete hosted stages along with their subcollections BEFORE deleting the stage docs
            //    (delete rules on voting_counters/spectator_votes inspect parent stage.ownerUid)
            val ownedStagesSnap = firestore.collection(COLLECTION_STAGES)
                .whereEqualTo("ownerUid", uid)
                .get()
                .await()
            val stageRefs = mutableListOf<DocumentReference>()
            for (stageDoc in ownedStagesSnap.documents) {
                deleteQueryDocs(
                    stageDoc.reference.collection(SUBCOLLECTION_VOTING_COUNTERS),
                    deletedPaths
                )
                deleteQueryDocs(
                    stageDoc.reference.collection(SUBCOLLECTION_SPECTATOR_VOTES),
                    deletedPaths
                )
                if (stageDoc.reference.path !in deletedPaths) {
                    stageRefs.add(stageDoc.reference)
                }
            }
            deleteRefsInBatches(stageRefs, deletedPaths)

            // 3. Delete spectator votes and live stage chat messages cast/sent by this user on other hosts' stages
            deleteQueryDocs(
                firestore.collectionGroup(SUBCOLLECTION_SPECTATOR_VOTES).whereEqualTo("voterUid", uid),
                deletedPaths
            )
            deleteQueryDocs(
                firestore.collectionGroup(SUBCOLLECTION_CHAT_MESSAGES).whereEqualTo("senderUid", uid),
                deletedPaths
            )

            // 4. Delete leaderboard votes, poll votes, and session votes cast by this user
            deleteQueryDocs(
                firestore.collection(COLLECTION_VOTES).whereEqualTo("userId", uid),
                deletedPaths
            )
            deleteQueryDocs(
                firestore.collection(COLLECTION_POLL_VOTES).whereEqualTo("userId", uid),
                deletedPaths
            )
            deleteQueryDocs(
                firestore.collection(COLLECTION_SESSION_VOTES).whereEqualTo("userId", uid),
                deletedPaths
            )

            // 5. Delete owned /performers docs and direct /performers/{targetId}
            deleteQueryDocs(
                firestore.collection(COLLECTION_PERFORMERS).whereEqualTo("ownerUid", uid),
                deletedPaths
            )
            if (performerRef.path !in deletedPaths) {
                val perfSnap = performerRef.get().await()
                if (perfSnap.exists() && perfSnap.getString("ownerUid") == uid) {
                    performerRef.delete().await()
                    deletedPaths.add(performerRef.path)
                }
            }

            // 6. Delete /users/{targetId} account document last
            if (userSnap.exists() && userRef.path !in deletedPaths) {
                userRef.delete().await()
                deletedPaths.add(userRef.path)
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val path = "$COLLECTION_USERS/${userId.ifBlank { "current" }}"
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    private suspend fun deleteQueryDocs(
        query: Query,
        deletedPaths: MutableSet<String>
    ) {
        val snap = query.get().await()
        val refs = snap.documents
            .map { it.reference }
            .filter { it.path !in deletedPaths }
        deleteRefsInBatches(refs, deletedPaths)
    }

    private suspend fun deleteRefsInBatches(
        refs: List<DocumentReference>,
        deletedPaths: MutableSet<String>
    ) {
        if (refs.isEmpty()) return
        refs.chunked(MAX_DELETE_BATCH_SIZE).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { ref -> batch.delete(ref) }
            batch.commit().await()
            chunk.forEach { ref -> deletedPaths.add(ref.path) }
        }
    }

    companion object {
        const val COLLECTION_PERFORMERS = "performers"
        const val COLLECTION_PERFORMANCES = "performances"
        const val COLLECTION_VOTES = "votes"
        const val COLLECTION_USERS = "users"
        const val COLLECTION_CREATOR_PROFILES = "creator_profiles"
        const val COLLECTION_LIVE_SESSIONS = "live_sessions"
        const val COLLECTION_EVENTS = "events"
        const val COLLECTION_STAGES = "stages"
        const val COLLECTION_POLL_VOTES = "poll_votes"
        const val COLLECTION_SESSION_VOTES = "session_votes"
        const val SUBCOLLECTION_VOTING_COUNTERS = "voting_counters"
        const val SUBCOLLECTION_SPECTATOR_VOTES = "spectator_votes"
        const val SUBCOLLECTION_CHAT_MESSAGES = "chat_messages"
        private const val MAX_DELETE_BATCH_SIZE = 450

        const val VISIBILITY_PUBLIC = "PUBLIC"
        const val VISIBILITY_PRIVATE = "PRIVATE"
        const val STATUS_COUNTED = "COUNTED"
        const val STATUS_REVOKED = "REVOKED"

        private val ALLOWED_VISIBILITY = setOf(VISIBILITY_PUBLIC, VISIBILITY_PRIVATE)
        private val ALLOWED_TIERS = setOf("VOTE", "SUPER_VOTE", "STANDING_OVATION", "POLL_VOTE")
        private val ALLOWED_TARGET_TYPES = setOf("PERFORMER", "PERFORMANCE", "SESSION", "POLL")

        fun sanitizeId(raw: String): String = VotesLeaderboardRepository.sanitizeId(raw)

        @Volatile
        private var cachedFirestore: Pair<String, FirebaseFirestore>? = null
        private val firestoreLock = Any()

        internal fun createFirestore(context: Context): FirebaseFirestore {
            val appContext = context.applicationContext
            com.example.security.UploadEncryptionManager.bindContext(appContext)
            val databaseId = appContext.getString(R.string.firestore_database_id)
            cachedFirestore?.let { (cachedId, instance) ->
                if (cachedId == databaseId) return instance
            }
            return synchronized(firestoreLock) {
                cachedFirestore?.let { (cachedId, instance) ->
                    if (cachedId == databaseId) return@synchronized instance
                }
                if (FirebaseApp.getApps(appContext).isEmpty()) {
                    FirebaseApp.initializeApp(appContext)
                }
                FirebaseFirestore.getInstance(FirebaseApp.getInstance(), databaseId).also { db ->
                    cachedFirestore = databaseId to db
                }
            }
        }

        internal fun DocumentSnapshot.toPerformerOrNull(): Performer? {
            val creatorId = getString("creatorId")?.takeIf { it.isNotBlank() } ?: id
            val name = getString("name")?.takeIf { it.isNotBlank() } ?: return null
            val catStr = getString("category") ?: PerformanceCategory.MUSIC.name
            val category = try {
                PerformanceCategory.valueOf(catStr)
            } catch (_: Exception) {
                PerformanceCategory.MUSIC
            }
            val createdTs = try {
                getTimestamp("createdAt")?.toDate()?.time
            } catch (_: Exception) {
                getLong("createdAt")
            } ?: System.currentTimeMillis()
            val updatedTs = try {
                getTimestamp("updatedAt")?.toDate()?.time
            } catch (_: Exception) {
                getLong("updatedAt")
            } ?: createdTs

            return Performer(
                id = id,
                creatorId = creatorId,
                ownerUid = getString("ownerUid") ?: "",
                name = name,
                stageTitle = getString("stageTitle") ?: "Stage Performer",
                bio = getString("bio") ?: "",
                category = category,
                avatarUrl = getString("avatarUrl")?.takeIf { it.isNotBlank() },
                avatarColorHex = getLong("avatarColorHex") ?: 0xFF9D4EDD,
                rating = (getDouble("rating") ?: 5.0).toFloat(),
                ratingCount = (getLong("ratingCount") ?: 1L).toInt(),
                totalVotes = (getLong("totalVotes") ?: 0L).toInt(),
                totalPerformances = (getLong("totalPerformances") ?: 0L).toInt(),
                totalSessions = (getLong("totalSessions") ?: 0L).toInt(),
                socialSpotify = getString("socialSpotify") ?: "",
                socialInstagram = getString("socialInstagram") ?: "",
                socialYoutube = getString("socialYoutube") ?: "",
                socialWebsite = getString("socialWebsite") ?: "",
                visibility = getString("visibility") ?: VISIBILITY_PUBLIC,
                createdAtMillis = createdTs,
                updatedAtMillis = updatedTs
            )
        }

        internal fun DocumentSnapshot.toPerformanceOrNull(): Performance? {
            val perfId = getString("id")?.takeIf { it.isNotBlank() } ?: id
            val title = getString("title")?.takeIf { it.isNotBlank() } ?: return null
            val artistId = getString("artistId")?.takeIf { it.isNotBlank() } ?: return null
            val cat = try {
                PerformanceCategory.valueOf(getString("category") ?: "MUSIC")
            } catch (_: Exception) {
                PerformanceCategory.MUSIC
            }
            val viz = try {
                VisualizerTheme.valueOf(getString("visualizerTheme") ?: "NEON_WAVES")
            } catch (_: Exception) {
                VisualizerTheme.NEON_WAVES
            }
            val audioType = try {
                SynthAudioType.valueOf(getString("audioType") ?: "UPLOADED_MUSIC")
            } catch (_: Exception) {
                SynthAudioType.UPLOADED_MUSIC
            }
            return Performance(
                id = perfId,
                title = title,
                artistId = artistId,
                artistName = getString("artistName") ?: "Stage Artist",
                avatarInitials = getString("avatarInitials") ?: "ME",
                category = cat,
                genre = getString("genre") ?: "Original",
                durationSec = (getLong("durationSec") ?: 180L).toInt(),
                lyricsOrScript = getString("lyricsOrScript") ?: "",
                visualizerTheme = viz,
                audioType = audioType,
                customAudioPath = com.example.security.UploadEncryptionManager.decryptField(
                    null,
                    getString("customAudioPath")?.takeIf { it.isNotBlank() }
                ),
                customVideoPath = com.example.security.UploadEncryptionManager.decryptField(
                    null,
                    getString("customVideoPath")?.takeIf { it.isNotBlank() }
                ),
                viewsCount = (getLong("viewsCount") ?: 0L).toInt(),
                likesCount = (getLong("likesCount") ?: 0L).toInt(),
                votesCount = (getLong("votesCount") ?: 0L).toInt(),
                cheersCount = (getLong("cheersCount") ?: 0L).toInt(),
                pollId = getString("pollId")?.takeIf { it.isNotBlank() },
                isUserCreated = true,
                createdAtTimestamp = getLong("createdAtTimestamp") ?: System.currentTimeMillis()
            )
        }

        internal fun DocumentSnapshot.toVoteRecordOrNull(): VoteRecord? {
            val performerId = getString("performerId")?.takeIf { it.isNotBlank() }
                ?: getString("targetId")?.takeIf { it.isNotBlank() }
                ?: return null
            val userId = getString("userId") ?: return null
            val category = try {
                PerformanceCategory.valueOf(getString("category") ?: PerformanceCategory.MUSIC.name)
            } catch (_: Exception) {
                PerformanceCategory.MUSIC
            }
            val createdTs = try {
                getTimestamp("createdAt")?.toDate()?.time
            } catch (_: Exception) {
                getLong("createdAt")
            } ?: System.currentTimeMillis()
            val updatedTs = try {
                getTimestamp("updatedAt")?.toDate()?.time
            } catch (_: Exception) {
                getLong("updatedAt")
            } ?: createdTs

            return VoteRecord(
                id = id,
                performerId = performerId,
                performerName = getString("performerName")?.takeIf { it.isNotBlank() } ?: "Stage Performer",
                targetId = getString("targetId")?.takeIf { it.isNotBlank() } ?: performerId,
                targetType = getString("targetType") ?: "PERFORMER",
                category = category,
                genre = getString("genre") ?: "Original",
                stageTitle = getString("stageTitle") ?: "Stage Artist",
                performanceId = getString("performanceId"),
                topPerformanceTitle = getString("topPerformanceTitle"),
                sessionId = getString("sessionId"),
                userId = userId,
                userName = getString("userName") ?: "Audience Voter",
                voteDelta = (getLong("voteDelta") ?: 1L).toInt().coerceAtLeast(1),
                voteTier = getString("voteTier") ?: "VOTE",
                status = getString("status") ?: STATUS_COUNTED,
                avatarColorHex = getLong("avatarColorHex")
                    ?: if (category == PerformanceCategory.MUSIC) 0xFF9D4EDD else 0xFF00B4D8,
                createdAtMillis = createdTs,
                updatedAtMillis = updatedTs
            )
        }

        internal fun DocumentSnapshot.toUserAccountOrNull(): UserAccount? {
            val uid = getString("uid")?.takeIf { it.isNotBlank() } ?: id
            val displayName = getString("displayName")?.takeIf { it.isNotBlank() } ?: return null
            val role = try {
                UserRole.valueOf(getString("role") ?: UserRole.SPECTATOR.name)
            } catch (_: Exception) {
                UserRole.SPECTATOR
            }
            val category = try {
                PerformanceCategory.valueOf(getString("primaryCategory") ?: PerformanceCategory.MUSIC.name)
            } catch (_: Exception) {
                PerformanceCategory.MUSIC
            }
            val createdTs = try {
                getTimestamp("createdAt")?.toDate()?.time
            } catch (_: Exception) {
                getLong("createdAt")
            } ?: System.currentTimeMillis()
            val updatedTs = try {
                getTimestamp("updatedAt")?.toDate()?.time
            } catch (_: Exception) {
                getLong("updatedAt")
            } ?: createdTs

            return UserAccount(
                uid = uid,
                email = getString("email")?.takeIf { it.isNotBlank() },
                displayName = displayName,
                photoUrl = getString("photoUrl")?.takeIf { it.isNotBlank() },
                role = role,
                stageName = getString("stageName") ?: displayName,
                stageTitle = getString("stageTitle") ?: "",
                bio = getString("bio") ?: "",
                primaryCategory = category,
                registrationComplete = getBoolean("registrationComplete") ?: true,
                createdAtMillis = createdTs,
                updatedAtMillis = updatedTs
            )
        }
    }
}
