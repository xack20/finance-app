package app.hisaab.screens.month

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabColors
import app.hisaab.domain.RecurringHit
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
        items.forEach { hit ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(hit.merchantName, color = palette.onBackground)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${hit.occurrenceCount} times · avg ৳${hit.avgAmount.toInt()}",
                        color = palette.muted,
                        fontSize = 11.sp,
                    )
                }
                Text(formatLastSeen(hit.lastSeenTs), color = palette.muted, fontSize = 11.sp)
            }
            HorizontalDivider(color = palette.rule)
        }
    }
}

private fun formatLastSeen(ms: Long): String {
    if (ms == 0L) return "—"
    val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    val pad: (Int) -> String = { if (it < 10) "0$it" else "$it" }
    return "${pad(ldt.monthNumber)}-${pad(ldt.dayOfMonth)}"
}
