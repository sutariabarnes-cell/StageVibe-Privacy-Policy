package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AchievementBadge
import com.example.model.BadgeFilterMode
import com.example.model.BadgeIconType
import com.example.model.CreatorAchievementEngine
import com.example.model.CreatorProfile
import com.example.model.CreatorSessionStats
import com.example.model.CreatorStatisticsSummary
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.GoldCrownLight
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletLight
import com.example.ui.theme.SpokenWordPrimary
import com.example.ui.theme.StageBackground
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Self-contained Creator Profile View presenting creator identity, core stage statistics
 * (Total Performances, Average Audience Rating, Badges Unlocked, Arena Rank, Votes, Views, Likes),
 * and an interactive Achievement Badge Gallery.
 */
@Composable
fun CreatorStatsProfileView(
    profile: CreatorProfile,
    performances: List<Performance>,
    sessionStats: CreatorSessionStats = CreatorSessionStats(),
    leaderboardRank: Int? = null,
    onBack: (() -> Unit)? = null,
    onRateCreator: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var userRating by remember(profile.creatorId) { mutableIntStateOf(0) }
    val summary = remember(profile, performances, sessionStats, leaderboardRank) {
        CreatorAchievementEngine.computeSummary(
            profile = profile,
            performances = performances,
            sessionStats = sessionStats,
            leaderboardRank = leaderboardRank
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StageBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("creator_stats_profile_view"),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .testTag("creator_stats_profile_list"),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                CreatorIdentitySummaryHeader(
                    profile = profile,
                    onBack = onBack
                )
            }

            item {
                CreatorStatisticsCard(
                    summary = summary,
                    allowRating = !profile.isCurrentUser && onRateCreator != null,
                    userRating = userRating,
                    onRate = { star ->
                        userRating = star
                        onRateCreator?.invoke(star.toFloat())
                    }
                )
            }

            item {
                PerformanceAnalyticsChart(
                    performances = performances,
                    creatorBaselineRating = profile.audienceRating
                )
            }

            item {
                AchievementBadgeGallery(
                    badges = summary.badges
                )
            }
        }
    }
}

