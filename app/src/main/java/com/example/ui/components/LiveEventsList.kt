package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.data.LiveEventsRepository
import com.example.model.LiveEvent
import com.example.model.LiveEventsUiState
import com.example.model.PerformanceCategory
import com.example.model.PerformanceCategoryCatalog
import com.example.model.PerformanceStyle
import com.example.model.performanceStyle
import com.example.ui.LiveEventsViewModel
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
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Fetches and displays a real-time list of live Music and Spoken Word events from Cloud Firestore,
 * showing each event's title and current participant count.
 *
 * Auth-gated on [FirebaseAuth]: only instantiates [LiveEventsViewModel] and attaches Firestore
 * snapshot listeners when an authenticated user session is active.
 */
@Composable
fun LiveEventsList(
    modifier: Modifier = Modifier,
    repository: LiveEventsRepository? = null,
    auth: FirebaseAuth? = remember {
        try {
            Firebase.auth
        } catch (_: Exception) {
            null
        }
    },
    includeSectionHeader: Boolean = true,
    useLazyColumn: Boolean = true,
    searchQuery: String = "",
    showFilterBar: Boolean = true,
    externalSelectedCategory: PerformanceCategory? = null,
    externalSelectedStyle: PerformanceStyle? = null,
    onExternalSelectStyle: ((PerformanceStyle?) -> Unit)? = null,
    onEventSelected: ((LiveEvent) -> Unit)? = null
) {
    var currentUserUid by remember(auth) { mutableStateOf(auth?.currentUser?.uid) }

    DisposableEffect(auth) {
        if (auth == null) {
            onDispose { }
        } else {
            val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
                currentUserUid = firebaseAuth.currentUser?.uid
            }
            auth.addAuthStateListener(listener)
            onDispose {
                auth.removeAuthStateListener(listener)
            }
        }
    }

    val uid = currentUserUid
    if (uid == null) {
        return
    }

    val liveEventsViewModel: LiveEventsViewModel = viewModel(
        key = "live_events_$uid",
        factory = viewModelFactory {
            initializer {
                val repo = repository ?: run {
                    val app = checkNotNull(this[APPLICATION_KEY]) {
                        "APPLICATION_KEY missing from CreationExtras"
                    }
                    LiveEventsRepository(db = com.example.data.FirestoreService.createFirestore(app))
                }
                LiveEventsViewModel(
                    repository = repo,
                    currentUserId = uid
                )
            }
        }
    )

    androidx.compose.runtime.LaunchedEffect(externalSelectedStyle, externalSelectedCategory, onExternalSelectStyle != null) {
        if (onExternalSelectStyle != null) {
            liveEventsViewModel.setStyleFilter(externalSelectedStyle)
            if (externalSelectedStyle == null) {
                liveEventsViewModel.setCategoryFilter(externalSelectedCategory)
            }
        }
    }

    val uiState by liveEventsViewModel.uiState.collectAsStateWithLifecycle()
    val selectedCategory by liveEventsViewModel.selectedCategory.collectAsStateWithLifecycle()
    val vmSelectedStyle by liveEventsViewModel.selectedStyle.collectAsStateWithLifecycle()
    val effectiveStyle = if (onExternalSelectStyle != null) externalSelectedStyle else vmSelectedStyle
    val effectiveCategory = if (onExternalSelectStyle != null && effectiveStyle == null) {
        externalSelectedCategory
    } else {
        selectedCategory
    }

    LiveEventsListContent(
        uiState = uiState,
        selectedCategory = effectiveCategory,
        onSelectCategory = { liveEventsViewModel.setCategoryFilter(it) },
        onToggleJoinEvent = { event ->
            liveEventsViewModel.toggleJoinEvent(
                event = event,
                onEventJoined = onEventSelected
            )
        },
        onCreateEvent = { title, category, genre ->
            liveEventsViewModel.hostLiveEvent(
                title = title,
                category = category,
                genre = genre,
                onCreated = onEventSelected
            )
        },
        onRetry = { liveEventsViewModel.retry() },
        modifier = modifier,
        includeSectionHeader = includeSectionHeader,
        useLazyColumn = useLazyColumn,
        searchQuery = searchQuery,
        onEventSelected = onEventSelected,
        showFilterBar = showFilterBar,
        selectedStyle = effectiveStyle,
        onSelectStyle = { nextStyle ->
            liveEventsViewModel.setStyleFilter(nextStyle)
            onExternalSelectStyle?.invoke(nextStyle)
        }
    )
}

/**
 * Stateless presentation composable for rendering live Music and Spoken Word events,
 * displaying event titles, performance style categories (Rap, Poetry, Jazz, Spoken Word, etc.),
 * and current participant counts.
 */
