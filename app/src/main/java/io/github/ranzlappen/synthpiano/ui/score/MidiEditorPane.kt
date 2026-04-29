package io.github.ranzlappen.synthpiano.ui.score

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.data.midi.MidiScore
import io.github.ranzlappen.synthpiano.data.midi.MidiTiming
import io.github.ranzlappen.synthpiano.data.midi.Note
import io.github.ranzlappen.synthpiano.ui.components.GlassCard

/**
 * Replacement for the legacy step-grid EditorPane. Hosts:
 *
 *  - file row (load demo / load .mid / new / save as),
 *  - transport row (play/stop, current beat readout, tempo slider),
 *  - title field,
 *  - the [PianoRollEditor] piano-roll surface.
 *
 * Mutations to notes are pushed back to the parent through a single
 * [onScoreChange] callback so [io.github.ranzlappen.synthpiano.ui.score.AppScoreState]
 * remains the canonical owner of the score.
 */
@Composable
fun MidiEditorPane(
    midiScore: MidiScore?,
    onScoreChange: (MidiScore) -> Unit,
    tempo: Int,
    onTempoChange: (Int) -> Unit,
    isPlaying: Boolean,
    currentTick: Int,
    onTogglePlay: () -> Unit,
    status: String?,
    onLoadJson: () -> Unit,
    onLoadDemo: (String) -> Unit,
    onNew: () -> Unit,
    onSaveAs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var zoomX by rememberSaveable { mutableFloatStateOf(1f) }
    var zoomY by rememberSaveable { mutableFloatStateOf(1f) }

    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // File row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DemoMenuButton(onPick = onLoadDemo)
                OutlinedButton(onClick = onLoadJson) { Text(stringResource(R.string.score_load)) }
                OutlinedButton(onClick = onNew) { Text(stringResource(R.string.score_new)) }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onSaveAs,
                    enabled = midiScore != null,
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.score_save_as))
                }
            }

            // Transport row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledIconButton(
                    onClick = onTogglePlay,
                    enabled = midiScore != null && midiScore.notes.isNotEmpty(),
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                    )
                }
                val ppq = midiScore?.ppq ?: MidiTiming.DEFAULT_PPQ
                val beatLabel = if (currentTick >= 0)
                    String.format("%.2f", currentTick.toFloat() / ppq)
                else "—"
                Text("Beat $beatLabel", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(96.dp))
                Text("${stringResource(R.string.score_tempo)} $tempo", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = tempo.toFloat(),
                    onValueChange = { onTempoChange(it.toInt()) },
                    valueRange = 30f..240f,
                    modifier = Modifier.weight(1f),
                )
                if (selectedIndex != null) {
                    IconButton(onClick = {
                        val s = midiScore ?: return@IconButton
                        val idx = selectedIndex ?: return@IconButton
                        if (idx in s.notes.indices) {
                            val newNotes = s.notes.toMutableList().also { it.removeAt(idx) }
                            onScoreChange(s.copy(notes = newNotes))
                            selectedIndex = null
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete selected note")
                    }
                }
                IconButton(
                    onClick = {
                        zoomX = (zoomX * 1.25f).coerceAtMost(PIANO_ROLL_ZOOM_MAX_X)
                        zoomY = (zoomY * 1.25f).coerceAtMost(PIANO_ROLL_ZOOM_MAX_Y)
                    },
                    enabled = midiScore != null &&
                        (zoomX < PIANO_ROLL_ZOOM_MAX_X - 1e-3f || zoomY < PIANO_ROLL_ZOOM_MAX_Y - 1e-3f),
                ) { Icon(Icons.Filled.Add, contentDescription = "Zoom in") }
                IconButton(
                    onClick = {
                        zoomX = (zoomX / 1.25f).coerceAtLeast(PIANO_ROLL_ZOOM_MIN_X)
                        zoomY = (zoomY / 1.25f).coerceAtLeast(PIANO_ROLL_ZOOM_MIN_Y)
                    },
                    enabled = midiScore != null &&
                        (zoomX > PIANO_ROLL_ZOOM_MIN_X + 1e-3f || zoomY > PIANO_ROLL_ZOOM_MIN_Y + 1e-3f),
                ) { Icon(Icons.Filled.Remove, contentDescription = "Zoom out") }
                IconButton(
                    onClick = { zoomX = 1f; zoomY = 1f },
                    enabled = midiScore != null && (zoomX != 1f || zoomY != 1f),
                ) { Icon(Icons.Filled.Refresh, contentDescription = "Reset zoom") }
            }

            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val s = midiScore
            if (s == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.score_no_score_loaded),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                OutlinedTextField(
                    value = s.title.orEmpty(),
                    onValueChange = { onScoreChange(s.copy(title = it.ifBlank { null })) },
                    label = { Text(stringResource(R.string.score_title)) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                )
                PianoRollEditor(
                    score = s,
                    modifier = Modifier.fillMaxSize(),
                    currentTick = currentTick,
                    selectedIndex = selectedIndex,
                    onSelectNote = { selectedIndex = it },
                    onAddNote = { midi, startTicks ->
                        val newNote = Note(
                            channel = 0,
                            midi = midi,
                            velocity = 100,
                            startTicks = startTicks,
                            durationTicks = s.ppq, // default 1 beat
                        )
                        val newNotes = (s.notes + newNote).toMutableList()
                        onScoreChange(s.copy(notes = newNotes))
                        selectedIndex = newNotes.size - 1
                    },
                    onUpdateNote = { idx, newNote ->
                        if (idx in s.notes.indices) {
                            val newNotes = s.notes.toMutableList().also { it[idx] = newNote }
                            onScoreChange(s.copy(notes = newNotes))
                        }
                    },
                    onDeleteNote = { idx ->
                        if (idx in s.notes.indices) {
                            val newNotes = s.notes.toMutableList().also { it.removeAt(idx) }
                            onScoreChange(s.copy(notes = newNotes))
                            if (selectedIndex == idx) selectedIndex = null
                        }
                    },
                    zoomX = zoomX,
                    zoomY = zoomY,
                    onZoomChange = { x, y -> zoomX = x; zoomY = y },
                )
            }
        }
    }
}
