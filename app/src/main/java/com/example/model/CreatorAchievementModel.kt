package com.example.model

import java.util.Locale

/**
 * Rarity tier for stage achievement badges displayed in a creator's badge gallery.
 */
enum class BadgeRarity(
    val label: String,
    val primaryColorHex: Long,
    val secondaryColorHex: Long
) {
    LEGENDARY(
        label = "Legendary",
        primaryColorHex = 0xFFFFB703,
        secondaryColorHex = 0xFFF72585
    ),
    EPIC(
        label = "Epic",
        primaryColorHex = 0xFF9D4EDD,
        secondaryColorHex = 0xFF00F5D4
    ),
    RARE(
        label = "Rare",
        primaryColorHex = 0xFF00F5D4,
        secondaryColorHex = 0xFF00BBF9
    ),
    MILESTONE(
        label = "Milestone",
        primaryColorHex = 0xFFF72585,
        secondaryColorHex = 0xFFC77DFF
    )
}

/**
 * Semantic icon identifier mapped to Material Symbols in the Compose UI layer.
 */
enum class BadgeIconType {
    STAGE_DEBUT,
    PROLIFIC_PERFORMER,
    CROWD_DARLING,
    VOTE_MAGNET,
    ARENA_HEADLINER,
    LIVE_STAGE_HOST,
    GENRE_CHAMELEON,
    VISUAL_VISIONARY
}

/**
 * Filter tab state for the profile Achievement Badge Gallery.
 */
enum class BadgeFilterMode {
    ALL,
    UNLOCKED,
    IN_PROGRESS
}

/**
 * Represents a single creator achievement badge with live unlock status and milestone progress.
 */
