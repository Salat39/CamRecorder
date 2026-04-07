package com.salat.preview.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.salat.preview.presentation.entity.RecordingIndicatorState

@Composable
fun RecordingIndicator(
    state: RecordingIndicatorState,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    showLabel: Boolean = true,
    glowIntensity: Float = 1f,
    pulseScaleIntensity: Float = 1f,
    pulseAlphaIntensity: Float = 0.4f,
    centerHighlightIntensity: Float = 1f
) {
    val safeGlowIntensity = glowIntensity.coerceIn(0f, 1f)
    val safePulseScaleIntensity = pulseScaleIntensity.coerceIn(0f, 1f)
    val safePulseAlphaIntensity = pulseAlphaIntensity.coerceIn(0f, 1f)
    val safeCenterHighlightIntensity = centerHighlightIntensity.coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "recording_indicator_transition")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f - (0.08f * safePulseScaleIntensity),
        targetValue = 1f + (0.08f * safePulseScaleIntensity),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == RecordingIndicatorState.REC) 900 else 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recording_indicator_scale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f - (0.55f * safePulseAlphaIntensity),
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == RecordingIndicatorState.REC) 900 else 1400,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recording_indicator_alpha"
    )

    val baseColor = state.indicatorAccentColor()
    val glowEnabled = state != RecordingIndicatorState.DISABLED && safeGlowIntensity > 0f
    val centerHighlightColor = lerp(
        start = baseColor,
        stop = Color.White.copy(alpha = 0.22f),
        fraction = safeCenterHighlightIntensity
    )

    val animatedScale = when (state) {
        RecordingIndicatorState.REC,
        RecordingIndicatorState.WARNING -> {
            if (safePulseScaleIntensity == 0f) 1f else pulseScale
        }
        RecordingIndicatorState.DISABLED,
        RecordingIndicatorState.READY -> 1f
    }

    val glowAlpha = when (state) {
        RecordingIndicatorState.REC -> 0.30f * pulseAlpha * safeGlowIntensity
        RecordingIndicatorState.WARNING -> 0.22f * pulseAlpha * safeGlowIntensity
        RecordingIndicatorState.READY -> 0.16f * safeGlowIntensity
        RecordingIndicatorState.DISABLED -> 0f
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(size * 1.8f)
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
                .drawBehind {
                    if (glowEnabled) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    baseColor.copy(alpha = glowAlpha),
                                    baseColor.copy(alpha = glowAlpha * 0.35f),
                                    Color.Transparent
                                )
                            ),
                            radius = this.size.minDimension / 2f
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .graphicsLayer {
                        alpha = when (state) {
                            RecordingIndicatorState.REC -> pulseAlpha
                            else -> 1f
                        }
                    }
                    .drawBehind {
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.18f),
                            radius = size.toPx() / 2f
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    centerHighlightColor,
                                    baseColor,
                                    baseColor.copy(alpha = 0.92f)
                                )
                            ),
                            radius = size.toPx() / 2f
                        )
                    }
            )
        }

        if (showLabel) {
            Text(
                text = state.label(),
                style = MaterialTheme.typography.labelLarge,
                color = baseColor
            )
        }
    }
}

internal fun RecordingIndicatorState.indicatorAccentColor(): Color = when (this) {
    RecordingIndicatorState.REC -> Color(0xFFE53935)
    RecordingIndicatorState.DISABLED -> Color(0xFF7A7A7A)
    RecordingIndicatorState.WARNING -> Color(0xFFFBC02D)
    RecordingIndicatorState.READY -> Color(0xFF43A047)
}

private fun RecordingIndicatorState.label() = when (this) {
    RecordingIndicatorState.REC -> "REC"
    RecordingIndicatorState.DISABLED -> "DISABLED"
    RecordingIndicatorState.WARNING -> "WARNING"
    RecordingIndicatorState.READY -> "READY"
}
