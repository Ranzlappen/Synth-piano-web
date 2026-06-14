package io.github.ranzlappen.synthpiano.ui.dj

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.ui.components.GlassCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bottom-sheet track browser. Primary action is the SAF file picker
 * ([onPickFile]) which needs no permission and mirrors the `.mid` loader. A
 * secondary MediaStore list lets the user browse on-device audio with
 * title/duration metadata after granting the audio-read permission. A recent
 * list surfaces previously loaded tracks. Every row loads into Deck A or B
 * with one tap.
 */
@Composable
fun AudioLibraryBrowser(
    recent: List<DjTrack>,
    onPickFile: (DeckId) -> Unit,
    onSelect: (DjTrack, DeckId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val permission = audioReadPermission()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, permission) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var library by remember { mutableStateOf<List<DjTrack>>(emptyList()) }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(hasPermission) {
        if (hasPermission) library = queryDeviceAudio(ctx)
    }

    // A single LazyColumn hosts every section so the sheet scrolls cleanly in
    // landscape without nested-scroll conflicts.
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                stringResource(R.string.dj_library),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(onClick = { onPickFile(DeckId.A) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                    Text(
                        stringResource(R.string.dj_pick_file_a),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Button(onClick = { onPickFile(DeckId.B) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                    Text(
                        stringResource(R.string.dj_pick_file_b),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        if (recent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.dj_recent)) }
            items(recent) { track -> TrackRow(track = track, onSelect = onSelect) }
        }

        item { SectionHeader(stringResource(R.string.dj_device_audio)) }
        when {
            !hasPermission -> item {
                OutlinedButton(
                    onClick = { permLauncher.launch(permission) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.dj_grant_permission))
                }
            }
            library.isEmpty() -> item {
                Text(
                    stringResource(R.string.dj_empty_library),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> items(library) { track -> TrackRow(track = track, onSelect = onSelect) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TrackRow(track: DjTrack, onSelect: (DjTrack, DeckId) -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (track.durationMs > 0) {
                    Text(
                        formatTime(track.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = { onSelect(track, DeckId.A) }) {
                Text(stringResource(R.string.dj_load_a))
            }
            OutlinedButton(onClick = { onSelect(track, DeckId.B) }) {
                Text(stringResource(R.string.dj_load_b))
            }
        }
    }
}

/** The runtime permission needed to query [MediaStore.Audio] on this OS. */
private fun audioReadPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

private suspend fun queryDeviceAudio(ctx: Context): List<DjTrack> = withContext(Dispatchers.IO) {
    val out = mutableListOf<DjTrack>()
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.DURATION,
    )
    runCatching {
        ctx.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id)
                out += DjTrack(
                    uri = uri.toString(),
                    title = cursor.getString(titleCol) ?: uri.lastPathSegment.orEmpty(),
                    durationMs = cursor.getInt(durCol),
                )
            }
        }
    }
    out
}
