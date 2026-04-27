package io.github.ranzlappen.synthpiano.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The current accent is read by composables (e.g. for the page gradient
 * background) without having to thread it through every call site.
 */
val LocalThemeAccent = staticCompositionLocalOf { ThemeAccent.AURORA }

/**
 * Always dark. The Synth Piano is a performance surface — bright modes
 * blow out the gradient and the glow on active controls. Accent picks the
 * scheme; SYSTEM defers to Android 12+ Material You.
 */
@Composable
fun SynthPianoTheme(
    accent: ThemeAccent = ThemeAccent.AURORA,
    content: @Composable () -> Unit,
) {
    val scheme = when (accent) {
        ThemeAccent.AURORA -> AuroraScheme
        ThemeAccent.SUNSET -> SunsetScheme
        ThemeAccent.MINT -> MintScheme
        ThemeAccent.MONO -> MonoScheme
        ThemeAccent.SYSTEM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicDarkColorScheme(LocalContext.current)
        } else {
            AuroraScheme
        }
    }

    CompositionLocalProvider(LocalThemeAccent provides accent) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SynthTypography,
            content = content,
        )
    }
}
