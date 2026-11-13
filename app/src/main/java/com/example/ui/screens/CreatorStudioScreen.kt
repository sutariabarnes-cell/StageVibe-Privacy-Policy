package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.net.Uri
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.res.stringResource
import com.example.R
import com.example.model.UploadedAudioInfo
import com.example.model.UploadedVideoInfo
import com.example.security.UploadEncryptionManager
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.core.content.ContextCompat
import com.example.model.PerformanceCategory
import com.example.model.PerformanceCategoryCatalog
import com.example.model.PerformanceStyle
import com.example.model.Poll
import com.example.model.SynthAudioType
import com.example.model.VisualizerTheme
import com.example.ui.components.HorizontalStageSearchBar
import com.example.ui.components.PerformanceVisualizer
import com.example.ui.components.StageVideoPlayer
import com.example.ui.components.accentColor
import com.example.ui.components.iconVector
import com.example.ui.components.localizedLabel
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletLight
import com.example.ui.theme.SpokenWordPrimary
import com.example.ui.theme.StageBackground
import com.example.ui.theme.StageSurface
import com.example.ui.theme.StageSurfaceElevated
import com.example.ui.theme.StageSurfaceHighlight
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class StudioAudioSource(val label: String) {
    UPLOAD("Upload Music"),
    VIDEO("Upload Video"),
    INSTRUMENTAL("Backing Beats"),
    MICROPHONE("Voice Recording")
}

