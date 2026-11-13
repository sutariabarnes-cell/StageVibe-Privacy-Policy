package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Lifecycle state of a live performance stage in `/stages/{stageId}`.
 */
enum class StageStatus {
    WAITING,
    LIVE,
    ENDED;

    companion object {
        fun fromFirestoreValue(raw: String?): StageStatus {
            if (raw.isNullOrBlank()) return LIVE
            return entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: LIVE
        }
    }
}

/**
 * Firestore document model for `/stages/{stageId}`.
 *
 * Manages a live performance stage hosted by a creator, tracking the currently spotlighted
 * performer/track, active spectator presence, and aggregated spectator voting counters.
 */
@IgnoreExtraProperties
data class PerformanceStage(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val title: String = "",
    val category: String = PerformanceCategory.MUSIC.name,
    val genre: String = "Live Stage",
    val description: String = "",
    val status: String = StageStatus.LIVE.name,
    val activePerformerId: String? = null,
    val activePerformerName: String? = null,
    val activePerformanceId: String? = null,
    val activePerformanceTitle: String? = null,
    val visualizerTheme: String = VisualizerTheme.STAGE_LIGHTS.name,
    val audioType: String = SynthAudioType.ACOUSTIC_SOUL.name,
    val activePollId: String? = null,
    val sessionId: String? = null,
    val spectatorCount: Long = 1L,
    val totalStageVotes: Long = 0L,
    val totalBallotsCast: Long = 0L,
    val totalCheersCount: Long = 0L,
    val visibility: String = VISIBILITY_PUBLIC,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val stageStatus: StageStatus
        get() = StageStatus.fromFirestoreValue(status)

    @get:Exclude
    val isLive: Boolean
        get() = stageStatus == StageStatus.LIVE

    @get:Exclude
    val performanceCategory: PerformanceCategory
        get() = try {
            PerformanceCategory.valueOf(category)
        } catch (_: Exception) {
            PerformanceCategory.MUSIC
        }

    @get:Exclude
    val resolvedVisualizerTheme: VisualizerTheme
        get() = try {
            VisualizerTheme.valueOf(visualizerTheme)
        } catch (_: Exception) {
            VisualizerTheme.STAGE_LIGHTS
        }

    @get:Exclude
    val resolvedAudioType: SynthAudioType
        get() = try {
            SynthAudioType.valueOf(audioType)
        } catch (_: Exception) {
            SynthAudioType.ACOUSTIC_SOUL
        }

    fun toLiveSession(currentUserId: String? = null): LiveSession {
        val resolvedSessionId = sessionId?.takeIf { it.isNotBlank() } ?: id
        val resolvedCreatorId = hostId.ifBlank { ownerUid }
        return LiveSession(
            id = resolvedSessionId,
            creatorId = resolvedCreatorId,
            creatorName = hostName.ifBlank { "Stage Host" },
            title = title.ifBlank { "Live Stage" },
            category = performanceCategory,
            genre = genre.ifBlank { performanceCategory.displayName },
            visualizerTheme = resolvedVisualizerTheme,
            audioType = resolvedAudioType,
            activePollId = activePollId,
            spectatorCount = spectatorCount.coerceAtLeast(0L).toInt(),
            isLive = isLive,
            isCreatorMe = currentUserId != null && (ownerUid == currentUserId || resolvedCreatorId == currentUserId),
            startedAt = createdAt?.toDate()?.time ?: System.currentTimeMillis()
        )
    }

    companion object {
        const val VISIBILITY_PUBLIC = "PUBLIC"
        const val VISIBILITY_PRIVATE = "PRIVATE"
    }
}

/**
 * Firestore document model for `/stages/{stageId}/voting_counters/{performerId}`.
 *
 * Stores real-time spectator voting counters per performer on a live performance stage,
 * including multi-tier ballot breakdowns (+1 Standard, +5 Super, +10 Standing Ovation),
 * distinct voter counts, and spectator cheers.
 */
@IgnoreExtraProperties
data class StageVotingCounter(
    @DocumentId val id: String = "",
    val stageId: String = "",
    val performerId: String = "",
    val performerName: String = "",
    val category: String = PerformanceCategory.MUSIC.name,
    val performanceId: String? = null,
    val performanceTitle: String? = null,
    val genre: String = "Live Stage",
    val avatarInitials: String = "SP",
    val avatarColorHex: Long = 0xFF9D4EDD,
    val isActiveOnStage: Boolean = true,
    val totalVotes: Long = 0L,
    val standardVotesCount: Long = 0L,
    val superVotesCount: Long = 0L,
    val ovationVotesCount: Long = 0L,
    val ballotsCast: Long = 0L,
    val uniqueVoterCount: Long = 0L,
    val cheersCount: Long = 0L,
    val visibility: String = PerformanceStage.VISIBILITY_PUBLIC,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val performanceCategory: PerformanceCategory
        get() = try {
            PerformanceCategory.valueOf(category)
        } catch (_: Exception) {
            PerformanceCategory.MUSIC
        }

    fun voteShareFraction(stageTotalVotes: Long): Float {
        if (stageTotalVotes <= 0L || totalVotes <= 0L) return 0f
        return (totalVotes.toFloat() / stageTotalVotes.toFloat()).coerceIn(0f, 1f)
    }
}

/**
 * Firestore document model for `/stages/{stageId}/spectator_votes/{voteId}`.
 *
 * Represents an authenticated spectator vote receipt cast on a live performance stage.
 */
@IgnoreExtraProperties
data class StageSpectatorVote(
    @DocumentId val id: String = "",
    val stageId: String = "",
    val performerId: String = "",
    val performerName: String = "",
    val category: String = PerformanceCategory.MUSIC.name,
    val performanceId: String? = null,
    val performanceTitle: String? = null,
    val voterUid: String = "",
    val voterName: String = "",
    val voteWeight: Long = 1L,
    val voteTier: String = VoteTier.STANDARD.firestoreKey,
    val status: String = STATUS_COUNTED,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    @get:Exclude
    val resolvedTier: VoteTier
        get() = VoteTier.fromKeyOrDelta(voteTier, voteWeight.toInt())

    @get:Exclude
    val isCounted: Boolean
        get() = status.equals(STATUS_COUNTED, ignoreCase = true)

    companion object {
        const val STATUS_COUNTED = "COUNTED"
        const val STATUS_REVOKED = "REVOKED"
    }
}

/**
 * Combined real-time stage and spectator voting counter snapshot for UI presentation.
 */
data class StageVotingSnapshot(
    val stageId: String = "",
    val stage: PerformanceStage? = null,
    val counters: List<StageVotingCounter> = emptyList(),
    val leadingPerformerId: String? = null,
    val totalStageVotes: Int = 0,
    val totalBallotsCast: Int = 0,
    val totalCheersCount: Int = 0,
    val spectatorCount: Int = 0,
    val isCloudSynced: Boolean = false
) {
    fun counterFor(performerOrPerformanceId: String): StageVotingCounter? {
        if (performerOrPerformanceId.isBlank()) return null
        return counters.firstOrNull {
            it.performerId == performerOrPerformanceId ||
                it.performanceId == performerOrPerformanceId ||
                it.id == performerOrPerformanceId
        }
    }
}
