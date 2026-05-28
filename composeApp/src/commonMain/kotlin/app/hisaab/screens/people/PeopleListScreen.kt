package app.hisaab.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.HisaabColors
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.PersonWithBalance
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleListScreen(onPersonClick: (String) -> Unit) {
    val palette = LocalHisaabPalette.current
    val container = LocalAppContainer.current
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember {
        PeopleViewModel(
            personRepo = container.personRepository,
            lendBorrowRepo = container.lendBorrowRepository,
            accountRepo = container.accountRepository,
        )
    }
    val people by viewModel.people.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(palette.background)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "People",
                style = MaterialTheme.typography.displaySmall,
                color = palette.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showAddSheet = true }) {
                Text("+ Add", color = palette.accent)
            }
        }

        if (people.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No one yet.\nLend or borrow to add a person.",
                    color = palette.muted,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(horizontal = 22.dp)) {
                items(people, key = { it.person.id }) { pwb ->
                    PersonRow(pwb = pwb, palette = palette, onClick = { onPersonClick(pwb.person.id) })
                    HorizontalDivider(color = palette.rule)
                }
            }
        }
    }

    if (showAddSheet) {
        AddPersonSheet(
            onAddManual = { name ->
                viewModel.addManualPerson(name)
                showAddSheet = false
            },
            onAddFromContact = {
                coroutineScope.launch {
                    val pick = container.contactPicker.pickContact()
                    if (pick != null) {
                        viewModel.upsertFromContact(pick.displayName, pick.phone)
                    }
                    showAddSheet = false
                }
            },
            contactPickerAvailable = container.contactPicker.isAvailable(),
            onDismiss = { showAddSheet = false },
            palette = palette,
        )
    }
}

@Composable
private fun PersonRow(pwb: PersonWithBalance, palette: HisaabColors.Palette, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(pwb.person.name, color = palette.onBackground, style = MaterialTheme.typography.bodyLarge)
            if (!pwb.person.contactRef.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(pwb.person.contactRef.orEmpty(), color = palette.muted, fontSize = 11.sp)
            }
        }
        val (sign, color) = when {
            pwb.balance > 0 -> "+" to palette.positive   // they owe you
            pwb.balance < 0 -> "−" to palette.negative   // you owe them
            else -> "" to palette.muted
        }
        Text(
            if (pwb.balance == 0.0) "Settled"
            else "$sign৳${kotlin.math.abs(pwb.balance).toInt()}",
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPersonSheet(
    onAddManual: (String) -> Unit,
    onAddFromContact: () -> Unit,
    contactPickerAvailable: Boolean,
    onDismiss: () -> Unit,
    palette: HisaabColors.Palette,
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.background,
    ) {
        Column(modifier = Modifier.padding(22.dp).fillMaxWidth()) {
            Text("Add person", color = palette.accent, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name", color = palette.muted) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { if (name.isNotBlank()) onAddManual(name.trim()) },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) { Text("Add", color = palette.background) }
            if (contactPickerAvailable) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = onAddFromContact,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Or pick from contacts", color = palette.accent) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
