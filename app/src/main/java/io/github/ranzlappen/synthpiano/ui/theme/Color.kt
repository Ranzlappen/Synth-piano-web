package io.github.ranzlappen.synthpiano.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Four curated dark palettes plus a [SYSTEM] sentinel that defers to the
 * platform's dynamic-color (Material You) scheme on Android 12+.
 *
 * Each accent ships its own background gradient so the workstation surface
 * has a consistent feel regardless of which palette the user picks. Single
 * source of truth: every UI surface reads MaterialTheme + this enum's
 * [gradient] when it wants the page background.
 */
enum class ThemeAccent(
    val displayName: String,
    val gradientTop: Color,
    val gradientBottom: Color,
) {
    AURORA(
        displayName = "Aurora",
        gradientTop = Color(0xFF1A0F2E),
        gradientBottom = Color(0xFF0B1340),
    ),
    SUNSET(
        displayName = "Sunset",
        gradientTop = Color(0xFF2A0F1A),
        gradientBottom = Color(0xFF3A1A0F),
    ),
    MINT(
        displayName = "Mint",
        gradientTop = Color(0xFF0E1F1A),
        gradientBottom = Color(0xFF0B2A2A),
    ),
    MONO(
        displayName = "Mono",
        gradientTop = Color(0xFF14161A),
        gradientBottom = Color(0xFF1F2228),
    ),
    SYSTEM(
        displayName = "System",
        gradientTop = Color(0xFF14161A),
        gradientBottom = Color(0xFF1F2228),
    );

    fun gradient(): Brush = Brush.verticalGradient(listOf(gradientTop, gradientBottom))

    companion object {
        fun fromName(name: String?): ThemeAccent =
            entries.firstOrNull { it.name == name } ?: AURORA
    }
}

internal val AuroraScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB497FF),
    onPrimary = Color(0xFF1B0F35),
    primaryContainer = Color(0xFF4A2F8C),
    onPrimaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFF7DD9FF),
    onSecondary = Color(0xFF002C3D),
    secondaryContainer = Color(0xFF1A4D66),
    onSecondaryContainer = Color(0xFFCDF0FF),
    tertiary = Color(0xFFFFB7D5),
    onTertiary = Color(0xFF3F0E26),
    tertiaryContainer = Color(0xFF6E2A50),
    onTertiaryContainer = Color(0xFFFFE0EC),
    background = Color(0xFF1A0F2E),
    onBackground = Color(0xFFE7E1F4),
    surface = Color(0xFF1F1438),
    onSurface = Color(0xFFE7E1F4),
    surfaceVariant = Color(0xFF2D2350),
    onSurfaceVariant = Color(0xFFC9BFE2),
    outline = Color(0xFF7E76A0),
)

internal val SunsetScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFFFB774),
    onPrimary = Color(0xFF3A1B00),
    primaryContainer = Color(0xFF8C4A14),
    onPrimaryContainer = Color(0xFFFFE0C2),
    secondary = Color(0xFFFF7A8A),
    onSecondary = Color(0xFF400916),
    secondaryContainer = Color(0xFF8C2235),
    onSecondaryContainer = Color(0xFFFFD3D9),
    tertiary = Color(0xFFFFE08A),
    onTertiary = Color(0xFF3A2800),
    tertiaryContainer = Color(0xFF7A5A14),
    onTertiaryContainer = Color(0xFFFFEFCC),
    background = Color(0xFF2A0F1A),
    onBackground = Color(0xFFF5E1D9),
    surface = Color(0xFF351420),
    onSurface = Color(0xFFF5E1D9),
    surfaceVariant = Color(0xFF4A2233),
    onSurfaceVariant = Color(0xFFE0C7B8),
    outline = Color(0xFFA08775),
)

internal val MintScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF6FE3C2),
    onPrimary = Color(0xFF002B22),
    primaryContainer = Color(0xFF146B55),
    onPrimaryContainer = Color(0xFFC2F5E5),
    secondary = Color(0xFFC8F07A),
    onSecondary = Color(0xFF1F3500),
    secondaryContainer = Color(0xFF466B14),
    onSecondaryContainer = Color(0xFFE8FFC2),
    tertiary = Color(0xFF7AD4FF),
    onTertiary = Color(0xFF002A40),
    tertiaryContainer = Color(0xFF14506B),
    onTertiaryContainer = Color(0xFFC2EBFF),
    background = Color(0xFF0E1F1A),
    onBackground = Color(0xFFD9F0E5),
    surface = Color(0xFF142A24),
    onSurface = Color(0xFFD9F0E5),
    surfaceVariant = Color(0xFF1F3D34),
    onSurfaceVariant = Color(0xFFB8E0CF),
    outline = Color(0xFF6E9085),
)

internal val MonoScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFE8E8EE),
    onPrimary = Color(0xFF1A1C20),
    primaryContainer = Color(0xFF3A3D44),
    onPrimaryContainer = Color(0xFFE8E8EE),
    secondary = Color(0xFFB8BCC4),
    onSecondary = Color(0xFF20232A),
    secondaryContainer = Color(0xFF3A3D44),
    onSecondaryContainer = Color(0xFFE0E2E8),
    tertiary = Color(0xFF9AA3B0),
    onTertiary = Color(0xFF1A1C20),
    tertiaryContainer = Color(0xFF3A3D44),
    onTertiaryContainer = Color(0xFFE0E2E8),
    background = Color(0xFF14161A),
    onBackground = Color(0xFFE8E8EE),
    surface = Color(0xFF1A1C20),
    onSurface = Color(0xFFE8E8EE),
    surfaceVariant = Color(0xFF2A2D33),
    onSurfaceVariant = Color(0xFFC2C5CC),
    outline = Color(0xFF7C8088),
)

// Source-coloring for piano keyboard. Each note source gets a distinct hue
// so a player can see at a glance whether a note came from touch, MIDI,
// the score player, or the hardware keyboard.
val SourceTouch = Color(0xFFB497FF)     // primary-ish purple
val SourceMidi = Color(0xFF7DD9FF)      // secondary-ish cyan
val SourceScore = Color(0xFFFFB7D5)     // tertiary pink
val SourceHwKey = Color(0xFFC8F07A)     // mint accent

// Piano key base colors (theme-agnostic; designed to read on every accent).
val KeyWhite = Color(0xFFF8F5F0)
val KeyBlack = Color(0xFF1A1622)
val KeyLabel = Color(0xFF373045)
val KeyDimmed = Color(0xFF8E8A95)       // out-of-scale white-key fill
val KeyDimmedBlack = Color(0xFF3A3340)  // out-of-scale black-key fill

// Legacy aliases — referenced by unmodified files until later phases
// rewrite the keyboard / play screen. Pinned to the new Aurora palette.
val SynthPurple = Color(0xFFB497FF)
val SynthPurpleDark = Color(0xFF4A2F8C)
val SynthAmber = Color(0xFFFFB774)
val SurfaceDark = Color(0xFF1A0F2E)
val SurfaceLight = Color(0xFFF5F2FA)
val KeyWhitePressed = Color(0xFFC9BBE5)
val KeyBlackPressed = Color(0xFF7B5BD6)
