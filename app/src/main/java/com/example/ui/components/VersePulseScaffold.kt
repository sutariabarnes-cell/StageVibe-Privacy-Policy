package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AppNavDestination
import com.example.model.Performance
import com.example.model.PerformanceVoteCount
import com.example.model.UserRole
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

private data class ScaffoldNavEntry(
    val destination: AppNavDestination,
    val labelResId: Int,
    val icon: ImageVector,
    val testTag: String,
    val badgeCount: Int = 0
)

/**
 * Adaptive Material 3 application UI scaffold for VersePulse Arena.
 *
 * Provides:
 * - Edge-to-edge safe-drawing inset handling (`WindowInsets.safeDrawing` & `WindowInsets.navigationBars`).
 * - Responsive navigation: bottom `NavigationBar` on compact screens (< 600.dp) and side
 *   `NavigationRail` on medium/expanded screens (>= 600.dp).
 * - Non-overlapping `TopAppBar` with brand badge, live stage vote tally indicator, sound toggle,
 *   Spectator/Creator role switcher, and authenticated account trigger.
 * - Docked `StageMiniPlayerBar` synced with real-time `StateFlow` vote counts when a track is
 *   minimized, plus smooth fullscreen player overlay support and `SnackbarHost` feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VersePulseScaffold(
    currentDestination: AppNavDestination,
    userRole: UserRole,
    accountDisplayName: String,
    activeLiveCount: Int,
    accountPhotoUrl: String? = null,
    totalActiveVotes: Int,
    isSoundEffectsEnabled: Boolean,
    isEncryptionEnabled: Boolean = true,
    activePerformance: Performance?,
    activePerformanceVoteState: PerformanceVoteCount?,
    isPlaying: Boolean,
    isPlayerExpanded: Boolean,
    snackbarMessage: String? = null,
    onSnackbarConsumed: () -> Unit = {},
    onNavigate: (AppNavDestination) -> Unit,
    onProfileTabSelected: () -> Unit,
    onToggleSoundEffects: () -> Unit,
    onOpenEncryptionVault: (() -> Unit)? = null,
    onToggleUserRole: () -> Unit,
    onOpenAccountSheet: () -> Unit,
    onCreateTrackClicked: () -> Unit,
    onMiniPlayerTogglePlayPause: () -> Unit,
    onMiniPlayerVote: (String) -> Unit,
    onMiniPlayerExpand: () -> Unit,
    onMiniPlayerClose: () -> Unit,
    fullscreenOverlay: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        if (!snackbarMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(snackbarMessage)
            onSnackbarConsumed()
        }
    }

    val navEntries = remember(userRole, activeLiveCount) {
        buildList {
            add(
                ScaffoldNavEntry(
                    destination = AppNavDestination.DISCOVER,
                    labelResId = R.string.nav_label_discover,
                    icon = Icons.Default.Explore,
                    testTag = "nav_discover"
                )
            )
            add(
                ScaffoldNavEntry(
                    destination = AppNavDestination.LIVE,
                    labelResId = R.string.nav_label_live,
                    icon = Icons.Default.Radio,
                    testTag = "nav_live",
                    badgeCount = activeLiveCount
                )
            )
            add(
                ScaffoldNavEntry(
                    destination = AppNavDestination.POLLS,
                    labelResId = R.string.nav_label_polls,
                    icon = Icons.Default.HowToVote,
                    testTag = "nav_polls"
                )
            )
            add(
                ScaffoldNavEntry(
                    destination = AppNavDestination.LEADERBOARD,
                    labelResId = R.string.nav_label_leaderboard,
                    icon = Icons.Default.EmojiEvents,
                    testTag = "nav_leaderboard"
                )
            )
            if (userRole == UserRole.CREATOR) {
                add(
                    ScaffoldNavEntry(
                        destination = AppNavDestination.STUDIO,
                        labelResId = R.string.nav_label_studio,
                        icon = Icons.Default.Mic,
                        testTag = "nav_studio"
                    )
                )
            }
            add(
                ScaffoldNavEntry(
                    destination = AppNavDestination.PROFILE,
                    labelResId = R.string.nav_label_profile,
                    icon = Icons.Default.Person,
                    testTag = "nav_profile"
                )
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("app_scaffold_root")
    ) {
        val isExpandedLayout = maxWidth >= 600.dp
        val showDockedMiniPlayer = activePerformance != null && !isPlayerExpanded
        val showCreatorFab = userRole == UserRole.CREATOR &&
            currentDestination == AppNavDestination.DISCOVER &&
            !isPlayerExpanded

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                VersePulseTopAppBar(
                    userRole = userRole,
                    accountDisplayName = accountDisplayName,
                    accountPhotoUrl = accountPhotoUrl,
                    totalActiveVotes = totalActiveVotes,
                    showVotesChip = maxWidth >= 430.dp,
                    showEncryptionButton = onOpenEncryptionVault != null && maxWidth >= 380.dp,
                    isEncryptionEnabled = isEncryptionEnabled,
                    isSoundEffectsEnabled = isSoundEffectsEnabled,
                    onOpenEncryptionVault = onOpenEncryptionVault,
                    onToggleSoundEffects = onToggleSoundEffects,
                    onToggleUserRole = onToggleUserRole,
                    onOpenAccountSheet = onOpenAccountSheet
                )
            },
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        actionColor = MaterialTheme.colorScheme.secondary,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            floatingActionButton = {
                if (showCreatorFab) {
                    ExtendedFloatingActionButton(
                        onClick = onCreateTrackClicked,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.scaffold_fab_studio),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("scaffold_fab_studio")
                    )
                }
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    AnimatedVisibility(
                        visible = showDockedMiniPlayer,
                        enter = slideInVertically(initialOffsetY = { it }),
                        exit = slideOutVertically(targetOffsetY = { it })
                    ) {
                        activePerformance?.let { perf ->
                            StageMiniPlayerBar(
                                performance = perf,
                                voteState = activePerformanceVoteState,
                                isPlaying = isPlaying,
                                onTogglePlayPause = onMiniPlayerTogglePlayPause,
                                onVote = { onMiniPlayerVote(perf.id) },
                                onExpand = onMiniPlayerExpand,
                                onClose = onMiniPlayerClose
                            )
                        }
                    }

                    if (!isExpandedLayout) {
                        VersePulseBottomNavBar(
                            entries = navEntries,
                            currentDestination = currentDestination,
                            onSelectEntry = { dest ->
                                if (dest == AppNavDestination.PROFILE) {
                                    onProfileTabSelected()
                                } else {
                                    onNavigate(dest)
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isExpandedLayout) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        VersePulseNavigationRail(
                            entries = navEntries,
                            currentDestination = currentDestination,
                            onSelectEntry = { dest ->
                                if (dest == AppNavDestination.PROFILE) {
                                    onProfileTabSelected()
                                } else {
                                    onNavigate(dest)
                                }
                            }
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            content(PaddingValues(0.dp))
                        }
                    }
                } else {
                    content(PaddingValues(0.dp))
                }

                fullscreenOverlay()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VersePulseTopAppBar(
    userRole: UserRole,
    accountDisplayName: String,
    accountPhotoUrl: String?,
    totalActiveVotes: Int,
    showVotesChip: Boolean,
    showEncryptionButton: Boolean,
    isEncryptionEnabled: Boolean,
    isSoundEffectsEnabled: Boolean,
    onOpenEncryptionVault: (() -> Unit)?,
    onToggleSoundEffects: () -> Unit,
    onToggleUserRole: () -> Unit,
    onOpenAccountSheet: () -> Unit
) {
    val roleDesc = stringResource(R.string.scaffold_role_switcher_desc)
    val fallbackAccount = stringResource(R.string.scaffold_account_fallback)
    val resolvedName = accountDisplayName.ifBlank { fallbackAccount }

    TopAppBar(
        modifier = Modifier.testTag("app_top_bar"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.scaffold_brand_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.scaffold_brand_badge),
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }

                if (showVotesChip && totalActiveVotes > 0) {
                    Box(
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .testTag("scaffold_live_votes_pill")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = GoldCrown,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "$totalActiveVotes",
                                color = GoldCrown,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        },
        actions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 6.dp)
            ) {
                if (showEncryptionButton && onOpenEncryptionVault != null) {
                    IconButton(
                        onClick = onOpenEncryptionVault,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEncryptionEnabled) ElectricCyan.copy(alpha = 0.18f)
                                else GoldCrown.copy(alpha = 0.20f)
                            )
                            .border(
                                1.dp,
                                if (isEncryptionEnabled) ElectricCyan.copy(alpha = 0.55f)
                                else GoldCrown.copy(alpha = 0.55f),
                                CircleShape
                            )
                            .testTag("scaffold_encryption_button")
                    ) {
                        Icon(
                            imageVector = if (isEncryptionEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = stringResource(
                                if (isEncryptionEnabled) R.string.scaffold_encryption_enabled_desc
                                else R.string.scaffold_encryption_disabled_desc
                            ),
                            tint = if (isEncryptionEnabled) ElectricCyan else GoldCrown,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleSoundEffects,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSoundEffectsEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        )
                        .testTag("sound_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isSoundEffectsEnabled) {
                            Icons.AutoMirrored.Filled.VolumeUp
                        } else {
                            Icons.AutoMirrored.Filled.VolumeOff
                        },
                        contentDescription = stringResource(
                            if (isSoundEffectsEnabled) R.string.scaffold_sound_on_desc
                            else R.string.scaffold_sound_off_desc
                        ),
                        tint = if (isSoundEffectsEnabled) MaterialTheme.colorScheme.secondary else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Accessible Role Switcher Pill (Spectator vs Creator)
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (userRole == UserRole.CREATOR) HotMagenta.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(
                            1.dp,
                            if (userRole == UserRole.CREATOR) HotMagenta else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(18.dp)
                        )
                        .clickable(role = Role.Button, onClick = onToggleUserRole)
                        .semantics { contentDescription = roleDesc }
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("role_switcher_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (userRole == UserRole.CREATOR) Icons.Default.Mic else Icons.Default.HowToVote,
                            contentDescription = null,
                            tint = if (userRole == UserRole.CREATOR) HotMagenta else MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                if (userRole == UserRole.CREATOR) R.string.scaffold_role_creator
                                else R.string.scaffold_role_spectator
                            ),
                            color = if (userRole == UserRole.CREATOR) TextPrimary else MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                // Authenticated User Account Chip
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .clickable(role = Role.Button, onClick = onOpenAccountSheet)
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                        .testTag("auth_user_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    if (userRole == UserRole.CREATOR) HotMagenta
                                    else MaterialTheme.colorScheme.secondary
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!accountPhotoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = accountPhotoUrl,
                                    contentDescription = "$resolvedName Google avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text(
                                    text = resolvedName.take(1).uppercase(),
                                    color = if (userRole == UserRole.CREATOR) Color.White else Color.Black,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = resolvedName.split(" ").firstOrNull()?.take(9) ?: fallbackAccount,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun VersePulseBottomNavBar(
    entries: List<ScaffoldNavEntry>,
    currentDestination: AppNavDestination,
    onSelectEntry: (AppNavDestination) -> Unit
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp,
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.testTag("app_bottom_nav_bar")
    ) {
        entries.forEach { entry ->
            val labelText = stringResource(entry.labelResId)
            NavigationBarItem(
                selected = currentDestination == entry.destination,
                onClick = { onSelectEntry(entry.destination) },
                icon = {
                    if (entry.badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = HotMagenta) {
                                    Text(
                                        text = "${entry.badgeCount}",
                                        color = Color.White,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        ) {
                            Icon(imageVector = entry.icon, contentDescription = labelText)
                        }
                    } else {
                        Icon(imageVector = entry.icon, contentDescription = labelText)
                    }
                },
                label = {
                    Text(
                        text = labelText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = ElectricCyan,
                    selectedTextColor = ElectricCyan,
                    indicatorColor = NeonViolet.copy(alpha = 0.35f),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                ),
                modifier = Modifier.testTag(entry.testTag)
            )
        }
    }
}

@Composable
private fun VersePulseNavigationRail(
    entries: List<ScaffoldNavEntry>,
    currentDestination: AppNavDestination,
    onSelectEntry: (AppNavDestination) -> Unit
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start),
        modifier = Modifier
            .fillMaxHeight()
            .testTag("app_nav_rail")
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        entries.forEach { entry ->
            val labelText = stringResource(entry.labelResId)
            NavigationRailItem(
                selected = currentDestination == entry.destination,
                onClick = { onSelectEntry(entry.destination) },
                icon = {
                    if (entry.badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = HotMagenta) {
                                    Text(
                                        text = "${entry.badgeCount}",
                                        color = Color.White,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        ) {
                            Icon(imageVector = entry.icon, contentDescription = labelText)
                        }
                    } else {
                        Icon(imageVector = entry.icon, contentDescription = labelText)
                    }
                },
                label = {
                    Text(
                        text = labelText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = ElectricCyan,
                    selectedTextColor = ElectricCyan,
                    indicatorColor = NeonViolet.copy(alpha = 0.35f),
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted
                ),
                modifier = Modifier.testTag(entry.testTag)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

/**
 * Docked Mini Player Bar rendered above the bottom navigation bar when a performance is active
 * and minimized, synced with real-time `StateFlow` vote counts.
 */
@Composable
fun StageMiniPlayerBar(
    performance: Performance,
    voteState: PerformanceVoteCount?,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onVote: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val liveVotes = voteState?.totalVotes ?: performance.votesCount
    val isVoted = voteState?.isVotedByCurrentUser ?: performance.isVotedByMe
    val expandDesc = stringResource(R.string.mini_player_expand_desc)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("mini_player_bar")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onExpand)
                    .semantics { contentDescription = expandDesc }
                    .padding(vertical = 2.dp)
                    .testTag("mini_player_expand_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(NeonViolet, HotMagenta))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.MusicNote else Icons.Default.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = performance.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${performance.artistName} • $liveVotes votes",
                        color = if (isVoted) GoldCrown else MaterialTheme.colorScheme.secondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("mini_player_vote_tally")
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onVote,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .testTag("mini_player_vote_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.HowToVote,
                        contentDescription = stringResource(R.string.mini_player_vote_desc),
                        tint = if (isVoted) GoldCrown else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .testTag("mini_player_play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.mini_player_play_pause_desc),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(40.dp)
                        .testTag("mini_player_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.mini_player_close_desc),
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
