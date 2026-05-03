package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ranzlappen.synthpiano.data.ChordInversion
import io.github.ranzlappen.synthpiano.data.ChordQuality

/**
 * Visual category of a modifier row.
 *
 *   * [Sticky]    — tap-to-toggle. Pills' labels are underlined as a
 *     subtle "this stays on" hint; otherwise the pills look identical
 *     to momentary pills so the strip stays visually quiet.
 *   * [Momentary] — active only while held; no underline.
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
 * Pills are laid out in an adaptive grid: column count comes from the
 * available width and pill height comes from the row's allotted height ÷
 * chunk count. Caller controls allotted height via the passed [modifier]
 * (typically `Modifier.weight(1f)` inside a `Column`), so the row never
 * needs an inner scroll.
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
    variant: PillVariant,
    onToggle: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val bg = if (selected) cs.primaryContainer else cs.surfaceVariant.copy(alpha = 0.55f)
    val fg = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant
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
        val target = (maxHeight.value * 0.42f).coerceIn(9f, 14f).sp
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = target,
                // Sticky pills carry an underline; momentary pills don't.
                // Subtle visual distinction without a per-row text label.
                textDecoration = if (variant == PillVariant.Sticky) TextDecoration.Underline
                                 else TextDecoration.None,
            ),
            color = fg,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}
