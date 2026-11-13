package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.ui.components.HorizontalStageSearchBar
import com.example.data.FirestoreDataSource
import com.example.data.FirestoreService
import com.example.data.PerformanceRepository
import com.example.model.CreatorAchievementEngine
import com.example.model.CreatorProfile
import com.example.model.CreatorSessionStats
import com.example.model.LiveSession
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.SynthAudioType
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
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface UserProfileUiState {
    object Loading : UserProfileUiState
    data class Success(
        val profile: CreatorProfile,
        val tracks: List<Performance>,
        val sessionStats: CreatorSessionStats,
        val totalVotes: Int,
        val totalViews: Int,
        val totalLikes: Int,
        val isCurrentUser: Boolean
    ) : UserProfileUiState
    data class Error(val message: String) : UserProfileUiState
}

/**
 * UserProfile Composable
 *
 * Retrieves and displays a performer's profile from Cloud Firestore:
 * - Creator Bio and Stage Identity
 * - Custom Avatar (URL image or stylized initials with stage neon glow)
 * - Past Live Stage Session Statistics and Session History
 * - Interactive Content Portfolio Grid with Audio Playback and Placeholder Slots
 */
@Composable
fun UserProfile(
    creatorId: String,
    leaderboardRank: Int? = null,
    onBack: (() -> Unit)? = null,
    onPlayTrack: ((performanceId: String) -> Unit)? = null,
    onUploadClick: (() -> Unit)? = null,
    onEditProfileClick: (() -> Unit)? = null,
    onRateCreator: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val firestoreService = remember { FirestoreService(context) }
    val firestoreDs = remember { FirestoreDataSource(context) }
    val localRepo = remember { PerformanceRepository(context) }

    var uiState by remember(creatorId) { mutableStateOf<UserProfileUiState>(UserProfileUiState.Loading) }
    var selectedCategoryFilter by remember { mutableStateOf<PerformanceCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isFollowing by remember { mutableStateOf(false) }
    var userRatingSelection by remember { mutableStateOf(0) }

    // Retrieve performer bio, tracks, and past session statistics via FirestoreService with local caching fallback
    LaunchedEffect(creatorId) {
        uiState = UserProfileUiState.Loading

        try {
            // First display local/cached profile immediately for zero latency
            val fallbackProfile = localRepo.getCreatorProfile(creatorId)
            val fallbackTracks = localRepo.getPerformancesForCreator(creatorId)
            val fallbackSessionStats = localRepo.getCreatorSessionStats(creatorId)

            uiState = UserProfileUiState.Success(
                profile = fallbackProfile,
                tracks = fallbackTracks,
                sessionStats = fallbackSessionStats,
                totalVotes = fallbackTracks.sumOf { it.votesCount },
                totalViews = fallbackTracks.sumOf { it.viewsCount },
                totalLikes = fallbackTracks.sumOf { it.likesCount },
                isCurrentUser = (creatorId == localRepo.currentUserId)
            )

            // Listen to live updates from Cloud Firestore ('performers', 'performances', and 'live_sessions')
            combine(
                firestoreService.observePerformerAuthenticated(creatorId),
                firestoreDs.observeCreatorProfile(creatorId),
                firestoreService.observePerformancesByPerformerAuthenticated(creatorId),
                firestoreDs.observeCreatorSessions(creatorId)
            ) { cloudPerformer, legacyProfile, cloudTracks, cloudSessions ->
                val profile = cloudPerformer?.toCreatorProfile(localRepo.currentUserId)
                    ?: legacyProfile
                    ?: fallbackProfile
                val mergedTracks = if (cloudTracks.isNotEmpty()) {
                    val map = fallbackTracks.associateBy { it.id }.toMutableMap()
                    cloudTracks.forEach { map[it.id] = it }
                    map.values.toList()
                } else {
                    fallbackTracks
                }

                val allSessions = if (cloudSessions.isNotEmpty()) {
                    val map = fallbackSessionStats.pastSessions.associateBy { it.id }.toMutableMap()
                    cloudSessions.forEach { map[it.id] = it }
                    map.values.toList().sortedByDescending { it.startedAt }
                } else {
                    fallbackSessionStats.pastSessions
                }

                val totalSessionsCount = maxOf(allSessions.size, cloudPerformer?.totalSessions ?: 0)
                val totalSpectators = allSessions.sumOf { it.spectatorCount }
                val peakViewers = allSessions.maxOfOrNull { it.spectatorCount } ?: 0
                val totalHours = (totalSessionsCount * 45) / 60f

                val sessionStats = CreatorSessionStats(
                    totalSessions = totalSessionsCount,
                    totalSpectatorsReached = totalSpectators,
                    peakViewers = peakViewers,
                    totalLiveHours = totalHours,
                    totalSessionVotes = maxOf(totalSpectators * 2, cloudPerformer?.totalVotes ?: 0),
                    pastSessions = allSessions
                )

                val aggregatedVotes = maxOf(
                    mergedTracks.sumOf { it.votesCount },
                    cloudPerformer?.totalVotes ?: 0
                )

                UserProfileUiState.Success(
                    profile = profile,
                    tracks = mergedTracks,
                    sessionStats = sessionStats,
                    totalVotes = aggregatedVotes,
                    totalViews = mergedTracks.sumOf { it.viewsCount },
                    totalLikes = mergedTracks.sumOf { it.likesCount },
                    isCurrentUser = profile.isCurrentUser || (creatorId == localRepo.currentUserId)
                )
            }.collect { state ->
                uiState = state
            }
        } catch (e: Exception) {
            uiState = UserProfileUiState.Error(e.message ?: "Failed to retrieve performer profile from Firestore")
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StageBackground)
            .testTag("user_profile_container")
    ) {
        when (val state = uiState) {
            is UserProfileUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("user_profile_loading"),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = ElectricCyan,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Retrieving performer data from Firestore...",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            is UserProfileUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("user_profile_error"),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Unable to load performer profile",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = HotMagenta,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.message,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val p = localRepo.getCreatorProfile(creatorId)
                            val t = localRepo.getPerformancesForCreator(creatorId)
                            val s = localRepo.getCreatorSessionStats(creatorId)
                            uiState = UserProfileUiState.Success(
                                profile = p,
                                tracks = t,
                                sessionStats = s,
                                totalVotes = t.sumOf { it.votesCount },
                                totalViews = t.sumOf { it.viewsCount },
                                totalLikes = t.sumOf { it.likesCount },
                                isCurrentUser = (creatorId == localRepo.currentUserId)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }

            is UserProfileUiState.Success -> {
                val profile = state.profile
                val allTracks = state.tracks
                val sessionStats = state.sessionStats
                val statsSummary = remember(profile, allTracks, sessionStats, state.totalVotes, leaderboardRank) {
                    CreatorAchievementEngine.computeSummary(
                        profile = profile,
                        performances = allTracks,
                        sessionStats = sessionStats,
                        leaderboardRank = leaderboardRank,
                        overrideTotalVotes = state.totalVotes
                    )
                }
                val filteredTracks = allTracks.filter { track ->
                    (selectedCategoryFilter == null || track.category == selectedCategoryFilter) &&
                    (searchQuery.isBlank() ||
                        track.title.contains(searchQuery, ignoreCase = true) ||
                        track.genre.contains(searchQuery, ignoreCase = true) ||
                        track.category.displayName.contains(searchQuery, ignoreCase = true))
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. Header Banner & Performer Avatar (Spans full width)
                    item(span = { GridItemSpan(2) }) {
                        UserProfileHeaderBanner(
                            profile = profile,
                            hasActiveLiveSession = sessionStats.pastSessions.any { it.isLive },
                            onBack = onBack,
                            isCurrentUser = state.isCurrentUser,
                            onEditProfileClick = onEditProfileClick,
                            isFollowing = isFollowing,
                            onToggleFollow = { isFollowing = !isFollowing }
                        )
                    }

                    // 2. Performer Bio Card & Social Links (Spans full width)
                    item(span = { GridItemSpan(2) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            // Performer Bio Card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("user_profile_bio"),
                                colors = CardDefaults.cardColors(containerColor = StageSurface),
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StageSurfaceHighlight)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Performer Biography",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = ElectricCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )

                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (profile.primaryCategory == PerformanceCategory.MUSIC) NeonViolet.copy(alpha = 0.2f)
                                                    else SpokenWordPrimary.copy(alpha = 0.2f),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = profile.primaryCategory.displayName,
                                                color = if (profile.primaryCategory == PerformanceCategory.MUSIC) NeonVioletLight else ElectricCyan,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = profile.bio.ifBlank {
                                            "Passionate artist sharing original music and spoken word poetry on the VersePulse stage."
                                        },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = TextPrimary,
                                            lineHeight = 22.sp
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Social Media & Streaming Channels
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SocialChip(label = "Spotify", handle = profile.socialSpotify, color = Color(0xFF1DB954), modifier = Modifier.weight(1f))
                                SocialChip(label = "Instagram", handle = profile.socialInstagram, color = Color(0xFFE1306C), modifier = Modifier.weight(1f))
                                SocialChip(label = "YouTube", handle = profile.socialYoutube, color = Color(0xFFFF0000), modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // 3. Past Session Statistics Section (Spans full width)
                    item(span = { GridItemSpan(2) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PerformerSessionStatisticsCard(
                                stats = sessionStats,
                                modifier = Modifier.testTag("user_profile_session_stats")
                            )
                        }
                    }

                    // 4. Creator Statistics, Audience Rating & Achievement Badge Gallery (Spans full width)
                    item(span = { GridItemSpan(2) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(8.dp))
                            CreatorStatisticsCard(
                                summary = statsSummary,
                                allowRating = !state.isCurrentUser,
                                userRating = userRatingSelection,
                                onRate = { star ->
                                    userRatingSelection = star
                                    val newCount = profile.totalAudienceRatingsCount + 1
                                    val weighted = ((profile.audienceRating * profile.totalAudienceRatingsCount) + star) / newCount
                                    val formattedWeighted = String.format(Locale.US, "%.1f", weighted).toFloat()
                                    uiState = state.copy(
                                        profile = profile.copy(
                                            audienceRating = formattedWeighted,
                                            totalAudienceRatingsCount = newCount
                                        )
                                    )
                                    onRateCreator?.invoke(star.toFloat())
                                },
                                modifier = Modifier.testTag("user_profile_stats")
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            PerformanceAnalyticsChart(
                                performances = allTracks,
                                creatorBaselineRating = profile.audienceRating,
                                onPerformancePointClick = { perfId -> onPlayTrack?.invoke(perfId) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            AchievementBadgeGallery(
                                badges = statsSummary.badges
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            // Content Portfolio Section Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Published Tracks",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(NeonViolet.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${allTracks.size}",
                                            color = NeonVioletLight,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (onUploadClick != null) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = onUploadClick,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = ElectricCyan,
                                            contentColor = Color.Black
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .height(34.dp)
                                            .testTag("profile_upload_button")
                                    ) {
                                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Upload Track", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Horizontal Search Bar for Creator Portfolio
                            HorizontalStageSearchBar(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                placeholderText = "Search published tracks & poems...",
                                testTag = "user_profile_search_bar",
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Category Filter Chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == null,
                                        onClick = { selectedCategoryFilter = null },
                                        label = { Text("All", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonViolet,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == PerformanceCategory.MUSIC,
                                        onClick = { selectedCategoryFilter = PerformanceCategory.MUSIC },
                                        label = { Text("Music", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = NeonViolet,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = selectedCategoryFilter == PerformanceCategory.SPOKEN_WORD,
                                        onClick = { selectedCategoryFilter = PerformanceCategory.SPOKEN_WORD },
                                        label = { Text("Spoken Word", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SpokenWordPrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 5. Content Grid Items
                    items(filteredTracks) { track ->
                        Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                            ContentGridTrackCard(
                                track = track,
                                onPlay = { onPlayTrack?.invoke(track.id) }
                            )
                        }
                    }

                    // 6. Content Grid Placeholders (Slots for upcoming tracks/battles)
                    val placeholderSlotsCount = (4 - filteredTracks.size).coerceAtLeast(2)
                    for (slotIndex in 1..placeholderSlotsCount) {
                        item {
                            Box(modifier = Modifier.padding(start = 16.dp, end = 16.dp)) {
                                ContentPlaceholderGridSlot(
                                    slotIndex = slotIndex,
                                    onClick = { onUploadClick?.invoke() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Performer Header Banner with Avatar representation
 * Fetches from Firestore and displays either image or stylized initials with neon glow.
 */
@Composable
private fun UserProfileHeaderBanner(
    profile: CreatorProfile,
    hasActiveLiveSession: Boolean,
    onBack: (() -> Unit)?,
    isCurrentUser: Boolean,
    onEditProfileClick: (() -> Unit)?,
    isFollowing: Boolean,
    onToggleFollow: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(profile.avatarColorHex).copy(alpha = 0.6f),
                        StageSurfaceElevated,
                        StageBackground
                    )
                )
            )
    ) {
        // Top Navigation & Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("user_profile_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(38.dp))
            }

            if (isCurrentUser) {
                if (onEditProfileClick != null) {
                    Button(
                        onClick = onEditProfileClick,
                        colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceHighlight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("profile_edit_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Bio", fontSize = 11.sp)
                    }
                }
            } else {
                Button(
                    onClick = onToggleFollow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFollowing) StageSurfaceHighlight else HotMagenta,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("profile_follow_button")
                ) {
                    Icon(
                        imageVector = if (isFollowing) Icons.Default.CheckCircle else Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isFollowing) "Following" else "Cheer Artist", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Performer Avatar & Identity Overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Performer Avatar (Firestore URL or Stylized Initials with Live status)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(
                        2.5.dp,
                        if (hasActiveLiveSession) HotMagenta else ElectricCyan,
                        CircleShape
                    )
                    .background(Color(profile.avatarColorHex))
                    .testTag("user_profile_avatar"),
                contentAlignment = Alignment.Center
            ) {
                if (!profile.avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = profile.avatarUrl,
                        contentDescription = "${profile.artistName} Avatar",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = profile.artistName.take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp
                    )
                }

                // Live Stage indicator dot if performer is currently streaming
                if (hasActiveLiveSession) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(HotMagenta)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.artistName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (profile.verifiedBadge) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Artist",
                            tint = ElectricCyan,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Text(
                    text = profile.stageTitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .background(
                                if (profile.primaryCategory == PerformanceCategory.MUSIC) NeonViolet else SpokenWordPrimary,
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = profile.primaryCategory.displayName.uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (hasActiveLiveSession) {
                        Box(
                            modifier = Modifier
                                .background(HotMagenta, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "🔴 LIVE NOW",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Past Session Statistics Card
 * Displays telemetry for stage sessions fetched from Firestore:
 * Total sessions, total audience reached, peak spectators, stage duration, and past session cards.
 */
@Composable
private fun PerformerSessionStatisticsCard(
    stats: CreatorSessionStats,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(HotMagenta.copy(alpha = 0.5f), NeonViolet.copy(alpha = 0.4f))
                ),
                RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = HotMagenta,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Past Session Statistics",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .background(StageSurfaceElevated, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${stats.totalSessions} Sessions Hosted",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4-Card Telemetry Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SessionStatMetric(
                    label = "Sessions",
                    value = "${stats.totalSessions}",
                    tint = HotMagenta,
                    icon = Icons.Default.LiveTv
                )
                SessionStatMetric(
                    label = "Audience",
                    value = "${stats.totalSpectatorsReached}",
                    tint = ElectricCyan,
                    icon = Icons.Default.Group
                )
                SessionStatMetric(
                    label = "Peak Viewers",
                    value = "${stats.peakViewers}",
                    tint = GoldCrown,
                    icon = Icons.Default.Bolt
                )
                SessionStatMetric(
                    label = "Live Hours",
                    value = String.format(Locale.US, "%.1f h", stats.totalLiveHours),
                    tint = NeonVioletLight,
                    icon = Icons.Default.Timer
                )
            }

            // Past Sessions History Cards
            if (stats.pastSessions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Recent Stage Sessions",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(stats.pastSessions) { session ->
                        PastSessionHistoryItem(session = session)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionStatMetric(
    label: String,
    value: String,
    tint: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Black, color = tint)
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
private fun PastSessionHistoryItem(session: LiveSession) {
    val dateStr = remember(session.startedAt) {
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        sdf.format(Date(session.startedAt))
    }

    Card(
        modifier = Modifier
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(12.dp))
            .testTag("past_session_card_${session.id}"),
        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            if (session.isLive) HotMagenta else StageSurfaceHighlight,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (session.isLive) "LIVE NOW" else "COMPLETED",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = dateStr,
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = session.title,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "${session.category.displayName} • ${session.genre}",
                color = TextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Group,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${session.spectatorCount} Spectators",
                    color = ElectricCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}



@Composable
private fun ContentGridTrackCard(
    track: Performance,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlay() }
            .testTag("content_grid_card_${track.id}"),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Visualizer Canvas Thumbnail Placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StageBackground),
                contentAlignment = Alignment.Center
            ) {
                PerformanceVisualizer(
                    theme = track.visualizerTheme,
                    amplitude = 0.45f,
                    isPlaying = false,
                    modifier = Modifier.fillMaxSize()
                )

                // Play Action Circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play track",
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Video / Uploaded Media Pill
                if (track.hasVideo || track.audioType == SynthAudioType.UPLOADED_MUSIC || !track.customAudioPath.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (track.hasVideo) {
                            Box(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Video", color = GoldCrown, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (track.audioType == SynthAudioType.UPLOADED_MUSIC || !track.customAudioPath.isNullOrEmpty()) {
                            Box(
                                modifier = Modifier
                                    .background(ElectricCyan.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Uploaded", color = ElectricCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = track.title,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${track.genre} • ${track.durationSec / 60}:${(track.durationSec % 60).toString().padStart(2, '0')}",
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = TextMuted, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("${track.viewsCount}", color = TextMuted, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = HotMagenta, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("${track.likesCount}", color = TextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ContentPlaceholderGridSlot(
    slotIndex: Int,
    onClick: () -> Unit
) {
    val slotTitle = when (slotIndex % 4) {
        1 -> "Upload Track"
        2 -> "Audio Battle"
        3 -> "Spoken Word"
        else -> "Live Stage"
    }

    val slotIcon = when (slotIndex % 4) {
        1 -> Icons.Default.Upload
        2 -> Icons.Default.HowToVote
        3 -> Icons.Default.Mic
        else -> Icons.Default.Radio
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(StageSurfaceElevated.copy(alpha = 0.5f))
            .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
            .testTag("content_placeholder_slot"),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(NeonViolet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = slotIcon,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = slotTitle,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Empty Stage Slot",
                color = TextMuted,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun SocialChip(
    label: String,
    handle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Text(text = label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text(
                text = handle.ifBlank { "N/A" },
                color = TextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
