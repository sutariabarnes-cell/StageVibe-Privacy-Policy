package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Whatshot
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.model.ActiveVotingState
import com.example.model.LiveEvent
import com.example.model.LiveSession
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.PerformanceCategoryCatalog
import com.example.model.PerformanceStyle
import com.example.model.PerformanceVoteCount
import com.example.model.Poll
import com.example.model.SynthAudioType
import com.example.model.VoteTier
import com.example.model.performanceStyle
import com.example.model.toLiveEvent
import com.example.ui.components.HorizontalStageSearchBar
import com.example.ui.components.LiveEventsList
import com.example.ui.components.PerformanceCategoryFilterChipBar
import com.example.ui.components.PerformanceStyleBadge
import com.example.ui.components.PerformanceVisualizer
import com.example.ui.components.VotingPollCard
import com.example.ui.components.accentColor
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
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

/**
 * Main screen UI displaying currently active performers with buttons for audience
 * members to cast their real-time votes.
 */
@Composable
fun ActivePerformersScreen(
    performances: List<Performance>,
    featuredPoll: Poll? = null,
    activeLiveSessions: List<LiveSession> = emptyList(),
    activePerformanceId: String? = null,
    isPlaying: Boolean = false,
    activeVotingState: ActiveVotingState? = null,
    pinnedPerformanceIds: Set<String> = emptySet(),
    onToggleOfflinePin: ((Performance) -> Unit)? = null,
    onPlayPerformance: (performanceId: String) -> Unit,
    onToggleLike: (performanceId: String) -> Unit,
    onVoteForPerformance: (performanceId: String) -> Unit,
    onCastTierVote: ((performanceId: String, tier: VoteTier) -> Unit)? = null,
    onVoteInPoll: (pollId: String, candidateId: String) -> Unit = { _, _ -> },
    onCheerPerformance: ((performanceId: String) -> Unit)? = null,
    onCreatorProfileClicked: (creatorId: String) -> Unit = {},
    onNavigateToPolls: () -> Unit = {},
    onNavigateToLive: () -> Unit = {},
    onJoinLiveEvent: ((LiveEvent) -> Unit)? = null,
    onJoinLiveSession: ((LiveSession) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedSectionCategory by remember { mutableStateOf<PerformanceCategory?>(null) }
    var selectedStyleFilter by remember { mutableStateOf<PerformanceStyle?>(null) }
    var showOnlyTopVoted by remember { mutableStateOf(false) }
    var showOnlyPinnedOffline by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val styleCounts = remember(performances, activeLiveSessions, selectedSectionCategory) {
        val counts = mutableMapOf<PerformanceStyle, Int>()
        activeLiveSessions
            .filter { it.isLive && (selectedSectionCategory == null || it.category == selectedSectionCategory) }
            .forEach { session ->
                val style = session.performanceStyle
                counts[style] = (counts[style] ?: 0) + 1
            }
        performances
            .filter { selectedSectionCategory == null || it.category == selectedSectionCategory }
            .forEach { perf ->
                val style = perf.performanceStyle
                counts[style] = (counts[style] ?: 0) + 1
            }
        counts.toMap()
    }

    val sectionTotalCount = remember(performances, activeLiveSessions, selectedSectionCategory) {
        activeLiveSessions.count { it.isLive && (selectedSectionCategory == null || it.category == selectedSectionCategory) } +
            performances.count { selectedSectionCategory == null || it.category == selectedSectionCategory }
    }

    val filteredLiveSessions = remember(activeLiveSessions, selectedSectionCategory, selectedStyleFilter, searchQuery) {
        activeLiveSessions
            .filter { it.isLive }
            .filter { session ->
                PerformanceCategoryCatalog.matchesFilter(
                    itemStyle = session.performanceStyle,
                    itemParentCategory = session.category,
                    selectedStyle = selectedStyleFilter,
                    selectedCategory = selectedSectionCategory
                )
            }
            .filter { session ->
                searchQuery.isBlank() ||
                    session.title.contains(searchQuery, ignoreCase = true) ||
                    session.creatorName.contains(searchQuery, ignoreCase = true) ||
                    session.genre.contains(searchQuery, ignoreCase = true) ||
                    session.performanceStyle.displayName.contains(searchQuery, ignoreCase = true) ||
                    session.category.displayName.contains(searchQuery, ignoreCase = true)
            }
    }

    val filteredPerformances = performances
        .filter { perf ->
            PerformanceCategoryCatalog.matchesFilter(
                itemStyle = perf.performanceStyle,
                itemParentCategory = perf.category,
                selectedStyle = selectedStyleFilter,
                selectedCategory = selectedSectionCategory
            )
        }
        .filter {
            searchQuery.isBlank() ||
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artistName.contains(searchQuery, ignoreCase = true) ||
                it.genre.contains(searchQuery, ignoreCase = true) ||
                it.performanceStyle.displayName.contains(searchQuery, ignoreCase = true) ||
                it.category.displayName.contains(searchQuery, ignoreCase = true)
        }
        .filter { perf ->
            !showOnlyPinnedOffline || pinnedPerformanceIds.contains(perf.id)
        }
        .let { list ->
            if (showOnlyTopVoted) {
                list.sortedByDescending { perf ->
                    activeVotingState?.getVoteCount(perf.id, perf.votesCount) ?: perf.votesCount
                }
            } else {
                list
            }
        }

    val totalStageVotes = (activeVotingState?.totalActiveVotes?.takeIf { it > 0 }
        ?: performances.sumOf { it.votesCount }).coerceAtLeast(1)
    val topRankedPerformers = performances
        .sortedByDescending { perf ->
            activeVotingState?.getVoteCount(perf.id, perf.votesCount) ?: perf.votesCount
        }
        .take(3)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(StageBackground)
            .testTag("active_performers_screen"),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Live Arena Stage Banner with Audience Voting Callout
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            ) {
                val context = LocalContext.current
                val heroRequest = remember(context) {
                    ImageRequest.Builder(context)
                        .data(R.drawable.img_stage_hero)
                        .size(800, 448)
                        .build()
                }
                AsyncImage(
                    model = heroRequest,
                    contentDescription = "Live Stage Hero",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Dark gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    StageBackground.copy(alpha = 0.5f),
                                    StageBackground
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    // Pulsing Live Status Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(HotMagenta, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LivePulsingDot()
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "LIVE STAGE ARENA",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${performances.size} Active Performers",
                                color = ElectricCyan,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Real-Time Audience Voting",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 22.sp
                        )
                    )

                    Text(
                        text = "Tap VOTE to back your favorite music & spoken word creators in real time.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = ElectricCyan,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }

        // Real-Time Leaderboard Mini-Ticker
        if (topRankedPerformers.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("live_voting_leaderboard_ticker"),
                    colors = CardDefaults.cardColors(containerColor = StageSurface),
                    shape = RoundedCornerShape(14.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(listOf(NeonViolet.copy(alpha = 0.4f), ElectricCyan.copy(alpha = 0.4f)))
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = null,
                                    tint = GoldCrown,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "CURRENT AUDIENCE VOTE LEADERS",
                                    color = GoldCrown,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$totalStageVotes Total Votes",
                                color = TextMuted,
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            topRankedPerformers.forEachIndexed { index, perf ->
                                val liveVotes = activeVotingState?.getVoteCount(perf.id, perf.votesCount) ?: perf.votesCount
                                LeaderMiniChip(
                                    rank = index + 1,
                                    artistName = perf.artistName,
                                    votes = liveVotes,
                                    onClick = { onCreatorProfileClicked(perf.artistId) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Horizontal Search Bar for Events & Stage Performers
        item(key = "discover_search_section") {
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                HorizontalStageSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholderText = "Search Hip-Hop, Rap, Poetry, Jazz, Spoken Word...",
                    testTag = "discover_search_bar",
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Section Switcher (All Stages, Music, Spoken Word) + Category Style Filter Chip Bar
        item(key = "discover_section_switcher") {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("discover_section_bar"),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                item(key = "section_all") {
                    FilterChip(
                        selected = selectedSectionCategory == null,
                        onClick = {
                            selectedSectionCategory = null
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.discover_section_all),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
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
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("discover_section_all")
                    )
                }
                item(key = "section_music") {
                    FilterChip(
                        selected = selectedSectionCategory == PerformanceCategory.MUSIC,
                        onClick = {
                            selectedSectionCategory = PerformanceCategory.MUSIC
                            if (selectedStyleFilter != null && selectedStyleFilter?.parentCategory != PerformanceCategory.MUSIC) {
                                selectedStyleFilter = null
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.discover_section_music),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonViolet,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = StageSurfaceElevated,
                            labelColor = TextSecondary,
                            iconColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("discover_section_music")
                    )
                }
                item(key = "section_spoken_word") {
                    FilterChip(
                        selected = selectedSectionCategory == PerformanceCategory.SPOKEN_WORD,
                        onClick = {
                            selectedSectionCategory = PerformanceCategory.SPOKEN_WORD
                            if (selectedStyleFilter != null && selectedStyleFilter?.parentCategory != PerformanceCategory.SPOKEN_WORD) {
                                selectedStyleFilter = null
                            }
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.discover_section_spoken_word),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpokenWordPrimary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = StageSurfaceElevated,
                            labelColor = TextSecondary,
                            iconColor = SpokenWordPrimary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("discover_section_spoken_word")
                    )
                }
                item(key = "section_offline_pinned") {
                    FilterChip(
                        selected = showOnlyPinnedOffline,
                        onClick = { showOnlyPinnedOffline = !showOnlyPinnedOffline },
                        label = {
                            Text(
                                text = "Offline (${pinnedPerformanceIds.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.24f),
                            selectedLabelColor = ElectricCyan,
                            selectedLeadingIconColor = ElectricCyan,
                            containerColor = StageSurfaceElevated,
                            labelColor = TextSecondary,
                            iconColor = ElectricCyan
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("discover_offline_filter_chip")
                    )
                }
            }
        }

        // Main Screen Performance Category Filter Chip Bar (All, Rap, Poetry, Jazz, Spoken Word, Hip-Hop, etc.)
        item(key = "main_category_filter_section") {
            val sectionAllLabel = when (selectedSectionCategory) {
                PerformanceCategory.MUSIC -> stringResource(R.string.category_filter_all_music)
                PerformanceCategory.SPOKEN_WORD -> stringResource(R.string.category_filter_all_spoken)
                null -> stringResource(R.string.category_filter_all)
            }
            PerformanceCategoryFilterChipBar(
                selectedStyle = selectedStyleFilter,
                onSelectStyle = { nextStyle ->
                    selectedStyleFilter = nextStyle
                    if (nextStyle != null) {
                        showOnlyTopVoted = false
                    }
                },
                categories = PerformanceCategoryCatalog.filterTagsForCategory(selectedSectionCategory),
                allLabel = sectionAllLabel,
                countsByStyle = styleCounts,
                totalCount = sectionTotalCount,
                showTopVotedChip = true,
                isTopVotedSelected = showOnlyTopVoted,
                onToggleTopVoted = { showOnlyTopVoted = !showOnlyTopVoted },
                contentPadding = PaddingValues(horizontal = 16.dp),
                rowTestTag = "main_category_filter_bar",
                chipTagPrefix = "category_chip"
            )
        }

        // Real-Time Firestore Live Music & Spoken Word Events Feed (Synced with Category Filter Chip Bar)
        item(key = "firestore_live_events_section") {
            LiveEventsList(
                useLazyColumn = false,
                searchQuery = searchQuery,
                showFilterBar = false,
                externalSelectedCategory = selectedSectionCategory,
                externalSelectedStyle = selectedStyleFilter,
                onExternalSelectStyle = { selectedStyleFilter = it },
                onEventSelected = { event ->
                    onJoinLiveEvent?.invoke(event) ?: onNavigateToLive()
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // Active Arena Stage Events List (Categorized by Rap, Poetry, Jazz, Spoken Word, etc.)
        if (activeLiveSessions.any { it.isLive }) {
            item(key = "main_stage_events_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .testTag("main_stage_events_list"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Radio,
                                contentDescription = null,
                                tint = HotMagenta,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.main_events_section_title),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary
                                )
                            )
                        }
                        Text(
                            text = "${filteredLiveSessions.size} Live",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("main_stage_events_count")
                        )
                    }

                    if (filteredLiveSessions.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("main_stage_events_empty"),
                            colors = CardDefaults.cardColors(containerColor = StageSurface),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.main_events_empty_filtered,
                                    selectedStyleFilter?.displayName ?: "this category"
                                ),
                                color = TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        filteredLiveSessions.forEach { session ->
                            StageSessionEventCard(
                                session = session,
                                onJoin = {
                                    when {
                                        onJoinLiveSession != null -> onJoinLiveSession(session)
                                        onJoinLiveEvent != null -> onJoinLiveEvent(session.toLiveEvent())
                                        else -> onNavigateToLive()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Spotlight Voting Battle Poll (Featured Head-to-Head Showdown)
        if (featuredPoll != null) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Whatshot, contentDescription = null, tint = HotMagenta, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "FEATURED LIVE BATTLE",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    color = GoldCrown,
                                    fontSize = 13.sp
                                )
                            )
                        }
                        androidx.compose.material3.TextButton(onClick = onNavigateToPolls) {
                            Text("All Polls", color = ElectricCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    VotingPollCard(
                        poll = featuredPoll,
                        onVote = onVoteInPoll,
                        onPlayPerformance = onPlayPerformance
                    )
                }
            }
        }

        // Active Performers Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active Performers on Stage",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                )

                Text(
                    text = "Cast Your Vote",
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // List of Currently Active Performers with Real-time Voting Buttons
        if (filteredPerformances.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    colors = CardDefaults.cardColors(containerColor = StageSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No performers match \"$searchQuery\"" else "No active performers in this category",
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        if (searchQuery.isNotBlank() || selectedSectionCategory != null || selectedStyleFilter != null || showOnlyTopVoted || showOnlyPinnedOffline) {
                            Button(
                                onClick = {
                                    searchQuery = ""
                                    selectedSectionCategory = null
                                    selectedStyleFilter = null
                                    showOnlyTopVoted = false
                                    showOnlyPinnedOffline = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                            ) {
                                Text("Reset Filters", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else {
            items(filteredPerformances, key = { it.id }) { performance ->
                val perfVoteState = activeVotingState?.getPerformanceVoteState(performance.id)
                val effectiveVotes = perfVoteState?.totalVotes ?: performance.votesCount
                val voteSharePercentage = perfVoteState?.voteSharePercent?.toInt()
                    ?: ((effectiveVotes.toFloat() / totalStageVotes) * 100).toInt()
                val isCurrentTrackPlaying = (activePerformanceId == performance.id && isPlaying)

                ActivePerformerCard(
                    performance = performance,
                    voteSharePercentage = voteSharePercentage,
                    voteState = perfVoteState,
                    isPlayingTrack = isCurrentTrackPlaying,
                    isPinnedOffline = pinnedPerformanceIds.contains(performance.id),
                    onToggleOfflinePin = onToggleOfflinePin?.let { cb -> { cb(performance) } },
                    onPlay = { onPlayPerformance(performance.id) },
                    onLike = { onToggleLike(performance.id) },
                    onVote = { onVoteForPerformance(performance.id) },
                    onSuperVote = onCastTierVote?.let { castFn ->
                        { castFn(performance.id, VoteTier.SUPER_VOTE) }
                    },
                    onCheer = { onCheerPerformance?.invoke(performance.id) },
                    onCreatorClick = { onCreatorProfileClicked(performance.artistId) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

/**
 * Primary DiscoverScreen delegating to ActivePerformersScreen for backward compatibility.
 */
@Composable
fun DiscoverScreen(
    performances: List<Performance>,
    featuredPoll: Poll?,
    activeLiveSessions: List<LiveSession> = emptyList(),
    activePerformanceId: String? = null,
    isPlaying: Boolean = false,
    activeVotingState: ActiveVotingState? = null,
    pinnedPerformanceIds: Set<String> = emptySet(),
    onToggleOfflinePin: ((Performance) -> Unit)? = null,
    onPlayPerformance: (performanceId: String) -> Unit,
    onToggleLike: (performanceId: String) -> Unit,
    onVoteInPoll: (pollId: String, candidateId: String) -> Unit,
    onVoteForPerformance: (performanceId: String) -> Unit = {},
    onCastTierVote: ((performanceId: String, tier: VoteTier) -> Unit)? = null,
    onCheerPerformance: ((performanceId: String) -> Unit)? = null,
    onCreatorProfileClicked: (creatorId: String) -> Unit,
    onNavigateToPolls: () -> Unit,
    onNavigateToLive: () -> Unit = {},
    onJoinLiveEvent: ((LiveEvent) -> Unit)? = null,
    onJoinLiveSession: ((LiveSession) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    ActivePerformersScreen(
        performances = performances,
        featuredPoll = featuredPoll,
        activeLiveSessions = activeLiveSessions,
        activePerformanceId = activePerformanceId,
        isPlaying = isPlaying,
        activeVotingState = activeVotingState,
        pinnedPerformanceIds = pinnedPerformanceIds,
        onToggleOfflinePin = onToggleOfflinePin,
        onPlayPerformance = onPlayPerformance,
        onToggleLike = onToggleLike,
        onVoteForPerformance = onVoteForPerformance,
        onCastTierVote = onCastTierVote,
        onVoteInPoll = onVoteInPoll,
        onCheerPerformance = onCheerPerformance,
        onCreatorProfileClicked = onCreatorProfileClicked,
        onNavigateToPolls = onNavigateToPolls,
        onNavigateToLive = onNavigateToLive,
        onJoinLiveEvent = onJoinLiveEvent,
        onJoinLiveSession = onJoinLiveSession,
        modifier = modifier
    )
}

@Composable
private fun StageSessionEventCard(
    session: LiveSession,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val style = session.performanceStyle
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onJoin() }
            .testTag("stage_event_card_${session.id}"),
        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(style.accentColor().copy(alpha = 0.7f), ElectricCyan.copy(alpha = 0.45f)))
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(HotMagenta, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(8.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    PerformanceStyleBadge(
                        style = style,
                        textTestTag = "stage_event_style_${session.id}"
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(ElectricCyan.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${session.spectatorCount} participants",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.testTag("stage_event_participants_${session.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("stage_event_title_${session.id}")
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Hosted by ${session.creatorName} • ${session.genre}",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onJoin,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HotMagenta,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("join_stage_event_${session.id}")
                ) {
                    Text("Join Stage", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Active Performer Card with prominent real-time voting buttons, live audio preview,
 * vote momentum progress bar, and instant audience reaction affordances.
 */
@Composable
fun ActivePerformerCard(
    performance: Performance,
    voteSharePercentage: Int,
    voteState: PerformanceVoteCount? = null,
    isPlayingTrack: Boolean = false,
    isPinnedOffline: Boolean = false,
    onToggleOfflinePin: (() -> Unit)? = null,
    onPlay: () -> Unit,
    onLike: () -> Unit,
    onVote: () -> Unit,
    onSuperVote: (() -> Unit)? = null,
    onCheer: (() -> Unit)?,
    onCreatorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isVoted = voteState?.isVotedByCurrentUser ?: performance.isVotedByMe
    val displayedVotes = voteState?.totalVotes ?: performance.votesCount
    val isStageLeader = voteState?.isLeading == true

    val animatedVoteContainerColor by animateColorAsState(
        targetValue = if (isVoted) GoldCrown else ElectricCyan,
        animationSpec = tween(durationMillis = 300),
        label = "vote_btn_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isVoted) 1.5.dp else 1.dp,
                brush = if (isVoted) {
                    Brush.horizontalGradient(listOf(GoldCrown, HotMagenta))
                } else {
                    Brush.horizontalGradient(listOf(StageSurfaceHighlight, NeonViolet.copy(alpha = 0.3f)))
                },
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("performer_card_${performance.id}"),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Live Status + Category + Genre + Theme Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Pulsing Active Tag
                    Box(
                        modifier = Modifier
                            .background(
                                if (performance.isLiveOnStage) HotMagenta.copy(alpha = 0.25f)
                                else ElectricCyan.copy(alpha = 0.15f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = if (performance.isLiveOnStage) HotMagenta else ElectricCyan,
                                modifier = Modifier.size(8.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (performance.isLiveOnStage) "LIVE ON STAGE" else "ACTIVE NOW",
                                color = if (performance.isLiveOnStage) HotMagenta else ElectricCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    // Performance Style Badge (Rap, Poetry, Jazz, Spoken Word, etc.)
                    PerformanceStyleBadge(
                        style = performance.performanceStyle,
                        textTestTag = "performer_style_${performance.id}"
                    )

                    Text(
                        text = "• ${performance.genre}",
                        color = TextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Video / Uploaded media badge
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (performance.hasVideo) {
                        Box(
                            modifier = Modifier
                                .background(GoldCrown.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("video_badge_${performance.id}")
                        ) {
                            Text(
                                text = "Video",
                                color = GoldCrown,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (performance.audioType == SynthAudioType.UPLOADED_MUSIC || !performance.customAudioPath.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .background(ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Uploaded",
                                color = ElectricCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Artist Info & Play Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onCreatorClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Artist Avatar Initials
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NeonViolet),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = performance.avatarInitials,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = performance.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "By ${performance.artistName}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ElectricCyan,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = ElectricCyan,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Audio Preview Trigger Button
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isPlayingTrack) {
                                Brush.linearGradient(listOf(ElectricCyan, NeonViolet))
                            } else {
                                Brush.linearGradient(listOf(NeonViolet, HotMagenta))
                            }
                        )
                        .testTag("play_feed_button_${performance.id}")
                ) {
                    Icon(
                        imageVector = if (isPlayingTrack) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlayingTrack) "Pause performance" else "Play performance preview",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Lyrics / Script snippet excerpt
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StageSurfaceElevated, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "“${performance.lyricsOrScript.trim().lines().take(2).joinToString("\n")}”",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Real-Time Vote Share / Progress Momentum Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.HowToVote,
                            contentDescription = null,
                            tint = if (isVoted || isStageLeader) GoldCrown else ElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$displayedVotes Real-Time Votes",
                            color = if (isVoted || isStageLeader) GoldCrown else ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isStageLeader) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(GoldCrown.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "👑 #1",
                                    color = GoldCrown,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "$voteSharePercentage% Stage Share",
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { (voteSharePercentage / 100f).coerceIn(0.05f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isVoted || isStageLeader) GoldCrown else ElectricCyan,
                    trackColor = StageSurfaceHighlight
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AUDIENCE ACTION ROW: Real-time Vote Button + Super Vote + Cheer + Like
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PRIMARY REAL-TIME VOTE BUTTON
                Button(
                    onClick = onVote,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = animatedVoteContainerColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("vote_button_${performance.id}")
                ) {
                    Icon(
                        imageVector = if (isVoted) Icons.Default.CheckCircle else Icons.Default.HowToVote,
                        contentDescription = if (isVoted) "Vote Cast" else "Cast Vote",
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isVoted) "VOTED ✓" else "CAST VOTE",
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 0.4.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onSuperVote != null) {
                    Button(
                        onClick = onSuperVote,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonViolet,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("super_vote_button_${performance.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Cast Super Vote",
                            tint = GoldCrown,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "+5",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                }

                // Quick Cheer Reaction Button (+1 Cheer)
                Button(
                    onClick = { onCheer?.invoke() ?: onVote() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StageSurfaceElevated,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("cheer_button_${performance.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Whatshot,
                        contentDescription = "Cheer",
                        tint = HotMagenta,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${performance.cheersCount}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Like Button
                IconButton(
                    onClick = onLike,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StageSurfaceElevated)
                        .testTag("like_button_${performance.id}")
                ) {
                    Icon(
                        imageVector = if (performance.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (performance.isLikedByMe) HotMagenta else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Sub-metrics Row (Views, Offline Replay Pin & Social Engagement)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${performance.viewsCount} listeners • ${performance.likesCount} favorites",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }

                if (onToggleOfflinePin != null) {
                    Row(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isPinnedOffline) ElectricCyan.copy(alpha = 0.16f)
                                else StageSurfaceElevated
                            )
                            .clickable { onToggleOfflinePin() }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("discover_offline_pin_${performance.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (isPinnedOffline) ElectricCyan else TextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = stringResource(
                                if (isPinnedOffline) R.string.offline_pinned_badge
                                else R.string.offline_pin_cta
                            ),
                            color = if (isPinnedOffline) ElectricCyan else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        text = "${performance.likesCount} favorites",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderMiniChip(
    rank: Int,
    artistName: String,
    votes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(StageSurfaceElevated)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "#$rank",
                    color = when (rank) {
                        1 -> GoldCrown
                        2 -> ElectricCyan
                        else -> HotMagenta
                    },
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = artistName,
                    color = TextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "$votes votes",
                color = TextSecondary,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun LivePulsingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = alpha))
    )
}
