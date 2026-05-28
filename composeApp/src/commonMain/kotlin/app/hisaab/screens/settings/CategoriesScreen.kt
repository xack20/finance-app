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
                title = { Text("Categories", color = palette.onBackground) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = palette.muted) } },
                actions = { TextButton(onClick = { showAddSheet = true }) { Text("+ Add", color = palette.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
            items(categories, key = { it.id }) { cat ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    cat.icon?.let { Text(it, modifier = Modifier.width(28.dp), fontSize = 16.sp) }
                    Text(cat.name, color = palette.onBackground, modifier = Modifier.weight(1f))
                    if (cat.isDefault) {
                        Text("default", color = palette.muted, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                }
                HorizontalDivider(color = palette.rule)
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

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.background) {
        Column(modifier = Modifier.padding(22.dp).fillMaxWidth()) {
            Text("New category", color = palette.accent, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name", color = palette.muted) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = icon, onValueChange = { icon = it },
                label = { Text("Emoji (optional)", color = palette.muted) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (name.isNotBlank()) onAdd(name.trim(), icon.ifBlank { null }) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) { Text("Add", color = palette.background) }
            Spacer(Modifier.height(16.dp))
        }
    }
}
