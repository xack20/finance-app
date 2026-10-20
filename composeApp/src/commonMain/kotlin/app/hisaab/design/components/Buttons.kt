package app.hisaab.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.LocalHisaabPalette

/** Button height variants matching the design's NBtn `size` prop (neo.jsx:23).
 *  [Lg] is the default full-size CTA; [Md] is the compact 46dp variant used in dense
 *  two-up rows like the CaptureOptIn consent card (neo-onboarding.jsx:124). */
enum class ButtonSize(val height: Dp) {
    Lg(54.dp),
    Md(46.dp),
}

/** Full-width lime primary CTA (dark onAccent label), pill shape, 54dp tall.
 *  [loading] shows an onAccent spinner in place of the label and blocks taps (fill stays lime).
 *  [leadingGlyph]/[trailingGlyph] are text glyphs (no material-icons dep). Set [fillMaxWidth]=false
 *  for two-up rows (constrain via modifier weight). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingGlyph: String? = null,
    leadingIcon: String? = null,
    trailingGlyph: String? = null,
    trailingIcon: String? = null,
    fillMaxWidth: Boolean = true,
    size: ButtonSize = ButtonSize.Lg,
) {
    val p = LocalHisaabPalette.current
    val widthMod = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.then(widthMod).height(size.height),
        shape = HisaabShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.accent,
            contentColor = p.onAccent,
            // while loading, keep the lime fill + dark spinner; the real disabled look is faint.
            disabledContainerColor = if (loading) p.accent else p.surfaceRaised,
            disabledContentColor = if (loading) p.onAccent else p.faint,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = p.onAccent, strokeWidth = 2.dp)
        } else {
            if (leadingIcon != null) { HisaabIcon(leadingIcon, tint = p.onAccent, size = 19.dp); Spacer(Modifier.width(8.dp)) }
            if (leadingGlyph != null) { Text(leadingGlyph); Spacer(Modifier.width(8.dp)) }
            Text(text)
            if (trailingGlyph != null) { Spacer(Modifier.width(8.dp)); Text(trailingGlyph) }
            if (trailingIcon != null) { Spacer(Modifier.width(8.dp)); HisaabIcon(trailingIcon, tint = p.onAccent, size = 19.dp) }
        }
    }
}

/** Secondary glass button: translucent fill, hairline border, primary text color. */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fillMaxWidth: Boolean = true,
    size: ButtonSize = ButtonSize.Lg,
) {
    val p = LocalHisaabPalette.current
    val widthMod = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(widthMod).height(size.height),
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
