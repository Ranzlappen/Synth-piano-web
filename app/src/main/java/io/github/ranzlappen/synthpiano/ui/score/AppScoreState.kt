package io.github.ranzlappen.synthpiano.ui.score

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.midi.MidiScore
import io.github.ranzlappen.synthpiano.data.midi.MidiTiming
import io.github.ranzlappen.synthpiano.data.midi.SmfReader
import io.github.ranzlappen.synthpiano.data.midi.TempoEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * App-scoped score state. Held in [io.github.ranzlappen.synthpiano.ui.SynthAppRoot]
 * via `remember`, so the loaded score survives switching tabs.
 *
 * Canonical state is a [MidiScore] (event-based, full SMF fidelity). The
 * `OpenDocument` launcher in `ComposerTab` calls
 * `takePersistableUriPermission` so the URI remains readable across
 * process death.
 */
class AppScoreState(
    private val ctx: Context,
    private val prefs: PreferencesRepository,
) {
    var midiScore: MidiScore? by mutableStateOf(null)
    var status: String? by mutableStateOf(null)

    suspend fun loadFromUri(uri: Uri, persist: Boolean = true) {
        val (loaded, msg) = readMidiScoreFromUri(ctx, uri)
        if (loaded != null) {
            midiScore = loaded
            status = "Loaded${loaded.title?.let { ": $it" } ?: ""}"
            if (persist) prefs.setLastScoreUri(uri.toString())
        } else {
            status = msg
        }
    }

    suspend fun loadFromAsset(assetPath: String) {
        val (loaded, msg) = readMidiScoreFromAsset(ctx, assetPath)
        if (loaded != null) {
            midiScore = loaded
            status = "Demo: ${loaded.title ?: assetPath}"
        } else {
            status = msg
        }
    }

    fun newEmpty(tempoBpm: Int) {
        midiScore = MidiScore(
            ppq = MidiTiming.DEFAULT_PPQ,
            title = "Untitled",
            tempoMap = mutableListOf(
                TempoEvent(0, MidiTiming.bpmToMicrosPerQuarter(tempoBpm)),
            ),
        )
        status = "New empty score"
    }

    /** Replace the current score (e.g. after the editor mutates it). */
    fun replace(newScore: MidiScore, newStatus: String? = null) {
        midiScore = newScore
        if (newStatus != null) status = newStatus
    }

    /** Re-hydrate from `LAST_SCORE_URI` if one was persisted. Silent on failure. */
    suspend fun loadFromPrefs() {
        val saved = prefs.lastScoreUri.first() ?: return
        runCatching { loadFromUri(Uri.parse(saved), persist = false) }
    }
}

internal suspend fun readMidiScoreFromUri(ctx: Context, uri: Uri): Pair<MidiScore?, String?> =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@runCatching Pair<MidiScore?, String?>(null, "Could not open file")
            Pair<MidiScore?, String?>(SmfReader.read(bytes), null)
        }.getOrElse { t -> Pair<MidiScore?, String?>(null, "Failed: ${t.message}") }
    }

internal suspend fun readMidiScoreFromAsset(ctx: Context, assetPath: String): Pair<MidiScore?, String?> =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = ctx.assets.open(assetPath).use { it.readBytes() }
            Pair<MidiScore?, String?>(SmfReader.read(bytes), null)
        }.getOrElse { t -> Pair<MidiScore?, String?>(null, "Failed: ${t.message}") }
    }

internal fun tryTakePersistablePermission(ctx: Context, uri: Uri) {
    runCatching {
        ctx.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
