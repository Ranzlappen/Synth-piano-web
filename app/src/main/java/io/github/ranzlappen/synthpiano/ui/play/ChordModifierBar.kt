package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.data.ChordInversion
import io.github.ranzlappen.synthpiano.data.ChordQuality

/**
 * One row of chord-modifier buttons. Two flavours, controlled by
 * [momentary]:
 *
 *   * **Sticky (LOCK)** — tap to toggle a quality on/off. Selection
 *     persists. [onToggle] is invoked with the tapped quality.
 *   * **Momentary (SHIFT)** — quality is active only while the button is
 *     pressed. [onPress] fires on finger-down, [onRelease] on lift /
 *     gesture cancellation.
 *
 * Each row also exposes 1/2/3 inversion pills appended after a small
 * gap. They follow the same momentary/sticky convention. Only one
 * inversion can be active per row; tapping the active one clears it.
 *
 * Buttons are rendered in a [FlowRow] so they wrap onto additional lines
 * when the row is narrower than the natural pill total — every button
 * stays visible regardless of container width.
 */
@Composable
fun ChordModifierRow(
    label: String,
    qualities: List<ChordQuality>,
    selected: Set<ChordQuality>,
    inversion: ChordInversion,
    momentary: Boolean = false,
    onToggle: ((ChordQuality) -> Unit)? = null,
    onPress: ((ChordQuality) -> Unit)? = null,
    onRelease: ((ChordQuality) -> Unit)? = null,
    onInversionToggle: ((ChordInversion) -> Unit)? = null,
    onInversionPress: ((ChordInversion) -> Unit)? = null,
    onInversionRelease: ((ChordInversion) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            qualities.forEach { q ->
                ChordPillButton(
                    text = q.label(),
                    selected = q in selected,
                    momentary = momentary,
                    onToggle = { onToggle?.invoke(q) },
                    onPress = { onPress?.invoke(q) },
                    onRelease = { onRelease?.invoke(q) },
                )
            }
            // Visual divider between qualities and inversions; flows like a pill.
            Box(modifier = Modifier.size(width = 8.dp, height = 1.dp))
            listOf(ChordInversion.FIRST, ChordInversion.SECOND, ChordInversion.THIRD).forEach { inv ->
                ChordPillButton(
                    text = inv.label(),
                    selected = inversion == inv,
                    momentary = momentary,
                    onToggle = { onInversionToggle?.invoke(inv) },
                    onPress = { onInversionPress?.invoke(inv) },
                    onRelease = { onInversionRelease?.invoke(inv) },
                )
            }
        }
    }
}

@Composable
private fun ChordPillButton(
    text: String,
    selected: Boolean,
    momentary: Boolean,
    onToggle: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .size(width = 56.dp, height = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .pointerInput(momentary) {
                if (momentary) {
                    detectTapGestures(
                        onPress = {
                            onPress()
                            try {
                                tryAwaitRelease()
                            } finally {
                                onRelease()
                            }
                        },
                    )
                } else {
                    detectTapGestures(onTap = { onToggle() })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
        )
    }
}
