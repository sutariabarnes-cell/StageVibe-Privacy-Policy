package com.example.model

import java.util.Locale
import kotlin.math.roundToInt

/**
 * Overall trajectory classification across a creator's recent performance window.
 */
enum class RatingTrendDirection {
    RISING,
    STEADY,
    DIPPING
}

/**
 * Visual presentation style for the PerformanceAnalyticsChart composable.
 */
enum class AnalyticsChartStyle {
    AREA_LINE,
    BAR_COLUMNS
}

/**
 * Multi-metric telemetry lens for the Creator Telemetry Studio.
 */
enum class AnalyticsMetricLens {
    RATINGS,
    VOTE_TIERS,
    MOMENTUM
}

/**
 * Single data point in the creator's last-10-performances audience rating & telemetry trend series.
 */
data class PerformanceRatingPoint(
    val slotNumber: Int,
    val slotLabel: String,
    val performanceId: String,
    val title: String,
    val styleLabel: String,
    val audienceRating: Float,
    val ratingsCount: Int,
    val votesCount: Int,
    val likesCount: Int,
    val viewsCount: Int,
    val deltaFromPrevious: Float?,
    val createdAtTimestamp: Long,
    val cheersCount: Int = 0,
    val standardVotesEst: Int = 0,
    val superVotesEst: Int = 0,
    val ovationVotesEst: Int = 0,
    val momentumPer100Views: Float = 0f
) {
    val formattedRating: String
        get() = String.format(Locale.US, "%.2f", audienceRating.coerceIn(1.0f, 5.0f))

    val formattedShortRating: String
        get() = String.format(Locale.US, "%.1f", audienceRating.coerceIn(1.0f, 5.0f))

    val formattedMomentum: String
        get() = String.format(Locale.US, "%.1f", momentumPer100Views.coerceAtLeast(0f))

    val formattedDelta: String?
        get() = deltaFromPrevious?.let { delta ->
            val sign = if (delta > 0.005f) "+" else ""
            String.format(Locale.US, "%s%.2f", sign, delta)
        }
}

/**
 * Computed analytics summary over at most the creator's last 10 performances.
 */
data class PerformanceAnalyticsSummary(
    val points: List<PerformanceRatingPoint>,
    val windowAverageRating: Float,
    val latestRating: Float,
    val peakRating: Float,
    val lowestRating: Float,
    val netTrendDelta: Float,
    val trendDirection: RatingTrendDirection,
    val totalCatalogCount: Int,
    val totalStandardVotesEst: Int = 0,
    val totalSuperVotesEst: Int = 0,
    val totalOvationVotesEst: Int = 0,
    val averageMomentumScore: Float = 0f
) {
    val totalAnalyzedCount: Int
        get() = points.size

    val formattedWindowAverage: String
        get() = String.format(Locale.US, "%.2f", windowAverageRating.coerceIn(0f, 5f))

    val formattedPeakRating: String
        get() = String.format(Locale.US, "%.2f", peakRating.coerceIn(0f, 5f))

    val formattedLatestRating: String
        get() = String.format(Locale.US, "%.2f", latestRating.coerceIn(0f, 5f))

    val formattedAverageMomentum: String
        get() = String.format(Locale.US, "%.1f", averageMomentumScore.coerceAtLeast(0f))

    val formattedNetDelta: String
        get() {
            val sign = if (netTrendDelta > 0.005f) "+" else ""
            return String.format(Locale.US, "%s%.2f", sign, netTrendDelta)
        }
}

/**
 * Analytics engine computing audience rating trends, vote-tier breakdowns, and engagement momentum
 * strictly across a creator's last 10 chronological stage performances.
 */
object PerformanceAnalyticsEngine {

    const val MAX_RECENT_PERFORMANCES = 10

    /**
     * Resolves an authentic audience star rating (`1.0f..5.0f`) for a [Performance].
     * Uses explicit [Performance.audienceRating] when recorded; otherwise derives a deterministic
     * engagement-weighted score from real votes, likes, cheers, and views anchored around
     * [creatorBaselineRating].
     */
    fun resolvePerformanceRating(
        performance: Performance,
        creatorBaselineRating: Float = 4.8f
    ): Float {
        performance.audienceRating?.let { explicit ->
            return ((explicit.coerceIn(1.0f, 5.0f) * 100f).roundToInt() / 100f)
        }

        val safeBaseline = creatorBaselineRating.coerceIn(3.5f, 5.0f)
        val views = performance.viewsCount.coerceAtLeast(1)
        val weightedEngagement = (performance.votesCount * 2.4f) +
            (performance.likesCount * 1.2f) +
            (performance.cheersCount * 1.8f)
        val engagementRatio = (weightedEngagement / views.toFloat()).coerceIn(0f, 2.5f)

        // Offset around baseline (-0.35..+0.22) driven by real audience engagement ratio
        val engagementAdjustment = ((engagementRatio - 0.85f) * 0.28f).coerceIn(-0.35f, 0.22f)
        val rawRating = (safeBaseline - 0.12f + engagementAdjustment).coerceIn(1.0f, 5.0f)
        return (rawRating * 100f).roundToInt() / 100f
    }

    /**
     * Resolves audience rating review count for a [Performance] from explicit count or
     * real voter/cheer participation.
     */
    fun resolvePerformanceRatingsCount(performance: Performance): Int {
        if (performance.ratingsCount > 0) return performance.ratingsCount
        val derived = (performance.votesCount / 4) + (performance.cheersCount / 3)
        return derived.coerceAtLeast(if (performance.viewsCount > 0) 1 else 0)
    }

