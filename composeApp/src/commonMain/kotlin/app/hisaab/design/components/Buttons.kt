package app.hisaab.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette

/** Full-width lime primary CTA (dark onAccent label), pill shape, 54dp tall. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val p = LocalHisaabPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = HisaabShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.accent,
            contentColor = p.onAccent,
            disabledContainerColor = p.surfaceRaised,
            disabledContentColor = p.faint,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) { Text(text) }
}

/** Secondary glass button: translucent fill, hairline border, primary text color. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val p = LocalHisaabPalette.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = HisaabShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.glass,
            contentColor = p.onBackground,
            disabledContainerColor = p.glass,
            disabledContentColor = p.faint,
        ),
        border = BorderStroke(1.dp, p.hair),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) { Text(text) }
}
