package io.github.ranzlappen.synthpiano.ui.dj

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.ui.components.GlassCard

/**
 * One turntable deck: track info, jog wheel, pitch + volume faders, transport
 * (play/pause + cue), and a draggable progress/seek bar. Stateless — all state
 * lives in [state] (a [DeckState]) and mutations route through the callbacks,
 * which [DjScreen] wires to [DjEngine].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Deck(
    state: DeckState,
    onPlayPause: () -> Unit,
    onCueJump: () -> Unit,
    onCueSet: () -> Unit,
    onScratch: (deltaMs: Int) -> Unit,
    onSeek: (ms: Int) -> Unit,
    onPitch: (Float) -> Unit,
    onVolume: (Float) -> Unit,
    onLoad: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackInfoBar(
                    title = state.title,
                    durationMs = state.durationMs,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onLoad) {
                    Icon(
                        Icons.Filled.FolderOpen,
                        contentDescription = stringResource(R.string.dj_load),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VerticalFader(
                    value = state.pitchPercent,
                    valueRange = -DjEngine.PITCH_RANGE..DjEngine.PITCH_RANGE,
                    onValueChange = onPitch,
                    label = stringResource(R.string.dj_pitch),
                    valueText = formatPitch(state.pitchPercent),
                    modifier = Modifier.fillMaxHeight(),
                    detentAt = 0f,
                    accent = pitchColor(state.pitchPercent),
                )

                Column(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    JogWheel(
                        positionMs = state.positionMs,
                        isPlaying = state.isPlaying,
                        onScratch = onScratch,
                        modifier = Modifier.weight(1f).padding(2.dp),
                    )

                    val duration = state.durationMs.coerceAtLeast(0)
                    Slider(
                        value = state.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                        onValueChange = { onSeek(it.toInt()) },
                        valueRange = 0f..(duration.takeIf { it > 0 }?.toFloat() ?: 1f),
                        enabled = state.isPrepared && duration > 0,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.18f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            formatTime(state.positionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "-" + formatTime((duration - state.positionMs).coerceAtLeast(0)),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CueButton(onJump = onCueJump, onSet = onCueSet)
                        FilledIconButton(
                            onClick = onPlayPause,
                            enabled = state.isPrepared,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (state.isPlaying) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                },
                            ),
                            modifier = Modifier.size(56.dp),
                        ) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = stringResource(
                                    if (state.isPlaying) R.string.dj_pause else R.string.dj_play,
                                ),
                            )
                        }
                    }
                }

                VerticalFader(
                    value = state.volume,
                    valueRange = 0f..1f,
                    onValueChange = onVolume,
                    label = stringResource(R.string.dj_volume),
                    valueText = "${(state.volume * 100).toInt()}%",
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }
}

/** Cue control: tap to jump to the cue point, long-press to set it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CueButton(onJump: () -> Unit, onSet: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f))
            .combinedClickable(onClick = onJump, onLongClick = onSet),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.dj_cue),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatPitch(percent: Float): String {
    val sign = if (percent >= 0f) "+" else ""
    return "$sign%.1f%%".format(percent)
}

/** Highlight the pitch fader when it deviates from the 0% center detent. */
@Composable
private fun pitchColor(percent: Float): Color =
    if (kotlin.math.abs(percent) < 0.1f) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.tertiary
    }