data class AchievementBadge(
    val id: String,
    val title: String,
    val description: String,
    val rarity: BadgeRarity,
    val iconType: BadgeIconType,
    val isUnlocked: Boolean,
    val progressCurrent: Int,
    val progressTarget: Int,
    val progressLabel: String
) {
    val progressFraction: Float
        get() = if (isUnlocked) {
            1f
        } else {
            (progressCurrent.toFloat() / progressTarget.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
        }
}

/**
 * Aggregated creator profile statistics and computed achievement badge gallery state.
 */
data class CreatorStatisticsSummary(
    val creatorId: String,
    val totalPerformances: Int,
    val averageAudienceRating: Float,
    val totalAudienceRatingsCount: Int,
    val totalVotes: Int,
    val totalViews: Int,
    val totalLikes: Int,
    val totalSessionsHosted: Int,
    val leaderboardRank: Int?,
    val distinctStylesCount: Int,
    val hasVideoPerformance: Boolean,
    val badges: List<AchievementBadge>
) {
    val formattedAverageRating: String
        get() = String.format(Locale.US, "%.1f", averageAudienceRating.coerceIn(0f, 5f))

    val unlockedBadgesCount: Int
        get() = badges.count { it.isUnlocked }

    val totalBadgesCount: Int
        get() = badges.size

    val completionFraction: Float
        get() = if (badges.isEmpty()) 0f else unlockedBadgesCount.toFloat() / badges.size.toFloat()
}

/**
 * Deterministic achievement evaluation engine computing creator telemetry and unlocking
 * stage badges from real portfolio performances, audience ratings, live sessions, and rank.
 */
object CreatorAchievementEngine {

    fun computeSummary(
        profile: CreatorProfile,
        performances: List<Performance>,
        sessionStats: CreatorSessionStats = CreatorSessionStats(),
        leaderboardRank: Int? = null,
        overrideTotalPerformances: Int? = null,
        overrideTotalVotes: Int? = null
    ): CreatorStatisticsSummary {
        val totalPerformances = maxOf(performances.size, overrideTotalPerformances ?: 0)
        val avgRating = profile.audienceRating.coerceIn(0f, 5f)
        val ratingsCount = profile.totalAudienceRatingsCount.coerceAtLeast(0)
        val totalVotes = maxOf(performances.sumOf { it.votesCount }, overrideTotalVotes ?: 0)
        val totalViews = performances.sumOf { it.viewsCount }
        val totalLikes = performances.sumOf { it.likesCount }
        val totalSessions = sessionStats.totalSessions.coerceAtLeast(sessionStats.pastSessions.size)
        val distinctStylesCount = performances.map { it.performanceStyle }.distinct().size
        val videoCount = performances.count { it.hasVideo }
        val hasVideoPerformance = videoCount > 0

        val badges = buildList {
            // 1. Stage Debut — First published performance
            val debutUnlocked = totalPerformances >= 1
            add(
                AchievementBadge(
                    id = "stage_debut",
                    title = "Stage Debut",
                    description = "Publish your first original music or spoken word performance to the stage.",
                    rarity = BadgeRarity.MILESTONE,
                    iconType = BadgeIconType.STAGE_DEBUT,
                    isUnlocked = debutUnlocked,
                    progressCurrent = totalPerformances.coerceAtMost(1),
                    progressTarget = 1,
                    progressLabel = "${totalPerformances.coerceAtMost(1)} / 1 performance"
                )
            )

            // 2. Prolific Performer — 3+ published performances
            val prolificTarget = 3
            val prolificUnlocked = totalPerformances >= prolificTarget
            add(
                AchievementBadge(
                    id = "prolific_performer",
                    title = "Prolific Performer",
                    description = "Build a stage repertoire of at least 3 published tracks or spoken word pieces.",
                    rarity = BadgeRarity.RARE,
                    iconType = BadgeIconType.PROLIFIC_PERFORMER,
                    isUnlocked = prolificUnlocked,
                    progressCurrent = totalPerformances.coerceAtMost(prolificTarget),
                    progressTarget = prolificTarget,
                    progressLabel = "${totalPerformances.coerceAtMost(prolificTarget)} / $prolificTarget performances"
                )
            )

            // 3. Crowd Darling — 4.8+ average audience rating with 10+ ratings
            val ratingReviewsTarget = 10
            val crowdDarlingUnlocked = avgRating >= 4.8f && ratingsCount >= ratingReviewsTarget
            val crowdProgressCurrent = if (crowdDarlingUnlocked) {
                ratingReviewsTarget
            } else if (avgRating >= 4.8f) {
                ratingsCount.coerceAtMost(ratingReviewsTarget)
            } else {
                ((avgRating / 4.8f) * ratingReviewsTarget.coerceAtMost(ratingsCount.coerceAtLeast(1)))
                    .toInt()
                    .coerceIn(0, ratingReviewsTarget - 1)
            }
            add(
                AchievementBadge(
                    id = "crowd_darling",
                    title = "Crowd Darling",
                    description = "Maintain a 4.8+ ★ average audience rating across 10 or more spectator reviews.",
                    rarity = BadgeRarity.EPIC,
                    iconType = BadgeIconType.CROWD_DARLING,
                    isUnlocked = crowdDarlingUnlocked,
                    progressCurrent = crowdProgressCurrent,
                    progressTarget = ratingReviewsTarget,
                    progressLabel = "${String.format(Locale.US, "%.1f", avgRating)} ★ ($ratingsCount/$ratingReviewsTarget)"
                )
            )

            // 4. Vote Magnet — 500+ total stage votes
            val voteTarget = 500
            val voteMagnetUnlocked = totalVotes >= voteTarget
            add(
                AchievementBadge(
                    id = "vote_magnet",
                    title = "Vote Magnet",
                    description = "Rally the arena audience to cast 500 or more real-time stage votes for your work.",
                    rarity = BadgeRarity.EPIC,
                    iconType = BadgeIconType.VOTE_MAGNET,
                    isUnlocked = voteMagnetUnlocked,
                    progressCurrent = totalVotes.coerceAtMost(voteTarget),
                    progressTarget = voteTarget,
                    progressLabel = "${totalVotes.coerceAtMost(voteTarget)} / $voteTarget votes"
                )
            )

            // 5. Arena Headliner — Ranked #1–#3 on Leaderboard
            val headlinerUnlocked = leaderboardRank != null && leaderboardRank in 1..3
            val headlinerProgress = when {
                headlinerUnlocked -> 3
                leaderboardRank != null && leaderboardRank <= 10 -> 2
                leaderboardRank != null -> 1
                else -> 0
            }
            add(
                AchievementBadge(
                    id = "arena_headliner",
                    title = "Arena Headliner",
                    description = "Claim a podium spot (#1–#3) on the VersePulse Top 10 Arena Leaderboard.",
                    rarity = BadgeRarity.LEGENDARY,
                    iconType = BadgeIconType.ARENA_HEADLINER,
                    isUnlocked = headlinerUnlocked,
                    progressCurrent = headlinerProgress,
                    progressTarget = 3,
                    progressLabel = if (leaderboardRank != null) "Rank #$leaderboardRank (Target Top 3)" else "Unranked (Target Top 3)"
                )
            )

            // 6. Live Stage Host — Host 1+ live session
            val liveHostUnlocked = totalSessions >= 1
            add(
                AchievementBadge(
                    id = "live_stage_host",
                    title = "Live Stage Host",
                    description = "Step under the spotlight and host a live music cipher or spoken word stage session.",
                    rarity = BadgeRarity.RARE,
                    iconType = BadgeIconType.LIVE_STAGE_HOST,
                    isUnlocked = liveHostUnlocked,
                    progressCurrent = totalSessions.coerceAtMost(1),
                    progressTarget = 1,
                    progressLabel = "${totalSessions.coerceAtMost(1)} / 1 live session"
                )
            )

            // 7. Genre Chameleon — 2+ distinct performance styles
            val stylesTarget = 2
            val chameleonUnlocked = distinctStylesCount >= stylesTarget
            add(
                AchievementBadge(
                    id = "genre_chameleon",
                    title = "Genre Chameleon",
                    description = "Showcase range across at least 2 distinct stage styles (Rap, Jazz, Poetry, Spoken Word, etc.).",
                    rarity = BadgeRarity.RARE,
                    iconType = BadgeIconType.GENRE_CHAMELEON,
                    isUnlocked = chameleonUnlocked,
                    progressCurrent = distinctStylesCount.coerceAtMost(stylesTarget),
                    progressTarget = stylesTarget,
                    progressLabel = "${distinctStylesCount.coerceAtMost(stylesTarget)} / $stylesTarget styles"
                )
            )

            // 8. Visual Visionary — Publish a video performance
            add(
                AchievementBadge(
                    id = "visual_visionary",
                    title = "Visual Visionary",
                    description = "Upload an original stage performance video to immerse spectators visually.",
                    rarity = BadgeRarity.MILESTONE,
                    iconType = BadgeIconType.VISUAL_VISIONARY,
                    isUnlocked = hasVideoPerformance,
                    progressCurrent = videoCount.coerceAtMost(1),
                    progressTarget = 1,
                    progressLabel = "${videoCount.coerceAtMost(1)} / 1 stage video"
                )
            )
        }

        return CreatorStatisticsSummary(
            creatorId = profile.creatorId,
            totalPerformances = totalPerformances,
            averageAudienceRating = avgRating,
            totalAudienceRatingsCount = ratingsCount,
            totalVotes = totalVotes,
            totalViews = totalViews,
            totalLikes = totalLikes,
            totalSessionsHosted = totalSessions,
            leaderboardRank = leaderboardRank,
            distinctStylesCount = distinctStylesCount,
            hasVideoPerformance = hasVideoPerformance,
            badges = badges
        )
    }
}
