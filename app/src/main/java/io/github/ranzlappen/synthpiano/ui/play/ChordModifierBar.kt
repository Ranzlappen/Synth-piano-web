package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
 * Each row also exposes the [inversions] pills appended after the
 * qualities. They follow the same momentary/sticky convention. Only one
 * inversion can be active per row; tapping the active one clears it.
 *
 * Pills are laid out in an adaptive grid: the column count is computed
 * from the available width so pills always **fill the row width** with
 * `weight(1f)`. Rows wrap only when the available width can't fit all
 * pills at the minimum target width — eliminating the wide blank band
 * that the old `FlowRow + fixed 56dp pill` approach left when the strip
 * was wide-and-short.
 */
@Composable
fun ChordModifierRow(
    label: String,
    qualities: List<ChordQuality>,
    selected: Set<ChordQuality>,
    inversion: ChordInversion,
    inversions: List<ChordInversion> = listOf(
        ChordInversion.FIRST,
        ChordInversion.SECOND,
        ChordInversion.THIRD,
    ),
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
        AdaptivePillGrid(
            qualities = qualities,
            inversions = inversions,
            selected = selected,
            inversion = inversion,
            momentary = momentary,
            onToggle = onToggle,
            onPress = onPress,
            onRelease = onRelease,
            onInversionToggle = onInversionToggle,
            onInversionPress = onInversionPress,
            onInversionRelease = onInversionRelease,
            modifier = Modifier.weight(1f),
        )
    }
}

private sealed interface PillData {
    data class Quality(val q: ChordQuality) : PillData
    data class Inversion(val inv: ChordInversion) : PillData
}

@Composable
private fun AdaptivePillGrid(
    qualities: List<ChordQuality>,
    inversions: List<ChordInversion>,
    selected: Set<ChordQuality>,
    inversion: ChordInversion,
    momentary: Boolean,
    onToggle: ((ChordQuality) -> Unit)?,
    onPress: ((ChordQuality) -> Unit)?,
    onRelease: ((ChordQuality) -> Unit)?,
    onInversionToggle: ((ChordInversion) -> Unit)?,
    onInversionPress: ((ChordInversion) -> Unit)?,
    onInversionRelease: ((ChordInversion) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val items: List<PillData> =
        qualities.map { PillData.Quality(it) } + inversions.map { PillData.Inversion(it) }
    if (items.isEmpty()) return

    BoxWithConstraints(modifier = modifier) {
        val gap = 6.dp
        val targetMinWidth = 56.dp
        // (W + g) / (target + g) ≈ how many target-width pills fit, accounting
        // for inter-pill gaps. Coerce against actual item count so we don't
        // create empty trailing columns when the strip is very wide.
        val columns = ((maxWidth + gap) / (targetMinWidth + gap))
            .toInt()
            .coerceIn(1, items.size)

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    rowItems.forEach { item ->
                        val pillModifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                        when (item) {
                            is PillData.Quality -> ChordPillButton(
                                text = item.q.label(),
                                selected = item.q in selected,
                                momentary = momentary,
                                modifier = pillModifier,
                                onToggle = { onToggle?.invoke(item.q) },
                                onPress = { onPress?.invoke(item.q) },
                                onRelease = { onRelease?.invoke(item.q) },
                            )
                            is PillData.Inversion -> ChordPillButton(
                                text = item.inv.label(),
                                selected = inversion == item.inv,
                                momentary = momentary,
                                modifier = pillModifier,
                                onToggle = { onInversionToggle?.invoke(item.inv) },
                                onPress = { onInversionPress?.invoke(item.inv) },
                                onRelease = { onInversionRelease?.invoke(item.inv) },
                            )
                        }
                    }
                    // Keep proportional widths on a partial trailing row.
                    repeat(columns - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
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
    modifier: Modifier = Modifier,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
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
