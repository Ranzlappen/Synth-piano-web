package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
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
 *
 * [pianoScrollFor] returns the horizontal [ScrollState] for a given
 * keyboard panel id; the caller persists each panel's scroll position
 * independently via DataStore.
 */
@Composable
fun KeyboardLayoutHost(
    layout: KeyboardLayout,
    heldBySource: Map<Int, NoteSource>,
    zoomFor: (String) -> Float,
    onZoomIn: (String) -> Unit,
    onZoomOut: (String) -> Unit,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit,
    modifierContent: @Composable (ModifierPanel) -> Unit,
    pianoScrollFor: (String) -> ScrollState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val cw = constraints.maxWidth
        val ch = constraints.maxHeight

        layout.panels.forEach { panel ->
            val p = panel.normalized()
            val panelZoom = zoomFor(p.id)
            FractionalBox(
                xFraction = p.xFraction,
                yFraction = p.yFraction,
                widthFraction = p.widthFraction,
                heightFraction = p.heightFraction,
                rotationDeg = p.rotationDeg,
                containerW = cw,
                containerH = ch,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    PianoKeyboard(
                        modifier = Modifier.fillMaxSize(),
                        scrollState = pianoScrollFor(p.id),
                        heldBySource = heldBySource,
                        zoom = panelZoom,
                        onNoteOn = onNoteOn,
                        onNoteOff = onNoteOff,
                        firstMidi = p.firstMidi,
                        whiteKeyCount = p.whiteKeyCount,
                    )
                    PerKeyboardZoomOverlay(
                        zoom = panelZoom,
                        onZoomIn = { onZoomIn(p.id) },
                        onZoomOut = { onZoomOut(p.id) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    )
                }
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

/**
 * Tiny floating zoom-control overlay anchored to a keyboard panel's
 * top-right corner. Sized small so it covers minimal black-key area.
 */
@Composable
private fun PerKeyboardZoomOverlay(
    zoom: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
        horizontalArrangement = Arrangement.spacedBy(0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onZoomOut,
            enabled = zoom > ZOOM_MIN + 1e-3f,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.score_zoom_out),
                modifier = Modifier.size(16.dp),
            )
        }
        IconButton(
            onClick = onZoomIn,
            enabled = zoom < ZOOM_MAX - 1e-3f,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.score_zoom_in),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
