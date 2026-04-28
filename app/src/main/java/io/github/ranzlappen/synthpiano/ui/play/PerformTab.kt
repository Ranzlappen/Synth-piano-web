package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.NoteSource
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.data.ChordQuality
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.buildChordIntervals
import io.github.ranzlappen.synthpiano.ui.components.GlassCard
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The PERFORM tab: the workstation's primary play surface.
 *
 * Layout (top → bottom):
 *  1. Two rows of chord-modifier buttons. The top row (LOCK) is sticky:
 *     tap a quality to toggle it, persists across sessions. The bottom
 *     row (SHIFT) is momentary: a quality is active only while held. The
 *     union of both sets decorates whichever piano key is currently
 *     pressed. With nothing active, a key plays a single note.
 *  2. Full-piano A0–C8 keyboard, horizontally scrollable, taking the rest
 *     of the viewport. Zoom is controlled by the +/- buttons in the panel
 *     above so multi-finger play is never interrupted by a pinch gesture.
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
    val sticky by prefs.chordModSticky.collectAsState(initial = emptySet())
    val zoom by prefs.pianoZoom.collectAsState(initial = 1.0f)
    val savedLeftC by prefs.keyboardLeftC.collectAsState(initial = 48)

    // Held (momentary) modifiers live only in memory; releasing the
    // SHIFT button or app exit clears them.
    var held by remember { mutableStateOf<Set<ChordQuality>>(emptySet()) }

    // Map: piano-key MIDI root -> notes currently sounding for that root.
    // Snapshots the chord at note-on so that toggling sticky modifiers mid-
    // press doesn't re-voice the live chord; releasing the same root lets
    // us turn off the exact notes we started.
    val activeChordsByRoot = remember { mutableStateMapOf<Int, List<Int>>() }

    val scrollState = rememberScrollState()
    val whiteKeyPx = with(density) { (PIANO_WHITE_KEY_DP * zoom).toPx() }

    // Restore persisted scroll position once on first composition.
    LaunchedEffect(Unit) {
        val target = scrollPxForC(savedLeftC, whiteKeyPx)
        if (scrollState.value != target) scrollState.scrollTo(target)
    }

    // When zoom changes via the +/- buttons, re-pin scroll so the
    // previously-leftmost C stays leftmost across the resize.
    LaunchedEffect(whiteKeyPx) {
        scrollState.scrollTo(scrollPxForC(savedLeftC, whiteKeyPx))
    }

    // Persist scroll changes; re-key on whiteKeyPx so the persisted left-C
    // reflects the current zoom.
    LaunchedEffect(whiteKeyPx) {
        androidx.compose.runtime.snapshotFlow { scrollState.value }
            .drop(1)
            .distinctUntilChanged()
            .collect { px ->
                val leftC = leftmostVisibleC(px, whiteKeyPx)
                prefs.setKeyboardLeftC(leftC)
            }
    }

    val onPianoNoteOn: (Int) -> Unit = { rootMidi ->
        val intervals = buildChordIntervals(sticky union held)
        val notes = intervals
            .map { (rootMidi + it).coerceIn(0, 127) }
            .distinct()
        activeChordsByRoot[rootMidi] = notes
        notes.forEach { synth.noteOn(it, source = NoteSource.TOUCH) }
    }
    val onPianoNoteOff: (Int) -> Unit = { rootMidi ->
        (activeChordsByRoot.remove(rootMidi) ?: listOf(rootMidi))
            .forEach { synth.noteOff(it) }
    }

    val qualities = remember { ChordQuality.values().toList() }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ChordModifierRow(
                    label = "LOCK",
                    qualities = qualities,
                    selected = sticky,
                    onToggle = { q ->
                        val next = if (q in sticky) sticky - q else sticky + q
                        scope.launch { prefs.setChordModSticky(next) }
                    },
                )
                ChordModifierRow(
                    label = "SHIFT",
                    qualities = qualities,
                    selected = held,
                    momentary = true,
                    onPress = { q -> held = held + q },
                    onRelease = { q -> held = held - q },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Zoom",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(48.dp),
                    )
                    IconButton(
                        onClick = {
                            val next = (zoom - 0.1f).coerceAtLeast(ZOOM_MIN)
                            if (next != zoom) scope.launch { prefs.setPianoZoom(next) }
                        },
                        enabled = zoom > ZOOM_MIN + 1e-3f,
                    ) {
                        Icon(
                            Icons.Filled.Remove,
                            contentDescription = stringResource(R.string.score_zoom_out),
                        )
                    }
                    Text(
                        "${(zoom * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(56.dp),
                    )
                    IconButton(
                        onClick = {
                            val next = (zoom + 0.1f).coerceAtMost(ZOOM_MAX)
                            if (next != zoom) scope.launch { prefs.setPianoZoom(next) }
                        },
                        enabled = zoom < ZOOM_MAX - 1e-3f,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.score_zoom_in),
                        )
                    }
                }
            }
        }

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
                zoom = zoom,
                onNoteOn = onPianoNoteOn,
                onNoteOff = onPianoNoteOff,
            )
        }
    }
}
