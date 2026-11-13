package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.VotesLeaderboardRepository
import com.example.model.Performance
import com.example.security.UploadEncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Room metadata row indexing an encrypted local offline playback copy stored under
 * `filesDir/offline_cache/<id>.vpenc` (authenticated `VPENC1` AES-256-GCM container).
 */
@Entity(tableName = "offline_cached_tracks")
data class OfflineCachedTrackEntity(
    @PrimaryKey val performanceId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val category: String,
    val genre: String,
    val durationSec: Int,
    val encryptedLocalAudioPath: String? = null,
    val encryptedLocalVideoPath: String? = null,
    val cachedBytes: Long = 0L,
    val pinnedAtTimestamp: Long = System.currentTimeMillis(),
    val lastPlayedTimestamp: Long = System.currentTimeMillis()
) {
    val formattedCachedSize: String
        get() {
            val kb = cachedBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(Locale.US, "%.1f MB", mb)
            } else {
                String.format(Locale.US, "%.1f KB", kb.coerceAtLeast(0.1))
            }
        }
}

@Dao
interface OfflineTrackCacheDao {
    @Query("SELECT * FROM offline_cached_tracks ORDER BY pinnedAtTimestamp DESC")
    fun observeAll(): Flow<List<OfflineCachedTrackEntity>>

    @Query("SELECT * FROM offline_cached_tracks ORDER BY lastPlayedTimestamp ASC")
    suspend fun getAllLeastRecentlyPlayedFirst(): List<OfflineCachedTrackEntity>

    @Query("SELECT * FROM offline_cached_tracks WHERE performanceId = :id LIMIT 1")
    suspend fun getById(id: String): OfflineCachedTrackEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineCachedTrackEntity)

    @Query("UPDATE offline_cached_tracks SET lastPlayedTimestamp = :timestamp WHERE performanceId = :id")
    suspend fun touchLastPlayed(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM offline_cached_tracks WHERE performanceId = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM offline_cached_tracks")
    suspend fun clearAll()
}

@Database(
    entities = [OfflineCachedTrackEntity::class],
    version = 1,
    exportSchema = false
)
abstract class OfflineTrackCacheDatabase : RoomDatabase() {
    abstract fun offlineTrackCacheDao(): OfflineTrackCacheDao

