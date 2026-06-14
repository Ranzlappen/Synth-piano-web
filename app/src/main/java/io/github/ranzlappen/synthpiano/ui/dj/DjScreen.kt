package io.github.ranzlappen.synthpiano.ui.dj

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.ui.components.GlassCard
import io.github.ranzlappen.synthpiano.ui.components.InfoSlider
import io.github.ranzlappen.synthpiano.ui.score.tryTakePersistablePermission
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val MAX_RECENT = 10
private val recentJsonFormat = Json { ignoreUnknownKeys = true; isLenient = true }
private val recentSerializer = ListSerializer(DjTrack.serializer())

/**
 * The DJ tab: a two-deck turntable set backed by [DjEngine] (two Android
 * [android.media.MediaPlayer] instances — fully separate from the Oboe/C++
 * synth). Deck A (left) and Deck B (right) flank a center column with the
 * crossfader, master volume, and library browser. Both decks pause when the
 * app is backgrounded and the players are released when the tab leaves the
 * composition.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DjScreen(
    prefs: PreferencesRepository,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { DjEngine(ctx.applicationContext) }

    val recentJson by prefs.djRecentJson.collectAsState(initial = null)
    val recent = remember(recentJson) { decodeRecent(recentJson) }

    var sheetOpen by remember { mutableStateOf(false) }
    var pendingDeck by remember { mutableStateOf(DeckId.A) }

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Persist read access so the URI survives in recent history.
        tryTakePersistablePermission(ctx, uri)
        loadInto(engine, pendingDeck, uri, title = null)
        val st = if (pendingDeck == DeckId.A) engine.deckA else engine.deckB
        scope.launch {
            persistRecent(prefs, recent, DjTrack(uri.toString(), st.title ?: "Track", st.durationMs))
        }
    }

    // Drive playhead updates while the tab is visible.
    LaunchedEffect(Unit) {
        while (true) {
            engine.poll()
            delay(50)
        }
    }

    // Pause on background; release players when the tab leaves composition.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) engine.pauseAll()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.release()
        }
    }

    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Deck(
            state = engine.deckA,
            onPlayPause = { engine.playPause(DeckId.A) },
            onCueJump = { engine.jumpToCue(DeckId.A) },
            onCueSet = { engine.setCue(DeckId.A) },
            onScratch = { engine.nudge(DeckId.A, it) },
            onSeek = { engine.seekTo(DeckId.A, it) },
            onPitch = { engine.setPitchPercent(DeckId.A, it) },
            onVolume = { engine.setVolume(DeckId.A, it) },
            onLoad = { pendingDeck = DeckId.A; openLauncher.launch(arrayOf("audio/*")) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )

        DjCenterColumn(
            crossfader = engine.crossfader,
            onCrossfader = { engine.crossfader = it },
            master = engine.master,
            onMaster = { engine.master = it },
            onOpenLibrary = { sheetOpen = true },
            modifier = Modifier.width(200.dp).fillMaxHeight(),
        )

        Deck(
            state = engine.deckB,
            onPlayPause = { engine.playPause(DeckId.B) },
            onCueJump = { engine.jumpToCue(DeckId.B) },
            onCueSet = { engine.setCue(DeckId.B) },
            onScratch = { engine.nudge(DeckId.B, it) },
            onSeek = { engine.seekTo(DeckId.B, it) },
            onPitch = { engine.setPitchPercent(DeckId.B, it) },
            onVolume = { engine.setVolume(DeckId.B, it) },
            onLoad = { pendingDeck = DeckId.B; openLauncher.launch(arrayOf("audio/*")) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            AudioLibraryBrowser(
                recent = recent,
                onPickFile = { deck ->
                    pendingDeck = deck
                    sheetOpen = false
                    openLauncher.launch(arrayOf("audio/*"))
                },
                onSelect = { track, deck ->
                    sheetOpen = false
                    loadInto(engine, deck, Uri.parse(track.uri), track.title)
                    scope.launch { persistRecent(prefs, recent, track) }
                },
            )
        }
    }
}

@Composable
private fun DjCenterColumn(
    crossfader: Float,
    onCrossfader: (Float) -> Unit,
    master: Float,
    onMaster: (Float) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = onOpenLibrary, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                Text(
                    stringResource(R.string.dj_library),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            InfoSlider(
                label = stringResource(R.string.dj_master),
                value = master,
                range = 0f..1f,
                onChange = onMaster,
                valueFormatter = { "${(it * 100).toInt()}%" },
                labelWidth = 56.dp,
            )

            Crossfader(value = crossfader, onValueChange = onCrossfader)
        }
    }
}

/** Load [uri] into [deck], setting [title] when known. */
private fun loadInto(engine: DjEngine, deck: DeckId, uri: Uri, title: String?) {
    engine.load(deck, uri, title)
}

private fun decodeRecent(raw: String?): List<DjTrack> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { recentJsonFormat.decodeFromString(recentSerializer, raw) }
        .getOrDefault(emptyList())
}

private suspend fun persistRecent(
    prefs: PreferencesRepository,
    current: List<DjTrack>,
    track: DjTrack,
) {
    val updated = (listOf(track) + current.filterNot { it.uri == track.uri }).take(MAX_RECENT)
    prefs.setDjRecentJson(recentJsonFormat.encodeToString(recentSerializer, updated))
}
