package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * The chord-modifier strip that historically lived at the top of the
 * PERFORM tab: LOCK (sticky) and SHIFT (momentary) chord-modifier rows
 * plus zoom +/- buttons. Now hostable in any layout panel via
 * [io.github.ranzlappen.synthpiano.data.ModifierPanel] — the show* flags
 * let layouts hide the inversion column or the zoom buttons.
 */
@Composable
fun PerformModifierStrip(
    qualities: List<ChordQuality>,
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
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showLock) {
                    ChordModifierRow(
                        label = "LOCK",
                        qualities = qualities,
                        selected = sticky,
                        inversion = stickyInv,
                        onToggle = onStickyToggle,
                        onInversionToggle = onStickyInvToggle,
                    )
                }
                if (showShift) {
                    ChordModifierRow(
                        label = "SHIFT",
                        qualities = qualities,
                        selected = held,
                        inversion = heldInv,
                        momentary = true,
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
