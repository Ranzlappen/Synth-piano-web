package io.github.ranzlappen.synthpiano.ui.onboarding

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.ranzlappen.synthpiano.R

/**
 * First-launch dialog inviting the user to customize the on-screen
 * keyboard layout. The default layout doesn't fit every device well,
 * so we surface the editor up-front. Either action marks the
 * onboarding as seen so the dialog never appears again.
 */
@Composable
fun LayoutOnboardingDialog(
    onCustomize: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.onboarding_layout_title)) },
        text = { Text(stringResource(R.string.onboarding_layout_body)) },
        confirmButton = {
            TextButton(onClick = onCustomize) {
                Text(stringResource(R.string.onboarding_layout_customize))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.onboarding_layout_later))
            }
        },
    )
}
