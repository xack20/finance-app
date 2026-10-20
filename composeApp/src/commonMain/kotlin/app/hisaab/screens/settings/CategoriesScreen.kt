package app.hisaab.screens.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.*
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val coroutineScope = rememberCoroutineScope()
    val categories by container.categoryRepository.observeAll().collectAsState(initial = emptyList())
    var showAddSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        NeoTopBar(
            title = "Categories",
            onBack = onBack,
            rightLabel = "+ Add",
            onRight = { showAddSheet = true },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HisaabSpacing.gutter)
                .padding(bottom = HisaabSpacing.xl),
        ) {
            Spacer(Modifier.height(HisaabSpacing.sm))
            // One grouped card with inner hairline dividers between rows (neo-settings.jsx:83-86).
            SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                val lastIndex = categories.lastIndex
                categories.forEachIndexed { i, cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlyphChip(iconName = cat.icon, hue = categoryHue(cat.color), size = 36)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            cat.name,
                            color = palette.onBackground,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.weight(1f),
                        )
                        if (cat.isDefault) Eyebrow("Default")
                    }
                    if (i < lastIndex) {
                        HorizontalDivider(color = palette.hair2, modifier = Modifier.padding(start = 50.dp))
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddCategorySheet(
            onAdd = { name, iconName ->
                coroutineScope.launch {
                    container.categoryRepository.add(name = name, color = "lime", icon = iconName, parentId = null)
                }
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }
}

/** Icon-name choices for the add-category picker (neo-settings.jsx:92, HisaabIcons names). */
private val CATEGORY_ICON_CHOICES = listOf(
    "food", "cart", "car", "bag", "health", "book", "film", "case", "receipt",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddCategorySheet(onAdd: (String, String?) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(CATEGORY_ICON_CHOICES.first()) }

    MidnightSheet(onDismiss = onDismiss, sheetState = sheetState, title = "New category") {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name", color = palette.muted) },
            placeholder = { Text("e.g. Gym", color = palette.faint) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = HisaabShapes.field,
            colors = midnightOutlinedColors(),
        )
        Spacer(Modifier.height(HisaabSpacing.lg))

        Eyebrow("Icon", Modifier.padding(bottom = 10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CATEGORY_ICON_CHOICES.forEach { iconName ->
                val selected = iconName == selectedIcon
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(
                            width = 2.dp,
                            color = if (selected) palette.accent else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { selectedIcon = iconName },
                ) {
                    GlyphChip(iconName = iconName, hue = palette.accent, size = 42)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        PrimaryButton(
            text = "Add category",
            onClick = { if (name.isNotBlank()) onAdd(name.trim(), selectedIcon) },
            enabled = name.isNotBlank(),
        )
    }
}
