package app.hisaab.screens.month

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.domain.RecurringHit
import app.hisaab.util.toTaka
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun RecurringList(items: List<RecurringHit>, palette: HisaabColors.Palette) {
    if (items.isEmpty()) {
        Text(
            "No patterns detected yet — needs 3+ transactions per merchant in the last 90 days.",
            color = palette.muted,
            fontSize = 12.sp,
        )
        return
    }
    Column {
        items.forEachIndexed { index, hit ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(hit.merchantName, color = palette.onBackground, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${hit.occurrenceCount}× · avg ${hit.avgAmount.toTaka()}",
                        color = palette.muted,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Normal),
                    )
                }
                Text(
                    formatLastSeen(hit.lastSeenTs),
                    color = palette.faint,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal),
                )
            }
            // Hairline on every row except the last; fainter --hair-2 (neo.jsx:342).
            if (index < items.lastIndex) HorizontalDivider(color = palette.hair2)
        }
    }
}

private fun formatLastSeen(ms: Long): String {
    if (ms == 0L) return "—"
    val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    val pad: (Int) -> String = { if (it < 10) "0$it" else "$it" }
    return "${pad(ldt.monthNumber)}-${pad(ldt.dayOfMonth)}"
}
