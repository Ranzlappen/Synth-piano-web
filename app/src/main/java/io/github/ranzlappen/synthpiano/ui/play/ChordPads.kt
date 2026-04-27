package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
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
            .width(36.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
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
        VerticalLabel(
            text = pad.label(),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Renders [text] rotated -90° so it reads bottom-to-top inside a tall, narrow
 * pad. The custom [layout] block swaps the measured width and height so the
 * rotated text reserves the correct bounding box (Compose otherwise measures
 * the text horizontally and clips). The [rotate] modifier handles the
 * pixel-level transform.
 */
@Composable
private fun VerticalLabel(
    text: String,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = color,
        maxLines = 1,
        modifier = Modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints())
                layout(placeable.height, placeable.width) {
                    placeable.place(
                        x = -(placeable.width / 2 - placeable.height / 2),
                        y = (placeable.width / 2 - placeable.height / 2),
                    )
                }
            }
            .rotate(-90f),
    )
}

@Composable
private fun ChordEditSheet(
    initial: ChordPad,
    onSave: (ChordPad) -> Unit,
    onCancel: () -> Unit,
) {
    var root by remember { mutableStateOf(initial.rootNote) }
    var quality by remember { mutableStateOf(initial.quality) }
    var octaveOffset by remember { mutableStateOf(initial.octaveOffset) }

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

        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.chord_octave_offset), style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(-1, 0, 1).forEach { off ->
                FilterChip(
                    selected = octaveOffset == off,
                    onClick = { octaveOffset = off },
                    label = { Text(if (off > 0) "+$off" else "$off") },
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
                onClick = {
                    onSave(ChordPad(rootNote = root, quality = quality, octaveOffset = octaveOffset))
                },
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

/**
 * Resolves the absolute MIDI notes a chord pad should play, anchored to the
 * keyboard's leftmost-visible C. The pad's stored `rootNote` contributes only
 * its pitch class; `octaveOffset` shifts the chord up or down from the anchor.
 *
 * @param baseC MIDI note of the leftmost C the keyboard is currently showing
 * (e.g. 48 = C3).
 */
fun chordNotes(pad: ChordPad, baseC: Int): List<Int> {
    val pitchClass = ((pad.rootNote % 12) + 12) % 12
    val rootMidi = baseC + pad.octaveOffset * 12 + pitchClass
    return intervalsFor(pad.quality).map { rootMidi + it }
}
