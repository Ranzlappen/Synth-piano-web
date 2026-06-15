package io.github.ranzlappen.synthpiano.ui.dj

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Which of the two decks. */
enum class DeckId { A, B }

/**
 * Observable per-deck playback state. Plain Compose snapshot-state holder —
 * the app uses no ViewModels (mirrors [io.github.ranzlappen.synthpiano.ui.score.AppScoreState]).
 *
 * The audio itself runs on an Android [MediaPlayer] owned by [DjEngine],
 * entirely separate from the Oboe/C++ synth engine and its JNI bridge.
 */
class DeckState {
    var title: String? by mutableStateOf(null)
    var durationMs: Int by mutableIntStateOf(0)
    var positionMs: Int by mutableIntStateOf(0)
    var isPlaying: Boolean by mutableStateOf(false)
    var isPrepared: Boolean by mutableStateOf(false)
    var cueMs: Int by mutableIntStateOf(0)

    /** Pitch deviation in percent, [-PITCH_RANGE]..[+PITCH_RANGE]. */
    var pitchPercent: Float by mutableFloatStateOf(0f)

    /** Per-deck fader, 0..1 (before crossfader + master scaling). */
    var volume: Float by mutableFloatStateOf(1f)
}

/**
 * Two-deck DJ playback engine backed by two independent [MediaPlayer]
 * instances (one per deck). Completely decoupled from the synth: it never
 * touches `NativeSynth`, Oboe, or the audio-thread ring.
 *
 * Real-time pitch/speed comes from [MediaPlayer.setPlaybackParams]; the
 * crossfader and master volume drive [MediaPlayer.setVolume] per deck.
 */
class DjEngine(private val appContext: Context) {

    val deckA = DeckState()
    val deckB = DeckState()

    /** Crossfader position: 0 = full A, 1 = full B. Equal-power blend. */
    var crossfader: Float
        get() = _crossfader
        set(value) {
            _crossfader = value.coerceIn(0f, 1f)
            applyVolume(DeckId.A)
            applyVolume(DeckId.B)
        }
    private var _crossfader: Float by mutableFloatStateOf(0.5f)

    /** Master output level, 0..1, applied on top of each deck volume. */
    var master: Float
        get() = _master
        set(value) {
            _master = value.coerceIn(0f, 1f)
            applyVolume(DeckId.A)
            applyVolume(DeckId.B)
        }
    private var _master: Float by mutableFloatStateOf(1f)

    private var playerA: MediaPlayer? = null
    private var playerB: MediaPlayer? = null

    private fun stateOf(id: DeckId): DeckState = if (id == DeckId.A) deckA else deckB
    private fun playerOf(id: DeckId): MediaPlayer? = if (id == DeckId.A) playerA else playerB
    private fun setPlayer(id: DeckId, mp: MediaPlayer?) {
        if (id == DeckId.A) playerA = mp else playerB = mp
    }

