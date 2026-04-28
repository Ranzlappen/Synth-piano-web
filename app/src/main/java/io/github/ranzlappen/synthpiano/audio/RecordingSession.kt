package io.github.ranzlappen.synthpiano.audio

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Session-level wrapper around [WavRecorder] that exposes StateFlow-driven
 * recording status to the Compose UI. The session is meant to be hoisted
 * once at the root of the workstation so every tab observes the same
 * recording chip state.
 */
class RecordingSession(
    private val recorder: WavRecorder,
    private val scoreRecorder: ScoreRecorder,
    private val scope: CoroutineScope,
) {
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _lastPath = MutableStateFlow<String?>(null)
    val lastPath: StateFlow<String?> = _lastPath.asStateFlow()

    private val _lastScorePath = MutableStateFlow<String?>(null)
    val lastScorePath: StateFlow<String?> = _lastScorePath.asStateFlow()

    private var tickJob: Job? = null

    fun toggle(ctx: Context) {
        if (_isRecording.value) stop() else start(ctx)
    }

    fun start(ctx: Context) {
        if (_isRecording.value) return
        val path = recorder.start(ctx) ?: return
        _lastPath.value = path
        scoreRecorder.start(scoreSiblingPath(path))
        _isRecording.value = true
        _elapsedMs.value = 0L
        val startedAt = System.currentTimeMillis()
        tickJob = scope.launch {
            while (isActive && _isRecording.value) {
                _elapsedMs.value = System.currentTimeMillis() - startedAt
                delay(250L)
            }
        }
    }

    fun stop() {
        if (!_isRecording.value) return
        recorder.stop()
        val title = _lastPath.value?.let { File(it).nameWithoutExtension }
        _lastScorePath.value = scoreRecorder.stop(title = title)
        _isRecording.value = false
        tickJob?.cancel()
        tickJob = null
    }

    fun shareLast(ctx: Context) {
        _lastPath.value?.let { recorder.share(ctx, it) }
    }

    private fun scoreSiblingPath(wavPath: String): String =
        if (wavPath.endsWith(".wav", ignoreCase = true)) wavPath.dropLast(4) + ".json"
        else "$wavPath.json"
}
