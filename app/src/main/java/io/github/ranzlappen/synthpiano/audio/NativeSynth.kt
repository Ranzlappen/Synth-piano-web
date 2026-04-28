package io.github.ranzlappen.synthpiano.audio

/**
 * Thin JNI wrapper around the native synth engine.
 *
 * Lifecycle: construct exactly once (engine is heap-allocated in C++),
 * call [start] before any note events, call [destroy] on app teardown.
 *
 * All methods are safe to call from any thread; they enqueue into
 * lock-free atomics or SPSC rings on the C++ side.
 */
class NativeSynth {

    private var handle: Long = 0L

    init {
        System.loadLibrary("synthpiano")
        handle = nativeCreate()
        require(handle != 0L) { "native engine allocation failed" }
    }

    fun start(): Boolean = nativeStart(handle)
    fun stop() = nativeStop(handle)

    fun destroy() {
        if (handle != 0L) {
            nativeStop(handle)
            nativeDestroy(handle)
            handle = 0L
        }
    }

    fun noteOn(midiNote: Int, velocity: Float = 1.0f) =
        nativeNoteOn(handle, midiNote, velocity.coerceIn(0f, 1f))

    fun noteOff(midiNote: Int) = nativeNoteOff(handle, midiNote)

    fun allNotesOff() = nativeAllNotesOff(handle)

    fun setWaveform(w: Waveform) = nativeSetWaveform(handle, w.ordinal)

    fun setAdsr(attackSec: Float, decaySec: Float, sustain: Float, releaseSec: Float) =
        nativeSetAdsr(handle, attackSec, decaySec, sustain, releaseSec)

    fun setMasterAmp(amp: Float) =
        nativeSetMasterAmp(handle, amp.coerceIn(0f, 1f))

    fun setEnvelopeCurve(curve: Float) =
        nativeSetEnvelopeCurve(handle, curve.coerceIn(-1f, 1f))

    fun setFilter(cutoffHz: Float, resonance: Float) =
        nativeSetFilter(handle, cutoffHz.coerceIn(20f, 20000f), resonance.coerceIn(0f, 1f))

    fun setVelocitySensitivity(v: Float) =
        nativeSetVelocitySensitivity(handle, v.coerceIn(0f, 1f))

    fun setGlideSec(s: Float) =
        nativeSetGlideSec(handle, s.coerceIn(0f, 0.5f))

    fun sampleRate(): Int = nativeGetSampleRate(handle)

    /** Returns peak magnitude since last call, in [0, 1]. */
    fun masterPeak(): Float = nativeGetMasterPeak(handle)

    fun setRecordingEnabled(on: Boolean) = nativeSetRecordingEnabled(handle, on)

    /**
     * Drains stereo float frames out of the audio thread's recording ring.
     *
     * @param out interleaved L,R,L,R... buffer (size must be even).
     * @param maxFrames cap on stereo frames to drain; 0 means "as many as fit".
     * @return frames actually written (≤ maxFrames, ≤ out.size/2).
     */
    fun drainRecording(out: FloatArray, maxFrames: Int = 0): Int =
        nativeDrainRecording(handle, out, maxFrames)

    fun setScopeEnabled(on: Boolean) = nativeSetScopeEnabled(handle, on)

    /**
     * Drains mono master-mix samples from the oscilloscope tap into [out].
     * @return frames written (≤ maxFrames, ≤ out.size).
     */
    fun drainScope(out: FloatArray, maxFrames: Int = 0): Int =
        nativeDrainScope(handle, out, maxFrames)

    // --- JNI ---

    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)

    private external fun nativeNoteOn(handle: Long, midiNote: Int, velocity: Float)
    private external fun nativeNoteOff(handle: Long, midiNote: Int)
    private external fun nativeAllNotesOff(handle: Long)

    private external fun nativeSetWaveform(handle: Long, type: Int)
    private external fun nativeSetAdsr(
        handle: Long, attack: Float, decay: Float, sustain: Float, release: Float
    )
    private external fun nativeSetMasterAmp(handle: Long, amp: Float)

    private external fun nativeSetEnvelopeCurve(handle: Long, curve: Float)
    private external fun nativeSetFilter(handle: Long, cutoffHz: Float, resonance: Float)
    private external fun nativeSetVelocitySensitivity(handle: Long, v: Float)
    private external fun nativeSetGlideSec(handle: Long, s: Float)

    private external fun nativeGetSampleRate(handle: Long): Int
    private external fun nativeGetMasterPeak(handle: Long): Float

    private external fun nativeSetRecordingEnabled(handle: Long, on: Boolean)
    private external fun nativeDrainRecording(handle: Long, out: FloatArray, maxFrames: Int): Int

    private external fun nativeSetScopeEnabled(handle: Long, on: Boolean)
    private external fun nativeDrainScope(handle: Long, out: FloatArray, maxFrames: Int): Int
}
