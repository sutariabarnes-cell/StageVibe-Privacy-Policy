package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.Performance
import com.example.model.StageVotingCounter
import com.example.model.StageVotingSnapshot
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletLight
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow

/**
 * Real-Time Session Voting Component for Live Streaming & Active Stage Sessions.
 *
 * Synchronizes audience votes in real-time via Cloud Firestore snapshot listeners,
 * rendering animated vote share bars, dynamic leader badges, and multi-tier
 * instant voting buttons (+1 Vote, +5 Super Vote, +10 Standing Ovation).
 */
@Composable
fun RealtimeSessionVotingComponent(
    sessionId: String,
    sessionTitle: String,
    performers: List<Performance>,
    sessionVotesFlow: Flow<Map<String, Int>>? = null,
    stageVotingSnapshot: StageVotingSnapshot? = null,
    onCastVote: (performerId: String, performerName: String, voteDelta: Int, voteType: String) -> Unit,
    onPlayPreview: ((performanceId: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Collect real-time votes stream from Cloud Firestore snapshot listener
    val cloudVotes by (sessionVotesFlow?.collectAsState(initial = emptyMap())
        ?: remember { mutableStateOf(emptyMap()) })

    // Track recently voted performer for celebration feedback badge
    var recentlyVotedPerformerId by remember { mutableStateOf<String?>(null) }
    var recentlyVotedLabel by remember { mutableStateOf("") }

    // Pulsing indicator for live voting session
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_live")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    // Compute effective votes combining baseline performance counts with real-time session & stage counter deltas
    val effectiveVotesMap = remember(performers, cloudVotes, stageVotingSnapshot) {
        performers.associate { perf ->
            val counterVotes = stageVotingSnapshot?.counterFor(perf.artistId)?.totalVotes?.toInt()
                ?: stageVotingSnapshot?.counterFor(perf.id)?.totalVotes?.toInt()
                ?: 0
            val cloudDelta = maxOf(
                cloudVotes[perf.id] ?: 0,
                cloudVotes[perf.artistId] ?: 0,
                counterVotes
            )
            perf.id to (perf.votesCount + cloudDelta)
        }
    }

    val totalSessionVotes = effectiveVotesMap.values.sum().coerceAtLeast(1)
    val maxVotes = effectiveVotesMap.values.maxOrNull() ?: 0
    val leaderPerformerId = if (maxVotes > 0) {
        effectiveVotesMap.filterValues { it == maxVotes }.keys.firstOrNull()
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(
                        HotMagenta.copy(alpha = 0.8f),
                        NeonViolet.copy(alpha = 0.6f),
                        ElectricCyan.copy(alpha = 0.8f)
                    )
                ),
                RoundedCornerShape(22.dp)
            )
            .testTag("realtime_session_voting_container_${sessionId}"),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Live Header with Pulsing Status Indicator
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
                            .background(
                                Brush.horizontalGradient(listOf(HotMagenta, NeonViolet)),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE AUDIENCE VOTING",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Total Real-Time Session Votes Counter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(StageSurfaceElevated, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = GoldCrown,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "$totalSessionVotes Live Votes",
                        color = GoldCrown,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = sessionTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Cast real-time votes for active performers to crown the stage champion!",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
            )

            if (stageVotingSnapshot != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("stage_voting_counters_summary_$sessionId"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(ElectricCyan.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                            .border(0.8.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (stageVotingSnapshot.isCloudSynced) {
                                stringResource(R.string.stage_counter_cloud_synced)
                            } else {
                                stringResource(R.string.stage_counter_local_mode)
                            },
                            color = ElectricCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(StageSurfaceElevated, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.stage_counter_ballots_format, stageVotingSnapshot.totalBallotsCast),
                            color = TextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    if (stageVotingSnapshot.totalCheersCount > 0) {
                        Box(
                            modifier = Modifier
                                .background(HotMagenta.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.stage_counter_cheers_format, stageVotingSnapshot.totalCheersCount),
                                color = HotMagenta,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Performer Voting List
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                performers.forEach { performer ->
                    val votes = effectiveVotesMap[performer.id] ?: performer.votesCount
                    val voteShareFraction = (votes.toFloat() / totalSessionVotes.toFloat()).coerceIn(0.05f, 1f)
                    val isLeader = performer.id == leaderPerformerId && maxVotes > 0
                    val stageCounter = stageVotingSnapshot?.counterFor(performer.artistId)
                        ?: stageVotingSnapshot?.counterFor(performer.id)

                    PerformerVotingRow(
                        performer = performer,
                        votesCount = votes,
                        voteShareFraction = voteShareFraction,
                        isLeader = isLeader,
                        stageCounter = stageCounter,
                        isRecentlyVoted = recentlyVotedPerformerId == performer.id,
                        recentVoteFeedback = if (recentlyVotedPerformerId == performer.id) recentlyVotedLabel else null,
                        onCastVote = { delta, type ->
                            recentlyVotedPerformerId = performer.id
                            recentlyVotedLabel = when (delta) {
                                1 -> "+1 Voted! ⚡"
                                5 -> "+5 Super Vote! 👑"
                                10 -> "+10 Standing Ovation! 🔥"
                                else -> "+$delta Votes!"
                            }
                            onCastVote(performer.id, performer.artistName, delta, type)
                        },
                        onPlayPreview = if (onPlayPreview != null) {
                            { onPlayPreview(performer.id) }
                        } else null
                    )
                }
            }

            // Auto-dismiss celebration badge after 2.5 seconds
            LaunchedEffect(recentlyVotedPerformerId) {
                if (recentlyVotedPerformerId != null) {
                    delay(2500)
                    recentlyVotedPerformerId = null
                }
            }
        }
    }
}

@Composable
private fun PerformerVotingRow(
    performer: Performance,
    votesCount: Int,
    voteShareFraction: Float,
    isLeader: Boolean,
    stageCounter: StageVotingCounter? = null,
    isRecentlyVoted: Boolean,
    recentVoteFeedback: String?,
    onCastVote: (voteDelta: Int, voteType: String) -> Unit,
    onPlayPreview: (() -> Unit)? = null
) {
    // Smooth animated progress bar for real-time vote share
    val animatedProgress by animateFloatAsState(
        targetValue = voteShareFraction,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "vote_share_progress"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = if (isLeader) GoldCrown.copy(alpha = 0.8f) else StageSurfaceHighlight,
        animationSpec = tween(durationMillis = 400),
        label = "border_color"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
            .testTag("performer_voting_card_${performer.id}"),
        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Performer Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Avatar circle with performer initials
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(NeonViolet, ElectricCyan)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = performer.avatarInitials.take(2),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = performer.artistName,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isLeader) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(GoldCrown.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                        .border(0.8.dp, GoldCrown, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = null,
                                            tint = GoldCrown,
                                            modifier = Modifier.size(10.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "LEADER",
                                            color = GoldCrown,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "${performer.title} • ${performer.genre}",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Audio preview button
                if (onPlayPreview != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onPlayPreview,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(StageSurfaceHighlight)
                            .testTag("preview_audio_button_${performer.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Preview Audio",
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Real-time vote share bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$votesCount Votes (${(animatedProgress * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isLeader) GoldCrown else ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                )

                AnimatedVisibility(
                    visible = isRecentlyVoted && recentVoteFeedback != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        text = recentVoteFeedback.orEmpty(),
                        color = HotMagenta,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Animated Progress Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (isLeader) listOf(GoldCrown, HotMagenta)
                                else listOf(ElectricCyan, NeonViolet)
                            )
                        )
                )
            }

            if (stageCounter != null && stageCounter.ballotsCast > 0L) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        R.string.stage_counter_tier_breakdown_format,
                        stageCounter.standardVotesCount.toInt(),
                        stageCounter.superVotesCount.toInt(),
                        stageCounter.ovationVotesCount.toInt()
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("performer_stage_counter_breakdown_${performer.id}")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Audience Multi-tier Instant Voting Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Tier 1: Standard Vote (+1)
                Button(
                    onClick = { onCastVote(1, "VOTE") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("session_vote_button_${performer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "+1 Vote",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tier 2: Super Vote (+5)
                Button(
                    onClick = { onCastVote(5, "SUPER_VOTE") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonViolet,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("session_super_vote_button_${performer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "+5 Super",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tier 3: Standing Ovation (+10)
                Button(
                    onClick = { onCastVote(10, "STANDING_OVATION") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HotMagenta,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("session_ovation_vote_button_${performer.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "+10 Ovation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
