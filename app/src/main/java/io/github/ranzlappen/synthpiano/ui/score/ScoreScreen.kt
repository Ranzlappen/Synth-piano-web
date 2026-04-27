package io.github.ranzlappen.synthpiano.ui.score

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.Score
import io.github.ranzlappen.synthpiano.data.ScorePlayer
import io.github.ranzlappen.synthpiano.data.ScoreStep
import io.github.ranzlappen.synthpiano.data.parseScoreJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreScreen(
    synth: SynthController,
    prefs: PreferencesRepository,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var score by remember { mutableStateOf<Score?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val tempo by prefs.tempoBpm.collectAsState(initial = 120)

    val player = remember { ScorePlayer(scope, synth) }
    val isPlaying by player.isPlaying.collectAsState()
    val currentStep by player.currentStep.collectAsState()

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (loaded, msg) = readScoreFromUri(ctx, uri)
            if (loaded != null) {
                score = loaded
                status = "Loaded${loaded.title?.let { ": $it" } ?: ""}"
                prefs.setLastScoreUri(uri.toString())
            } else {
                status = msg
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Score") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to play",
                        )
                    }
                },
            )
        },
    ) { topPadding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(topPadding).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { openLauncher.launch(arrayOf("application/json", "*/*")) }) {
                Text("Load .json")
            }
            DemoMenuButton { name ->
                scope.launch {
                    val (loaded, msg) = readScoreFromAsset(ctx, "scores/$name")
                    if (loaded != null) { score = loaded; status = "Demo: ${loaded.title ?: name}" }
                    else status = msg
                }
            }
            Button(
                onClick = {
                    if (score == null) {
                        score = Score(notes = emptyList(), title = "Untitled", tempoBpm = tempo)
                        status = "New empty score"
                    }
                }
            ) { Text("New") }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = {
                val s = score ?: return@IconButton
                if (isPlaying) player.stop() else player.start(s, tempo)
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                )
            }
            Text("Tempo $tempo bpm", modifier = Modifier.padding(end = 4.dp))
            Slider(
                value = tempo.toFloat(),
                onValueChange = { v -> scope.launch { prefs.setTempoBpm(v.toInt()) } },
                valueRange = 30f..240f,
                modifier = Modifier.weight(1f),
            )
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.labelMedium)
        }

        HorizontalDivider()

        val s = score
        if (s == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No score loaded",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            ScoreEditor(
                score = s,
                currentStep = currentStep,
                onChange = { score = it },
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
        Button(onClick = { expanded = true }) { Text("Load demo") }
        androidx.compose.material3.DropdownMenu(
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
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = score.title.orEmpty(),
                onValueChange = { onChange(score.copy(title = it.ifBlank { null })) },
                label = { Text("Title") },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
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
        FloatingActionButton(
            onClick = {
                val newStep = ScoreStep(noteNames = listOf("C4"), durationBeats = 1f)
                onChange(score.copy(notes = score.notes + newStep))
            },
            modifier = Modifier.align(Alignment.End).padding(8.dp),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add step") }
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
             else MaterialTheme.colorScheme.surfaceVariant
    Card(
        colors = CardDefaults.cardColors(containerColor = bg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("#${index + 1}", modifier = Modifier.padding(end = 4.dp))
            OutlinedTextField(
                value = step.noteNames.joinToString(","),
                onValueChange = { v ->
                    val names = v.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    onChange(step.copy(noteNames = names))
                },
                label = { Text("Notes") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = "%.2f".format(step.durationBeats),
                onValueChange = { it.toFloatOrNull()?.let { v -> onChange(step.copy(durationBeats = v)) } },
                label = { Text("Beats") },
                modifier = Modifier.width(96.dp),
                singleLine = true,
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

private suspend fun readScoreFromUri(ctx: Context, uri: Uri): Pair<Score?, String?> = withContext(Dispatchers.IO) {
    runCatching {
        val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: return@runCatching Pair<Score?, String?>(null, "Could not open file")
        val s = parseScoreJson(text)
        Pair<Score?, String?>(s, null)
    }.getOrElse { t -> Pair<Score?, String?>(null, "Failed: ${t.message}") }
}

private suspend fun readScoreFromAsset(ctx: Context, assetPath: String): Pair<Score?, String?> = withContext(Dispatchers.IO) {
    runCatching {
        val text = ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
        val s = parseScoreJson(text)
        Pair<Score?, String?>(s, null)
    }.getOrElse { t -> Pair<Score?, String?>(null, "Failed: ${t.message}") }
}

