package io.github.ranzlappen.synthpiano.data

/**
 * Diatonic scale highlight set used by the piano view to dim
 * out-of-scale keys. [pitchClasses] holds the seven pitch classes that
 * make up the scale (0..11). [NONE] disables the highlight.
 *
 * Add more scales by appending entries here — the picker UI is fed
 * directly from [Scale.entries].
 */
enum class Scale(
    val displayName: String,
    val pitchClasses: Set<Int>?,
) {
    NONE("None", null),
    C_MAJOR("C major", setOf(0, 2, 4, 5, 7, 9, 11)),
    A_MINOR("A minor", setOf(9, 11, 0, 2, 4, 5, 7)),
    G_MAJOR("G major", setOf(7, 9, 11, 0, 2, 4, 6)),
    E_MINOR("E minor", setOf(4, 6, 7, 9, 11, 0, 2)),
    D_MAJOR("D major", setOf(2, 4, 6, 7, 9, 11, 1)),
    B_MINOR("B minor", setOf(11, 1, 2, 4, 6, 7, 9)),
    F_MAJOR("F major", setOf(5, 7, 9, 10, 0, 2, 4)),
    D_MINOR("D minor", setOf(2, 4, 5, 7, 9, 10, 0));

    fun contains(midi: Int): Boolean {
        val pc = ((midi % 12) + 12) % 12
        return pitchClasses == null || pc in pitchClasses
    }

    companion object {
        fun fromName(name: String?): Scale =
            entries.firstOrNull { it.name == name } ?: NONE
    }
}
