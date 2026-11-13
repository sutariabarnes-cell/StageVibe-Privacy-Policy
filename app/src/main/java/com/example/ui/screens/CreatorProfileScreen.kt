package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.VerifiedUser
import com.example.model.UserRole
import com.example.ui.theme.CyanAccent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.model.CreatorAchievementEngine
import com.example.model.CreatorProfile
import com.example.model.CreatorSessionStats
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.security.UploadEncryptionManager
import com.example.ui.components.AchievementBadgeGallery
import com.example.ui.components.CreatorStatisticsCard
import com.example.ui.components.EncryptionControlCard
import com.example.ui.components.HorizontalStageSearchBar
import com.example.ui.components.PerformanceAnalyticsChart
import com.example.ui.components.PrivacyPolicyDialog
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

@Composable
fun CreatorProfileScreen(
    profile: CreatorProfile,
    portfolio: List<Performance>,
    leaderboardRank: Int?,
    onBack: (() -> Unit)? = null,
    onPlayPerformance: (performanceId: String) -> Unit,
    onRateCreator: (rating: Float) -> Unit,
    onSaveProfile: (artistName: String, stageTitle: String, bio: String, primaryCategory: PerformanceCategory, spotify: String, instagram: String, youtube: String, website: String) -> Unit,
    onNavigateToStudio: () -> Unit,
    accountEmail: String? = null,
    isStandaloneAccount: Boolean = false,
    accountRole: UserRole = if (profile.stageTitle.equals("Stage Spectator", ignoreCase = true)) UserRole.SPECTATOR else UserRole.CREATOR,
    isDeletingAccount: Boolean = false,
    onSwitchGoogleAccount: (() -> Unit)? = null,
    onSignOut: (() -> Unit)? = null,
    onDeleteAccount: (() -> Unit)? = null,
    encryptionStatus: UploadEncryptionManager.EncryptionStatusSnapshot? = null,
    onToggleEncryption: ((Boolean) -> Unit)? = null,
    onVerifyAndEncryptVault: (() -> Unit)? = null,
    onOpenEncryptionVault: (() -> Unit)? = null,
    offlineCachedTracks: List<com.example.data.local.OfflineCachedTrackEntity> = emptyList(),
    pinnedPerformanceIds: Set<String> = emptySet(),
    onToggleOfflinePin: ((Performance) -> Unit)? = null,
    onUnpinOfflineTrack: ((String) -> Unit)? = null,
    onClearOfflineCache: (() -> Unit)? = null,
    sessionStats: CreatorSessionStats = CreatorSessionStats(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isEditingProfile by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showPlayReleaseCenterDialog by remember { mutableStateOf(false) }
    val appCheckStatus by com.example.security.AppCheckTelemetry.status.collectAsStateWithLifecycle()
    var selectedPortfolioCategory by remember { mutableStateOf<PerformanceCategory?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var userSelectedRating by remember { mutableStateOf(0) }

    val filteredPortfolio = portfolio
        .filter { selectedPortfolioCategory == null || it.category == selectedPortfolioCategory }
        .filter {
            searchQuery.isBlank() ||
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.genre.contains(searchQuery, ignoreCase = true) ||
            it.category.displayName.contains(searchQuery, ignoreCase = true)
        }

    val statsSummary = remember(profile, portfolio, sessionStats, leaderboardRank) {
        CreatorAchievementEngine.computeSummary(
            profile = profile,
            performances = portfolio,
            sessionStats = sessionStats,
            leaderboardRank = leaderboardRank
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StageBackground)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("creator_profile_list"),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Header Banner & Avatar
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(profile.avatarColorHex).copy(alpha = 0.5f),
                                    StageSurfaceElevated,
                                    StageBackground
                                )
                            )
                        )
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .padding(16.dp)
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .align(Alignment.TopStart)
                                .testTag("profile_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    }

                    if (profile.isCurrentUser) {
                        Button(
                            onClick = { isEditingProfile = true },
                            colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceHighlight),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .testTag("edit_profile_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit Profile", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Creator Identity & Bio Space
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .offset(y = (-40).dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Large Avatar (renders Google photoUrl when available)
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(Color(profile.avatarColorHex))
                                .border(3.dp, StageBackground, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!profile.avatarUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = profile.avatarUrl,
                                    contentDescription = "${profile.artistName} Google Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = profile.artistName.take(2).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 28.sp
                                )
                            }
                        }

                        // Category Pill
                        Box(
                            modifier = Modifier
                                .background(
                                    if (profile.primaryCategory == PerformanceCategory.MUSIC) NeonViolet else SpokenWordPrimary,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (profile.primaryCategory == PerformanceCategory.MUSIC) Icons.Default.MusicNote else Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = profile.primaryCategory.displayName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Artist Name & Stage Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = profile.artistName,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (profile.verifiedBadge) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Verified Creator",
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Text(
                        text = profile.stageTitle,
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = NeonVioletLight,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Bio Space
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = profile.bio,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextPrimary,
                                lineHeight = 20.sp
                            ),
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Social Media Links Space
                    Text(
                        text = "Social Channels & Music Links",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SocialLinkBadge(
                            label = "Spotify",
                            handle = profile.socialSpotify,
                            color = Color(0xFF1DB954),
                            modifier = Modifier.weight(1f)
                        )
                        SocialLinkBadge(
                            label = "Instagram",
                            handle = profile.socialInstagram,
                            color = Color(0xFFE1306C),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SocialLinkBadge(
                            label = "YouTube",
                            handle = profile.socialYoutube,
                            color = Color(0xFFFF0000),
                            modifier = Modifier.weight(1f)
                        )
                        SocialLinkBadge(
                            label = "Website",
                            handle = profile.socialWebsite,
                            color = CyanAccent,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Creator Statistics Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .offset(y = (-22).dp)
                ) {
                    CreatorStatisticsCard(
                        summary = statsSummary,
                        allowRating = !profile.isCurrentUser,
                        userRating = userSelectedRating,
                        onRate = { star ->
                            userSelectedRating = star
                            onRateCreator(star.toFloat())
                        }
                    )
                }
            }

            // Audience Rating Trends over Last 10 Performances
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                ) {
                    PerformanceAnalyticsChart(
                        performances = portfolio,
                        creatorBaselineRating = profile.audienceRating,
                        onPerformancePointClick = onPlayPerformance
                    )
                }
            }

            // Achievement Badge Gallery
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                ) {
                    AchievementBadgeGallery(
                        badges = statsSummary.badges
                    )
                }
            }

            // Portfolio Header, Search, & Category Filter
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {

                    // Portfolio Section Header & Filter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Content Portfolio (${portfolio.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (profile.isCurrentUser) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = onNavigateToStudio,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp).testTag("upload_music_profile_button")
                                ) {
                                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Upload", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }

                                Button(
                                    onClick = onNavigateToStudio,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp).testTag("add_performance_button")
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Studio", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal Search Bar for Creator Portfolio
                    HorizontalStageSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        placeholderText = "Search portfolio tracks, genres...",
                        testTag = "creator_profile_search_bar",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Portfolio Category Filter Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedPortfolioCategory == null,
                                onClick = { selectedPortfolioCategory = null },
                                label = { Text("All (${portfolio.size})", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonViolet,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedPortfolioCategory == PerformanceCategory.MUSIC,
                                onClick = { selectedPortfolioCategory = PerformanceCategory.MUSIC },
                                label = { Text("Music", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonViolet,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedPortfolioCategory == PerformanceCategory.SPOKEN_WORD,
                                onClick = { selectedPortfolioCategory = PerformanceCategory.SPOKEN_WORD },
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

            // Portfolio Items List
            if (filteredPortfolio.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No portfolio tracks match \"$searchQuery\"." else "No content in this category yet.",
                            color = TextMuted
                        )
                    }
                }
            } else {
                items(filteredPortfolio) { performance ->
                    PortfolioItemCard(
                        performance = performance,
                        isPinnedOffline = pinnedPerformanceIds.contains(performance.id),
                        onToggleOfflinePin = onToggleOfflinePin?.let { cb -> { cb(performance) } },
                        onPlay = { onPlayPerformance(performance.id) },
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
            }

            if (profile.isCurrentUser) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .testTag("profile_offline_cache_card"),
                        colors = CardDefaults.cardColors(containerColor = StageSurface),
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.offline_cache_card_badge),
                                        color = ElectricCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = stringResource(R.string.offline_cache_card_title),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = stringResource(R.string.offline_cache_card_subtitle),
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                val totalOfflineKb = offlineCachedTracks.sumOf { it.cachedBytes } / 1024L
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(StageSurfaceElevated)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${offlineCachedTracks.size} • ${totalOfflineKb} KB",
                                        color = ElectricCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (offlineCachedTracks.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.offline_cache_empty_tip),
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            } else {
                                offlineCachedTracks.take(5).forEach { cached ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(StageSurfaceElevated)
                                            .clickable { onPlayPerformance(cached.performanceId) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = cached.title,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${cached.artistName} • ${cached.genre}",
                                                color = TextSecondary,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (onUnpinOfflineTrack != null) {
                                            OutlinedButton(
                                                onClick = { onUnpinOfflineTrack(cached.performanceId) },
                                                border = BorderStroke(1.dp, StageSurfaceHighlight),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .defaultMinSize(minHeight = 36.dp)
                                                    .testTag("profile_unpin_${cached.performanceId}")
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.offline_unpin_cta),
                                                    color = TextSecondary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                if (onClearOfflineCache != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = onClearOfflineCache,
                                        border = BorderStroke(1.dp, HotMagenta.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("profile_clear_offline_cache_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.offline_cache_clear_cta),
                                            color = HotMagenta,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (profile.isCurrentUser && encryptionStatus != null && onToggleEncryption != null) {
                item {
                    val encryptedPortfolioCount = remember(portfolio) {
                        portfolio.count { it.hasEncryptedMedia }
                    }
                    EncryptionControlCard(
                        status = encryptionStatus,
                        encryptedPortfolioTracksCount = encryptedPortfolioCount,
                        onToggleEncryption = onToggleEncryption,
                        onVerifyAndEncryptVault = { onVerifyAndEncryptVault?.invoke() },
                        onOpenVaultDetails = onOpenEncryptionVault,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            if (profile.isCurrentUser && (onSwitchGoogleAccount != null || onSignOut != null || onDeleteAccount != null)) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .testTag("profile_google_account_card"),
                        colors = CardDefaults.cardColors(containerColor = StageSurface),
                        border = BorderStroke(1.dp, StageSurfaceHighlight),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isStandaloneAccount) {
                                            stringResource(R.string.auth_profile_standalone_session_title)
                                        } else {
                                            stringResource(R.string.auth_profile_session_title)
                                        },
                                        color = TextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = accountEmail?.takeIf { it.isNotBlank() }
                                            ?: if (isStandaloneAccount) {
                                                stringResource(R.string.auth_profile_standalone_session_subtitle)
                                            } else {
                                                stringResource(R.string.auth_profile_session_subtitle)
                                            },
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (onSwitchGoogleAccount != null) {
                                    OutlinedButton(
                                        onClick = onSwitchGoogleAccount,
                                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("profile_switch_google_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SwitchAccount,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Switch",
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }

                                if (onSignOut != null) {
                                    Button(
                                        onClick = onSignOut,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = HotMagenta.copy(alpha = 0.18f),
                                            contentColor = HotMagenta
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 48.dp)
                                            .testTag("profile_signout_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(R.string.auth_sign_out),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            if (onDeleteAccount != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = { showDeleteAccountDialog = true },
                                    enabled = !isDeletingAccount,
                                    border = BorderStroke(1.dp, HotMagenta.copy(alpha = 0.65f)),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("profile_delete_account_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteForever,
                                        contentDescription = null,
                                        tint = HotMagenta,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isDeletingAccount) {
                                            stringResource(R.string.auth_delete_account_deleting_cta)
                                        } else {
                                            stringResource(R.string.auth_delete_account_cta)
                                        },
                                        color = HotMagenta,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { showPlayReleaseCenterDialog = true },
                                border = BorderStroke(1.dp, GoldCrown.copy(alpha = 0.55f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("profile_play_release_center_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = GoldCrown,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.play_release_center_cta),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { showPrivacyPolicyDialog = true },
                                border = BorderStroke(1.dp, StageSurfaceHighlight),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("profile_privacy_policy_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Policy,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.privacy_policy_cta),
                                    color = TextSecondary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Edit Profile Dialog
        if (isEditingProfile) {
            EditProfileDialog(
                profile = profile,
                onDismiss = { isEditingProfile = false },
                onSave = { name, title, bio, cat, spot, insta, yt, web ->
                    onSaveProfile(name, title, bio, cat, spot, insta, yt, web)
                    isEditingProfile = false
                }
            )
        }

        // Delete Account Confirmation Dialog
        if (showDeleteAccountDialog && onDeleteAccount != null) {
            DeleteAccountConfirmationDialog(
                role = accountRole,
                isStandalone = isStandaloneAccount,
                isDeleting = isDeletingAccount,
                onConfirmDelete = {
                    showDeleteAccountDialog = false
                    onDeleteAccount()
                },
                onDismiss = { showDeleteAccountDialog = false }
            )
        }

        // Privacy Policy & Data Safety Dialog
        if (showPrivacyPolicyDialog) {
            PrivacyPolicyDialog(
                onDismiss = { showPrivacyPolicyDialog = false }
            )
        }

        if (showPlayReleaseCenterDialog) {
            com.example.ui.components.PlayReleaseCenterDialog(
                appCheckStatus = appCheckStatus,
                encryptionEnabled = encryptionStatus?.isEnabled ?: true,
                initialEmailSeed = accountEmail.orEmpty(),
                onOpenPrivacyPolicy = {
                    showPlayReleaseCenterDialog = false
                    showPrivacyPolicyDialog = true
                },
                onOpenVault = onOpenEncryptionVault?.let { openVault ->
                    {
                        showPlayReleaseCenterDialog = false
                        openVault()
                    }
                },
                onDismiss = { showPlayReleaseCenterDialog = false }
            )
        }
    }
}

@Composable
private fun SocialLinkBadge(
    label: String,
    handle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(
                text = handle,
                color = TextPrimary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}



@Composable
private fun PortfolioItemCard(
    performance: Performance,
    isPinnedOffline: Boolean = false,
    onToggleOfflinePin: (() -> Unit)? = null,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .testTag("portfolio_item_${performance.id}"),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlay,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (performance.category == PerformanceCategory.MUSIC) NeonViolet.copy(alpha = 0.2f)
                            else SpokenWordPrimary.copy(alpha = 0.2f)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = if (performance.category == PerformanceCategory.MUSIC) NeonVioletLight else ElectricCyan
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = performance.title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (performance.hasVideo) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(GoldCrown.copy(alpha = 0.22f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("Video", color = GoldCrown, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                        if (performance.audioType == com.example.model.SynthAudioType.UPLOADED_MUSIC || !performance.customAudioPath.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(ElectricCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text("Uploaded", color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                        if (performance.hasEncryptedMedia) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(NeonViolet.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                                    .testTag("portfolio_encrypted_badge_${performance.id}")
                            ) {
                                Text("AES-256", color = NeonVioletLight, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                    Text(
                        text = "${performance.genre} • ${performance.durationSec / 60}:${(performance.durationSec % 60).toString().padStart(2, '0')}",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Stats + Offline Pin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onToggleOfflinePin != null) {
                    IconButton(
                        onClick = onToggleOfflinePin,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPinnedOffline) ElectricCyan.copy(alpha = 0.18f)
                                else StageSurfaceElevated
                            )
                            .testTag("portfolio_offline_pin_${performance.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(
                                if (isPinnedOffline) R.string.offline_pinned_badge
                                else R.string.offline_pin_cta
                            ),
                            tint = if (isPinnedOffline) ElectricCyan else TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HowToVote, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("${performance.votesCount}", fontSize = 11.sp, color = TextPrimary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = HotMagenta, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(2.dp))
                    Text("${performance.likesCount}", fontSize = 11.sp, color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    profile: CreatorProfile,
    onDismiss: () -> Unit,
    onSave: (name: String, title: String, bio: String, category: PerformanceCategory, spotify: String, instagram: String, youtube: String, website: String) -> Unit
) {
    var name by remember { mutableStateOf(profile.artistName) }
    var stageTitle by remember { mutableStateOf(profile.stageTitle) }
    var bio by remember { mutableStateOf(profile.bio) }
    var category by remember { mutableStateOf(profile.primaryCategory) }
    var spotify by remember { mutableStateOf(profile.socialSpotify) }
    var instagram by remember { mutableStateOf(profile.socialInstagram) }
    var youtube by remember { mutableStateOf(profile.socialYoutube) }
    var website by remember { mutableStateOf(profile.socialWebsite) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = StageSurface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Customize Profile",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Stage Name / Artist Name") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_name_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = stageTitle,
                    onValueChange = { stageTitle = it },
                    label = { Text("Stage Tagline / Title") },
                    modifier = Modifier.fillMaxWidth().testTag("edit_title_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Creator Bio & Story") },
                    modifier = Modifier.fillMaxWidth().height(100.dp).testTag("edit_bio_input"),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = spotify,
                    onValueChange = { spotify = it },
                    label = { Text("Spotify / Music Link") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = instagram,
                    onValueChange = { instagram = it },
                    label = { Text("Instagram Handle") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(name, stageTitle, bio, category, spotify, instagram, youtube, website) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = Color.Black),
                        modifier = Modifier.testTag("save_profile_button")
                    ) {
                        Text("Save Profile", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
