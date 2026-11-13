package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.PerformanceCategoryCatalog
import com.example.model.PerformanceCategoryTag
import com.example.model.PerformanceStyle
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextSecondary

/**
 * Maps each [PerformanceStyle] to a distinct Material 3 icon for fast visual scanning
 * in filter chip bars and stage event badges.
 */
fun PerformanceStyle.iconVector(): ImageVector = when (this) {
    PerformanceStyle.RAP -> Icons.Default.GraphicEq
    PerformanceStyle.HIP_HOP -> Icons.Default.Equalizer
    PerformanceStyle.POETRY -> Icons.Default.AutoStories
    PerformanceStyle.JAZZ -> Icons.Default.Piano
    PerformanceStyle.SPOKEN_WORD -> Icons.Default.Mic
    PerformanceStyle.SOUL_RNB -> Icons.Default.MusicNote
    PerformanceStyle.ACOUSTIC -> Icons.Default.Headphones
    PerformanceStyle.ELECTRONIC -> Icons.Default.Album
}

@Composable
fun PerformanceStyle.localizedLabel(): String = when (this) {
    PerformanceStyle.RAP -> stringResource(R.string.category_rap)
    PerformanceStyle.HIP_HOP -> stringResource(R.string.category_hip_hop)
    PerformanceStyle.POETRY -> stringResource(R.string.category_poetry)
    PerformanceStyle.JAZZ -> stringResource(R.string.category_jazz)
    PerformanceStyle.SPOKEN_WORD -> stringResource(R.string.category_spoken_word)
    PerformanceStyle.SOUL_RNB -> stringResource(R.string.category_soul_rnb)
    PerformanceStyle.ACOUSTIC -> stringResource(R.string.category_acoustic)
    PerformanceStyle.ELECTRONIC -> stringResource(R.string.category_electronic)
}

fun PerformanceStyle.accentColor(): Color = Color(accentColorHex)

/**
 * Horizontal Material 3 Filter Chip Bar for categorizing and filtering live stage events
 * and performances (All, Rap, Poetry, Jazz, Spoken Word, Soul & R&B, Acoustic, Synth & Lo-Fi).
 */
@Composable
fun PerformanceCategoryFilterChipBar(
    selectedStyle: PerformanceStyle?,
    onSelectStyle: (PerformanceStyle?) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<PerformanceCategoryTag> = PerformanceCategoryCatalog.filterTags,
    allLabel: String = stringResource(R.string.category_filter_all),
    countsByStyle: Map<PerformanceStyle, Int>? = null,
    totalCount: Int? = null,
    showTopVotedChip: Boolean = false,
    isTopVotedSelected: Boolean = false,
    onToggleTopVoted: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp),
    rowTestTag: String = "category_filter_chip_bar",
    chipTagPrefix: String = "category_chip"
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag(rowTestTag),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(categories, key = { it.id }) { tag ->
            val style = tag.style
            val isSelected = if (style == null) {
                selectedStyle == null && !isTopVotedSelected
            } else {
                selectedStyle == style
            }

            val chipAccent = style?.accentColor() ?: NeonViolet
            val selectedTextColor = if (style == PerformanceStyle.JAZZ || style == PerformanceStyle.ACOUSTIC || style == PerformanceStyle.HIP_HOP) {
                Color.Black
            } else {
                Color.White
            }

            val baseLabel = if (style == null) allLabel else style.localizedLabel()
            val countSuffix = when {
                style == null && totalCount != null -> " ($totalCount)"
                style != null && countsByStyle != null -> {
                    val count = countsByStyle[style] ?: 0
                    if (count > 0) " ($count)" else ""
                }
                else -> ""
            }

            FilterChip(
                selected = isSelected,
                onClick = {
                    if (style == null) {
                        onSelectStyle(null)
                    } else {
                        onSelectStyle(if (selectedStyle == style) null else style)
                    }
                },
                label = {
                    Text(
                        text = "$baseLabel$countSuffix",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = style?.iconVector() ?: Icons.Default.GridView,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = chipAccent,
                    selectedLabelColor = selectedTextColor,
                    selectedLeadingIconColor = selectedTextColor,
                    containerColor = StageSurfaceElevated,
                    labelColor = TextSecondary,
                    iconColor = chipAccent
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = StageSurfaceHighlight,
                    selectedBorderColor = chipAccent,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.5.dp
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("${chipTagPrefix}_${tag.id}")
            )
        }

        if (showTopVotedChip && onToggleTopVoted != null) {
            item(key = "top_voted_chip") {
                FilterChip(
                    selected = isTopVotedSelected,
                    onClick = onToggleTopVoted,
                    label = {
                        Text(
                            text = stringResource(R.string.category_top_voted),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GoldCrown,
                        selectedLabelColor = Color.Black,
                        selectedLeadingIconColor = Color.Black,
                        containerColor = StageSurfaceElevated,
                        labelColor = TextSecondary,
                        iconColor = GoldCrown
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isTopVotedSelected,
                        borderColor = StageSurfaceHighlight,
                        selectedBorderColor = GoldCrown
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("filter_top_voted")
                )
            }
        }
    }
}

/**
 * Compact stage category pill displaying a performance or live event's [PerformanceStyle]
 * with its signature icon and accent color.
 */
@Composable
fun PerformanceStyleBadge(
    style: PerformanceStyle,
    modifier: Modifier = Modifier,
    textTestTag: String? = null
) {
    val accent = style.accentColor()
    Box(
        modifier = modifier
            .background(accent.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = style.iconVector(),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = style.localizedLabel(),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (textTestTag != null) Modifier.testTag(textTestTag) else Modifier
            )
        }
    }
}