    /** Load [uri] into [id], replacing whatever was there. Prepares async. */
    fun load(id: DeckId, uri: Uri, displayName: String? = null) {
        releasePlayer(id)
        val state = stateOf(id)
        state.isPrepared = false
        state.isPlaying = false
        state.positionMs = 0
        state.durationMs = 0
        state.cueMs = 0
        state.title = displayName ?: queryDisplayName(uri) ?: uri.lastPathSegment

        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(appContext, uri)
            mp.setOnPreparedListener {
                state.durationMs = it.duration.coerceAtLeast(0)
                state.isPrepared = true
                applyVolume(id)
            }
            mp.setOnCompletionListener {
                state.isPlaying = false
                state.positionMs = state.durationMs
            }
            mp.setOnErrorListener { _, _, _ ->
                state.isPrepared = false
                state.isPlaying = false
                true
            }
            mp.prepareAsync()
            setPlayer(id, mp)
        }.onFailure {
            mp.release()
            state.title = null
            state.isPrepared = false
        }
    }

    fun playPause(id: DeckId) {
        val mp = playerOf(id) ?: return
        val state = stateOf(id)
        if (!state.isPrepared) return
        runCatching {
            if (state.isPlaying) {
                mp.pause()
                state.isPlaying = false
            } else {
                applySpeed(mp, state)
                mp.start()
                state.isPlaying = true
            }
        }
    }

    /**
     * Standard CDJ-style cue, single button:
     *  - while paused: set the cue point at the current position;
     *  - while playing: jump back to the cue point and pause there.
     */
    fun cue(id: DeckId) {
        val mp = playerOf(id) ?: return
        val state = stateOf(id)
        if (!state.isPrepared) return
        if (state.isPlaying) {
            runCatching { mp.pause() }
            state.isPlaying = false
            seekTo(id, state.cueMs)
        } else {
            state.cueMs = state.positionMs
        }
    }

    fun seekTo(id: DeckId, ms: Int) {
        val mp = playerOf(id) ?: return
        val state = stateOf(id)
        if (!state.isPrepared) return
        val clamped = ms.coerceIn(0, state.durationMs)
        runCatching { mp.seekTo(clamped) }
        state.positionMs = clamped
    }

    /** Jog-wheel scratch: nudge the playhead by [deltaMs] (may be negative). */
    fun nudge(id: DeckId, deltaMs: Int) =
        seekTo(id, stateOf(id).positionMs + deltaMs)

    fun setPitchPercent(id: DeckId, percent: Float) {
        val state = stateOf(id)
        state.pitchPercent = percent.coerceIn(-PITCH_RANGE, PITCH_RANGE)
        val mp = playerOf(id) ?: return
        if (state.isPrepared && state.isPlaying) applySpeed(mp, state)
    }

    fun setVolume(id: DeckId, v: Float) {
        stateOf(id).volume = v.coerceIn(0f, 1f)
        applyVolume(id)
    }

    /** Refresh playhead positions from the players. Called from a UI poll loop. */
    fun poll() {
        pollDeck(DeckId.A)
        pollDeck(DeckId.B)
    }

    private fun pollDeck(id: DeckId) {
        val mp = playerOf(id) ?: return
        val state = stateOf(id)
        if (state.isPrepared && state.isPlaying) {
            runCatching { state.positionMs = mp.currentPosition.coerceIn(0, state.durationMs) }
        }
    }

    /** Pause both decks (app went to background). */
    fun pauseAll() {
        listOf(DeckId.A, DeckId.B).forEach { id ->
            val mp = playerOf(id) ?: return@forEach
            val state = stateOf(id)
            if (state.isPlaying) {
                runCatching { mp.pause() }
                state.isPlaying = false
            }
        }
    }

    /** Release both players. Call from onDispose. */
    fun release() {
        releasePlayer(DeckId.A)
        releasePlayer(DeckId.B)
    }

    private fun releasePlayer(id: DeckId) {
        playerOf(id)?.let { mp -> runCatching { mp.release() } }
        setPlayer(id, null)
    }

    private fun applySpeed(mp: MediaPlayer, state: DeckState) {
        if (!state.isPrepared) return
        val speed = 1f + state.pitchPercent / 100f
        // Setting params on a paused player can auto-start it; we only call
        // this around start(), so any side-effect start is the intended one.
        runCatching { mp.playbackParams = mp.playbackParams.setSpeed(speed) }
    }

    private fun applyVolume(id: DeckId) {
        val mp = playerOf(id) ?: return
        val state = stateOf(id)
        val gain = (state.volume * master * crossfadeGain(id)).coerceIn(0f, 1f)
        runCatching { mp.setVolume(gain, gain) }
    }

    /** Equal-power crossfade gain for [id] given the current [crossfader]. */
    private fun crossfadeGain(id: DeckId): Float {
        val theta = (crossfader * PI / 2.0).toFloat()
        return if (id == DeckId.A) cos(theta) else sin(theta)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        appContext.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    companion object {
        /** Maximum pitch deviation in percent (professional ±16% range). */
        const val PITCH_RANGE: Float = 16f
    }
}
