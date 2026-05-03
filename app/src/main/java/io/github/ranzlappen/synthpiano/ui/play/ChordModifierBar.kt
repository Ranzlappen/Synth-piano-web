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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ranzlappen.synthpiano.data.ChordInversion
import io.github.ranzlappen.synthpiano.data.ChordQuality

/**
 * Visual category of a modifier row. Replaces the old "LOCK"/"SHIFT" text
 * labels with a colour the row's pills inherit, so the affordance is
 * communicated by hue rather than a redundant English word that needed
 * translating.
 *
 *   * [Sticky]    — tap-to-toggle (was "LOCK")
 *   * [Momentary] — active only while held (was "SHIFT")
 */
enum class PillVariant { Sticky, Momentary }

/**
 * One row of chord-modifier buttons. Two flavours, controlled by [variant]:
 *
 *   * [PillVariant.Sticky]    — tap to toggle a quality on/off. Selection
 *     persists. [onToggle] is invoked with the tapped quality.
 *   * [PillVariant.Momentary] — quality is active only while the button is
 *     pressed. [onPress] fires on finger-down, [onRelease] on lift /
 *     gesture cancellation.
 *
 * Each row also exposes the [inversions] pills appended after the
 * qualities. They follow the same momentary/sticky convention. Only one
 * inversion can be active per row; tapping the active one clears it.
 *
 * Pills are laid out in an adaptive grid: the column count is computed
 * from the available width so pills always fill the row width with
 * `weight(1f)`, and pill height is computed from the row's allotted
 * vertical space ÷ chunk count. Caller controls allotted height via
 * the passed [modifier] (typically `Modifier.weight(1f)` inside a
 * `Column`), so the row never needs an inner scroll.
 */
@Composable
fun ChordModifierRow(
    variant: PillVariant,
    qualities: List<ChordQuality>,
    selected: Set<ChordQuality>,
    inversion: ChordInversion,
    inversions: List<ChordInversion> = listOf(
        ChordInversion.FIRST,
        ChordInversion.SECOND,
        ChordInversion.THIRD,
    ),
    onToggle: ((ChordQuality) -> Unit)? = null,
    onPress: ((ChordQuality) -> Unit)? = null,
    onRelease: ((ChordQuality) -> Unit)? = null,
    onInversionToggle: ((ChordInversion) -> Unit)? = null,
    onInversionPress: ((ChordInversion) -> Unit)? = null,
    onInversionRelease: ((ChordInversion) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AdaptivePillGrid(
        variant = variant,
        qualities = qualities,
        inversions = inversions,
        selected = selected,
        inversion = inversion,
        onToggle = onToggle,
        onPress = onPress,
        onRelease = onRelease,
        onInversionToggle = onInversionToggle,
        onInversionPress = onInversionPress,
        onInversionRelease = onInversionRelease,
        modifier = modifier.fillMaxWidth(),
    )
}

private sealed interface PillData {
    data class Quality(val q: ChordQuality) : PillData
    data class Inversion(val inv: ChordInversion) : PillData
}

@Composable
private fun AdaptivePillGrid(
    variant: PillVariant,
    qualities: List<ChordQuality>,
    inversions: List<ChordInversion>,
    selected: Set<ChordQuality>,
    inversion: ChordInversion,
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
        val gap = 4.dp
        val targetMinWidth = 48.dp
        val columns = ((maxWidth + gap) / (targetMinWidth + gap))
            .toInt()
            .coerceIn(1, items.size)
        val chunks = (items.size + columns - 1) / columns
        // Compute pill height from the row's actual allotted space so each
        // row scales to the container instead of forcing an outer scroll.
        // 28 dp floor keeps pills tappable; 64 dp ceiling avoids absurdly
        // tall pills when only a single chunk renders into a tall container.
        val pillHeight = ((maxHeight - gap * (chunks - 1).coerceAtLeast(0)) / chunks)
            .coerceIn(28.dp, 64.dp)

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
                            .height(pillHeight)
                        when (item) {
                            is PillData.Quality -> ChordPillButton(
                                text = item.q.label(),
                                selected = item.q in selected,
                                variant = variant,
                                modifier = pillModifier,
                                onToggle = { onToggle?.invoke(item.q) },
                                onPress = { onPress?.invoke(item.q) },
                                onRelease = { onRelease?.invoke(item.q) },
                            )
                            is PillData.Inversion -> ChordPillButton(
                                text = item.inv.label(),
                                selected = inversion == item.inv,
                                variant = variant,
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

/** M3 colour pair for a [PillVariant], picked once per call so the active
 *  and inactive states stay theme-coherent. */
@Composable
internal fun pillColors(variant: PillVariant, selected: Boolean): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    return when (variant) {
        PillVariant.Sticky ->
            if (selected) cs.tertiaryContainer to cs.onTertiaryContainer
            else cs.surfaceVariant.copy(alpha = 0.55f) to cs.onSurfaceVariant
        PillVariant.Momentary ->
            if (selected) cs.secondaryContainer to cs.onSecondaryContainer
            else cs.surfaceVariant.copy(alpha = 0.55f) to cs.onSurfaceVariant
    }
}

@Composable
private fun ChordPillButton(
    text: String,
    selected: Boolean,
    variant: PillVariant,
    onToggle: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = pillColors(variant, selected)
    val momentary = variant == PillVariant.Momentary

    BoxWithConstraints(
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
        // Auto-scale the label so long glyphs (sus2/sus4/aug, inversion arrows)
        // shrink instead of clipping when the pill is tight. Floor at 9.sp so
        // text stays legible; ceil at 14.sp so it never feels oversized.
        val target = (maxHeight.value * 0.42f).coerceIn(9f, 14f).sp
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = target),
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}

/** Two-dot legend explaining the colour code, intended for the top of the
 *  modifier strip so users don't have to memorise the variant mapping. */
@Composable
fun PillVariantLegend(
    stickyLabel: String,
    momentaryLabel: String,
    fontSize: TextUnit = 10.sp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LegendDot(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            label = stickyLabel,
            fontSize = fontSize,
        )
        LegendDot(
            color = MaterialTheme.colorScheme.secondaryContainer,
            label = momentaryLabel,
            fontSize = fontSize,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String, fontSize: TextUnit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
