package app.hisaab.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.LocalAppContainer
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.GlassButton
import app.hisaab.design.components.GradientAvatar
import app.hisaab.design.components.MidnightSheet
import app.hisaab.design.components.MoneyText
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.SectionHeader
import app.hisaab.design.components.SurfaceCard
import app.hisaab.domain.PersonWithBalance
import kotlinx.coroutines.launch

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionHeader(title = "People", modifier = Modifier.weight(1f))
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
            LazyColumn(modifier = Modifier.padding(horizontal = 20.dp)) {
                items(people, key = { it.person.id }) { pwb ->
                    Spacer(Modifier.height(10.dp))
                    PersonCard(pwb = pwb, onClick = { onPersonClick(pwb.person.id) })
                }
                item { Spacer(Modifier.height(10.dp)) }
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
        )
    }
}

@Composable
private fun PersonCard(pwb: PersonWithBalance, onClick: () -> Unit) {
    val palette = LocalHisaabPalette.current
    SurfaceCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GradientAvatar(name = pwb.person.name, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pwb.person.name,
                    color = palette.onBackground,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                val statusText = when {
                    pwb.balance > 0 -> "Owes you"
                    pwb.balance < 0 -> "You owe"
                    else -> "Settled"
                }
                Text(
                    text = statusText,
                    color = palette.muted,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (pwb.balance == 0.0) {
                Text("Settled", color = palette.muted, fontSize = 13.sp)
            } else {
                MoneyText(
                    amount = pwb.balance,
                    signed = true,
                    decimals = 0,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPersonSheet(
    onAddManual: (String) -> Unit,
    onAddFromContact: () -> Unit,
    contactPickerAvailable: Boolean,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    MidnightSheet(
        onDismiss = onDismiss,
        title = "Add person",
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        PrimaryButton(
            text = "Add",
            onClick = { if (name.isNotBlank()) onAddManual(name.trim()) },
            enabled = name.isNotBlank(),
        )
        if (contactPickerAvailable) {
            Spacer(Modifier.height(8.dp))
            GlassButton(
                text = "Or pick from contacts",
                onClick = onAddFromContact,
            )
        }
    }
}
