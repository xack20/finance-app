package app.hisaab.design.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.hisaab.design.LocalHisaabPalette

/** Small uppercase tracked label in the faint color — the Midnight "eyebrow". */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = p.faint,
    )
}

/** A section header: a title in the display face, optionally preceded by an [eyebrow]. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, eyebrow: String? = null) {
    val p = LocalHisaabPalette.current
    Column(modifier = modifier) {
        if (eyebrow != null) Eyebrow(eyebrow)
        Text(text = title, style = MaterialTheme.typography.headlineMedium, color = p.onBackground)
    }
}