@Composable
fun LiveEventsListContent(
    uiState: LiveEventsUiState,
    selectedCategory: PerformanceCategory?,
    onSelectCategory: (PerformanceCategory?) -> Unit,
    onToggleJoinEvent: (LiveEvent) -> Unit,
    onCreateEvent: (title: String, category: PerformanceCategory, genre: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    includeSectionHeader: Boolean = true,
    useLazyColumn: Boolean = true,
    searchQuery: String = "",
    onEventSelected: ((LiveEvent) -> Unit)? = null,
    showFilterBar: Boolean = true,
    selectedStyle: PerformanceStyle? = null,
    onSelectStyle: ((PerformanceStyle?) -> Unit)? = null
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var internalSelectedStyle by remember { mutableStateOf<PerformanceStyle?>(null) }
    val activeStyle = if (onSelectStyle != null) selectedStyle else internalSelectedStyle

    val handleStyleSelect: (PerformanceStyle?) -> Unit = { nextStyle ->
        if (onSelectStyle != null) {
            onSelectStyle(nextStyle)
        } else {
            internalSelectedStyle = nextStyle
            onSelectCategory(nextStyle?.parentCategory)
        }
    }

    val filteredEvents = remember(uiState, searchQuery, activeStyle, selectedCategory) {
        when (uiState) {
            is LiveEventsUiState.Success -> {
                uiState.events
                    .filter { event ->
                        PerformanceCategoryCatalog.matchesFilter(
                            itemStyle = event.performanceStyle,
                            itemParentCategory = event.performanceCategory,
                            selectedStyle = activeStyle,
                            selectedCategory = selectedCategory
                        )
                    }
                    .filter { event ->
                        searchQuery.isBlank() ||
                            event.title.contains(searchQuery, ignoreCase = true) ||
                            event.hostName.contains(searchQuery, ignoreCase = true) ||
                            event.genre.contains(searchQuery, ignoreCase = true) ||
                            event.performanceStyle.displayName.contains(searchQuery, ignoreCase = true) ||
                            event.performanceCategory.displayName.contains(searchQuery, ignoreCase = true)
                    }
            }
            else -> emptyList()
        }
    }

    val joinedEventIds = (uiState as? LiveEventsUiState.Success)?.joinedEventIds ?: emptySet()
    val totalParticipants = remember(filteredEvents) {
        filteredEvents.sumOf { it.currentParticipants }
    }

    if (useLazyColumn) {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .background(StageBackground)
                .testTag("live_events_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (includeSectionHeader) {
                item(key = "header") {
                    LiveEventsHeader(
                        eventCount = filteredEvents.size,
                        totalParticipants = totalParticipants,
                        onHostEventClick = { showCreateDialog = true }
                    )
                }
            }

            if (showFilterBar) {
                item(key = "filters") {
                    PerformanceCategoryFilterChipBar(
                        selectedStyle = activeStyle,
                        onSelectStyle = handleStyleSelect,
                        allLabel = stringResource(R.string.live_events_filter_all),
                        rowTestTag = "live_events_filter_row",
                        chipTagPrefix = "live_events_filter"
                    )
                }
            }

            when (uiState) {
                is LiveEventsUiState.Loading -> {
                    item(key = "loading") {
                        LiveEventsLoadingCard()
                    }
                }

                is LiveEventsUiState.Error -> {
                    item(key = "error") {
                        LiveEventsErrorCard(
                            message = uiState.message,
                            onRetry = onRetry
                        )
                    }
                }

                is LiveEventsUiState.Success -> {
                    if (filteredEvents.isEmpty()) {
                        item(key = "empty") {
                            LiveEventsEmptyCard(
                                onHostEventClick = { showCreateDialog = true }
                            )
                        }
                    } else {
                        items(filteredEvents, key = { it.id.ifBlank { it.title } }) { event ->
                            LiveEventCard(
                                event = event,
                                isJoined = event.id in joinedEventIds,
                                onToggleJoin = { onToggleJoinEvent(event) },
                                onClick = { onEventSelected?.invoke(event) ?: onToggleJoinEvent(event) }
                            )
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag("live_events_list"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (includeSectionHeader) {
                LiveEventsHeader(
                    eventCount = filteredEvents.size,
                    totalParticipants = totalParticipants,
                    onHostEventClick = { showCreateDialog = true }
                )
            }

            if (showFilterBar) {
                PerformanceCategoryFilterChipBar(
                    selectedStyle = activeStyle,
                    onSelectStyle = handleStyleSelect,
                    allLabel = stringResource(R.string.live_events_filter_all),
                    rowTestTag = "live_events_filter_row",
                    chipTagPrefix = "live_events_filter"
                )
            }

            when (uiState) {
                is LiveEventsUiState.Loading -> {
                    LiveEventsLoadingCard()
                }

                is LiveEventsUiState.Error -> {
                    LiveEventsErrorCard(
                        message = uiState.message,
                        onRetry = onRetry
                    )
                }

                is LiveEventsUiState.Success -> {
                    if (filteredEvents.isEmpty()) {
                        LiveEventsEmptyCard(
                            onHostEventClick = { showCreateDialog = true }
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            filteredEvents.forEach { event ->
                                LiveEventCard(
                                    event = event,
                                    isJoined = event.id in joinedEventIds,
                                    onToggleJoin = { onToggleJoinEvent(event) },
                                    onClick = { onEventSelected?.invoke(event) ?: onToggleJoinEvent(event) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateLiveEventDialog(
            initialCategory = activeStyle?.parentCategory ?: selectedCategory ?: PerformanceCategory.MUSIC,
            initialStyle = activeStyle,
            onDismiss = { showCreateDialog = false },
            onConfirm = { title, category, genre ->
                onCreateEvent(title, category, genre)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun LiveEventsHeader(
    eventCount: Int,
    totalParticipants: Int,
    onHostEventClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulseTransition = rememberInfiniteTransition(label = "live_events_pulse")
    val dotAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live_events_dot_alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_events_header"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(HotMagenta.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .border(1.dp, HotMagenta.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .alpha(dotAlpha)
                                .clip(CircleShape)
                                .background(HotMagenta)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.live_events_section_badge),
                            color = HotMagenta,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                }

                if (eventCount > 0) {
                    Text(
                        text = "$eventCount active • $totalParticipants participants",
                        color = ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("live_events_summary_stats")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.live_events_section_title),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = stringResource(R.string.live_events_section_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Button(
            onClick = onHostEventClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonViolet,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .defaultMinSize(minHeight = 48.dp)
                .testTag("create_live_event_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.live_events_host_cta),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}


/**
 * Individual Live Event card displaying the event title, performance style badge
 * (Rap, Poetry, Jazz, Spoken Word, etc.), host details, and real-time participant count.
 */
@Composable
fun LiveEventCard(
    event: LiveEvent,
    isJoined: Boolean,
    onToggleJoin: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = event.performanceCategory
    val style = event.performanceStyle
    val participants = event.currentParticipants
    val participantLabel = if (participants == 1) {
        "1 participant"
    } else {
        "$participants participants"
    }

    val joinBtnColor by animateColorAsState(
        targetValue = if (isJoined) GoldCrown else ElectricCyan,
        animationSpec = tween(durationMillis = 240),
        label = "join_event_btn_color"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    if (isJoined) {
                        listOf(GoldCrown.copy(alpha = 0.85f), HotMagenta.copy(alpha = 0.65f))
                    } else {
                        listOf(style.accentColor().copy(alpha = 0.65f), ElectricCyan.copy(alpha = 0.45f))
                    }
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() }
            .testTag("live_event_card_${event.id}"),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top Row: LIVE pill + Performance Style badge | Current Participant Count pill
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
                            .background(HotMagenta, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FiberManualRecord,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(9.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                    }

                    PerformanceStyleBadge(
                        style = style,
                        textTestTag = "live_event_style_${event.id}"
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Current Participant Count Badge
                Box(
                    modifier = Modifier
                        .background(ElectricCyan.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = participantLabel,
                            color = ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            modifier = Modifier.testTag("live_event_participants_${event.id}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Event Title + Host & Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            fontSize = 16.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("live_event_title_${event.id}")
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val hostSubtitle = buildString {
                        if (event.hostName.isNotBlank()) {
                            append("Hosted by ${event.hostName}")
                        }
                        if (event.genre.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(event.genre)
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = category.displayName,
                            color = if (category == PerformanceCategory.MUSIC) NeonVioletLight else ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier.testTag("live_event_category_${event.id}")
                        )
                        if (hostSubtitle.isNotBlank()) {
                            Text(
                                text = "• $hostSubtitle",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Button(
                    onClick = onToggleJoin,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = joinBtnColor,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("join_live_event_button_${event.id}")
                ) {
                    Icon(
                        imageVector = if (isJoined) Icons.Default.CheckCircle else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isJoined) {
                            stringResource(R.string.live_events_joined_cta)
                        } else {
                            stringResource(R.string.live_events_join_cta)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveEventsLoadingCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_events_loading"),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                color = ElectricCyan,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Syncing live Music & Spoken Word events...",
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LiveEventsEmptyCard(
    onHostEventClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(16.dp))
            .testTag("live_events_empty_state"),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(NeonViolet.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.live_events_empty_title),
                style = MaterialTheme.typography.titleSmall.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.live_events_empty_subtitle),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onHostEventClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("empty_host_live_event_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.live_events_host_cta),
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LiveEventsErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, HotMagenta.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .testTag("live_events_error_state"),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = HotMagenta,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = message,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            OutlinedButton(
                onClick = onRetry,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("live_events_retry_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.live_events_retry_cta),
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CreateLiveEventDialog(
    initialCategory: PerformanceCategory,
    initialStyle: PerformanceStyle? = null,
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: PerformanceCategory, genre: String) -> Unit
) {
    val defaultStyle = initialStyle ?: if (initialCategory == PerformanceCategory.MUSIC) {
        PerformanceStyle.SOUL_RNB
    } else {
        PerformanceStyle.SPOKEN_WORD
    }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(initialCategory) }
    var selectedStyle by remember { mutableStateOf<PerformanceStyle?>(defaultStyle) }
    var genre by remember { mutableStateOf(defaultStyle.defaultGenre) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StageSurfaceElevated,
        title = {
            Text(
                text = stringResource(R.string.live_events_dialog_title),
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.live_events_title_input_label)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_event_title_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = category == PerformanceCategory.MUSIC,
                        onClick = {
                            category = PerformanceCategory.MUSIC
                            if (selectedStyle?.parentCategory != PerformanceCategory.MUSIC) {
                                selectedStyle = PerformanceStyle.SOUL_RNB
                                genre = PerformanceStyle.SOUL_RNB.defaultGenre
                            }
                        },
                        label = { Text(stringResource(R.string.live_events_filter_music)) },
                        leadingIcon = {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonViolet,
                            selectedLabelColor = Color.White,
                            containerColor = StageSurface,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dialog_category_music")
                    )

                    FilterChip(
                        selected = category == PerformanceCategory.SPOKEN_WORD,
                        onClick = {
                            category = PerformanceCategory.SPOKEN_WORD
                            if (selectedStyle?.parentCategory != PerformanceCategory.SPOKEN_WORD) {
                                selectedStyle = PerformanceStyle.SPOKEN_WORD
                                genre = PerformanceStyle.SPOKEN_WORD.defaultGenre
                            }
                        },
                        label = { Text(stringResource(R.string.live_events_filter_spoken_word)) },
                        leadingIcon = {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(15.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpokenWordPrimary,
                            selectedLabelColor = Color.White,
                            containerColor = StageSurface,
                            labelColor = TextSecondary
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dialog_category_spoken_word")
                    )
                }

                Text(
                    text = stringResource(R.string.live_events_style_label),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_style_chip_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val stylesForDialogCategory = PerformanceCategoryCatalog.stylesForCategory(category)
                    items(stylesForDialogCategory, key = { it.id }) { styleOption ->
                        val isPicked = selectedStyle == styleOption
                        val isDarkOnAccent = styleOption == PerformanceStyle.JAZZ ||
                            styleOption == PerformanceStyle.ACOUSTIC ||
                            styleOption == PerformanceStyle.HIP_HOP
                        FilterChip(
                            selected = isPicked,
                            onClick = {
                                selectedStyle = styleOption
                                category = styleOption.parentCategory
                                genre = styleOption.defaultGenre
                            },
                            label = {
                                Text(
                                    text = styleOption.localizedLabel(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = styleOption.iconVector(),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = styleOption.accentColor(),
                                selectedLabelColor = if (isDarkOnAccent) Color.Black else Color.White,
                                selectedLeadingIconColor = if (isDarkOnAccent) Color.Black else Color.White,
                                containerColor = StageSurface,
                                labelColor = TextSecondary,
                                iconColor = styleOption.accentColor()
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("dialog_style_${styleOption.id}")
                        )
                    }
                }

                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text(stringResource(R.string.live_events_genre_input_label)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = TextMuted
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_event_genre_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalTitle = title.trim().ifBlank {
                        if (category == PerformanceCategory.MUSIC) "Midnight Acoustic Jam" else "Open Mic Poetry Cipher"
                    }
                    onConfirm(finalTitle, category, genre.trim().ifBlank { category.displayName })
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("confirm_create_live_event_button")
            ) {
                Text(
                    text = stringResource(R.string.live_events_publish_cta),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("cancel_create_live_event_button")
            ) {
                Text(
                    text = stringResource(R.string.live_events_cancel_cta),
                    color = TextSecondary
                )
            }
        }
    )
}
