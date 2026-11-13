package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PerformanceCategory(val displayName: String) {
    MUSIC("Music"),
    SPOKEN_WORD("Spoken Word")
}

enum class VisualizerTheme(val displayName: String, val description: String) {
    NEON_WAVES("Neon Waves", "Fluid glowing waveforms shifting with rhythm"),
    CYAN_SPECTRUM("Cyan Spectrum", "Dynamic multi-band equalizer bars"),
    PULSE_ORB("Pulse Orb", "Kinetic glowing sound orb with shockwave ripples"),
    STAGE_LIGHTS("Stage Lights", "Concert spotlights and floating particle motes"),
    COSMIC_VINYL("Cosmic Vinyl", "Vintage spinning record with iridescent audio grooves")
}

enum class SynthAudioType(val displayName: String, val genreDesc: String) {
    ACOUSTIC_SOUL("Acoustic Soul", "Warm acoustic guitar chords & melodic bass"),
    HIPHOP_BEAT("Lo-Fi & Hip-Hop", "Crisp boom-bap drums & smooth rhodes chords"),
    POETRY_AMBIENCE("Spoken Word Ambience", "Atmospheric ethereal pads & gentle chimes"),
    SYNTH_CHILL("Neon Synthwave", "80s retro synth arpeggios & driving rhythm"),
    PIANO_WARMTH("Piano Reverie", "Lyrical neo-classical grand piano chords"),
    CUSTOM_RECORDING("Voice Recording", "Microphone audio recorded in Creator Studio"),
    UPLOADED_MUSIC("Uploaded Music", "Original music file uploaded from device storage"),
    UPLOADED_VIDEO("Stage Video", "Original performance video uploaded from device")
}

data class UploadedAudioInfo(
    val localFilePath: String,
    val fileName: String,
    val detectedTitle: String,
    val detectedArtist: String?,
    val detectedGenre: String?,
    val durationSec: Int,
    val fileSizeBytes: Long,
    val isEncrypted: Boolean = false,
    val encryptionAlgorithm: String = "AES-256-GCM",
    val contentSha256Short: String? = null
) {
    val formattedDuration: String
        get() {
            val minutes = durationSec / 60
            val seconds = durationSec % 60
            return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
        }

    val formattedFileSize: String
        get() {
            if (fileSizeBytes <= 0) return "Unknown size"
            val kb = fileSizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f MB", mb)
            } else {
                String.format(java.util.Locale.US, "%.0f KB", kb)
            }
        }
}

data class UploadedVideoInfo(
    val localFilePath: String,
    val thumbnailPath: String? = null,
    val fileName: String,
    val detectedTitle: String,
    val durationSec: Int,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val fileSizeBytes: Long,
    val isEncrypted: Boolean = false,
    val encryptionAlgorithm: String = "AES-256-GCM",
    val contentSha256Short: String? = null
) {
    val formattedDuration: String
        get() {
            val minutes = durationSec / 60
            val seconds = durationSec % 60
            return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds)
        }

    val formattedFileSize: String
        get() {
            if (fileSizeBytes <= 0) return "Unknown size"
            val kb = fileSizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f MB", mb)
            } else {
                String.format(java.util.Locale.US, "%.0f KB", kb)
            }
        }

    val formattedResolution: String
        get() = if (widthPx > 0 && heightPx > 0) "${widthPx}x${heightPx}" else "HD Video"
}

data class Performance(
    val id: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val avatarInitials: String,
    val category: PerformanceCategory,
    val genre: String,
    val durationSec: Int,
    val lyricsOrScript: String,
    val visualizerTheme: VisualizerTheme,
    val audioType: SynthAudioType,
    val customAudioPath: String? = null,
    val customVideoPath: String? = null,
    val viewsCount: Int,
    val likesCount: Int,
    val votesCount: Int,
    val cheersCount: Int,
    val isLikedByMe: Boolean = false,
    val isVotedByMe: Boolean = false,
    val isLiveOnStage: Boolean = false,
    val pollId: String? = null,
    val isUserCreated: Boolean = false,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val styleId: String? = null,
    val audienceRating: Float? = null,
    val ratingsCount: Int = 0
) {
    val hasVideo: Boolean
        get() = !customVideoPath.isNullOrBlank()

    @get:Exclude
    val hasEncryptedMedia: Boolean
        get() = com.example.security.UploadEncryptionManager.isEncryptedPath(customAudioPath) ||
            com.example.security.UploadEncryptionManager.isEncryptedPath(customVideoPath)
}

