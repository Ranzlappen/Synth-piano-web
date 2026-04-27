package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R

/**
 * Compact pill that surfaces USB-MIDI connectivity. Tap opens a device
 * sheet (handled upstream); a green dot signals at least one device is
 * connected, dim grey signals none.
 */
@Composable
fun MidiStatusChip(
    deviceNames: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = deviceNames.isNotEmpty()
    val dotColor = if (connected) MaterialTheme.colorScheme.tertiary
                   else MaterialTheme.colorScheme.outline
    val labelText = if (connected) stringResource(R.string.midi_status_count, deviceNames.size)
                    else stringResource(R.string.midi_status_none)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Cable,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
