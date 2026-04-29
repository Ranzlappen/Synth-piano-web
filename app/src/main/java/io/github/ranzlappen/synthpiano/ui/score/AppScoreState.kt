package io.github.ranzlappen.synthpiano.ui.score

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.Score
import io.github.ranzlappen.synthpiano.data.midi.MidiScore
import io.github.ranzlappen.synthpiano.data.midi.SmfReader
import io.github.ranzlappen.synthpiano.data.midi.importLegacyJsonAsMidiScore
import io.github.ranzlappen.synthpiano.data.midi.toLegacyScore
import io.github.ranzlappen.synthpiano.data.midi.toMidiScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * App-scoped score state. Held in [io.github.ranzlappen.synthpiano.ui.SynthAppRoot]
 * via `remember`, so the loaded score survives switching tabs.
 *
 * The canonical state is now a [MidiScore] (event-based, full SMF
 * fidelity). The legacy step-based [Score] is exposed as a derived,
 * read-only view (`score`) so the existing step-grid Composer can keep
 * rendering until the piano-roll editor lands. The `OpenDocument`
 * launcher in `ComposerTab` calls `takePersistableUriPermission` so the
 * URI remains readable across process death.
 *
 * Loading dispatches on the file's magic bytes:
 *   - `MThd...`  → parsed by [SmfReader]
 *   - else       → treated as legacy JSON (Python-source format) and
 *                  imported via [importLegacyJsonAsMidiScore]
 */
class AppScoreState(
    private val ctx: Context,
    private val prefs: PreferencesRepository,
) {
    var midiScore: MidiScore? by mutableStateOf(null)
    var status: String? by mutableStateOf(null)

    /**
     * Step-grid projection for the legacy Composer / ScorePlayer.
     *
     * Reads project the canonical [midiScore] through [toLegacyScore].
     * Writes (used by the legacy step editor while the piano-roll editor
     * is being built) re-encode the step model back to a [MidiScore] via
     * [toMidiScore]. Lossy for non-step content, which the legacy editor
     * cannot author anyway. The setter goes away in Batch 4 once the
     * piano-roll editor mutates [midiScore] directly.
     */
    private val derivedScore = derivedStateOf { midiScore?.toLegacyScore() }
    var score: Score?
        get() = derivedScore.value
        set(value) { midiScore = value?.toMidiScore() }

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
        midiScore = Score(notes = emptyList(), title = "Untitled", tempoBpm = tempoBpm).toMidiScore()
        status = "New empty score"
    }

    /** Replace the current score (e.g. after the editor saves a new version). */
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
            val parsed = parseSmfOrLegacyJson(bytes)
            Pair<MidiScore?, String?>(parsed, null)
        }.getOrElse { t -> Pair<MidiScore?, String?>(null, "Failed: ${t.message}") }
    }

internal suspend fun readMidiScoreFromAsset(ctx: Context, assetPath: String): Pair<MidiScore?, String?> =
    withContext(Dispatchers.IO) {
        runCatching {
            val bytes = ctx.assets.open(assetPath).use { it.readBytes() }
            val parsed = parseSmfOrLegacyJson(bytes)
            Pair<MidiScore?, String?>(parsed, null)
        }.getOrElse { t -> Pair<MidiScore?, String?>(null, "Failed: ${t.message}") }
    }

/** Dispatch parsing on magic bytes: SMF (`MThd...`) vs legacy JSON. */
private fun parseSmfOrLegacyJson(bytes: ByteArray): MidiScore {
    if (looksLikeSmf(bytes)) return SmfReader.read(bytes)
    val text = bytes.toString(Charsets.UTF_8)
    return importLegacyJsonAsMidiScore(text)
}

private fun looksLikeSmf(bytes: ByteArray): Boolean =
    bytes.size >= 4 &&
        bytes[0] == 'M'.code.toByte() &&
        bytes[1] == 'T'.code.toByte() &&
        bytes[2] == 'h'.code.toByte() &&
        bytes[3] == 'd'.code.toByte()

internal fun tryTakePersistablePermission(ctx: Context, uri: Uri) {
    runCatching {
        ctx.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
