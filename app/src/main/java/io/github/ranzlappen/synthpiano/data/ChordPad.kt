package io.github.ranzlappen.synthpiano.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ChordQuality {
    MAJ, MIN, SEVEN, DIM, SUS;

    fun label(): String = when (this) {
        MAJ -> "Maj"
        MIN -> "Min"
        SEVEN -> "7"
        DIM -> "Dim"
        SUS -> "Sus"
    }
}

@Serializable
data class ChordPad(
    val rootNote: Int = 60,            // MIDI note for the chord root
    val quality: ChordQuality = ChordQuality.MAJ,
) {
    fun label(): String {
        val pitchClass = ((rootNote % 12) + 12) % 12
        val noteName = NOTE_NAMES[pitchClass]
        return "$noteName${quality.label()}"
    }
}

private val NOTE_NAMES =
    listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Semitone intervals from the root, including the root (0). */
fun intervalsFor(q: ChordQuality): List<Int> = when (q) {
    ChordQuality.MAJ -> listOf(0, 4, 7)
    ChordQuality.MIN -> listOf(0, 3, 7)
    ChordQuality.SEVEN -> listOf(0, 4, 7, 10)
    ChordQuality.DIM -> listOf(0, 3, 6)
    ChordQuality.SUS -> listOf(0, 5, 7) // sus4
}

/**
 * Default 11-pad layout, roughly mirroring the Python source's chord row:
 * a sweep of common roots in C major. Users can long-press to remap.
 */
fun defaultChordPads(): List<ChordPad> = listOf(
    ChordPad(60, ChordQuality.MAJ), // C
    ChordPad(62, ChordQuality.MIN), // Dm
    ChordPad(64, ChordQuality.MIN), // Em
    ChordPad(65, ChordQuality.MAJ), // F
    ChordPad(67, ChordQuality.MAJ), // G
    ChordPad(67, ChordQuality.SEVEN), // G7
    ChordPad(69, ChordQuality.MIN), // Am
    ChordPad(71, ChordQuality.DIM), // Bdim
    ChordPad(72, ChordQuality.MAJ), // C (octave up)
    ChordPad(65, ChordQuality.SUS), // Fsus
    ChordPad(67, ChordQuality.SUS), // Gsus
)

private val padsJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun parseChordPadsJson(s: String): List<ChordPad> =
    padsJson.decodeFromString<List<ChordPad>>(s)

fun List<ChordPad>.toJson(): String =
    padsJson.encodeToString(kotlinx.serialization.builtins.ListSerializer(ChordPad.serializer()), this)