data class PollCandidate(
    val candidateId: String,
    val performanceId: String,
    val title: String,
    val artistName: String,
    val category: PerformanceCategory,
    val votesCount: Int,
    val avatarColorHex: Long
)

data class Poll(
    val id: String,
    val title: String,
    val description: String,
    val category: PerformanceCategory,
    val badge: String,
    val endsInHours: Int,
    val candidates: List<PollCandidate>,
    val userVotedCandidateId: String? = null
) {
    val totalVotes: Int get() = candidates.sumOf { it.votesCount }
}

enum class LeaderboardTimePeriod(val label: String, val description: String) {
    WEEKLY("Weekly", "Top performers in this week's live battles & showcases"),
    MONTHLY("Monthly", "Monthly tournament rankings across all stages"),
    ALL_TIME("All-Time", "Global Hall of Fame cumulative creator standings")
}

enum class LeaderboardCategoryFilter(val label: String) {
    ALL("All Categories"),
    MUSIC("Music"),
    SPOKEN_WORD("Spoken Word")
}

enum class LeaderboardRankingMode(val label: String) {
    CUMULATIVE_RATINGS("Audience Ratings"),
    STAGE_VOTES("Stage Votes")
}

enum class CreatorCompetitiveTier(
    val label: String,
    val minCumulativeStars: Float,
    val colorHex: Long
) {
    LEGEND("Stage Legend", 8000f, 0xFFFFB703),
    GRANDMASTER("Grandmaster", 4500f, 0xFFF72585),
    HEADLINER("Headliner", 2000f, 0xFF00F5D4),
    CONTENDER("Contender", 500f, 0xFF9D4EDD),
    RISING_STAR("Rising Star", 0f, 0xFF00BBF9);

    companion object {
        fun fromCumulativeStars(stars: Float): CreatorCompetitiveTier = when {
            stars >= LEGEND.minCumulativeStars -> LEGEND
            stars >= GRANDMASTER.minCumulativeStars -> GRANDMASTER
            stars >= HEADLINER.minCumulativeStars -> HEADLINER
            stars >= CONTENDER.minCumulativeStars -> CONTENDER
            else -> RISING_STAR
        }
    }
}

data class GlobalRatingsArenaSummary(
    val totalCumulativeRatingStars: Float = 0f,
    val globalWeightedAverageRating: Float = 0f,
    val totalAudienceRatingsCount: Int = 0,
    val totalRankedCreators: Int = 0
) {
    val formattedTotalCumulativeStars: String
        get() = String.format(java.util.Locale.US, "%,.0f", totalCumulativeRatingStars.coerceAtLeast(0f))

    val formattedGlobalAverage: String
        get() = String.format(java.util.Locale.US, "%.2f", globalWeightedAverageRating.coerceIn(0f, 5f))

    val formattedTotalRatingsCount: String
        get() = String.format(java.util.Locale.US, "%,d", totalAudienceRatingsCount.coerceAtLeast(0))
}

