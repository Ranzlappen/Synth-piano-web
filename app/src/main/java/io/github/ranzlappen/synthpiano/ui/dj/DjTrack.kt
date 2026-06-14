package io.github.ranzlappen.synthpiano.ui.dj

import kotlinx.serialization.Serializable

/**
 * A loadable audio track (from the SAF picker, MediaStore, or recent
 * history). [uri] is the string form of a content URI; [durationMs] is 0
 * when unknown (e.g. SAF picks before the player prepares).
 */
@Serializable
data class DjTrack(
    val uri: String,
    val title: String,
    val durationMs: Int = 0,
)

/** Format milliseconds as `m:ss`. */
fun formatTime(ms: Int): String {
    val totalSec = (ms.coerceAtLeast(0)) / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
