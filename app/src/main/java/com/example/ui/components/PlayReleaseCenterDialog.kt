package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.BuildConfig
import com.example.R
import com.example.security.AppCheckStatusSnapshot
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletLight
import com.example.ui.theme.StageBackground
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

data class InvalidTesterEntry(
    val raw: String,
    val reason: String
)

data class PlayTesterValidationResult(
    val validEmails: List<String>,
    val invalidEntries: List<InvalidTesterEntry>,
    val formattedCsv: String
)

/**
 * Validates closed-track tester email lists for Google Play Console so creators avoid
 * Play Console email-list sync errors caused by malformed entries or non-Google consumer webmail domains.
 */
object PlayTesterEmailValidator {

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    private val NON_GOOGLE_CONSUMER_DOMAINS = setOf(
        "yahoo.com",
        "ymail.com",
        "rocketmail.com",
        "hotmail.com",
        "outlook.com",
        "live.com",
        "msn.com",
        "icloud.com",
        "me.com",
        "mac.com",
        "aol.com",
        "protonmail.com",
        "proton.me",
        "zoho.com",
        "gmx.com",
        "mail.com",
        "yandex.com"
    )

    fun validateTesterInput(rawInput: String): PlayTesterValidationResult {
        if (rawInput.isBlank()) {
            return PlayTesterValidationResult(
                validEmails = emptyList(),
                invalidEntries = emptyList(),
                formattedCsv = ""
            )
        }

        val tokens = rawInput
            .split(Regex("[,;\\s\\n\\r]+"))
            .map { it.trim().lowercase(Locale.US) }
            .filter { it.isNotBlank() }

        val validSet = LinkedHashSet<String>()
        val invalidList = mutableListOf<InvalidTesterEntry>()
        val seenInvalid = HashSet<String>()

        for (token in tokens) {
            if (!EMAIL_REGEX.matches(token)) {
                if (seenInvalid.add(token)) {
                    invalidList.add(
                        InvalidTesterEntry(
                            raw = token,
                            reason = "Malformed email address"
                        )
                    )
                }
                continue
            }

            val domain = token.substringAfter('@', "")
            if (domain in NON_GOOGLE_CONSUMER_DOMAINS) {
                if (seenInvalid.add(token)) {
                    invalidList.add(
                        InvalidTesterEntry(
                            raw = token,
                            reason = "Non-Google consumer domain (@$domain) — Play closed tracks require @gmail.com or Google Workspace"
                        )
                    )
                }
                continue
            }

            validSet.add(token)
        }

        val validList = validSet.toList()
        return PlayTesterValidationResult(
            validEmails = validList,
            invalidEntries = invalidList,
            formattedCsv = validList.joinToString(", ")
        )
    }
}

/**
 * Interactive Play Release & Tester Readiness Center (Pillar 2) guiding pre-publish health checks
 * and Google Account closed-track tester email validation.
 */
