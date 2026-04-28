package io.github.ranzlappen.synthpiano.audio

import kotlinx.serialization.Serializable

/**
 * Mirrors the C++ enum `synthpiano::Waveform`. Order MUST match the native side
 * (see app/src/main/cpp/oscillator.h).
 */
@Serializable
enum class Waveform {
    SINE,
    SQUARE,
    SAW,
    TRIANGLE,
    PIANO;

    fun displayName(): String = when (this) {
        SINE -> "Sine"
        SQUARE -> "Square"
        SAW -> "Saw"
        TRIANGLE -> "Triangle"
        PIANO -> "Piano"
    }
}