    /**
     * Deterministically decomposes cumulative vote points into `+1` Standard, `+5` Super,
     * and `+10` Standing Ovation ballot counts weighted by crowd cheers & likes.
     */
    fun computeVoteTierBreakdown(performance: Performance): Triple<Int, Int, Int> {
        val totalVotePoints = performance.votesCount.coerceAtLeast(0)
        if (totalVotePoints == 0) return Triple(0, 0, 0)

        val ovationRatio = if (performance.cheersCount > 0) 0.28f else 0.15f
        val ovationBallots = ((totalVotePoints * ovationRatio) / 10f).toInt().coerceAtLeast(0)
        val remainingAfterOvation = (totalVotePoints - ovationBallots * 10).coerceAtLeast(0)

        val superBallots = ((remainingAfterOvation * 0.45f) / 5f).toInt().coerceAtLeast(0)
        val standardBallots = (remainingAfterOvation - superBallots * 5).coerceAtLeast(0)
        return Triple(standardBallots, superBallots, ovationBallots)
    }

    /**
     * Computes engagement & reaction velocity per 100 stage views.
     */
    fun computeMomentumPer100Views(performance: Performance): Float {
        val safeViews = performance.viewsCount.coerceAtLeast(10)
        val weightedSignals = (performance.votesCount * 1.5f) +
            (performance.cheersCount * 2.2f) +
            (performance.likesCount * 1.1f)
        val rawScore = (weightedSignals / safeViews.toFloat()) * 100f
        return ((rawScore.coerceIn(0f, 999f) * 10f).roundToInt() / 10f)
    }

    /**
     * Computes the audience rating trend across the creator's last 10 chronological performances
     * (ordered oldest -> newest from `#1` up to `#10`).
     */
    fun computeLastTenTrend(
        performances: List<Performance>,
        creatorBaselineRating: Float = 4.8f
    ): PerformanceAnalyticsSummary {
        if (performances.isEmpty()) {
            return PerformanceAnalyticsSummary(
                points = emptyList(),
                windowAverageRating = 0f,
                latestRating = 0f,
                peakRating = 0f,
                lowestRating = 0f,
                netTrendDelta = 0f,
                trendDirection = RatingTrendDirection.STEADY,
                totalCatalogCount = 0
            )
        }

        val sortedChronological = performances.sortedWith(
            compareBy<Performance> { it.createdAtTimestamp }
                .thenBy { it.id }
        )
        val recentWindow = sortedChronological.takeLast(MAX_RECENT_PERFORMANCES)

        val points = mutableListOf<PerformanceRatingPoint>()
        var previousRating: Float? = null

        recentWindow.forEachIndexed { index, perf ->
            val slot = index + 1
            val rating = resolvePerformanceRating(perf, creatorBaselineRating)
            val delta = previousRating?.let { prev ->
                ((rating - prev) * 100f).roundToInt() / 100f
            }
            previousRating = rating

            val (stdVotes, superVotes, ovationVotes) = computeVoteTierBreakdown(perf)
            val momentum = computeMomentumPer100Views(perf)

            points.add(
                PerformanceRatingPoint(
                    slotNumber = slot,
                    slotLabel = "P$slot",
                    performanceId = perf.id,
                    title = perf.title,
                    styleLabel = perf.performanceStyle.displayName,
                    audienceRating = rating,
                    ratingsCount = resolvePerformanceRatingsCount(perf),
                    votesCount = perf.votesCount,
                    likesCount = perf.likesCount,
                    viewsCount = perf.viewsCount,
                    deltaFromPrevious = delta,
                    createdAtTimestamp = perf.createdAtTimestamp,
                    cheersCount = perf.cheersCount,
                    standardVotesEst = stdVotes,
                    superVotesEst = superVotes,
                    ovationVotesEst = ovationVotes,
                    momentumPer100Views = momentum
                )
            )
        }

        val avg = points.map { it.audienceRating }.average().toFloat()
        val roundedAvg = (avg * 100f).roundToInt() / 100f
        val firstRating = points.first().audienceRating
        val latestRating = points.last().audienceRating
        val peakRating = points.maxOf { it.audienceRating }
        val lowestRating = points.minOf { it.audienceRating }
        val netDelta = ((latestRating - firstRating) * 100f).roundToInt() / 100f

        val direction = when {
            points.size < 2 -> RatingTrendDirection.STEADY
            netDelta >= 0.05f -> RatingTrendDirection.RISING
            netDelta <= -0.05f -> RatingTrendDirection.DIPPING
            else -> RatingTrendDirection.STEADY
        }

        val avgMomentum = ((points.map { it.momentumPer100Views }.average().toFloat() * 10f).roundToInt() / 10f)

        return PerformanceAnalyticsSummary(
            points = points,
            windowAverageRating = roundedAvg,
            latestRating = latestRating,
            peakRating = peakRating,
            lowestRating = lowestRating,
            netTrendDelta = netDelta,
            trendDirection = direction,
            totalCatalogCount = performances.size,
            totalStandardVotesEst = points.sumOf { it.standardVotesEst },
            totalSuperVotesEst = points.sumOf { it.superVotesEst },
            totalOvationVotesEst = points.sumOf { it.ovationVotesEst },
            averageMomentumScore = avgMomentum
        )
    }
}
