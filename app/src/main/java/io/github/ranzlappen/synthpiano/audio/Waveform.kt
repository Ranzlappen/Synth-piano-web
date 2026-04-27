package io.github.ranzlappen.synthpiano.audio

/**
 * Mirrors the C++ enum `synthpiano::Waveform`. Order MUST match the native side
 * (see app/src/main/cpp/oscillator.h).
 */
enum class Waveform {
    SINE,
    SQUARE,
    SAW,
    TRIANGLE;

    fun displayName(): String = when (this) {
        SINE -> "Sine"
        SQUARE -> "Square"
        SAW -> "Saw"
        TRIANGLE -> "Triangle"
    }
}