@Composable
fun CreatorStudioScreen(
    availablePolls: List<Poll>,
    isRecordingAudio: Boolean,
    recordAmplitude: Float,
    recordedFilePath: String?,
    uploadedAudio: UploadedAudioInfo? = null,
    isUploadingAudio: Boolean = false,
    uploadError: String? = null,
    uploadedVideo: UploadedVideoInfo? = null,
    isUploadingVideo: Boolean = false,
    videoUploadError: String? = null,
    previewAudioAmplitude: Float = 0.2f,
    isPlayingPreview: Boolean = false,
    isEncryptionEnabled: Boolean = true,
    onToggleEncryption: ((Boolean) -> Unit)? = null,
    onOpenEncryptionVault: (() -> Unit)? = null,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onAudioFileSelected: (Uri) -> Unit = {},
    onClearUploadedAudio: () -> Unit = {},
    onVideoFileSelected: (Uri) -> Unit = {},
    onClearUploadedVideo: () -> Unit = {},
    onPreviewAudioFile: (String) -> Unit = {},
    onStopAudioPreview: () -> Unit = {},
    onPreviewSynthAudio: (SynthAudioType) -> Unit,
    onPublish: (
        title: String,
        category: PerformanceCategory,
        genre: String,
        lyrics: String,
        visualTheme: VisualizerTheme,
        audioType: SynthAudioType,
        customAudioPath: String?,
        enrollInPollId: String?,
        durationSec: Int
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onPublishWithVideo: ((
        title: String,
        category: PerformanceCategory,
        genre: String,
        lyrics: String,
        visualTheme: VisualizerTheme,
        audioType: SynthAudioType,
        customAudioPath: String?,
        customVideoPath: String?,
        enrollInPollId: String?,
        durationSec: Int
    ) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(PerformanceCategory.MUSIC) }
    var genre by remember { mutableStateOf("Neo-Soul") }
    var lyricsOrScript by remember { mutableStateOf("") }
    var selectedVisualTheme by remember { mutableStateOf(VisualizerTheme.NEON_WAVES) }
    var selectedAudioType by remember { mutableStateOf(SynthAudioType.ACOUSTIC_SOUL) }
    var selectedPollId by remember { mutableStateOf<String?>(availablePolls.firstOrNull()?.id) }
    var pollSearchQuery by remember { mutableStateOf("") }
    var selectedAudioSource by remember { mutableStateOf(StudioAudioSource.UPLOAD) }
    var isPreviewingStudioVideo by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Auto-fill title & genre from uploaded music
    androidx.compose.runtime.LaunchedEffect(uploadedAudio) {
        uploadedAudio?.let { audio ->
            if (title.isBlank()) {
                title = audio.detectedTitle
            }
            if (!audio.detectedGenre.isNullOrBlank() && (genre.isBlank() || genre == "Neo-Soul")) {
                genre = audio.detectedGenre
            }
        }
    }

    // Auto-fill title from uploaded video if still blank
    androidx.compose.runtime.LaunchedEffect(uploadedVideo) {
        uploadedVideo?.let { video ->
            if (title.isBlank() && video.detectedTitle.isNotBlank()) {
                title = video.detectedTitle
            }
        }
        if (uploadedVideo == null) {
            isPreviewingStudioVideo = false
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onAudioFileSelected(it) }
    }

    // Zero-permission Google Play compliant Android Photo Picker for Video
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let { onVideoFileSelected(it) }
    }

    val launchVideoPicker = {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onStartRecording()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(StageBackground)
            .padding(horizontal = 16.dp)
            .testTag("studio_lazy_column"),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header + At-Rest Encryption Vault Banner
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column {
                    Text(
                        text = "Creator Studio",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = stringResource(R.string.studio_subtitle),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }

                val bannerAccent = if (isEncryptionEnabled) ElectricCyan else GoldCrown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(StageSurface)
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                if (isEncryptionEnabled) {
                                    listOf(ElectricCyan.copy(alpha = 0.45f), NeonViolet.copy(alpha = 0.45f))
                                } else {
                                    listOf(GoldCrown.copy(alpha = 0.55f), StageSurfaceHighlight)
                                }
                            ),
                            RoundedCornerShape(12.dp)
                        )
                        .then(
                            if (onOpenEncryptionVault != null) {
                                Modifier.clickable { onOpenEncryptionVault() }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("studio_encryption_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(bannerAccent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isEncryptionEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = stringResource(R.string.encryption_badge_aes256),
                            tint = bannerAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (isEncryptionEnabled) R.string.studio_encryption_banner_title
                                else R.string.studio_encryption_banner_disabled_title
                            ),
                            color = bannerAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(
                                if (isEncryptionEnabled) R.string.studio_encryption_banner_subtitle
                                else R.string.studio_encryption_banner_disabled_subtitle
                            ),
                            color = TextSecondary,
                            fontSize = 10.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (onToggleEncryption != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isEncryptionEnabled,
                            onCheckedChange = onToggleEncryption,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = ElectricCyan,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = StageSurfaceHighlight
                            ),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("studio_encryption_switch")
                        )
                    }
                }
            }
        }

        // Category Selector (Music vs Spoken Word)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StageSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "1. Performance Category",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                selectedCategory = PerformanceCategory.MUSIC
                                genre = "Neo-Soul"
                                selectedAudioType = SynthAudioType.ACOUSTIC_SOUL
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedCategory == PerformanceCategory.MUSIC) NeonViolet else StageSurfaceElevated
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).testTag("select_music_category")
                        ) {
                            Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Music", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = {
                                selectedCategory = PerformanceCategory.SPOKEN_WORD
                                genre = "Slam Poetry"
                                selectedAudioType = SynthAudioType.POETRY_AMBIENCE
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedCategory == PerformanceCategory.SPOKEN_WORD) SpokenWordPrimary else StageSurfaceElevated
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).testTag("select_spoken_category")
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Spoken Word", fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.studio_style_chips_label),
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_style_chip_row"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val stylesForCategory = PerformanceCategoryCatalog.stylesForCategory(selectedCategory)
                        items(stylesForCategory, key = { it.id }) { styleOption ->
                            val isSelectedStyle = PerformanceCategoryCatalog.resolveStyle(
                                category = selectedCategory,
                                genre = genre
                            ) == styleOption
                            val chipTextColor = if (styleOption == PerformanceStyle.JAZZ ||
                                styleOption == PerformanceStyle.ACOUSTIC ||
                                styleOption == PerformanceStyle.HIP_HOP
                            ) {
                                Color.Black
                            } else {
                                Color.White
                            }
                            FilterChip(
                                selected = isSelectedStyle,
                                onClick = {
                                    selectedCategory = styleOption.parentCategory
                                    genre = styleOption.defaultGenre
                                    selectedAudioType = when (styleOption) {
                                        PerformanceStyle.RAP, PerformanceStyle.HIP_HOP -> SynthAudioType.HIPHOP_BEAT
                                        PerformanceStyle.JAZZ -> SynthAudioType.PIANO_WARMTH
                                        PerformanceStyle.POETRY, PerformanceStyle.SPOKEN_WORD -> SynthAudioType.POETRY_AMBIENCE
                                        PerformanceStyle.ELECTRONIC -> SynthAudioType.SYNTH_CHILL
                                        PerformanceStyle.SOUL_RNB, PerformanceStyle.ACOUSTIC -> SynthAudioType.ACOUSTIC_SOUL
                                    }
                                },
                                label = {
                                    Text(
                                        text = styleOption.localizedLabel(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = styleOption.iconVector(),
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = styleOption.accentColor(),
                                    selectedLabelColor = chipTextColor,
                                    selectedLeadingIconColor = chipTextColor,
                                    containerColor = StageSurfaceElevated,
                                    labelColor = TextSecondary,
                                    iconColor = styleOption.accentColor()
                                ),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("studio_style_${styleOption.id}")
                            )
                        }
                    }
                }
            }
        }

        // Details Form: Title & Genre
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StageSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "2. Title & Genre Details",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Performance Title") },
                        placeholder = { Text("e.g. Echoes in the Night") },
                        modifier = Modifier.fillMaxWidth().testTag("studio_title_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = StageSurfaceElevated,
                            unfocusedContainerColor = StageSurfaceElevated,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = StageSurfaceHighlight
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Genre / Style") },
                        placeholder = { Text("e.g. Neo-Soul, R&B, Slam Poetry, Hip-Hop") },
                        modifier = Modifier.fillMaxWidth().testTag("studio_genre_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = StageSurfaceElevated,
                            unfocusedContainerColor = StageSurfaceElevated,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = StageSurfaceHighlight
                        )
                    )
                }
            }
        }

        // Lyrics / Script Teleprompter Editor
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StageSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = if (selectedCategory == PerformanceCategory.MUSIC) "3. Song Lyrics & Verses" else "3. Spoken Word Script & Teleprompter",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = lyricsOrScript,
                        onValueChange = { lyricsOrScript = it },
                        label = { Text("Write your verses, poetry, or lyrics...") },
                        placeholder = { Text("Every brick in this city has a story to tell...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .testTag("studio_lyrics_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = StageSurfaceElevated,
                            unfocusedContainerColor = StageSurfaceElevated,
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = StageSurfaceHighlight
                        )
                    )
                }
            }
        }

        // Media & Sound Source Selection: Upload Music, Upload Video, Backing Beats, or Voice Recording
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StageSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "4. Media & Sound Source",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = { selectedAudioSource = StudioAudioSource.UPLOAD },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedAudioSource == StudioAudioSource.UPLOAD) ElectricCyan else StageSurfaceElevated,
                                contentColor = if (selectedAudioSource == StudioAudioSource.UPLOAD) Color.Black else TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).testTag("tab_upload_music")
                        ) {
                            Text("Music", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { selectedAudioSource = StudioAudioSource.VIDEO },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedAudioSource == StudioAudioSource.VIDEO) GoldCrown else StageSurfaceElevated,
                                contentColor = if (selectedAudioSource == StudioAudioSource.VIDEO) Color.Black else TextPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).testTag("tab_upload_video")
                        ) {
                            Text(
                                text = stringResource(R.string.studio_tab_video),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Button(
                            onClick = { selectedAudioSource = StudioAudioSource.INSTRUMENTAL },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedAudioSource == StudioAudioSource.INSTRUMENTAL) NeonViolet else StageSurfaceElevated,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).testTag("tab_synth_beats")
                        ) {
                            Text("Beats", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { selectedAudioSource = StudioAudioSource.MICROPHONE },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedAudioSource == StudioAudioSource.MICROPHONE) HotMagenta else StageSurfaceElevated,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f).testTag("tab_mic_record")
                        ) {
                            Text("Vocals", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (selectedAudioSource) {
                        StudioAudioSource.UPLOAD -> {
                            if (uploadedAudio != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(StageSurfaceElevated)
                                        .border(1.dp, Brush.horizontalGradient(listOf(ElectricCyan, NeonViolet)), RoundedCornerShape(12.dp))
                                        .padding(14.dp)
                                        .testTag("uploaded_track_preview_card")
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clip(CircleShape)
                                                    .background(Brush.linearGradient(listOf(NeonViolet, ElectricCyan))),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = "Audio track",
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = uploadedAudio.detectedTitle.ifBlank { uploadedAudio.fileName },
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${uploadedAudio.fileName} • ${uploadedAudio.formattedDuration} • ${uploadedAudio.formattedFileSize}",
                                                    color = ElectricCyan,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (uploadedAudio.isEncrypted || UploadEncryptionManager.isEncryptedPath(uploadedAudio.localFilePath)) {
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    EncryptionStatusBadge(
                                                        shaShort = uploadedAudio.contentSha256Short,
                                                        testTag = "uploaded_audio_encrypted_badge"
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = onClearUploadedAudio,
                                                modifier = Modifier.testTag("remove_uploaded_audio_button")
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Remove file", tint = HotMagenta)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(StageSurface, RoundedCornerShape(10.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        if (isPlayingPreview) onStopAudioPreview() else onPreviewAudioFile(uploadedAudio.localFilePath)
                                                    },
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isPlayingPreview) HotMagenta else ElectricCyan)
                                                        .testTag("preview_uploaded_audio_button")
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = if (isPlayingPreview) "Pause" else "Play",
                                                        tint = Color.Black,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = if (isPlayingPreview) "Previewing Music..." else "Preview Track",
                                                    color = if (isPlayingPreview) HotMagenta else TextPrimary,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                for (i in 0 until 5) {
                                                    val barHeight = if (isPlayingPreview) (10 + ((i + 1) * 3 * previewAudioAmplitude * 2).toInt()).coerceIn(6, 24) else 8
                                                    Box(
                                                        modifier = Modifier
                                                            .width(3.dp)
                                                            .height(barHeight.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(if (isPlayingPreview) ElectricCyan else TextMuted.copy(alpha = 0.4f))
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Button(
                                            onClick = { audioPickerLauncher.launch("audio/*") },
                                            colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceHighlight),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth().testTag("change_audio_file_button")
                                        ) {
                                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Select Different Music File", fontSize = 11.sp)
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(StageSurfaceElevated)
                                        .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(12.dp))
                                        .clickable { audioPickerLauncher.launch("audio/*") }
                                        .padding(20.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(NeonViolet.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Upload,
                                                contentDescription = "Upload music",
                                                tint = NeonVioletLight,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = "Upload Your Music Track",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = "Pick MP3, WAV, M4A, FLAC, or AAC files from your device",
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        if (isUploadingAudio) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = ElectricCyan,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.studio_processing_encrypted_audio),
                                                    color = ElectricCyan,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { audioPickerLauncher.launch("audio/*") },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = NeonViolet,
                                                    contentColor = Color.White
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.testTag("upload_music_button")
                                            ) {
                                                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Choose Music File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }

                                        if (uploadError != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = uploadError ?: "",
                                                color = HotMagenta,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        StudioAudioSource.VIDEO -> {
                            if (uploadedVideo != null) {
                                UploadedVideoStudioCard(
                                    uploadedVideo = uploadedVideo,
                                    isPreviewingVideo = isPreviewingStudioVideo,
                                    onTogglePreview = { isPreviewingStudioVideo = !isPreviewingStudioVideo },
                                    onPickDifferentVideo = launchVideoPicker,
                                    onRemoveVideo = {
                                        isPreviewingStudioVideo = false
                                        onClearUploadedVideo()
                                    }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(StageSurfaceElevated)
                                        .border(1.dp, StageSurfaceHighlight, RoundedCornerShape(12.dp))
                                        .clickable { launchVideoPicker() }
                                        .padding(20.dp)
                                        .testTag("upload_video_dropzone"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(CircleShape)
                                                .background(GoldCrown.copy(alpha = 0.18f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Videocam,
                                                contentDescription = "Upload video",
                                                tint = GoldCrown,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = stringResource(R.string.studio_upload_video_title),
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = stringResource(R.string.studio_upload_video_subtitle),
                                            color = TextSecondary,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        if (isUploadingVideo) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(16.dp),
                                                    color = GoldCrown,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = stringResource(R.string.studio_processing_video),
                                                    color = GoldCrown,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = { launchVideoPicker() },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = GoldCrown,
                                                    contentColor = Color.Black
                                                ),
                                                shape = RoundedCornerShape(10.dp),
                                                modifier = Modifier.testTag("upload_video_button")
                                            ) {
                                                Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = stringResource(R.string.studio_choose_video_cta),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }

                                        if (videoUploadError != null) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = videoUploadError,
                                                color = HotMagenta,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        StudioAudioSource.INSTRUMENTAL -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SynthAudioType.entries.filter {
                                    it != SynthAudioType.CUSTOM_RECORDING &&
                                        it != SynthAudioType.UPLOADED_MUSIC &&
                                        it != SynthAudioType.UPLOADED_VIDEO
                                }.forEach { audioType ->
                                    val isSelected = selectedAudioType == audioType
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) NeonViolet.copy(alpha = 0.25f) else StageSurfaceElevated)
                                            .border(
                                                1.dp,
                                                if (isSelected) NeonViolet else StageSurfaceHighlight,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable {
                                                selectedAudioType = audioType
                                                onPreviewSynthAudio(audioType)
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = audioType.displayName,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = audioType.genreDesc,
                                                color = TextSecondary,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Test synth",
                                            tint = if (isSelected) ElectricCyan else TextMuted
                                        )
                                    }
                                }
                            }
                        }

                        StudioAudioSource.MICROPHONE -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(StageSurfaceElevated, RoundedCornerShape(12.dp))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (isRecordingAudio) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(HotMagenta)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("RECORDING IN PROGRESS...", color = HotMagenta, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .background(StageSurfaceHighlight, CircleShape)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(fraction = recordAmplitude.coerceIn(0.05f, 1f))
                                                    .height(8.dp)
                                                    .background(HotMagenta, CircleShape)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = onStopRecording,
                                            colors = ButtonDefaults.buttonColors(containerColor = HotMagenta),
                                            modifier = Modifier.testTag("stop_recording_button")
                                        ) {
                                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Finish & Save Audio")
                                        }
                                    } else {
                                        val isRecordingEncrypted = recordedFilePath != null &&
                                            UploadEncryptionManager.isEncryptedPath(recordedFilePath)
                                        Text(
                                            text = if (recordedFilePath != null) {
                                                stringResource(R.string.studio_vocals_encrypted_ready)
                                            } else {
                                                "Ready to record your spoken word / song vocals"
                                            },
                                            color = if (recordedFilePath != null) ElectricCyan else TextSecondary,
                                            fontSize = 12.sp
                                        )
                                        if (isRecordingEncrypted) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            EncryptionStatusBadge(
                                                shaShort = null,
                                                testTag = "recorded_vocals_encrypted_badge"
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                                    onStartRecording()
                                                } else {
                                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = HotMagenta),
                                            modifier = Modifier.testTag("start_recording_button")
                                        ) {
                                            Icon(Icons.Default.FiberManualRecord, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (recordedFilePath != null) "Re-record Voice" else "Start Recording Mic")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Stage Video Attachment & Visualizer Theme Preview
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StageSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "5. Stage Video & Visualizer",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (selectedAudioSource != StudioAudioSource.VIDEO) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { launchVideoPicker() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (uploadedVideo != null) GoldCrown.copy(alpha = 0.22f) else StageSurfaceElevated,
                                    contentColor = if (uploadedVideo != null) GoldCrown else ElectricCyan
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("attach_stage_video_button")
                            ) {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (uploadedVideo != null) stringResource(R.string.studio_attached_video_label)
                                    else stringResource(R.string.studio_attach_stage_video_cta),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (selectedAudioSource != StudioAudioSource.VIDEO && uploadedVideo != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(StageSurfaceElevated)
                                .border(1.dp, GoldCrown.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("attached_companion_video_row"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = GoldCrown,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = uploadedVideo.detectedTitle.ifBlank { uploadedVideo.fileName },
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${uploadedVideo.formattedResolution} • ${uploadedVideo.formattedDuration} • Plays on Stage",
                                        color = GoldCrown,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            IconButton(
                                onClick = onClearUploadedVideo,
                                modifier = Modifier.size(36.dp).testTag("remove_companion_video_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove attached video",
                                    tint = HotMagenta,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Stage Mini Preview (Visualizer or Uploaded Video)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    ) {
                        if (uploadedVideo != null && isPreviewingStudioVideo) {
                            StageVideoPlayer(
                                videoFilePath = uploadedVideo.localFilePath,
                                isPlaying = true,
                                muteAudio = selectedAudioSource != StudioAudioSource.VIDEO,
                                thumbnailPath = uploadedVideo.thumbnailPath,
                                onPlaybackError = { isPreviewingStudioVideo = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            PerformanceVisualizer(
                                theme = selectedVisualTheme,
                                amplitude = 0.65f,
                                isPlaying = true,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(8.dp)
                                .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (uploadedVideo != null) {
                                    "Video + ${selectedVisualTheme.displayName} Fallback"
                                } else {
                                    "Previewing ${selectedVisualTheme.displayName}"
                                },
                                color = ElectricCyan,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(VisualizerTheme.entries) { theme ->
                            val isSelected = selectedVisualTheme == theme
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) ElectricCyan else StageSurfaceElevated)
                                    .clickable { selectedVisualTheme = theme }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = theme.displayName,
                                    color = if (isSelected) Color.Black else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Competition Poll Enrollment
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StageSurface)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "6. Enroll in Competition Poll",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Get audience votes from spectators to rise on the Leaderboard!",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    HorizontalStageSearchBar(
                        query = pollSearchQuery,
                        onQueryChange = { pollSearchQuery = it },
                        placeholderText = "Search competition polls...",
                        testTag = "studio_poll_search_bar",
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    val filteredStudioPolls = availablePolls.filter { poll ->
                        pollSearchQuery.isBlank() ||
                        poll.title.contains(pollSearchQuery, ignoreCase = true) ||
                        poll.badge.contains(pollSearchQuery, ignoreCase = true)
                    }

                    filteredStudioPolls.forEach { poll ->
                        val isSelected = selectedPollId == poll.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) GoldCrown.copy(alpha = 0.2f) else StageSurfaceElevated)
                                .border(
                                    1.dp,
                                    if (isSelected) GoldCrown else StageSurfaceHighlight,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { selectedPollId = poll.id }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) GoldCrown else StageSurfaceHighlight)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = poll.title,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${poll.badge} • ${poll.candidates.size} competitors",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        // Validation error display if any
        if (validationError != null) {
            item {
                Text(
                    text = validationError ?: "",
                    color = HotMagenta,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Publish Button
        item {
            Button(
                onClick = {
                    val hasAnyUploadedVideo = uploadedVideo != null
                    if (title.isBlank()) {
                        validationError = "Please enter a performance title."
                    } else if (lyricsOrScript.isBlank() && !hasAnyUploadedVideo) {
                        validationError = "Please write your lyrics or spoken word script, or attach a performance video."
                    } else if (selectedAudioSource == StudioAudioSource.UPLOAD && uploadedAudio == null) {
                        validationError = "Please select a music file to upload, or switch to Video, Beats, or Vocals."
                    } else if (selectedAudioSource == StudioAudioSource.VIDEO && uploadedVideo == null) {
                        validationError = "Please select a video file to upload, or switch to Music, Beats, or Vocals."
                    } else if (selectedAudioSource == StudioAudioSource.MICROPHONE && recordedFilePath == null) {
                        validationError = "Please record vocal audio before publishing."
                    } else {
                        validationError = null
                        isPreviewingStudioVideo = false
                        val finalAudioType = when (selectedAudioSource) {
                            StudioAudioSource.UPLOAD -> SynthAudioType.UPLOADED_MUSIC
                            StudioAudioSource.VIDEO -> SynthAudioType.UPLOADED_VIDEO
                            StudioAudioSource.MICROPHONE -> SynthAudioType.CUSTOM_RECORDING
                            StudioAudioSource.INSTRUMENTAL -> selectedAudioType
                        }
                        val finalAudioPath = when (selectedAudioSource) {
                            StudioAudioSource.UPLOAD -> uploadedAudio?.localFilePath
                            StudioAudioSource.MICROPHONE -> recordedFilePath
                            StudioAudioSource.VIDEO,
                            StudioAudioSource.INSTRUMENTAL -> null
                        }
                        val finalVideoPath = uploadedVideo?.localFilePath
                        val finalDuration = when (selectedAudioSource) {
                            StudioAudioSource.VIDEO -> uploadedVideo?.durationSec ?: 120
                            StudioAudioSource.UPLOAD -> uploadedAudio?.durationSec ?: uploadedVideo?.durationSec ?: 180
                            else -> uploadedVideo?.durationSec ?: 180
                        }
                        val resolvedLyrics = lyricsOrScript.trim().ifBlank {
                            "Original stage video performance."
                        }

                        if (onPublishWithVideo != null) {
                            onPublishWithVideo(
                                title.trim(),
                                selectedCategory,
                                genre.trim(),
                                resolvedLyrics,
                                selectedVisualTheme,
                                finalAudioType,
                                finalAudioPath,
                                finalVideoPath,
                                selectedPollId,
                                finalDuration
                            )
                        } else {
                            onPublish(
                                title.trim(),
                                selectedCategory,
                                genre.trim(),
                                resolvedLyrics,
                                selectedVisualTheme,
                                finalAudioType,
                                finalAudioPath,
                                selectedPollId,
                                finalDuration
                            )
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("publish_performance_button")
            ) {
                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Publish to Stage Arena",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun UploadedVideoStudioCard(
    uploadedVideo: UploadedVideoInfo,
    isPreviewingVideo: Boolean,
    onTogglePreview: () -> Unit,
    onPickDifferentVideo: () -> Unit,
    onRemoveVideo: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StageSurfaceElevated)
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(GoldCrown, HotMagenta)),
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp)
            .testTag("uploaded_video_preview_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GoldCrown, HotMagenta))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "Stage video",
                        tint = Color.Black,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uploadedVideo.detectedTitle.ifBlank { uploadedVideo.fileName },
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${uploadedVideo.formattedResolution} • ${uploadedVideo.formattedDuration} • ${uploadedVideo.formattedFileSize}",
                        color = GoldCrown,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (uploadedVideo.isEncrypted || UploadEncryptionManager.isEncryptedPath(uploadedVideo.localFilePath)) {
                        Spacer(modifier = Modifier.height(4.dp))
                        EncryptionStatusBadge(
                            shaShort = uploadedVideo.contentSha256Short,
                            testTag = "uploaded_video_encrypted_badge"
                        )
                    }
                }
                IconButton(
                    onClick = onRemoveVideo,
                    modifier = Modifier.testTag("remove_uploaded_video_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove video file",
                        tint = HotMagenta
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isPreviewingVideo) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, GoldCrown.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    StageVideoPlayer(
                        videoFilePath = uploadedVideo.localFilePath,
                        isPlaying = true,
                        muteAudio = false,
                        thumbnailPath = uploadedVideo.thumbnailPath,
                        onPlaybackError = onTogglePreview,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StageSurface, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onTogglePreview,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (isPreviewingVideo) HotMagenta else GoldCrown)
                            .testTag("preview_uploaded_video_button")
                    ) {
                        Icon(
                            imageVector = if (isPreviewingVideo) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPreviewingVideo) "Pause video preview" else "Play video preview",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (isPreviewingVideo) {
                            stringResource(R.string.studio_previewing_video_label)
                        } else {
                            stringResource(R.string.studio_preview_video_label)
                        },
                        color = if (isPreviewingVideo) HotMagenta else TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = GoldCrown,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onPickDifferentVideo,
                colors = ButtonDefaults.buttonColors(containerColor = StageSurfaceHighlight),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().testTag("change_video_file_button")
            ) {
                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.studio_change_video_cta), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun EncryptionStatusBadge(
    shaShort: String?,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ElectricCyan.copy(alpha = 0.14f))
            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (!shaShort.isNullOrBlank()) {
                stringResource(R.string.encryption_badge_sha_format, shaShort)
            } else {
                stringResource(R.string.encryption_badge_aes256)
            },
            color = ElectricCyan,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
