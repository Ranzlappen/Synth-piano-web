package io.github.ranzlappen.synthpiano.ui.score

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.audio.WavRecorder
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.Score
import io.github.ranzlappen.synthpiano.data.ScorePlayer
import io.github.ranzlappen.synthpiano.data.ScoreStep
import io.github.ranzlappen.synthpiano.data.toJsonString
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

    val score = scoreState.score
    val status = scoreState.status
    val tempo by prefs.tempoBpm.collectAsState(initial = 120)

    val player = remember { ScorePlayer(scope, synth) }
    val isPlaying by player.isPlaying.collectAsState()
    val currentStep by player.currentStep.collectAsState()

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
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val current = score ?: return@rememberLauncherForActivityResult
        scope.launch {
            val ok = writeScoreToUri(ctx, uri, current)
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
        EditorPane(
            score = score,
            onScoreChange = { scoreState.score = it },
            tempo = tempo,
            onTempoChange = { v -> scope.launch { prefs.setTempoBpm(v) } },
            isPlaying = isPlaying,
            currentStep = currentStep,
            onTogglePlay = {
                val s = score ?: return@EditorPane
                if (isPlaying) player.stop() else player.start(s, tempo)
            },
            status = status,
            onLoadJson = { openLauncher.launch(arrayOf("application/json", "*/*")) },
            onLoadDemo = { name ->
                scope.launch { scoreState.loadFromAsset("scores/$name") }
            },
            onNew = { scoreState.newEmpty(tempo) },
            onSaveAs = {
                val name = (score?.title?.takeIf { it.isNotBlank() } ?: "score").let { "$it.json" }
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
private fun EditorPane(
    score: Score?,
    onScoreChange: (Score) -> Unit,
    tempo: Int,
    onTempoChange: (Int) -> Unit,
    isPlaying: Boolean,
    currentStep: Int,
    onTogglePlay: () -> Unit,
    status: String?,
    onLoadJson: () -> Unit,
    onLoadDemo: (String) -> Unit,
    onNew: () -> Unit,
    onSaveAs: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                    enabled = score != null,
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
                    enabled = score != null,
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Stop" else "Play",
                    )
                }
                val total = score?.notes?.size ?: 0
                val stepLabel = if (currentStep >= 0 && total > 0)
                    stringResource(R.string.score_step, currentStep + 1, total)
                else "—"
                Text(stepLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(86.dp))
                Text("${stringResource(R.string.score_tempo)} $tempo", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = tempo.toFloat(),
                    onValueChange = { onTempoChange(it.toInt()) },
                    valueRange = 30f..240f,
                    modifier = Modifier.weight(1f),
                )
            }

            status?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val s = score
            if (s == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.score_no_score_loaded),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ScoreEditor(
                    score = s,
                    currentStep = currentStep,
                    onChange = onScoreChange,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun DemoMenuButton(onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val demos = listOf(
        "ode_to_joy.json" to "Ode to Joy",
        "twinkle_twinkle.json" to "Twinkle Twinkle",
        "frere_jacques.json" to "Frère Jacques",
        "scarborough_fair.json" to "Scarborough Fair",
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
private fun ScoreEditor(
    score: Score,
    currentStep: Int,
    onChange: (Score) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = score.title.orEmpty(),
            onValueChange = { onChange(score.copy(title = it.ifBlank { null })) },
            label = { Text(stringResource(R.string.score_title)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(score.notes) { idx, step ->
                StepRow(
                    index = idx,
                    step = step,
                    isCurrent = idx == currentStep,
                    onChange = { newStep ->
                        onChange(score.copy(notes = score.notes.toMutableList().also { it[idx] = newStep }))
                    },
                    onDelete = {
                        onChange(score.copy(notes = score.notes.toMutableList().also { it.removeAt(idx) }))
                    },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = {
                val newStep = ScoreStep(noteNames = listOf("C4"), durationBeats = 1f)
                onChange(score.copy(notes = score.notes + newStep))
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.score_add_step))
        }
    }
}

@Composable
private fun StepRow(
    index: Int,
    step: ScoreStep,
    isCurrent: Boolean,
    onChange: (ScoreStep) -> Unit,
    onDelete: () -> Unit,
) {
    val bg = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
             else MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)

    var durText by remember { mutableStateOf("%.2f".format(step.durationBeats)) }
    LaunchedEffect(step.durationBeats) {
        if (durText.toFloatOrNull() != step.durationBeats) {
            durText = "%.2f".format(step.durationBeats)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("#${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(36.dp))

        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (step.noteNames.isEmpty()) {
                Text(
                    stringResource(R.string.score_rest),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
            step.noteNames.forEachIndexed { i, name ->
                NoteChipField(
                    value = name,
                    onChange = { v ->
                        val next = step.noteNames.toMutableList().also { it[i] = v }
                        onChange(step.copy(noteNames = next))
                    },
                    onRemove = {
                        val next = step.noteNames.toMutableList().also { it.removeAt(i) }
                        onChange(step.copy(noteNames = next))
                    },
                )
            }
            IconButton(
                onClick = {
                    val newName = step.noteNames.lastOrNull() ?: "C4"
                    onChange(step.copy(noteNames = step.noteNames + newName))
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.score_add_note))
            }
        }

        OutlinedTextField(
            value = durText,
            onValueChange = { v ->
                durText = v
                v.toFloatOrNull()?.takeIf { it > 0f }
                    ?.let { onChange(step.copy(durationBeats = it)) }
            },
            label = { Text(stringResource(R.string.score_beats)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(96.dp),
            singleLine = true,
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.score_delete_step))
        }
    }
}

@Composable
private fun NoteChipField(
    value: String,
    onChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (text != value) text = value
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                text = v
                onChange(v)
            },
            modifier = Modifier.width(72.dp),
            singleLine = true,
        )
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.score_remove_note),
                modifier = Modifier.size(18.dp),
            )
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

private suspend fun writeScoreToUri(ctx: Context, uri: Uri, score: Score): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val text = score.toJsonString(prettyPrint = true)
        ctx.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
        true
    }.getOrElse { false }
}
