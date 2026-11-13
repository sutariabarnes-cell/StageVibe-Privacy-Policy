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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LiveChatMessage
import com.example.model.LiveSession
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.PerformanceCategoryCatalog
import com.example.model.PerformanceStyle
import com.example.model.Poll
import com.example.model.StageVotingSnapshot
import com.example.model.SynthAudioType
import com.example.model.UserRole
import com.example.model.VisualizerTheme
import com.example.model.toLiveSession
import com.example.ui.components.FloatingReactionOverlay
import com.example.ui.components.HorizontalStageSearchBar
import com.example.ui.components.LiveEventsList
import com.example.ui.components.LiveMusicAudioVisualizer
import com.example.ui.components.LiveVisualizerMode
import com.example.ui.components.PerformanceVisualizer
import com.example.ui.components.RealtimeSessionVotingComponent
import com.example.ui.components.SpectatorCheerBar
import com.example.ui.components.VotingPollCard
import com.example.ui.components.accentColor
import com.example.ui.components.iconVector
import com.example.ui.components.localizedLabel
import kotlinx.coroutines.flow.Flow
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

@Composable
fun LiveStreamScreen(
    liveSessions: List<LiveSession>,
    activeSession: LiveSession?,
    attachedPoll: Poll?,
    audioAmplitude: Float,
    isPlayingLiveAudio: Boolean,
    userRole: UserRole,
    performers: List<Performance> = emptyList(),
    sessionVotesFlow: Flow<Map<String, Int>>? = null,
    stageVotingSnapshotFlow: Flow<StageVotingSnapshot>? = null,
    chatMessagesFlow: Flow<List<LiveChatMessage>>? = null,
    onJoinSession: (LiveSession) -> Unit,
    onLeaveSession: () -> Unit,
    onStartNewLive: (title: String, category: PerformanceCategory, genre: String, visualTheme: VisualizerTheme, audioType: SynthAudioType, pollId: String?) -> Unit,
    onStopLiveSession: (String) -> Unit,
    onSendChatMessage: (sessionId: String, text: String, isCheer: Boolean) -> Unit,
    onVoteInPoll: (pollId: String, candidateId: String) -> Unit,
    onCastSessionVote: (sessionId: String, performerId: String, performerName: String, voteDelta: Int, voteType: String) -> Unit = { _, _, _, _, _ -> },
    onPlayCandidatePreview: (performanceId: String) -> Unit,
    onSendReaction: (com.example.model.StageReactionType) -> Unit = {},
    onToggleSlowMode: () -> Unit = {},
    onToggleChatLock: () -> Unit = {},
    onPinAnnouncement: (String?) -> Unit = {},
    onToggleHideMessage: (messageId: String, hidden: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    if (activeSession != null && activeSession.isLive) {
        // Active Stream Player View (Spectator or Creator)
        LiveStreamRoom(
            session = activeSession,
            attachedPoll = attachedPoll,
            audioAmplitude = audioAmplitude,
            isPlayingAudio = isPlayingLiveAudio,
            performers = performers,
            sessionVotesFlow = sessionVotesFlow,
            stageVotingSnapshotFlow = stageVotingSnapshotFlow,
            chatMessagesFlow = chatMessagesFlow,
            onLeave = onLeaveSession,
            onStopSession = { onStopLiveSession(activeSession.id) },
            onSendMessage = { text, isCheer -> onSendChatMessage(activeSession.id, text, isCheer) },
            onVoteInPoll = onVoteInPoll,
            onCastSessionVote = onCastSessionVote,
            onPlayCandidatePreview = onPlayCandidatePreview,
            onSendReaction = onSendReaction,
            onToggleSlowMode = onToggleSlowMode,
            onToggleChatLock = onToggleChatLock,
            onPinAnnouncement = onPinAnnouncement,
            onToggleHideMessage = onToggleHideMessage,
            modifier = modifier
        )
    } else {
        // Live Streams Lobby / Directory & "Go Live" Creator action
        LiveStreamLobby(
            sessions = liveSessions,
            userRole = userRole,
            onJoinSession = onJoinSession,
            onStartNewLive = onStartNewLive,
            modifier = modifier
        )
    }
}

@Composable
private fun LiveStreamLobby(
    sessions: List<LiveSession>,
    userRole: UserRole,
    onJoinSession: (LiveSession) -> Unit,
    onStartNewLive: (String, PerformanceCategory, String, VisualizerTheme, SynthAudioType, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showStartLiveDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(StageBackground)
            .padding(horizontal = 18.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Header
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
                        .size(38.dp)
                        .background(
                            Brush.linearGradient(listOf(HotMagenta, NeonViolet)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LiveTv,
                        contentDescription = "Live",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Live Stages",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Real-time performances, chat & live voting",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { showStartLiveDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = HotMagenta,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("start_live_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FiberManualRecord,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Go Live", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal Search Bar
        HorizontalStageSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholderText = "Search live streams by creator, title, genre...",
            testTag = "livestream_search_bar",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Streams List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "firestore_live_events_feed") {
                LiveEventsList(
                    useLazyColumn = false,
                    searchQuery = searchQuery,
                    onEventSelected = { event ->
                        onJoinSession(event.toLiveSession())
                    }
                )
            }

            val liveOnly = sessions.filter { session ->
                session.isLive && (
                    searchQuery.isBlank() ||
                    session.title.contains(searchQuery, ignoreCase = true) ||
                    session.creatorName.contains(searchQuery, ignoreCase = true) ||
                    session.genre.contains(searchQuery, ignoreCase = true) ||
                    session.category.displayName.contains(searchQuery, ignoreCase = true)
                )
            }

            if (liveOnly.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp),
                        colors = CardDefaults.cardColors(containerColor = StageSurface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) "No live streams match \"$searchQuery\"" else "No streams currently live",
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    if (searchQuery.isNotBlank()) searchQuery = ""
                                    else showStartLiveDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                            ) {
                                Text(if (searchQuery.isNotBlank()) "Clear Search" else "Be the first to Start a Live Session!")
                            }
                        }
                    }
                }
            } else {
                items(liveOnly) { session ->
                    LiveSessionCard(
                        session = session,
                        onJoin = { onJoinSession(session) }
                    )
                }
            }
        }
    }

    if (showStartLiveDialog) {
        StartLiveStreamDialog(
            onDismiss = { showStartLiveDialog = false },
            onConfirm = { title, cat, genre, viz, audio, pollId ->
                onStartNewLive(title, cat, genre, viz, audio, pollId)
                showStartLiveDialog = false
            }
        )
    }
}

