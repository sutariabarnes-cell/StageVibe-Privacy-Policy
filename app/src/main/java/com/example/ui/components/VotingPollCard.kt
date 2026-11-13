package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.model.PerformanceCategory
import com.example.model.Poll
import com.example.model.PollCandidate
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletLight
import com.example.ui.theme.SpokenWordPrimary
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun VotingPollCard(
    poll: Poll,
    onVote: (pollId: String, candidateId: String) -> Unit,
    onPlayPerformance: (performanceId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(NeonViolet.copy(alpha = 0.5f), ElectricCyan.copy(alpha = 0.3f))),
                RoundedCornerShape(20.dp)
            )
            .testTag("poll_card_${poll.id}"),
        colors = CardDefaults.cardColors(containerColor = StageSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .animateContentSize()
        ) {
            // Header: Badge + Category + Timer
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
                                Brush.horizontalGradient(listOf(NeonViolet, HotMagenta)),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = poll.badge,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                if (poll.category == PerformanceCategory.MUSIC) NeonViolet.copy(alpha = 0.2f)
                                else SpokenWordPrimary.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = poll.category.displayName,
                            color = if (poll.category == PerformanceCategory.MUSIC) NeonVioletLight else ElectricCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Time remaining",
                        tint = GoldCrown,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${poll.endsInHours}h left",
                        color = GoldCrown,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Poll Title & Description
            Text(
                text = poll.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    lineHeight = 22.sp
                )
            )

            if (poll.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = poll.description,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Candidates with live percentage bars
            val totalVotes = poll.totalVotes.coerceAtLeast(1)

            poll.candidates.forEach { candidate ->
                val isVoted = poll.userVotedCandidateId == candidate.candidateId
                val percentage = (candidate.votesCount.toFloat() / totalVotes.toFloat())
                val animatedPercentage by animateFloatAsState(targetValue = percentage, label = "vote_percent")

                PollCandidateRow(
                    candidate = candidate,
                    percentage = animatedPercentage,
                    rawPercentageInt = (percentage * 100).toInt(),
                    isVoted = isVoted,
                    onVoteClicked = { onVote(poll.id, candidate.candidateId) },
                    onPlayClicked = { onPlayPerformance(candidate.performanceId) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Total votes counter summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${poll.totalVotes} total spectator votes",
                    color = TextMuted,
                    fontSize = 12.sp
                )
                if (poll.userVotedCandidateId != null) {
                    Text(
                        text = "✓ Vote recorded",
                        color = ElectricCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PollCandidateRow(
    candidate: PollCandidate,
    percentage: Float,
    rawPercentageInt: Int,
    isVoted: Boolean,
    onVoteClicked: () -> Unit,
    onPlayClicked: () -> Unit
) {
    val candidateColor = Color(candidate.avatarColorHex)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(StageSurfaceElevated)
            .border(
                width = if (isVoted) 1.5.dp else 1.dp,
                color = if (isVoted) ElectricCyan else StageSurfaceHighlight,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onVoteClicked() }
            .testTag("candidate_row_${candidate.candidateId}")
    ) {
        // Percentage background progress fill
        val progressModifier = if (isVoted) {
            Modifier.background(Brush.horizontalGradient(listOf(ElectricCyan.copy(alpha = 0.22f), NeonViolet.copy(alpha = 0.28f))))
        } else {
            Modifier.background(candidateColor.copy(alpha = 0.12f))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction = percentage.coerceIn(0.02f, 1f))
                .matchParentSize()
                .then(progressModifier)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play audio button
                IconButton(
                    onClick = onPlayClicked,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(candidateColor.copy(alpha = 0.2f))
                        .testTag("play_candidate_${candidate.candidateId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Listen to ${candidate.title}",
                        tint = candidateColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = candidate.artistName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isVoted) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Your Vote",
                                tint = ElectricCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = candidate.title,
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Percentage & Vote Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$rawPercentageInt%",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isVoted) ElectricCyan else TextPrimary
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "${candidate.votesCount} votes",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextMuted, fontSize = 11.sp),
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onVoteClicked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isVoted) ElectricCyan else StageSurfaceHighlight,
                        contentColor = if (isVoted) Color.Black else TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("vote_button_${candidate.candidateId}")
                ) {
                    Text(
                        text = if (isVoted) "Voted" else "Vote",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
