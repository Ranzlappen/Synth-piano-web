package io.github.ranzlappen.synthpiano.ui.score

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.audio.WavRecorder
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.audio.MidiScorePlayer
import io.github.ranzlappen.synthpiano.data.midi.MidiScore
import io.github.ranzlappen.synthpiano.data.midi.SmfWriter
import io.github.ranzlappen.synthpiano.ui.components.GlassCard
import io.github.ranzlappen.synthpiano.ui.components.HorizontalDragHandle
import io.github.ranzlappen.synthpiano.ui.components.RecordingsList
import io.github.ranzlappen.synthpiano.ui.components.VerticalDragHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The COMPOSE tab: load + edit + play scores, plus a list of saved
 * recordings. Layout: side-by-side editor + recordings on >= 900dp wide
 * screens; stacked on narrower phones.
 */
@Composable
fun ComposerTab(
    synth: SynthController,
    prefs: PreferencesRepository,
    scoreState: AppScoreState,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val midiScore = scoreState.midiScore
    val status = scoreState.status
    val tempo by prefs.tempoBpm.collectAsState(initial = 120)

    val player = remember { MidiScorePlayer(scope, synth) }
    val isPlaying by player.isPlaying.collectAsState()
    val currentTick by player.currentTick.collectAsState()

    val recorder = remember { WavRecorder(synth) }
    var recordingsRefreshTick by remember { mutableIntStateOf(0) }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Persist read access so the next launch can auto-reload via
        // AppScoreState.loadFromPrefs() across process death.
        tryTakePersistablePermission(ctx, uri)
        scope.launch { scoreState.loadFromUri(uri) }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/midi"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = midiScore ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = writeMidiScoreToUri(ctx, uri, current)
            scoreState.status = if (ok) ctx.getString(R.string.score_saved)
                                else ctx.getString(R.string.score_save_failed, "I/O error")
        }
    }

    LaunchedEffect(Unit) {
        // Re-read whenever a recording finishes (RecordingSession not
        // observable directly here; the chip in HeaderStrip writes files
        // we want to surface). Simple heuristic: refresh once on enter
        // and let the user re-enter the tab if they record while inside.
        recordingsRefreshTick++
    }

    val widthDp = LocalConfiguration.current.screenWidthDp
    val sideBySide = widthDp >= 900
    val density = androidx.compose.ui.platform.LocalDensity.current

    val editorWeight by prefs.composerEditorWeight.collectAsState(initial = 0.667f)
    val editorHeightDp by prefs.composerEditorHeightDp.collectAsState(initial = 600f)

    val editorPaneFactory: @Composable (Modifier) -> Unit = { paneModifier ->
        MidiEditorPane(
            midiScore = midiScore,
            onScoreChange = { scoreState.replace(it) },
            tempo = tempo,
            onTempoChange = { v -> scope.launch { prefs.setTempoBpm(v) } },
            isPlaying = isPlaying,
            currentTick = currentTick,
            onTogglePlay = {
                val s = midiScore ?: return@MidiEditorPane
                if (isPlaying) player.stop() else player.start(s, tempoOverrideBpm = tempo)
            },
            status = status,
            onLoadJson = {
                // MIME filter is permissive — many file pickers don't recognise
                // audio/midi for .mid files, so we fall back to */* so the user
                // can always pick one. The reader validates by magic bytes.
                openLauncher.launch(arrayOf("audio/midi", "audio/x-midi", "*/*"))
            },
            onLoadDemo = { name ->
                scope.launch { scoreState.loadFromAsset("scores/$name") }
            },
            onNew = { scoreState.newEmpty(tempo) },
            onSaveAs = {
                val name = (midiScore?.title?.takeIf { it.isNotBlank() } ?: "score").let { "$it.mid" }
                saveLauncher.launch(name)
            },
            modifier = paneModifier,
        )
    }

    if (sideBySide) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            Row(modifier = Modifier.fillMaxSize()) {
                editorPaneFactory(Modifier.weight(editorWeight).fillMaxHeight())
                VerticalDragHandle(
                    onDelta = { dx ->
                        val w = (editorWeight + dx / totalWidthPx).coerceIn(0.2f, 0.85f)
                        scope.launch { prefs.setComposerEditorWeight(w) }
                    },
                )
                RecordingsPane(
                    recorder = recorder,
                    refreshTick = recordingsRefreshTick,
                    onRefresh = { recordingsRefreshTick++ },
                    modifier = Modifier.weight(1f - editorWeight).fillMaxHeight(),
                )
            }
        }
    } else {
        // Stacked: scrollable parent, fixed-height editor (tripled by
        // default), drag handle, then recordings with a bounded height so
        // the LazyColumn inside can measure under verticalScroll.
        val scroll = rememberScrollState()
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            editorPaneFactory(
                Modifier
                    .fillMaxWidth()
                    .height(editorHeightDp.dp),
            )
            HorizontalDragHandle(
                onDelta = { dy ->
                    val newDp = editorHeightDp + with(density) { dy.toDp().value }
                    scope.launch { prefs.setComposerEditorHeightDp(newDp) }
                },
            )
            RecordingsPane(
                recorder = recorder,
                refreshTick = recordingsRefreshTick,
                onRefresh = { recordingsRefreshTick++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 480.dp),
            )
        }
    }
}


@Composable
internal fun DemoMenuButton(onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val demos = listOf(
        "ode_to_joy.mid" to "Ode to Joy",
        "twinkle_twinkle.mid" to "Twinkle Twinkle",
        "frere_jacques.mid" to "Frère Jacques",
        "scarborough_fair.mid" to "Scarborough Fair",
    )
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(stringResource(R.string.score_load_demo)) }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            demos.forEach { (file, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onPick(file); expanded = false },
                )
            }
        }
    }
}




@Composable
private fun RecordingsPane(
    recorder: WavRecorder,
    refreshTick: Int,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.recordings_section),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onRefresh) { Text("↻") }
            }
            RecordingsList(
                recorder = recorder,
                refreshTick = refreshTick,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private suspend fun writeMidiScoreToUri(ctx: Context, uri: Uri, score: MidiScore): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val bytes = SmfWriter.write(score)
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
        true
    }.getOrElse { false }
}
