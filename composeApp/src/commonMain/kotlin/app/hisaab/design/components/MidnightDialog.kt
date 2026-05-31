package app.hisaab.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

/**
 * Midnight confirm dialog: surface fill, card radius, accent confirm (or negative when
 * [destructive]), muted dismiss. Replaces raw AlertDialog confirm/sign-out/delete call sites.
 *
 * [body] renders as the primary text block (pass "" to omit). [content] is an optional composable
 * slot rendered below the body (e.g. a text field). [confirmEnabled] disables the confirm button
 * when false (rendered in muted color).
 */
@Composable
fun MidnightDialog(
    onDismiss: () -> Unit,
    title: String,
    body: String = "",
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String = "Cancel",
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val p = LocalHisaabPalette.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        titleContentColor = p.onBackground,
        textContentColor = p.muted,
        shape = HisaabShapes.card,
        title = { Text(title, style = MaterialTheme.typography.headlineMedium) },
        text = {
            Column {
                if (body.isNotEmpty()) {
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
                if (content != null) {
                    if (body.isNotEmpty()) Spacer(Modifier.height(HisaabSpacing.md))
                    content()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) {
                Text(
                    confirmLabel,
                    color = when {
                        !confirmEnabled -> p.muted
                        destructive -> p.negative
                        else -> p.accent
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel, color = p.muted) }
        },
    )
}
