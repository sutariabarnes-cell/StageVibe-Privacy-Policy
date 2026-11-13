package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.StageSurfaceElevated
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

data class ReactionBubble(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val initialXOffset: Float,
    val animProgress: Animatable<Float, *> = Animatable(0f)
)

@Composable
fun FloatingReactionOverlay(
    modifier: Modifier = Modifier,
    content: @Composable (triggerReaction: (String) -> Unit) -> Unit
) {
    val reactions = remember { mutableStateListOf<ReactionBubble>() }
    val scope = rememberCoroutineScope()

    val triggerReaction: (String) -> Unit = { emoji ->
        val bubble = ReactionBubble(
            emoji = emoji,
            initialXOffset = (Random.nextFloat() * 120f) - 60f
        )
        reactions.add(bubble)
        scope.launch {
            bubble.animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1800)
            )
            reactions.remove(bubble)
        }
    }

    Box(modifier = modifier) {
        content(triggerReaction)

        // Floating particles container
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 72.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            reactions.forEach { bubble ->
                val progress = bubble.animProgress.value
                val yOffset = (-progress * 260).dp
                val xWiggle = (bubble.initialXOffset + kotlin.math.sin(progress * 6f) * 20f).dp
                val alpha = (1f - progress).coerceIn(0f, 1f)

                Text(
                    text = bubble.emoji,
                    fontSize = (26 + (1f - progress) * 10).sp,
                    modifier = Modifier
                        .offset { IntOffset(xWiggle.roundToPx(), yOffset.roundToPx()) }
                        .alpha(alpha)
                )
            }
        }
    }
}

@Composable
fun SpectatorCheerBar(
    onCheerSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val emojis = listOf("👏", "🔥", "🤯", "💖", "🎤")

    Row(
        modifier = modifier
            .background(StageSurfaceElevated.copy(alpha = 0.85f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        emojis.forEach { emoji ->
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable { onCheerSelected(emoji) }
                    .testTag("cheer_button_$emoji"),
                contentAlignment = Alignment.Center
            ) {
                Text(text = emoji, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun StageCrowdReactionBar(
    reactionCounts: Map<com.example.model.StageReactionType, Int>,
    onReactionSelected: (com.example.model.StageReactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(StageSurfaceElevated.copy(alpha = 0.90f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("live_crowd_reaction_bar"),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.example.model.StageReactionType.entries.forEach { reactionType ->
            val count = reactionCounts[reactionType] ?: 0
            val accentColor = Color(reactionType.accentColorHex)
            Row(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .clickable { onReactionSelected(reactionType) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("live_reaction_chip_${reactionType.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(text = reactionType.emoji, fontSize = 15.sp)
                Text(
                    text = "$count",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                )
            }
        }
    }
}

