package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The PERFORM tab: the workstation's primary play surface.
 *
 * Layout (top → bottom):
 *  1. Slim toolbar: Scale picker + chord-pad hint.
 *  2. Chord pad strip with vertical-text pads, anchored to the keyboard's
 *     leftmost-visible C (so chords play "what you can see").
 *  3. Full-piano A0–C8 keyboard, horizontally scrollable, taking the rest
 *     of the viewport.
 */
@Composable
fun PerformTab(
    synth: SynthController,
    prefs: PreferencesRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val heldBySource by synth.heldBySource.collectAsState()
    val padsJson by prefs.chordPadsJson.collectAsState(initial = null)
    val scaleName by prefs.scaleKey.collectAsState(initial = "NONE")
    val scale = remember(scaleName) { Scale.fromName(scaleName) }
    val savedLeftC by prefs.keyboardLeftC.collectAsState(initial = 48)

    val pads = remember(padsJson) {
        padsJson?.let { runCatching { parseChordPadsJson(it) }.getOrNull() }
            ?: defaultChordPads()
    }

    val scrollState = rememberScrollState()
    val whiteKeyPx = with(density) { PIANO_WHITE_KEY_DP.toPx() }

    // Restore persisted scroll position once whiteKeyPx is known. Subsequent
    // user scrolls take precedence.
    LaunchedEffect(whiteKeyPx) {
        val target = scrollPxForC(savedLeftC, whiteKeyPx)
        if (scrollState.value != target) scrollState.scrollTo(target)
    }

    // Persist scroll changes (skip the initial value from the snapshotFlow).
    LaunchedEffect(whiteKeyPx) {
        androidx.compose.runtime.snapshotFlow { scrollState.value }
            .drop(1)
            .distinctUntilChanged()
            .collect { px ->
                val leftC = leftmostVisibleC(px, whiteKeyPx)
                prefs.setKeyboardLeftC(leftC)
            }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
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

        GlassCard(modifier = Modifier.fillMaxWidth().height(92.dp)) {
            ChordPadStrip(
                pads = pads,
                onPadDown = { pad ->
                    val baseC = leftmostVisibleC(scrollState.value, whiteKeyPx)
                    chordNotes(pad, baseC).forEach { midi ->
                        val safe = midi.coerceIn(0, 127)
                        synth.noteOn(safe, source = NoteSource.TOUCH)
                    }
                },
                onPadUp = { pad ->
                    val baseC = leftmostVisibleC(scrollState.value, whiteKeyPx)
                    chordNotes(pad, baseC).forEach { midi ->
                        val safe = midi.coerceIn(0, 127)
                        synth.noteOff(safe)
                    }
                },
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
                .heightIn(min = 160.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            PianoKeyboard(
                modifier = Modifier.fillMaxSize(),
                scrollState = scrollState,
                heldBySource = heldBySource,
                scale = scale,
                onNoteOn = { synth.noteOn(it, source = NoteSource.TOUCH) },
                onNoteOff = { synth.noteOff(it) },
            )
        }
    }
}