    companion object {
        private const val DB_NAME = "versepulse_offline_cache.db"

        @Volatile
        private var instance: OfflineTrackCacheDatabase? = null

        fun getInstance(context: Context): OfflineTrackCacheDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    OfflineTrackCacheDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

/**
 * Repository managing user-pinned encrypted offline playback copies (`VPENC1` AES-256-GCM)
 * paired with a Room index (`OfflineTrackCacheDatabase`) and a 150 MB LRU quota guard.
 */
class OfflinePlaybackCacheRepository(
    context: Context,
    databaseOverride: OfflineTrackCacheDatabase? = null
) {
    private val appContext = context.applicationContext ?: context
    private val database: OfflineTrackCacheDatabase =
        databaseOverride ?: OfflineTrackCacheDatabase.getInstance(appContext)
    private val dao: OfflineTrackCacheDao = database.offlineTrackCacheDao()

    companion object {
        const val MAX_OFFLINE_CACHE_BYTES: Long = 150L * 1024L * 1024L // 150 MB cap
        private const val OFFLINE_CACHE_DIR = "offline_cache"
    }

    fun observeCachedTracks(): Flow<List<OfflineCachedTrackEntity>> = dao.observeAll()

    suspend fun getCachedTrack(performanceId: String): OfflineCachedTrackEntity? =
        dao.getById(performanceId)

    suspend fun recordOfflinePlayback(performanceId: String) {
        dao.touchLastPlayed(performanceId, System.currentTimeMillis())
    }

    /**
     * Toggles offline pinning for [performance].
     * Returns `true` if the performance is now pinned offline, or `false` if unpinned.
     */
    suspend fun togglePinPerformance(performance: Performance): Boolean = withContext(Dispatchers.IO) {
        val existing = dao.getById(performance.id)
        if (existing != null) {
            unpinPerformance(performance.id)
            false
        } else {
            pinPerformance(performance)
            true
        }
    }

    suspend fun pinPerformance(performance: Performance): OfflineCachedTrackEntity = withContext(Dispatchers.IO) {
        UploadEncryptionManager.bindContext(appContext)
        val cacheDir = File(appContext.filesDir, OFFLINE_CACHE_DIR).apply {
            if (!exists()) mkdirs()
        }
        val safeId = VotesLeaderboardRepository.sanitizeId(performance.id)

        var encryptedAudioPath: String? = null
        var encryptedVideoPath: String? = null
        var totalBytes = 0L

        // 1. Cache custom audio file if present on disk
        val sourceAudio = performance.customAudioPath?.takeIf { it.isNotBlank() }?.let { File(it) }
        if (sourceAudio != null && sourceAudio.exists() && sourceAudio.isFile) {
            val destAudio = File(cacheDir, "offline_audio_${safeId}${UploadEncryptionManager.ENCRYPTED_FILE_EXTENSION}")
            if (UploadEncryptionManager.isEncryptedFile(sourceAudio)) {
                sourceAudio.copyTo(destAudio, overwrite = true)
            } else {
                FileInputStream(sourceAudio).use { fis ->
                    UploadEncryptionManager.encryptStreamToFile(appContext, fis, destAudio)
                }
            }
            encryptedAudioPath = destAudio.absolutePath
            totalBytes += destAudio.length()
        }

        // 2. Cache custom video file if present on disk
        val sourceVideo = performance.customVideoPath?.takeIf { it.isNotBlank() }?.let { File(it) }
        if (sourceVideo != null && sourceVideo.exists() && sourceVideo.isFile) {
            val destVideo = File(cacheDir, "offline_video_${safeId}${UploadEncryptionManager.ENCRYPTED_FILE_EXTENSION}")
            if (UploadEncryptionManager.isEncryptedFile(sourceVideo)) {
                sourceVideo.copyTo(destVideo, overwrite = true)
            } else {
                FileInputStream(sourceVideo).use { fis ->
                    UploadEncryptionManager.encryptStreamToFile(appContext, fis, destVideo)
                }
            }
            encryptedVideoPath = destVideo.absolutePath
            totalBytes += destVideo.length()
        }

        // 3. For synthesized stage tracks (or cloud tracks without local raw media yet), write an
        //    authenticated VPENC1 AES-256-GCM offline stage capsule (lyrics/script, synth profile,
        //    visualizer preset, and deterministic acoustic envelope) so offline replay works 100% locally.
        if (encryptedAudioPath == null && encryptedVideoPath == null) {
            val destCapsule = File(cacheDir, "offline_stage_${safeId}${UploadEncryptionManager.ENCRYPTED_FILE_EXTENSION}")
            val capsulePayload = buildString {
                appendLine("VERSEPULSE_STAGE_REPLAY_V1")
                appendLine("id=${performance.id}")
                appendLine("title=${performance.title}")
                appendLine("artist=${performance.artistName}")
                appendLine("category=${performance.category.name}")
                appendLine("genre=${performance.genre}")
                appendLine("durationSec=${performance.durationSec}")
                appendLine("audioType=${performance.audioType.name}")
                appendLine("visualizerTheme=${performance.visualizerTheme.name}")
                appendLine("---SCRIPT---")
                append(performance.lyricsOrScript)
            }.toByteArray(StandardCharsets.UTF_8)

            val encRes = UploadEncryptionManager.encryptBytesToFile(appContext, capsulePayload, destCapsule)
            encryptedAudioPath = encRes.outputFile.absolutePath
            totalBytes += encRes.encryptedSizeBytes
        }

        val now = System.currentTimeMillis()
        val entity = OfflineCachedTrackEntity(
            performanceId = performance.id,
            title = performance.title,
            artistId = performance.artistId,
            artistName = performance.artistName,
            category = performance.category.name,
            genre = performance.genre,
            durationSec = performance.durationSec,
            encryptedLocalAudioPath = encryptedAudioPath,
            encryptedLocalVideoPath = encryptedVideoPath,
            cachedBytes = totalBytes,
            pinnedAtTimestamp = now,
            lastPlayedTimestamp = now
        )
        dao.upsert(entity)
        enforceDiskQuotaLocked(keepId = performance.id)
        UploadEncryptionManager.refreshVaultStatus(appContext)
        entity
    }

    suspend fun unpinPerformance(performanceId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getById(performanceId)
        if (existing != null) {
            existing.encryptedLocalAudioPath?.let { UploadEncryptionManager.secureDelete(File(it)) }
            existing.encryptedLocalVideoPath?.let { UploadEncryptionManager.secureDelete(File(it)) }
            dao.deleteById(performanceId)
        }
        UploadEncryptionManager.refreshVaultStatus(appContext)
    }

    /**
     * Permanently deletes all cached offline `.vpenc` files and clears the Room index.
     * Invoked both from the Profile Offline Vault Clear action and during full Account Deletion.
     */
    suspend fun clearAllCache() = withContext(Dispatchers.IO) {
        val allEntries = dao.getAllLeastRecentlyPlayedFirst()
        allEntries.forEach { entry ->
            entry.encryptedLocalAudioPath?.let { UploadEncryptionManager.secureDelete(File(it)) }
            entry.encryptedLocalVideoPath?.let { UploadEncryptionManager.secureDelete(File(it)) }
        }
        val cacheDir = File(appContext.filesDir, OFFLINE_CACHE_DIR)
        if (cacheDir.exists() && cacheDir.isDirectory) {
            cacheDir.listFiles()?.forEach { f ->
                if (f.isFile) {
                    UploadEncryptionManager.secureDelete(f)
                }
            }
        }
        dao.clearAll()
        UploadEncryptionManager.refreshVaultStatus(appContext)
    }

    private suspend fun enforceDiskQuotaLocked(keepId: String) {
        val ordered = dao.getAllLeastRecentlyPlayedFirst()
        var runningTotal = ordered.sumOf { it.cachedBytes }
        if (runningTotal <= MAX_OFFLINE_CACHE_BYTES) return

        for (candidate in ordered) {
            if (runningTotal <= MAX_OFFLINE_CACHE_BYTES) break
            if (candidate.performanceId == keepId) continue
            candidate.encryptedLocalAudioPath?.let { UploadEncryptionManager.secureDelete(File(it)) }
            candidate.encryptedLocalVideoPath?.let { UploadEncryptionManager.secureDelete(File(it)) }
            dao.deleteById(candidate.performanceId)
            runningTotal -= candidate.cachedBytes
        }
    }
}
