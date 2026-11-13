package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.example.model.CreatorProfile
import com.example.model.LiveChatMessage
import com.example.model.LiveSession
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.StageReactionType
import com.example.model.SynthAudioType
import com.example.model.VisualizerTheme
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalCoroutinesApi::class)
class FirestoreDataSource(private val context: Context) {

    private val appContext = context.applicationContext.also {
        com.example.security.UploadEncryptionManager.bindContext(it)
    }

    private val db: FirebaseFirestore? by lazy {
        try {
            FirestoreService.createFirestore(appContext)
        } catch (e: Exception) {
            Log.e("FirestoreDataSource", "Failed to initialize Firestore with custom database ID", e)
            null
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            Firebase.auth
        } catch (_: Exception) {
            null
        }
    }

    fun isAvailable(): Boolean = db != null

    private fun currentUid(): String? = auth?.currentUser?.uid

    private fun observeAuthUid(): Flow<String?> = callbackFlow {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val listener = FirebaseAuth.AuthStateListener { a ->
            trySend(a.currentUser?.uid)
        }
        firebaseAuth.addAuthStateListener(listener)
        awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    // 1. Creator Profile Data Persistence
    fun saveCreatorProfile(profile: CreatorProfile) {
        val firestore = db ?: return
        val uid = currentUid() ?: return
        val sanitizedCreatorId = VotesLeaderboardRepository.sanitizeId(profile.creatorId)
        val docRef = firestore.collection("creator_profiles").document(sanitizedCreatorId)
        val data = mapOf<String, Any?>(
            "creatorId" to sanitizedCreatorId,
            "ownerUid" to uid,
            "artistName" to profile.artistName.ifBlank { "Stage Artist" }.take(120),
            "stageTitle" to profile.stageTitle.take(120),
            "bio" to profile.bio.take(1000),
            "primaryCategory" to profile.primaryCategory.name,
            "avatarUrl" to profile.avatarUrl?.take(500),
            "avatarColorHex" to profile.avatarColorHex,
            "audienceRating" to profile.audienceRating.toDouble().coerceIn(0.0, 5.0),
            "totalAudienceRatingsCount" to profile.totalAudienceRatingsCount.toLong().coerceAtLeast(0L),
            "socialSpotify" to profile.socialSpotify.take(200),
            "socialInstagram" to profile.socialInstagram.take(200),
            "socialYoutube" to profile.socialYoutube.take(200),
            "socialWebsite" to profile.socialWebsite.take(200),
            "visibility" to "PUBLIC",
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        docRef.set(data, SetOptions.merge())
            .addOnSuccessListener { Log.d("FirestoreDataSource", "Creator profile saved: $sanitizedCreatorId") }
            .addOnFailureListener { e -> handleFirestoreError(e, OperationType.WRITE, docRef.path) }
    }

    fun updateCreatorAudienceRating(creatorId: String, newRating: Float, newRatingsCount: Int) {
        val firestore = db ?: return
        if (currentUid() == null) return
        val sanitizedCreatorId = VotesLeaderboardRepository.sanitizeId(creatorId)
        val docRef = firestore.collection("creator_profiles").document(sanitizedCreatorId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            if (snapshot.exists()) {
                transaction.update(
                    docRef,
                    mapOf(
                        "audienceRating" to newRating.toDouble().coerceIn(0.0, 5.0),
                        "totalAudienceRatingsCount" to newRatingsCount.toLong().coerceAtLeast(0L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }
            .addOnSuccessListener { Log.d("FirestoreDataSource", "Creator audience rating updated: $sanitizedCreatorId") }
            .addOnFailureListener { e -> handleFirestoreError(e, OperationType.UPDATE, docRef.path) }
    }

    fun observeCreatorProfiles(): Flow<Map<String, CreatorProfile>> =
        observeAuthUid().flatMapLatest { uid ->
            val firestore = db
            if (firestore == null || uid == null) {
                flowOf(emptyMap())
            } else {
                callbackFlow {
                    val registration = firestore.collection("creator_profiles")
                        .whereEqualTo("visibility", "PUBLIC")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                handleFirestoreError(error, OperationType.LIST, "creator_profiles")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val map = mutableMapOf<String, CreatorProfile>()
                                for (doc in snapshot.documents) {
                                    try {
                                        val id = doc.getString("creatorId") ?: doc.id
                                        val name = doc.getString("artistName") ?: "Artist"
                                        val title = doc.getString("stageTitle") ?: "Performer"
                                        val bio = doc.getString("bio") ?: ""
                                        val catStr = doc.getString("primaryCategory") ?: PerformanceCategory.MUSIC.name
                                        val category = try {
                                            PerformanceCategory.valueOf(catStr)
                                        } catch (_: Exception) {
                                            PerformanceCategory.MUSIC
                                        }
                                        val rating = (doc.getDouble("audienceRating") ?: 5.0).toFloat()
                                        val count = (doc.getLong("totalAudienceRatingsCount") ?: 10).toInt()
                                        val spotify = doc.getString("socialSpotify") ?: ""
                                        val insta = doc.getString("socialInstagram") ?: ""
                                        val yt = doc.getString("socialYoutube") ?: ""
                                        val web = doc.getString("socialWebsite") ?: ""
                                        val avatarUrl = doc.getString("avatarUrl")?.takeIf { it.isNotBlank() }
                                        val avatarColor = doc.getLong("avatarColorHex") ?: 0xFF9D4EDD

                                        map[id] = CreatorProfile(
                                            creatorId = id,
                                            artistName = name,
                                            stageTitle = title,
                                            bio = bio,
                                            primaryCategory = category,
                                            avatarUrl = avatarUrl,
                                            avatarColorHex = avatarColor,
                                            audienceRating = rating,
                                            totalAudienceRatingsCount = count,
                                            socialSpotify = spotify,
                                            socialInstagram = insta,
                                            socialYoutube = yt,
                                            socialWebsite = web,
                                            isCurrentUser = (doc.getString("ownerUid") == uid)
                                        )
                                    } catch (e: Exception) {
                                        Log.w("FirestoreDataSource", "Error parsing profile doc", e)
                                    }
                                }
                                trySend(map)
                            }
                        }
                    awaitClose { registration.remove() }
                }
            }
        }

    fun observeCreatorProfile(creatorId: String): Flow<CreatorProfile?> =
        observeAuthUid().flatMapLatest { uid ->
            val firestore = db
            if (firestore == null || uid == null) {
                flowOf(null)
            } else {
                callbackFlow {
                    val sanitizedId = VotesLeaderboardRepository.sanitizeId(creatorId)
                    val docRef = firestore.collection("creator_profiles").document(sanitizedId)
                    val registration = docRef.addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            handleFirestoreError(error, OperationType.GET, docRef.path)
                            trySend(null)
                            return@addSnapshotListener
                        }
                        if (snapshot != null && snapshot.exists()) {
                            try {
                                val id = snapshot.getString("creatorId") ?: snapshot.id
                                val name = snapshot.getString("artistName") ?: "Artist"
                                val title = snapshot.getString("stageTitle") ?: "Performer"
                                val bio = snapshot.getString("bio") ?: ""
                                val catStr = snapshot.getString("primaryCategory") ?: PerformanceCategory.MUSIC.name
                                val category = try {
                                    PerformanceCategory.valueOf(catStr)
                                } catch (_: Exception) {
                                    PerformanceCategory.MUSIC
                                }
                                val rating = (snapshot.getDouble("audienceRating") ?: 5.0).toFloat()
                                val count = (snapshot.getLong("totalAudienceRatingsCount") ?: 10).toInt()
                                val spotify = snapshot.getString("socialSpotify") ?: ""
                                val insta = snapshot.getString("socialInstagram") ?: ""
                                val yt = snapshot.getString("socialYoutube") ?: ""
                                val web = snapshot.getString("socialWebsite") ?: ""
                                val avatarUrl = snapshot.getString("avatarUrl")?.takeIf { it.isNotBlank() }
                                val avatarColor = snapshot.getLong("avatarColorHex") ?: 0xFF9D4EDD

                                trySend(
                                    CreatorProfile(
                                        creatorId = id,
                                        artistName = name,
                                        stageTitle = title,
                                        bio = bio,
                                        primaryCategory = category,
                                        avatarUrl = avatarUrl,
                                        avatarColorHex = avatarColor,
                                        audienceRating = rating,
                                        totalAudienceRatingsCount = count,
                                        socialSpotify = spotify,
                                        socialInstagram = insta,
                                        socialYoutube = yt,
                                        socialWebsite = web,
                                        isCurrentUser = (snapshot.getString("ownerUid") == uid)
                                    )
                                )
                            } catch (e: Exception) {
                                Log.w("FirestoreDataSource", "Error parsing profile doc $creatorId", e)
                                trySend(null)
                            }
                        } else {
                            trySend(null)
                        }
                    }
                    awaitClose { registration.remove() }
                }
            }
        }

    fun observeCreatorPerformances(creatorId: String): Flow<List<Performance>> =
        observeAuthUid().flatMapLatest { uid ->
            val firestore = db
            if (firestore == null || uid == null) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val registration = firestore.collection("performances")
                        .whereEqualTo("artistId", creatorId)
                        .whereEqualTo("visibility", "PUBLIC")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                handleFirestoreError(error, OperationType.LIST, "performances")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val list = snapshot.documents.mapNotNull { doc -> parsePerformanceDoc(doc) }
                                trySend(list)
                            }
                        }
                    awaitClose { registration.remove() }
                }
            }
        }

    // 2. Live Stream Metadata, Moderation & Reactions Persistence
    fun saveLiveSession(session: LiveSession) {
        val firestore = db ?: return
        val uid = currentUid() ?: return
        val sanitizedSessionId = VotesLeaderboardRepository.sanitizeId(session.id)
        val docRef = firestore.collection("live_sessions").document(sanitizedSessionId)

        firestore.runTransaction { transaction ->
            val existing = transaction.get(docRef)
            val reactionMap = mapOf(
                StageReactionType.FIRE.firestoreField to (session.reactionCounts[StageReactionType.FIRE] ?: 0).toLong().coerceAtLeast(0L),
                StageReactionType.MIC_DROP.firestoreField to (session.reactionCounts[StageReactionType.MIC_DROP] ?: 0).toLong().coerceAtLeast(0L),
                StageReactionType.OVATION.firestoreField to (session.reactionCounts[StageReactionType.OVATION] ?: 0).toLong().coerceAtLeast(0L),
                StageReactionType.HEART.firestoreField to (session.reactionCounts[StageReactionType.HEART] ?: 0).toLong().coerceAtLeast(0L),
                StageReactionType.ENCORE.firestoreField to (session.reactionCounts[StageReactionType.ENCORE] ?: 0).toLong().coerceAtLeast(0L)
            )
            if (existing.exists()) {
                val updateMap = mutableMapOf<String, Any?>(
                    "title" to session.title.ifBlank { "Live Stage" }.take(160),
                    "isLive" to session.isLive,
                    "spectatorCount" to session.spectatorCount.toLong().coerceAtLeast(0L),
                    "activePollId" to session.activePollId?.take(128),
                    "visibility" to "PUBLIC",
                    "slowModeSeconds" to session.slowModeSeconds.coerceIn(0, 120),
                    "isChatLocked" to session.isChatLocked,
                    "pinnedAnnouncement" to session.pinnedAnnouncement?.take(240),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.update(docRef, updateMap.filterValues { it != null || updateMap.containsKey("pinnedAnnouncement") })
            } else {
                val createData = (mapOf<String, Any?>(
                    "id" to sanitizedSessionId,
                    "creatorId" to VotesLeaderboardRepository.sanitizeId(session.creatorId.ifBlank { uid }),
                    "ownerUid" to uid,
                    "creatorName" to session.creatorName.take(120),
                    "title" to session.title.ifBlank { "Live Stage" }.take(160),
                    "category" to session.category.name,
                    "genre" to session.genre.take(80),
                    "visualizerTheme" to session.visualizerTheme.name,
                    "audioType" to session.audioType.name,
                    "activePollId" to session.activePollId?.take(128),
                    "spectatorCount" to session.spectatorCount.toLong().coerceAtLeast(0L),
                    "isLive" to session.isLive,
                    "slowModeSeconds" to session.slowModeSeconds.coerceIn(0, 120),
                    "isChatLocked" to session.isChatLocked,
                    "pinnedAnnouncement" to session.pinnedAnnouncement?.takeIf { it.isNotBlank() }?.take(240),
                    "visibility" to "PUBLIC",
                    "startedAt" to session.startedAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                ) + reactionMap).filterValues { it != null }
                transaction.set(docRef, createData)
            }
        }
            .addOnSuccessListener { Log.d("FirestoreDataSource", "Live session updated: $sanitizedSessionId") }
            .addOnFailureListener { e -> handleFirestoreError(e, OperationType.WRITE, docRef.path) }
    }

    suspend fun updateLiveSessionModeration(
        sessionId: String,
        slowModeSeconds: Int? = null,
        isChatLocked: Boolean? = null,
        pinnedAnnouncement: String? = null,
        clearPinnedAnnouncement: Boolean = false
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        if (currentUid() == null) return Result.failure(IllegalStateException("User not signed in"))
        val sanitizedSessionId = VotesLeaderboardRepository.sanitizeId(sessionId)
        val docRef = firestore.collection("live_sessions").document(sanitizedSessionId)
        return try {
            val updates = mutableMapOf<String, Any?>(
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (slowModeSeconds != null) {
                updates["slowModeSeconds"] = slowModeSeconds.coerceIn(0, 120)
            }
            if (isChatLocked != null) {
                updates["isChatLocked"] = isChatLocked
            }
            if (clearPinnedAnnouncement) {
                updates["pinnedAnnouncement"] = null
            } else if (pinnedAnnouncement != null) {
                updates["pinnedAnnouncement"] = pinnedAnnouncement.trim().take(240).ifBlank { null }
            }
            firestore.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    tx.update(docRef, updates)
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

    suspend fun incrementLiveSessionReaction(
        sessionId: String,
        reactionType: StageReactionType,
        delta: Int = 1
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        if (currentUid() == null) return Result.failure(IllegalStateException("User not signed in"))
        val sanitizedSessionId = VotesLeaderboardRepository.sanitizeId(sessionId)
        val docRef = firestore.collection("live_sessions").document(sanitizedSessionId)
        val boundedDelta = delta.coerceIn(1, 25).toLong()
        return try {
            firestore.runTransaction { tx ->
                val snap = tx.get(docRef)
                if (snap.exists()) {
                    val currentCount = (snap.getLong(reactionType.firestoreField) ?: 0L).coerceAtLeast(0L)
                    tx.update(
                        docRef,
                        mapOf(
                            reactionType.firestoreField to (currentCount + boundedDelta),
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

    suspend fun sendStageChatMessage(
        sessionId: String,
        sessionOwnerUid: String,
        message: LiveChatMessage
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        val uid = currentUid() ?: return Result.failure(IllegalStateException("User not signed in"))
        val sanitizedSessionId = VotesLeaderboardRepository.sanitizeId(sessionId)
        val sanitizedMsgId = VotesLeaderboardRepository.sanitizeId(message.id)
        val docRef = firestore.collection("live_sessions")
            .document(sanitizedSessionId)
            .collection(FirestoreService.SUBCOLLECTION_CHAT_MESSAGES)
            .document(sanitizedMsgId)
        return try {
            val payload = mapOf<String, Any?>(
                "id" to sanitizedMsgId,
                "sessionId" to sanitizedSessionId,
                "sessionOwnerUid" to sessionOwnerUid,
                "senderUid" to uid,
                "senderName" to message.senderName.ifBlank { "Stage Fan" }.take(120),
                "text" to message.text.trim().take(400),
                "isCheer" to message.isCheer,
                "isHiddenByHost" to false,
                "senderAvatarColorHex" to message.senderAvatarColorHex,
                "badge" to message.badge?.takeIf { it.isNotBlank() }?.take(32),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun setStageChatMessageHidden(
        sessionId: String,
        messageId: String,
        hidden: Boolean
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore unavailable"))
        if (currentUid() == null) return Result.failure(IllegalStateException("User not signed in"))
        val sanitizedSessionId = VotesLeaderboardRepository.sanitizeId(sessionId)
        val sanitizedMsgId = VotesLeaderboardRepository.sanitizeId(messageId)
        val docRef = firestore.collection("live_sessions")
            .document(sanitizedSessionId)
            .collection(FirestoreService.SUBCOLLECTION_CHAT_MESSAGES)
            .document(sanitizedMsgId)
        return try {
            docRef.update(
                mapOf(
                    "isHiddenByHost" to hidden,
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

    fun observeStageChatMessages(
        sessionId: String,
        isHost: Boolean,
        sessionOwnerUid: String
    ): Flow<List<LiveChatMessage>> =
        observeAuthUid().flatMapLatest { uid ->
            val firestore = db
            if (firestore == null || uid == null) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val sanitizedSessionId = VotesLeaderboardRepository.sanitizeId(sessionId)
                    val baseCollection = firestore.collection("live_sessions")
                        .document(sanitizedSessionId)
                        .collection(FirestoreService.SUBCOLLECTION_CHAT_MESSAGES)
                    val query = if (isHost && sessionOwnerUid.isNotBlank() && sessionOwnerUid == uid) {
                        baseCollection.whereEqualTo("sessionOwnerUid", uid)
                    } else {
                        baseCollection.whereEqualTo("isHiddenByHost", false)
                    }
                    val registration = query.addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            if (currentUid() == uid) {
                                handleFirestoreError(
                                    error,
                                    OperationType.LIST,
                                    "live_sessions/$sanitizedSessionId/${FirestoreService.SUBCOLLECTION_CHAT_MESSAGES}"
                                )
                            }
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val parsed = snapshot.documents.mapNotNull { doc ->
                                try {
                                    val msgId = doc.getString("id") ?: doc.id
                                    val senderName = doc.getString("senderName") ?: "Spectator"
                                    val text = doc.getString("text") ?: return@mapNotNull null
                                    val isCheer = doc.getBoolean("isCheer") ?: false
                                    val isHidden = doc.getBoolean("isHiddenByHost") ?: false
                                    val senderUid = doc.getString("senderUid") ?: ""
                                    val colorHex = doc.getLong("senderAvatarColorHex") ?: 0xFF00F5D4
                                    val badge = doc.getString("badge")?.takeIf { it.isNotBlank() }
                                    val createdMillis = try {
                                        doc.getTimestamp("createdAt")?.toDate()?.time
                                    } catch (_: Exception) {
                                        null
                                    } ?: System.currentTimeMillis()
                                    LiveChatMessage(
                                        id = msgId,
                                        senderName = senderName,
                                        text = text,
                                        isCheer = isCheer,
                                        timestamp = createdMillis,
                                        senderAvatarColorHex = colorHex,
                                        badge = badge,
                                        senderUid = senderUid,
                                        isHiddenByHost = isHidden
                                    )
                                } catch (e: Exception) {
                                    Log.w("FirestoreDataSource", "Error parsing chat message doc", e)
                                    null
                                }
                            }.sortedBy { it.timestamp }
                            trySend(parsed)
                        }
                    }
                    awaitClose { registration.remove() }
                }
            }
        }

    fun observeLiveSessions(): Flow<List<LiveSession>> =
        observeAuthUid().flatMapLatest { uid ->
            val firestore = db
            if (firestore == null || uid == null) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val registration = firestore.collection("live_sessions")
                        .whereEqualTo("isLive", true)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                handleFirestoreError(error, OperationType.LIST, "live_sessions")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val list = snapshot.documents.mapNotNull { doc -> parseLiveSessionDoc(doc, defaultLive = true) }
                                trySend(list)
                            }
                        }
                    awaitClose { registration.remove() }
                }
            }
        }

    fun observeCreatorSessions(creatorId: String): Flow<List<LiveSession>> =
        observeAuthUid().flatMapLatest { uid ->
            val firestore = db
            if (firestore == null || uid == null) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val registration = firestore.collection("live_sessions")
                        .whereEqualTo("creatorId", creatorId)
                        .whereEqualTo("visibility", "PUBLIC")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                handleFirestoreError(error, OperationType.LIST, "live_sessions")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val list = snapshot.documents
                                    .mapNotNull { doc -> parseLiveSessionDoc(doc, defaultLive = false) }
                                    .sortedByDescending { it.startedAt }
                                trySend(list)
                            }
                        }
                    awaitClose { registration.remove() }
                }
            }
        }

    // 3. Poll Votes Persistence
    fun recordVote(pollId: String, candidateId: String, userId: String) {
        val firestore = db ?: return
        val uid = currentUid() ?: return
        val sanitizedPoll = VotesLeaderboardRepository.sanitizeId(pollId)
        val sanitizedCandidate = VotesLeaderboardRepository.sanitizeId(candidateId)
        val voteDocId = VotesLeaderboardRepository.sanitizeId("${sanitizedPoll}_${uid}")
        val docRef = firestore.collection("poll_votes").document(voteDocId)
        val data = mapOf(
            "pollId" to sanitizedPoll,
            "candidateId" to sanitizedCandidate,
            "userId" to uid,
            "createdAt" to FieldValue.serverTimestamp()
        )
        docRef.set(data, SetOptions.merge())
            .addOnSuccessListener { Log.d("FirestoreDataSource", "Vote recorded in poll_votes: $sanitizedPoll") }
            .addOnFailureListener { e -> handleFirestoreError(e, OperationType.WRITE, docRef.path) }
    }

    fun observeUserPollVotes(): Flow<Map<String, String>> =
        observeAuthUid().flatMapLatest { uid ->
            val firestore = db
            if (firestore == null || uid == null) {
                flowOf(emptyMap())
            } else {
                callbackFlow {
                    val registration = firestore.collection("poll_votes")
                        .whereEqualTo("userId", uid)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                if (currentUid() == uid) {
                                    handleFirestoreError(error, OperationType.LIST, "poll_votes")
                                }
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val votesByPoll = mutableMapOf<String, String>()
                                for (doc in snapshot.documents) {
                                    val pollId = doc.getString("pollId")?.takeIf { it.isNotBlank() } ?: continue
                                    val candidateId = doc.getString("candidateId")?.takeIf { it.isNotBlank() } ?: continue
                                    votesByPoll[pollId] = candidateId
                                }
                                trySend(votesByPoll)
                            }
                        }
                    awaitClose { registration.remove() }
                }
            }
        }

    // 4. Performance & Uploaded Music Persistence
    fun savePerformance(performance: Performance) {
        val firestore = db ?: return
        val uid = currentUid() ?: return
        val sanitizedPerfId = VotesLeaderboardRepository.sanitizeId(performance.id)
        val docRef = firestore.collection("performances").document(sanitizedPerfId)
        val data = mapOf<String, Any?>(
            "id" to sanitizedPerfId,
            "ownerUid" to uid,
            "title" to performance.title.ifBlank { "Untitled Track" }.take(160),
            "artistId" to VotesLeaderboardRepository.sanitizeId(performance.artistId),
            "artistName" to performance.artistName.take(120),
            "avatarInitials" to performance.avatarInitials.take(8),
            "category" to performance.category.name,
            "genre" to performance.genre.take(80),
            "durationSec" to performance.durationSec.coerceIn(1, 7200),
            "lyricsOrScript" to performance.lyricsOrScript.take(10000),
            "visualizerTheme" to performance.visualizerTheme.name,
            "audioType" to performance.audioType.name,
            "customAudioPath" to com.example.security.UploadEncryptionManager.encryptField(
                appContext,
                performance.customAudioPath?.takeIf { it.isNotBlank() }
            )?.takeIf { it.length <= 500 },
            "customVideoPath" to com.example.security.UploadEncryptionManager.encryptField(
                appContext,
                performance.customVideoPath?.takeIf { it.isNotBlank() }
            )?.takeIf { it.length <= 500 },
            "hasVideo" to performance.hasVideo,
            "viewsCount" to performance.viewsCount.toLong().coerceAtLeast(0L),
            "likesCount" to performance.likesCount.toLong().coerceAtLeast(0L),
            "votesCount" to performance.votesCount.toLong().coerceAtLeast(0L),
            "cheersCount" to performance.cheersCount.toLong().coerceAtLeast(0L),
            "pollId" to performance.pollId?.take(128),
            "createdAtTimestamp" to performance.createdAtTimestamp,
            "visibility" to "PUBLIC",
            "updatedAt" to FieldValue.serverTimestamp()
        ).filterValues { it != null }

        docRef.set(data, SetOptions.merge())
            .addOnSuccessListener { Log.d("FirestoreDataSource", "Performance saved to Firestore: $sanitizedPerfId") }
            .addOnFailureListener { e -> handleFirestoreError(e, OperationType.WRITE, docRef.path) }
    }

    fun observePerformances(): Flow<List<Performance>> =
        observeAuthUid().flatMapLatest { uid ->
            val firestore = db
            if (firestore == null || uid == null) {
                flowOf(emptyList())
            } else {
                callbackFlow {
                    val registration = firestore.collection("performances")
                        .whereEqualTo("visibility", "PUBLIC")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                handleFirestoreError(error, OperationType.LIST, "performances")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val list = snapshot.documents.mapNotNull { doc -> parsePerformanceDoc(doc) }
                                trySend(list)
                            }
                        }
                    awaitClose { registration.remove() }
                }
            }
        }

    fun incrementPerformanceVotes(performanceId: String, delta: Long = 1L) {
        val firestore = db ?: return
        if (currentUid() == null) return
        val sanitizedPerfId = VotesLeaderboardRepository.sanitizeId(performanceId)
        val docRef = firestore.collection("performances").document(sanitizedPerfId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            if (snapshot.exists()) {
                val currentVotes = snapshot.getLong("votesCount") ?: 0L
                val newVotes = (currentVotes + delta).coerceAtLeast(0L)
                transaction.update(
                    docRef,
                    mapOf(
                        "votesCount" to newVotes,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }.addOnSuccessListener {
            Log.d("FirestoreDataSource", "Updated real-time votes for performance: $sanitizedPerfId (+$delta)")
        }.addOnFailureListener { e ->
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
        }
    }

    // 5. Real-Time Session Voting for Active Performers
    fun recordSessionVote(
        sessionId: String,
        performerId: String,
        performerName: String,
        userId: String,
        voteDelta: Int = 1,
        voteType: String = "VOTE"
    ) {
        val firestore = db ?: return
        val uid = currentUid() ?: return
        val sanitizedSession = VotesLeaderboardRepository.sanitizeId(sessionId)
        val sanitizedPerformer = VotesLeaderboardRepository.sanitizeId(performerId)
        val voteDocId = VotesLeaderboardRepository.sanitizeId("sv_${sanitizedSession}_${sanitizedPerformer}_${uid}")
        val docRef = firestore.collection("session_votes").document(voteDocId)

        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(docRef)
            val currentVotes = if (snapshot.exists()) (snapshot.getLong("voteDelta") ?: 0L).toInt() else 0
            val newTotal = (currentVotes + voteDelta).coerceIn(1, 1000)
            val data = mapOf(
                "voteId" to voteDocId,
                "sessionId" to sanitizedSession,
                "performerId" to sanitizedPerformer,
                "performerName" to performerName.ifBlank { "Performer" }.take(120),
                "userId" to uid,
                "voteDelta" to newTotal,
                "voteType" to voteType.take(32),
                "status" to "COUNTED",
                "updatedAt" to FieldValue.serverTimestamp()
            )
            transaction.set(docRef, data)
        }.addOnSuccessListener {
            Log.d("FirestoreDataSource", "Session vote recorded: $sanitizedSession for $performerName (+$voteDelta)")
            incrementPerformanceVotes(sanitizedPerformer, voteDelta.toLong())
        }.addOnFailureListener { e ->
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
        }
    }

    fun observeSessionVotes(sessionId: String): Flow<Map<String, Int>> =
        observeAuthUid().flatMapLatest { uid ->
            val firestore = db
            if (firestore == null || uid == null) {
                flowOf(emptyMap())
            } else {
                callbackFlow {
                    val sanitizedSession = VotesLeaderboardRepository.sanitizeId(sessionId)
                    val registration = firestore.collection("session_votes")
                        .whereEqualTo("sessionId", sanitizedSession)
                        .whereEqualTo("status", "COUNTED")
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                handleFirestoreError(error, OperationType.LIST, "session_votes")
                                return@addSnapshotListener
                            }
                            if (snapshot != null) {
                                val tallies = mutableMapOf<String, Int>()
                                for (doc in snapshot.documents) {
                                    try {
                                        val performerId = doc.getString("performerId") ?: continue
                                        val delta = (doc.getLong("voteDelta") ?: 0L).toInt()
                                        tallies[performerId] = (tallies[performerId] ?: 0) + delta
                                    } catch (e: Exception) {
                                        Log.w("FirestoreDataSource", "Error parsing session vote doc", e)
                                    }
                                }
                                trySend(tallies)
                            }
                        }
                    awaitClose { registration.remove() }
                }
            }
        }

    private fun parsePerformanceDoc(doc: com.google.firebase.firestore.DocumentSnapshot): Performance? {
        return try {
            val id = doc.getString("id") ?: doc.id
            val title = doc.getString("title") ?: "Untitled Track"
            val artistId = doc.getString("artistId") ?: ""
            val artistName = doc.getString("artistName") ?: "Artist"
            val avatarInitials = doc.getString("avatarInitials") ?: "ME"
            val cat = try {
                PerformanceCategory.valueOf(doc.getString("category") ?: "MUSIC")
            } catch (_: Exception) {
                PerformanceCategory.MUSIC
            }
            val genre = doc.getString("genre") ?: "Original"
            val durationSec = (doc.getLong("durationSec") ?: 180).toInt()
            val lyrics = doc.getString("lyricsOrScript") ?: ""
            val viz = try {
                VisualizerTheme.valueOf(doc.getString("visualizerTheme") ?: "NEON_WAVES")
            } catch (_: Exception) {
                VisualizerTheme.NEON_WAVES
            }
            val audioType = try {
                SynthAudioType.valueOf(doc.getString("audioType") ?: "UPLOADED_MUSIC")
            } catch (_: Exception) {
                SynthAudioType.UPLOADED_MUSIC
            }
            val customAudioPath = com.example.security.UploadEncryptionManager.decryptField(
                appContext,
                doc.getString("customAudioPath")?.takeIf { it.isNotBlank() }
            )
            val customVideoPath = com.example.security.UploadEncryptionManager.decryptField(
                appContext,
                doc.getString("customVideoPath")?.takeIf { it.isNotBlank() }
            )
            val views = (doc.getLong("viewsCount") ?: 0).toInt()
            val likes = (doc.getLong("likesCount") ?: 0).toInt()
            val votes = (doc.getLong("votesCount") ?: 0).toInt()
            val cheers = (doc.getLong("cheersCount") ?: 0).toInt()
            val pollId = doc.getString("pollId")?.takeIf { it.isNotBlank() }
            val createdAt = doc.getLong("createdAtTimestamp") ?: System.currentTimeMillis()

            Performance(
                id = id,
                title = title,
                artistId = artistId,
                artistName = artistName,
                avatarInitials = avatarInitials,
                category = cat,
                genre = genre,
                durationSec = durationSec,
                lyricsOrScript = lyrics,
                visualizerTheme = viz,
                audioType = audioType,
                customAudioPath = customAudioPath,
                customVideoPath = customVideoPath,
                viewsCount = views,
                likesCount = likes,
                votesCount = votes,
                cheersCount = cheers,
                pollId = pollId,
                isUserCreated = true,
                createdAtTimestamp = createdAt
            )
        } catch (e: Exception) {
            Log.w("FirestoreDataSource", "Error parsing performance doc", e)
            null
        }
    }

    private fun parseLiveSessionDoc(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        defaultLive: Boolean
    ): LiveSession? {
        return try {
            val id = doc.getString("id") ?: doc.id
            val creatorId = doc.getString("creatorId") ?: ""
            val creatorName = doc.getString("creatorName") ?: "Creator"
            val title = doc.getString("title") ?: "Live Stage"
            val cat = try {
                PerformanceCategory.valueOf(doc.getString("category") ?: "MUSIC")
            } catch (_: Exception) {
                PerformanceCategory.MUSIC
            }
            val genre = doc.getString("genre") ?: "Live"
            val viz = try {
                VisualizerTheme.valueOf(doc.getString("visualizerTheme") ?: "STAGE_LIGHTS")
            } catch (_: Exception) {
                VisualizerTheme.STAGE_LIGHTS
            }
            val audio = try {
                SynthAudioType.valueOf(doc.getString("audioType") ?: "ACOUSTIC_SOUL")
            } catch (_: Exception) {
                SynthAudioType.ACOUSTIC_SOUL
            }
            val pollId = doc.getString("activePollId")?.takeIf { it.isNotBlank() }
            val spectators = (doc.getLong("spectatorCount") ?: 1).toInt()
            val isLive = doc.getBoolean("isLive") ?: defaultLive
            val startedAt = doc.getLong("startedAt") ?: System.currentTimeMillis()

            val ownerUid = doc.getString("ownerUid")?.takeIf { it.isNotBlank() } ?: creatorId
            val currentUserUid = currentUid()
            val slowModeSeconds = (doc.getLong("slowModeSeconds") ?: 0L).toInt().coerceIn(0, 120)
            val isChatLocked = doc.getBoolean("isChatLocked") ?: false
            val pinnedAnnouncement = doc.getString("pinnedAnnouncement")?.takeIf { it.isNotBlank() }
            val reactionCounts = buildMap {
                StageReactionType.entries.forEach { reactionType ->
                    val count = (doc.getLong(reactionType.firestoreField) ?: 0L).toInt().coerceAtLeast(0)
                    if (count > 0) {
                        put(reactionType, count)
                    }
                }
            }

            LiveSession(
                id = id,
                creatorId = creatorId,
                creatorName = creatorName,
                title = title,
                category = cat,
                genre = genre,
                visualizerTheme = viz,
                audioType = audio,
                activePollId = pollId,
                spectatorCount = spectators,
                isLive = isLive,
                isCreatorMe = currentUserUid != null && (ownerUid == currentUserUid || creatorId == currentUserUid),
                startedAt = startedAt,
                ownerUid = ownerUid,
                slowModeSeconds = slowModeSeconds,
                isChatLocked = isChatLocked,
                pinnedAnnouncement = pinnedAnnouncement,
                reactionCounts = reactionCounts
            )
        } catch (e: Exception) {
            Log.w("FirestoreDataSource", "Error parsing live session doc", e)
            null
        }
    }
}
