package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            // Design (neo-onboarding.jsx:90): 12dp radius card, 13/12 padding; mono lime index
            // (min-width 16), and the WORD in sans Medium 14.5 (not the bold mono it was rendering).
            val cardShape = RoundedCornerShape(12.dp)
            Row(
                Modifier
                    .clip(cardShape)
                    .background(p.surface, cardShape)
                    .border(1.dp, p.hair, cardShape)
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${index + 1}",
                    color = p.accent,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.widthIn(min = 16.dp).padding(end = HisaabSpacing.sm),
                )
                Text(
                    text = word,
                    color = p.onBackground,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}
