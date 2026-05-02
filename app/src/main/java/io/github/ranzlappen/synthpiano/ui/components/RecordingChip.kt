package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R

/**
 * REC pill: tap to start/stop recording; while recording, a soft pulsing
 * red dot + mm:ss timer; when stopped and a recording exists, an inline
 * Share icon appears.
 *
 * State is hoisted to the caller (typically a session-level holder in the
 * root scaffold) so the chip remains visually consistent across tabs.
 */
@Composable
fun RecordingChip(
    isRecording: Boolean,
    elapsedMs: Long,
    onToggle: () -> Unit,
    onShareLast: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val pulseAlpha by rememberInfiniteTransition(label = "recPulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recPulseAlpha",
    )

    val container = if (isRecording) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
    val onContainer = if (isRecording) MaterialTheme.colorScheme.onErrorContainer
                      else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5A5A))
                        .graphicsLayer { alpha = pulseAlpha },
                )
                Spacer(Modifier.width(6.dp))
                Text(formatElapsed(elapsedMs), style = MaterialTheme.typography.labelMedium, color = onContainer)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.record_stop),
                    tint = onContainer,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.FiberManualRecord,
                    contentDescription = stringResource(R.string.record_start),
                    tint = Color(0xFFFF5A5A),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.recording_now),
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer,
                )
            }
        }
        if (!isRecording && onShareLast != null) {
            Box(
                modifier = Modifier
                    .clickable(onClick = onShareLast)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.IosShare,
                    contentDescription = stringResource(R.string.record_share),
                    tint = onContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
