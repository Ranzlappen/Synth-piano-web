package io.github.ranzlappen.synthpiano.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * One on-screen keyboard within the play area. Position and size are
 * expressed as fractions of the host container so the layout is
 * resolution-independent. Rotation snaps to 0/90/180/270 degrees.
 *
 * `firstMidi` and `whiteKeyCount` choose which slice of the piano this
 * panel renders, letting a thumb-friendly layout show two ~3-octave panels
 * side by side, etc.
 */
@Serializable
data class KeyboardPanel(
    val id: String = UUID.randomUUID().toString(),
    val xFraction: Float = 0f,
    val yFraction: Float = 0f,
    val widthFraction: Float = 1f,
    val heightFraction: Float = 1f,
    val rotationDeg: Int = 0,
    val firstMidi: Int = 21,
    val whiteKeyCount: Int = 52,
) {
    fun normalized(): KeyboardPanel = copy(
        xFraction = xFraction.coerceIn(0f, 1f - MIN_W),
        yFraction = yFraction.coerceIn(0f, 1f - MIN_H),
        widthFraction = widthFraction.coerceIn(MIN_W, 1f),
        heightFraction = heightFraction.coerceIn(MIN_H, 1f),
        rotationDeg = ((rotationDeg % 360) + 360) % 360,
        firstMidi = firstMidi.coerceIn(0, 108),
        whiteKeyCount = whiteKeyCount.coerceIn(7, 52),
    )

    companion object {
        const val MIN_W = 0.20f
        const val MIN_H = 0.30f
    }
}

@Serializable
data class KeyboardLayout(
    val name: String,
    val panels: List<KeyboardPanel>,
    val builtin: Boolean = false,
)

object BuiltInLayouts {

    val DEFAULT: KeyboardLayout = KeyboardLayout(
        name = "Default",
        panels = listOf(
            KeyboardPanel(
                id = "default",
                xFraction = 0f, yFraction = 0f,
                widthFraction = 1f, heightFraction = 1f,
                rotationDeg = 0,
                firstMidi = 21, whiteKeyCount = 52,
            ),
        ),
        builtin = true,
    )

    /** Two side-by-side ~3-octave panels with a small center gutter. */
    val THUMB_FRIENDLY: KeyboardLayout = KeyboardLayout(
        name = "Thumb-Friendly",
        panels = listOf(
            KeyboardPanel(
                id = "thumb-left",
                xFraction = 0f, yFraction = 0.05f,
                widthFraction = 0.48f, heightFraction = 0.95f,
                rotationDeg = 0,
                firstMidi = 48, whiteKeyCount = 21,  // C3..A5 white keys
            ),
            KeyboardPanel(
                id = "thumb-right",
                xFraction = 0.52f, yFraction = 0.05f,
                widthFraction = 0.48f, heightFraction = 0.95f,
                rotationDeg = 0,
                firstMidi = 60, whiteKeyCount = 21,  // C4..A6 white keys
            ),
        ),
        builtin = true,
    )

    val ALL: List<KeyboardLayout> = listOf(DEFAULT, THUMB_FRIENDLY)
}

private val layoutJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun KeyboardLayout.toJsonString(): String =
    layoutJson.encodeToString(KeyboardLayout.serializer(), this)

fun parseKeyboardLayoutJson(text: String): KeyboardLayout? = runCatching {
    val raw = layoutJson.decodeFromString(KeyboardLayout.serializer(), text)
    raw.copy(panels = raw.panels.map { it.normalized() })
}.getOrNull()
