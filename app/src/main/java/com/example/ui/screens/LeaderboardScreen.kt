package com.example.ui.screens

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CreatorRanking
import com.example.model.LeaderboardCategoryFilter
import com.example.model.LeaderboardTimePeriod
import com.example.model.LeaderboardUiState
import com.example.model.PerformanceCategory
import com.example.model.VotesAggregationSnapshot
import com.example.ui.components.HorizontalStageSearchBar
import com.example.ui.theme.BronzeMedal
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.GoldCrownLight
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletLight
import com.example.ui.theme.SilverMedal
import com.example.ui.theme.SpokenWordPrimary
import com.example.ui.theme.StageBackground
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.model.GlobalRatingsArenaSummary
import com.example.model.LeaderboardRankingMode
import com.example.ui.components.CompetitiveStandingCard
import com.example.ui.components.GlobalCreatorRatingCard
import com.example.ui.components.GlobalRatingsArenaBanner
import com.example.ui.components.GlobalRatingsPodium
import com.example.ui.components.LeaderboardModeToggleBar

@Composable
fun LeaderboardScreen(
    rankings: List<CreatorRanking>,
    selectedCategory: LeaderboardCategoryFilter,
    selectedTimePeriod: LeaderboardTimePeriod,
    onCategoryChanged: (LeaderboardCategoryFilter) -> Unit,
    onTimePeriodChanged: (LeaderboardTimePeriod) -> Unit,
    onPlayPerformance: (performanceId: String) -> Unit,
    onCreatorClicked: (creatorId: String, performanceId: String) -> Unit,
    selectedRankingMode: LeaderboardRankingMode = LeaderboardRankingMode.CUMULATIVE_RATINGS,
    onRankingModeChanged: (LeaderboardRankingMode) -> Unit = {},
    arenaSummary: GlobalRatingsArenaSummary = GlobalRatingsArenaSummary(),
    userSubmittedRatings: Map<String, Int> = emptyMap(),
    onRateCreator: ((creatorId: String, rating: Float) -> Unit)? = null,
    votesAggregation: VotesAggregationSnapshot = VotesAggregationSnapshot(),
    uiState: LeaderboardUiState? = null,
    isAuthenticated: Boolean = false,
    onCastVoteForCreator: ((CreatorRanking) -> Unit)? = null,
    onSignInClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAlgorithmDetails by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val resolvedMode = when (uiState) {
        is LeaderboardUiState.Success -> uiState.rankingMode
        else -> selectedRankingMode
    }
    val isRatingsMode = resolvedMode == LeaderboardRankingMode.CUMULATIVE_RATINGS

    val resolvedRankings = when (uiState) {
        is LeaderboardUiState.Success -> uiState.rankings
        is LeaderboardUiState.Error -> uiState.fallbackRankings.ifEmpty { rankings }
        else -> rankings
    }

    val resolvedArenaSummary = remember(uiState, arenaSummary, resolvedRankings) {
        when {
            uiState is LeaderboardUiState.Success && uiState.arenaSummary.totalRankedCreators > 0 ->
                uiState.arenaSummary
            arenaSummary.totalRankedCreators > 0 -> arenaSummary
            resolvedRankings.isNotEmpty() -> {
                val totalStars = resolvedRankings.sumOf { it.cumulativeRatingPoints.toDouble() }.toFloat()
                val totalCount = resolvedRankings.sumOf { it.totalAudienceRatingsCount }
                val avg = if (totalCount > 0) {
                    (totalStars / totalCount.toFloat()).coerceIn(1f, 5f)
                } else {
                    resolvedRankings.map { it.averageAudienceRating }.average().toFloat()
                }
                GlobalRatingsArenaSummary(
                    totalCumulativeRatingStars = totalStars,
                    globalWeightedAverageRating = avg,
                    totalAudienceRatingsCount = totalCount,
                    totalRankedCreators = resolvedRankings.size
                )
            }
            else -> GlobalRatingsArenaSummary()
        }
    }

    val resolvedAggregation = when (uiState) {
        is LeaderboardUiState.Success -> uiState.aggregationSnapshot
        else -> votesAggregation
    }

    val resolvedAuthenticated = when (uiState) {
        is LeaderboardUiState.Success -> uiState.isAuthenticated
        else -> isAuthenticated
    }

    val filteredRankings = remember(resolvedRankings, searchQuery) {
        resolvedRankings.filter { ranking ->
            searchQuery.isBlank() ||
                ranking.artistName.contains(searchQuery, ignoreCase = true) ||
                ranking.stageTitle.contains(searchQuery, ignoreCase = true) ||
                ranking.genre.contains(searchQuery, ignoreCase = true) ||
                ranking.topPerformanceTitle.contains(searchQuery, ignoreCase = true) ||
                ranking.competitiveTier.label.contains(searchQuery, ignoreCase = true)
        }
    }

    val myCurrentRanking = remember(resolvedRankings) {
        resolvedRankings.find { it.isCurrentUser }
    }

    val totalLeaderboardVotes = remember(resolvedRankings) {
        resolvedRankings.sumOf { it.totalVotes }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StageBackground)
            .testTag("global_leaderboard_root"),
        contentAlignment = Alignment.TopCenter
    ) {
        if (uiState is LeaderboardUiState.Loading && resolvedRankings.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("leaderboard_loading_indicator"),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = if (isRatingsMode) GoldCrown else ElectricCyan)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 16.dp)
                    .testTag("leaderboard_rankings_list"),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Global Header & Formula Info Button
                item(key = "header_banner") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(
                                            Brush.linearGradient(
                                                if (isRatingsMode) {
                                                    listOf(GoldCrown, HotMagenta)
                                                } else {
                                                    listOf(NeonViolet, ElectricCyan)
                                                }
                                            ),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isRatingsMode) Icons.Default.Star else Icons.Default.EmojiEvents,
                                        contentDescription = "Global Leaderboard Emblem",
                                        tint = if (isRatingsMode) Color.Black else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.leaderboard_global_title),
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = TextPrimary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("leaderboard_header_title")
                                    )
                                    Text(
                                        text = stringResource(
                                            if (isRatingsMode) R.string.leaderboard_global_subtitle_ratings
                                            else R.string.leaderboard_global_subtitle_votes
                                        ),
                                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { showAlgorithmDetails = !showAlgorithmDetails },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("algorithm_info_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Scoring Breakdown",
                                    tint = if (showAlgorithmDetails) GoldCrown else TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Mode Switcher: Cumulative Audience Ratings vs Stage Votes
                        LeaderboardModeToggleBar(
                            selectedMode = resolvedMode,
                            onModeChanged = onRankingModeChanged
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (isRatingsMode) {
                            GlobalRatingsArenaBanner(
                                summary = resolvedArenaSummary,
                                isAuthenticated = resolvedAuthenticated,
                                onSignInClick = onSignInClick
                            )
                        } else {
                            VotesCollectionStatusCard(
                                totalLeaderboardVotes = totalLeaderboardVotes,
                                aggregation = resolvedAggregation,
                                isAuthenticated = resolvedAuthenticated,
                                onSignInClick = onSignInClick
                            )
                        }

                        AnimatedVisibility(visible = showAlgorithmDetails) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp)
                                    .testTag("leaderboard_algorithm_card"),
                                colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = if (isRatingsMode) {
                                            stringResource(R.string.leaderboard_algorithm_title_ratings)
                                        } else {
                                            "Real-Time 'votes' Aggregation Engine"
                                        },
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = if (isRatingsMode) GoldCrownLight else ElectricCyan
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (isRatingsMode) {
                                            stringResource(
                                                R.string.leaderboard_algorithm_body_ratings,
                                                selectedTimePeriod.label
                                            )
                                        } else {
                                            "Vote counts are streamed live from the Cloud Firestore 'votes' collection, summed per performer for ${selectedTimePeriod.label}, and ranked dynamically from highest to lowest total votes."
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = TextSecondary,
                                            lineHeight = 18.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Category Tabs + Time Period Filter Chips + Search Bar
                item(key = "filters_and_search") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val categories = LeaderboardCategoryFilter.entries
                        TabRow(
                            selectedTabIndex = categories.indexOf(selectedCategory),
                            containerColor = StageSurface,
                            contentColor = TextPrimary,
                            modifier = Modifier.clip(RoundedCornerShape(14.dp)),
                            indicator = { tabPositions ->
                                val index = categories.indexOf(selectedCategory)
                                if (index < tabPositions.size) {
                                    TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[index]),
                                        color = if (isRatingsMode) GoldCrown else ElectricCyan,
                                        height = 3.dp
                                    )
                                }
                            }
                        ) {
                            categories.forEach { category ->
                                val isSelected = selectedCategory == category
                                val activeColor = if (isRatingsMode) GoldCrownLight else ElectricCyan
                                Tab(
                                    selected = isSelected,
                                    onClick = { onCategoryChanged(category) },
                                    text = {
                                        Text(
                                            text = category.label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) activeColor else TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag("leaderboard_tab_${category.name}")
                                )
                            }
                        }

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            items(LeaderboardTimePeriod.entries) { period ->
                                val selected = selectedTimePeriod == period
                                FilterChip(
                                    selected = selected,
                                    onClick = { onTimePeriodChanged(period) },
                                    label = {
                                        Text(
                                            text = period.label,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    },
                                    leadingIcon = if (selected) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = if (isRatingsMode) GoldCrown else NeonViolet,
                                        selectedLabelColor = if (isRatingsMode) Color.Black else Color.White,
                                        selectedLeadingIconColor = if (isRatingsMode) Color.Black else Color.White,
                                        containerColor = StageSurfaceElevated,
                                        labelColor = TextSecondary
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.testTag("time_period_chip_${period.name}")
                                )
                            }
                        }

                        HorizontalStageSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            placeholderText = "Search creator, tier, song title, or genre...",
                            testTag = "leaderboard_search_bar",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (uiState is LeaderboardUiState.Error) {
                    item(key = "error_banner") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = HotMagenta.copy(alpha = 0.14f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = uiState.message,
                                color = HotMagenta,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // 3. Competitive Standing Chase Card (fosters competitive engagement)
                if (isRatingsMode && searchQuery.isBlank() && myCurrentRanking != null) {
                    item(key = "competitive_standing") {
                        CompetitiveStandingCard(
                            myRanking = myCurrentRanking,
                            onViewMyProfile = {
                                onCreatorClicked(myCurrentRanking.creatorId, myCurrentRanking.topPerformanceId)
                            }
                        )
                    }
                }

                // 4. Top-3 Podium Card
                if (searchQuery.isBlank() && filteredRankings.size >= 3) {
                    item(key = "podium_section") {
                        if (isRatingsMode) {
                            GlobalRatingsPodium(
                                first = filteredRankings[0],
                                second = filteredRankings[1],
                                third = filteredRankings[2],
                                onCreatorClicked = { creatorId, perfId -> onCreatorClicked(creatorId, perfId) },
                                onPlayClicked = { perfId -> onPlayPerformance(perfId) }
                            )
                        } else {
                            PodiumDisplay(
                                first = filteredRankings[0],
                                second = filteredRankings[1],
                                third = filteredRankings[2],
                                onCreatorClicked = { creatorId, perfId -> onCreatorClicked(creatorId, perfId) },
                                onPlayClicked = { perfId -> onPlayPerformance(perfId) }
                            )
                        }
                    }
                }

                // 5. Standings Section Header
                item(key = "standings_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "Search Results (${filteredRankings.size})"
                            } else if (isRatingsMode) {
                                stringResource(R.string.leaderboard_list_header_ratings)
                            } else {
                                stringResource(R.string.leaderboard_list_header_votes)
                            },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = stringResource(
                                if (isRatingsMode) R.string.leaderboard_sort_badge_ratings
                                else R.string.leaderboard_sort_badge_votes
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isRatingsMode) GoldCrownLight else ElectricCyan,
                                fontWeight = FontWeight.SemiBold
                            ),
                            maxLines = 1
                        )
                    }
                }

                // 6. Ranked Creators List or Empty State
                if (filteredRankings.isEmpty()) {
                    item(key = "empty_state") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp)
                                .testTag("leaderboard_empty_state"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Equalizer,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) {
                                        "No creators match \"$searchQuery\""
                                    } else {
                                        stringResource(R.string.leaderboard_empty_ratings_tip)
                                    },
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredRankings,
                        key = { "${resolvedMode.name}_${it.creatorId}" }
                    ) { ranking ->
                        if (isRatingsMode) {
                            GlobalCreatorRatingCard(
                                ranking = ranking,
                                submittedUserStars = userSubmittedRatings[ranking.creatorId],
                                onPlayClicked = { onPlayPerformance(ranking.topPerformanceId) },
                                onCardClicked = { onCreatorClicked(ranking.creatorId, ranking.topPerformanceId) },
                                onRateCreator = onRateCreator?.let { rateCallback ->
                                    { stars -> rateCallback(ranking.creatorId, stars) }
                                }
                            )
                        } else {
                            CreatorRankingCard(
                                ranking = ranking,
                                onPlayClicked = { onPlayPerformance(ranking.topPerformanceId) },
                                onCardClicked = { onCreatorClicked(ranking.creatorId, ranking.topPerformanceId) },
                                onVoteClicked = onCastVoteForCreator?.let { callback ->
                                    { callback(ranking) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VotesCollectionStatusCard(
    totalLeaderboardVotes: Int,
    aggregation: VotesAggregationSnapshot,
    isAuthenticated: Boolean,
    onSignInClick: (() -> Unit)?
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_votes_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (aggregation.isLiveCloudSynced) {
                    ElectricCyan.copy(alpha = 0.45f)
                } else {
                    StageSurfaceHighlight
                },
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("votes_aggregation_banner"),
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
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            if (aggregation.isLiveCloudSynced) {
                                ElectricCyan.copy(alpha = 0.16f)
                            } else {
                                NeonViolet.copy(alpha = 0.2f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (aggregation.isLiveCloudSynced) {
                            Icons.Default.CloudDone
                        } else {
                            Icons.Default.CloudSync
                        },
                        contentDescription = "Firestore votes collection status",
                        tint = if (aggregation.isLiveCloudSynced) ElectricCyan else NeonVioletLight,
                        modifier = Modifier
                            .size(18.dp)
                            .alpha(if (aggregation.isLiveCloudSynced) pulseAlpha else 1f)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (aggregation.isLiveCloudSynced) {
                                "LIVE 'votes' COLLECTION"
                            } else if (isAuthenticated) {
                                "CONNECTING 'votes' STREAM"
                            } else {
                                "SIGN IN FOR CLOUD 'votes' SYNC"
                            },
                            color = if (aggregation.isLiveCloudSynced) ElectricCyan else GoldCrownLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val votesSummaryText = buildString {
                        append("$totalLeaderboardVotes total votes")
                        if (aggregation.totalCountedVotes > 0) {
                            append(" • +${aggregation.totalCountedVotes} live cast")
                        }
                        if (aggregation.uniqueVotersCount > 0) {
                            append(" • ${aggregation.uniqueVotersCount} voters")
                        }
                    }

                    Text(
                        text = votesSummaryText,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (!isAuthenticated && onSignInClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSignInClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("leaderboard_signin_sync_button")
                ) {
                    Text(
                        text = "Sign In",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            } else if (aggregation.totalVoteRecords > 0) {
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
                        tint = ElectricCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${aggregation.totalVoteRecords}",
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

@Composable
private fun PodiumDisplay(
    first: CreatorRanking,
    second: CreatorRanking,
    third: CreatorRanking,
    onCreatorClicked: (String, String) -> Unit,
    onPlayClicked: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(GoldCrown.copy(alpha = 0.6f), NeonViolet.copy(alpha = 0.2f))),
                RoundedCornerShape(24.dp)
            )
            .testTag("leaderboard_podium_card"),
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
                    text = "Top Vote Leaders",
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
                // 2nd Place (Silver)
                PodiumColumn(
                    ranking = second,
                    medalColor = SilverMedal,
                    heightDp = 132,
                    placeLabel = "2nd",
                    onCreatorClicked = onCreatorClicked,
                    onPlayClicked = onPlayClicked,
                    modifier = Modifier.weight(1f)
                )

                // 1st Place (Gold - Elevated)
                PodiumColumn(
                    ranking = first,
                    medalColor = GoldCrown,
                    heightDp = 160,
                    placeLabel = "1st",
                    isGold = true,
                    onCreatorClicked = onCreatorClicked,
                    onPlayClicked = onPlayClicked,
                    modifier = Modifier.weight(1f)
                )

                // 3rd Place (Bronze)
                PodiumColumn(
                    ranking = third,
                    medalColor = BronzeMedal,
                    heightDp = 114,
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
private fun PodiumColumn(
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
        modifier = modifier
            .clickable { onCreatorClicked(ranking.creatorId, ranking.topPerformanceId) }
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
            text = "${ranking.totalVotes} votes",
            color = medalColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
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
                            medalColor.copy(alpha = if (isGold) 0.35f else 0.2f),
                            StageSurfaceElevated
                        )
                    )
                )
                .border(
                    1.dp,
                    medalColor.copy(alpha = 0.5f),
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
                    text = if (ranking.category == PerformanceCategory.MUSIC) "Music" else "Spoken",
                    color = TextMuted,
                    fontSize = 10.sp,
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

@Composable
fun CreatorRankingCard(
    ranking: CreatorRanking,
    onPlayClicked: () -> Unit,
    onCardClicked: () -> Unit,
    onVoteClicked: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
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

    val animatedShare by animateFloatAsState(
        targetValue = (ranking.voteSharePercent / 100f).coerceIn(0.03f, 1f),
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "vote_share_${ranking.creatorId}"
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
            // Main Top Row: Rank Badge + Avatar + Creator Info + Trend + Play
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
                                modifier = Modifier.weight(1f, fill = false)
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
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${ranking.stageTitle} • ${ranking.genre}",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                                    fontSize = 11.sp,
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
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "+${ranking.rankChange}",
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        ranking.rankChange < 0 -> {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = "Rank down",
                                tint = HotMagenta,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${ranking.rankChange}",
                                color = HotMagenta,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
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

            // Real-Time Vote Share & Cloud 'votes' Tally Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StageSurfaceElevated.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
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

                    val shareText = if (ranking.cloudVotesCount > 0) {
                        String.format(
                            Locale.US,
                            "+%d live • %.1f%% share",
                            ranking.cloudVotesCount,
                            ranking.voteSharePercent
                        )
                    } else {
                        String.format(Locale.US, "%.1f%% vote share", ranking.voteSharePercent)
                    }

                    Text(
                        text = shareText,
                        color = if (ranking.cloudVotesCount > 0) ElectricCyan else TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
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
                            .fillMaxWidth(animatedShare)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    if (ranking.rank == 1) {
                                        listOf(GoldCrown, HotMagenta)
                                    } else {
                                        listOf(NeonViolet, ElectricCyan)
                                    }
                                )
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Score Breakdown & Quick Real-Time Vote Row
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
                    MetricChip(
                        icon = Icons.Default.HowToVote,
                        label = "${ranking.totalVotes}",
                        subLabel = "Votes",
                        tint = ElectricCyan
                    )

                    MetricChip(
                        icon = Icons.Default.Visibility,
                        label = "${ranking.totalViews}",
                        subLabel = "Views",
                        tint = NeonVioletLight
                    )

                    MetricChip(
                        icon = Icons.Default.Favorite,
                        label = "${ranking.totalLikes}",
                        subLabel = "Likes",
                        tint = HotMagenta
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (onVoteClicked != null) {
                    Button(
                        onClick = onVoteClicked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (ranking.isVotedByCurrentUser) {
                                ElectricCyan.copy(alpha = 0.22f)
                            } else {
                                NeonViolet
                            },
                            contentColor = if (ranking.isVotedByCurrentUser) {
                                ElectricCyan
                            } else {
                                Color.White
                            }
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("leaderboard_vote_button_${ranking.creatorId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HowToVote,
                            contentDescription = "Cast real-time vote for ${ranking.artistName}",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "+1 Vote",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${ranking.totalScore}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                color = GoldCrownLight
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = "STAGE PTS",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subLabel: String,
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
                text = label,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = subLabel,
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
    }
}
