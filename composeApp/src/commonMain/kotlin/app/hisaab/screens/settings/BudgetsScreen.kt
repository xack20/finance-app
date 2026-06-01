package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.*
import app.hisaab.domain.BudgetRow
import app.hisaab.domain.Category
import app.hisaab.domain.YearMonth
import app.hisaab.util.toTaka
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Salary/transfer are never budgetable (neo-settings.jsx:102). */
private val EXCLUDED_FROM_BUDGETS = setOf("salary", "transfer")

@Composable
fun BudgetsScreen(onBack: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val coroutineScope = rememberCoroutineScope()
    val budgets by container.budgetRepository.observeActive().collectAsState(initial = emptyList())
    val categories by container.categoryRepository.observeAll().collectAsState(initial = emptyList())
    var showAddSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
        NeoTopBar(
            title = "Budgets",
            onBack = onBack,
            rightLabel = "+ Add",
            onRight = { showAddSheet = true },
        )
        if (budgets.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No budgets yet. Tap + Add.", color = palette.muted)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = HisaabSpacing.gutter),
                verticalArrangement = Arrangement.spacedBy(HisaabSpacing.md),
                contentPadding = PaddingValues(top = HisaabSpacing.sm, bottom = HisaabSpacing.xl),
            ) {
                items(budgets, key = { it.id }) { budget ->
                    BudgetCard(
                        budget = budget,
                        onArchive = {
                            coroutineScope.launch { container.budgetRepository.archive(budget.id) }
                        },
                    )
                }
            }
        }
    }

    if (showAddSheet) {
        AddBudgetSheet(
            categories = categories.filter { it.id !in EXCLUDED_FROM_BUDGETS },
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

@Composable
private fun BudgetCard(budget: BudgetRow, onArchive: () -> Unit) {
    val palette = LocalHisaabPalette.current
    SurfaceCard(modifier = Modifier.fillMaxWidth(), shape = HisaabShapes.cardCompact) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    budget.categoryName,
                    color = palette.onBackground,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(HisaabSpacing.xs))
                // mono sub-line, grouped cap (neo-settings.jsx:106).
                Text(
                    "${budget.monthlyCapAmount.toTaka()}/mo · since ${budget.startsMonth.value}",
                    color = palette.muted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                )
            }
            TextButton(onClick = onArchive) {
                Text(
                    "Archive",
                    color = palette.negative,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
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
        Eyebrow("Category")
        Spacer(Modifier.height(HisaabSpacing.sm))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 230.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            categories.forEach { cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedCategoryId = cat.id }
                        .padding(vertical = 11.dp, horizontal = HisaabSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.md),
                ) {
                    HRadio(selected = cat.id == selectedCategoryId, onClick = { selectedCategoryId = cat.id })
                    GlyphChip(iconName = cat.icon, hue = categoryHue(cat.color), size = 36)
                    Text(
                        cat.name,
                        color = palette.onBackground,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
        Spacer(Modifier.height(HisaabSpacing.md))
        MidnightTextField(
            value = amount,
            onValueChange = { amount = it.filter { c -> c.isDigit() } },
            label = "Monthly cap",
            prefix = "৳",
            placeholder = "8000",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(HisaabSpacing.lg))
        PrimaryButton(
            text = "Save budget",
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
