package io.github.ranzlappen.synthpiano.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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

/**
 * The chord-modifier strip (LOCK + SHIFT rows + zoom buttons) hosted as a
 * draggable container. Toggles let the user hide the inversion column or
 * the zoom column on smaller layouts. Position/size/rotation match
 * [KeyboardPanel].
 */
@Serializable
data class ModifierPanel(
    val id: String = UUID.randomUUID().toString(),
    val xFraction: Float = 0f,
    val yFraction: Float = 0f,
    val widthFraction: Float = 1f,
    val heightFraction: Float = 0.25f,
    val rotationDeg: Int = 0,
    val showLock: Boolean = true,
    val showShift: Boolean = true,
    val showZoom: Boolean = true,
    val qualities: List<ChordQuality> = ChordQuality.entries.toList(),
    val inversions: List<ChordInversion> = DEFAULT_INVERSIONS,
) {
    fun normalized(): ModifierPanel {
        val q = qualities.distinct().filter { it in ChordQuality.entries }
        val i = inversions.distinct().filter { it != ChordInversion.NONE }
        return copy(
            xFraction = xFraction.coerceIn(0f, 1f - MIN_W),
            yFraction = yFraction.coerceIn(0f, 1f - MIN_H),
            widthFraction = widthFraction.coerceIn(MIN_W, 1f),
            heightFraction = heightFraction.coerceIn(MIN_H, 1f),
            rotationDeg = ((rotationDeg % 360) + 360) % 360,
            qualities = q.ifEmpty { ChordQuality.entries.toList() },
            inversions = i.ifEmpty { DEFAULT_INVERSIONS },
        )
    }

    companion object {
        const val MIN_W = 0.25f
        const val MIN_H = 0.08f
        val DEFAULT_INVERSIONS: List<ChordInversion> =
            listOf(ChordInversion.FIRST, ChordInversion.SECOND, ChordInversion.THIRD)
    }
}

@Serializable
data class KeyboardLayout(
    val name: String,
    val panels: List<KeyboardPanel> = emptyList(),
    val modifiers: List<ModifierPanel> = emptyList(),
    val builtin: Boolean = false,
)

object BuiltInLayouts {

    val DEFAULT_MODIFIER: ModifierPanel = ModifierPanel(
        id = "default-mods",
        xFraction = 0f,
        yFraction = 0f,
        widthFraction = 1f,
        heightFraction = 0.30f,
        rotationDeg = 0,
        showLock = true,
        showShift = true,
        showZoom = true,
    )

    val DEFAULT: KeyboardLayout = KeyboardLayout(
        name = "Default",
        panels = listOf(
            KeyboardPanel(
                id = "default",
                xFraction = 0f, yFraction = 0.30f,
                widthFraction = 1f, heightFraction = 0.70f,
                rotationDeg = 0,
                firstMidi = 21, whiteKeyCount = 52,
            ),
        ),
        modifiers = listOf(DEFAULT_MODIFIER),
        builtin = true,
    )

    /** Two side-by-side ~3-octave panels under a slim modifier strip. */
    val THUMB_FRIENDLY: KeyboardLayout = KeyboardLayout(
        name = "Thumb-Friendly",
        panels = listOf(
            KeyboardPanel(
                id = "thumb-left",
                xFraction = 0f, yFraction = 0.24f,
                widthFraction = 0.48f, heightFraction = 0.76f,
                rotationDeg = 0,
                firstMidi = 48, whiteKeyCount = 21,  // C3..A5 white keys
            ),
            KeyboardPanel(
                id = "thumb-right",
                xFraction = 0.52f, yFraction = 0.24f,
                widthFraction = 0.48f, heightFraction = 0.76f,
                rotationDeg = 0,
                firstMidi = 60, whiteKeyCount = 21,  // C4..A6 white keys
            ),
        ),
        modifiers = listOf(
            DEFAULT_MODIFIER.copy(
                id = "thumb-mods",
                heightFraction = 0.22f,
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
    val normKbs = raw.panels.map { it.normalized() }
    val normMods = raw.modifiers.map { it.normalized() }
    if (normMods.isEmpty()) {
        // Migration: layouts saved before modifier panels existed had
        // keyboards filling the whole container. Insert the default
        // modifier strip on top and shrink the keyboards to 78% height.
        val modH = BuiltInLayouts.DEFAULT_MODIFIER.heightFraction
        val migratedKbs = normKbs.map { p ->
            p.copy(
                yFraction = (p.yFraction * (1f - modH)) + modH,
                heightFraction = (p.heightFraction * (1f - modH)),
            ).normalized()
        }
        raw.copy(
            panels = migratedKbs,
            modifiers = listOf(BuiltInLayouts.DEFAULT_MODIFIER),
        )
    } else {
        raw.copy(panels = normKbs, modifiers = normMods)
    }
}.getOrNull()

private val layoutListSerializer = ListSerializer(KeyboardLayout.serializer())

fun List<KeyboardLayout>.toLayoutListJson(): String =
    layoutJson.encodeToString(layoutListSerializer, this)

fun parseKeyboardLayoutListJson(text: String): List<KeyboardLayout> = runCatching {
    layoutJson.decodeFromString(layoutListSerializer, text).map { raw ->
        val normKbs = raw.panels.map { it.normalized() }
        val normMods = raw.modifiers.map { it.normalized() }
        raw.copy(panels = normKbs, modifiers = normMods, builtin = false)
    }
}.getOrDefault(emptyList())
