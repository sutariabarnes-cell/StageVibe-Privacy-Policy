package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AnalyticsChartStyle
import com.example.model.AnalyticsMetricLens
import com.example.model.Performance
import com.example.model.PerformanceAnalyticsEngine
import com.example.model.PerformanceRatingPoint
import com.example.model.RatingTrendDirection
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.GoldCrownLight
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletLight
import com.example.ui.theme.StageBackground
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Interactive Cartesian analytics chart visualizing audience star rating trends, vote-tier breakdowns,
 * and engagement momentum over a creator's last 10 chronological stage performances.
 */
@Composable
fun PerformanceAnalyticsChart(
    performances: List<Performance>,
    creatorBaselineRating: Float = 4.8f,
    onPerformancePointClick: ((performanceId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val summary = remember(performances, creatorBaselineRating) {
        PerformanceAnalyticsEngine.computeLastTenTrend(
            performances = performances,
            creatorBaselineRating = creatorBaselineRating
        )
    }
    val points = summary.points

    var selectedLens by remember { mutableStateOf(AnalyticsMetricLens.RATINGS) }
    var chartStyle by remember { mutableStateOf(AnalyticsChartStyle.AREA_LINE) }
    var selectedIndex by remember(points.size, points.lastOrNull()?.performanceId) {
        mutableIntStateOf((points.size - 1).coerceAtLeast(0))
    }
    val safeSelectedIndex = selectedIndex.coerceIn(0, (points.size - 1).coerceAtLeast(0))
    val selectedPoint = points.getOrNull(safeSelectedIndex)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        ElectricCyan.copy(alpha = 0.45f),
                        NeonViolet.copy(alpha = 0.45f),
                        GoldCrown.copy(alpha = 0.35f)
                    )
                ),
                RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("performance_analytics_chart")
        ) {
            // Header Row + Trend Direction Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.analytics_section_badge),
                        color = ElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.analytics_section_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    )
                    val subtitleRes = when (selectedLens) {
                        AnalyticsMetricLens.RATINGS -> R.string.analytics_section_subtitle
                        AnalyticsMetricLens.VOTE_TIERS -> R.string.analytics_vote_tiers_subtitle
                        AnalyticsMetricLens.MOMENTUM -> R.string.analytics_momentum_subtitle
                    }
                    Text(
                        text = stringResource(subtitleRes),
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                if (points.isNotEmpty()) {
                    val (trendTint, trendIcon) = when (summary.trendDirection) {
                        RatingTrendDirection.RISING -> ElectricCyan to Icons.AutoMirrored.Filled.TrendingUp
                        RatingTrendDirection.STEADY -> GoldCrownLight to Icons.AutoMirrored.Filled.TrendingFlat
                        RatingTrendDirection.DIPPING -> HotMagenta to Icons.AutoMirrored.Filled.TrendingDown
                    }
                    Box(
                        modifier = Modifier
                            .background(trendTint.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                            .border(1.dp, trendTint.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = trendIcon,
                                contentDescription = null,
                                tint = trendTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${summary.formattedNetDelta} ★",
                                color = trendTint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("analytics_trend_delta_badge")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-Metric Telemetry Lens Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("analytics_lens_selector_row"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedLens == AnalyticsMetricLens.RATINGS,
                    onClick = { selectedLens = AnalyticsMetricLens.RATINGS },
                    label = {
                        Text(
                            text = stringResource(R.string.analytics_lens_ratings),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldCrown.copy(alpha = 0.24f),
                        selectedLabelColor = GoldCrownLight,
                        containerColor = StageSurfaceElevated,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("analytics_lens_ratings")
                )
                FilterChip(
                    selected = selectedLens == AnalyticsMetricLens.VOTE_TIERS,
                    onClick = { selectedLens = AnalyticsMetricLens.VOTE_TIERS },
                    label = {
                        Text(
                            text = stringResource(R.string.analytics_lens_vote_tiers),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NeonViolet.copy(alpha = 0.28f),
                        selectedLabelColor = NeonVioletLight,
                        containerColor = StageSurfaceElevated,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("analytics_lens_vote_tiers")
                )
                FilterChip(
                    selected = selectedLens == AnalyticsMetricLens.MOMENTUM,
                    onClick = { selectedLens = AnalyticsMetricLens.MOMENTUM },
                    label = {
                        Text(
                            text = stringResource(R.string.analytics_lens_momentum),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                        selectedLabelColor = ElectricCyan,
                        containerColor = StageSurfaceElevated,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("analytics_lens_momentum")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (points.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StageSurfaceElevated)
                        .padding(20.dp)
                        .testTag("analytics_chart_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ShowChart,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.analytics_empty_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.analytics_empty_subtitle),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // 3-KPI Summary Strip tailored to active lens
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StageSurfaceElevated)
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    when (selectedLens) {
                        AnalyticsMetricLens.RATINGS -> {
                            AnalyticsKpiCell(
                                label = stringResource(R.string.analytics_kpi_window_avg),
                                value = "${summary.formattedWindowAverage} ★",
                                tint = GoldCrownLight,
                                valueTestTag = "analytics_window_avg_value"
                            )
                            AnalyticsKpiCell(
                                label = stringResource(R.string.analytics_kpi_peak_rating),
                                value = "${summary.formattedPeakRating} ★",
                                tint = ElectricCyan,
                                valueTestTag = "analytics_peak_rating_value"
                            )
                            AnalyticsKpiCell(
                                label = stringResource(R.string.analytics_kpi_latest_rating),
                                value = "${summary.formattedLatestRating} ★",
                                tint = NeonVioletLight,
                                valueTestTag = "analytics_latest_rating_value"
                            )
                        }

                        AnalyticsMetricLens.VOTE_TIERS -> {
                            AnalyticsKpiCell(
                                label = "+1 Standard",
                                value = "${summary.totalStandardVotesEst}",
                                tint = ElectricCyan,
                                valueTestTag = "analytics_window_avg_value"
                            )
                            AnalyticsKpiCell(
                                label = "+5 Super",
                                value = "${summary.totalSuperVotesEst}",
                                tint = NeonVioletLight,
                                valueTestTag = "analytics_peak_rating_value"
                            )
                            AnalyticsKpiCell(
                                label = "+10 Ovation",
                                value = "${summary.totalOvationVotesEst}",
                                tint = GoldCrownLight,
                                valueTestTag = "analytics_latest_rating_value"
                            )
                        }

                        AnalyticsMetricLens.MOMENTUM -> {
                            val peakMomentum = points.maxOfOrNull { it.momentumPer100Views } ?: 0f
                            val latestMomentum = points.lastOrNull()?.momentumPer100Views ?: 0f
                            AnalyticsKpiCell(
                                label = "Avg Velocity",
                                value = "${summary.formattedAverageMomentum}/100v",
                                tint = ElectricCyan,
                                valueTestTag = "analytics_window_avg_value"
                            )
                            AnalyticsKpiCell(
                                label = "Peak Velocity",
                                value = String.format(Locale.US, "%.1f/100v", peakMomentum),
                                tint = GoldCrownLight,
                                valueTestTag = "analytics_peak_rating_value"
                            )
                            AnalyticsKpiCell(
                                label = "Latest Velocity",
                                value = String.format(Locale.US, "%.1f/100v", latestMomentum),
                                tint = NeonVioletLight,
                                valueTestTag = "analytics_latest_rating_value"
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Chart Mode Switcher & Reference Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selectedLens == AnalyticsMetricLens.VOTE_TIERS) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            VoteTierLegendDot(label = "+1 Vote", color = ElectricCyan)
                            VoteTierLegendDot(label = "+5 Super", color = NeonVioletLight)
                            VoteTierLegendDot(label = "+10 Ovation", color = GoldCrown)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = chartStyle == AnalyticsChartStyle.AREA_LINE,
                                onClick = { chartStyle = AnalyticsChartStyle.AREA_LINE },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.analytics_mode_area),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricCyan,
                                    selectedLeadingIconColor = ElectricCyan,
                                    containerColor = StageSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("analytics_mode_area")
                            )

                            FilterChip(
                                selected = chartStyle == AnalyticsChartStyle.BAR_COLUMNS,
                                onClick = { chartStyle = AnalyticsChartStyle.BAR_COLUMNS },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.BarChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(R.string.analytics_mode_bars),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonViolet.copy(alpha = 0.28f),
                                    selectedLabelColor = NeonVioletLight,
                                    selectedLeadingIconColor = NeonVioletLight,
                                    containerColor = StageSurfaceElevated,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("analytics_mode_bars")
                            )
                        }
                    }

                    val refLegendText = when (selectedLens) {
                        AnalyticsMetricLens.RATINGS ->
                            stringResource(R.string.analytics_avg_ref_label, summary.formattedWindowAverage)
                        AnalyticsMetricLens.VOTE_TIERS ->
                            "${points.sumOf { it.votesCount }} total pts"
                        AnalyticsMetricLens.MOMENTUM ->
                            "Avg ${summary.formattedAverageMomentum}"
                    }
                    Text(
                        text = refLegendText,
                        color = GoldCrownLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Cartesian Plot Area (Y-Axis + Responsive Canvas + X-Axis Labels)
                CartesianRatingTrendPlot(
                    points = points,
                    windowAverage = summary.windowAverageRating,
                    lowestRating = summary.lowestRating,
                    averageMomentum = summary.averageMomentumScore,
                    selectedLens = selectedLens,
                    selectedIndex = safeSelectedIndex,
                    chartStyle = chartStyle,
                    onSelectIndex = { idx -> selectedIndex = idx }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Accessible Point Selector Strip (P1..P10 with 48dp+ touch targets)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("analytics_point_selector_row")
                ) {
                    itemsIndexed(points) { idx, point ->
                        val isSelected = idx == safeSelectedIndex
                        val chipMetricText = when (selectedLens) {
                            AnalyticsMetricLens.RATINGS -> "${point.slotLabel} • ${point.formattedShortRating}★"
                            AnalyticsMetricLens.VOTE_TIERS -> "${point.slotLabel} • ${point.votesCount}v"
                            AnalyticsMetricLens.MOMENTUM -> "${point.slotLabel} • ${point.formattedMomentum}"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedIndex = idx },
                            label = {
                                Text(
                                    text = chipMetricText,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonViolet,
                                selectedLabelColor = Color.White,
                                containerColor = StageSurfaceElevated,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("analytics_point_chip_${point.slotNumber}")
                        )
                    }
                }

                // Selected Point Interactive Tooltip Card
                if (selectedPoint != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AnalyticsPointTooltipCard(
                        point = selectedPoint,
                        totalInWindow = points.size,
                        onPlayClick = onPerformancePointClick?.let { cb ->
                            { cb(selectedPoint.performanceId) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun VoteTierLegendDot(
    label: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AnalyticsKpiCell(
    label: String,
    value: String,
    tint: Color,
    valueTestTag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.testTag(valueTestTag)
        )
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CartesianRatingTrendPlot(
    points: List<PerformanceRatingPoint>,
    windowAverage: Float,
    lowestRating: Float,
    averageMomentum: Float,
    selectedLens: AnalyticsMetricLens,
    selectedIndex: Int,
    chartStyle: AnalyticsChartStyle,
    onSelectIndex: (Int) -> Unit
) {
    val maxVoteBallots = remember(points) {
        (points.maxOfOrNull { it.standardVotesEst + it.superVotesEst + it.ovationVotesEst } ?: 10)
            .coerceAtLeast(5)
    }
    val maxMomentum = remember(points) {
        ((points.maxOfOrNull { it.momentumPer100Views } ?: 50f) * 1.15f).coerceAtLeast(20f)
    }

    val (yMaxLabel, yMidLabel, yMinLabel) = when (selectedLens) {
        AnalyticsMetricLens.RATINGS -> {
            val yMax = 5.0f
            val rawFloor = (lowestRating - 0.35f).coerceIn(1.0f, 4.0f)
            val yMin = (floor(rawFloor * 2f) / 2f).coerceIn(1.0f, 4.0f)
            val yMid = (yMax + yMin) / 2f
            Triple(
                String.format(Locale.US, "%.1f★", yMax),
                String.format(Locale.US, "%.1f★", yMid),
                String.format(Locale.US, "%.1f★", yMin)
            )
        }
        AnalyticsMetricLens.VOTE_TIERS -> {
            Triple("$maxVoteBallots", "${maxVoteBallots / 2}", "0")
        }
        AnalyticsMetricLens.MOMENTUM -> {
            Triple(
                String.format(Locale.US, "%.0f", maxMomentum),
                String.format(Locale.US, "%.0f", maxMomentum / 2f),
                "0"
            )
        }
    }

    val canvasDesc = stringResource(R.string.analytics_chart_canvas_desc, points.size)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StageBackground)
            .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(14.dp))
            .padding(top = 12.dp, bottom = 10.dp, start = 8.dp, end = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Y-Axis Ticks Column
            Column(
                modifier = Modifier
                    .width(38.dp)
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = yMaxLabel,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = yMidLabel,
                    color = TextMuted,
                    fontSize = 9.sp
                )
                Text(
                    text = yMinLabel,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Responsive Cartesian Canvas using DrawScope dimensions
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .semantics { contentDescription = canvasDesc }
                    .pointerInput(points.size) {
                        detectTapGestures { tapOffset ->
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val idx = resolveNearestPointIndex(tapOffset.x, width, points.size)
                            onSelectIndex(idx)
                        }
                    }
                    .pointerInput(points.size) {
                        detectHorizontalDragGestures { change, _ ->
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val idx = resolveNearestPointIndex(change.position.x, width, points.size)
                            onSelectIndex(idx)
                        }
                    }
                    .testTag("analytics_plot_canvas")
            ) {
                val plotWidth = size.width
                val plotHeight = size.height
                val topPad = 10.dp.toPx()
                val bottomPad = 10.dp.toPx()
                val horizontalPad = 12.dp.toPx()
                val usableHeight = (plotHeight - topPad - bottomPad).coerceAtLeast(1f)
                val usableWidth = (plotWidth - horizontalPad * 2f).coerceAtLeast(1f)
                val baselineY = topPad + usableHeight

                fun indexToX(idx: Int): Float {
                    return if (points.size <= 1) {
                        plotWidth / 2f
                    } else {
                        horizontalPad + (idx.toFloat() / (points.size - 1).toFloat()) * usableWidth
                    }
                }

                val gridDash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                listOf(topPad, topPad + usableHeight * 0.5f, baselineY).forEach { gy ->
                    drawLine(
                        color = StageSurfaceHighlight.copy(alpha = 0.75f),
                        start = Offset(0f, gy),
                        end = Offset(plotWidth, gy),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = gridDash
                    )
                }

                if (selectedLens == AnalyticsMetricLens.VOTE_TIERS) {
                    val slotWidth = if (points.size <= 1) {
                        usableWidth * 0.28f
                    } else {
                        (usableWidth / points.size.toFloat()) * 0.58f
                    }.coerceIn(10.dp.toPx(), 34.dp.toPx())

                    points.forEachIndexed { idx, pt ->
                        val cx = indexToX(idx)
                        val totalBallots = (pt.standardVotesEst + pt.superVotesEst + pt.ovationVotesEst).coerceAtLeast(1)
                        val totalFraction = ((pt.standardVotesEst + pt.superVotesEst + pt.ovationVotesEst).toFloat() / maxVoteBallots.toFloat())
                            .coerceIn(0.06f, 1f)
                        val fullBarH = usableHeight * totalFraction
                        val stdH = fullBarH * (pt.standardVotesEst.toFloat() / totalBallots.toFloat())
                        val supH = fullBarH * (pt.superVotesEst.toFloat() / totalBallots.toFloat())
                        val ovaH = (fullBarH - stdH - supH).coerceAtLeast(0f)

                        var currentBottom = baselineY
                        if (stdH > 0.5f) {
                            drawRoundRect(
                                color = ElectricCyan.copy(alpha = if (idx == selectedIndex) 1f else 0.75f),
                                topLeft = Offset(cx - slotWidth / 2f, currentBottom - stdH),
                                size = Size(slotWidth, stdH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            currentBottom -= stdH
                        }
                        if (supH > 0.5f) {
                            drawRoundRect(
                                color = NeonVioletLight.copy(alpha = if (idx == selectedIndex) 1f else 0.78f),
                                topLeft = Offset(cx - slotWidth / 2f, currentBottom - supH),
                                size = Size(slotWidth, supH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                            currentBottom -= supH
                        }
                        if (ovaH > 0.5f) {
                            drawRoundRect(
                                color = GoldCrown.copy(alpha = if (idx == selectedIndex) 1f else 0.85f),
                                topLeft = Offset(cx - slotWidth / 2f, currentBottom - ovaH),
                                size = Size(slotWidth, ovaH),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }
                    }
                } else {
                    val yMax = if (selectedLens == AnalyticsMetricLens.RATINGS) 5.0f else maxMomentum
                    val yMin = if (selectedLens == AnalyticsMetricLens.RATINGS) {
                        val rawFloor = (lowestRating - 0.35f).coerceIn(1.0f, 4.0f)
                        (floor(rawFloor * 2f) / 2f).coerceIn(1.0f, 4.0f)
                    } else {
                        0f
                    }
                    val domainSpan = (yMax - yMin).coerceAtLeast(0.5f)

                    fun valueToY(v: Float): Float {
                        val normalized = ((v.coerceIn(yMin, yMax) - yMin) / domainSpan).coerceIn(0f, 1f)
                        return topPad + usableHeight * (1f - normalized)
                    }

                    val refVal = if (selectedLens == AnalyticsMetricLens.RATINGS) windowAverage else averageMomentum
                    val avgY = valueToY(refVal)
                    drawLine(
                        color = GoldCrown.copy(alpha = 0.75f),
                        start = Offset(0f, avgY),
                        end = Offset(plotWidth, avgY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )

                    val offsets = points.mapIndexed { idx, pt ->
                        val v = if (selectedLens == AnalyticsMetricLens.RATINGS) pt.audienceRating else pt.momentumPer100Views
                        Offset(indexToX(idx), valueToY(v))
                    }

                    if (chartStyle == AnalyticsChartStyle.BAR_COLUMNS) {
                        val slotWidth = if (points.size <= 1) {
                            usableWidth * 0.25f
                        } else {
                            (usableWidth / points.size.toFloat()) * 0.56f
                        }.coerceIn(10.dp.toPx(), 34.dp.toPx())

                        offsets.forEachIndexed { idx, ptOffset ->
                            val isSelected = idx == selectedIndex
                            val barHeight = (baselineY - ptOffset.y).coerceAtLeast(6.dp.toPx())
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    colors = if (isSelected) {
                                        listOf(GoldCrown, ElectricCyan)
                                    } else {
                                        listOf(ElectricCyan.copy(alpha = 0.85f), NeonViolet.copy(alpha = 0.45f))
                                    },
                                    startY = ptOffset.y,
                                    endY = baselineY
                                ),
                                topLeft = Offset(ptOffset.x - slotWidth / 2f, ptOffset.y),
                                size = Size(slotWidth, barHeight),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                        }
                    } else {
                        if (offsets.size >= 2) {
                            val linePath = Path().apply {
                                moveTo(offsets.first().x, offsets.first().y)
                                for (i in 0 until offsets.size - 1) {
                                    val curr = offsets[i]
                                    val next = offsets[i + 1]
                                    val controlX = (curr.x + next.x) / 2f
                                    cubicTo(
                                        controlX, curr.y,
                                        controlX, next.y,
                                        next.x, next.y
                                    )
                                }
                            }

                            val fillPath = Path().apply {
                                addPath(linePath)
                                lineTo(offsets.last().x, baselineY)
                                lineTo(offsets.first().x, baselineY)
                                close()
                            }

                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        ElectricCyan.copy(alpha = 0.34f),
                                        NeonViolet.copy(alpha = 0.14f),
                                        Color.Transparent
                                    ),
                                    startY = topPad,
                                    endY = baselineY
                                )
                            )

                            drawPath(
                                path = linePath,
                                brush = Brush.horizontalGradient(
                                    colors = listOf(NeonVioletLight, ElectricCyan, GoldCrown)
                                ),
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        } else if (offsets.size == 1) {
                            val single = offsets.first()
                            drawLine(
                                color = ElectricCyan.copy(alpha = 0.45f),
                                start = Offset(horizontalPad, single.y),
                                end = Offset(plotWidth - horizontalPad, single.y),
                                strokeWidth = 2.dp.toPx()
                            )
                        }

                        offsets.getOrNull(selectedIndex)?.let { activeOffset ->
                            drawLine(
                                color = ElectricCyan.copy(alpha = 0.4f),
                                start = Offset(activeOffset.x, topPad),
                                end = Offset(activeOffset.x, baselineY),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = gridDash
                            )
                        }

                        offsets.forEachIndexed { idx, ptOffset ->
                            val isSelected = idx == selectedIndex
                            if (isSelected) {
                                drawCircle(
                                    color = GoldCrown.copy(alpha = 0.28f),
                                    radius = 10.dp.toPx(),
                                    center = ptOffset
                                )
                            }
                            drawCircle(
                                color = StageBackground,
                                radius = if (isSelected) 6.dp.toPx() else 4.5.dp.toPx(),
                                center = ptOffset
                            )
                            drawCircle(
                                color = if (isSelected) GoldCrown else ElectricCyan,
                                radius = if (isSelected) 4.5.dp.toPx() else 3.dp.toPx(),
                                center = ptOffset
                            )
                        }
                    }
                }
            }
        }

        // X-Axis Slot Labels (P1..P10)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 46.dp, top = 4.dp),
            horizontalArrangement = if (points.size <= 1) Arrangement.Center else Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { idx, pt ->
                val isSelected = idx == selectedIndex
                Text(
                    text = pt.slotLabel,
                    color = if (isSelected) GoldCrownLight else TextMuted,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                )
            }
        }
    }
}

private fun resolveNearestPointIndex(xPx: Float, widthPx: Float, count: Int): Int {
    if (count <= 1) return 0
    val fraction = (xPx / widthPx).coerceIn(0f, 1f)
    return (fraction * (count - 1).toFloat()).roundToInt().coerceIn(0, count - 1)
}

@Composable
private fun AnalyticsPointTooltipCard(
    point: PerformanceRatingPoint,
    totalInWindow: Int,
    onPlayClick: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("analytics_selected_tooltip"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(NeonViolet.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "#${point.slotNumber} of $totalInWindow",
                            color = NeonVioletLight,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = point.styleLabel,
                        color = ElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = point.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("analytics_tooltip_title")
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${point.ratingsCount} ratings • ${point.votesCount} votes • ${point.viewsCount} views",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = GoldCrown,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${point.formattedRating} ★",
                            color = GoldCrown,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            modifier = Modifier.testTag("analytics_tooltip_rating")
                        )
                    }
                    point.formattedDelta?.let { deltaText ->
                        val deltaColor = when {
                            (point.deltaFromPrevious ?: 0f) > 0.005f -> ElectricCyan
                            (point.deltaFromPrevious ?: 0f) < -0.005f -> HotMagenta
                            else -> TextMuted
                        }
                        Text(
                            text = "$deltaText vs prev",
                            color = deltaColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onPlayClick != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onPlayClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.16f))
                            .testTag("analytics_tooltip_play_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.analytics_tooltip_play_desc),
                            tint = ElectricCyan
                        )
                    }
                }
            }
        }
    }
}