@Composable
private fun CreatorIdentitySummaryHeader(
    profile: CreatorProfile,
    onBack: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("creator_identity_header_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        border = BorderStroke(1.dp, StageSurfaceHighlight)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(profile.avatarColorHex).copy(alpha = 0.35f),
                            StageSurface
                        )
                    )
                )
                .padding(16.dp)
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(StageSurfaceElevated)
                        .testTag("creator_stats_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(Color(profile.avatarColorHex))
                        .border(2.5.dp, ElectricCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!profile.avatarUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = profile.avatarUrl,
                            contentDescription = "${profile.artistName} avatar",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = profile.artistName.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = profile.artistName,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("creator_stats_artist_name")
                        )
                        if (profile.verifiedBadge) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified Creator",
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = profile.stageTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = NeonVioletLight,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .background(
                                if (profile.primaryCategory == PerformanceCategory.MUSIC) NeonViolet.copy(alpha = 0.25f)
                                else SpokenWordPrimary.copy(alpha = 0.25f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = profile.primaryCategory.displayName,
                            color = if (profile.primaryCategory == PerformanceCategory.MUSIC) NeonVioletLight else ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (profile.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = profile.bio,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
}

/**
 * Creator Statistics Card showcasing Total Performances, Average Audience Rating (with 5-star
 * indicator & review count), Unlocked Badges count, secondary stage metrics, and accessible
 * spectator star rating controls.
 */
@Composable
fun CreatorStatisticsCard(
    summary: CreatorStatisticsSummary,
    allowRating: Boolean = false,
    userRating: Int = 0,
    onRate: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(
                        GoldCrown.copy(alpha = 0.55f),
                        NeonViolet.copy(alpha = 0.45f),
                        ElectricCyan.copy(alpha = 0.4f)
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
                .testTag("creator_statistics_card")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_stats_section_badge),
                        color = ElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.profile_stats_section_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .background(GoldCrown.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (summary.leaderboardRank != null) {
                            "#${summary.leaderboardRank} ${stringResource(R.string.profile_stat_stage_rank)}"
                        } else {
                            "${stringResource(R.string.profile_stat_unranked)} • ${stringResource(R.string.profile_stat_stage_rank)}"
                        },
                        color = GoldCrownLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("creator_stat_rank_badge")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Hero Telemetry Row: Total Performances & Average Audience Rating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Hero 1: Total Performances
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StageSurfaceElevated)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                        .testTag("creator_stat_total_performances_card")
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${summary.totalPerformances}",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                modifier = Modifier.testTag("creator_stat_total_performances_value")
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.profile_stat_total_performances),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${summary.distinctStylesCount.coerceAtLeast(if (summary.totalPerformances > 0) 1 else 0)} styles • ${summary.totalSessionsHosted} live",
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Hero 2: Average Audience Rating
                Box(
                    modifier = Modifier
                        .weight(1.15f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(StageSurfaceElevated)
                        .border(1.dp, GoldCrown.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .padding(12.dp)
                        .testTag("creator_stat_average_rating_card")
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = summary.formattedAverageRating,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = GoldCrown,
                                modifier = Modifier.testTag("creator_stat_average_rating_value")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val filledStars = summary.averageAudienceRating.roundToInt().coerceIn(0, 5)
                            Row {
                                repeat(5) { idx ->
                                    Icon(
                                        imageVector = if (idx < filledStars) Icons.Default.Star else Icons.Outlined.StarOutline,
                                        contentDescription = null,
                                        tint = if (idx < filledStars) GoldCrown else TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.profile_stat_average_rating),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                R.string.profile_rating_reviews_format,
                                summary.totalAudienceRatingsCount
                            ),
                            color = TextMuted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("creator_stat_ratings_count")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary 4-Metric Stage Telemetry Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(StageSurfaceElevated.copy(alpha = 0.65f))
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                SecondaryTelemetryItem(
                    label = stringResource(R.string.profile_stat_badges_earned),
                    value = "${summary.unlockedBadgesCount}/${summary.totalBadgesCount}",
                    tint = GoldCrownLight,
                    icon = Icons.Default.WorkspacePremium,
                    valueTestTag = "creator_stat_badges_unlocked_value"
                )
                SecondaryTelemetryItem(
                    label = stringResource(R.string.profile_stat_total_votes),
                    value = "${summary.totalVotes}",
                    tint = ElectricCyan,
                    icon = Icons.Default.HowToVote,
                    valueTestTag = "creator_stat_votes_value"
                )
                SecondaryTelemetryItem(
                    label = stringResource(R.string.profile_stat_total_views),
                    value = "${summary.totalViews}",
                    tint = NeonVioletLight,
                    icon = Icons.Default.Visibility,
                    valueTestTag = "creator_stat_views_value"
                )
                SecondaryTelemetryItem(
                    label = stringResource(R.string.profile_stat_total_likes),
                    value = "${summary.totalLikes}",
                    tint = HotMagenta,
                    icon = Icons.Default.Favorite,
                    valueTestTag = "creator_stat_likes_value"
                )
            }

            // Interactive 5-Star Spectator Rating Controls (48dp+ touch targets, responsive on compact widths)
            if (allowRating && onRate != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StageSurfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("creator_interactive_rating_bar")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.profile_rate_creator_prompt),
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (userRating > 0) {
                            Text(
                                text = "$userRating / 5 ★",
                                color = GoldCrown,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (star in 1..5) {
                            IconButton(
                                onClick = { onRate(star) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(48.dp)
                                    .testTag("rate_creator_star_$star")
                            ) {
                                Icon(
                                    imageVector = if (star <= userRating) Icons.Default.Star else Icons.Outlined.StarOutline,
                                    contentDescription = stringResource(R.string.profile_rate_star_desc, star),
                                    tint = if (star <= userRating) GoldCrown else TextMuted,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryTelemetryItem(
    label: String,
    value: String,
    tint: Color,
    icon: ImageVector,
    valueTestTag: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            color = tint,
            modifier = Modifier.testTag(valueTestTag)
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Achievement Badge Gallery displaying unlocked vs in-progress stage milestones in a
 * non-lazy 2-column medallion grid (safe inside LazyColumn or LazyVerticalGrid) with
 * filter chips and an interactive badge inspection dialog.
 */
@Composable
fun AchievementBadgeGallery(
    badges: List<AchievementBadge>,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(BadgeFilterMode.ALL) }
    var inspectedBadge by remember { mutableStateOf<AchievementBadge?>(null) }

    val unlockedCount = badges.count { it.isUnlocked }
    val inProgressCount = badges.size - unlockedCount
    val filteredBadges = remember(badges, selectedFilter) {
        when (selectedFilter) {
            BadgeFilterMode.ALL -> badges
            BadgeFilterMode.UNLOCKED -> badges.filter { it.isUnlocked }
            BadgeFilterMode.IN_PROGRESS -> badges.filter { !it.isUnlocked }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring())
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(20.dp))
            .testTag("achievement_badge_gallery"),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Section Header & Unlocked Counter Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.badge_gallery_section_badge),
                        color = GoldCrown,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.badge_gallery_section_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .background(NeonViolet.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                        .border(1.dp, NeonVioletLight.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.badge_gallery_unlocked_counter,
                            unlockedCount,
                            badges.size
                        ),
                        color = NeonVioletLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.testTag("badge_gallery_unlocked_count")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.badge_gallery_section_subtitle),
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips (48dp+ touch targets)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("badge_filter_row")
            ) {
                item {
                    FilterChip(
                        selected = selectedFilter == BadgeFilterMode.ALL,
                        onClick = { selectedFilter = BadgeFilterMode.ALL },
                        label = {
                            Text(
                                text = stringResource(R.string.badge_filter_all, badges.size),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
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
                            .testTag("badge_filter_all")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == BadgeFilterMode.UNLOCKED,
                        onClick = { selectedFilter = BadgeFilterMode.UNLOCKED },
                        label = {
                            Text(
                                text = stringResource(R.string.badge_filter_unlocked, unlockedCount),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = GoldCrown.copy(alpha = 0.28f),
                            selectedLabelColor = GoldCrownLight,
                            containerColor = StageSurfaceElevated,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("badge_filter_unlocked")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedFilter == BadgeFilterMode.IN_PROGRESS,
                        onClick = { selectedFilter = BadgeFilterMode.IN_PROGRESS },
                        label = {
                            Text(
                                text = stringResource(R.string.badge_filter_in_progress, inProgressCount),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.24f),
                            selectedLabelColor = ElectricCyan,
                            containerColor = StageSurfaceElevated,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("badge_filter_in_progress")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredBadges.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StageSurfaceElevated)
                        .padding(20.dp)
                        .testTag("badge_gallery_empty_state"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedFilter == BadgeFilterMode.UNLOCKED) {
                            stringResource(R.string.badge_empty_unlocked_tip)
                        } else {
                            stringResource(R.string.badge_empty_in_progress_tip)
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // 2-Column Medallion Grid built with chunked(2) to avoid nested lazy scroll crashes
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    filteredBadges.chunked(2).forEach { rowBadges ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowBadges.forEach { badge ->
                                AchievementBadgeCard(
                                    badge = badge,
                                    onClick = { inspectedBadge = badge },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowBadges.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    inspectedBadge?.let { badge ->
        AchievementBadgeDetailDialog(
            badge = badge,
            onDismiss = { inspectedBadge = null }
        )
    }
}

@Composable
private fun AchievementBadgeCard(
    badge: AchievementBadge,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryAccent = Color(badge.rarity.primaryColorHex)
    val secondaryAccent = Color(badge.rarity.secondaryColorHex)

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 148.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StageSurfaceElevated)
            .drawBehind {
                if (badge.isUnlocked) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                primaryAccent.copy(alpha = 0.24f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.22f, size.height * 0.22f),
                            radius = size.maxDimension * 0.65f
                        )
                    )
                }
            }
            .border(
                width = 1.dp,
                brush = if (badge.isUnlocked) {
                    Brush.linearGradient(listOf(primaryAccent.copy(alpha = 0.7f), secondaryAccent.copy(alpha = 0.45f)))
                } else {
                    Brush.linearGradient(listOf(StageSurfaceHighlight, StageSurfaceHighlight))
                },
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag("badge_card_${badge.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Medallion Emblem
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (badge.isUnlocked) {
                                Brush.linearGradient(listOf(primaryAccent.copy(alpha = 0.3f), secondaryAccent.copy(alpha = 0.25f)))
                            } else {
                                Brush.linearGradient(listOf(StageSurface, StageSurface))
                            }
                        )
                        .border(
                            1.5.dp,
                            if (badge.isUnlocked) primaryAccent else StageSurfaceHighlight,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badge.iconType.toImageVector(),
                        contentDescription = badge.title,
                        tint = if (badge.isUnlocked) primaryAccent else TextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Rarity or Lock Pill
                Box(
                    modifier = Modifier
                        .background(
                            if (badge.isUnlocked) primaryAccent.copy(alpha = 0.18f) else StageSurface,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!badge.isUnlocked) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = if (badge.isUnlocked) badge.rarity.label.uppercase() else stringResource(R.string.badge_status_in_progress),
                            color = if (badge.isUnlocked) primaryAccent else TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = badge.title,
                color = if (badge.isUnlocked) TextPrimary else TextSecondary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("badge_title_${badge.id}")
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = badge.description,
                color = if (badge.isUnlocked) TextSecondary else TextMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { badge.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = if (badge.isUnlocked) primaryAccent else ElectricCyan.copy(alpha = 0.7f),
                trackColor = StageSurface,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (badge.isUnlocked) stringResource(R.string.badge_status_unlocked) else badge.progressLabel,
                color = if (badge.isUnlocked) primaryAccent else TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("badge_status_${badge.id}")
            )
        }
    }
}

@Composable
private fun AchievementBadgeDetailDialog(
    badge: AchievementBadge,
    onDismiss: () -> Unit
) {
    val primaryAccent = Color(badge.rarity.primaryColorHex)
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, primaryAccent.copy(alpha = 0.6f), RoundedCornerShape(22.dp))
                .testTag("badge_detail_dialog"),
            colors = CardDefaults.cardColors(containerColor = StageSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(primaryAccent.copy(alpha = 0.2f))
                        .border(2.dp, primaryAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badge.iconType.toImageVector(),
                        contentDescription = badge.title,
                        tint = primaryAccent,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "${badge.rarity.label.uppercase()} ACHIEVEMENT",
                    color = primaryAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = TextPrimary
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("badge_dialog_title")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = badge.description,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { badge.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = primaryAccent,
                    trackColor = StageSurfaceElevated,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "${badge.progressLabel} • ${if (badge.isUnlocked) stringResource(R.string.badge_status_unlocked) else stringResource(R.string.badge_status_in_progress)}",
                    color = if (badge.isUnlocked) primaryAccent else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryAccent,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("badge_dialog_close_button")
                ) {
                    Text(
                        text = stringResource(R.string.badge_dialog_close_cta),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

private fun BadgeIconType.toImageVector(): ImageVector = when (this) {
    BadgeIconType.STAGE_DEBUT -> Icons.Default.Mic
    BadgeIconType.PROLIFIC_PERFORMER -> Icons.Default.GraphicEq
    BadgeIconType.CROWD_DARLING -> Icons.Default.AutoAwesome
    BadgeIconType.VOTE_MAGNET -> Icons.Default.HowToVote
    BadgeIconType.ARENA_HEADLINER -> Icons.Default.EmojiEvents
    BadgeIconType.LIVE_STAGE_HOST -> Icons.Default.LiveTv
    BadgeIconType.GENRE_CHAMELEON -> Icons.Default.Palette
    BadgeIconType.VISUAL_VISIONARY -> Icons.Default.Videocam
}
