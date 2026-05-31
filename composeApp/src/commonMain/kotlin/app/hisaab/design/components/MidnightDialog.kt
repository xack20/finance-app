package app.hisaab.design.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette

/**
 * Midnight confirm dialog: surface fill, card radius, accent confirm (or negative when
 * [destructive]), muted dismiss. Replaces raw AlertDialog confirm/sign-out/delete call sites.
 */
@Composable
fun MidnightDialog(
    onDismiss: () -> Unit,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
) {
    val p = LocalHisaabPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        titleContentColor = p.onBackground,
        textContentColor = p.muted,
        shape = HisaabShapes.card,
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (destructive) p.negative else p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = p.muted) }
        },
    )
}
