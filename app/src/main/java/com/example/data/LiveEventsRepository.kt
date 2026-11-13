package com.example.data

import android.content.Context
import com.example.R
import com.example.model.LiveEvent
import com.example.model.LiveSession
import com.example.model.PerformanceCategory
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Repository for fetching, creating, and updating live Music and Spoken Word events
 * in Cloud Firestore (`/events/{eventId}` and `/live_sessions/{sessionId}`), using the
 * named database configured in `R.string.firestore_database_id`.
 */
class LiveEventsRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {

    constructor(context: Context) : this(
        db = createNamedFirestore(context),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun currentUserDisplayName(): String {
        return auth.currentUser?.displayName?.takeIf { it.isNotBlank() }?.take(120) ?: "Stage Host"
    }

    /**
     * Real-time stream of active live Music and Spoken Word events from `/events`
     * (and optionally active `/live_sessions` mapped into [LiveEvent] items when not already mirrored).
     */
    fun observeLiveEvents(
        categoryFilter: PerformanceCategory? = null,
        includeLiveSessions: Boolean = false
    ): Flow<List<LiveEvent>> {
        val activeUid = auth.currentUser?.uid
        val eventsFlow: Flow<List<LiveEvent>> = db.collection(COLLECTION_EVENTS)
            .whereEqualTo("isLive", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(LiveEvent::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                logStreamErrorIfAuthenticated(error, COLLECTION_EVENTS, activeUid)
                throw error
            }

        val combinedFlow = if (includeLiveSessions) {
            val sessionsFlow: Flow<List<LiveEvent>> = db.collection(COLLECTION_LIVE_SESSIONS)
                .whereEqualTo("isLive", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> parseSessionDocumentAsEvent(doc) }
                }
                .catch { error ->
                    logStreamErrorIfAuthenticated(error, COLLECTION_LIVE_SESSIONS, activeUid)
                    emit(emptyList())
                }

            combine(eventsFlow, sessionsFlow) { events, sessionEvents ->
                val coveredIds = events.flatMap { listOfNotNull(it.id, it.sessionId) }.toSet()
                val additional = sessionEvents.filter { it.id !in coveredIds }
                events + additional
            }
        } else {
            eventsFlow
        }

        return combinedFlow.map { allLiveEvents ->
            allLiveEvents
                .filter { event ->
                    event.isLive &&
                        (event.visibility == VISIBILITY_PUBLIC || event.ownerUid == auth.currentUser?.uid) &&
                        (categoryFilter == null || event.performanceCategory == categoryFilter)
                }
                .sortedWith(
                    compareByDescending<LiveEvent> { it.participantCount }
                        .thenByDescending { it.updatedAt?.seconds ?: it.createdAt?.seconds ?: 0L }
                )
        }
    }

    /**
     * Creates a new live Music or Spoken Word event in `/events/{eventId}`.
     * Never writes `"id"` inside the document body so `@DocumentId` maps cleanly.
     */
    suspend fun createLiveEvent(
        title: String,
        category: PerformanceCategory,
        genre: String = category.displayName,
        description: String = "",
        hostName: String? = null,
        hostId: String? = null,
        initialParticipants: Int = 1,
        eventId: String? = null,
        sessionId: String? = null
    ): Result<LiveEvent> {
        val rawId = eventId?.takeIf { it.isNotBlank() } ?: "evt_${UUID.randomUUID().toString().take(10)}"
        val sanitizedEventId = VotesLeaderboardRepository.sanitizeId(rawId)
        val docRef = db.collection(COLLECTION_EVENTS).document(sanitizedEventId)

        return try {
            val uid = requireUserId()
            val resolvedHostId = VotesLeaderboardRepository.sanitizeId(hostId?.takeIf { it.isNotBlank() } ?: uid)
            val resolvedHostName = (hostName?.takeIf { it.isNotBlank() } ?: currentUserDisplayName()).take(120)
            val resolvedTitle = title.trim().ifBlank { "Live ${category.displayName} Showcase" }.take(160)
            val resolvedGenre = genre.trim().ifBlank { category.displayName }.take(80)
            val resolvedDescription = description.trim().take(500)
            val resolvedParticipants = initialParticipants.coerceIn(0, 1_000_000).toLong()
            val sanitizedSessionId = sessionId?.takeIf { it.isNotBlank() }?.let {
                VotesLeaderboardRepository.sanitizeId(it)
            }

            val payload = mapOf<String, Any?>(
                "ownerUid" to uid,
                "hostId" to resolvedHostId,
                "hostName" to resolvedHostName,
                "title" to resolvedTitle,
                "category" to category.name,
                "genre" to resolvedGenre,
                "description" to resolvedDescription.takeIf { it.isNotBlank() },
                "participantCount" to resolvedParticipants,
                "isLive" to true,
                "visibility" to VISIBILITY_PUBLIC,
                "sessionId" to sanitizedSessionId,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()

            Result.success(
                LiveEvent(
                    id = sanitizedEventId,
                    ownerUid = uid,
                    hostId = resolvedHostId,
                    hostName = resolvedHostName,
                    title = resolvedTitle,
                    category = category.name,
                    genre = resolvedGenre,
                    description = resolvedDescription,
                    participantCount = resolvedParticipants,
                    isLive = true,
                    visibility = VISIBILITY_PUBLIC,
                    sessionId = sanitizedSessionId
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Mirrors a [LiveSession] into `/events/{sessionId}` so live stage broadcasts
     * appear in `/events` queries with real-time participant counts.
     */
    suspend fun syncLiveSessionToEvent(session: LiveSession): Result<Unit> {
        val sanitizedId = VotesLeaderboardRepository.sanitizeId(session.id)
        val docRef = db.collection(COLLECTION_EVENTS).document(sanitizedId)
        return try {
            val uid = requireUserId()
            val hostId = VotesLeaderboardRepository.sanitizeId(session.creatorId.ifBlank { uid })
            val hostName = session.creatorName.ifBlank { currentUserDisplayName() }.take(120)
            val cleanTitle = session.title.ifBlank { "Live Stage" }.take(160)
            val participants = session.spectatorCount.coerceIn(0, 1_000_000).toLong()

            db.runTransaction { transaction ->
                val snap = transaction.get(docRef)
                if (snap.exists()) {
                    if (snap.getBoolean("isLive") == true && snap.getString("ownerUid") == uid) {
                        transaction.update(
                            docRef,
                            mapOf(
                                "title" to cleanTitle,
                                "genre" to session.genre.take(80),
                                "participantCount" to participants,
                                "isLive" to session.isLive,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                } else if (session.isLive) {
                    transaction.set(
                        docRef,
                        mapOf(
                            "ownerUid" to uid,
                            "hostId" to hostId,
                            "hostName" to hostName,
                            "title" to cleanTitle,
                            "category" to session.category.name,
                            "genre" to session.genre.take(80),
                            "description" to "Live ${session.category.displayName.lowercase()} broadcast on stage",
                            "participantCount" to participants,
                            "isLive" to true,
                            "visibility" to VISIBILITY_PUBLIC,
                            "sessionId" to sanitizedId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    /**
     * Atomically increments `participantCount` by 1 when a spectator or performer joins a live event.
     */
    suspend fun joinLiveEvent(eventId: String): Result<Long> {
        val sanitizedId = VotesLeaderboardRepository.sanitizeId(eventId)
        val eventRef = db.collection(COLLECTION_EVENTS).document(sanitizedId)
        return try {
            requireUserId()
            val updatedCount = db.runTransaction { transaction ->
                val eventSnap = transaction.get(eventRef)
                if (eventSnap.exists()) {
                    val current = (eventSnap.getLong("participantCount") ?: 0L).coerceAtLeast(0L)
                    val next = (current + 1L).coerceAtMost(1_000_000L)
                    transaction.update(
                        eventRef,
                        mapOf(
                            "participantCount" to next,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    next
                } else {
                    val sessionRef = db.collection(COLLECTION_LIVE_SESSIONS).document(sanitizedId)
                    val sessionSnap = transaction.get(sessionRef)
                    if (sessionSnap.exists()) {
                        val current = (sessionSnap.getLong("spectatorCount") ?: 0L).coerceAtLeast(0L)
                        val next = current + 1L
                        transaction.update(
                            sessionRef,
                            mapOf(
                                "spectatorCount" to next,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        next
                    } else {
                        throw IllegalArgumentException("Live event not found: $sanitizedId")
                    }
                }
            }.await()
            Result.success(updatedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, eventRef.path)
            Result.failure(e)
        }
    }

    /**
     * Atomically decrements `participantCount` by 1 (bounded at 0) when a participant leaves a live event.
     */
    suspend fun leaveLiveEvent(eventId: String): Result<Long> {
        val sanitizedId = VotesLeaderboardRepository.sanitizeId(eventId)
        val eventRef = db.collection(COLLECTION_EVENTS).document(sanitizedId)
        return try {
            requireUserId()
            val updatedCount = db.runTransaction { transaction ->
                val snap = transaction.get(eventRef)
                if (!snap.exists()) return@runTransaction 0L
                val current = (snap.getLong("participantCount") ?: 0L).coerceAtLeast(0L)
                val next = (current - 1L).coerceAtLeast(0L)
                transaction.update(
                    eventRef,
                    mapOf(
                        "participantCount" to next,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                next
            }.await()
            Result.success(updatedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, eventRef.path)
            Result.failure(e)
        }
    }

    /**
     * Marks a live event as ended (`isLive = false`). Restricted to the event host (`ownerUid`).
     */
    suspend fun endLiveEvent(eventId: String): Result<Unit> {
        val sanitizedId = VotesLeaderboardRepository.sanitizeId(eventId)
        val eventRef = db.collection(COLLECTION_EVENTS).document(sanitizedId)
        return try {
            requireUserId()
            eventRef.update(
                mapOf(
                    "isLive" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, eventRef.path)
            Result.failure(e)
        }
    }

    private fun parseSessionDocumentAsEvent(doc: DocumentSnapshot): LiveEvent? {
        return try {
            val sessionId = doc.getString("id") ?: doc.id
            val ownerUid = doc.getString("ownerUid") ?: ""
            val creatorId = doc.getString("creatorId") ?: ownerUid
            val creatorName = doc.getString("creatorName") ?: "Stage Host"
            val title = doc.getString("title") ?: "Live Stage"
            val category = doc.getString("category") ?: PerformanceCategory.MUSIC.name
            val genre = doc.getString("genre") ?: "Live Stage"
            val participants = (doc.getLong("spectatorCount") ?: 0L).coerceAtLeast(0L)
            val isLive = doc.getBoolean("isLive") ?: false
            val visibility = doc.getString("visibility") ?: VISIBILITY_PUBLIC
            val updatedAt = doc.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)

            LiveEvent(
                id = sessionId,
                ownerUid = ownerUid,
                hostId = creatorId,
                hostName = creatorName,
                title = title,
                category = category,
                genre = genre,
                description = "Live stage broadcast",
                participantCount = participants,
                isLive = isLive,
                visibility = visibility,
                sessionId = sessionId,
                createdAt = updatedAt,
                updatedAt = updatedAt
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun logStreamErrorIfAuthenticated(error: Throwable, path: String, listenerUid: String?) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && listenerUid != null && auth.currentUser?.uid == listenerUid) {
            handleFirestoreError(exceptionToLog, OperationType.LIST, path)
        }
    }

    companion object {
        const val COLLECTION_EVENTS = "events"
        const val COLLECTION_LIVE_SESSIONS = "live_sessions"
        const val VISIBILITY_PUBLIC = "PUBLIC"

        private fun createNamedFirestore(context: Context): FirebaseFirestore {
            val appContext = context.applicationContext
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
            }
            val databaseId = appContext.getString(R.string.firestore_database_id)
            return FirebaseFirestore.getInstance(FirebaseApp.getInstance(), databaseId)
        }
    }
}
