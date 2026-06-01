package app.hisaab.design.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.util.toTaka

/** How a [MoneyText] colors itself by amount sign. */
enum class MoneyTone { Auto, Plain, Accent }

/**
 * Tabular money figure (Space Mono) with the ৳ mark, grouped thousands, and an optional sign.
 * [tone] = Auto colors positive=positive, negative=negative; Plain uses the inherited text color.
 */
@Composable
fun MoneyText(
    amount: Double,
    modifier: Modifier = Modifier,
    signed: Boolean = false,
    decimals: Int = 0,
    tone: MoneyTone = MoneyTone.Auto,
    color: Color? = null,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val p = LocalHisaabPalette.current
    val resolved = color ?: when (tone) {
        MoneyTone.Accent -> p.accent
        MoneyTone.Plain -> LocalTextStyle.current.color
        MoneyTone.Auto -> when {
            amount > 0.0 -> p.positive
            amount < 0.0 -> p.negative
            else -> p.onBackground
        }
    }
    Text(
        text = amount.toTaka(signed = signed, decimals = decimals),
        modifier = modifier,
        style = style,
        color = resolved,
        maxLines = maxLines,
        softWrap = softWrap,
    )
}
