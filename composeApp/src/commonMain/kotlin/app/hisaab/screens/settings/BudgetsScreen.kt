package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.Category
import app.hisaab.domain.YearMonth
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(onBack: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val coroutineScope = rememberCoroutineScope()
    val budgets by container.budgetRepository.observeActive().collectAsState(initial = emptyList())
    val categories by container.categoryRepository.observeAll().collectAsState(initial = emptyList())
    var showAddSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Budgets", color = palette.onBackground) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = palette.muted) } },
                actions = { TextButton(onClick = { showAddSheet = true }) { Text("+ Add", color = palette.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        if (budgets.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No budgets yet. Tap + Add.", color = palette.muted)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
                items(budgets, key = { it.id }) { budget ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(budget.categoryName, color = palette.onBackground)
                            Spacer(Modifier.height(2.dp))
                            Text("৳${budget.monthlyCapAmount.toInt()}/month · since ${budget.startsMonth.value}",
                                color = palette.muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = {
                            coroutineScope.launch { container.budgetRepository.archive(budget.id) }
                        }) { Text("Archive", color = palette.negative, fontSize = 12.sp) }
                    }
                    HorizontalDivider(color = palette.rule)
                }
            }
        }
    }

    if (showAddSheet) {
        AddBudgetSheet(
            categories = categories.filter { it.id !in setOf("salary", "transfer") },
            onAdd = { categoryId, amount ->
                coroutineScope.launch {
                    container.budgetRepository.set(categoryId, amount, currentYearMonth())
                }
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetSheet(
    categories: List<Category>,
    onAdd: (String, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    val sheetState = rememberModalBottomSheetState()
    var selectedCategoryId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "") }
    var amount by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.background) {
        Column(modifier = Modifier.padding(22.dp).fillMaxWidth()) {
            Text("New budget", color = palette.accent, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text("Category", color = palette.muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            categories.forEach { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = cat.id == selectedCategoryId,
                        onClick = { selectedCategoryId = cat.id },
                        colors = RadioButtonDefaults.colors(selectedColor = palette.accent),
                    )
                    Text(cat.name, color = palette.onBackground)
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Monthly cap (৳)", color = palette.muted) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val a = amount.toDoubleOrNull()
                    if (a != null && a > 0 && selectedCategoryId.isNotBlank()) {
                        onAdd(selectedCategoryId, a)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && selectedCategoryId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) { Text("Save", color = palette.background) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun currentYearMonth(): YearMonth {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return YearMonth.of(now.year, now.monthNumber)
}
