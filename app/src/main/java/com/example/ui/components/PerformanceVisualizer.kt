package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.example.model.VisualizerTheme
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GoldCrown
import com.example.ui.theme.HotMagenta
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.NeonVioletLight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PerformanceVisualizer(
    theme: VisualizerTheme,
    amplitude: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer_motion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 3000 else 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 6000 else 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Box(
        modifier = modifier.background(Color(0xFF090714))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val amp = if (isPlaying) amplitude.coerceIn(0.15f, 1.0f) else 0.1f

            when (theme) {
                VisualizerTheme.NEON_WAVES -> {
                    // Fluid undulating neon sine waves
                    val wavePath1 = Path()
                    val wavePath2 = Path()
                    val midY = height * 0.5f

                    wavePath1.moveTo(0f, midY)
                    wavePath2.moveTo(0f, midY)

                    val waveSteps = 60
                    for (i in 0..waveSteps) {
                        val x = (i.toFloat() / waveSteps) * width
                        val wave1 = sin((i * 0.15f) + phase) * (height * 0.22f * amp)
                        val wave2 = cos((i * 0.18f) - phase * 1.3f) * (height * 0.18f * amp)
                        wavePath1.lineTo(x, midY + wave1)
                        wavePath2.lineTo(x, midY + wave2)
                    }

                    drawPath(
                        path = wavePath1,
                        brush = Brush.horizontalGradient(
                            listOf(NeonViolet, ElectricCyan, HotMagenta)
                        ),
                        style = Stroke(width = 6f)
                    )
                    drawPath(
                        path = wavePath2,
                        brush = Brush.horizontalGradient(
                            listOf(HotMagenta, NeonVioletLight, ElectricCyan)
                        ),
                        style = Stroke(width = 3.5f)
                    )

                    // Ambient glow center circle
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(NeonViolet.copy(alpha = 0.35f * amp), Color.Transparent),
                            center = Offset(width * 0.5f, midY),
                            radius = width * 0.45f
                        ),
                        radius = width * 0.45f,
                        center = Offset(width * 0.5f, midY)
                    )
                }

                VisualizerTheme.CYAN_SPECTRUM -> {
                    // Equalizer frequency spectrum bars
                    val barCount = 28
                    val spacing = width / (barCount * 1.4f)
                    val barWidth = spacing * 0.75f
                    val startX = (width - (barCount * spacing)) / 2f
                    val baselineY = height * 0.85f

                    for (i in 0 until barCount) {
                        val harmonic = sin((i * 0.4f) + phase) * 0.5f + 0.5f
                        val centerWeight = sin((i.toFloat() / barCount) * PI.toFloat())
                        val barHeight = (height * 0.7f) * (harmonic * 0.4f + centerWeight * 0.6f) * amp
                        val x = startX + (i * spacing)

                        drawRect(
                            brush = Brush.verticalGradient(
                                listOf(ElectricCyan, NeonViolet),
                                startY = baselineY - barHeight,
                                endY = baselineY
                            ),
                            topLeft = Offset(x, baselineY - barHeight),
                            size = Size(barWidth, barHeight)
                        )

                        // Glowing cap
                        drawCircle(
                            color = Color.White,
                            radius = barWidth * 0.5f,
                            center = Offset(x + barWidth / 2f, baselineY - barHeight)
                        )
                    }
                }

                VisualizerTheme.PULSE_ORB -> {
                    // Pulsing kinetic energy orb with shockwave ripples
                    val center = Offset(width * 0.5f, height * 0.5f)
                    val baseRadius = (width.coerceAtMost(height) * 0.22f) * (1f + amp * 0.35f)

                    // Expanding ripple rings
                    for (ring in 1..4) {
                        val ringPhase = (phase + (ring * 1.2f)) % (2f * PI.toFloat())
                        val ringRadius = baseRadius + (ringPhase * 40f * amp)
                        val alpha = (1f - (ringPhase / (2f * PI.toFloat()))).coerceIn(0f, 1f) * 0.45f
                        drawCircle(
                            color = HotMagenta.copy(alpha = alpha),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = 3f)
                        )
                    }

                    // Main glowing orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White, NeonViolet, HotMagenta, Color.Transparent),
                            center = center,
                            radius = baseRadius * 1.4f
                        ),
                        radius = baseRadius,
                        center = center
                    )
                }

                VisualizerTheme.STAGE_LIGHTS -> {
                    // Dramatic stage spotlights and particle motes
                    val centerBottom = Offset(width * 0.5f, height * 0.95f)

                    // Spotlight 1 (Left rotating beam)
                    val beam1Angle = sin(phase) * 35f - 20f
                    rotate(degrees = beam1Angle, pivot = centerBottom) {
                        val beamPath = Path().apply {
                            moveTo(centerBottom.x - 20f, centerBottom.y)
                            lineTo(centerBottom.x - 140f, 0f)
                            lineTo(centerBottom.x + 140f, 0f)
                            lineTo(centerBottom.x + 20f, centerBottom.y)
                            close()
                        }
                        drawPath(
                            path = beamPath,
                            brush = Brush.verticalGradient(
                                listOf(ElectricCyan.copy(alpha = 0.4f * amp), Color.Transparent),
                                startY = 0f,
                                endY = centerBottom.y
                            )
                        )
                    }

                    // Spotlight 2 (Right rotating beam)
                    val beam2Angle = cos(phase * 0.8f) * 35f + 20f
                    rotate(degrees = beam2Angle, pivot = centerBottom) {
                        val beamPath2 = Path().apply {
                            moveTo(centerBottom.x - 20f, centerBottom.y)
                            lineTo(centerBottom.x - 130f, 0f)
                            lineTo(centerBottom.x + 130f, 0f)
                            lineTo(centerBottom.x + 20f, centerBottom.y)
                            close()
                        }
                        drawPath(
                            path = beamPath2,
                            brush = Brush.verticalGradient(
                                listOf(NeonViolet.copy(alpha = 0.45f * amp), Color.Transparent),
                                startY = 0f,
                                endY = centerBottom.y
                            )
                        )
                    }

                    // Stage dust / floating particles
                    for (p in 0..18) {
                        val px = (width * ((p * 37) % 100) / 100f)
                        val py = (height * ((p * 53) % 100) / 100f) + sin(phase + p) * 15f
                        drawCircle(
                            color = Color.White.copy(alpha = 0.6f * amp),
                            radius = 2.5f + (p % 3) * 1.5f,
                            center = Offset(px, py)
                        )
                    }
                }

                VisualizerTheme.COSMIC_VINYL -> {
                    // Spinning vinyl record
                    val center = Offset(width * 0.5f, height * 0.5f)
                    val vinylRadius = width.coerceAtMost(height) * 0.38f

                    rotate(degrees = rotationAngle, pivot = center) {
                        // Outer black vinyl body
                        drawCircle(
                            color = Color(0xFF15151A),
                            radius = vinylRadius,
                            center = center
                        )

                        // Grooves
                        for (g in 1..7) {
                            val r = vinylRadius * (0.42f + (g * 0.075f))
                            drawCircle(
                                color = Color(0xFF282833),
                                radius = r,
                                center = center,
                                style = Stroke(width = 1.2f)
                            )
                        }

                        // Center label
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(HotMagenta, NeonViolet, GoldCrown, HotMagenta),
                                center = center
                            ),
                            radius = vinylRadius * 0.35f,
                            center = center
                        )

                        // Spindle hole
                        drawCircle(
                            color = Color(0xFF090714),
                            radius = vinylRadius * 0.08f,
                            center = center
                        )
                    }

                    // Iridescent dynamic reflection sweep
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(ElectricCyan.copy(alpha = 0.25f * amp), Color.Transparent),
                            center = center,
                            radius = vinylRadius * 1.1f
                        ),
                        radius = vinylRadius,
                        center = center
                    )
                }
            }
        }
    }
}
