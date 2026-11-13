package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.CreatorRanking
import com.example.model.GlobalRatingsArenaSummary
import com.example.model.LeaderboardRankingMode
import com.example.model.PerformanceCategory
import com.example.ui.theme.BronzeMedal
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.GoldCrownLight
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletLight
import com.example.ui.theme.SilverMedal
import com.example.ui.theme.SpokenWordPrimary
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Segmented mode switcher allowing users to toggle the Global Leaderboard between
 * Cumulative Audience Ratings (`CUMULATIVE_RATINGS`) and Stage Votes (`STAGE_VOTES`).
 */
@Composable
fun LeaderboardModeToggleBar(
    selectedMode: LeaderboardRankingMode,
    onModeChanged: (LeaderboardRankingMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StageSurfaceElevated)
            .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(16.dp))
            .padding(4.dp)
            .testTag("leaderboard_mode_toggle_bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        LeaderboardRankingMode.entries.forEach { mode ->
            val isSelected = selectedMode == mode
            val isRatings = mode == LeaderboardRankingMode.CUMULATIVE_RATINGS
            val label = stringResource(
                if (isRatings) R.string.leaderboard_mode_ratings else R.string.leaderboard_mode_votes
            )
            val activeBrush = if (isRatings) {
                Brush.horizontalGradient(listOf(GoldCrown, HotMagenta))
            } else {
                Brush.horizontalGradient(listOf(NeonViolet, ElectricCyan))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(activeBrush)
                        } else {
                            Modifier.background(Color.Transparent)
                        }
                    )
                    .clickable(role = Role.Tab) { onModeChanged(mode) }
                    .padding(horizontal = 10.dp)
                    .testTag("leaderboard_mode_${mode.name}"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isRatings) Icons.Default.Star else Icons.Default.HowToVote,
                        contentDescription = null,
                        tint = if (isSelected) {
                            if (isRatings) Color.Black else Color.White
                        } else {
                            TextSecondary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = label,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = if (isSelected) {
                            if (isRatings) Color.Black else Color.White
                        } else {
                            TextSecondary
                        },
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Global Arena telemetry card summarizing cumulative audience star points, weighted average
 * rating, and total spectator reviews across all ranked creators.
 */
@Composable
fun GlobalRatingsArenaBanner(
    summary: GlobalRatingsArenaSummary,
    isAuthenticated: Boolean,
    onSignInClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "global_star_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "star_pulse_alpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(GoldCrown.copy(alpha = 0.6f), HotMagenta.copy(alpha = 0.45f))
                ),
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("global_ratings_arena_banner"),
        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GoldCrown.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = GoldCrown,
                        modifier = Modifier
                            .size(19.dp)
                            .alpha(pulseAlpha)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.leaderboard_arena_badge_ratings),
                        color = GoldCrownLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.leaderboard_arena_summary_format,
                            summary.formattedTotalCumulativeStars,
                            summary.formattedGlobalAverage,
                            summary.formattedTotalRatingsCount
                        ),
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("global_ratings_arena_summary")
                    )
                }
            }

            if (!isAuthenticated && onSignInClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSignInClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GoldCrown,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .height(38.dp)
                        .testTag("leaderboard_signin_sync_button")
                ) {
                    Text(
                        text = stringResource(R.string.auth_tab_sign_in),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            } else if (summary.totalRankedCreators > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(StageSurface, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = GoldCrown,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${summary.totalRankedCreators}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Personal competitive engagement card showing the signed-in user's global rank, cumulative
 * star points, tier badge, and exact star gap needed to overtake the rival immediately ahead.
 */
@Composable
fun CompetitiveStandingCard(
    myRanking: CreatorRanking,
    onViewMyProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tierColor = Color(myRanking.competitiveTier.colorHex)
    val chaseProgress = remember(myRanking.cumulativeRatingPoints, myRanking.pointsToNextRank) {
        if (myRanking.rank <= 1 || myRanking.pointsToNextRank <= 0f) {
            1f
        } else {
            val targetTotal = myRanking.cumulativeRatingPoints + myRanking.pointsToNextRank
            (myRanking.cumulativeRatingPoints / targetTotal.coerceAtLeast(1f)).coerceIn(0.08f, 0.98f)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(listOf(ElectricCyan.copy(alpha = 0.7f), tierColor.copy(alpha = 0.6f))),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(role = Role.Button, onClick = onViewMyProfile)
            .testTag("competitive_standing_card"),
        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.TrendingUp,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.leaderboard_standing_badge),
                        color = ElectricCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                Box(
                    modifier = Modifier
                        .background(tierColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .border(1.dp, tierColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = myRanking.competitiveTier.label,
                        color = tierColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Global #${myRanking.rank} • ${myRanking.artistName}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${myRanking.formattedCumulativeStars} ★ pts",
                    color = GoldCrownLight,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(StageSurfaceHighlight)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(chaseProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(Brush.horizontalGradient(listOf(ElectricCyan, GoldCrown)))
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val chaseText = if (myRanking.rank <= 1 || myRanking.nextRankArtistName == null) {
                stringResource(R.string.leaderboard_standing_leader_text)
            } else {
                stringResource(
                    R.string.leaderboard_standing_chase_format,
                    myRanking.formattedPointsToNextRank,
                    myRanking.rank - 1,
                    myRanking.nextRankArtistName,
                    myRanking.fiveStarRatingsNeededToAdvance
                )
            }

            Text(
                text = chaseText,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("competitive_standing_chase_text")
            )
        }
    }
}

/**
 * Top-3 Gold, Silver, and Bronze podium highlighting creators' cumulative audience star ratings.
 */
@Composable
fun GlobalRatingsPodium(
    first: CreatorRanking,
    second: CreatorRanking,
    third: CreatorRanking,
    onCreatorClicked: (String, String) -> Unit,
    onPlayClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(GoldCrown.copy(alpha = 0.65f), HotMagenta.copy(alpha = 0.25f))),
                RoundedCornerShape(24.dp)
            )
            .testTag("global_ratings_podium_card"),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = GoldCrownLight,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.leaderboard_podium_title_ratings),
                    color = GoldCrownLight,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                RatingsPodiumColumn(
                    ranking = second,
                    medalColor = SilverMedal,
                    heightDp = 136,
                    placeLabel = "2nd",
                    onCreatorClicked = onCreatorClicked,
                    onPlayClicked = onPlayClicked,
                    modifier = Modifier.weight(1f)
                )
                RatingsPodiumColumn(
                    ranking = first,
                    medalColor = GoldCrown,
                    heightDp = 164,
                    placeLabel = "1st",
                    isGold = true,
                    onCreatorClicked = onCreatorClicked,
                    onPlayClicked = onPlayClicked,
                    modifier = Modifier.weight(1f)
                )
                RatingsPodiumColumn(
                    ranking = third,
                    medalColor = BronzeMedal,
                    heightDp = 120,
                    placeLabel = "3rd",
                    onCreatorClicked = onCreatorClicked,
                    onPlayClicked = onPlayClicked,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RatingsPodiumColumn(
    ranking: CreatorRanking,
    medalColor: Color,
    heightDp: Int,
    placeLabel: String,
    isGold: Boolean = false,
    onCreatorClicked: (String, String) -> Unit,
    onPlayClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable { onCreatorClicked(ranking.creatorId, ranking.topPerformanceId) }
    ) {
        Box(
            modifier = Modifier
                .size(if (isGold) 54.dp else 46.dp)
                .clip(CircleShape)
                .background(Color(ranking.avatarColorHex))
                .border(2.dp, medalColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = ranking.artistName.take(2).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = if (isGold) 16.sp else 13.sp,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = ranking.artistName,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Text(
            text = "${ranking.formattedCumulativeStars} ★",
            color = medalColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("podium_cumulative_stars_${ranking.rank}")
        )

        Text(
            text = "${ranking.formattedAverageRating}★ (${ranking.totalAudienceRatingsCount})",
            color = TextSecondary,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            medalColor.copy(alpha = if (isGold) 0.36f else 0.2f),
                            StageSurfaceElevated
                        )
                    )
                )
                .border(
                    1.dp,
                    medalColor.copy(alpha = 0.55f),
                    RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Text(
                    text = placeLabel,
                    color = medalColor,
                    fontSize = if (isGold) 20.sp else 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
                Text(
                    text = ranking.competitiveTier.label,
                    color = TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                IconButton(
                    onClick = { onPlayClicked(ranking.topPerformanceId) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play ${ranking.artistName} top performance",
                        tint = medalColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Ranked creator card for the Global Cumulative Audience Ratings Leaderboard, featuring
 * cumulative star progress, competitive tier badge, and an expandable 1–5★ quick-rate bar.
 */
@Composable
fun GlobalCreatorRatingCard(
    ranking: CreatorRanking,
    submittedUserStars: Int?,
    onPlayClicked: () -> Unit,
    onCardClicked: () -> Unit,
    onRateCreator: ((Float) -> Unit)?,
    modifier: Modifier = Modifier
) {
    var showQuickRateBar by remember { mutableStateOf(false) }

    val rankBadgeColor = when (ranking.rank) {
        1 -> GoldCrown
        2 -> SilverMedal
        3 -> BronzeMedal
        else -> StageSurfaceHighlight
    }
    val rankTextColor = when (ranking.rank) {
        1, 2, 3 -> Color.Black
        else -> TextPrimary
    }
    val tierColor = Color(ranking.competitiveTier.colorHex)

    val animatedRatingShare by animateFloatAsState(
        targetValue = (ranking.ratingSharePercent / 100f).coerceIn(0.05f, 1f),
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "rating_share_${ranking.creatorId}"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (ranking.isCurrentUser) 1.5.dp else 1.dp,
                color = if (ranking.isCurrentUser) ElectricCyan else StageSurfaceHighlight,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onCardClicked() }
            .testTag("creator_ranking_card_${ranking.rank}"),
        colors = CardDefaults.cardColors(
            containerColor = if (ranking.isCurrentUser) StageSurfaceElevated else StageSurface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(rankBadgeColor, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#${ranking.rank}",
                            color = rankTextColor,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(modifier = Modifier.size(44.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.TopStart)
                                .clip(CircleShape)
                                .background(Color(ranking.avatarColorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = ranking.artistName.take(2).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 1
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(
                                    if (ranking.category == PerformanceCategory.MUSIC) NeonViolet else SpokenWordPrimary
                                )
                                .border(1.5.dp, StageSurface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (ranking.category == PerformanceCategory.MUSIC) {
                                    Icons.Default.MusicNote
                                } else {
                                    Icons.Default.Mic
                                },
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = ranking.artistName,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .testTag("ranking_artist_name_${ranking.creatorId}")
                            )
                            if (ranking.isCurrentUser) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(ElectricCyan, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "YOU",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(tierColor.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                                    .testTag("creator_tier_badge_${ranking.creatorId}")
                            ) {
                                Text(
                                    text = ranking.competitiveTier.label,
                                    color = tierColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = ranking.genre,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    when {
                        ranking.rankChange == 999 -> {
                            Box(
                                modifier = Modifier
                                    .background(HotMagenta.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "NEW",
                                    color = HotMagenta,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                        ranking.rankChange > 0 -> {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Rank up",
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "+${ranking.rankChange}",
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        ranking.rankChange < 0 -> {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Rank down",
                                tint = HotMagenta,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${ranking.rankChange}",
                                color = HotMagenta,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Rank unchanged",
                                tint = TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onPlayClicked,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("play_rank_perf_${ranking.rank}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(NeonViolet.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Listen to ${ranking.artistName} top performance",
                                tint = NeonVioletLight,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Cumulative Audience Rating Progress Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StageSurfaceElevated.copy(alpha = 0.72f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\"${ranking.topPerformanceTitle}\"",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "${ranking.formattedCumulativeStars} ★ pts",
                        color = GoldCrownLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        modifier = Modifier.testTag("creator_cumulative_stars_${ranking.creatorId}")
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(StageSurfaceHighlight)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedRatingShare)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    if (ranking.rank == 1) {
                                        listOf(GoldCrown, HotMagenta)
                                    } else {
                                        listOf(GoldCrownLight, ElectricCyan)
                                    }
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rating Metrics & Competitive Rate Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RatingMetricItem(
                        icon = Icons.Default.Star,
                        value = "${ranking.formattedAverageRating}★",
                        label = "Avg Rating",
                        tint = GoldCrown
                    )
                    RatingMetricItem(
                        icon = Icons.Default.People,
                        value = "${ranking.totalAudienceRatingsCount}",
                        label = "Ratings",
                        tint = ElectricCyan
                    )
                    RatingMetricItem(
                        icon = Icons.Default.Bolt,
                        value = "${ranking.totalVotes}",
                        label = "Votes",
                        tint = NeonVioletLight
                    )
                }

                if (onRateCreator != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showQuickRateBar = !showQuickRateBar },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (submittedUserStars != null) {
                                GoldCrown.copy(alpha = 0.22f)
                            } else {
                                GoldCrown
                            },
                            contentColor = if (submittedUserStars != null) {
                                GoldCrownLight
                            } else {
                                Color.Black
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("leaderboard_rate_button_${ranking.creatorId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rate ${ranking.artistName}",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (submittedUserStars != null) {
                                stringResource(R.string.leaderboard_rated_cta, submittedUserStars)
                            } else {
                                stringResource(R.string.leaderboard_rate_cta)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Expandable 1–5★ Competitive Quick-Rate Strip
            if (onRateCreator != null) {
                AnimatedVisibility(visible = showQuickRateBar) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .background(StageSurfaceElevated, RoundedCornerShape(12.dp))
                            .border(1.dp, GoldCrown.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("quick_rate_strip_${ranking.creatorId}")
                    ) {
                        Text(
                            text = stringResource(R.string.leaderboard_quick_rate_prompt, ranking.artistName),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (1..5).forEach { star ->
                                val isFilled = (submittedUserStars ?: 0) >= star
                                IconButton(
                                    onClick = {
                                        onRateCreator(star.toFloat())
                                        showQuickRateBar = false
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("leaderboard_quick_star_${ranking.creatorId}_$star")
                                ) {
                                    Icon(
                                        imageVector = if (isFilled) Icons.Default.Star else Icons.Outlined.StarOutline,
                                        contentDescription = stringResource(R.string.profile_rate_star_desc, star),
                                        tint = GoldCrown,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RatingMetricItem(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = label,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
