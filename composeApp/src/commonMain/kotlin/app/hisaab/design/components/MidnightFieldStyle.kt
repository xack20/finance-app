package app.hisaab.design.components

import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import app.hisaab.design.LocalHisaabPalette

/**
 * Shared Midnight styling for raw [androidx.compose.material3.OutlinedTextField] call sites that
 * cannot use [MidnightTextField] — multi-line inputs, fields with a trailing affordance, or compact
 * in-row fields. Pair with `shape = HisaabShapes.field` so the field reads as part of the Midnight
 * system (surface fill, hairline → lime focus border, accent cursor) instead of Material 3 defaults.
 *
 * Standalone hero/form fields should still prefer [MidnightTextField] (eyebrow label variant).
 */
@Composable
fun midnightOutlinedColors(): TextFieldColors {
    val p = LocalHisaabPalette.current
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = p.accent,
        unfocusedBorderColor = p.hair,
        disabledBorderColor = p.hair,
        errorBorderColor = p.negative,
        focusedTextColor = p.onBackground,
        unfocusedTextColor = p.onBackground,
        disabledTextColor = p.muted,
        cursorColor = p.accent,
        focusedContainerColor = p.surface,
        unfocusedContainerColor = p.surface,
        disabledContainerColor = p.surface,
        focusedLabelColor = p.accent,
        unfocusedLabelColor = p.muted,
    )
}
