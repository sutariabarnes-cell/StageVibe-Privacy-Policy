package com.example.data

import com.example.model.ActiveVotingState
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.PerformanceVoteCount
import com.example.model.RealtimeVote
import com.example.model.VoteRecord
import com.example.model.VoteTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * StateFlow-based real-time voting state manager that tracks live vote counts,
 * audience vote shares, current-user vote state, and stage leaders for active performances.
 *
 * Thread-safe via synchronized state transitions that atomically emit both the rich
 * [votingState] ([ActiveVotingState]) and a lightweight [voteCounts] map (`performanceId -> count`).
 */
class ActivePerformanceVotingManager {

    private data class RegisteredPerformanceMeta(
        val performanceId: String,
        val title: String,
        val performerId: String,
        val performerName: String,
        val category: PerformanceCategory,
        val baselineVotes: Int,
        val initialVotedByMe: Boolean,
        val isActive: Boolean
    )

    private val lock = Any()

    private val registeredPerformances = LinkedHashMap<String, RegisteredPerformanceMeta>()
    private val optimisticVotesById = LinkedHashMap<String, RealtimeVote>()
    private val cloudVotesById = LinkedHashMap<String, RealtimeVote>()
    private val manualToggleAdjustments = HashMap<String, Int>()
    private val manualVotedByUser = HashMap<String, MutableSet<String>>()

    private var activeUserId: String? = null
    private var cloudStreamSynced: Boolean = false
    private var latestVoteEvent: RealtimeVote? = null
    private var latestError: String? = null

    private val _votingState = MutableStateFlow(ActiveVotingState())
    val votingState: StateFlow<ActiveVotingState> = _votingState.asStateFlow()

    private val _voteCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val voteCounts: StateFlow<Map<String, Int>> = _voteCounts.asStateFlow()

    /**
     * Registers or refreshes the set of active performances tracked by [votingState].
     * Preserves initial baseline counts and any accumulated real-time or cloud votes.
     */
    fun syncActivePerformances(
        performances: List<Performance>,
        currentUserId: String? = activeUserId
    ) {
        synchronized(lock) {
            if (currentUserId != null) {
                activeUserId = currentUserId
            }
            val incomingIds = performances.map { it.id }.toSet()
            performances.forEach { perf ->
                val existing = registeredPerformances[perf.id]
                registeredPerformances[perf.id] = if (existing != null) {
                    existing.copy(
                        title = perf.title,
                        performerId = perf.artistId,
                        performerName = perf.artistName,
                        category = perf.category
                    )
                } else {
                    RegisteredPerformanceMeta(
                        performanceId = perf.id,
                        title = perf.title,
                        performerId = perf.artistId,
                        performerName = perf.artistName,
                        category = perf.category,
                        baselineVotes = perf.votesCount.coerceAtLeast(0),
                        initialVotedByMe = perf.isVotedByMe,
                        isActive = true
                    )
                }
            }
            // Mark performances no longer in the incoming active list as inactive if removed
            val staleIds = registeredPerformances.keys.filter { it !in incomingIds }
            staleIds.forEach { id ->
                registeredPerformances[id]?.let { meta ->
                    registeredPerformances[id] = meta.copy(isActive = false)
                }
            }
            publishSnapshotLocked()
        }
    }

    /**
     * Updates the active user identity so `isVotedByCurrentUser` and `currentUserVoteTotal`
     * reflect the currently signed-in spectator or performer.
     */
    fun setCurrentUser(userId: String?) {
        synchronized(lock) {
            if (activeUserId != userId) {
                activeUserId = userId
                publishSnapshotLocked()
            }
        }
    }

    /**
     * Marks a specific performance as active or inactive on stage.
     */
    fun setPerformanceActive(performanceId: String, isActive: Boolean) {
        synchronized(lock) {
            val current = registeredPerformances[performanceId] ?: return
            registeredPerformances[performanceId] = current.copy(isActive = isActive)
            publishSnapshotLocked()
        }
    }

