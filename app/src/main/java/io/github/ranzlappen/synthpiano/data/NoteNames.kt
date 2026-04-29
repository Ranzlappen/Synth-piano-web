package io.github.ranzlappen.synthpiano.data

private val NOTE_PC = mapOf(
    "C" to 0, "C#" to 1, "DB" to 1,
    "D" to 2, "D#" to 3, "EB" to 3,
    "E" to 4, "FB" to 4,
    "F" to 5, "F#" to 6, "GB" to 6,
    "G" to 7, "G#" to 8, "AB" to 8,
    "A" to 9, "A#" to 10, "BB" to 10,
    "B" to 11, "CB" to 11,
)

private val NAMES_SHARP = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/**
 * Convert a note name like "A4", "C#5", or "Bb3" to a MIDI number, or null
 * if unparseable. MIDI 60 = C4 (middle C). Sharps and flats both accepted;
 * a missing octave defaults to 4.
 *
 * Used by the keyboard-layout and keymap editors to label keys, and by
 * the legacy-asset adapter to decode note names from the bundled JSON
 * source format.
 */
fun noteNameToMidi(name: String): Int? {
    val s = name.trim().uppercase()
    if (s.isEmpty()) return null
    var i = 1
    if (i < s.length && (s[i] == '#' || s[i] == 'B')) i++
    val pcKey = s.substring(0, i)
    val pc = NOTE_PC[pcKey] ?: return null
    val octStr = s.substring(i).ifEmpty { "4" }
    val oct = octStr.toIntOrNull() ?: return null
    return (oct + 1) * 12 + pc
}

fun midiToNoteName(midi: Int): String {
    val pc = ((midi % 12) + 12) % 12
    val oct = midi / 12 - 1
    return "${NAMES_SHARP[pc]}$oct"
}
