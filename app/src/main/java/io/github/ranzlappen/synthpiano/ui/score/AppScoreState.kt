package io.github.ranzlappen.synthpiano.ui.score

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.Score
import io.github.ranzlappen.synthpiano.data.parseScoreJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * App-scoped score state. Held in [io.github.ranzlappen.synthpiano.ui.SynthAppRoot]
 * via `remember`, so the loaded score survives switching tabs (the previous
 * implementation kept it in a screen-local `remember` inside `ComposerTab`,
 * which was discarded on every tab change).
 *
 * On first composition the root calls [loadFromPrefs] to re-hydrate the score
 * from the persisted `LAST_SCORE_URI`. The `OpenDocument` launcher in
 * `ComposerTab` calls `takePersistableUriPermission` so the URI remains
 * readable across process death.
 */
class AppScoreState(
    private val ctx: Context,
    private val prefs: PreferencesRepository,
) {
    var score: Score? by mutableStateOf(null)
    var status: String? by mutableStateOf(null)

    suspend fun loadFromUri(uri: Uri, persist: Boolean = true) {
        val (loaded, msg) = readScoreFromUri(ctx, uri)
        if (loaded != null) {
            score = loaded
            status = "Loaded${loaded.title?.let { ": $it" } ?: ""}"
            if (persist) prefs.setLastScoreUri(uri.toString())
        } else {
            status = msg
        }
    }

    suspend fun loadFromAsset(assetPath: String) {
        val (loaded, msg) = readScoreFromAsset(ctx, assetPath)
        if (loaded != null) {
            score = loaded
            status = "Demo: ${loaded.title ?: assetPath}"
        } else {
            status = msg
        }
    }

    fun newEmpty(tempoBpm: Int) {
        score = Score(notes = emptyList(), title = "Untitled", tempoBpm = tempoBpm)
        status = "New empty score"
    }

    /** Re-hydrate from `LAST_SCORE_URI` if one was persisted. Silent on failure. */
    suspend fun loadFromPrefs() {
        val saved = prefs.lastScoreUri.first() ?: return
        runCatching { loadFromUri(Uri.parse(saved), persist = false) }
    }
}

internal suspend fun readScoreFromUri(ctx: Context, uri: Uri): Pair<Score?, String?> =
    withContext(Dispatchers.IO) {
        runCatching {
            val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()
                ?.use { it.readText() }
                ?: return@runCatching Pair<Score?, String?>(null, "Could not open file")
            val s = parseScoreJson(text)
            Pair<Score?, String?>(s, null)
        }.getOrElse { t -> Pair<Score?, String?>(null, "Failed: ${t.message}") }
    }

internal suspend fun readScoreFromAsset(ctx: Context, assetPath: String): Pair<Score?, String?> =
    withContext(Dispatchers.IO) {
        runCatching {
            val text = ctx.assets.open(assetPath).bufferedReader().use { it.readText() }
            val s = parseScoreJson(text)
            Pair<Score?, String?>(s, null)
        }.getOrElse { t -> Pair<Score?, String?>(null, "Failed: ${t.message}") }
    }

internal fun tryTakePersistablePermission(ctx: Context, uri: Uri) {
    runCatching {
        ctx.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}