    /**
     * Casts a real-time vote ([VoteTier.STANDARD], [VoteTier.SUPER_VOTE], or [VoteTier.STANDING_OVATION])
     * for an active performance and immediately updates [votingState] and [voteCounts].
     *
     * Pass a deterministic [voteId] when also writing to Firestore `/votes/{voteId}` so that
     * when the Firestore snapshot arrives via [reconcileCloudVotes], the optimistic vote is
     * seamlessly promoted without double-counting.
     */
    fun castVote(
        performanceId: String,
        voterId: String,
        voterName: String = "Stage Voter",
        tier: VoteTier = VoteTier.STANDARD,
        sessionId: String? = null,
        voteId: String = "vote_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    ): RealtimeVote? {
        synchronized(lock) {
            val meta = registeredPerformances[performanceId] ?: return null
            if (!meta.isActive) return null

            if (activeUserId == null && voterId.isNotBlank()) {
                activeUserId = voterId
            }

            val vote = RealtimeVote(
                id = voteId,
                performanceId = performanceId,
                performerId = meta.performerId,
                performerName = meta.performerName,
                voterId = voterId,
                voterName = voterName,
                voteDelta = tier.weight,
                voteTier = tier.firestoreKey,
                category = meta.category.name,
                sessionId = sessionId,
                status = RealtimeVote.STATUS_COUNTED,
                timestampMillis = System.currentTimeMillis()
            )
            optimisticVotesById[vote.id] = vote
            latestVoteEvent = vote
            latestError = null
            publishSnapshotLocked()
            return vote
        }
    }

    /**
     * Toggles a single standard (+1 / -1) audience vote for [performanceId] by [voterId].
     * Returns `(isNowVoted, appliedDelta)` so callers can persist the exact delta.
     */
    fun toggleVote(
        performanceId: String,
        voterId: String,
        voterName: String = "Stage Voter",
        voteIdForCreate: String = "vote_${UUID.randomUUID().toString().replace("-", "").take(20)}"
    ): Pair<Boolean, Int> {
        synchronized(lock) {
            val meta = registeredPerformances[performanceId] ?: return false to 0
            if (activeUserId == null && voterId.isNotBlank()) {
                activeUserId = voterId
            }

            val currentEntry = _votingState.value.activePerformances[performanceId]
            val currentlyVoted = currentEntry?.isVotedByCurrentUser ?: meta.initialVotedByMe

            return if (!currentlyVoted) {
                val vote = RealtimeVote(
                    id = voteIdForCreate,
                    performanceId = performanceId,
                    performerId = meta.performerId,
                    performerName = meta.performerName,
                    voterId = voterId,
                    voterName = voterName,
                    voteDelta = 1,
                    voteTier = VoteTier.STANDARD.firestoreKey,
                    category = meta.category.name,
                    status = RealtimeVote.STATUS_COUNTED,
                    timestampMillis = System.currentTimeMillis()
                )
                optimisticVotesById[vote.id] = vote
                manualVotedByUser.getOrPut(performanceId) { mutableSetOf() }.add(voterId)
                latestVoteEvent = vote
                latestError = null
                publishSnapshotLocked()
                true to 1
            } else {
                // Remove one optimistic standard vote from this voter if present; otherwise apply -1 adjustment
                val matchingOptKey = optimisticVotesById.entries
                    .lastOrNull { (_, v) ->
                        v.performanceId == performanceId && v.voterId == voterId && v.voteDelta == 1
                    }?.key

                if (matchingOptKey != null) {
                    optimisticVotesById.remove(matchingOptKey)
                } else {
                    manualToggleAdjustments[performanceId] = (manualToggleAdjustments[performanceId] ?: 0) - 1
                }
                manualVotedByUser[performanceId]?.remove(voterId)
                // If voter also had cloud votes, mark manual unvote override so UI reflects unvoted state
                cloudVotesById.entries
                    .filter { (_, v) -> v.performanceId == performanceId && v.voterId == voterId }
                    .forEach { (k, _) -> cloudVotesById.remove(k) }

                publishSnapshotLocked()
                false to -1
            }
        }
    }

