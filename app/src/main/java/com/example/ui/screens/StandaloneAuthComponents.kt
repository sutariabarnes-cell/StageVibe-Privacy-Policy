package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.StandaloneAccountStore
import com.example.model.UserRole
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SpokenWordPrimary
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private enum class InlineStandaloneSubMode {
    CREATE_NEW,
    SIGN_IN_EXISTING
}

/**
 * Standalone sign-in & credential creation card embedded directly in Account Registration
 * (`RegisterModeCard`) so both **Creators (Performers)** and **Spectators** can register
 * standalone credentials or sign in with an existing standalone pass without a Google Account.
 */
@Composable
fun StandaloneRegistrationAuthSection(
    selectedRole: UserRole,
    identifier: String,
    onIdentifierChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    isLoading: Boolean,
    onRegisterStandalone: () -> Unit,
    onSignInStandaloneFromRegister: () -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var subMode by rememberSaveable { mutableStateOf(InlineStandaloneSubMode.CREATE_NEW) }

    val roleAccent = if (selectedRole == UserRole.CREATOR) HotMagenta else ElectricCyan
    val roleBrush = if (selectedRole == UserRole.CREATOR) {
        Brush.horizontalGradient(listOf(NeonViolet, HotMagenta))
    } else {
        Brush.horizontalGradient(listOf(ElectricCyan, SpokenWordPrimary))
    }
    val ctaTextColor = if (selectedRole == UserRole.CREATOR) Color.White else Color.Black

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = roleAccent,
        unfocusedBorderColor = StageSurfaceHighlight,
        focusedLabelColor = roleAccent,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = roleAccent,
        focusedContainerColor = StageSurface,
        unfocusedContainerColor = StageSurface
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("register_standalone_section"),
        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
        border = BorderStroke(1.dp, roleAccent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = roleAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.auth_standalone_badge),
                        color = roleAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(roleAccent.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (selectedRole == UserRole.CREATOR) "CREATOR PASS" else "SPECTATOR PASS",
                        color = roleAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.auth_standalone_register_section_title),
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.auth_standalone_register_section_subtitle),
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-mode toggle inside registration: Create Standalone Pass vs Direct Standalone Sign-In
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StageSurface)
                    .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(12.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StandaloneSubModeTab(
                    label = stringResource(R.string.auth_standalone_mode_create),
                    selected = subMode == InlineStandaloneSubMode.CREATE_NEW,
                    accentColor = roleAccent,
                    onClick = { subMode = InlineStandaloneSubMode.CREATE_NEW },
                    testTag = "register_standalone_mode_create",
                    modifier = Modifier.weight(1f)
                )
                StandaloneSubModeTab(
                    label = stringResource(R.string.auth_standalone_mode_signin),
                    selected = subMode == InlineStandaloneSubMode.SIGN_IN_EXISTING,
                    accentColor = roleAccent,
                    onClick = { subMode = InlineStandaloneSubMode.SIGN_IN_EXISTING },
                    testTag = "register_standalone_mode_signin",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = identifier,
                onValueChange = onIdentifierChange,
                label = { Text(stringResource(R.string.auth_standalone_identifier_label)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.auth_standalone_identifier_placeholder),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = roleAccent,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_standalone_identifier_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = {
                    Text(
                        if (subMode == InlineStandaloneSubMode.CREATE_NEW) {
                            stringResource(R.string.auth_standalone_password_label)
                        } else {
                            stringResource(R.string.auth_standalone_password_signin_label)
                        }
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.auth_standalone_password_placeholder),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = roleAccent,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("register_standalone_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_standalone_password_input")
            )

            if (subMode == InlineStandaloneSubMode.CREATE_NEW) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (password.length >= StandaloneAccountStore.MIN_PASSWORD_LENGTH) ElectricCyan else TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (password.length >= StandaloneAccountStore.MIN_PASSWORD_LENGTH) {
                            "Password ready • PBKDF2-HMAC-SHA256 + AES-256-GCM Vault"
                        } else {
                            "Minimum ${StandaloneAccountStore.MIN_PASSWORD_LENGTH} characters • Encrypted locally on device"
                        },
                        color = if (password.length >= StandaloneAccountStore.MIN_PASSWORD_LENGTH) ElectricCyan else TextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val actionLabel = if (subMode == InlineStandaloneSubMode.CREATE_NEW) {
                if (selectedRole == UserRole.CREATOR) {
                    stringResource(R.string.auth_standalone_register_creator_cta)
                } else {
                    stringResource(R.string.auth_standalone_register_spectator_cta)
                }
            } else {
                if (selectedRole == UserRole.CREATOR) {
                    stringResource(R.string.auth_standalone_signin_creator_cta)
                } else {
                    stringResource(R.string.auth_standalone_signin_spectator_cta)
                }
            }

            StandalonePrimaryActionButton(
                label = actionLabel,
                role = selectedRole,
                isCreateFlow = subMode == InlineStandaloneSubMode.CREATE_NEW,
                isLoading = isLoading,
                accentBrush = roleBrush,
                textColor = ctaTextColor,
                onClick = {
                    if (subMode == InlineStandaloneSubMode.CREATE_NEW) {
                        onRegisterStandalone()
                    } else {
                        onSignInStandaloneFromRegister()
                    }
                },
                testTag = if (subMode == InlineStandaloneSubMode.CREATE_NEW) {
                    "standalone_register_button"
                } else {
                    "register_inline_standalone_signin_button"
                }
            )
        }
    }
}

