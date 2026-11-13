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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
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

data class PrivacyPolicySection(
    val number: String,
    val title: String,
    val icon: ImageVector,
    val accentColor: Color,
    val body: String
)

object VersePulsePrivacyPolicyContent {
    const val EFFECTIVE_DATE = "November 13, 2026"
    const val PRIVACY_CONTACT_EMAIL = "sutariabarnes@gmail.com"

    val sections: List<PrivacyPolicySection> = listOf(
        PrivacyPolicySection(
            number = "01",
            title = "Account & Profile Data Collected",
            icon = Icons.Default.PersonOutline,
            accentColor = ElectricCyan,
            body = "VersePulse collects only the account details needed to operate Creator and Spectator passes: your chosen Stage Name or Display Handle, unique user ID (uid), selected Arena role (Creator or Spectator), primary category (Music or Spoken Word), optional artist bio/social links, and—when using Google Sign-In via Android Credential Manager or an email-based Standalone pass—your email address and optional Google profile photo URL."
        ),
        PrivacyPolicySection(
            number = "02",
            title = "Audio, Video & Stage Activity",
            icon = Icons.Default.Mic,
            accentColor = NeonVioletLight,
            body = "When Creators record vocals in Creator Studio or explicitly pick music/video files via Android's system picker, VersePulse stores those recordings, media files, preview thumbnails, lyrics/scripts, live stage/event rooms, poll enrollments, spectator votes (+1/+5/+10), cheers, and 1–5 star ratings solely to power playback, live stages, polls, and leaderboards."
        ),
        PrivacyPolicySection(
            number = "03",
            title = "No Location, Ads, or Broad Storage Access",
            icon = Icons.Default.LocationOff,
            accentColor = GoldCrown,
            body = "VersePulse does NOT collect coarse, fine, or background device location at any time. The app bundles zero advertising or cross-app tracking SDKs, does not access the Android Advertising ID, never sells user data, and never requests Contacts, SMS, Call Logs, Camera feed, or broad external storage permissions."
        ),
        PrivacyPolicySection(
            number = "04",
            title = "Device Permissions (Least Privilege)",
            icon = Icons.Default.Shield,
            accentColor = SpokenWordPrimary,
            body = "• INTERNET (Install-time): Used for Firebase Authentication and real-time Cloud Firestore stage, event, and voting sync over TLS.\n• RECORD_AUDIO (Optional Runtime): Requested only when a Creator taps Record inside Creator Studio. Microphone capture never runs in the background and can be revoked anytime in Android Settings."
        ),
        PrivacyPolicySection(
            number = "05",
            title = "Encryption In Transit & At Rest",
            icon = Icons.Default.Lock,
            accentColor = ElectricCyan,
            body = "All cloud traffic is encrypted in transit using TLS/HTTPS (cleartext HTTP is blocked). On-device uploaded tracks, stage videos, preview thumbnails, vocal recordings, and Standalone account credentials are encrypted at rest via Android Keystore AES-256-GCM (VPENC1) and PBKDF2-HMAC-SHA256 password derivation."
        ),
        PrivacyPolicySection(
            number = "06",
            title = "Data Sharing & Visibility Rules",
            icon = Icons.Default.GppGood,
            accentColor = NeonVioletLight,
            body = "Google Firebase (Authentication & Cloud Firestore) acts strictly as our cloud infrastructure processor. Your private /users/{uid} account record (including email) is accessible only to you via zero-trust Firestore Security Rules. Published Creator profiles, tracks, live stages, and counted vote/rating tallies are visible to signed-in VersePulse community members."
        ),
        PrivacyPolicySection(
            number = "07",
            title = "Permanent Account & Content Deletion",
            icon = Icons.Default.DeleteSweep,
            accentColor = HotMagenta,
            body = "You can permanently delete your account and ALL associated content anytime inside the app (Top Bar Account Chip → Delete Account → Delete Permanently, or Profile → Delete Account). Confirming deletion erases your Firebase Auth or Standalone credentials, /users/{uid}, performer/creator profiles, published performances, live sessions, events, hosted stages & subcollections, votes, ratings, and local encrypted audio/video/recording files. Uninstalled users may also email $PRIVACY_CONTACT_EMAIL with subject 'VersePulse Account Deletion Request'."
        ),
        PrivacyPolicySection(
            number = "08",
            title = "Children's Privacy, Rights & Contact",
            icon = Icons.Default.Block,
            accentColor = GoldCrown,
            body = "VersePulse is intended for audiences aged 13+ and does not knowingly collect personal data from children under 13. You may view, edit, switch roles, export/copy policy disclosures, or permanently delete your data at any time. Privacy inquiries & data requests: $PRIVACY_CONTACT_EMAIL."
        )
    )

