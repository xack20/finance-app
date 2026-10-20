package app.hisaab.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.hisaab.design.LocalHisaabPalette

/**
 * Small uppercase tracked label — the Midnight "eyebrow". Defaults to the faint tertiary color;
 * pass [color] (e.g. the lime accent) for the lime eyebrows the design uses on Verify/Secure/
 * Recover/Language/CAPTURED.
 */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color? = null) {
    val p = LocalHisaabPalette.current
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = color ?: p.faint,
    )
}

/** A section header: a title in the display face, optionally preceded by an [eyebrow]. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    eyebrowColor: Color? = null,
) {
    val p = LocalHisaabPalette.current
    Column(modifier = modifier) {
        if (eyebrow != null) Eyebrow(eyebrow, color = eyebrowColor)
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = p.onBackground)
    }
}
