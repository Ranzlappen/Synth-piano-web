package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.data.ChordPad
import io.github.ranzlappen.synthpiano.data.ChordQuality
import io.github.ranzlappen.synthpiano.data.intervalsFor

/**
 * Horizontally scrollable strip of chord pads. Tap a pad to trigger every
 * note in the chord; long-press to open the inline editor (root × quality)
 * as a bottom sheet.
 *
 * Pads scroll instead of squeezing into the available width so the layout
 * is comfortable on narrow phones in landscape and on wide tablets alike.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordPadStrip(
    pads: List<ChordPad>,
    onPadDown: (ChordPad) -> Unit,
    onPadUp: (ChordPad) -> Unit,
    onAssign: (Int, ChordPad) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(pads) { index, pad ->
            PadButton(
                pad = pad,
                onDown = { onPadDown(pad) },
                onUp = { onPadUp(pad) },
                onLongPress = { editing = index },
            )
        }
    }

    val current = editing
    if (current != null) {
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheetState,
        ) {
            ChordEditSheet(
                initial = pads[current],
                onSave = { newPad ->
                    onAssign(current, newPad)
                    editing = null
                },
                onCancel = { editing = null },
            )
        }
    }
}

@Composable
private fun PadButton(
    pad: ChordPad,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onLongPress: () -> Unit,
) {
    val color = MaterialTheme.colorScheme.primaryContainer
    Box(
        modifier = Modifier
            .width(96.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color)
            .pointerInput(pad) {
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onPress = {
                        onDown()
                        try {
                            tryAwaitRelease()
                        } finally {
                            onUp()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = pad.label(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ChordEditSheet(
    initial: ChordPad,
    onSave: (ChordPad) -> Unit,
    onCancel: () -> Unit,
) {
    var root by remember { mutableStateOf(initial.rootNote) }
    var quality by remember { mutableStateOf(initial.quality) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.chord_assign_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.chord_root), style = MaterialTheme.typography.labelMedium)

        val roots = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(roots) { i, name ->
                FilterChip(
                    selected = (root % 12) == i,
                    onClick = { root = 60 + i },
                    label = { Text(name) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.chord_quality), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ChordQuality.values().forEach { q ->
                FilterChip(
                    selected = q == quality,
                    onClick = { quality = q },
                    label = { Text(q.label()) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel))
            }
            androidx.compose.material3.Button(
                onClick = { onSave(ChordPad(rootNote = root, quality = quality)) },
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

/** Triggers all notes in a chord through the controller. */
fun chordNotes(pad: ChordPad): List<Int> {
    val intervals = intervalsFor(pad.quality)
    return intervals.map { pad.rootNote + it }
}
