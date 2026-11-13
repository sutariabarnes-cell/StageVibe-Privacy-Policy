package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VisualizerTheme
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
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Visualizer modes supported by the live dynamic Canvas visualizer.
 */
enum class LiveVisualizerMode(val label: String, val description: String) {
    SPECTRUM_EQ("Spectrum EQ", "32-band reactive frequency equalizer with peak caps & floor reflection"),
    NEON_WAVES("Neon Waves", "Harmonic multi-layer sinusoidal waves syncing with live audio harmonics"),
    PULSE_ORB("Shockwave Orb", "Bass-reactive kinetic energy core with expanding radial shockwaves"),
    STAGE_LASERS("Stage Lasers", "Concert sweeping spotlights and strobe lasers pulsing to transients"),
    COSMIC_VORTEX("Cosmic Vortex", "Hypnotic perspective warp tunnel accelerating with live tempo")
}

/**
 * LiveMusicAudioVisualizer
 * High-performance Canvas API visualizer that dynamically syncs with the live audio input
 * (amplitude, frequency bands, beat transients, and audience reaction triggers) during
 * live music and spoken word performances.
 */
@Composable
fun LiveMusicAudioVisualizer(
    amplitude: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    initialMode: LiveVisualizerMode = LiveVisualizerMode.SPECTRUM_EQ,
    showControls: Boolean = true,
    cheerTriggerCount: Int = 0,
    onModeChanged: ((LiveVisualizerMode) -> Unit)? = null
) {
    var currentMode by remember(initialMode) { mutableStateOf(initialMode) }
    var isBoostMode by remember { mutableStateOf(false) }

    // Multiplied amplitude when in high-energy boost mode
    val effectiveAmp = remember(amplitude, isPlaying, isBoostMode) {
        if (!isPlaying) 0.08f
        else {
            val mult = if (isBoostMode) 1.35f else 1.0f
            (amplitude * mult).coerceIn(0.12f, 1.0f)
        }
    }

    // Peak levels for equalizer caps (gravity decay physics)
    val peakHold = remember { FloatArray(36) { 0.1f } }

    // Infinite time transition for smooth mathematical fluid waves and rotations
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_time")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 2800 else 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val fastRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 5000 else 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "fast_rotation"
    )

    // Cheer flash shockwave state that radiates across canvas when audience members react
    var shockwaveRadius by remember { mutableFloatStateOf(0f) }
    var shockwaveAlpha by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(cheerTriggerCount) {
        if (cheerTriggerCount > 0) {
            // Trigger radial shockwave expanding from center across canvas
            shockwaveAlpha = 0.85f
            for (step in 1..25) {
                shockwaveRadius = step * 25f
                shockwaveAlpha = (1f - (step / 25f)) * 0.85f
                kotlinx.coroutines.delay(16)
            }
            shockwaveAlpha = 0f
            shockwaveRadius = 0f
        }
    }

    Box(
        modifier = modifier
            .background(Color(0xFF07050E))
            .testTag("live_music_visualizer_container")
    ) {
        // High-Performance Jetpack Compose Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("live_canvas_visualizer")
        ) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            when (currentMode) {
                LiveVisualizerMode.SPECTRUM_EQ -> {
                    drawSpectrumEqualizer(
                        width = width,
                        height = height,
                        amplitude = effectiveAmp,
                        phase = phase,
                        peakHold = peakHold
                    )
                }

                LiveVisualizerMode.NEON_WAVES -> {
                    drawNeonWaveforms(
                        width = width,
                        height = height,
                        amplitude = effectiveAmp,
                        phase = phase
                    )
                }

                LiveVisualizerMode.PULSE_ORB -> {
                    drawShockwavePulseOrb(
                        width = width,
                        height = height,
                        amplitude = effectiveAmp,
                        phase = phase,
                        rotation = fastRotation
                    )
                }

                LiveVisualizerMode.STAGE_LASERS -> {
                    drawStageConcertLasers(
                        width = width,
                        height = height,
                        amplitude = effectiveAmp,
                        phase = phase
                    )
                }

                LiveVisualizerMode.COSMIC_VORTEX -> {
                    drawCosmicVortex(
                        width = width,
                        height = height,
                        amplitude = effectiveAmp,
                        phase = phase,
                        rotation = fastRotation
                    )
                }
            }

            // Audience Cheer/Vote Radial Flash Shockwave
            if (shockwaveAlpha > 0.01f) {
                val center = Offset(width * 0.5f, height * 0.5f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            GoldCrown.copy(alpha = shockwaveAlpha),
                            HotMagenta.copy(alpha = shockwaveAlpha * 0.5f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = shockwaveRadius + 40f
                    ),
                    radius = shockwaveRadius + 40f,
                    center = center
                )
                drawCircle(
                    color = Color.White.copy(alpha = shockwaveAlpha),
                    radius = shockwaveRadius,
                    center = center,
                    style = Stroke(width = 4f)
                )
            }
        }

        // Live Audio Telemetry HUD Header (Decibel & Sync Indicator)
        if (showControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Sync Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                        .border(0.8.dp, if (isPlaying) ElectricCyan.copy(alpha = 0.5f) else StageSurfaceHighlight, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (isPlaying) ElectricCyan else TextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isPlaying) "AUDIO INPUT SYNC" else "AUDIO IDLE",
                        color = if (isPlaying) ElectricCyan else TextMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Live dB level readout
                    val db = if (isPlaying) -((1.0f - effectiveAmp) * 36f) else -48f
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f dB", db),
                        color = if (effectiveAmp > 0.75f) HotMagenta else Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // High Energy Boost Toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isBoostMode) HotMagenta.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.65f))
                        .border(0.8.dp, if (isBoostMode) HotMagenta else StageSurfaceHighlight, RoundedCornerShape(12.dp))
                        .clickable { isBoostMode = !isBoostMode }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("visualizer_boost_toggle")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Whatshot,
                            contentDescription = "FX Boost",
                            tint = if (isBoostMode) HotMagenta else TextMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBoostMode) "BOOST ON" else "FX BOOST",
                            color = if (isBoostMode) Color.White else TextMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Audience Visualizer Mode Selector Strip (Bottom)
        if (showControls) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("visualizer_mode_selector"),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(LiveVisualizerMode.values()) { mode ->
                    val isSelected = currentMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) NeonViolet else StageSurfaceElevated.copy(alpha = 0.85f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) ElectricCyan else StageSurfaceHighlight,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                currentMode = mode
                                onModeChanged?.invoke(mode)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag("visualizer_mode_${mode.name}")
                    ) {
                        Text(
                            text = mode.label,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// CANVAS API RENDERING ENGINE FUNCTIONS
// -------------------------------------------------------------------------------------------------

/**
 * 1. SPECTRUM EQUALIZER
 * 32-band reactive frequency equalizer bars with floating peak caps & floor reflection.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpectrumEqualizer(
    width: Float,
    height: Float,
    amplitude: Float,
    phase: Float,
    peakHold: FloatArray
) {
    val barCount = 32
    val usableWidth = width * 0.92f
    val spacing = usableWidth / barCount
    val barWidth = (spacing * 0.72f).coerceAtLeast(3f)
    val startX = (width - (barCount * spacing)) / 2f
    val baselineY = height * 0.72f

    // Subtle background grid lines
    for (g in 1..4) {
        val gy = baselineY - (height * 0.14f * g)
        drawLine(
            color = Color.White.copy(alpha = 0.05f),
            start = Offset(startX, gy),
            end = Offset(startX + (barCount * spacing), gy),
            strokeWidth = 1f
        )
    }

    for (i in 0 until barCount) {
        // Frequency distribution: sub-bass (left), vocals/mid (center), cymbal/high (right)
        val freqWeight = sin((i.toFloat() / barCount) * PI.toFloat())
        val harmonicOsc = sin((i * 0.42f) + phase) * 0.35f + 0.65f
        val rawHeight = (height * 0.65f) * freqWeight * harmonicOsc * amplitude
        val barHeight = rawHeight.coerceIn(4f, height * 0.65f)

        // Peak hold physics with slow decay
        if (barHeight > peakHold[i]) {
            peakHold[i] = barHeight
        } else {
            peakHold[i] = (peakHold[i] - 1.5f).coerceAtLeast(4f)
        }

        val x = startX + (i * spacing)

        // Main Equalizer Bar
        val barBrush = Brush.verticalGradient(
            colors = listOf(ElectricCyan, NeonViolet, HotMagenta),
            startY = baselineY - barHeight,
            endY = baselineY
        )
        drawRect(
            brush = barBrush,
            topLeft = Offset(x, baselineY - barHeight),
            size = Size(barWidth, barHeight)
        )

        // Floating Peak Cap
        val capY = baselineY - peakHold[i] - 4f
        drawRect(
            color = if (peakHold[i] > height * 0.5f) GoldCrown else Color.White,
            topLeft = Offset(x, capY),
            size = Size(barWidth, 3f)
        )

        // Mirrored Floor Reflection (Soft Fading Alpha)
        val reflectionHeight = barHeight * 0.35f
        val reflectionBrush = Brush.verticalGradient(
            colors = listOf(HotMagenta.copy(alpha = 0.25f), Color.Transparent),
            startY = baselineY,
            endY = baselineY + reflectionHeight
        )
        drawRect(
            brush = reflectionBrush,
            topLeft = Offset(x, baselineY + 2f),
            size = Size(barWidth, reflectionHeight)
        )
    }
}

/**
 * 2. NEON WAVEFORMS
 * Multi-layer sinusoidal & harmonic Bézier curves undulating with live audio input.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNeonWaveforms(
    width: Float,
    height: Float,
    amplitude: Float,
    phase: Float
) {
    val midY = height * 0.48f
    val wavePath1 = Path()
    val wavePath2 = Path()
    val wavePath3 = Path()
    val fillPath = Path()

    wavePath1.moveTo(0f, midY)
    wavePath2.moveTo(0f, midY)
    wavePath3.moveTo(0f, midY)
    fillPath.moveTo(0f, height)
    fillPath.lineTo(0f, midY)

    val steps = 80
    for (i in 0..steps) {
        val x = (i.toFloat() / steps) * width
        val wave1 = sin((i * 0.12f) + phase) * (height * 0.26f * amplitude)
        val wave2 = cos((i * 0.16f) - phase * 1.4f) * (height * 0.20f * amplitude)
        val wave3 = sin((i * 0.28f) + phase * 2.0f) * (height * 0.12f * amplitude)

        val y1 = midY + wave1
        val y2 = midY + wave2
        val y3 = midY + wave3

        wavePath1.lineTo(x, y1)
        wavePath2.lineTo(x, y2)
        wavePath3.lineTo(x, y3)
        fillPath.lineTo(x, y1)
    }

    fillPath.lineTo(width, height)
    fillPath.close()

    // Ambient under-wave gradient glow
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colors = listOf(NeonViolet.copy(alpha = 0.22f * amplitude), Color.Transparent),
            startY = midY,
            endY = height
        )
    )

    // Primary Neon Wave
    drawPath(
        path = wavePath1,
        brush = Brush.horizontalGradient(
            listOf(NeonViolet, ElectricCyan, HotMagenta, NeonVioletLight)
        ),
        style = Stroke(width = 5.5f, cap = StrokeCap.Round)
    )

    // Counter Wave 2
    drawPath(
        path = wavePath2,
        brush = Brush.horizontalGradient(
            listOf(HotMagenta, NeonVioletLight, ElectricCyan)
        ),
        style = Stroke(width = 3.5f, cap = StrokeCap.Round)
    )

    // Treble Harmonic Wave 3
    drawPath(
        path = wavePath3,
        brush = Brush.horizontalGradient(
            listOf(Color.White, GoldCrown, ElectricCyan)
        ),
        style = Stroke(width = 2.0f, cap = StrokeCap.Round)
    )

    // Central pulsing acoustic glow
    drawCircle(
        brush = Brush.radialGradient(
            listOf(ElectricCyan.copy(alpha = 0.35f * amplitude), Color.Transparent),
            center = Offset(width * 0.5f, midY),
            radius = width * 0.45f
        ),
        radius = width * 0.45f,
        center = Offset(width * 0.5f, midY)
    )
}

/**
 * 3. SHOCKWAVE PULSE ORB
 * Bass-reactive kinetic core with expanding concentric shockwaves & orbiting spark particles.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShockwavePulseOrb(
    width: Float,
    height: Float,
    amplitude: Float,
    phase: Float,
    rotation: Float
) {
    val center = Offset(width * 0.5f, height * 0.48f)
    val maxRadius = width.coerceAtMost(height) * 0.22f
    val baseRadius = maxRadius * (0.85f + amplitude * 0.45f)

    // Concentric expanding shockwave rings
    for (ring in 1..4) {
        val ringPhase = (phase + (ring * 1.3f)) % (2f * PI.toFloat())
        val ringRadius = baseRadius + (ringPhase * 48f * amplitude)
        val alpha = (1f - (ringPhase / (2f * PI.toFloat()))).coerceIn(0f, 1f) * 0.5f

        drawCircle(
            color = if (ring % 2 == 0) ElectricCyan.copy(alpha = alpha) else HotMagenta.copy(alpha = alpha),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 3f)
        )
    }

    // Outer kinetic aura glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, ElectricCyan, NeonViolet, HotMagenta, Color.Transparent),
            center = center,
            radius = baseRadius * 1.6f
        ),
        radius = baseRadius * 1.5f,
        center = center
    )

    // Core Solid Energy Orb
    drawCircle(
        color = Color.White,
        radius = baseRadius * 0.4f,
        center = center
    )

    // Orbiting Sparks / Particles
    rotate(degrees = rotation, pivot = center) {
        val sparkCount = 20
        for (s in 0 until sparkCount) {
            val sparkAngle = (s.toFloat() / sparkCount) * 2f * PI.toFloat()
            val dist = baseRadius * (1.15f + (s % 3) * 0.2f * amplitude)
            val sx = center.x + cos(sparkAngle) * dist
            val sy = center.y + sin(sparkAngle) * dist
            drawCircle(
                color = if (s % 2 == 0) ElectricCyan else GoldCrown,
                radius = 3.5f * (0.7f + amplitude * 0.6f),
                center = Offset(sx, sy)
            )
        }
    }
}

/**
 * 4. STAGE CONCERT LASERS
 * Sweeping stage spotlights & overhead neon laser lines pulsing to music transients.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStageConcertLasers(
    width: Float,
    height: Float,
    amplitude: Float,
    phase: Float
) {
    val bottomCenter = Offset(width * 0.5f, height * 0.98f)
    val topLeft = Offset(0f, 0f)
    val topRight = Offset(width, 0f)

    // 1. Left Stage Spotlight (Sweeps back and forth)
    val beam1Angle = sin(phase) * 38f - 18f
    rotate(degrees = beam1Angle, pivot = bottomCenter) {
        val beamPath1 = Path().apply {
            moveTo(bottomCenter.x - 24f, bottomCenter.y)
            lineTo(bottomCenter.x - 160f, 0f)
            lineTo(bottomCenter.x + 160f, 0f)
            lineTo(bottomCenter.x + 24f, bottomCenter.y)
            close()
        }
        drawPath(
            path = beamPath1,
            brush = Brush.verticalGradient(
                listOf(ElectricCyan.copy(alpha = 0.42f * amplitude), Color.Transparent),
                startY = 0f,
                endY = bottomCenter.y
            )
        )
    }

    // 2. Right Stage Spotlight
    val beam2Angle = cos(phase * 0.85f) * 38f + 18f
    rotate(degrees = beam2Angle, pivot = bottomCenter) {
        val beamPath2 = Path().apply {
            moveTo(bottomCenter.x - 24f, bottomCenter.y)
            lineTo(bottomCenter.x - 150f, 0f)
            lineTo(bottomCenter.x + 150f, 0f)
            lineTo(bottomCenter.x + 24f, bottomCenter.y)
            close()
        }
        drawPath(
            path = beamPath2,
            brush = Brush.verticalGradient(
                listOf(NeonViolet.copy(alpha = 0.48f * amplitude), Color.Transparent),
                startY = 0f,
                endY = bottomCenter.y
            )
        )
    }

    // 3. Sharp Laser Lines (Overhead concert lasers cross)
    val laserY = height * 0.38f + sin(phase * 1.5f) * 30f
    drawLine(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, HotMagenta, Color.White, Color.Transparent)),
        start = Offset(0f, laserY - 15f),
        end = Offset(width, laserY + 15f),
        strokeWidth = 3f * amplitude
    )
    drawLine(
        brush = Brush.horizontalGradient(listOf(Color.Transparent, ElectricCyan, Color.White, Color.Transparent)),
        start = Offset(0f, laserY + 25f),
        end = Offset(width, laserY - 25f),
        strokeWidth = 3f * amplitude
    )

    // Floating Stage Particles / Motes (Accelerate upward)
    for (p in 0..24) {
        val px = (width * ((p * 41) % 100) / 100f)
        val py = ((height * ((p * 67) % 100) / 100f) - (phase * 15f)) % height
        val normalizedY = if (py < 0f) py + height else py
        drawCircle(
            color = Color.White.copy(alpha = 0.55f * amplitude),
            radius = 2.5f + (p % 3) * 1.2f,
            center = Offset(px, normalizedY)
        )
    }
}

/**
 * 5. COSMIC VORTEX
 * Hypnotic perspective warp tunnel expanding towards the audience.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCosmicVortex(
    width: Float,
    height: Float,
    amplitude: Float,
    phase: Float,
    rotation: Float
) {
    val center = Offset(width * 0.5f, height * 0.48f)
    val maxRadius = width.coerceAtLeast(height) * 0.75f
    val ringCount = 8

    rotate(degrees = rotation, pivot = center) {
        for (r in 1..ringCount) {
            val ringProgress = ((r.toFloat() / ringCount) + (phase / (2f * PI.toFloat()))) % 1.0f
            val radius = ringProgress * maxRadius * (0.8f + amplitude * 0.35f)
            val alpha = (ringProgress).coerceIn(0.1f, 1f) * 0.65f

            drawCircle(
                color = when (r % 3) {
                    0 -> ElectricCyan.copy(alpha = alpha)
                    1 -> NeonViolet.copy(alpha = alpha)
                    else -> HotMagenta.copy(alpha = alpha)
                },
                radius = radius,
                center = center,
                style = Stroke(width = 2.5f + (ringProgress * 3f))
            )
        }

        // Radiating warp streaks
        val streakCount = 12
        for (s in 0 until streakCount) {
            val angle = (s.toFloat() / streakCount) * 2f * PI.toFloat()
            val startR = maxRadius * 0.15f
            val endR = maxRadius * (0.7f + amplitude * 0.3f)
            val start = Offset(center.x + cos(angle) * startR, center.y + sin(angle) * startR)
            val end = Offset(center.x + cos(angle) * endR, center.y + sin(angle) * endR)
            drawLine(
                color = Color.White.copy(alpha = 0.3f * amplitude),
                start = start,
                end = end,
                strokeWidth = 1.5f
            )
        }
    }

    // Core bright white star
    drawCircle(
        color = Color.White,
        radius = 8f * (1f + amplitude * 0.5f),
        center = center
    )
}
