package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Multi-tier audience vote weights supported during active performances and live stage sessions.
 */
enum class VoteTier(
    val weight: Int,
    val displayLabel: String,
    val firestoreKey: String
) {
    STANDARD(weight = 1, displayLabel = "+1 Vote", firestoreKey = "VOTE"),
    SUPER_VOTE(weight = 5, displayLabel = "+5 Super", firestoreKey = "SUPER_VOTE"),
    STANDING_OVATION(weight = 10, displayLabel = "+10 Ovation", firestoreKey = "STANDING_OVATION"),
    POLL_VOTE(weight = 1, displayLabel = "Poll Vote", firestoreKey = "POLL_VOTE");

    companion object {
        fun fromKeyOrDelta(key: String?, delta: Int = 1): VoteTier {
            if (!key.isNullOrBlank()) {
                entries.firstOrNull {
                    it.firestoreKey.equals(key, ignoreCase = true) ||
                        it.name.equals(key, ignoreCase = true)
                }?.let { return it }
            }
            return when {
                delta >= STANDING_OVATION.weight -> STANDING_OVATION
                delta >= SUPER_VOTE.weight -> SUPER_VOTE
                else -> STANDARD
            }
        }
    }
}

/**
 * Real-time audience vote data model compatible with Cloud Firestore serialization
 * and in-memory StateFlow vote tracking for active performances.
 */
@IgnoreExtraProperties
data class RealtimeVote(
    @DocumentId val id: String = "",
    val performanceId: String = "",
    val performerId: String = "",
    val performerName: String = "",
    val voterId: String = "",
    val voterName: String = "",
    val voteDelta: Int = 1,
    val voteTier: String = VoteTier.STANDARD.firestoreKey,
    val category: String = PerformanceCategory.MUSIC.name,
    val sessionId: String? = null,
    val status: String = STATUS_COUNTED,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val resolvedTier: VoteTier
        get() = VoteTier.fromKeyOrDelta(voteTier, voteDelta)

    val resolvedCategory: PerformanceCategory
        get() = try {
            PerformanceCategory.valueOf(category)
        } catch (_: Exception) {
            PerformanceCategory.MUSIC
        }

    val isCounted: Boolean
        get() = status.equals(STATUS_COUNTED, ignoreCase = true)

    companion object {
        const val STATUS_COUNTED = "COUNTED"
        const val STATUS_REVOKED = "REVOKED"

        fun fromVoteRecord(record: VoteRecord): RealtimeVote? {
            val resolvedPerfId = record.performanceId?.takeIf { it.isNotBlank() }
                ?: record.targetId.takeIf { record.targetType.equals("PERFORMANCE", ignoreCase = true) }
                ?: return null
            return RealtimeVote(
                id = record.id,
                performanceId = resolvedPerfId,
                performerId = record.performerId,
                performerName = record.performerName,
                voterId = record.userId,
                voterName = record.userName,
                voteDelta = record.voteDelta,
                voteTier = record.voteTier,
                category = record.category.name,
                sessionId = record.sessionId,
                status = record.status,
                timestampMillis = record.createdAtMillis
            )
        }
    }
}

/**
 * Aggregated real-time vote count state for a single active performance.
 */
data class PerformanceVoteCount(
    val performanceId: String = "",
    val performanceTitle: String = "",
    val performerId: String = "",
    val performerName: String = "",
    val category: PerformanceCategory = PerformanceCategory.MUSIC,
    val isActive: Boolean = true,
    val baselineVotes: Int = 0,
    val realtimeVotesDelta: Int = 0,
    val totalVotes: Int = (baselineVotes + realtimeVotesDelta).coerceAtLeast(0),
    val uniqueVoterIds: Set<String> = emptySet(),
    val isVotedByCurrentUser: Boolean = false,
    val currentUserVoteTotal: Int = 0,
    val voteSharePercent: Float = 0f,
    val isLeading: Boolean = false,
    val lastVoteTier: VoteTier? = null,
    val lastVotedAtMillis: Long = 0L
) {
    val uniqueVotersCount: Int
        get() = uniqueVoterIds.size
}

/**
 * Immutable UI & domain state snapshot managed via StateFlow to track real-time
 * vote counts across all currently active performances.
 */
data class ActiveVotingState(
    val activePerformances: Map<String, PerformanceVoteCount> = emptyMap(),
    val voteCounts: Map<String, Int> = emptyMap(),
    val totalActiveVotes: Int = 0,
    val activePerformanceCount: Int = 0,
    val leadingPerformanceId: String? = null,
    val recentVote: RealtimeVote? = null,
    val isCloudSynced: Boolean = false,
    val errorMessage: String? = null
) {
    fun getVoteCount(performanceId: String, fallback: Int = 0): Int =
        voteCounts[performanceId] ?: activePerformances[performanceId]?.totalVotes ?: fallback

    fun getPerformanceVoteState(performanceId: String): PerformanceVoteCount? =
        activePerformances[performanceId]

    fun getVoteSharePercent(performanceId: String): Float =
        activePerformances[performanceId]?.voteSharePercent ?: 0f

    fun isVotedByCurrentUser(performanceId: String, fallback: Boolean = false): Boolean =
        activePerformances[performanceId]?.isVotedByCurrentUser ?: fallback

    fun isLeading(performanceId: String): Boolean =
        leadingPerformanceId != null && leadingPerformanceId == performanceId

    fun rankedActivePerformances(): List<PerformanceVoteCount> =
        activePerformances.values
            .filter { it.isActive }
            .sortedWith(
                compareByDescending<PerformanceVoteCount> { it.totalVotes }
                    .thenByDescending { it.realtimeVotesDelta }
                    .thenBy { it.performanceId }
            )
}