@Composable
private fun LiveSessionCard(
    session: LiveSession,
    onJoin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(HotMagenta.copy(alpha = 0.6f), NeonViolet.copy(alpha = 0.3f))),
                RoundedCornerShape(20.dp)
            )
            .clickable { onJoin() }
            .testTag("join_live_card_${session.id}"),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Live Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically
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
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "LIVE STAGE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .background(
                                if (session.category == PerformanceCategory.MUSIC) NeonViolet.copy(alpha = 0.2f)
                                else SpokenWordPrimary.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = session.category.displayName,
                            color = if (session.category == PerformanceCategory.MUSIC) NeonVioletLight else ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${session.spectatorCount} watching",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title & Creator
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "By ${session.creatorName} • ${session.genre}",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (session.activePollId != null) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Poll,
                            contentDescription = null,
                            tint = GoldCrown,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Active Live Voting Poll",
                            color = GoldCrown,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onJoin,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Join Stage", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun LiveStreamRoom(
    session: LiveSession,
    attachedPoll: Poll?,
    audioAmplitude: Float,
    isPlayingAudio: Boolean,
    performers: List<Performance> = emptyList(),
    sessionVotesFlow: Flow<Map<String, Int>>? = null,
    stageVotingSnapshotFlow: Flow<StageVotingSnapshot>? = null,
    chatMessagesFlow: Flow<List<LiveChatMessage>>? = null,
    onLeave: () -> Unit,
    onStopSession: () -> Unit,
    onSendMessage: (text: String, isCheer: Boolean) -> Unit,
    onVoteInPoll: (pollId: String, candidateId: String) -> Unit,
    onCastSessionVote: (sessionId: String, performerId: String, performerName: String, voteDelta: Int, voteType: String) -> Unit = { _, _, _, _, _ -> },
    onPlayCandidatePreview: (performanceId: String) -> Unit,
    onSendReaction: (com.example.model.StageReactionType) -> Unit = {},
    onToggleSlowMode: () -> Unit = {},
    onToggleChatLock: () -> Unit = {},
    onPinAnnouncement: (String?) -> Unit = {},
    onToggleHideMessage: (messageId: String, hidden: Boolean) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val stageVotingSnapshot by (stageVotingSnapshotFlow?.collectAsState(initial = null)
        ?: remember { mutableStateOf<StageVotingSnapshot?>(null) })
    val observedMessages by (chatMessagesFlow?.collectAsState(initial = session.chatMessages)
        ?: remember(session.chatMessages) { mutableStateOf(session.chatMessages) })
    val visibleChatMessages = remember(observedMessages, session.isCreatorMe) {
        if (session.isCreatorMe) observedMessages else observedMessages.filter { !it.isHiddenByHost }
    }
    val typedReactionCounts = session.reactionCounts
    var chatInputText by remember { mutableStateOf("") }
    var showPollModal by remember { mutableStateOf(false) }
    var showRealtimeVoting by remember { mutableStateOf(true) }
    var showPinAnnouncementDialog by remember { mutableStateOf(false) }
    var pinDraftText by remember(session.pinnedAnnouncement) {
        mutableStateOf(session.pinnedAnnouncement.orEmpty())
    }
    val chatListState = rememberLazyListState()

    // Auto-scroll chat to latest message
    LaunchedEffect(visibleChatMessages.size) {
        if (visibleChatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(visibleChatMessages.size - 1)
        }
    }

    FloatingReactionOverlay(modifier = modifier.fillMaxSize()) { triggerReaction ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(StageBackground)
        ) {
            // Stage Visual Area (Live Music Canvas Visualizer)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                val cheerCount = session.chatMessages.count { it.isCheer }
                val initialVisualizerMode = when (session.visualizerTheme) {
                    VisualizerTheme.NEON_WAVES -> LiveVisualizerMode.NEON_WAVES
                    VisualizerTheme.CYAN_SPECTRUM -> LiveVisualizerMode.SPECTRUM_EQ
                    VisualizerTheme.PULSE_ORB -> LiveVisualizerMode.PULSE_ORB
                    VisualizerTheme.STAGE_LIGHTS -> LiveVisualizerMode.STAGE_LASERS
                    VisualizerTheme.COSMIC_VINYL -> LiveVisualizerMode.COSMIC_VORTEX
                }

                LiveMusicAudioVisualizer(
                    amplitude = audioAmplitude,
                    isPlaying = isPlayingAudio,
                    initialMode = initialVisualizerMode,
                    showControls = false,
                    cheerTriggerCount = cheerCount,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Overlay Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LIVE badge + spectator count
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(HotMagenta)
                        )
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(text = "•", color = TextMuted)
                        Text(
                            text = "${session.spectatorCount} watching",
                            color = ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (session.isCreatorMe) {
                            Button(
                                onClick = onStopSession,
                                colors = ButtonDefaults.buttonColors(containerColor = HotMagenta),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(34.dp).testTag("end_stream_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("End Stream", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        IconButton(
                            onClick = onLeave,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                                .testTag("leave_stream_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Leave stage",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Non-overlapping Unified Bottom Control Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Performing: ${session.creatorName} • ${session.genre}",
                            style = MaterialTheme.typography.bodySmall.copy(color = ElectricCyan),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Action Pills (Real-time Live Voting + Live Poll)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Real-Time Live Audience Voting Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(listOf(HotMagenta, NeonViolet))
                                )
                                .clickable {
                                    showRealtimeVoting = !showRealtimeVoting
                                    if (showRealtimeVoting) showPollModal = false
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("toggle_realtime_voting_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (showRealtimeVoting) "Hide Votes" else "⚡ Vote Live",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Live Poll Quick Action Pill
                        if (attachedPoll != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(GoldCrown)
                                    .clickable {
                                        showPollModal = !showPollModal
                                        if (showPollModal) showRealtimeVoting = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("toggle_live_poll_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Poll,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (showPollModal) "Hide Poll" else "Live Poll (${attachedPoll.totalVotes})",
                                        color = Color.Black,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Real-Time Session Voting Panel (Audience real-time Firestore votes)
            AnimatedVisibility(visible = showRealtimeVoting) {
                val stagePerformers = remember(performers, session) {
                    if (performers.isNotEmpty()) {
                        val matching = performers.filter { it.artistId == session.creatorId || it.category == session.category }
                        if (matching.isNotEmpty()) matching.take(3) else performers.take(3)
                    } else {
                        listOf(
                            Performance(
                                id = "perf_${session.creatorId}",
                                title = session.title,
                                artistId = session.creatorId,
                                artistName = session.creatorName,
                                avatarInitials = session.creatorName.take(2).uppercase(),
                                category = session.category,
                                genre = session.genre,
                                durationSec = 180,
                                lyricsOrScript = "Live stage performance by ${session.creatorName}",
                                visualizerTheme = session.visualizerTheme,
                                audioType = session.audioType,
                                customAudioPath = null,
                                viewsCount = session.spectatorCount * 5,
                                likesCount = session.spectatorCount * 2,
                                votesCount = session.spectatorCount * 2,
                                cheersCount = session.spectatorCount
                            )
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    RealtimeSessionVotingComponent(
                        sessionId = session.id,
                        sessionTitle = session.title,
                        performers = stagePerformers,
                        sessionVotesFlow = sessionVotesFlow,
                        stageVotingSnapshot = stageVotingSnapshot,
                        onCastVote = { performerId, performerName, delta, type ->
                            onCastSessionVote(session.id, performerId, performerName, delta, type)
                        },
                        onPlayPreview = onPlayCandidatePreview
                    )
                }
            }

            // Attached Live Poll Dropdown Panel
            AnimatedVisibility(visible = showPollModal && attachedPoll != null) {
                attachedPoll?.let { poll ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        VotingPollCard(
                            poll = poll,
                            onVote = onVoteInPoll,
                            onPlayPerformance = onPlayCandidatePreview
                        )
                    }
                }
            }

            // Host Moderation Bar (visible when current user is the stage host)
            if (session.isCreatorMe) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(StageSurfaceElevated)
                        .border(1.dp, NeonVioletLight.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("live_host_moderation_bar"),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.live_mod_bar_badge),
                        color = GoldCrown,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            FilterChip(
                                selected = session.slowModeSeconds > 0,
                                onClick = onToggleSlowMode,
                                label = {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(
                                            if (session.slowModeSeconds > 0) com.example.R.string.live_mod_slow_mode_on
                                            else com.example.R.string.live_mod_slow_mode_off
                                        ),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GoldCrown.copy(alpha = 0.24f),
                                    selectedLabelColor = GoldCrown,
                                    containerColor = StageSurface,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("live_mod_slow_mode_button")
                            )
                        }
                        item {
                            FilterChip(
                                selected = session.isChatLocked,
                                onClick = onToggleChatLock,
                                label = {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(
                                            if (session.isChatLocked) com.example.R.string.live_mod_unlock_chat
                                            else com.example.R.string.live_mod_lock_chat
                                        ),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HotMagenta.copy(alpha = 0.25f),
                                    selectedLabelColor = HotMagenta,
                                    containerColor = StageSurface,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("live_mod_lock_chat_button")
                            )
                        }
                        item {
                            val hasPinned = !session.pinnedAnnouncement.isNullOrBlank()
                            FilterChip(
                                selected = hasPinned,
                                onClick = {
                                    if (hasPinned) {
                                        onPinAnnouncement(null)
                                    } else {
                                        pinDraftText = ""
                                        showPinAnnouncementDialog = true
                                    }
                                },
                                label = {
                                    Text(
                                        text = androidx.compose.ui.res.stringResource(
                                            if (hasPinned) com.example.R.string.live_mod_unpin_cta
                                            else com.example.R.string.live_mod_pin_cta
                                        ),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.22f),
                                    selectedLabelColor = ElectricCyan,
                                    containerColor = StageSurface,
                                    labelColor = TextSecondary
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("live_mod_pin_button")
                            )
                        }
                    }
                }
            }

            // Pinned Stage Announcement Banner
            if (!session.pinnedAnnouncement.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElectricCyan.copy(alpha = 0.14f))
                        .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("live_pinned_announcement_banner"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "📌 ${session.pinnedAnnouncement}",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (session.isCreatorMe) {
                        IconButton(
                            onClick = { onPinAnnouncement(null) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear pinned announcement",
                                tint = ElectricCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Real-Time Chat & Cheers Section
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Audience Chat",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (session.slowModeSeconds > 0 && !session.isChatLocked) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(
                                com.example.R.string.live_chat_slow_mode_hint,
                                session.slowModeSeconds
                            ),
                            color = GoldCrown,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("live_chat_slow_mode_hint")
                        )
                    }
                }

                // Messages List
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(visibleChatMessages, key = { it.id }) { message ->
                        ChatMessageItem(
                            message = message,
                            isHost = session.isCreatorMe,
                            onToggleHide = { hidden -> onToggleHideMessage(message.id, hidden) }
                        )
                    }
                }

                // Synchronized Crowd Reaction Bar (FIRE, MIC_DROP, OVATION, HEART, ENCORE)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    com.example.ui.components.StageCrowdReactionBar(
                        reactionCounts = typedReactionCounts,
                        onReactionSelected = { reactionType ->
                            triggerReaction(reactionType.emoji)
                            onSendReaction(reactionType)
                        }
                    )
                }

                // Quick Cheer Chips (Horizontally Scrollable)
                val quickCheerChips = listOf("🔥 Fire verse!", "👏 10/10", "Encore!! 🎵", "Voted! ✨")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(quickCheerChips) { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(StageSurfaceElevated)
                                .clickable {
                                    onSendMessage(chip, true)
                                    triggerReaction("🔥")
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(text = chip, fontSize = 11.sp, color = TextPrimary, maxLines = 1)
                        }
                    }
                }

                if (session.isChatLocked && !session.isCreatorMe) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(HotMagenta.copy(alpha = 0.16f))
                            .border(1.dp, HotMagenta.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("live_chat_locked_banner"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(com.example.R.string.live_chat_locked_banner),
                            color = TextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    // Full-Width Horizontal Chat Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInputText,
                            onValueChange = { chatInputText = it },
                            placeholder = {
                                Text(
                                    text = "Say something to the stage...",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("live_chat_input"),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = StageSurfaceElevated,
                                unfocusedContainerColor = StageSurface,
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = StageSurfaceHighlight,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (chatInputText.isNotBlank()) {
                                    onSendMessage(chatInputText.trim(), false)
                                    chatInputText = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(NeonViolet)
                                .testTag("send_live_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send Message",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPinAnnouncementDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPinAnnouncementDialog = false },
            containerColor = StageSurface,
            title = {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.live_mod_pin_dialog_title),
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                OutlinedTextField(
                    value = pinDraftText,
                    onValueChange = { if (it.length <= 180) pinDraftText = it },
                    label = {
                        Text(androidx.compose.ui.res.stringResource(com.example.R.string.live_mod_pin_input_label))
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = StageSurfaceHighlight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live_mod_pin_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPinAnnouncement(pinDraftText.trim().takeIf { it.isNotBlank() })
                        showPinAnnouncementDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("live_mod_pin_save_button")
                ) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.live_mod_pin_save_cta),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showPinAnnouncementDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ChatMessageItem(
    message: LiveChatMessage,
    isHost: Boolean = false,
    onToggleHide: ((Boolean) -> Unit)? = null
) {
    val isHidden = message.isHiddenByHost
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    isHidden -> StageSurfaceElevated.copy(alpha = 0.55f)
                    message.isCheer -> HotMagenta.copy(alpha = 0.1f)
                    else -> Color.Transparent
                }
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(message.senderAvatarColorHex)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.senderName.take(1).uppercase(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.senderName,
                        color = if (isHidden) TextMuted else ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (message.badge != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(GoldCrown, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = message.badge,
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    if (isHidden) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(HotMagenta.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(com.example.R.string.live_mod_hidden_badge),
                                color = HotMagenta,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Text(
                    text = message.text,
                    color = when {
                        isHidden -> TextMuted
                        message.isCheer -> HotMagenta
                        else -> TextPrimary
                    },
                    fontSize = 12.sp,
                    fontWeight = if (message.isCheer) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }

        if (isHost && onToggleHide != null) {
            IconButton(
                onClick = { onToggleHide(!isHidden) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(34.dp)
                    .testTag("live_mod_hide_msg_${message.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = androidx.compose.ui.res.stringResource(
                        if (isHidden) com.example.R.string.live_mod_restore_msg_desc
                        else com.example.R.string.live_mod_hide_msg_desc
                    ),
                    tint = if (isHidden) HotMagenta else TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StartLiveStreamDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, category: PerformanceCategory, genre: String, visualTheme: VisualizerTheme, audioType: SynthAudioType, pollId: String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(PerformanceCategory.MUSIC) }
    var genre by remember { mutableStateOf("Neo-Soul Live") }
    var selectedVisual by remember { mutableStateOf(VisualizerTheme.STAGE_LIGHTS) }
    var selectedAudio by remember { mutableStateOf(SynthAudioType.ACOUSTIC_SOUL) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
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
                    text = "Launch Live Stage",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                )
                Text(
                    text = "Broadcast your music or spoken word live to spectators",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Stream Title") },
                    placeholder = { Text("e.g. Midnight Poetry Jam") },
                    modifier = Modifier.fillMaxWidth().testTag("start_live_title_input"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Toggle
                Text("Performance Type", color = TextSecondary, fontSize = 12.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            selectedCategory = PerformanceCategory.MUSIC
                            genre = "Neo-Soul Live"
                            selectedAudio = SynthAudioType.ACOUSTIC_SOUL
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedCategory == PerformanceCategory.MUSIC) NeonViolet else StageSurfaceElevated
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Music", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            selectedCategory = PerformanceCategory.SPOKEN_WORD
                            genre = "Slam Poetry Live"
                            selectedAudio = SynthAudioType.POETRY_AMBIENCE
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedCategory == PerformanceCategory.SPOKEN_WORD) SpokenWordPrimary else StageSurfaceElevated
                        ),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Spoken Word", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text("Style / Sub-Category", color = TextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("start_live_style_chip_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val dialogStyles = PerformanceCategoryCatalog.stylesForCategory(selectedCategory)
                    items(dialogStyles, key = { it.id }) { styleOption ->
                        val isPicked = PerformanceCategoryCatalog.resolveStyle(
                            category = selectedCategory,
                            genre = genre
                        ) == styleOption
                        val isDarkOnAccent = styleOption == PerformanceStyle.JAZZ ||
                            styleOption == PerformanceStyle.ACOUSTIC ||
                            styleOption == PerformanceStyle.HIP_HOP
                        FilterChip(
                            selected = isPicked,
                            onClick = {
                                selectedCategory = styleOption.parentCategory
                                genre = styleOption.defaultGenre
                                selectedVisual = when (styleOption) {
                                    PerformanceStyle.RAP, PerformanceStyle.HIP_HOP -> VisualizerTheme.COSMIC_VINYL
                                    PerformanceStyle.JAZZ, PerformanceStyle.POETRY, PerformanceStyle.SPOKEN_WORD -> VisualizerTheme.STAGE_LIGHTS
                                    PerformanceStyle.ELECTRONIC -> VisualizerTheme.CYAN_SPECTRUM
                                    PerformanceStyle.SOUL_RNB, PerformanceStyle.ACOUSTIC -> VisualizerTheme.NEON_WAVES
                                }
                                selectedAudio = when (styleOption) {
                                    PerformanceStyle.RAP, PerformanceStyle.HIP_HOP -> SynthAudioType.HIPHOP_BEAT
                                    PerformanceStyle.JAZZ -> SynthAudioType.PIANO_WARMTH
                                    PerformanceStyle.POETRY, PerformanceStyle.SPOKEN_WORD -> SynthAudioType.POETRY_AMBIENCE
                                    PerformanceStyle.ELECTRONIC -> SynthAudioType.SYNTH_CHILL
                                    PerformanceStyle.SOUL_RNB, PerformanceStyle.ACOUSTIC -> SynthAudioType.ACOUSTIC_SOUL
                                }
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
                                containerColor = StageSurfaceElevated,
                                labelColor = TextSecondary,
                                iconColor = styleOption.accentColor()
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("start_live_style_${styleOption.id}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("Cancel", color = TextMuted)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val streamTitle = if (title.isBlank()) {
                                if (selectedCategory == PerformanceCategory.MUSIC) "Live Jam Session" else "Live Spoken Word Slam"
                            } else title
                            onConfirm(
                                streamTitle,
                                selectedCategory,
                                genre,
                                selectedVisual,
                                selectedAudio,
                                if (selectedCategory == PerformanceCategory.MUSIC) "poll_soul_clash" else "poll_slam_finals"
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HotMagenta),
                        modifier = Modifier.testTag("confirm_start_live_button")
                    ) {
                        Text("Go Live Now!")
                    }
                }
            }
        }
    }
}
