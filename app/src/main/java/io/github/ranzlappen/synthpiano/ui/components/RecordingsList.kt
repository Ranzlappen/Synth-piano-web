package io.github.ranzlappen.synthpiano.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.WavRecorder
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lists every WAV recording under [Context.filesDir]/recordings, with
 * inline share + delete actions. Refreshes whenever [refreshTick] changes
 * (callers bump it after a recording stops).
 *
 * Each row reads the WAV header to compute duration in mm:ss; if parsing
 * fails the row falls back to the file size in KB.
 */
@Composable
fun RecordingsList(
    recorder: WavRecorder,
    refreshTick: Int,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf<List<RecordingEntry>>(emptyList()) }

    LaunchedEffect(refreshTick) {
        entries = scanRecordings(ctx)
    }

    if (entries.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.recordings_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items = entries, key = { it.path }) { entry ->
            RecordingRow(
                entry = entry,
                onShare = { recorder.share(ctx, entry.path) },
                onDelete = {
                    runCatching { File(entry.path).delete() }
                    entries = entries.filterNot { it.path == entry.path }
                },
            )
        }
    }
}

@Composable
private fun RecordingRow(
    entry: RecordingEntry,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                entry.subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onShare) {
            Icon(
                Icons.Filled.IosShare,
                contentDescription = stringResource(R.string.action_share),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private data class RecordingEntry(
    val path: String,
    val displayName: String,
    val subtitle: String,
)

private fun scanRecordings(ctx: Context): List<RecordingEntry> {
    val dir = File(ctx.filesDir, "recordings")
    if (!dir.exists()) return emptyList()
    val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".wav") } ?: return emptyList()
    return files
        .sortedByDescending { it.lastModified() }
        .map { file ->
            val durationStr = readWavDuration(file)
            val sizeKb = (file.length() / 1024L).coerceAtLeast(1L)
            val timestampLabel = parseTimestamp(file.name)
                ?: SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(file.lastModified()))
            val subtitle = "$timestampLabel  •  ${durationStr ?: "${sizeKb} KB"}"
            RecordingEntry(
                path = file.absolutePath,
                displayName = file.name.removeSuffix(".wav"),
                subtitle = subtitle,
            )
        }
}

private fun readWavDuration(file: File): String? = runCatching {
    RandomAccessFile(file, "r").use { raf ->
        if (raf.length() < 44) return@use null
        // "RIFF" header check
        val header = ByteArray(4)
        raf.read(header)
        if (String(header) != "RIFF") return@use null
        // Sample rate is at byte offset 24; 4-byte little-endian.
        raf.seek(24)
        val srBytes = ByteArray(4).also { raf.read(it) }
        val sampleRate = (srBytes[0].toInt() and 0xFF) or
            ((srBytes[1].toInt() and 0xFF) shl 8) or
            ((srBytes[2].toInt() and 0xFF) shl 16) or
            ((srBytes[3].toInt() and 0xFF) shl 24)
        if (sampleRate <= 0) return@use null
        // Stereo 16-bit PCM = 4 bytes/frame.
        val dataBytes = (raf.length() - 44L).coerceAtLeast(0L)
        val seconds = dataBytes / (4L * sampleRate)
        val m = seconds / 60
        val s = seconds % 60
        "%d:%02d".format(m, s)
    }
}.getOrNull()

private fun parseTimestamp(fileName: String): String? {
    val match = Regex("synth_(\\d{8})_(\\d{6})\\.wav").matchEntire(fileName) ?: return null
    val (date, time) = match.destructured
    val parser = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
    val display = SimpleDateFormat("MMM d, HH:mm", Locale.US)
    val parsed = runCatching { parser.parse(date + time) }.getOrNull() ?: return null
    return display.format(parsed)
}