data class CreatorRanking(
    val rank: Int,
    val previousRank: Int,
    val creatorId: String,
    val artistName: String,
    val stageTitle: String,
    val category: PerformanceCategory,
    val genre: String,
    val topPerformanceTitle: String,
    val topPerformanceId: String,
    val totalVotes: Int,
    val totalViews: Int,
    val totalLikes: Int,
    val totalScore: Int,
    val rankChange: Int, // e.g. +2, -1, 0, or 999 for NEW
    val isCurrentUser: Boolean = false,
    val verifiedBadge: Boolean = true,
    val avatarColorHex: Long = 0xFF9D4EDD,
    val cloudVotesCount: Int = 0,
    val uniqueVotersCount: Int = 0,
    val voteSharePercent: Float = 0f,
    val isVotedByCurrentUser: Boolean = false,
    val averageAudienceRating: Float = 4.8f,
    val totalAudienceRatingsCount: Int = 0,
    val cumulativeRatingPoints: Float = 0f,
    val ratingSharePercent: Float = 0f,
    val pointsToNextRank: Float = 0f,
    val nextRankArtistName: String? = null,
    val competitiveTier: CreatorCompetitiveTier = CreatorCompetitiveTier.fromCumulativeStars(cumulativeRatingPoints),
    val totalPerformancesCount: Int = 0
) {
    val formattedAverageRating: String
        get() = String.format(java.util.Locale.US, "%.2f", averageAudienceRating.coerceIn(0f, 5f))

    val formattedCumulativeStars: String
        get() = if (cumulativeRatingPoints >= 100f || cumulativeRatingPoints % 1f == 0f) {
            String.format(java.util.Locale.US, "%,.0f", cumulativeRatingPoints.coerceAtLeast(0f))
        } else {
            String.format(java.util.Locale.US, "%,.1f", cumulativeRatingPoints.coerceAtLeast(0f))
        }

    val formattedPointsToNextRank: String
        get() = if (pointsToNextRank >= 100f) {
            String.format(java.util.Locale.US, "%,.0f", pointsToNextRank.coerceAtLeast(0f))
        } else {
            String.format(java.util.Locale.US, "%,.1f", pointsToNextRank.coerceAtLeast(0f))
        }

    val fiveStarRatingsNeededToAdvance: Int
        get() = if (pointsToNextRank <= 0f) {
            0
        } else {
            kotlin.math.ceil((pointsToNextRank + 0.1f) / 5.0f).toInt().coerceAtLeast(1)
        }
}

