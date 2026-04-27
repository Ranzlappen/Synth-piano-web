package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.audio.Adsr
import io.github.ranzlappen.synthpiano.audio.Waveform

/**
 * A bottom-sheet modal hosting the same wave + ADSR controls that used
 * to live inline on the Play screen. Triggered by the "tune" icon in the
 * top app bar; tap-outside or swipe-down dismisses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynthParamsModal(
    waveform: Waveform,
    onWaveform: (Waveform) -> Unit,
    adsr: Adsr,
    onAdsr: (Adsr) -> Unit,
    masterAmp: Float,
    onMasterAmp: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
    ) {
        Text(
            text = "Synth parameters",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        SynthControlsBar(
            waveform = waveform,
            onWaveform = onWaveform,
            adsr = adsr,
            onAdsr = onAdsr,
            masterAmp = masterAmp,
            onMasterAmp = onMasterAmp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}
