package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
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
 * and Momentary (was "SHIFT") chord-modifier rows.
 *
 * The two row variants are distinguished by an underline on Sticky pill
 * labels. Pill heights still scale to fill the available container
 * vertical space (AdaptivePillGrid clamps to 32 dp at most); when the
 * pad is smaller than the natural content height even at the 14 dp
 * pill-height floor, the inner column scrolls vertically. Each pad has
 * its own [verticalScrollState] so two pads in one layout never share
 * scroll position.
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
    verticalScrollState: ScrollState,
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
            // Inner column scrolls when content can't fit even at the 28 dp
            // pill-height floor. When the pad is roomy, AdaptivePillGrid
            // sees an effectively unbounded height and pins pills to 64 dp;
            // the column is still shorter than the parent so no scroll.
            // A row with no qualities AND no inversions has nothing to show;
            // hide the entire row container so it doesn't reserve vertical
            // space. `showLock`/`showShift` remain the coarse-grained
            // toggles; deselecting all chips is the finer-grained equivalent.
            val hasPills = qualities.isNotEmpty() || inversions.isNotEmpty()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(verticalScrollState),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showLock && hasPills) {
                    ChordModifierRow(
                        variant = PillVariant.Sticky,
                        qualities = qualities,
                        inversions = inversions,
                        selected = sticky,
                        inversion = stickyInv,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp),
                        onToggle = onStickyToggle,
                        onInversionToggle = onStickyInvToggle,
                    )
                }
                if (showShift && hasPills) {
                    ChordModifierRow(
                        variant = PillVariant.Momentary,
                        qualities = qualities,
                        inversions = inversions,
                        selected = held,
                        inversion = heldInv,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 28.dp),
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
