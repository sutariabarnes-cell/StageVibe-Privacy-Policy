package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EnhancedEncryption
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.security.UploadEncryptionManager
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
import java.util.Locale

/**
 * Interactive Material 3 Security & Encryption Card rendered on the Creator/User Profile screen.
 */
@Composable
fun EncryptionControlCard(
    status: UploadEncryptionManager.EncryptionStatusSnapshot,
    encryptedPortfolioTracksCount: Int = 0,
    onToggleEncryption: (Boolean) -> Unit,
    onVerifyAndEncryptVault: () -> Unit,
    onOpenVaultDetails: (() -> Unit)? = null,
    cardTestTag: String = "profile_encryption_card",
    switchTestTag: String = "profile_encryption_switch",
    verifyButtonTestTag: String = "profile_verify_vault_button",
    modifier: Modifier = Modifier
) {
    val accentColor = if (status.isEnabled) ElectricCyan else GoldCrown
    val toggleDesc = stringResource(R.string.encryption_master_toggle_title)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(cardTestTag),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.horizontalGradient(
                if (status.isEnabled) {
                    listOf(ElectricCyan.copy(alpha = 0.55f), NeonViolet.copy(alpha = 0.55f))
                } else {
                    listOf(GoldCrown.copy(alpha = 0.55f), StageSurfaceHighlight)
                }
            )
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (status.isEnabled) Icons.Default.EnhancedEncryption else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.profile_encryption_card_badge),
                                color = accentColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accentColor.copy(alpha = 0.20f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        if (status.isEnabled) R.string.encryption_status_enabled_pill
                                        else R.string.encryption_status_paused_pill
                                    ),
                                    color = accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Text(
                            text = stringResource(R.string.profile_encryption_card_title),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Switch(
                    checked = status.isEnabled,
                    onCheckedChange = onToggleEncryption,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = ElectricCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = StageSurfaceHighlight
                    ),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics { contentDescription = toggleDesc }
                        .testTag(switchTestTag)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(
                    if (status.isEnabled) R.string.encryption_master_toggle_on_desc
                    else R.string.encryption_master_toggle_off_desc
                ),
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StageSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VaultStatMiniColumn(
                    label = "Cipher",
                    value = status.algorithm,
                    valueColor = ElectricCyan
                )
                VaultStatMiniColumn(
                    label = "Transport",
                    value = if (status.tlsTransportEnforced) "TLS Only" else "Standard",
                    valueColor = NeonVioletLight
                )
                VaultStatMiniColumn(
                    label = "Vault Files",
                    value = "${maxOf(status.encryptedFilesCount, encryptedPortfolioTracksCount)}",
                    valueColor = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onVerifyAndEncryptVault,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan.copy(alpha = 0.18f),
                        contentColor = ElectricCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag(verifyButtonTestTag)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.encryption_vault_verify_cta),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onOpenVaultDetails != null) {
                    OutlinedButton(
                        onClick = onOpenVaultDetails,
                        border = BorderStroke(1.dp, NeonVioletLight.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("profile_open_vault_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = NeonVioletLight,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Details",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultStatMiniColumn(
    label: String,
    value: String,
    valueColor: Color
) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label,
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Full Encryption & Security Vault Dialog accessible from any screen via the TopAppBar shield,
 * Account dialog, Creator Studio banner, or Profile security card.
 */
@Composable
fun EncryptionVaultDialog(
    status: UploadEncryptionManager.EncryptionStatusSnapshot,
    onToggleEncryption: (Boolean) -> Unit,
    onVerifyAndEncryptVault: () -> Unit,
    onAdvanceRolloutStage: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val accentColor = if (status.isEnabled) ElectricCyan else GoldCrown
    val toggleDesc = stringResource(R.string.encryption_master_toggle_title)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("encryption_vault_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = StageSurface,
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(listOf(ElectricCyan.copy(alpha = 0.55f), NeonViolet.copy(alpha = 0.55f)))
            )
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
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    if (status.isEnabled) listOf(ElectricCyan.copy(alpha = 0.25f), NeonViolet.copy(alpha = 0.35f))
                                    else listOf(GoldCrown.copy(alpha = 0.25f), HotMagenta.copy(alpha = 0.20f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (status.isEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.encryption_vault_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                        Text(
                            text = stringResource(R.string.encryption_vault_subtitle),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Encryption Vault dialog",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Master Toggle Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StageSurfaceElevated)
                        .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.encryption_master_toggle_title),
                                color = TextPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(
                                if (status.isEnabled) R.string.encryption_master_toggle_on_desc
                                else R.string.encryption_master_toggle_off_desc
                            ),
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }

                    Switch(
                        checked = status.isEnabled,
                        onCheckedChange = onToggleEncryption,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = ElectricCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = StageSurfaceHighlight
                        ),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .semantics { contentDescription = toggleDesc }
                            .testTag("vault_encryption_master_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = StageSurfaceHighlight)
                Spacer(modifier = Modifier.height(12.dp))

                VaultProtectionLayerRow(
                    icon = Icons.Default.EnhancedEncryption,
                    label = stringResource(R.string.encryption_layer_at_rest_label),
                    value = stringResource(R.string.encryption_layer_at_rest_value),
                    active = status.isEnabled
                )
                Spacer(modifier = Modifier.height(10.dp))

                VaultProtectionLayerRow(
                    icon = Icons.Default.Key,
                    label = "Master Key Protection",
                    value = status.keyBackingLabel,
                    active = true
                )
                Spacer(modifier = Modifier.height(10.dp))

                VaultProtectionLayerRow(
                    icon = Icons.Default.CloudDone,
                    label = stringResource(R.string.encryption_layer_cloud_label),
                    value = stringResource(R.string.encryption_layer_cloud_value),
                    active = status.isEnabled
                )
                Spacer(modifier = Modifier.height(10.dp))

                VaultProtectionLayerRow(
                    icon = Icons.Default.Security,
                    label = stringResource(R.string.encryption_layer_transport_label),
                    value = stringResource(R.string.encryption_layer_transport_value),
                    active = status.tlsTransportEnforced
                )
                Spacer(modifier = Modifier.height(10.dp))

                val appCheckStatus by com.example.security.AppCheckTelemetry.status.collectAsStateWithLifecycle()
                VaultProtectionLayerRow(
                    icon = Icons.Default.VerifiedUser,
                    label = stringResource(R.string.encryption_layer_appcheck_label),
                    value = if (appCheckStatus.isConfigured) {
                        appCheckStatus.providerName
                    } else {
                        stringResource(R.string.encryption_layer_appcheck_unconfigured)
                    },
                    active = appCheckStatus.isConfigured
                )
                Spacer(modifier = Modifier.height(10.dp))

                VaultProtectionLayerRow(
                    icon = Icons.Default.CheckCircle,
                    label = "Protected Local Vault Storage",
                    value = "${status.encryptedFilesCount} encrypted file(s) (${formatVaultBytes(status.encryptedBytesTotal)})",
                    active = true
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = StageSurfaceHighlight)
                Spacer(modifier = Modifier.height(12.dp))

                // Phased App Check Rollout & Live Attestation Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StageSurfaceElevated)
                        .border(1.dp, NeonVioletLight.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                        .testTag("vault_appcheck_rollout_section")
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.appcheck_rollout_section_badge),
                            color = ElectricCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        val stagePillText = when (appCheckStatus.rolloutStage) {
                            com.example.security.AppCheckRolloutStage.CLIENT_ATTESTATION_ACTIVE ->
                                stringResource(R.string.appcheck_verified_pill)
                            com.example.security.AppCheckRolloutStage.CLOUD_METRICS_MONITORING ->
                                stringResource(R.string.appcheck_monitoring_pill)
                            com.example.security.AppCheckRolloutStage.STRICT_ENFORCEMENT_READY ->
                                stringResource(R.string.appcheck_enforced_pill)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ElectricCyan.copy(alpha = 0.18f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("vault_appcheck_stage_pill")
                        ) {
                            Text(
                                text = stagePillText,
                                color = ElectricCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.appcheck_rollout_section_title),
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            if (appCheckStatus.siteKeyValid) R.string.appcheck_site_key_valid_label
                            else R.string.appcheck_site_key_missing_label
                        ),
                        color = if (appCheckStatus.siteKeyValid) ElectricCyan else GoldCrown,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("vault_appcheck_site_key_status")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val currentStep = appCheckStatus.rolloutStage.stepNumber
                    listOf(
                        Triple(
                            1,
                            stringResource(R.string.appcheck_rollout_stage_1_title),
                            stringResource(R.string.appcheck_rollout_stage_1_desc)
                        ),
                        Triple(
                            2,
                            stringResource(R.string.appcheck_rollout_stage_2_title),
                            stringResource(R.string.appcheck_rollout_stage_2_desc)
                        ),
                        Triple(
                            3,
                            stringResource(R.string.appcheck_rollout_stage_3_title),
                            stringResource(R.string.appcheck_rollout_stage_3_desc)
                        )
                    ).forEach { (stepNum, stageTitle, stageDesc) ->
                        val isReached = currentStep >= stepNum
                        val isActiveStage = currentStep == stepNum
                        val rowTint = when {
                            isActiveStage -> ElectricCyan
                            isReached -> NeonVioletLight
                            else -> TextMuted
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("vault_rollout_stage_row_$stepNum"),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isReached) Icons.Default.CheckCircle else Icons.Default.Security,
                                contentDescription = null,
                                tint = rowTint,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(15.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stageTitle,
                                    color = if (isActiveStage) ElectricCyan else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isActiveStage) FontWeight.ExtraBold else FontWeight.Bold
                                )
                                Text(
                                    text = stageDesc,
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    if (onAdvanceRolloutStage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onAdvanceRolloutStage,
                            border = BorderStroke(1.dp, NeonVioletLight.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("vault_advance_rollout_button")
                        ) {
                            Text(
                                text = stringResource(R.string.appcheck_advance_stage_cta),
                                color = NeonVioletLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onVerifyAndEncryptVault,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("vault_verify_encrypt_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.appcheck_verify_cta),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, StageSurfaceHighlight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("vault_close_button")
                ) {
                    Text(
                        text = stringResource(R.string.encryption_vault_close_cta),
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultProtectionLayerRow(
    icon: ImageVector,
    label: String,
    value: String,
    active: Boolean
) {
    val tint = if (active) ElectricCyan else GoldCrown
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatVaultBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return String.format(Locale.US, "%.1f KB", kb)
    }
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}
