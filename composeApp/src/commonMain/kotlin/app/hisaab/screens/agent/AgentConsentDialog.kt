package app.hisaab.screens.agent

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

/**
 * Disclosure dialog shown when the user attempts to use the assistant before consenting.
 * Lists what data leaves the device and what never does, so the user can make an informed choice.
 *
 * onConsent  — user tapped "Turn on assistant"; caller should call AgentViewModel.onConsent().
 * onDismiss  — user tapped "Not now" or dismissed; caller should close the agent screen.
 */
@Composable
fun AgentConsentDialog(
    onConsent: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHisaabPalette.current

    AlertDialog(
        modifier = Modifier.testTag("agent_consent_dialog"),
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        shape = app.hisaab.design.HisaabShapes.card,
        titleContentColor = palette.onBackground,
        textContentColor = palette.muted,
        title = {
            Text(
                text = "Turn on the assistant",
                style = MaterialTheme.typography.titleMedium,
                color = palette.onBackground,
            )
        },
        text = {
            Column {
                Text(
                    text = "The assistant is a cloud feature. To answer your questions it sends some information to the AI model.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = "What leaves your device:",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onBackground,
                )
                Text(
                    text = "• Account and category names\n" +
                        "• People you name in a message\n" +
                        "• Amounts and card limits / due dates\n" +
                        "• Your conversation with the assistant",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "What never leaves your device:",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.onBackground,
                )
                Text(
                    text = "• Raw SMS messages\n" +
                        "• Your full transaction ledger\n" +
                        "• Audio or microphone data",
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.muted,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConsent,
                modifier = Modifier.testTag("agent_consent_confirm"),
            ) {
                Text(
                    text = "Turn on assistant",
                    color = palette.accent,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Not now",
                    color = palette.muted,
                )
            }
        },
    )
}