@Composable
fun PlayReleaseCenterDialog(
    appCheckStatus: AppCheckStatusSnapshot,
    encryptionEnabled: Boolean = true,
    initialEmailSeed: String = "",
    onOpenPrivacyPolicy: (() -> Unit)? = null,
    onOpenVault: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var testerEmailsInput by remember {
        mutableStateOf(
            initialEmailSeed.takeIf { it.isNotBlank() } ?: ""
        )
    }
    var hasCopiedCsv by remember { mutableStateOf(false) }

    val validation = remember(testerEmailsInput) {
        PlayTesterEmailValidator.validateTesterInput(testerEmailsInput)
    }

    val appCheckReady = appCheckStatus.isConfigured || appCheckStatus.siteKeyValid
    val versionReady = BuildConfig.VERSION_CODE >= 3
    val readyChecksCount = listOf(appCheckReady, true, true, versionReady && encryptionEnabled).count { it }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("play_release_center_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = StageSurface,
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(GoldCrown.copy(alpha = 0.65f), ElectricCyan.copy(alpha = 0.55f), NeonViolet.copy(alpha = 0.55f))
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(GoldCrown.copy(alpha = 0.28f), ElectricCyan.copy(alpha = 0.28f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RocketLaunch,
                            contentDescription = null,
                            tint = GoldCrown,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.play_release_center_badge),
                            color = GoldCrown,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = stringResource(R.string.play_release_center_title),
                            color = TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = stringResource(R.string.play_release_center_subtitle),
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
                            contentDescription = "Close Play Release Center",
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Readiness Summary Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StageSurfaceElevated)
                        .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$readyChecksCount/4 Core Launch Checks Ready",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        color = GoldCrown,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Checklist Rows
                PlayCheckRow(
                    title = stringResource(R.string.play_check_appcheck_title),
                    subtitle = stringResource(R.string.play_check_appcheck_desc),
                    ready = appCheckReady,
                    actionLabel = if (onOpenVault != null) "Vault" else null,
                    onAction = onOpenVault
                )
                Spacer(modifier = Modifier.height(8.dp))

                PlayCheckRow(
                    title = stringResource(R.string.play_check_privacy_title),
                    subtitle = stringResource(R.string.play_check_privacy_desc),
                    ready = true,
                    actionLabel = if (onOpenPrivacyPolicy != null) "Policy" else null,
                    onAction = onOpenPrivacyPolicy
                )
                Spacer(modifier = Modifier.height(8.dp))

                PlayCheckRow(
                    title = stringResource(R.string.play_check_deletion_title),
                    subtitle = stringResource(R.string.play_check_deletion_desc),
                    ready = true
                )
                Spacer(modifier = Modifier.height(8.dp))

                PlayCheckRow(
                    title = stringResource(R.string.play_check_r8_title),
                    subtitle = stringResource(R.string.play_check_r8_desc),
                    ready = versionReady
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = StageSurfaceHighlight)
                Spacer(modifier = Modifier.height(14.dp))

                // Closed-Track Google Account Validator Section
                Text(
                    text = stringResource(R.string.play_tester_section_title),
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = stringResource(R.string.play_tester_section_subtitle),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = testerEmailsInput,
                    onValueChange = {
                        testerEmailsInput = it
                        hasCopiedCsv = false
                    },
                    label = { Text(stringResource(R.string.play_tester_input_label), fontSize = 12.sp) },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.play_tester_input_placeholder),
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .testTag("play_tester_email_input"),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = StageBackground,
                        unfocusedContainerColor = StageBackground,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = StageSurfaceHighlight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Validation Status Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.play_tester_valid_count_format,
                            validation.validEmails.size,
                            validation.invalidEntries.size
                        ),
                        color = if (validation.invalidEntries.isNotEmpty()) HotMagenta else ElectricCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("play_tester_validation_summary")
                    )

                    if (validation.invalidEntries.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                testerEmailsInput = validation.formattedCsv
                                hasCopiedCsv = false
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("play_tester_remove_invalid_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = null,
                                tint = HotMagenta,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.play_tester_remove_invalid_cta),
                                color = HotMagenta,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                if (validation.invalidEntries.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(HotMagenta.copy(alpha = 0.12f))
                            .border(1.dp, HotMagenta.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                            .testTag("play_tester_invalid_list"),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        validation.invalidEntries.take(4).forEach { invalid ->
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = HotMagenta,
                                    modifier = Modifier
                                        .size(14.dp)
                                        .padding(top = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${invalid.raw}: ${invalid.reason}",
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Copy & Share Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText("VersePulse Play Testers", validation.formattedCsv)
                            )
                            hasCopiedCsv = true
                        },
                        enabled = validation.validEmails.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("play_tester_copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hasCopiedCsv) {
                                stringResource(R.string.play_tester_copied_cta)
                            } else {
                                stringResource(R.string.play_tester_copy_list_cta)
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val inviteBody = buildString {
                                appendLine("You're invited to test VersePulse on Google Play Closed Track!")
                                appendLine("• Sign in on Google Play with an active @gmail.com or Google Workspace account.")
                                if (validation.formattedCsv.isNotBlank()) {
                                    appendLine("• Approved Tester List: ${validation.formattedCsv}")
                                }
                            }
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "VersePulse Closed-Track Beta Invite")
                                putExtra(Intent.EXTRA_TEXT, inviteBody)
                            }
                            runCatching {
                                context.startActivity(
                                    Intent.createChooser(shareIntent, "Share VersePulse Tester Invite")
                                )
                            }
                        },
                        border = BorderStroke(1.dp, NeonVioletLight.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("play_tester_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.play_tester_share_invite_cta),
                            tint = NeonVioletLight,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    border = BorderStroke(1.dp, StageSurfaceHighlight),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("play_release_close_button")
                ) {
                    Text(
                        text = stringResource(R.string.play_release_close_cta),
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
private fun PlayCheckRow(
    title: String,
    subtitle: String,
    ready: Boolean,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val tint = if (ready) ElectricCyan else GoldCrown
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StageSurfaceElevated)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.Security,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Text(
                    text = actionLabel,
                    color = ElectricCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
