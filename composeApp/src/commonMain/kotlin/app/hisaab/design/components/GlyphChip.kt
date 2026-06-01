package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabColors

/** Resolve a category-hue key (e.g. "violet") to its Color, falling back to slate. */
fun categoryHue(key: String?): Color =
    HisaabColors.categoryHues[key] ?: HisaabColors.categoryHues.getValue("slate")

/**
 * A 40dp rounded-square glyph chip: the [icon] in the category [hue] over a 16%-alpha fill of the
 * same hue (radius 14). Used in the transaction feed, category lists, account strip.
 */
@Composable
fun GlyphChip(
    icon: ImageVector,
    hue: Color,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(hue.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = hue)
    }
}

/**
 * Category glyph chip driven by a [HisaabIcons] name (e.g. "food", "cart"): the stroke icon in the
 * [hue] over a 16%-alpha fill of the same hue (radius 14). Unknown/absent names fall back to "receipt".
 */
@Composable
fun GlyphChip(
    iconName: String?,
    hue: Color,
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(hue.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        HisaabIcon(name = iconName ?: "receipt", tint = hue, size = (size * 0.5f).dp)
    }
}
