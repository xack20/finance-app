package app.hisaab.screens.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(candidateId: String? = null, onDone: () -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val viewModel = remember {
        EntryViewModel(
            txnRepo = container.transactionRepository,
            lendBorrowRepo = container.lendBorrowRepository,
            personRepo = container.personRepository,
            attachmentRepo = container.attachmentRepository,
            inboxRepo = container.captureInboxRepository,
            // M3-int Fix 5: atomic insert+confirm via AppContainer to prevent orphan PENDING rows.
            confirmWithEdits = { newTxn, capId -> container.confirmCandidateWithEdits(newTxn, capId) },
        )
    }
    androidx.compose.runtime.LaunchedEffect(candidateId) {
        if (candidateId != null) viewModel.prefillFromCandidate(candidateId)
    }
    val state by viewModel.state.collectAsState()
    val accounts by container.accountRepository.observeActive().collectAsState(initial = emptyList())
    val categories by container.categoryRepository.observeAll().collectAsState(initial = emptyList())
    val coroutineScope = rememberCoroutineScope()

    var showSplitSheet by remember { mutableStateOf(false) }
    var showPersonSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New entry", color = palette.onBackground) },
                navigationIcon = {
                    TextButton(onClick = onDone) { Text("Cancel", color = palette.muted) }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onDone) },
                        enabled = state.isValid && !state.isSaving,
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = palette.accent)
                        } else {
                            Text("Save", color = if (state.isValid) palette.accent else palette.muted)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = palette.background),
            )
        },
        containerColor = palette.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            // Kind selector
            KindSelector(
                kind = state.kind,
                onSelect = { viewModel.setKind(it) },
            )
            Spacer(Modifier.height(28.dp))

            // Hero amount
            AmountField(
                value = state.amount,
                onChange = { viewModel.setAmount(it) },
            )
            Spacer(Modifier.height(28.dp))

            AccountPicker(
                accounts = accounts,
                selectedId = state.accountId,
                onSelect = { viewModel.setAccount(it) },
            )
            CategoryPicker(
                categories = categories,
                selectedId = state.categoryId,
                onSelect = { viewModel.setCategory(it) },
            )
            FieldRow(
                label = "When",
                value = formatDateTime(state.whenMs),
                onClick = { /* date-time picker — P0d polish; for now keep "now" */ },
                palette = palette,
            )

            // Merchant input (free-text — full autocomplete is P0d polish)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.merchantName,
                onValueChange = { viewModel.setMerchant(it) },
                label = { Text("Merchant", color = palette.muted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(8.dp))
            NotesField(
                value = state.notes,
                onChange = { viewModel.setNotes(it) },
            )

            // Tags
            Spacer(Modifier.height(12.dp))
            TagChipInput(
                tags = state.tagNames,
                onAdd = { viewModel.addTag(it) },
                onRemove = { viewModel.removeTag(it) },
                palette = palette,
            )

            // Attachment
            Spacer(Modifier.height(12.dp))
            AttachmentRow(
                hasAttachment = state.attachmentBytes != null,
                onPick = {
                    coroutineScope.launch {
                        val picked = container.imagePicker.pickFromGallery()
                        if (picked != null) {
                            viewModel.setAttachment(picked.bytes, picked.mimeType)
                        }
                    }
                },
                onClear = { viewModel.clearAttachment() },
                palette = palette,
            )

            // Split row
            FieldRow(
                label = "Split",
                value = if (state.splits.isEmpty()) "Single entry" else "${state.splits.size} parts",
                onClick = { showSplitSheet = true },
                palette = palette,
            )

            // LEND/BORROW only fields
            if (state.kind in setOf(TxnKind.LEND, TxnKind.BORROW)) {
                Spacer(Modifier.height(12.dp))
                FieldRow(
                    label = "Person",
                    value = state.newPersonName ?: state.personId?.let { "selected" } ?: "Add",
                    onClick = { showPersonSheet = true },
                    palette = palette,
                )
                FieldRow(
                    label = "Due date",
                    value = state.dueDate?.let { formatDate(it) } ?: "Optional",
                    onClick = { /* date picker — P0d polish */ },
                    palette = palette,
                )
            }

            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    // Bottom sheets
    if (showSplitSheet) {
        SplitEditorSheet(
            initialSplits = state.splits,
            parentAmount = state.amount.toDoubleOrNull() ?: 0.0,
            categories = categories,
            onSave = { splits -> viewModel.setSplits(splits); showSplitSheet = false },
            onDismiss = { showSplitSheet = false },
        )
    }
    if (showPersonSheet) {
        PersonPickerSheet(
            onNewPerson = { name -> viewModel.setNewPerson(name, null); showPersonSheet = false },
            onDismiss = { showPersonSheet = false },
            palette = palette,
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String, onClick: () -> Unit, palette: HisaabColors.Palette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.muted, modifier = Modifier.weight(1f))
        Text(value, color = palette.onBackground)
        Spacer(Modifier.width(6.dp))
        Text("›", color = palette.muted)
    }
    HorizontalDivider(color = palette.rule)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagChipInput(
    tags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    palette: HisaabColors.Palette,
) {
    var input by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text("Add tag", color = palette.muted) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                if (input.isNotBlank()) {
                    TextButton(onClick = {
                        onAdd(input.trim())
                        input = ""
                    }) { Text("+", color = palette.accent) }
                }
            },
        )
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    AssistChip(
                        onClick = { onRemove(tag) },
                        label = { Text(tag, color = palette.onBackground) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = palette.surface),
                    )
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    hasAttachment: Boolean,
    onPick: () -> Unit,
    onClear: () -> Unit,
    palette: HisaabColors.Palette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (hasAttachment) onClear() else onPick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Photo", color = palette.muted, modifier = Modifier.weight(1f))
        Text(
            if (hasAttachment) "Attached — tap to remove" else "Add receipt",
            color = if (hasAttachment) palette.accent else palette.onBackground,
        )
        Spacer(Modifier.width(6.dp))
        Text("›", color = palette.muted)
    }
    HorizontalDivider(color = palette.rule)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonPickerSheet(
    onNewPerson: (String) -> Unit,
    onDismiss: () -> Unit,
    palette: HisaabColors.Palette,
) {
    val sheetState = rememberModalBottomSheetState()
    var input by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = palette.background) {
        Column(modifier = Modifier.padding(22.dp).fillMaxWidth()) {
            Text("Person", color = palette.accent, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Name", color = palette.muted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (input.isNotBlank()) onNewPerson(input.trim()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
                enabled = input.isNotBlank(),
            ) { Text("Add", color = palette.background) }
            Spacer(Modifier.height(16.dp))
            Text("Contact picker comes in P0c-3.", color = palette.muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun formatDateTime(ms: Long): String {
    val instant = Instant.fromEpochMilliseconds(ms)
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val pad: (Int) -> String = { if (it < 10) "0$it" else "$it" }
    return "${ldt.year}-${pad(ldt.monthNumber)}-${pad(ldt.dayOfMonth)} ${pad(ldt.hour)}:${pad(ldt.minute)}"
}

private fun formatDate(ms: Long): String {
    val instant = Instant.fromEpochMilliseconds(ms)
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val pad: (Int) -> String = { if (it < 10) "0$it" else "$it" }
    return "${ldt.year}-${pad(ldt.monthNumber)}-${pad(ldt.dayOfMonth)}"
}
