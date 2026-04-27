package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.NoteSource
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.Scale
import io.github.ranzlappen.synthpiano.data.defaultChordPads
import io.github.ranzlappen.synthpiano.data.parseChordPadsJson
import io.github.ranzlappen.synthpiano.data.toJson
import io.github.ranzlappen.synthpiano.ui.components.GlassCard
import io.github.ranzlappen.synthpiano.ui.components.ScalePicker
import kotlinx.coroutines.launch

/**
 * The PERFORM tab: the workstation's primary play surface. Octave stepper
 * and scale picker on top, scrollable chord pad strip below them, and a
 * full-width multi-touch piano filling the rest of the viewport.
 */
@Composable
fun PerformTab(
    synth: SynthController,
    prefs: PreferencesRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    val heldBySource by synth.heldBySource.collectAsState()
    val padsJson by prefs.chordPadsJson.collectAsState(initial = null)
    val octave by prefs.octave.collectAsState(initial = 0)
    val scaleName by prefs.scaleKey.collectAsState(initial = "NONE")
    val scale = remember(scaleName) { Scale.fromName(scaleName) }

    val pads = remember(padsJson) {
        padsJson?.let { runCatching { parseChordPadsJson(it) }.getOrNull() }
            ?: defaultChordPads()
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OctaveStepper(
                    octave = octave,
                    onChange = { v -> scope.launch { prefs.setOctave(v) } },
                )
                ScalePicker(
                    selected = scale,
                    onSelect = { s -> scope.launch { prefs.setScaleKey(s.name) } },
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.chord_pad_long_press_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        GlassCard(modifier = Modifier.fillMaxWidth().height(80.dp)) {
            ChordPadStrip(
                pads = pads,
                onPadDown = { pad -> chordNotes(pad).forEach { synth.noteOn(it + octave * 12, source = NoteSource.TOUCH) } },
                onPadUp = { pad -> chordNotes(pad).forEach { synth.noteOff(it + octave * 12) } },
                onAssign = { i, newPad ->
                    val updated = pads.toMutableList().apply { this[i] = newPad }
                    scope.launch { prefs.setChordPadsJson(updated.toJson()) }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Piano takes the remaining space. heightIn floor prevents collapse
        // on extreme small landscape phones.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 140.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            PianoKeyboard(
                modifier = Modifier.fillMaxSize(),
                firstMidiNote = 48 + octave * 12,
                whiteKeyCount = 14,
                heldBySource = heldBySource,
                scale = scale,
                onNoteOn = { synth.noteOn(it, source = NoteSource.TOUCH) },
                onNoteOff = { synth.noteOff(it) },
            )
        }
    }
}

@Composable
private fun OctaveStepper(
    octave: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 4.dp),
    ) {
        IconButton(
            onClick = { onChange((octave - 1).coerceAtLeast(-3)) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.octave_down))
        }
        Text(
            "${stringResource(R.string.octave)}  $octave",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(78.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(
            onClick = { onChange((octave + 1).coerceAtMost(3)) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.octave_up))
        }
    }
}