data class VoteRecord(
    val id: String = "",
    val performerId: String,
    val performerName: String,
    val targetId: String,
    val targetType: String = "PERFORMER",
    val category: PerformanceCategory = PerformanceCategory.MUSIC,
    val genre: String = "Original",
    val stageTitle: String = "Stage Performer",
    val performanceId: String? = null,
    val topPerformanceTitle: String? = null,
    val sessionId: String? = null,
    val userId: String,
    val userName: String,
    val voteDelta: Int = 1,
    val voteTier: String = "VOTE",
    val status: String = "COUNTED",
    val avatarColorHex: Long = 0xFF9D4EDD,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class PerformerVoteSummary(
    val performerId: String,
    val performerName: String,
    val category: PerformanceCategory,
    val genre: String,
    val stageTitle: String,
    val topPerformanceId: String,
    val topPerformanceTitle: String,
    val avatarColorHex: Long,
    val aggregatedVotes: Int,
    val uniqueVoterCount: Int,
    val voteDocumentsCount: Int,
    val votedByCurrentUser: Boolean,
    val latestVoteAtMillis: Long
)

data class VotesAggregationSnapshot(
    val totalCountedVotes: Int = 0,
    val totalVoteRecords: Int = 0,
    val uniqueVotersCount: Int = 0,
    val performerSummaries: List<PerformerVoteSummary> = emptyList(),
    val lastSyncedAtMillis: Long = 0L,
    val isLiveCloudSynced: Boolean = false
)

sealed interface LeaderboardUiState {
    data object Loading : LeaderboardUiState
    data class Success(
        val rankings: List<CreatorRanking>,
        val aggregationSnapshot: VotesAggregationSnapshot,
        val isAuthenticated: Boolean,
        val rankingMode: LeaderboardRankingMode = LeaderboardRankingMode.CUMULATIVE_RATINGS,
        val arenaSummary: GlobalRatingsArenaSummary = GlobalRatingsArenaSummary()
    ) : LeaderboardUiState
    data class Error(
        val message: String,
        val fallbackRankings: List<CreatorRanking> = emptyList()
    ) : LeaderboardUiState
}

data class CreatorProfile(
    val creatorId: String,
    val artistName: String,
    val stageTitle: String,
    val bio: String,
    val primaryCategory: PerformanceCategory,
    val avatarUrl: String? = null,
    val avatarColorHex: Long = 0xFF9D4EDD,
    val bannerGradientHex: Long = 0xFF161226,
    val audienceRating: Float = 4.9f,
    val totalAudienceRatingsCount: Int = 1240,
    val socialSpotify: String = "@spotify.com/artist",
    val socialInstagram: String = "@stageverse",
    val socialYoutube: String = "youtube.com/@stageverse",
    val socialWebsite: String = "https://versepulse.stage",
    val verifiedBadge: Boolean = true,
    val isCurrentUser: Boolean = false
)

data class Performer(
    val id: String,
    val creatorId: String = id,
    val ownerUid: String = "",
    val name: String,
    val stageTitle: String = "Stage Performer",
    val bio: String = "",
    val category: PerformanceCategory = PerformanceCategory.MUSIC,
    val avatarUrl: String? = null,
    val avatarColorHex: Long = 0xFF9D4EDD,
    val rating: Float = 5.0f,
    val ratingCount: Int = 1,
    val totalVotes: Int = 0,
    val totalPerformances: Int = 0,
    val totalSessions: Int = 0,
    val socialSpotify: String = "",
    val socialInstagram: String = "",
    val socialYoutube: String = "",
    val socialWebsite: String = "",
    val visibility: String = "PUBLIC",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    fun toCreatorProfile(currentUserId: String? = null): CreatorProfile = CreatorProfile(
        creatorId = creatorId,
        artistName = name,
        stageTitle = stageTitle,
        bio = bio,
        primaryCategory = category,
        avatarUrl = avatarUrl,
        avatarColorHex = avatarColorHex,
        audienceRating = rating,
        totalAudienceRatingsCount = ratingCount,
        socialSpotify = socialSpotify,
        socialInstagram = socialInstagram,
        socialYoutube = socialYoutube,
        socialWebsite = socialWebsite,
        isCurrentUser = currentUserId != null && (ownerUid == currentUserId || creatorId == currentUserId)
    )
}

fun CreatorProfile.toPerformer(
    ownerUid: String,
    totalVotes: Int = 0,
    totalPerformances: Int = 0,
    totalSessions: Int = 0
): Performer = Performer(
    id = creatorId,
    creatorId = creatorId,
    ownerUid = ownerUid,
    name = artistName,
    stageTitle = stageTitle,
    bio = bio,
    category = primaryCategory,
    avatarUrl = avatarUrl,
    avatarColorHex = avatarColorHex,
    rating = audienceRating,
    ratingCount = totalAudienceRatingsCount,
    totalVotes = totalVotes,
    totalPerformances = totalPerformances,
    totalSessions = totalSessions,
    socialSpotify = socialSpotify,
    socialInstagram = socialInstagram,
    socialYoutube = socialYoutube,
    socialWebsite = socialWebsite,
    visibility = "PUBLIC"
)

data class CreatorSessionStats(
    val totalSessions: Int = 0,
    val totalSpectatorsReached: Int = 0,
    val peakViewers: Int = 0,
    val totalLiveHours: Float = 0f,
    val totalSessionVotes: Int = 0,
    val pastSessions: List<LiveSession> = emptyList()
)

enum class StageReactionType(
    val firestoreField: String,
    val emoji: String,
    val defaultLabel: String,
    val accentColorHex: Long
) {
    FIRE("fireCount", "🔥", "Fire", 0xFFFF6B35),
    MIC_DROP("micDropCount", "🎤", "Mic Drop", 0xFF9D4EDD),
    OVATION("ovationCount", "👏", "Ovation", 0xFFFFB703),
    HEART("heartCount", "💖", "Heart", 0xFFF72585),
    ENCORE("encoreCount", "⚡", "Encore", 0xFF00F5D4)
}

data class LiveChatMessage(
    val id: String,
    val senderName: String,
    val text: String,
    val isCheer: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val senderAvatarColorHex: Long = 0xFF00F5D4,
    val badge: String? = null,
    val senderUid: String = "",
    val isHiddenByHost: Boolean = false
)

data class LiveSession(
    val id: String,
    val creatorId: String,
    val creatorName: String,
    val title: String,
    val category: PerformanceCategory,
    val genre: String,
    val visualizerTheme: VisualizerTheme,
    val audioType: SynthAudioType,
    val activePollId: String?,
    val spectatorCount: Int,
    val isLive: Boolean,
    val isCreatorMe: Boolean = false,
    val startedAt: Long = System.currentTimeMillis(),
    val chatMessages: List<LiveChatMessage> = emptyList(),
    val styleId: String? = null,
    val ownerUid: String = creatorId,
    val slowModeSeconds: Int = 0,
    val isChatLocked: Boolean = false,
    val pinnedAnnouncement: String? = null,
    val reactionCounts: Map<StageReactionType, Int> = emptyMap()
) {
    val totalReactionsCount: Int
        get() = reactionCounts.values.sum()
}

@IgnoreExtraProperties
data class LiveEvent(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val title: String = "",
    val category: String = PerformanceCategory.MUSIC.name,
    val genre: String = "Live Stage",
    val description: String = "",
    val participantCount: Long = 0L,
    val isLive: Boolean = true,
    val visibility: String = "PUBLIC",
    val sessionId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
    @get:Exclude val styleId: String? = null
) {
    @get:Exclude
    val performanceCategory: PerformanceCategory
        get() = try {
            PerformanceCategory.valueOf(category)
        } catch (_: Exception) {
            PerformanceCategory.MUSIC
        }

    @get:Exclude
    val currentParticipants: Int
        get() = participantCount.coerceAtLeast(0L).toInt()
}

fun LiveSession.toLiveEvent(ownerUid: String = creatorId): LiveEvent = LiveEvent(
    id = id,
    ownerUid = ownerUid,
    hostId = creatorId.ifBlank { ownerUid },
    hostName = creatorName.ifBlank { "Stage Host" },
    title = title,
    category = category.name,
    genre = genre.ifBlank { performanceStyle.defaultGenre },
    description = "Live ${performanceStyle.displayName.lowercase()} stage session",
    participantCount = spectatorCount.coerceAtLeast(0).toLong(),
    isLive = isLive,
    visibility = "PUBLIC",
    sessionId = id,
    styleId = styleId ?: performanceStyle.id
)

fun LiveEvent.toLiveSession(): LiveSession {
    val cat = performanceCategory
    val resolvedStyle = performanceStyle
    val resolvedVisualizer = when (resolvedStyle) {
        PerformanceStyle.RAP, PerformanceStyle.HIP_HOP -> VisualizerTheme.COSMIC_VINYL
        PerformanceStyle.JAZZ -> VisualizerTheme.STAGE_LIGHTS
        PerformanceStyle.POETRY, PerformanceStyle.SPOKEN_WORD -> VisualizerTheme.STAGE_LIGHTS
        PerformanceStyle.ELECTRONIC -> VisualizerTheme.CYAN_SPECTRUM
        PerformanceStyle.SOUL_RNB, PerformanceStyle.ACOUSTIC -> VisualizerTheme.NEON_WAVES
    }
    val resolvedAudio = when (resolvedStyle) {
        PerformanceStyle.RAP, PerformanceStyle.HIP_HOP -> SynthAudioType.HIPHOP_BEAT
        PerformanceStyle.JAZZ -> SynthAudioType.PIANO_WARMTH
        PerformanceStyle.POETRY, PerformanceStyle.SPOKEN_WORD -> SynthAudioType.POETRY_AMBIENCE
        PerformanceStyle.ELECTRONIC -> SynthAudioType.SYNTH_CHILL
        PerformanceStyle.SOUL_RNB, PerformanceStyle.ACOUSTIC -> SynthAudioType.ACOUSTIC_SOUL
    }
    val resolvedCreatorId = hostId.ifBlank { ownerUid }
    return LiveSession(
        id = sessionId?.takeIf { it.isNotBlank() } ?: id,
        creatorId = resolvedCreatorId,
        creatorName = hostName.ifBlank { "Stage Host" },
        title = title.ifBlank { "Live Stage Event" },
        category = cat,
        genre = genre.ifBlank { resolvedStyle.displayName },
        visualizerTheme = resolvedVisualizer,
        audioType = resolvedAudio,
        activePollId = null,
        spectatorCount = currentParticipants.coerceAtLeast(1),
        isLive = isLive,
        styleId = styleId ?: resolvedStyle.id,
        ownerUid = ownerUid.ifBlank { resolvedCreatorId }
    )
}

sealed interface LiveEventsUiState {
    data object Loading : LiveEventsUiState
    data class Success(
        val events: List<LiveEvent>,
        val joinedEventIds: Set<String> = emptySet()
    ) : LiveEventsUiState
    data class Error(val message: String) : LiveEventsUiState
}

enum class UserRole(val label: String) {
    SPECTATOR("Spectator Mode"),
    CREATOR("Creator Studio")
}

enum class AuthAccountProvider {
    GOOGLE,
    STANDALONE
}

enum class RegistrationAuthOption {
    STANDALONE,
    GOOGLE
}

data class UserAccount(
    val uid: String = "",
    val email: String? = null,
    val displayName: String = "",
    val photoUrl: String? = null,
    val role: UserRole = UserRole.SPECTATOR,
    val stageName: String = "",
    val stageTitle: String = "",
    val bio: String = "",
    val primaryCategory: PerformanceCategory = PerformanceCategory.MUSIC,
    val registrationComplete: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    @get:Exclude val authProvider: AuthAccountProvider = AuthAccountProvider.GOOGLE
) {
    val effectiveName: String
        get() = stageName.ifBlank { displayName.ifBlank { if (role == UserRole.CREATOR) "Stage Performer" else "Stage Spectator" } }

    val effectiveTitle: String
        get() = stageTitle.ifBlank {
            if (role == UserRole.CREATOR) "${primaryCategory.displayName} Performer" else "Stage Spectator"
        }

    fun toCreatorProfile(): CreatorProfile = CreatorProfile(
        creatorId = uid,
        artistName = effectiveName,
        stageTitle = effectiveTitle,
        bio = bio.ifBlank {
            if (role == UserRole.CREATOR) "Performing original ${primaryCategory.displayName.lowercase()} on VersePulse."
            else "Supporting live artists and voting in VersePulse Arena."
        },
        primaryCategory = primaryCategory,
        avatarUrl = photoUrl,
        avatarColorHex = if (primaryCategory == PerformanceCategory.MUSIC) 0xFF9D4EDD else 0xFF00B4D8,
        isCurrentUser = true
    )
}

data class RegistrationDraft(
    val role: UserRole = UserRole.SPECTATOR,
    val stageName: String = "",
    val stageTitle: String = "",
    val bio: String = "",
    val primaryCategory: PerformanceCategory = PerformanceCategory.MUSIC,
    val identifier: String = "",
    val password: String = "",
    val authProvider: AuthAccountProvider = AuthAccountProvider.GOOGLE
)

enum class AuthFlowMode {
    SIGN_IN,
    REGISTER
}

sealed interface AuthSessionState {
    data object Loading : AuthSessionState
    data class Unauthenticated(val errorMessage: String? = null) : AuthSessionState
    data class NeedsRegistration(
        val uid: String,
        val email: String?,
        val suggestedName: String,
        val photoUrl: String?,
        val initialRole: UserRole = UserRole.SPECTATOR
    ) : AuthSessionState
    data class Authenticated(val account: UserAccount) : AuthSessionState
}

enum class AppNavDestination(val label: String) {
    DISCOVER("Discover"),
    LIVE("Live Stage"),
    POLLS("Polls"),
    LEADERBOARD("Leaderboard"),
    STUDIO("Studio"),
    PROFILE("Profile")
}
