package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.audio.NoteSource
import io.github.ranzlappen.synthpiano.data.KeyboardLayout
import io.github.ranzlappen.synthpiano.data.KeyboardPanel

/**
 * Renders one or more [PianoKeyboard] panels positioned by fractional
 * coordinates inside a single container. Each panel can rotate
 * independently in 90° increments. Panels share the same note callbacks,
 * so multiple panels driving the same synth voice is fine — duplicates are
 * coalesced upstream by `SynthController`.
 */
@Composable
fun KeyboardLayoutHost(
    layout: KeyboardLayout,
    heldBySource: Map<Int, NoteSource>,
    zoom: Float,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val cw = constraints.maxWidth
        val ch = constraints.maxHeight
        val density = LocalDensity.current
        layout.panels.forEach { panel ->
            val p = panel.normalized()
            val xPx = (p.xFraction * cw).toInt()
            val yPx = (p.yFraction * ch).toInt()
            val wPx = (p.widthFraction * cw).toInt()
            val hPx = (p.heightFraction * ch).toInt()
            val widthDp = with(density) { wPx.toDp() }
            val heightDp = with(density) { hPx.toDp() }
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .offset { IntOffset(xPx, yPx) }
                    .size(width = widthDp, height = heightDp)
                    .graphicsLayer { rotationZ = p.rotationDeg.toFloat() }
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                PianoKeyboard(
                    modifier = Modifier.fillMaxSize(),
                    scrollState = scrollState,
                    heldBySource = heldBySource,
                    zoom = zoom,
                    onNoteOn = onNoteOn,
                    onNoteOff = onNoteOff,
                    firstMidi = p.firstMidi,
                    whiteKeyCount = p.whiteKeyCount,
                )
            }
        }
    }
}
