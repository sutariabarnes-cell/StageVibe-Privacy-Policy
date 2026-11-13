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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Performance
import com.example.model.PerformanceCategory
import com.example.model.PerformanceVoteCount
import com.example.model.Poll
import com.example.model.SynthAudioType
import com.example.model.VoteTier
import com.example.ui.components.FloatingReactionOverlay
import com.example.ui.components.LiveMusicAudioVisualizer
import com.example.ui.components.LiveVisualizerMode
import com.example.ui.components.PerformanceVisualizer
import com.example.ui.components.SpectatorCheerBar
import com.example.ui.components.StageVideoPlayer
import com.example.ui.components.VotingPollCard
import java.io.File
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
fun PlayerScreen(
    performance: Performance,
    isPlaying: Boolean,
    progressSeconds: Int,
    audioAmplitude: Float,
    attachedPoll: Poll?,
    performanceVoteState: PerformanceVoteCount? = null,
    isPinnedOffline: Boolean = false,
    onToggleOfflinePin: (() -> Unit)? = null,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onToggleLike: (String) -> Unit,
    onToggleVote: (String) -> Unit = {},
    onCastTierVote: (String, VoteTier) -> Unit = { _, _ -> },
    onCheerReaction: (String) -> Unit,
    onVoteInPoll: (pollId: String, candidateId: String) -> Unit,
    onCreatorClicked: (String) -> Unit,
    onVideoProgressChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isTeleprompterExpanded by remember { androidx.compose.runtime.mutableStateOf(true) }
    val localVideoAvailable = remember(performance.customVideoPath) {
        val path = performance.customVideoPath
        !path.isNullOrBlank() && File(path).exists()
    }
    var showVideoStage by remember(performance.id, localVideoAvailable) {
        androidx.compose.runtime.mutableStateOf(localVideoAvailable)
    }
    var videoPlaybackFailed by remember(performance.id, performance.customVideoPath) {
        androidx.compose.runtime.mutableStateOf(false)
    }

    FloatingReactionOverlay(modifier = modifier.fillMaxSize()) { triggerReaction ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(StageBackground)
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("player_close_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close Player",
                        tint = TextPrimary
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "STAGE PLAYER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = ElectricCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = performance.artistName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        ),
                        modifier = Modifier.clickable { onCreatorClicked(performance.artistId) }
                    )
                }

                IconButton(
                    onClick = { onToggleLike(performance.id) },
                    modifier = Modifier.testTag("player_like_button")
                ) {
                    Icon(
                        imageVector = if (performance.isLikedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Like",
                        tint = if (performance.isLikedByMe) HotMagenta else TextMuted
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Stage Video or Reactive Audio Visualizer Canvas
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .border(
                                1.dp,
                                Brush.horizontalGradient(listOf(NeonViolet.copy(alpha = 0.5f), ElectricCyan.copy(alpha = 0.3f))),
                                RoundedCornerShape(20.dp)
                            )
                    ) {
                        val initialMode = when (performance.visualizerTheme) {
                            com.example.model.VisualizerTheme.NEON_WAVES -> LiveVisualizerMode.NEON_WAVES
                            com.example.model.VisualizerTheme.CYAN_SPECTRUM -> LiveVisualizerMode.SPECTRUM_EQ
                            com.example.model.VisualizerTheme.PULSE_ORB -> LiveVisualizerMode.PULSE_ORB
                            com.example.model.VisualizerTheme.STAGE_LIGHTS -> LiveVisualizerMode.STAGE_LASERS
                            com.example.model.VisualizerTheme.COSMIC_VINYL -> LiveVisualizerMode.COSMIC_VORTEX
                        }

                        val isRenderingVideo = localVideoAvailable && showVideoStage && !videoPlaybackFailed

                        if (isRenderingVideo) {
                            val muteVideoAudio = !performance.customAudioPath.isNullOrBlank() ||
                                performance.audioType != SynthAudioType.UPLOADED_VIDEO
                            StageVideoPlayer(
                                videoFilePath = performance.customVideoPath.orEmpty(),
                                isPlaying = isPlaying,
                                muteAudio = muteVideoAudio,
                                onProgressSeconds = onVideoProgressChanged,
                                onPlaybackError = { videoPlaybackFailed = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            LiveMusicAudioVisualizer(
                                amplitude = audioAmplitude,
                                isPlaying = isPlaying,
                                initialMode = initialMode,
                                showControls = false,
                                cheerTriggerCount = performance.cheersCount,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        if (performance.hasVideo) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .background(Color.Black.copy(alpha = 0.68f), RoundedCornerShape(8.dp))
                                    .border(1.dp, GoldCrown.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("player_video_stage_badge")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = null,
                                        tint = GoldCrown,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.player_video_badge),
                                        color = GoldCrown,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        // Floating Video / Visualizer Mode Toggle or Theme Badge
                        if (localVideoAvailable && !videoPlaybackFailed) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .clickable { showVideoStage = !showVideoStage }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("player_visual_mode_toggle")
                            ) {
                                Text(
                                    text = if (showVideoStage) {
                                        "🎨 ${stringResource(R.string.player_toggle_visualizer)}"
                                    } else {
                                        "🎬 ${stringResource(R.string.player_toggle_video)}"
                                    },
                                    color = ElectricCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(12.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "🎨 ${performance.visualizerTheme.displayName}",
                                    color = TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Live audio status badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.VolumeUp,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = performance.audioType.displayName,
                                    color = ElectricCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Title, Artist & Category Row
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = performance.title,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "By ${performance.artistName} • ${performance.genre}",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    modifier = Modifier.clickable { onCreatorClicked(performance.artistId) }
                                )
                                if (performance.hasEncryptedMedia) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(ElectricCyan.copy(alpha = 0.14f))
                                            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                            .testTag("player_encrypted_media_badge"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = ElectricCyan,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.player_encrypted_upload_badge),
                                            color = ElectricCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .background(
                                        if (performance.category == PerformanceCategory.MUSIC) NeonViolet.copy(alpha = 0.2f)
                                        else SpokenWordPrimary.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = performance.category.displayName,
                                    color = if (performance.category == PerformanceCategory.MUSIC) NeonVioletLight else ElectricCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (onToggleOfflinePin != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isPinnedOffline) ElectricCyan.copy(alpha = 0.18f)
                                        else StageSurfaceElevated
                                    )
                                    .border(
                                        1.dp,
                                        if (isPinnedOffline) ElectricCyan.copy(alpha = 0.55f)
                                        else StageSurfaceHighlight,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { onToggleOfflinePin() }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("player_offline_pin_button"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = if (isPinnedOffline) ElectricCyan else NeonVioletLight,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(
                                        if (isPinnedOffline) R.string.offline_pinned_badge
                                        else R.string.offline_pin_cta
                                    ),
                                    color = if (isPinnedOffline) ElectricCyan else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Playback Scrubber & Times
                item {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        val duration = performance.durationSec.coerceAtLeast(1)
                        val sliderPos = (progressSeconds.toFloat() / duration).coerceIn(0f, 1f)

                        Slider(
                            value = sliderPos,
                            onValueChange = { /* scrubbing handled */ },
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan,
                                inactiveTrackColor = StageSurfaceHighlight
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val currMin = progressSeconds / 60
                            val currSec = progressSeconds % 60
                            val totalMin = duration / 60
                            val totalSec = duration % 60

                            Text(
                                text = String.format(java.util.Locale.US, "%d:%02d", currMin, currSec),
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%d:%02d", totalMin, totalSec),
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Playback Control Buttons & Spectator Cheers
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Play/Pause Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { /* -10s */ }) {
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = TextSecondary, modifier = Modifier.size(28.dp))
                            }

                            // Big Play/Pause Button
                            IconButton(
                                onClick = onTogglePlayPause,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(listOf(NeonViolet, HotMagenta))
                                    )
                                    .testTag("player_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            IconButton(onClick = { /* +10s */ }) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = TextSecondary, modifier = Modifier.size(28.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Spectator Cheer Bar
                        Text(
                            text = "Cheer Performer",
                            color = TextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        SpectatorCheerBar(
                            onCheerSelected = { emoji ->
                                triggerReaction(emoji)
                                onCheerReaction(emoji)
                            }
                        )
                    }
                }

                // Real-Time Active Performance Voting Card (StateFlow-backed)
                item {
                    val totalVotes = performanceVoteState?.totalVotes ?: performance.votesCount
                    val sharePercent = performanceVoteState?.voteSharePercent ?: 0f
                    val isVotedByMe = performanceVoteState?.isVotedByCurrentUser ?: performance.isVotedByMe
                    val isLeading = performanceVoteState?.isLeading == true

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .border(
                                1.dp,
                                Brush.horizontalGradient(
                                    if (isLeading) listOf(GoldCrown, HotMagenta)
                                    else listOf(ElectricCyan.copy(alpha = 0.6f), NeonViolet.copy(alpha = 0.6f))
                                ),
                                RoundedCornerShape(18.dp)
                            )
                            .testTag("player_realtime_voting_card"),
                        colors = CardDefaults.cardColors(containerColor = StageSurface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
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
                                        tint = if (isLeading || isVotedByMe) GoldCrown else ElectricCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f, fill = false)) {
                                        Text(
                                            text = stringResource(R.string.voting_player_section_title),
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = stringResource(R.string.voting_player_section_subtitle),
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                if (isLeading) {
                                    Box(
                                        modifier = Modifier
                                            .background(GoldCrown.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.EmojiEvents,
                                                contentDescription = null,
                                                tint = GoldCrown,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = stringResource(R.string.voting_live_stage_badge),
                                                color = GoldCrown,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$totalVotes Live Votes",
                                    color = if (isVotedByMe || isLeading) GoldCrown else ElectricCyan,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.testTag("player_live_vote_count")
                                )
                                Text(
                                    text = "${sharePercent.toInt()}% Stage Share",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            LinearProgressIndicator(
                                progress = { (sharePercent / 100f).coerceIn(0.05f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (isVotedByMe || isLeading) GoldCrown else ElectricCyan,
                                trackColor = StageSurfaceHighlight
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        triggerReaction("⚡")
                                        onToggleVote(performance.id)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isVotedByMe) GoldCrown else ElectricCyan,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("player_vote_standard_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isVotedByMe) "Voted ✓" else stringResource(R.string.voting_tier_standard),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Button(
                                    onClick = {
                                        triggerReaction("👑")
                                        onCastTierVote(performance.id, VoteTier.SUPER_VOTE)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonViolet,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("player_vote_super_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.voting_tier_super),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Button(
                                    onClick = {
                                        triggerReaction("🔥")
                                        onCastTierVote(performance.id, VoteTier.STANDING_OVATION)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HotMagenta,
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("player_vote_ovation_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocalFireDepartment,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.voting_tier_ovation),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // Attached Live Voting Poll Section (if performance is in a competition)
                if (attachedPoll != null) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text(
                                text = "🏆 Live Competition Poll",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = GoldCrownLight
                                )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            VotingPollCard(
                                poll = attachedPoll,
                                onVote = onVoteInPoll,
                                onPlayPerformance = { /* already playing or preview */ }
                            )
                        }
                    }
                }

                // Teleprompter / Lyrics & Script View
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(StageSurfaceElevated)
                                .clickable { isTeleprompterExpanded = !isTeleprompterExpanded }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (performance.category == PerformanceCategory.MUSIC) "📜 Song Lyrics & Verses" else "📜 Spoken Word Script & Teleprompter",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (isTeleprompterExpanded) "Collapse" else "Expand",
                                color = ElectricCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        AnimatedVisibility(visible = isTeleprompterExpanded) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = StageSurface),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = performance.lyricsOrScript,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = TextPrimary,
                                        lineHeight = 24.sp,
                                        fontSize = 14.sp
                                    ),
                                    modifier = Modifier.padding(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
