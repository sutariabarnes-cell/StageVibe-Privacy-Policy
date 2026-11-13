package com.example.model

/**
 * Fine-grained stage performance styles used to categorize both recorded/active stage
 * performances and live arena events (e.g., Rap, Poetry, Jazz, Spoken Word).
 *
 * Each [PerformanceStyle] maps to a top-level [PerformanceCategory] (`MUSIC` or `SPOKEN_WORD`)
 * for seamless compatibility with Cloud Firestore schemas and leaderboards.
 */
enum class PerformanceStyle(
    val id: String,
    val displayName: String,
    val parentCategory: PerformanceCategory,
    val defaultGenre: String,
    val accentColorHex: Long,
    val description: String,
    val keywords: List<String>
) {
    RAP(
        id = "rap",
        displayName = "Rap",
        parentCategory = PerformanceCategory.MUSIC,
        defaultGenre = "Rap & Boom-Bap",
        accentColorHex = 0xFFF72585,
        description = "Lyrical bars, boom-bap ciphers, and freestyle emceeing",
        keywords = listOf("rap", "boom-bap", "emcee", "cipher", "freestyle", "bars")
    ),
    HIP_HOP(
        id = "hip_hop",
        displayName = "Hip-Hop",
        parentCategory = PerformanceCategory.MUSIC,
        defaultGenre = "Hip-Hop",
        accentColorHex = 0xFFFF6B35,
        description = "808 grooves, turntable scratches, golden-era beats, and hip-hop culture",
        keywords = listOf("hip-hop", "hip hop", "hiphop", "trap", "turntablism", "breakbeat", "808", "golden era")
    ),
    POETRY(
        id = "poetry",
        displayName = "Poetry",
        parentCategory = PerformanceCategory.SPOKEN_WORD,
        defaultGenre = "Slam Poetry",
        accentColorHex = 0xFF00F5D4,
        description = "Slam poetry, lyrical verse, and poetic cadences",
        keywords = listOf("poetry", "poem", "slam", "sonnet", "stanza", "poetic")
    ),
    JAZZ(
        id = "jazz",
        displayName = "Jazz",
        parentCategory = PerformanceCategory.MUSIC,
        defaultGenre = "Vocal & Nu-Jazz",
        accentColorHex = 0xFFFFB703,
        description = "Live brass, nu-jazz improvisation, and velvet swing",
        keywords = listOf("jazz", "nu-jazz", "sax", "saxophone", "swing", "bebop", "brass", "blue note")
    ),
    SPOKEN_WORD(
        id = "spoken_word",
        displayName = "Spoken Word",
        parentCategory = PerformanceCategory.SPOKEN_WORD,
        defaultGenre = "Spoken Word",
        accentColorHex = 0xFF00B4D8,
        description = "Dramatic monologues, griot storytelling, and stage spoken word",
        keywords = listOf("spoken word", "monologue", "storytelling", "griot", "spoken", "oration")
    ),
    SOUL_RNB(
        id = "soul_rnb",
        displayName = "Soul & R&B",
        parentCategory = PerformanceCategory.MUSIC,
        defaultGenre = "Neo-Soul & R&B",
        accentColorHex = 0xFF9D4EDD,
        description = "Warm vocal runs, neo-soul grooves, and R&B harmonies",
        keywords = listOf("neo-soul", "soul", "r&b", "rnb", "motown", "vocalist")
    ),
    ACOUSTIC(
        id = "acoustic",
        displayName = "Acoustic",
        parentCategory = PerformanceCategory.MUSIC,
        defaultGenre = "Acoustic Ballad",
        accentColorHex = 0xFFFFD166,
        description = "Unplugged guitar, intimate folk, and stripped-back ballads",
        keywords = listOf("acoustic", "folk", "unplugged", "ballad", "guitar", "singer-songwriter")
    ),
    ELECTRONIC(
        id = "electronic",
        displayName = "Synth & Lo-Fi",
        parentCategory = PerformanceCategory.MUSIC,
        defaultGenre = "Synthwave & Lo-Fi",
        accentColorHex = 0xFF00BBF9,
        description = "Neon synthwave arpeggios, chill beats, and ambient textures",
        keywords = listOf("synthwave", "synth", "electronic", "lo-fi", "lofi", "ambient", "chill")
    );

    fun toCategoryTag(): PerformanceCategoryTag = PerformanceCategoryTag(
        id = id,
        label = displayName,
        style = this,
        parentCategory = parentCategory,
        defaultGenre = defaultGenre,
        accentColorHex = accentColorHex,
        description = description
    )

    companion object {
        fun fromId(id: String?): PerformanceStyle? {
            if (id.isNullOrBlank()) return null
            val normalized = id.trim().lowercase()
            return entries.firstOrNull {
                it.id == normalized ||
                    it.name.lowercase() == normalized ||
                    it.displayName.lowercase() == normalized
            }
        }
    }
}

