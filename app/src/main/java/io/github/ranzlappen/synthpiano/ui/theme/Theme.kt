package io.github.ranzlappen.synthpiano.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkScheme = darkColorScheme(
    primary = SynthPurple,
    onPrimary = SurfaceDark,
    secondary = SynthAmber,
    background = SurfaceDark,
    surface = SurfaceDark,
)

private val LightScheme = lightColorScheme(
    primary = SynthPurpleDark,
    onPrimary = SurfaceLight,
    secondary = SynthAmber,
    background = SurfaceLight,
    surface = SurfaceLight,
)

@Composable
fun SynthPianoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = SynthTypography,
        content = content,
    )
}
