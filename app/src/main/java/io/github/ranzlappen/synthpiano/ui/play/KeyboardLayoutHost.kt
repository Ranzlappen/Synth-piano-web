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
import io.github.ranzlappen.synthpiano.data.ModifierPanel

/**
 * Renders all panels in a [KeyboardLayout] inside a single absolute-
 * positioned container. Each panel — keyboard or modifier — uses
 * fractional coordinates and an independent rotation in 90° increments.
 *
 * The keyboard rendering is owned here; the modifier-strip body is
 * provided by the caller via [modifierContent], so this composable does
 * not need to thread through all of the chord-modifier state.
 */
@Composable
fun KeyboardLayoutHost(
    layout: KeyboardLayout,
    heldBySource: Map<Int, NoteSource>,
    zoom: Float,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit,
    modifierContent: @Composable (ModifierPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val cw = constraints.maxWidth
        val ch = constraints.maxHeight

        layout.panels.forEach { panel ->
            val p = panel.normalized()
            FractionalBox(
                xFraction = p.xFraction,
                yFraction = p.yFraction,
                widthFraction = p.widthFraction,
                heightFraction = p.heightFraction,
                rotationDeg = p.rotationDeg,
                containerW = cw,
                containerH = ch,
            ) {
                val scrollState = rememberScrollState()
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

        layout.modifiers.forEach { mod ->
            val m = mod.normalized()
            FractionalBox(
                xFraction = m.xFraction,
                yFraction = m.yFraction,
                widthFraction = m.widthFraction,
                heightFraction = m.heightFraction,
                rotationDeg = m.rotationDeg,
                containerW = cw,
                containerH = ch,
            ) {
                modifierContent(m)
            }
        }
    }
}

@Composable
private fun FractionalBox(
    xFraction: Float,
    yFraction: Float,
    widthFraction: Float,
    heightFraction: Float,
    rotationDeg: Int,
    containerW: Int,
    containerH: Int,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val xPx = (xFraction * containerW).toInt()
    val yPx = (yFraction * containerH).toInt()
    val wPx = (widthFraction * containerW).toInt()
    val hPx = (heightFraction * containerH).toInt()
    val widthDp = with(density) { wPx.toDp() }
    val heightDp = with(density) { hPx.toDp() }
    Box(
        modifier = Modifier
            .offset { IntOffset(xPx, yPx) }
            .size(width = widthDp, height = heightDp)
            .graphicsLayer { rotationZ = rotationDeg.toFloat() }
            .clip(RoundedCornerShape(8.dp)),
    ) {
        content()
    }
}
