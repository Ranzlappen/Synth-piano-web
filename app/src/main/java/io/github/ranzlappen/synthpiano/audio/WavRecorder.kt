package io.github.ranzlappen.synthpiano.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Drains stereo float frames from the native engine's recording ring and
 * writes a 16-bit PCM WAV file to app-specific storage. Lock-free: the
 * audio thread writes the ring; this class reads on a background thread.
 *
 * Files land in `<filesDir>/recordings/synth_<timestamp>.wav` and are
 * shared via FileProvider with authority `${packageName}.fileprovider`.
 */
class WavRecorder(private val synth: SynthController) {

    @Volatile private var running = false
    private var writer: Thread? = null
    private var currentFile: File? = null

    /** Begins recording; returns the absolute path of the WAV file or null on error. */
    fun start(ctx: Context): String? {
        if (running) return currentFile?.absolutePath
        val dir = File(ctx.filesDir, "recordings").apply { mkdirs() }
        val name = "synth_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.wav"
        val file = File(dir, name)
        currentFile = file

        val sampleRate = synth.engine().sampleRate().takeIf { it > 0 } ?: 48000
        val raf = try {
            RandomAccessFile(file, "rw").apply { writeWavHeader(this, sampleRate) }
        } catch (t: Throwable) {
            return null
        }

        running = true
        synth.engine().setRecordingEnabled(true)

        writer = thread(name = "WavRecorder", isDaemon = true) {
            val drainBufFrames = 2048
            val floatBuf = FloatArray(drainBufFrames * 2)
            val bytes = ByteBuffer.allocate(drainBufFrames * 2 * 2).order(ByteOrder.LITTLE_ENDIAN)
            var totalBytes = 0L
            try {
                while (running) {
                    val n = synth.engine().drainRecording(floatBuf, drainBufFrames)
                    if (n > 0) {
                        bytes.clear()
                        for (i in 0 until n * 2) {
                            val s = (floatBuf[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                            bytes.putShort(s)
                        }
                        bytes.flip()
                        val arr = ByteArray(bytes.remaining())
                        bytes.get(arr)
                        raf.write(arr)
                        totalBytes += arr.size
                    } else {
                        Thread.sleep(5)
                    }
                }
            } catch (_: InterruptedException) {
                // expected on stop
            } finally {
                try { finalizeWavHeader(raf, totalBytes) } catch (_: Throwable) {}
                try { raf.close() } catch (_: Throwable) {}
            }
        }
        return file.absolutePath
    }

    fun stop() {
        if (!running) return
        running = false
        synth.engine().setRecordingEnabled(false)
        writer?.join(500)
        writer = null
    }

    fun share(ctx: Context, path: String) {
        val file = File(path)
        if (!file.exists()) return
        val authority = "${ctx.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(ctx, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(
            Intent.createChooser(intent, "Share recording")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun writeWavHeader(raf: RandomAccessFile, sampleRate: Int) {
        // Standard 44-byte WAVE/PCM/16-bit/stereo header. We patch sizes on stop().
        val channels = 2
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(0)                                  // chunkSize, patched
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)                                 // PCM subchunk size
            putShort(1)                                // audioFormat = PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(0)                                  // dataSize, patched
        }
        raf.write(header.array())
    }

    private fun finalizeWavHeader(raf: RandomAccessFile, dataBytes: Long) {
        val totalSize = dataBytes + 36
        raf.seek(4)
        raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalSize.toInt()).array())
        raf.seek(40)
        raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataBytes.toInt()).array())
    }
}
