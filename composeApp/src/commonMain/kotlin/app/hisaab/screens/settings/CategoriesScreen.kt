package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val coroutineScope = rememberCoroutineScope()
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = HisaabSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(HisaabSpacing.sm),
            contentPadding = PaddingValues(bottom = HisaabSpacing.xl),
        ) {
            item {
                SectionHeader("Categories", modifier = Modifier.padding(vertical = HisaabSpacing.lg))
            }
            items(categories, key = { it.id }) { cat ->
                SurfaceCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Emoji/icon — wrap in a small hue tile
                        if (cat.icon != null) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        color = palette.surfaceRaised,
                                        shape = RoundedCornerShape(10.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(cat.icon, fontSize = 16.sp)
                            }
                            Spacer(Modifier.width(HisaabSpacing.md))
                        }
                        Text(
                            cat.name,
                            color = palette.onBackground,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        if (cat.isDefault) {
                            Eyebrow("Default")
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddCategorySheet(
            onAdd = { name, icon ->
                coroutineScope.launch {
                    container.categoryRepository.add(name = name, color = null, icon = icon, parentId = null)
                }
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddCategorySheet(onAdd: (String, String?) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("") }

    MidnightSheet(onDismiss = onDismiss, sheetState = sheetState, title = "New category") {
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Name", color = palette.muted) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Spacer(Modifier.height(HisaabSpacing.sm))
        OutlinedTextField(
            value = icon, onValueChange = { icon = it },
            label = { Text("Emoji (optional)", color = palette.muted) },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
        Spacer(Modifier.height(HisaabSpacing.lg))
        PrimaryButton(
            text = "Add",
            onClick = { if (name.isNotBlank()) onAdd(name.trim(), icon.ifBlank { null }) },
            enabled = name.isNotBlank(),
        )
    }
}