/**
 * Data model representing a selectable category tag in the filter chip bar or studio selector.
 * When [style] is `null`, the tag represents the unfiltered "All" view.
 */
data class PerformanceCategoryTag(
    val id: String,
    val label: String,
    val style: PerformanceStyle? = null,
    val parentCategory: PerformanceCategory? = style?.parentCategory,
    val defaultGenre: String = style?.defaultGenre ?: "All Categories",
    val accentColorHex: Long = 0xFF9D4EDD,
    val description: String = "All live music and spoken word categories"
) {
    val isAll: Boolean
        get() = style == null && parentCategory == null
}

/**
 * Resolved categorization metadata for a [Performance], [LiveEvent], or [LiveSession].
 */
data class PerformanceClassification(
    val parentCategory: PerformanceCategory,
    val style: PerformanceStyle,
    val genreLabel: String,
    val tag: PerformanceCategoryTag = style.toCategoryTag()
)

/**
 * Canonical catalog and resolution engine for categorizing performances and filtering
 * main-screen events and stage tracks.
 */
object PerformanceCategoryCatalog {

    val allTag = PerformanceCategoryTag(
        id = "all",
        label = "All",
        style = null,
        parentCategory = null,
        defaultGenre = "All Categories",
        accentColorHex = 0xFF9D4EDD,
        description = "Show all live events and stage performances"
    )

    /**
     * Ordered styles displayed in the main screen event & stage filter chip bar,
     * foregrounding Rap, Poetry, Jazz, and Spoken Word.
     */
    val orderedStyles: List<PerformanceStyle> = listOf(
        PerformanceStyle.RAP,
        PerformanceStyle.POETRY,
        PerformanceStyle.JAZZ,
        PerformanceStyle.SPOKEN_WORD,
        PerformanceStyle.HIP_HOP,
        PerformanceStyle.SOUL_RNB,
        PerformanceStyle.ACOUSTIC,
        PerformanceStyle.ELECTRONIC
    )

    val filterTags: List<PerformanceCategoryTag> =
        listOf(allTag) + orderedStyles.map { it.toCategoryTag() }

    val musicStyles: List<PerformanceStyle> =
        orderedStyles.filter { it.parentCategory == PerformanceCategory.MUSIC }

    fun stylesForCategory(category: PerformanceCategory?): List<PerformanceStyle> =
        if (category == null) orderedStyles else orderedStyles.filter { it.parentCategory == category }

    fun filterTagsForCategory(category: PerformanceCategory?): List<PerformanceCategoryTag> =
        when (category) {
            null -> filterTags
            PerformanceCategory.MUSIC -> listOf(
                PerformanceCategoryTag(
                    id = "all_music",
                    label = "All Music",
                    style = null,
                    parentCategory = PerformanceCategory.MUSIC,
                    defaultGenre = "All Music",
                    accentColorHex = 0xFF9D4EDD,
                    description = "Show all live music performances and events"
                )
            ) + stylesForCategory(PerformanceCategory.MUSIC).map { it.toCategoryTag() }
            PerformanceCategory.SPOKEN_WORD -> listOf(
                PerformanceCategoryTag(
                    id = "all_spoken_word",
                    label = "All Spoken Word",
                    style = null,
                    parentCategory = PerformanceCategory.SPOKEN_WORD,
                    defaultGenre = "All Spoken Word",
                    accentColorHex = 0xFF00B4D8,
                    description = "Show all spoken word and poetry stages"
                )
            ) + stylesForCategory(PerformanceCategory.SPOKEN_WORD).map { it.toCategoryTag() }
        }