    /**
     * Reconciles live Firestore `'votes'` collection snapshots with active performance tallies.
     * Automatically deduplicates confirmed cloud votes against pending optimistic votes by ID.
     */
    fun reconcileCloudVotes(
        records: List<VoteRecord>,
        currentUserId: String? = activeUserId,
        isCloudSynced: Boolean = true
    ) {
        synchronized(lock) {
            if (currentUserId != null) {
                activeUserId = currentUserId
            }
            cloudStreamSynced = isCloudSynced
            cloudVotesById.clear()

            records.forEach { record ->
                if (!record.status.equals(RealtimeVote.STATUS_COUNTED, ignoreCase = true)) {
                    optimisticVotesById.remove(record.id)
                    return@forEach
                }
                val mapped = RealtimeVote.fromVoteRecord(record) ?: return@forEach
                cloudVotesById[mapped.id] = mapped
                // Promote optimistic vote once confirmed in Firestore snapshot
                optimisticVotesById.remove(mapped.id)
            }

            publishSnapshotLocked()
        }
    }

    /**
     * Observes the real-time vote count for a single active performance.
     */
    fun observeVoteCount(performanceId: String): Flow<Int> =
        voteCounts
            .map { counts -> counts[performanceId] ?: 0 }
            .distinctUntilChanged()

    /**
     * Observes the detailed [PerformanceVoteCount] state for a single active performance.
     */
    fun observePerformanceVoteState(performanceId: String): Flow<PerformanceVoteCount?> =
        votingState
            .map { state -> state.activePerformances[performanceId] }
            .distinctUntilChanged()

    fun setError(message: String?) {
        synchronized(lock) {
            latestError = message
            publishSnapshotLocked()
        }
    }

    fun reset() {
        synchronized(lock) {
            registeredPerformances.clear()
            optimisticVotesById.clear()
            cloudVotesById.clear()
            manualToggleAdjustments.clear()
            manualVotedByUser.clear()
            cloudStreamSynced = false
            latestVoteEvent = null
            latestError = null
            _votingState.value = ActiveVotingState()
            _voteCounts.value = emptyMap()
        }
    }

    fun removeUserContent(
        deletedUserIds: Set<String>,
        deletedPerformanceIds: Set<String>,
        fallbackUserId: String
    ) {
        synchronized(lock) {
            val perfKeysToRemove = registeredPerformances
                .filter { (id, meta) -> id in deletedPerformanceIds || meta.performerId in deletedUserIds }
                .keys
                .toSet()
            perfKeysToRemove.forEach { registeredPerformances.remove(it) }

            val allRemovedPerfIds = deletedPerformanceIds + perfKeysToRemove
            optimisticVotesById.entries.removeAll { (_, vote) ->
                vote.voterId in deletedUserIds ||
                    vote.performerId in deletedUserIds ||
                    vote.performanceId in allRemovedPerfIds
            }
            cloudVotesById.entries.removeAll { (_, vote) ->
                vote.voterId in deletedUserIds ||
                    vote.performerId in deletedUserIds ||
                    vote.performanceId in allRemovedPerfIds
            }
            allRemovedPerfIds.forEach { perfId ->
                manualToggleAdjustments.remove(perfId)
                manualVotedByUser.remove(perfId)
            }
            manualVotedByUser.values.forEach { voters ->
                voters.removeAll(deletedUserIds)
            }
            latestVoteEvent?.let { latest ->
                if (latest.voterId in deletedUserIds ||
                    latest.performerId in deletedUserIds ||
                    latest.performanceId in allRemovedPerfIds
                ) {
                    latestVoteEvent = null
                }
            }
            activeUserId = fallbackUserId
            publishSnapshotLocked()
        }
    }

