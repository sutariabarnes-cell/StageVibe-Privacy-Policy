package com.example.data

import android.content.Context
import com.example.model.LeaderboardTimePeriod
import com.example.model.PerformanceCategory
import com.example.model.PerformerVoteSummary
import com.example.model.VoteRecord
import com.example.model.VotesAggregationSnapshot
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow

/**
 * Real-time leaderboard repository backed by [FirestoreService] for casting votes into
 * `/votes/{voteId}` and aggregating performer rankings in real time.
 */
class VotesLeaderboardRepository(
    firestore: FirebaseFirestore,
    auth: FirebaseAuth = Firebase.auth
) {

    private val service = FirestoreService(firestore, auth)

    constructor(context: Context) : this(
        firestore = FirestoreService.createFirestore(context),
        auth = Firebase.auth
    )

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
    ): Result<String> = service.castVote(
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
        avatarColorHex = avatarColorHex,
        voteId = voteId
    )

    suspend fun revokeVote(voteId: String): Result<Unit> = service.revokeVote(voteId)

    suspend fun getVoteById(voteId: String): Result<VoteRecord?> = service.getVoteById(voteId)

    fun observeCountedVotes(): Flow<List<VoteRecord>> = service.observeVotes()

    fun observeLeaderboardAggregation(
        timePeriod: LeaderboardTimePeriod = LeaderboardTimePeriod.ALL_TIME
    ): Flow<VotesAggregationSnapshot> = service.observeLeaderboardAggregation(timePeriod)

    companion object {
        const val COLLECTION_VOTES = FirestoreService.COLLECTION_VOTES
        const val STATUS_COUNTED = FirestoreService.STATUS_COUNTED
        const val STATUS_REVOKED = FirestoreService.STATUS_REVOKED

        private const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
        private const val THIRTY_DAYS_MS = 30L * 24L * 60L * 60L * 1000L

        fun sanitizeId(raw: String): String {
            val cleaned = raw.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(128)
            return cleaned.ifBlank { "unknown_id" }
        }

        fun aggregateVotes(
            votes: List<VoteRecord>,
            timePeriod: LeaderboardTimePeriod = LeaderboardTimePeriod.ALL_TIME,
            currentUserId: String? = null,
            nowMillis: Long = System.currentTimeMillis()
        ): VotesAggregationSnapshot {
            val countedOnly = votes.filter { it.status == STATUS_COUNTED && it.voteDelta > 0 }
            val cutoffMillis = when (timePeriod) {
                LeaderboardTimePeriod.WEEKLY -> nowMillis - SEVEN_DAYS_MS
                LeaderboardTimePeriod.MONTHLY -> nowMillis - THIRTY_DAYS_MS
                LeaderboardTimePeriod.ALL_TIME -> 0L
            }

            val windowedVotes = countedOnly.filter { it.createdAtMillis >= cutoffMillis }

            val summaries = windowedVotes
                .groupBy { it.performerId }
                .map { (performerId, performerVotes) ->
                    val latest = performerVotes.maxByOrNull { it.createdAtMillis } ?: performerVotes.first()
                    val totalDelta = performerVotes.sumOf { it.voteDelta }
                    val uniqueVoters = performerVotes.map { it.userId }.distinct().size
                    val votedByMe = currentUserId != null && performerVotes.any { it.userId == currentUserId }

                    PerformerVoteSummary(
                        performerId = performerId,
                        performerName = latest.performerName,
                        category = latest.category,
                        genre = latest.genre,
                        stageTitle = latest.stageTitle,
                        topPerformanceId = latest.performanceId ?: "perf_$performerId",
                        topPerformanceTitle = latest.topPerformanceTitle ?: "${latest.performerName} Live Showcase",
                        avatarColorHex = latest.avatarColorHex,
                        aggregatedVotes = totalDelta,
                        uniqueVoterCount = uniqueVoters,
                        voteDocumentsCount = performerVotes.size,
                        votedByCurrentUser = votedByMe,
                        latestVoteAtMillis = latest.createdAtMillis
                    )
                }
                .sortedWith(
                    compareByDescending<PerformerVoteSummary> { it.aggregatedVotes }
                        .thenByDescending { it.uniqueVoterCount }
                        .thenByDescending { it.latestVoteAtMillis }
                        .thenBy { it.performerId }
                )

            return VotesAggregationSnapshot(
                totalCountedVotes = summaries.sumOf { it.aggregatedVotes },
                totalVoteRecords = windowedVotes.size,
                uniqueVotersCount = windowedVotes.map { it.userId }.distinct().size,
                performerSummaries = summaries,
                lastSyncedAtMillis = nowMillis,
                isLiveCloudSynced = true
            )
        }
    }
}
