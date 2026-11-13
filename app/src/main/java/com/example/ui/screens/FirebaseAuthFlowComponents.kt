package com.example.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AuthAccountProvider
import com.example.model.PerformanceCategory
import com.example.model.UserAccount
import com.example.model.UserRole
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

/**
 * Dynamic banner highlighting the active **Creator** or **Spectator** Firebase Authentication flow,
 * capabilities, and Firestore account provisioning targets.
 */
@Composable
fun FirebaseRoleFlowBanner(
    selectedRole: UserRole,
    modifier: Modifier = Modifier
) {
    val isCreator = selectedRole == UserRole.CREATOR
    val accent = if (isCreator) HotMagenta else ElectricCyan
    val gradientColors = if (isCreator) {
        listOf(NeonViolet.copy(alpha = 0.24f), HotMagenta.copy(alpha = 0.14f))
    } else {
        listOf(ElectricCyan.copy(alpha = 0.20f), SpokenWordPrimary.copy(alpha = 0.14f))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("auth_role_flow_banner"),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Crossfade(targetState = isCreator, label = "RoleFlowBannerCrossfade") { creatorActive ->
            val roleAccent = if (creatorActive) HotMagenta else ElectricCyan
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(gradientColors))
                    .padding(16.dp)
                    .testTag(if (creatorActive) "creator_flow_highlights" else "spectator_flow_highlights")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = roleAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (creatorActive) {
                            stringResource(R.string.auth_firebase_badge_creator)
                        } else {
                            stringResource(R.string.auth_firebase_badge_spectator)
                        },
                        color = roleAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (creatorActive) {
                        stringResource(R.string.auth_creator_flow_banner_title)
                    } else {
                        stringResource(R.string.auth_spectator_flow_banner_title)
                    },
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (creatorActive) {
                        stringResource(R.string.auth_creator_flow_banner_subtitle)
                    } else {
                        stringResource(R.string.auth_spectator_flow_banner_subtitle)
                    },
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (creatorActive) {
                        FlowCapabilityPill(
                            icon = Icons.Default.GraphicEq,
                            label = stringResource(R.string.auth_creator_perk_1),
                            accent = HotMagenta,
                            modifier = Modifier.weight(1f)
                        )
                        FlowCapabilityPill(
                            icon = Icons.Default.Mic,
                            label = stringResource(R.string.auth_creator_perk_2),
                            accent = NeonVioletLight,
                            modifier = Modifier.weight(1f)
                        )
                        FlowCapabilityPill(
                            icon = Icons.Default.EmojiEvents,
                            label = stringResource(R.string.auth_creator_perk_3),
                            accent = GoldCrown,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        FlowCapabilityPill(
                            icon = Icons.Default.HowToVote,
                            label = stringResource(R.string.auth_spectator_perk_1),
                            accent = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                        FlowCapabilityPill(
                            icon = Icons.Default.Bolt,
                            label = stringResource(R.string.auth_spectator_perk_2),
                            accent = SpokenWordPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        FlowCapabilityPill(
                            icon = Icons.Default.Star,
                            label = stringResource(R.string.auth_spectator_perk_3),
                            accent = GoldCrown,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowCapabilityPill(
    icon: ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(StageSurfaceElevated.copy(alpha = 0.88f))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Distinct **Creator** registration form flow: configures public artist stage name,
 * primary discipline (Music / Spoken Word), one-tap stage moniker presets, artist biography,
 * and a live Creator Studio Pass preview synced to `/users/{uid}` and `/performers/{uid}`.
 */
@Composable
fun CreatorRegistrationFormSection(
    stageName: String,
    onStageNameChange: (String) -> Unit,
    stageTitle: String,
    onStageTitleChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    selectedCategory: PerformanceCategory,
    onSelectCategory: (PerformanceCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = HotMagenta,
        unfocusedBorderColor = StageSurfaceHighlight,
        focusedLabelColor = HotMagenta,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = HotMagenta,
        focusedContainerColor = StageSurfaceElevated,
        unfocusedContainerColor = StageSurfaceElevated
    )

    val monikerPresets = if (selectedCategory == PerformanceCategory.MUSIC) {
        listOf("Neo-Soul Vocalist", "Beatmaker & MC", "Acoustic Songsmith")
    } else {
        listOf("Slam Poet & Lyricist", "Spoken Word Artist", "Live Cipher Host")
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("creator_registration_flow_panel"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = stageName,
            onValueChange = onStageNameChange,
            label = { Text(stringResource(R.string.auth_stage_name_label)) },
            placeholder = {
                Text("e.g., Maya Vance", color = TextMuted, fontSize = 12.sp)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = null,
                    tint = HotMagenta,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_stage_name_input")
        )

        CategoryDisciplineSelector(
            label = "Primary Performance Discipline",
            selectedCategory = selectedCategory,
            onSelectCategory = onSelectCategory
        )

        OutlinedTextField(
            value = stageTitle,
            onValueChange = onStageTitleChange,
            label = { Text(stringResource(R.string.auth_stage_title_label)) },
            placeholder = {
                Text("e.g., Neo-Soul Vocalist & Songsmith", color = TextMuted, fontSize = 12.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_stage_title_input")
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.auth_creator_presets_label),
                color = HotMagenta,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                monikerPresets.forEachIndexed { index, preset ->
                    val isSelected = stageTitle.equals(preset, ignoreCase = true)
                    AssistChip(
                        onClick = { onStageTitleChange(preset) },
                        label = {
                            Text(
                                text = preset,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) HotMagenta.copy(alpha = 0.22f) else StageSurfaceElevated,
                            labelColor = if (isSelected) Color.White else TextSecondary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) HotMagenta else StageSurfaceHighlight
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("creator_moniker_chip_$index")
                    )
                }
            }
        }

        OutlinedTextField(
            value = bio,
            onValueChange = onBioChange,
            label = { Text(stringResource(R.string.auth_bio_label)) },
            placeholder = {
                Text(
                    "Share your sound, story, or poetic vision with the audience...",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            },
            minLines = 2,
            maxLines = 4,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_bio_input")
        )

        // Live Creator Studio Pass Preview
        val previewName = stageName.trim().ifBlank { "Stage Creator" }
        val previewTitle = stageTitle.trim().ifBlank { "${selectedCategory.displayName} Performer" }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("creator_pass_preview_card"),
            colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
            border = BorderStroke(1.dp, HotMagenta.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(NeonViolet, HotMagenta))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = previewName.take(2).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auth_pass_preview_creator_badge),
                        color = HotMagenta,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = previewName,
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$previewTitle • ${selectedCategory.displayName} Studio",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Distinct **Spectator** registration form flow: streamlined front-row setup for display handle,
 * favorite stage category, interactive voting persona chips, and a live Spectator Voting Pass preview.
 */
@Composable
fun SpectatorRegistrationFormSection(
    fanHandle: String,
    onFanHandleChange: (String) -> Unit,
    personaTagline: String,
    onPersonaTaglineChange: (String) -> Unit,
    selectedCategory: PerformanceCategory,
    onSelectCategory: (PerformanceCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ElectricCyan,
        unfocusedBorderColor = StageSurfaceHighlight,
        focusedLabelColor = ElectricCyan,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = ElectricCyan,
        focusedContainerColor = StageSurfaceElevated,
        unfocusedContainerColor = StageSurfaceElevated
    )

    val spectatorPersonas = listOf(
        "Front-Row Voter",
        "Live Battle Judge",
        "Poetry & Soul Supporter"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spectator_registration_flow_panel"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = fanHandle,
            onValueChange = onFanHandleChange,
            label = { Text(stringResource(R.string.auth_fan_handle_label)) },
            placeholder = {
                Text("e.g., FrontRowSam", color = TextMuted, fontSize = 12.sp)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.HowToVote,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(18.dp)
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = fieldColors,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("register_stage_name_input")
        )

        CategoryDisciplineSelector(
            label = "Favorite Stage Category",
            selectedCategory = selectedCategory,
            onSelectCategory = onSelectCategory
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.auth_spectator_persona_label),
                color = ElectricCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                spectatorPersonas.forEachIndexed { index, persona ->
                    val isSelected = personaTagline.equals(persona, ignoreCase = true)
                    AssistChip(
                        onClick = { onPersonaTaglineChange(persona) },
                        label = {
                            Text(
                                text = persona,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) ElectricCyan.copy(alpha = 0.22f) else StageSurfaceElevated,
                            labelColor = if (isSelected) ElectricCyan else TextSecondary
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) ElectricCyan else StageSurfaceHighlight
                        ),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("spectator_persona_chip_$index")
                    )
                }
            }
        }

        // Live Spectator Voting Pass Preview
        val previewHandle = fanHandle.trim().ifBlank { "Arena Spectator" }
        val previewPersona = personaTagline.trim().ifBlank { "Stage Spectator" }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("spectator_pass_preview_card"),
            colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(ElectricCyan, SpokenWordPrimary))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = previewHandle.take(2).uppercase(),
                        color = Color.Black,
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.auth_pass_preview_spectator_badge),
                        color = ElectricCyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = previewHandle,
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "$previewPersona • +1 / +5 / +10 Stage Ballots Ready",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryDisciplineSelector(
    label: String,
    selectedCategory: PerformanceCategory,
    onSelectCategory: (PerformanceCategory) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilterChip(
                selected = selectedCategory == PerformanceCategory.MUSIC,
                onClick = { onSelectCategory(PerformanceCategory.MUSIC) },
                label = { Text("Music", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeonViolet,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                    containerColor = StageSurfaceElevated,
                    labelColor = TextSecondary,
                    iconColor = TextSecondary
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("register_category_music")
            )

            FilterChip(
                selected = selectedCategory == PerformanceCategory.SPOKEN_WORD,
                onClick = { onSelectCategory(PerformanceCategory.SPOKEN_WORD) },
                label = { Text("Spoken Word", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.RecordVoiceOver,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SpokenWordPrimary,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White,
                    containerColor = StageSurfaceElevated,
                    labelColor = TextSecondary,
                    iconColor = TextSecondary
                ),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("register_category_spoken_word")
            )
        }
    }
}

@Composable
fun GoogleCredentialActionButton(
    label: String,
    isLoading: Boolean,
    accentBrush: Brush,
    textColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = StageSurfaceHighlight
        ),
        contentPadding = PaddingValues(0.dp),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (isLoading) Brush.horizontalGradient(listOf(StageSurfaceHighlight, StageSurfaceHighlight))
                    else accentBrush,
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        color = ElectricCyan,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Verifying with Firebase Auth...",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GoogleBrandEmblemBadge()
                    Text(
                        text = label,
                        color = textColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun GoogleBrandEmblemBadge() {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val stroke = size.minDimension * 0.22f
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)

            // Red top arc
            drawArc(
                color = Color(0xFFEA4335),
                startAngle = -145f,
                sweepAngle = 105f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            // Yellow left arc
            drawArc(
                color = Color(0xFFFBBC05),
                startAngle = 135f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            // Green bottom arc
            drawArc(
                color = Color(0xFF34A853),
                startAngle = 40f,
                sweepAngle = 95f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            // Blue right arc + horizontal crossbar
            drawArc(
                color = Color(0xFF4285F4),
                startAngle = -10f,
                sweepAngle = 52f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt)
            )
            drawLine(
                color = Color(0xFF4285F4),
                start = Offset(size.width * 0.50f, size.height * 0.50f),
                end = Offset(size.width - inset * 0.4f, size.height * 0.50f),
                strokeWidth = stroke,
                cap = StrokeCap.Square
            )
        }
    }
}

/**
 * In-app Account & Arena Role Management Dialog accessible from the TopAppBar user chip.
 * Allows signed-in Creators and Spectators to inspect their verified Firebase / Standalone identity,
 * switch between Creator Studio and Spectator mode, switch Google accounts, or Sign Out cleanly.
 */
@Composable
fun AccountRoleSheetDialog(
    account: UserAccount?,
    emailFallback: String?,
    displayNameFallback: String?,
    currentRole: UserRole,
    onSwitchRole: (UserRole) -> Unit,
    onViewMyProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit,
    onSwitchGoogleAccount: (() -> Unit)? = null,
    isEncryptionEnabled: Boolean = true,
    onToggleEncryption: ((Boolean) -> Unit)? = null,
    onOpenEncryptionVault: (() -> Unit)? = null,
    isDeletingAccount: Boolean = false,
    onDeleteAccount: (() -> Unit)? = null
) {
    val name = account?.effectiveName ?: displayNameFallback ?: "Stage Member"
    val email = account?.email ?: emailFallback
    val title = account?.effectiveTitle
        ?: if (currentRole == UserRole.CREATOR) "Stage Performer" else "Stage Spectator"
    val isStandalone = account?.authProvider == AuthAccountProvider.STANDALONE
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showPlayReleaseCenter by remember { mutableStateOf(false) }
    val appCheckStatus by com.example.security.AppCheckTelemetry.status.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("account_dialog_root"),
            shape = RoundedCornerShape(22.dp),
            color = StageSurface,
            border = BorderStroke(1.dp, StageSurfaceHighlight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    if (currentRole == UserRole.CREATOR) listOf(NeonViolet, HotMagenta)
                                    else listOf(ElectricCyan, SpokenWordPrimary)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!account?.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = account?.photoUrl,
                                contentDescription = "$name Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Text(
                                text = name.take(2).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = title,
                            color = if (currentRole == UserRole.CREATOR) HotMagenta else ElectricCyan,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!email.isNullOrBlank()) {
                            Text(
                                text = email,
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close account dialog",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val badgeTint = if (isStandalone) NeonVioletLight else ElectricCyan
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(badgeTint.copy(alpha = 0.12f))
                        .border(1.dp, badgeTint.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("account_auth_provider_badge")
                ) {
                    Icon(
                        imageVector = if (isStandalone) Icons.Default.Security else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isStandalone) {
                            stringResource(R.string.auth_standalone_connected_label)
                        } else {
                            stringResource(R.string.auth_google_connected_label)
                        },
                        color = badgeTint,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (onToggleEncryption != null || onOpenEncryptionVault != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(StageSurfaceElevated)
                            .border(
                                1.dp,
                                if (isEncryptionEnabled) ElectricCyan.copy(alpha = 0.4f)
                                else StageSurfaceHighlight,
                                RoundedCornerShape(12.dp)
                            )
                            .then(
                                if (onOpenEncryptionVault != null) {
                                    Modifier.clickable {
                                        onDismiss()
                                        onOpenEncryptionVault()
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("account_encryption_row"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = if (isEncryptionEnabled) ElectricCyan else TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isEncryptionEnabled) "AES-256-GCM Encryption Enabled" else "Upload Encryption Paused",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Tap to inspect Keystore & TLS Vault",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (onToggleEncryption != null) {
                            Switch(
                                checked = isEncryptionEnabled,
                                onCheckedChange = onToggleEncryption,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = ElectricCyan,
                                    uncheckedThumbColor = TextSecondary,
                                    uncheckedTrackColor = StageSurfaceHighlight
                                ),
                                modifier = Modifier.testTag("account_encryption_switch")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = StageSurfaceHighlight)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Active Arena Role",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val targetRole = if (currentRole == UserRole.CREATOR) UserRole.SPECTATOR else UserRole.CREATOR
                OutlinedButton(
                    onClick = {
                        onSwitchRole(targetRole)
                        onDismiss()
                    },
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("account_switch_role_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SwitchAccount,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (currentRole == UserRole.CREATOR) {
                            "Switch to Spectator Voting Mode"
                        } else {
                            "Activate Creator Studio Pass"
                        },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onViewMyProfile()
                    },
                    border = BorderStroke(1.dp, NeonVioletLight.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("account_view_profile_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = NeonVioletLight,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View My Stage Profile",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                if (onSwitchGoogleAccount != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            onDismiss()
                            onSwitchGoogleAccount()
                        },
                        border = BorderStroke(1.dp, StageSurfaceHighlight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("account_switch_google_button")
                    ) {
                        GoogleBrandEmblemBadge()
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (account?.authProvider == AuthAccountProvider.STANDALONE) {
                                stringResource(R.string.auth_connect_google_account)
                            } else {
                                stringResource(R.string.auth_switch_google_account)
                            },
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        onDismiss()
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HotMagenta.copy(alpha = 0.18f),
                        contentColor = HotMagenta
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("confirm_signout_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.auth_sign_out),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }

                if (onDeleteAccount != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        enabled = !isDeletingAccount,
                        border = BorderStroke(1.dp, HotMagenta.copy(alpha = 0.65f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("account_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = null,
                            tint = HotMagenta,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isDeletingAccount) {
                                stringResource(R.string.auth_delete_account_deleting_cta)
                            } else {
                                stringResource(R.string.auth_delete_account_cta)
                            },
                            color = HotMagenta,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showPlayReleaseCenter = true },
                    border = BorderStroke(1.dp, com.example.ui.theme.GoldCrown.copy(alpha = 0.55f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("account_play_release_center_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = com.example.ui.theme.GoldCrown,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.play_release_center_cta),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { showPrivacyPolicy = true },
                    border = BorderStroke(1.dp, StageSurfaceHighlight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("account_privacy_policy_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Policy,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.privacy_policy_cta),
                        color = TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation && onDeleteAccount != null) {
        DeleteAccountConfirmationDialog(
            role = currentRole,
            isStandalone = isStandalone,
            isDeleting = isDeletingAccount,
            onConfirmDelete = {
                showDeleteConfirmation = false
                onDismiss()
                onDeleteAccount()
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }

    if (showPrivacyPolicy) {
        com.example.ui.components.PrivacyPolicyDialog(
            onDismiss = { showPrivacyPolicy = false }
        )
    }

    if (showPlayReleaseCenter) {
        com.example.ui.components.PlayReleaseCenterDialog(
            appCheckStatus = appCheckStatus,
            encryptionEnabled = isEncryptionEnabled,
            initialEmailSeed = email.orEmpty(),
            onOpenPrivacyPolicy = {
                showPlayReleaseCenter = false
                showPrivacyPolicy = true
            },
            onOpenVault = onOpenEncryptionVault?.let { openVault ->
                {
                    showPlayReleaseCenter = false
                    onDismiss()
                    openVault()
                }
            },
            onDismiss = { showPlayReleaseCenter = false }
        )
    }
}

/**
 * High-contrast confirmation modal requiring explicit confirmation before permanently deleting a
 * Creator or Spectator account (both Standalone encrypted vault & Firebase Auth + Firestore accounts).
 */
@Composable
fun DeleteAccountConfirmationDialog(
    role: UserRole,
    isStandalone: Boolean,
    isDeleting: Boolean = false,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        containerColor = StageSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        modifier = Modifier.testTag("delete_account_confirmation_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.DeleteForever,
                contentDescription = null,
                tint = HotMagenta,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.auth_delete_account_dialog_title),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (role == UserRole.CREATOR) {
                        stringResource(R.string.auth_delete_account_dialog_message_creator)
                    } else {
                        stringResource(R.string.auth_delete_account_dialog_message_spectator)
                    },
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = TextSecondary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(StageSurfaceElevated)
                        .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = if (isStandalone) Icons.Default.Security else Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = if (isStandalone) NeonVioletLight else ElectricCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isStandalone) {
                            stringResource(R.string.auth_standalone_connected_label)
                        } else {
                            "Firebase Auth • Cloud Firestore Account"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HotMagenta,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("confirm_delete_account_button")
            ) {
                Text(
                    text = if (isDeleting) {
                        stringResource(R.string.auth_delete_account_deleting_cta)
                    } else {
                        stringResource(R.string.auth_delete_account_confirm_cta)
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isDeleting,
                border = BorderStroke(1.dp, StageSurfaceHighlight),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("cancel_delete_account_button")
            ) {
                Text(
                    text = stringResource(R.string.auth_delete_account_cancel_cta),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    )
}