    private fun publishSnapshotLocked() {
        val mergedVotesById = LinkedHashMap<String, RealtimeVote>()
        cloudVotesById.forEach { (id, vote) ->
            if (vote.isCounted) mergedVotesById[id] = vote
        }
        optimisticVotesById.forEach { (id, vote) ->
            if (vote.isCounted && id !in mergedVotesById) {
                mergedVotesById[id] = vote
            }
        }

        val votesByPerformance = mergedVotesById.values.groupBy { it.performanceId }
        val currentUid = activeUserId

        // First pass: compute raw totals for each registered performance
        data class RawEntry(
            val meta: RegisteredPerformanceMeta,
            val delta: Int,
            val total: Int,
            val uniqueVoters: Set<String>,
            val votedByCurrentUser: Boolean,
            val userVoteSum: Int,
            val lastTier: VoteTier?,
            val lastVotedAt: Long
        )

        val rawEntries = registeredPerformances.values.map { meta ->
            val perfVotes = votesByPerformance[meta.performanceId].orEmpty()
            val votesSum = perfVotes.sumOf { it.voteDelta }
            val manualAdj = manualToggleAdjustments[meta.performanceId] ?: 0
            val netDelta = votesSum + manualAdj
            val total = (meta.baselineVotes + netDelta).coerceAtLeast(0)

            val voterSet = buildSet {
                perfVotes.forEach { v -> if (v.voterId.isNotBlank()) add(v.voterId) }
                manualVotedByUser[meta.performanceId]?.forEach { uid -> if (uid.isNotBlank()) add(uid) }
            }

            val userVotes = if (!currentUid.isNullOrBlank()) {
                perfVotes.filter { it.voterId == currentUid }.sumOf { it.voteDelta }
            } else {
                0
            }
            val manuallyVoted = !currentUid.isNullOrBlank() &&
                (manualVotedByUser[meta.performanceId]?.contains(currentUid) == true)
            val isVotedByMe = if (userVotes > 0 || manuallyVoted) {
                true
            } else if (manualAdj < 0) {
                false
            } else {
                meta.initialVotedByMe
            }

            val latestPerfVote = perfVotes.maxByOrNull { it.timestampMillis }

            RawEntry(
                meta = meta,
                delta = netDelta,
                total = total,
                uniqueVoters = voterSet,
                votedByCurrentUser = isVotedByMe,
                userVoteSum = userVotes,
                lastTier = latestPerfVote?.resolvedTier,
                lastVotedAt = latestPerfVote?.timestampMillis ?: 0L
            )
        }

        val activeRaw = rawEntries.filter { it.meta.isActive }
        val totalActiveStageVotes = activeRaw.sumOf { it.total }
        val denominator = totalActiveStageVotes.coerceAtLeast(1)

        val leaderEntry = activeRaw
            .filter { it.total > 0 }
            .maxWithOrNull(
                compareBy<RawEntry> { it.total }
                    .thenBy { it.delta }
                    .thenByDescending { it.lastVotedAt }
            )
        val leaderId = leaderEntry?.meta?.performanceId

        val finalMap = LinkedHashMap<String, PerformanceVoteCount>(rawEntries.size)
        val countsOnly = LinkedHashMap<String, Int>(activeRaw.size)

        rawEntries.forEach { raw ->
            val share = if (raw.meta.isActive && totalActiveStageVotes > 0) {
                ((raw.total.toFloat() / denominator.toFloat()) * 100f).coerceIn(0f, 100f)
            } else {
                0f
            }
            val isLeader = raw.meta.isActive && leaderId != null && raw.meta.performanceId == leaderId

            val stateEntry = PerformanceVoteCount(
                performanceId = raw.meta.performanceId,
                performanceTitle = raw.meta.title,
                performerId = raw.meta.performerId,
                performerName = raw.meta.performerName,
                category = raw.meta.category,
                isActive = raw.meta.isActive,
                baselineVotes = raw.meta.baselineVotes,
                realtimeVotesDelta = raw.delta,
                totalVotes = raw.total,
                uniqueVoterIds = raw.uniqueVoters,
                isVotedByCurrentUser = raw.votedByCurrentUser,
                currentUserVoteTotal = raw.userVoteSum,
                voteSharePercent = share,
                isLeading = isLeader,
                lastVoteTier = raw.lastTier,
                lastVotedAtMillis = raw.lastVotedAt
            )
            finalMap[raw.meta.performanceId] = stateEntry
            if (raw.meta.isActive) {
                countsOnly[raw.meta.performanceId] = raw.total
            }
        }

        _voteCounts.value = countsOnly
        _votingState.value = ActiveVotingState(
            activePerformances = finalMap,
            voteCounts = countsOnly,
            totalActiveVotes = totalActiveStageVotes,
            activePerformanceCount = activeRaw.size,
            leadingPerformanceId = leaderId,
            recentVote = latestVoteEvent,
            isCloudSynced = cloudStreamSynced,
            errorMessage = latestError
        )
    }
}
