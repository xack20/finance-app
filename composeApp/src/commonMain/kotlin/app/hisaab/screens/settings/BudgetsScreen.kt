package app.hisaab.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.*
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
                title = {},
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = palette.muted) }
                },
                actions = {
                    TextButton(onClick = { showAddSheet = true }) {
                        Text("+ Add", color = palette.accent)
                    }
                },
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = HisaabSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(HisaabSpacing.sm),
                contentPadding = PaddingValues(bottom = HisaabSpacing.xl),
            ) {
                item {
                    SectionHeader("Budgets", modifier = Modifier.padding(vertical = HisaabSpacing.lg))
                }
                items(budgets, key = { it.id }) { budget ->
                    SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    budget.categoryName,
                                    color = palette.onBackground,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Spacer(Modifier.height(HisaabSpacing.xs))
                                Text(
                                    "৳${budget.monthlyCapAmount.toInt()}/mo · since ${budget.startsMonth.value}",
                                    color = palette.muted,
                                    fontSize = 11.sp,
                                )
                            }
                            TextButton(onClick = {
                                coroutineScope.launch { container.budgetRepository.archive(budget.id) }
                            }) { Text("Archive", color = palette.negative, fontSize = 12.sp) }
                        }
                    }
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

    MidnightSheet(onDismiss = onDismiss, sheetState = sheetState, title = "New budget") {
        Text("Category", color = palette.muted, fontSize = 12.sp)
        Spacer(Modifier.height(HisaabSpacing.xs))
        // HRadio category list (replaces Material RadioButton)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            categories.forEach { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = HisaabSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HRadio(
                        selected = cat.id == selectedCategoryId,
                        onClick = { selectedCategoryId = cat.id },
                    )
                    Spacer(Modifier.width(HisaabSpacing.md))
                    Text(cat.name, color = palette.onBackground)
                }
            }
        }
        Spacer(Modifier.height(HisaabSpacing.md))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("৳ Monthly cap", color = palette.muted) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Spacer(Modifier.height(HisaabSpacing.lg))
        PrimaryButton(
            text = "Save",
            onClick = {
                val a = amount.toDoubleOrNull()
                if (a != null && a > 0 && selectedCategoryId.isNotBlank()) {
                    onAdd(selectedCategoryId, a)
                }
            },
            enabled = amount.toDoubleOrNull()?.let { it > 0 } == true && selectedCategoryId.isNotBlank(),
        )
    }
}

private fun currentYearMonth(): YearMonth {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return YearMonth.of(now.year, now.monthNumber)
}