    private val resolutionPriority: List<PerformanceStyle> = listOf(
        PerformanceStyle.JAZZ,
        PerformanceStyle.RAP,
        PerformanceStyle.HIP_HOP,
        PerformanceStyle.POETRY,
        PerformanceStyle.SPOKEN_WORD,
        PerformanceStyle.SOUL_RNB,
        PerformanceStyle.ACOUSTIC,
        PerformanceStyle.ELECTRONIC
    )

    private val rapWordRegex = Regex("\\brap\\b")

    /**
     * Resolves a deterministic [PerformanceStyle] from an optional [explicitStyleId],
     * [genre] descriptor, [title], and fallback [category].
     */
    fun resolveStyle(
        category: PerformanceCategory,
        genre: String,
        title: String = "",
        explicitStyleId: String? = null
    ): PerformanceStyle {
        PerformanceStyle.fromId(explicitStyleId)?.let { return it }

        val normalizedGenre = genre.trim().lowercase()
        if (normalizedGenre.isNotEmpty()) {
            PerformanceStyle.fromId(normalizedGenre)?.let { return it }
            matchKeywords(normalizedGenre)?.let { return it }
        }

        val normalizedTitle = title.trim().lowercase()
        if (normalizedTitle.isNotEmpty()) {
            matchKeywords(normalizedTitle)?.let { return it }
        }

        return if (category == PerformanceCategory.SPOKEN_WORD) {
            PerformanceStyle.SPOKEN_WORD
        } else {
            PerformanceStyle.SOUL_RNB
        }
    }

    private fun matchKeywords(haystack: String): PerformanceStyle? {
        for (candidate in resolutionPriority) {
            val matched = candidate.keywords.any { keyword ->
                if (candidate == PerformanceStyle.RAP && keyword == "rap") {
                    rapWordRegex.containsMatchIn(haystack)
                } else {
                    haystack.contains(keyword)
                }
            }
            if (matched) {
                return candidate
            }
        }
        return null
    }

    fun matchesFilter(
        itemStyle: PerformanceStyle,
        itemParentCategory: PerformanceCategory,
        selectedStyle: PerformanceStyle?,
        selectedCategory: PerformanceCategory? = null
    ): Boolean {
        val styleMatches = selectedStyle == null || itemStyle == selectedStyle
        val categoryMatches = selectedCategory == null || itemParentCategory == selectedCategory
        return styleMatches && categoryMatches
    }

    fun classify(performance: Performance): PerformanceClassification {
        val resolved = resolveStyle(
            category = performance.category,
            genre = performance.genre,
            title = performance.title,
            explicitStyleId = performance.styleId
        )
        return PerformanceClassification(
            parentCategory = performance.category,
            style = resolved,
            genreLabel = performance.genre.ifBlank { resolved.defaultGenre }
        )
    }

    fun classify(event: LiveEvent): PerformanceClassification {
        val cat = event.performanceCategory
        val resolved = resolveStyle(
            category = cat,
            genre = event.genre,
            title = event.title,
            explicitStyleId = event.styleId
        )
        return PerformanceClassification(
            parentCategory = cat,
            style = resolved,
            genreLabel = event.genre.ifBlank { resolved.defaultGenre }
        )
    }

    fun classify(session: LiveSession): PerformanceClassification {
        val resolved = resolveStyle(
            category = session.category,
            genre = session.genre,
            title = session.title,
            explicitStyleId = session.styleId
        )
        return PerformanceClassification(
            parentCategory = session.category,
            style = resolved,
            genreLabel = session.genre.ifBlank { resolved.defaultGenre }
        )
    }
}

val Performance.performanceStyle: PerformanceStyle
    get() = PerformanceCategoryCatalog.resolveStyle(
        category = category,
        genre = genre,
        title = title,
        explicitStyleId = styleId
    )

val Performance.classification: PerformanceClassification
    get() = PerformanceCategoryCatalog.classify(this)

val LiveEvent.performanceStyle: PerformanceStyle
    get() = PerformanceCategoryCatalog.resolveStyle(
        category = performanceCategory,
        genre = genre,
        title = title,
        explicitStyleId = styleId
    )

val LiveEvent.classification: PerformanceClassification
    get() = PerformanceCategoryCatalog.classify(this)

val LiveSession.performanceStyle: PerformanceStyle
    get() = PerformanceCategoryCatalog.resolveStyle(
        category = category,
        genre = genre,
        title = title,
        explicitStyleId = styleId
    )

val LiveSession.classification: PerformanceClassification
    get() = PerformanceCategoryCatalog.classify(this)
