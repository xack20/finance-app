package app.hisaab.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.TxnKind

@Composable
fun TodayScreen(onTxnClick: (String) -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val viewModel = remember {
        TodayViewModel(
            txnRepo = container.transactionRepository,
            accountRepo = container.accountRepository,
            categoryRepo = container.categoryRepository,
            merchantRepo = container.merchantRepository,
        )
    }
    val net by viewModel.todayNet.collectAsState()
    val recent by viewModel.recent.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = 22.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Today", style = MaterialTheme.typography.displaySmall, color = palette.onBackground)
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            NetCell("In", net.income, palette.positive)
            NetCell("Out", net.expense, palette.negative)
            NetCell("Net", net.net, palette.onBackground)
        }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(color = palette.rule)
        Spacer(Modifier.height(8.dp))

        if (recent.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No entries yet.\nTap + to record your first.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.muted,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                items(recent, key = { it.row.id }) { display ->
                    TxnRow(display = display, palette = palette, onClick = { onTxnClick(display.row.id) })
                    HorizontalDivider(color = palette.rule)
                }
            }
        }
    }
}

@Composable
private fun NetCell(label: String, amount: Double, color: Color) {
    val palette = LocalHisaabPalette.current
    Column {
        Text(label.uppercase(), color = palette.muted, fontSize = 11.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "৳${amount.toInt()}",
            color = color,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun TxnRow(
    display: TransactionRowDisplay,
    palette: HisaabColors.Palette,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                display.merchantName ?: display.categoryName ?: "—",
                color = palette.onBackground,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    append(display.accountName)
                    if (!display.categoryName.isNullOrBlank() && display.merchantName != null) {
                        append(" · ")
                        append(display.categoryName)
                    }
                },
                fontSize = 12.sp,
                color = palette.muted,
            )
        }
        val (sign, color) = when (display.row.kind) {
            TxnKind.INCOME, TxnKind.LEND, TxnKind.SETTLEMENT -> "+" to palette.positive
            else -> "−" to palette.negative
        }
        Text(
            "$sign৳${display.row.amount.toInt()}",
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
