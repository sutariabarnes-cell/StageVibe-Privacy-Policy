package com.example.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaDataSource
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.security.UploadEncryptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Renders a creator's uploaded performance video inside a Stage frame using TextureView +
 * MediaPlayer so video frames composite via the standard RenderThread hardware layer without
 * registering an extra SurfaceView SurfaceSyncGroup on VRI[MainActivity].
 *
 * Transparently streams AES-256-GCM encrypted `.vpenc` video containers and thumbnails in memory
 * via [UploadEncryptionManager] without writing plaintext copies to disk.
 */
@Composable
fun StageVideoPlayer(
    videoFilePath: String,
    isPlaying: Boolean,
    muteAudio: Boolean = false,
    loop: Boolean = true,
    thumbnailPath: String? = null,
    onProgressSeconds: (Int) -> Unit = {},
    onPlaybackError: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current.applicationContext
    val fileExists = remember(videoFilePath) {
        videoFilePath.isNotBlank() && File(videoFilePath).exists()
    }

    if (!fileExists) {
        LaunchedEffect(videoFilePath) {
            onPlaybackError()
        }
        return
    }

    val currentIsPlaying by rememberUpdatedState(isPlaying)
    val currentMuteAudio by rememberUpdatedState(muteAudio)
    val currentLoop by rememberUpdatedState(loop)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)

    var mediaPlayerRef by remember(videoFilePath) { mutableStateOf<MediaPlayer?>(null) }
    var mediaDataSourceRef by remember(videoFilePath) { mutableStateOf<MediaDataSource?>(null) }
    var surfaceRef by remember(videoFilePath) { mutableStateOf<Surface?>(null) }
    var isPrepared by remember(videoFilePath) { mutableStateOf(false) }

    val resolvedThumbnailModel by produceState<Any?>(initialValue = null, key1 = thumbnailPath) {
        value = if (thumbnailPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val thumbFile = File(thumbnailPath)
                if (!thumbFile.exists()) {
                    null
                } else if (UploadEncryptionManager.isEncryptedFile(thumbFile)) {
                    runCatching {
                        UploadEncryptionManager.decryptFileToBytes(appContext, thumbFile)
                    }.getOrNull()
                } else {
                    thumbFile
                }
            }
        }
    }

    fun releasePlayerResources() {
        isPrepared = false
        val player = mediaPlayerRef
        mediaPlayerRef = null
        runCatching {
            player?.setSurface(null)
            if (player?.isPlaying == true) {
                player.stop()
            }
            player?.reset()
            player?.release()
        }
        val dataSource = mediaDataSourceRef
        mediaDataSourceRef = null
        runCatching { dataSource?.close() }
        val surface = surfaceRef
        surfaceRef = null
        runCatching { surface?.release() }
    }

    LaunchedEffect(muteAudio, mediaPlayerRef, isPrepared) {
        if (!isPrepared) return@LaunchedEffect
        val vol = if (muteAudio) 0f else 1f
        runCatching { mediaPlayerRef?.setVolume(vol, vol) }
    }

    LaunchedEffect(isPlaying, isPrepared, mediaPlayerRef) {
        val player = mediaPlayerRef ?: return@LaunchedEffect
        if (!isPrepared) return@LaunchedEffect
        runCatching {
            if (isPlaying) {
                if (!player.isPlaying) {
                    player.start()
                }
            } else if (player.isPlaying) {
                player.pause()
            }
        }
    }

    LaunchedEffect(isPlaying, isPrepared, mediaPlayerRef) {
        if (!isPlaying || !isPrepared) return@LaunchedEffect
        while (isPlaying) {
            val posMs = runCatching { mediaPlayerRef?.currentPosition ?: 0 }.getOrDefault(0)
            if (posMs >= 0) {
                onProgressSeconds(posMs / 1000)
            }
            delay(500L)
        }
    }

    DisposableEffect(videoFilePath) {
        onDispose {
            releasePlayerResources()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag("stage_video_player"),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = true
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            releasePlayerResources()
                            val surface = Surface(surfaceTexture)
                            surfaceRef = surface
                            val mp = MediaPlayer()
                            mediaPlayerRef = mp
                            runCatching {
                                mp.setSurface(surface)
                                mp.setOnErrorListener { _, _, _ ->
                                    isPrepared = false
                                    currentOnPlaybackError()
                                    true
                                }
                                mp.setOnVideoSizeChangedListener { _, videoW, videoH ->
                                    applyCenterCropTransform(this@apply, videoW, videoH)
                                }
                                mp.setOnPreparedListener { preparedMp ->
                                    isPrepared = true
                                    runCatching {
                                        applyCenterCropTransform(
                                            this@apply,
                                            preparedMp.videoWidth,
                                            preparedMp.videoHeight
                                        )
                                        preparedMp.isLooping = currentLoop
                                        val vol = if (currentMuteAudio) 0f else 1f
                                        preparedMp.setVolume(vol, vol)
                                        if (currentIsPlaying) {
                                            preparedMp.start()
                                        } else {
                                            preparedMp.seekTo(1)
                                        }
                                    }
                                }
                                mp.setOnCompletionListener { completedMp ->
                                    if (currentLoop) {
                                        runCatching {
                                            completedMp.seekTo(0)
                                            if (currentIsPlaying) completedMp.start()
                                        }
                                    }
                                }
                                val videoFile = File(videoFilePath)
                                if (UploadEncryptionManager.isEncryptedFile(videoFile)) {
                                    val encryptedDs = UploadEncryptionManager.createMediaDataSource(
                                        ctx.applicationContext,
                                        videoFile
                                    )
                                    mediaDataSourceRef = encryptedDs
                                    mp.setDataSource(encryptedDs)
                                } else {
                                    mp.setDataSource(videoFilePath)
                                }
                                mp.prepareAsync()
                            }.onFailure {
                                releasePlayerResources()
                                currentOnPlaybackError()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(
                            surfaceTexture: SurfaceTexture,
                            width: Int,
                            height: Int
                        ) {
                            val mp = mediaPlayerRef ?: return
                            if (isPrepared) {
                                runCatching {
                                    applyCenterCropTransform(this@apply, mp.videoWidth, mp.videoHeight)
                                }
                            }
                        }

                        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                            releasePlayerResources()
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isPrepared && resolvedThumbnailModel != null) {
            AsyncImage(
                model = resolvedThumbnailModel,
                contentDescription = "Stage video frame preview",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun applyCenterCropTransform(textureView: TextureView, videoWidth: Int, videoHeight: Int) {
    val viewWidth = textureView.width.toFloat()
    val viewHeight = textureView.height.toFloat()
    if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) return

    val viewRatio = viewWidth / viewHeight
    val videoRatio = videoWidth.toFloat() / videoHeight.toFloat()

    val scaleX: Float
    val scaleY: Float
    if (videoRatio > viewRatio) {
        scaleX = videoRatio / viewRatio
        scaleY = 1f
    } else {
        scaleX = 1f
        scaleY = viewRatio / videoRatio
    }

    val matrix = Matrix().apply {
        setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f)
    }
    textureView.setTransform(matrix)
}