/**
 * Standalone sign-in card rendered on `SignInModeCard` supporting both **Creators (Performers)**
 * and **Spectators**.
 */
@Composable
fun StandaloneSignInSection(
    preferredRole: UserRole,
    hasExplicitRoleSelection: Boolean,
    identifier: String,
    onIdentifierChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    savedAccounts: List<StandaloneAccountStore.StandaloneAccountSummary>,
    onSelectSavedAccount: (StandaloneAccountStore.StandaloneAccountSummary) -> Unit,
    isLoading: Boolean,
    onSignInStandalone: () -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    val roleAccent = if (preferredRole == UserRole.CREATOR) HotMagenta else ElectricCyan
    val roleBrush = if (preferredRole == UserRole.CREATOR) {
        Brush.horizontalGradient(listOf(NeonViolet, HotMagenta))
    } else {
        Brush.horizontalGradient(listOf(ElectricCyan, SpokenWordPrimary))
    }
    val ctaTextColor = if (preferredRole == UserRole.CREATOR) Color.White else Color.Black

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = roleAccent,
        unfocusedBorderColor = StageSurfaceHighlight,
        focusedLabelColor = roleAccent,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary,
        cursorColor = roleAccent,
        focusedContainerColor = StageSurface,
        unfocusedContainerColor = StageSurface
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("signin_standalone_section"),
        colors = CardDefaults.cardColors(containerColor = StageSurfaceElevated),
        border = BorderStroke(1.dp, roleAccent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Key,
                    contentDescription = null,
                    tint = roleAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.auth_standalone_signin_section_title),
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.auth_standalone_signin_section_subtitle),
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            if (savedAccounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Saved Standalone Passes on Device",
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
                    savedAccounts.forEach { summary ->
                        val chipAccent = if (summary.role == UserRole.CREATOR) HotMagenta else ElectricCyan
                        AssistChip(
                            onClick = { onSelectSavedAccount(summary) },
                            label = {
                                Text(
                                    text = "${summary.displayName} • ${if (summary.role == UserRole.CREATOR) "Creator" else "Spectator"}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (summary.role == UserRole.CREATOR) Icons.Default.Mic else Icons.Default.HowToVote,
                                    contentDescription = null,
                                    tint = chipAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = StageSurface,
                                labelColor = TextPrimary
                            ),
                            border = BorderStroke(1.dp, chipAccent.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .testTag("standalone_saved_account_${summary.uid}")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = identifier,
                onValueChange = onIdentifierChange,
                label = { Text(stringResource(R.string.auth_standalone_identifier_label)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.auth_standalone_identifier_placeholder),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.AlternateEmail,
                        contentDescription = null,
                        tint = roleAccent,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("standalone_signin_identifier_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(stringResource(R.string.auth_standalone_password_signin_label)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.auth_standalone_password_placeholder),
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = roleAccent,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("standalone_signin_password_visibility")
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("standalone_signin_password_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            val buttonLabel = when {
                hasExplicitRoleSelection && preferredRole == UserRole.CREATOR ->
                    stringResource(R.string.auth_standalone_signin_creator_cta)
                hasExplicitRoleSelection && preferredRole == UserRole.SPECTATOR ->
                    stringResource(R.string.auth_standalone_signin_spectator_cta)
                else ->
                    stringResource(R.string.auth_standalone_signin_default_cta)
            }

            StandalonePrimaryActionButton(
                label = buttonLabel,
                role = preferredRole,
                isCreateFlow = false,
                isLoading = isLoading,
                accentBrush = roleBrush,
                textColor = ctaTextColor,
                onClick = onSignInStandalone,
                testTag = "standalone_signin_button"
            )
        }
    }
}

@Composable
fun AuthOrGoogleDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(
            color = StageSurfaceHighlight,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(R.string.auth_or_google_divider),
            color = TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold
        )
        HorizontalDivider(
            color = StageSurfaceHighlight,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StandaloneSubModeTab(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) accentColor.copy(alpha = 0.2f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(9.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) TextPrimary else TextSecondary,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StandalonePrimaryActionButton(
    label: String,
    role: UserRole,
    isCreateFlow: Boolean,
    isLoading: Boolean,
    accentBrush: Brush,
    textColor: Color,
    onClick: () -> Unit,
    testTag: String
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
        modifier = Modifier
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
                CircularProgressIndicator(
                    color = ElectricCyan,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isCreateFlow -> Icons.Default.PersonAdd
                            role == UserRole.CREATOR -> Icons.Default.Mic
                            else -> Icons.Default.HowToVote
                        },
                        contentDescription = null,
                        tint = textColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = label,
                        color = textColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
