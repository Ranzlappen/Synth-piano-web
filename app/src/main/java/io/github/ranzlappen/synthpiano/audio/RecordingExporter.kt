package io.github.ranzlappen.synthpiano.audio

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Copies a recording (and its sibling `.mid`, if present) from app-specific
 * external storage into the user's public `Download/Synth Piano/` collection
 * via MediaStore so every file manager and music app on the device can see
 * it. No permissions required on Android 10+ (scoped storage handles it).
 *
 * On pre-Q devices MediaStore.Downloads doesn't exist; callers should fall
 * back to the existing FileProvider share sheet ([WavRecorder.shareFiles]),
 * which lets the user pick "Save to Files" or any other handler.
 */
object RecordingExporter {

    private const val TAG = "RecordingExporter"
    private const val SUBDIR = "Synth Piano"

    /** True if the MediaStore export path is available on this device. */
    val isMediaStoreSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    data class Result(val exported: List<String>, val failed: List<String>) {
        /** At least one file exported. Partial failures still report success. */
        val isSuccess: Boolean get() = exported.isNotEmpty()
    }

    /**
     * Export the WAV at [wavPath] and its sibling `.mid` (if it exists) to
     * `Download/Synth Piano/`. Returns the list of MediaStore display paths
     * actually written, plus any failures. On pre-Q this returns an empty
     * Result — callers must fall back to the share sheet.
     */
    fun exportWavAndMidi(ctx: Context, wavPath: String): Result {
        if (!isMediaStoreSupported) return Result(emptyList(), emptyList())

        val wav = File(wavPath).takeIf { it.exists() && it.isFile }
            ?: return Result(emptyList(), listOf(wavPath))

        val exported = mutableListOf<String>()
        val failed = mutableListOf<String>()

        exportSingle(ctx, wav, mimeFor(wav))?.let { exported += it } ?: run { failed += wav.absolutePath }

        val midi = File(wav.absolutePath.removeSuffix(".wav") + ".mid")
        if (midi.exists() && midi.isFile) {
            exportSingle(ctx, midi, mimeFor(midi))?.let { exported += it } ?: run { failed += midi.absolutePath }
        }

        return Result(exported, failed)
    }

    private fun exportSingle(ctx: Context, file: File, mime: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = ctx.contentResolver
        val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/$SUBDIR"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val itemUri = try {
            resolver.insert(collection, values)
        } catch (t: Throwable) {
            Log.e(TAG, "MediaStore insert failed for ${file.name}", t)
            null
        } ?: return null

        return try {
            resolver.openOutputStream(itemUri, "w")?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: run {
                resolver.delete(itemUri, null, null)
                return null
            }
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(itemUri, done, null, null)
            "$relativePath/${file.name}"
        } catch (t: Throwable) {
            Log.e(TAG, "MediaStore copy failed for ${file.name}", t)
            runCatching { resolver.delete(itemUri, null, null) }
            null
        }
    }

    private fun mimeFor(file: File): String = when (file.extension.lowercase(Locale.US)) {
        "wav" -> "audio/wav"
        "mid", "midi" -> "audio/midi"
        else -> "application/octet-stream"
    }
}
