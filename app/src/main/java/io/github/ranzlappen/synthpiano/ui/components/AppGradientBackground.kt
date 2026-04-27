package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.ranzlappen.synthpiano.ui.theme.LocalThemeAccent

/**
 * Page-level vertical gradient sourced from the active [ThemeAccent].
 *
 * Wraps the entire workstation; every glass card on top reads as a
 * frosted layer over this background.
 */
@Composable
fun AppGradientBackground(content: @Composable () -> Unit) {
    val accent = LocalThemeAccent.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(accent.gradient()),
    ) {
        content()
    }
}
