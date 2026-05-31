package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

/** Read-only 2-column grid of the 24 recovery words. Mono accent index + word. Pass a
 *  weight/height modifier from the caller so it shares the column with the warning + CTA. */
@Composable
fun RecoveryWordGrid(words: List<String>, modifier: Modifier = Modifier) {
    val p = LocalHisaabPalette.current
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(HisaabSpacing.sm),
    ) {
        itemsIndexed(words) { index, word ->
            Row(
                Modifier
                    .clip(HisaabShapes.field)
                    .background(p.surface, HisaabShapes.field)
                    .border(1.dp, p.hair, HisaabShapes.field)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    color = p.accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = HisaabSpacing.sm),
                )
                Text(text = word, color = p.onBackground, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
