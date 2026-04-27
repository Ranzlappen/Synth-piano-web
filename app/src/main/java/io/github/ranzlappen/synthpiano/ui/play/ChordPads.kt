package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.data.ChordPad
import io.github.ranzlappen.synthpiano.data.ChordQuality
import io.github.ranzlappen.synthpiano.data.intervalsFor

/**
 * 11 chord pads. Tap to trigger; long-press to assign root + quality.
 * Defaults map roughly to the Python source's "chord row".
 */
@Composable
fun ChordPadsRow(
    pads: List<ChordPad>,
    onPadDown: (ChordPad) -> Unit,
    onPadUp: (ChordPad) -> Unit,
    onAssign: (Int, ChordPad) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<Int?>(null) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        pads.forEachIndexed { index, pad ->
            PadButton(
                pad = pad,
                onDown = { onPadDown(pad) },
                onUp = { onPadUp(pad) },
                onLongPress = { editing = index },
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }
    }

    editing?.let { idx ->
        ChordEditDialog(
            initial = pads[idx],
            onDismiss = { editing = null },
            onConfirm = { newPad ->
                onAssign(idx, newPad)
                editing = null
            },
        )
    }
}

@Composable
private fun PadButton(
    pad: ChordPad,
    onDown: () -> Unit,
    onUp: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.secondaryContainer
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .pointerInput(pad) {
                // Long-press wins over tap; press triggers chord on down,
                // releases on up.
                detectTapGestures(
                    onLongPress = { onLongPress() },
                    onPress = {
                        onDown()
                        try {
                            val released = tryAwaitRelease()
                            if (released) onUp() else onUp()
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
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun ChordEditDialog(
    initial: ChordPad,
    onDismiss: () -> Unit,
    onConfirm: (ChordPad) -> Unit,
) {
    var root by remember { mutableStateOf(initial.rootNote) }
    var quality by remember { mutableStateOf(initial.quality) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign chord") },
        text = {
            androidx.compose.foundation.layout.Column {
                Text("Root", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                val roots = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    roots.forEachIndexed { i, name ->
                        FilterChip(
                            selected = (root % 12) == i,
                            onClick = { root = 60 + i },
                            label = { Text(name) },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Quality", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ChordQuality.values().forEach { q ->
                        FilterChip(
                            selected = q == quality,
                            onClick = { quality = q },
                            label = { Text(q.label()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(ChordPad(rootNote = root, quality = quality))
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Triggers all notes in a chord through the controller. */
fun chordNotes(pad: ChordPad): List<Int> {
    val intervals = intervalsFor(pad.quality)
    return intervals.map { pad.rootNote + it }
}
