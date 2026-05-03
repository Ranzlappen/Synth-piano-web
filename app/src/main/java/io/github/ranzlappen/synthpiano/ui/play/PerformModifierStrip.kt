package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.data.ChordInversion
import io.github.ranzlappen.synthpiano.data.ChordQuality
import io.github.ranzlappen.synthpiano.ui.components.GlassCard

/**
 * The chord-modifier strip pinned to a layout panel: Sticky (was "LOCK")
 * and Momentary (was "SHIFT") chord-modifier rows plus zoom +/- buttons.
 *
 * The two row variants are now distinguished by colour rather than text
 * labels — see [PillVariantLegend] at the top of the strip for the
 * mapping. Pill heights scale to fill the available container vertical
 * space, so the strip never needs to scroll: it gets taller pills when
 * given more room and tighter pills when squeezed.
 */
@Composable
fun PerformModifierStrip(
    qualities: List<ChordQuality>,
    inversions: List<ChordInversion>,
    sticky: Set<ChordQuality>,
    stickyInv: ChordInversion,
    held: Set<ChordQuality>,
    heldInv: ChordInversion,
    zoom: Float,
    showLock: Boolean,
    showShift: Boolean,
    showZoom: Boolean,
    onStickyToggle: (ChordQuality) -> Unit,
    onStickyInvToggle: (ChordInversion) -> Unit,
    onShiftPress: (ChordQuality) -> Unit,
    onShiftRelease: (ChordQuality) -> Unit,
    onShiftInvPress: (ChordInversion) -> Unit,
    onShiftInvRelease: (ChordInversion) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showLock) {
                    ChordModifierRow(
                        variant = PillVariant.Sticky,
                        qualities = qualities,
                        inversions = inversions,
                        selected = sticky,
                        inversion = stickyInv,
                        modifier = Modifier.weight(1f),
                        onToggle = onStickyToggle,
                        onInversionToggle = onStickyInvToggle,
                    )
                }
                if (showShift) {
                    ChordModifierRow(
                        variant = PillVariant.Momentary,
                        qualities = qualities,
                        inversions = inversions,
                        selected = held,
                        inversion = heldInv,
                        modifier = Modifier.weight(1f),
                        onPress = onShiftPress,
                        onRelease = onShiftRelease,
                        onInversionPress = onShiftInvPress,
                        onInversionRelease = onShiftInvRelease,
                    )
                }
            }
            if (showZoom) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconButton(
                        onClick = onZoomIn,
                        enabled = zoom < ZOOM_MAX - 1e-3f,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.score_zoom_in),
                        )
                    }
                    IconButton(
                        onClick = onZoomOut,
                        enabled = zoom > ZOOM_MIN + 1e-3f,
                    ) {
                        Icon(
                            Icons.Filled.Remove,
                            contentDescription = stringResource(R.string.score_zoom_out),
                        )
                    }
                }
            }
        }
    }
}
