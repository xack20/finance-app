package app.hisaab.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import app.hisaab.domain.Account
import app.hisaab.domain.AccountKind
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(onBack: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val coroutineScope = rememberCoroutineScope()
    val accounts by container.accountRepository.observeActive().collectAsState(initial = emptyList())
    var showAddSheet by remember { mutableStateOf(false) }
    var renamingAccount by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Accounts", color = palette.onBackground) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back", color = palette.muted) } },
                actions = { TextButton(onClick = { showAddSheet = true }) { Text("+ Add", color = palette.accent) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 22.dp)) {
            items(accounts, key = { it.id }) { acc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { renamingAccount = acc }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(acc.name, color = palette.onBackground)
                        Spacer(Modifier.height(2.dp))
                        Text("${acc.kind} · ${acc.currency}", color = palette.muted, fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        coroutineScope.launch { container.accountRepository.archive(acc.id) }
                    }) { Text("Archive", color = palette.negative, fontSize = 12.sp) }
                }
                HorizontalDivider(color = palette.rule)
            }
        }
    }

    if (showAddSheet) {
        AddAccountSheet(
            onAdd = { name, kind ->
                coroutineScope.launch {
                    container.accountRepository.add(name, kind, institution = null)
                }
                showAddSheet = false
            },
            onDismiss = { showAddSheet = false },
        )
    }

    renamingAccount?.let { acc ->
        RenameAccountDialog(
            current = acc.name,
            onSave = { newName ->
                coroutineScope.launch { container.accountRepository.rename(acc.id, newName) }
                renamingAccount = null
            },
            onDismiss = { renamingAccount = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(onAdd: (String, AccountKind) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AccountKind.CASH) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.background) {
        Column(modifier = Modifier.padding(22.dp).fillMaxWidth()) {
            Text("New account", color = palette.accent, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name", color = palette.muted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Text("Kind", color = palette.muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AccountKind.entries.forEach { k ->
                    Button(
                        onClick = { kind = k },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (kind == k) palette.accent else palette.surface,
                            contentColor = if (kind == k) palette.background else palette.onBackground,
                        ),
                    ) { Text(k.name, fontSize = 11.sp) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (name.isNotBlank()) onAdd(name.trim(), kind) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) { Text("Add", color = palette.background) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RenameAccountDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    val palette = LocalHisaabPalette.current
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename account", color = palette.onBackground) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank() && name != current) onSave(name.trim()) }) {
                Text("Save", color = palette.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = palette.muted) } },
    )
}