    fun buildPlainTextSummary(): String = buildString {
        appendLine("VersePulse Privacy Policy & Google Play Data Safety Disclosure")
        appendLine("Effective Date: $EFFECTIVE_DATE")
        appendLine("Privacy Contact: $PRIVACY_CONTACT_EMAIL")
        appendLine()
        sections.forEach { section ->
            appendLine("${section.number}. ${section.title}")
            appendLine(section.body)
            appendLine()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copiedToClipboard by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .testTag("privacy_policy_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = StageSurface,
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                                Brush.linearGradient(listOf(ElectricCyan, NeonViolet))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Policy,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.privacy_policy_badge),
                            color = ElectricCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = stringResource(R.string.privacy_policy_title),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = stringResource(R.string.privacy_policy_effective_date),
                            color = TextMuted,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("privacy_policy_close_icon_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.privacy_policy_close_cta),
                            tint = TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Google Play Data Safety Quick Highlights
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("privacy_policy_highlights_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DataSafetyHighlightPill(
                        icon = Icons.Default.LocationOff,
                        label = stringResource(R.string.privacy_policy_pill_no_location),
                        tint = GoldCrown
                    )
                    DataSafetyHighlightPill(
                        icon = Icons.Default.Lock,
                        label = stringResource(R.string.privacy_policy_pill_encrypted),
                        tint = ElectricCyan
                    )
                    DataSafetyHighlightPill(
                        icon = Icons.Default.Block,
                        label = stringResource(R.string.privacy_policy_pill_no_ads),
                        tint = SpokenWordPrimary
                    )
                    DataSafetyHighlightPill(
                        icon = Icons.Default.DeleteSweep,
                        label = stringResource(R.string.privacy_policy_pill_deletion),
                        tint = HotMagenta
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = StageSurfaceHighlight)
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Policy Sections
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                        .testTag("privacy_policy_scroll_container"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    VersePulsePrivacyPolicyContent.sections.forEach { section ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = StageSurfaceElevated,
                            border = BorderStroke(1.dp, section.accentColor.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = section.icon,
                                        contentDescription = null,
                                        tint = section.accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${section.number} • ${section.title}",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = section.body,
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Row: Copy, Share, Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(
                                ClipData.newPlainText(
                                    "VersePulse Privacy Policy",
                                    VersePulsePrivacyPolicyContent.buildPlainTextSummary()
                                )
                            )
                            copiedToClipboard = true
                        },
                        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.55f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("privacy_policy_copy_button")
                    ) {
                        Icon(
                            imageVector = if (copiedToClipboard) Icons.Default.Check else Icons.Default.ContentCopy,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (copiedToClipboard) {
                                stringResource(R.string.privacy_policy_copied_cta)
                            } else {
                                stringResource(R.string.privacy_policy_copy_cta)
                            },
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "VersePulse Privacy Policy")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    VersePulsePrivacyPolicyContent.buildPlainTextSummary()
                                )
                            }
                            context.startActivity(
                                Intent.createChooser(shareIntent, "Share Privacy Policy")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        border = BorderStroke(1.dp, StageSurfaceHighlight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("privacy_policy_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = stringResource(R.string.privacy_policy_share_cta),
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .defaultMinSize(minWidth = 84.dp, minHeight = 48.dp)
                            .testTag("privacy_policy_done_button")
                    ) {
                        Text(
                            text = stringResource(R.string.privacy_policy_close_cta),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DataSafetyHighlightPill(
    icon: ImageVector,
    label: String,
    tint: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
