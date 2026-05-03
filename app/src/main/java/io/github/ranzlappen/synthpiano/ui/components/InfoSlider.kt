package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R

/**
 * Slider row used throughout the SOUND tab. Same layout as the previous
 * `EnvelopeSlider` (label · slider · value), with one addition: when
 * [info] is non-null, an info-icon button is appended that opens a modal
 * dialog explaining what the slider does in plain language.
 *
 * Info copy is fully localized — pass [InfoCopy] holding string resource
 * IDs rather than raw text.
 */
@Composable
fun InfoSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    valueFormatter: (Float) -> String,
    modifier: Modifier = Modifier,
    info: InfoCopy? = null,
    labelWidth: androidx.compose.ui.unit.Dp = 72.dp,
    valueWidth: androidx.compose.ui.unit.Dp = 64.dp,
) {
    var infoOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueFormatter(value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(valueWidth),
            textAlign = TextAlign.End,
        )
        if (info != null) InfoIconButton(info)
    }
}

/**
 * Standalone "ⓘ" button with the same modal dialog [InfoSlider] uses.
 * Reuse for non-slider controls (e.g. steppers) so every interactive
 * surface in the SOUND tab can have the same affordance.
 */
@Composable
fun InfoIconButton(info: InfoCopy, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(
        onClick = { open = true },
        modifier = modifier.size(28.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = stringResource(R.string.action_info),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(info.titleRes)) },
            text = { Text(stringResource(info.bodyRes)) },
            confirmButton = {
                TextButton(onClick = { open = false }) {
                    Text(stringResource(R.string.action_got_it))
                }
            },
        )
    }
}

/**
 * Pair of string resource IDs identifying a slider's info-popup title
 * and body. Localized in `res/values*/strings.xml`.
 */
data class InfoCopy(val titleRes: Int, val bodyRes: Int)
