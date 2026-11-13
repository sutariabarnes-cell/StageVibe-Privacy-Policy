package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.auth.StandaloneAccountStore
import com.example.model.AuthAccountProvider
import com.example.model.AuthFlowMode
import com.example.model.AuthSessionState
import com.example.model.PerformanceCategory
import com.example.model.RegistrationDraft
import com.example.model.UserRole
import com.example.ui.components.PrivacyPolicyDialog
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.SpokenWordPrimary
import com.example.ui.theme.StageBackground
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AuthScreen(
    sessionState: AuthSessionState,
    isLoading: Boolean,
    errorMessage: String?,
    onDismissError: () -> Unit,
    onSignInWithGoogle: (preferredRoleIfNew: UserRole) -> Unit,
    onRegisterWithGoogle: (draft: RegistrationDraft) -> Unit,
    onCompleteRegistration: (draft: RegistrationDraft) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    onSignInWithGoogleExplicit: ((preferredRole: UserRole, isExplicitRoleSelection: Boolean) -> Unit)? = null,
    onSignInStandalone: (identifier: String, password: String, explicitRole: UserRole?) -> Unit = { _, _, _ -> },
    onRegisterStandalone: (draft: RegistrationDraft, identifier: String, password: String) -> Unit = { _, _, _ -> },
    statusMessage: String? = null,
    onDismissStatus: () -> Unit = {}
) {
    val context = LocalContext.current
    var flowMode by rememberSaveable { mutableStateOf(AuthFlowMode.SIGN_IN) }
    var selectedRole by rememberSaveable { mutableStateOf(UserRole.SPECTATOR) }
    var hasExplicitSignInRoleSelection by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicy by rememberSaveable { mutableStateOf(false) }

    var stageName by rememberSaveable { mutableStateOf("") }
    var creatorStageTitle by rememberSaveable { mutableStateOf("") }
    var spectatorPersona by rememberSaveable { mutableStateOf("Front-Row Voter") }
    var bio by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf(PerformanceCategory.MUSIC) }

    var signInIdentifier by rememberSaveable { mutableStateOf("") }
    var signInPassword by rememberSaveable { mutableStateOf("") }
    var registerIdentifier by rememberSaveable { mutableStateOf("") }
    var registerPassword by rememberSaveable { mutableStateOf("") }

    val savedStandaloneAccounts = remember(flowMode, sessionState, errorMessage) {
        runCatching { StandaloneAccountStore.listRegisteredSummaries(context) }.getOrDefault(emptyList())
    }

    val needsRegState = sessionState as? AuthSessionState.NeedsRegistration
    LaunchedEffect(needsRegState?.uid) {
        if (needsRegState != null) {
            selectedRole = needsRegState.initialRole
            if (stageName.isBlank()) {
                stageName = needsRegState.suggestedName
            }
        }
    }

    val scrollState = rememberScrollState()
    val topGlowColor = if (selectedRole == UserRole.CREATOR) {
        Color(0xFF230E30)
    } else {
        Color(0xFF0C1C2B)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        topGlowColor,
                        StageBackground,
                        Color(0xFF07060D)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("auth_screen_root"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Arena Hero Header (dynamic Creator vs Spectator emblem accent)
            AuthArenaHeroHeader(selectedRole = selectedRole)

            Spacer(modifier = Modifier.height(18.dp))

            // 1b. Status Banner (e.g., Account permanently deleted)
            AnimatedVisibility(
                visible = !statusMessage.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("auth_status_banner"),
                    colors = CardDefaults.cardColors(containerColor = ElectricCyan.copy(alpha = 0.14f)),
                    border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Account status",
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = statusMessage.orEmpty(),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissStatus,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss status",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 2. Error / Diagnostic Banner
            AnimatedVisibility(
                visible = !errorMessage.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("auth_error_banner"),
                    colors = CardDefaults.cardColors(containerColor = HotMagenta.copy(alpha = 0.14f)),
                    border = BorderStroke(1.dp, HotMagenta.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Authentication message",
                            tint = HotMagenta,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = errorMessage.orEmpty(),
                            color = TextPrimary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onDismissError,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3. Body Content: Loading vs NeedsRegistration vs Distinct Creator / Spectator Auth Flows
            when {
                sessionState is AuthSessionState.Loading -> {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .testTag("auth_loading_card"),
                        colors = CardDefaults.cardColors(containerColor = StageSurface),
                        border = BorderStroke(1.dp, StageSurfaceHighlight),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = ElectricCyan,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Verifying Firebase Authentication session...",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Restoring your Creator Studio or Spectator pass",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                needsRegState != null -> {
                    val resolvedTitle = if (selectedRole == UserRole.CREATOR) creatorStageTitle else spectatorPersona
                    CompleteGoogleRegistrationCard(
                        state = needsRegState,
                        selectedRole = selectedRole,
                        onSelectRole = { selectedRole = it },
                        stageName = stageName,
                        onStageNameChange = { stageName = it },
                        creatorStageTitle = creatorStageTitle,
                        onCreatorStageTitleChange = { creatorStageTitle = it },
                        spectatorPersona = spectatorPersona,
                        onSpectatorPersonaChange = { spectatorPersona = it },
                        bio = bio,
                        onBioChange = { bio = it },
                        selectedCategory = selectedCategory,
                        onSelectCategory = { selectedCategory = it },
                        isLoading = isLoading,
                        onSubmit = {
                            onCompleteRegistration(
                                RegistrationDraft(
                                    role = selectedRole,
                                    stageName = stageName,
                                    stageTitle = resolvedTitle,
                                    bio = if (selectedRole == UserRole.CREATOR) bio else "",
                                    primaryCategory = selectedCategory
                                )
                            )
                        },
                        onSwitchAccount = onSignOut
                    )
                }

                else -> {
                    // Sign-In vs Register Segmented Switch
                    AuthModeSegmentedSwitch(
                        activeMode = flowMode,
                        selectedRole = selectedRole,
                        onModeChange = { flowMode = it }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Top-level Creator vs Spectator Role Portal Switcher
                    RoleSelectionRow(
                        selectedRole = selectedRole,
                        onSelectRole = { role ->
                            selectedRole = role
                            if (flowMode == AuthFlowMode.SIGN_IN) {
                                hasExplicitSignInRoleSelection = true
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dynamic Role Flow Highlights Banner
                    FirebaseRoleFlowBanner(selectedRole = selectedRole)

                    Spacer(modifier = Modifier.height(16.dp))

                    if (flowMode == AuthFlowMode.SIGN_IN) {
                        SignInModeCard(
                            preferredRole = selectedRole,
                            hasExplicitRoleSelection = hasExplicitSignInRoleSelection,
                            standaloneIdentifier = signInIdentifier,
                            onStandaloneIdentifierChange = { signInIdentifier = it },
                            standalonePassword = signInPassword,
                            onStandalonePasswordChange = { signInPassword = it },
                            savedStandaloneAccounts = savedStandaloneAccounts,
                            onSelectSavedStandaloneAccount = { summary ->
                                signInIdentifier = summary.identifier
                                selectedRole = summary.role
                                hasExplicitSignInRoleSelection = false
                            },
                            isLoading = isLoading,
                            onSignInStandaloneClicked = {
                                onSignInStandalone(
                                    signInIdentifier,
                                    signInPassword,
                                    if (hasExplicitSignInRoleSelection) selectedRole else null
                                )
                            },
                            onSignInClicked = {
                                if (onSignInWithGoogleExplicit != null) {
                                    onSignInWithGoogleExplicit(selectedRole, hasExplicitSignInRoleSelection)
                                } else {
                                    onSignInWithGoogle(selectedRole)
                                }
                            },
                            onSwitchToRegister = { flowMode = AuthFlowMode.REGISTER }
                        )
                    } else {
                        val resolvedTitle = if (selectedRole == UserRole.CREATOR) {
                            creatorStageTitle
                        } else {
                            spectatorPersona
                        }
                        RegisterModeCard(
                            selectedRole = selectedRole,
                            stageName = stageName,
                            onStageNameChange = { stageName = it },
                            creatorStageTitle = creatorStageTitle,
                            onCreatorStageTitleChange = { creatorStageTitle = it },
                            spectatorPersona = spectatorPersona,
                            onSpectatorPersonaChange = { spectatorPersona = it },
                            bio = bio,
                            onBioChange = { bio = it },
                            selectedCategory = selectedCategory,
                            onSelectCategory = { selectedCategory = it },
                            standaloneIdentifier = registerIdentifier,
                            onStandaloneIdentifierChange = { registerIdentifier = it },
                            standalonePassword = registerPassword,
                            onStandalonePasswordChange = { registerPassword = it },
                            isLoading = isLoading,
                            onRegisterStandaloneClicked = {
                                onRegisterStandalone(
                                    RegistrationDraft(
                                        role = selectedRole,
                                        stageName = stageName,
                                        stageTitle = resolvedTitle,
                                        bio = if (selectedRole == UserRole.CREATOR) bio else "",
                                        primaryCategory = selectedCategory,
                                        identifier = registerIdentifier,
                                        password = registerPassword,
                                        authProvider = AuthAccountProvider.STANDALONE
                                    ),
                                    registerIdentifier,
                                    registerPassword
                                )
                            },
                            onSignInStandaloneFromRegisterClicked = {
                                onSignInStandalone(
                                    registerIdentifier,
                                    registerPassword,
                                    selectedRole
                                )
                            },
                            onRegisterClicked = {
                                onRegisterWithGoogle(
                                    RegistrationDraft(
                                        role = selectedRole,
                                        stageName = stageName,
                                        stageTitle = resolvedTitle,
                                        bio = if (selectedRole == UserRole.CREATOR) bio else "",
                                        primaryCategory = selectedCategory
                                    )
                                )
                            },
                            onSwitchToSignIn = { flowMode = AuthFlowMode.SIGN_IN }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Security & Firebase Auth Footer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("auth_encryption_security_footer")
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.auth_security_footer_encrypted),
                    color = TextMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = { showPrivacyPolicy = true },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("auth_privacy_policy_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Policy,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.privacy_policy_cta),
                    color = ElectricCyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (showPrivacyPolicy) {
            PrivacyPolicyDialog(
                onDismiss = { showPrivacyPolicy = false }
            )
        }
    }
}

@Composable
private fun AuthArenaHeroHeader(selectedRole: UserRole) {
    val isCreator = selectedRole == UserRole.CREATOR
    val ringGradient = if (isCreator) {
        listOf(NeonViolet, HotMagenta, ElectricCyan)
    } else {
        listOf(ElectricCyan, SpokenWordPrimary, NeonViolet)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(ringGradient))
                .padding(2.5.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(StageSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isCreator) Icons.Default.Mic else Icons.Default.HowToVote,
                    contentDescription = "VersePulse Stage Emblem",
                    tint = if (isCreator) HotMagenta else ElectricCyan,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            )
            Box(
                modifier = Modifier
                    .background(
                        Brush.horizontalGradient(
                            if (isCreator) listOf(NeonViolet, HotMagenta)
                            else listOf(NeonViolet, ElectricCyan)
                        ),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (isCreator) "CREATOR" else "ARENA",
                    color = if (isCreator) Color.White else Color.Black,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.auth_arena_subtitle),
            color = TextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AuthModeSegmentedSwitch(
    activeMode: AuthFlowMode,
    selectedRole: UserRole,
    onModeChange: (AuthFlowMode) -> Unit
) {
    val activeBrush = if (selectedRole == UserRole.CREATOR) {
        Brush.horizontalGradient(listOf(NeonViolet, HotMagenta.copy(alpha = 0.9f)))
    } else {
        Brush.horizontalGradient(listOf(SpokenWordPrimary, ElectricCyan.copy(alpha = 0.85f)))
    }
    val activeContentColor = if (selectedRole == UserRole.CREATOR) Color.White else Color.Black

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StageSurface)
            .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AuthSegmentTab(
            label = stringResource(R.string.auth_tab_sign_in),
            icon = Icons.Default.AccountCircle,
            selected = activeMode == AuthFlowMode.SIGN_IN,
            activeBrush = activeBrush,
            activeContentColor = activeContentColor,
            onClick = { onModeChange(AuthFlowMode.SIGN_IN) },
            testTag = "auth_tab_signin",
            modifier = Modifier.weight(1f)
        )
        AuthSegmentTab(
            label = stringResource(R.string.auth_tab_register),
            icon = Icons.Default.PersonAdd,
            selected = activeMode == AuthFlowMode.REGISTER,
            activeBrush = activeBrush,
            activeContentColor = activeContentColor,
            onClick = { onModeChange(AuthFlowMode.REGISTER) },
            testTag = "auth_tab_register",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AuthSegmentTab(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    activeBrush: Brush,
    activeContentColor: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) activeBrush
                else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) activeContentColor else TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                color = if (selected) activeContentColor else TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SignInModeCard(
    preferredRole: UserRole,
    hasExplicitRoleSelection: Boolean,
    standaloneIdentifier: String,
    onStandaloneIdentifierChange: (String) -> Unit,
    standalonePassword: String,
    onStandalonePasswordChange: (String) -> Unit,
    savedStandaloneAccounts: List<StandaloneAccountStore.StandaloneAccountSummary>,
    onSelectSavedStandaloneAccount: (StandaloneAccountStore.StandaloneAccountSummary) -> Unit,
    isLoading: Boolean,
    onSignInStandaloneClicked: () -> Unit,
    onSignInClicked: () -> Unit,
    onSwitchToRegister: () -> Unit
) {
    val isCreator = preferredRole == UserRole.CREATOR
    val roleAccent = if (isCreator) HotMagenta else ElectricCyan

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_signin_card"),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        border = BorderStroke(1.dp, roleAccent.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .testTag(if (isCreator) "creator_login_flow_panel" else "spectator_login_flow_panel")
        ) {
            // Primary Firebase Authentication Sign-In Flow (Creator vs Spectator)
            Crossfade(targetState = isCreator, label = "SignInHeaderCrossfade") { creatorMode ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (creatorMode) Icons.Default.GraphicEq else Icons.Default.HowToVote,
                            contentDescription = null,
                            tint = if (creatorMode) HotMagenta else ElectricCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (creatorMode) {
                                stringResource(R.string.auth_firebase_badge_creator)
                            } else {
                                stringResource(R.string.auth_firebase_badge_spectator)
                            },
                            color = if (creatorMode) HotMagenta else ElectricCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (creatorMode) {
                            stringResource(R.string.auth_creator_signin_headline)
                        } else {
                            stringResource(R.string.auth_spectator_signin_headline)
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            color = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (creatorMode) {
                            stringResource(R.string.auth_creator_signin_subtitle)
                        } else {
                            stringResource(R.string.auth_spectator_signin_subtitle)
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Destination & Firestore sync indicator pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(StageSurfaceElevated)
                    .border(1.dp, roleAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("firebase_signin_route_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = roleAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isCreator) {
                        stringResource(R.string.auth_creator_route_hint)
                    } else {
                        stringResource(R.string.auth_spectator_route_hint)
                    },
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val googleSignInLabel = when {
                isCreator -> stringResource(R.string.auth_sign_in_google_creator_cta)
                hasExplicitRoleSelection -> stringResource(R.string.auth_sign_in_google_spectator_cta)
                else -> stringResource(R.string.auth_sign_in_google_cta)
            }

            GoogleCredentialActionButton(
                label = googleSignInLabel,
                isLoading = isLoading,
                accentBrush = if (isCreator) {
                    Brush.horizontalGradient(listOf(NeonViolet, HotMagenta))
                } else {
                    Brush.horizontalGradient(listOf(Color.White, Color(0xFFE5FAFF)))
                },
                textColor = if (isCreator) Color.White else Color.Black,
                onClick = onSignInClicked,
                testTag = "google_signin_button"
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = StageSurfaceHighlight, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.auth_or_standalone_divider),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                HorizontalDivider(color = StageSurfaceHighlight, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            StandaloneSignInSection(
                preferredRole = preferredRole,
                hasExplicitRoleSelection = hasExplicitRoleSelection,
                identifier = standaloneIdentifier,
                onIdentifierChange = onStandaloneIdentifierChange,
                password = standalonePassword,
                onPasswordChange = onStandalonePasswordChange,
                savedAccounts = savedStandaloneAccounts,
                onSelectSavedAccount = onSelectSavedStandaloneAccount,
                isLoading = isLoading,
                onSignInStandalone = onSignInStandaloneClicked
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "New to the Arena?",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                TextButton(
                    onClick = onSwitchToRegister,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("switch_to_register_button")
                ) {
                    Text(
                        text = if (isCreator) "Register Creator Account" else "Register Spectator Pass",
                        color = roleAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RegisterModeCard(
    selectedRole: UserRole,
    stageName: String,
    onStageNameChange: (String) -> Unit,
    creatorStageTitle: String,
    onCreatorStageTitleChange: (String) -> Unit,
    spectatorPersona: String,
    onSpectatorPersonaChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    selectedCategory: PerformanceCategory,
    onSelectCategory: (PerformanceCategory) -> Unit,
    standaloneIdentifier: String,
    onStandaloneIdentifierChange: (String) -> Unit,
    standalonePassword: String,
    onStandalonePasswordChange: (String) -> Unit,
    isLoading: Boolean,
    onRegisterStandaloneClicked: () -> Unit,
    onSignInStandaloneFromRegisterClicked: () -> Unit,
    onRegisterClicked: () -> Unit,
    onSwitchToSignIn: () -> Unit
) {
    val isCreator = selectedRole == UserRole.CREATOR
    val roleAccent = if (isCreator) HotMagenta else ElectricCyan

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_register_card"),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        border = BorderStroke(1.dp, roleAccent.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isCreator) Icons.Default.Mic else Icons.Default.HowToVote,
                    contentDescription = null,
                    tint = roleAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isCreator) {
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
                text = if (isCreator) {
                    stringResource(R.string.auth_creator_register_headline)
                } else {
                    stringResource(R.string.auth_spectator_register_headline)
                },
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isCreator) {
                    stringResource(R.string.auth_creator_register_subtitle)
                } else {
                    stringResource(R.string.auth_spectator_register_subtitle)
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Distinct Role-Specific Registration Form
            if (isCreator) {
                CreatorRegistrationFormSection(
                    stageName = stageName,
                    onStageNameChange = onStageNameChange,
                    stageTitle = creatorStageTitle,
                    onStageTitleChange = onCreatorStageTitleChange,
                    bio = bio,
                    onBioChange = onBioChange,
                    selectedCategory = selectedCategory,
                    onSelectCategory = onSelectCategory
                )
            } else {
                SpectatorRegistrationFormSection(
                    fanHandle = stageName,
                    onFanHandleChange = onStageNameChange,
                    personaTagline = spectatorPersona,
                    onPersonaTaglineChange = onSpectatorPersonaChange,
                    selectedCategory = selectedCategory,
                    onSelectCategory = onSelectCategory
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            val ctaText = if (isCreator) {
                stringResource(R.string.auth_register_performer_cta)
            } else {
                stringResource(R.string.auth_register_spectator_cta)
            }

            GoogleCredentialActionButton(
                label = ctaText,
                isLoading = isLoading,
                accentBrush = if (isCreator) {
                    Brush.horizontalGradient(listOf(NeonViolet, HotMagenta))
                } else {
                    Brush.horizontalGradient(listOf(ElectricCyan, SpokenWordPrimary))
                },
                textColor = if (isCreator) Color.White else Color.Black,
                onClick = onRegisterClicked,
                testTag = "google_register_button"
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = StageSurfaceHighlight, modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.auth_or_standalone_divider),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                HorizontalDivider(color = StageSurfaceHighlight, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            StandaloneRegistrationAuthSection(
                selectedRole = selectedRole,
                identifier = standaloneIdentifier,
                onIdentifierChange = onStandaloneIdentifierChange,
                password = standalonePassword,
                onPasswordChange = onStandalonePasswordChange,
                isLoading = isLoading,
                onRegisterStandalone = onRegisterStandaloneClicked,
                onSignInStandaloneFromRegister = onSignInStandaloneFromRegisterClicked
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Already registered?",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                TextButton(
                    onClick = onSwitchToSignIn,
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("switch_to_signin_button")
                ) {
                    Text(
                        text = "Sign In",
                        color = roleAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CompleteGoogleRegistrationCard(
    state: AuthSessionState.NeedsRegistration,
    selectedRole: UserRole,
    onSelectRole: (UserRole) -> Unit,
    stageName: String,
    onStageNameChange: (String) -> Unit,
    creatorStageTitle: String,
    onCreatorStageTitleChange: (String) -> Unit,
    spectatorPersona: String,
    onSpectatorPersonaChange: (String) -> Unit,
    bio: String,
    onBioChange: (String) -> Unit,
    selectedCategory: PerformanceCategory,
    onSelectCategory: (PerformanceCategory) -> Unit,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    onSwitchAccount: () -> Unit
) {
    val isCreator = selectedRole == UserRole.CREATOR
    val roleAccent = if (isCreator) HotMagenta else ElectricCyan

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_complete_registration_card"),
        colors = CardDefaults.cardColors(containerColor = StageSurface),
        border = BorderStroke(1.dp, roleAccent.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(NeonViolet)
                        .border(1.5.dp, roleAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (!state.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = state.photoUrl,
                            contentDescription = "Verified Google Avatar",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = state.suggestedName.take(2).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = roleAccent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Firebase Auth Verified",
                            color = roleAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = state.suggestedName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!state.email.isNullOrBlank()) {
                        Text(
                            text = state.email,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                TextButton(
                    onClick = onSwitchAccount,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp)
                ) {
                    Text("Change", color = TextSecondary, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = StageSurfaceHighlight)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Choose Your Arena Role",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(10.dp))

            RoleSelectionRow(
                selectedRole = selectedRole,
                onSelectRole = onSelectRole
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (isCreator) {
                CreatorRegistrationFormSection(
                    stageName = stageName,
                    onStageNameChange = onStageNameChange,
                    stageTitle = creatorStageTitle,
                    onStageTitleChange = onCreatorStageTitleChange,
                    bio = bio,
                    onBioChange = onBioChange,
                    selectedCategory = selectedCategory,
                    onSelectCategory = onSelectCategory
                )
            } else {
                SpectatorRegistrationFormSection(
                    fanHandle = stageName,
                    onFanHandleChange = onStageNameChange,
                    personaTagline = spectatorPersona,
                    onPersonaTaglineChange = onSpectatorPersonaChange,
                    selectedCategory = selectedCategory,
                    onSelectCategory = onSelectCategory
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            GoogleCredentialActionButton(
                label = if (isCreator) {
                    "Complete Creator Studio Setup"
                } else {
                    stringResource(R.string.auth_complete_registration_cta)
                },
                isLoading = isLoading,
                accentBrush = if (isCreator) {
                    Brush.horizontalGradient(listOf(NeonViolet, HotMagenta))
                } else {
                    Brush.horizontalGradient(listOf(ElectricCyan, SpokenWordPrimary))
                },
                textColor = if (isCreator) Color.White else Color.Black,
                onClick = onSubmit,
                testTag = "complete_registration_button"
            )
        }
    }
}

@Composable
private fun RoleSelectionRow(
    selectedRole: UserRole,
    onSelectRole: (UserRole) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RoleOptionCard(
            title = stringResource(R.string.auth_role_creator_card_title),
            subtitle = stringResource(R.string.auth_role_creator_card_subtitle),
            icon = Icons.Default.Mic,
            accentColor = HotMagenta,
            selected = selectedRole == UserRole.CREATOR,
            onClick = { onSelectRole(UserRole.CREATOR) },
            testTag = "register_role_performer",
            modifier = Modifier.weight(1f)
        )
        RoleOptionCard(
            title = stringResource(R.string.auth_role_spectator_card_title),
            subtitle = stringResource(R.string.auth_role_spectator_card_subtitle),
            icon = Icons.Default.HowToVote,
            accentColor = ElectricCyan,
            selected = selectedRole == UserRole.SPECTATOR,
            onClick = { onSelectRole(UserRole.SPECTATOR) },
            testTag = "register_role_spectator",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RoleOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .defaultMinSize(minHeight = 112.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag(testTag),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) accentColor.copy(alpha = 0.14f) else StageSurfaceElevated
        ),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor else StageSurfaceHighlight
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Selected role",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                color = TextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
