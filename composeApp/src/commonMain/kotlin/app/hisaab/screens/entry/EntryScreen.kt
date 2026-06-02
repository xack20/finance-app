package app.hisaab.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabColors
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.midnightOutlinedColors
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.launch

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
    var showCategorySheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "New entry",
                        color = palette.onBackground,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    )
                },
                navigationIcon = {
                    // Round glass × close button (replaces the "Cancel" text button).
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(palette.glass)
                            .border(1.dp, palette.hair, CircleShape)
                            .clickable(onClick = onDone),
                        contentAlignment = Alignment.Center,
                    ) { HisaabIcon("close", tint = palette.onBackground, size = 19.dp) }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = palette.background),
            )
        },
        // Inset content for the TOP only (status bar / Dynamic Island). The bottom safe area is owned
        // by the keypad panel's navigationBarsPadding() so its raised surface reaches the screen edge
        // (design neo.jsx:264 uses env(safe-area-inset-bottom)); without this the Scaffold also pads
        // the bottom, double-insetting the keypad and stealing height on short screens (iPhone 15 Pro).
        contentWindowInsets = WindowInsets.statusBars,
        containerColor = palette.background,
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Spacer(Modifier.height(8.dp))
            KindChipRow(
                kind = state.kind,
                onSelect = { viewModel.setKind(it) },
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            // Amount + quick categories fill the space between the kind chips and the detail
            // chips, vertically centered (neo.jsx:237-250). verticalScroll keeps them from
            // overlapping the kind row / detail chips when the available height is tight (short
            // devices, or the OS keyboard pushing the layout up) — it only scrolls when they don't fit.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BigAmount(amount = state.amount, kind = state.kind)
                Spacer(Modifier.height(24.dp))
                CategoryQuickRow(
                    selectedId = state.categoryId,
                    onSelect = { viewModel.setCategory(it) },
                    onMore = { showCategorySheet = true },
                )
            }

            state.error?.let {
                Text(
                    it,
                    color = palette.negative,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            // Detail chips strip (neo.jsx:251-262) — each chip opens a sheet (or toggles, Receipt).
            EntryDetailChips(
                state = state,
                accounts = accounts,
                onAccount = { viewModel.setAccount(it) },
                onToAccount = { viewModel.setToAccount(it) },
                onWhen = { viewModel.setWhen(it) },
                onMerchant = { viewModel.setMerchant(it) },
                onNotes = { viewModel.setNotes(it) },
                onAddTag = { viewModel.addTag(it) },
                onRemoveTag = { viewModel.removeTag(it) },
                onReceipt = {
                    if (state.attachmentBytes != null) {
                        viewModel.clearAttachment()
                    } else {
                        coroutineScope.launch {
                            val picked = container.imagePicker.pickFromGallery()
                            if (picked != null) viewModel.setAttachment(picked.bytes, picked.mimeType)
                        }
                    }
                },
                onSplit = { showSplitSheet = true },
                onPerson = { showPersonSheet = true },
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // Keypad + Save on a raised bg-2 panel with a top hairline + rounded top (neo.jsx:264-269).
            val hairColor = palette.hair
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(palette.surfaceRaised)
                    .drawBehind {
                        drawLine(
                            color = hairColor,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    .padding(horizontal = 14.dp)
                    .padding(top = 12.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 14.dp),
            ) {
                NumericKeypad(onKey = { viewModel.setAmount(applyAmountKey(state.amount, it)) })
                Spacer(Modifier.height(10.dp))
                PrimaryButton(
                    text = if (state.isSaving) "Saving…" else "Save entry",
                    onClick = { viewModel.save(onDone) },
                    enabled = state.isValid && !state.isSaving,
                )
            }
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
    if (showCategorySheet) {
        // Full category list beyond the quick-row shortlist (reuses the detail-chips list sheet).
        EntryListSheet(
            title = "Category",
            items = categories.map { it.id to it.name },
            selectedId = state.categoryId,
            onSelect = { viewModel.setCategory(it); showCategorySheet = false },
            onDismiss = { showCategorySheet = false },
        )
    }
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
        Column(modifier = Modifier.padding(HisaabSpacing.gutter).fillMaxWidth()) {
            Text("Person", color = palette.accent, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Name", color = palette.muted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = HisaabShapes.field,
                colors = midnightOutlinedColors(),
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "Add",
                onClick = { if (input.isNotBlank()) onNewPerson(input.trim()) },
                enabled = input.isNotBlank(),
            )
            Spacer(Modifier.height(16.dp))
            Text("Contact picker comes in P0c-3.", color = palette.muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
    }
}
