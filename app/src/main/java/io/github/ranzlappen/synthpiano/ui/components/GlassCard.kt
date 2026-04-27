package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Translucent surface card with a subtle vertical sheen and a soft border.
 *
 * The DAW workstation uses a single visual primitive for every control
 * cluster (oscillator, ADSR, chord pads, recording HUD, etc.). Centralising
 * it here keeps spacing, corner radius, and contrast consistent across
 * tabs.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    cornerRadius: Dp = 18.dp,
    content: @Composable () -> Unit,
) {
    val sheen = Brush.verticalGradient(
        listOf(
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.02f),
        )
    )
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .background(sheen)
            .border(BorderStroke(1.dp, outline), shape)
            .padding(contentPadding),
    ) {
        content()
    }
}
