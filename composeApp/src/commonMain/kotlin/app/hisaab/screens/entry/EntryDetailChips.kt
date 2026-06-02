package app.hisaab.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.MidnightTextField
import app.hisaab.design.components.PrimaryButton
import app.hisaab.domain.Account
import app.hisaab.domain.TxnKind
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private const val DAY_MS = 86_400_000L

private enum class EntrySheet { Account, ToAccount, When, Merchant, Note, Tags }

/**
 * The Midnight entry "detail chips" strip (neo.jsx:251-262): a horizontally-scrolling row of compact
 * pill chips — Account, [To-account on TRANSFER], [Person on LEND/BORROW], When, Merchant, Note, Tags,
 * Receipt, Split. Each chip reflects its current value (on = filled lime-soft, off = surface + hair)
 * and opens a sheet, or toggles (Receipt). Split/Person reuse the screen's existing sheets via
 * [onSplit]/[onPerson]; the rest own their sheets here. Pure presentation over existing
 * EntryViewModel setters — no logic, state shape, or data access changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailChips(
    state: EntryFormState,
    accounts: List<Account>,
    onAccount: (String) -> Unit,
    onToAccount: (String) -> Unit,
    onWhen: (Long) -> Unit,
    onMerchant: (String) -> Unit,
    onNotes: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    onReceipt: () -> Unit,
    onSplit: () -> Unit,
    onPerson: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheet by remember { mutableStateOf<EntrySheet?>(null) }

    val accountName = accounts.firstOrNull { it.id == state.accountId }?.name
    val toAccountName = accounts.firstOrNull { it.id == state.toAccountId }?.name
    val personLabel = state.newPersonName ?: state.personId?.let { "Person" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = HisaabSpacing.gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DChip("wallet", accountName ?: "Account", on = accountName != null) { sheet = EntrySheet.Account }
        if (state.kind == TxnKind.TRANSFER) {
            DChip("arrow-right", toAccountName?.let { "To $it" } ?: "To account", on = toAccountName != null) {
                sheet = EntrySheet.ToAccount
            }
        }
        if (state.kind == TxnKind.LEND || state.kind == TxnKind.BORROW) {
            DChip("people", personLabel ?: "Person", on = personLabel != null, onClick = onPerson)
        }
        DChip("calendar", whenChipLabel(state.whenMs), on = false) { sheet = EntrySheet.When }
        DChip("edit", state.merchantName.ifBlank { "Merchant" }, on = state.merchantName.isNotBlank()) {
            sheet = EntrySheet.Merchant
        }
        DChip("sms", if (state.notes.isNotBlank()) "Note ✓" else "Note", on = state.notes.isNotBlank()) {
            sheet = EntrySheet.Note
        }
        DChip("tag", if (state.tagNames.isNotEmpty()) "${state.tagNames.size} tags" else "Tags", on = state.tagNames.isNotEmpty()) {
            sheet = EntrySheet.Tags
        }
        DChip("camera", if (state.attachmentBytes != null) "Receipt ✓" else "Receipt", on = state.attachmentBytes != null, onClick = onReceipt)
        DChip("swap", if (state.splits.isNotEmpty()) "${state.splits.size} parts" else "Split", on = state.splits.isNotEmpty(), onClick = onSplit)
    }

    when (sheet) {
        EntrySheet.Account -> EntryListSheet(
            "Account", accounts.map { it.id to it.name }, state.accountId,
            onSelect = { onAccount(it); sheet = null }, onDismiss = { sheet = null },
        )
        EntrySheet.ToAccount -> EntryListSheet(
            "To account", accounts.map { it.id to it.name }, state.toAccountId,
            onSelect = { onToAccount(it); sheet = null }, onDismiss = { sheet = null },
        )
        EntrySheet.When -> EntryWhenSheet(state.whenMs, onPick = { onWhen(it); sheet = null }, onDismiss = { sheet = null })
        EntrySheet.Merchant -> EntryTextSheet(
            "Merchant", state.merchantName, "Where did this happen?",
            onSave = { onMerchant(it); sheet = null }, onDismiss = { sheet = null },
        )
        EntrySheet.Note -> EntryTextSheet(
            "Note", state.notes, "Add a note",
            onSave = { onNotes(it); sheet = null }, onDismiss = { sheet = null },
        )
        EntrySheet.Tags -> EntryTagsSheet(state.tagNames, onAddTag, onRemoveTag, onDismiss = { sheet = null })
        null -> Unit
    }
}

/** A single Midnight detail chip (neo.jsx:177): pill, icon + label, lime-soft when [on]. */
@Composable
private fun DChip(icon: String, label: String, on: Boolean, onClick: () -> Unit) {
    val p = LocalHisaabPalette.current
    val fg = if (on) p.accent else p.muted
    Row(
        modifier = Modifier
            .clip(HisaabShapes.pill)
            .background(if (on) p.accentSoft else p.surface)
            .then(if (on) Modifier else Modifier.border(1.dp, p.hair, HisaabShapes.pill))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        HisaabIcon(icon, tint = fg, size = 15.dp)
        Text(label, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Generic single-select bottom sheet over (id → name) options (account, to-account, category). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntryListSheet(
    title: String,
    items: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalHisaabPalette.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = p.background) {
        Column(
            modifier = Modifier
                .padding(HisaabSpacing.gutter)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, color = p.accent, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            if (items.isEmpty()) {
                Text("Nothing to choose yet.", color = p.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
            }
            items.forEach { (id, name) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(id) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, color = p.onBackground, modifier = Modifier.weight(1f))
                    if (id == selectedId) HisaabIcon("check", tint = p.accent, size = 18.dp, strokeWidth = 2.4f)
                }
                HorizontalDivider(color = p.rule)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Single-field text sheet (merchant, note) — autofocused, Done commits. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTextSheet(
    title: String,
    initial: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalHisaabPalette.current
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = p.background) {
        Column(modifier = Modifier.padding(HisaabSpacing.gutter).fillMaxWidth().imePadding()) {
            MidnightTextField(
                value = text,
                onValueChange = { text = it },
                label = title,
                placeholder = placeholder,
                imeAction = ImeAction.Done,
                onImeAction = { onSave(text.trim()) },
                focusRequester = focus,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "Save", onClick = { onSave(text.trim()) })
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Tag editor sheet: add via the field, tap a tag pill to remove. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EntryTagsSheet(
    tags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = LocalHisaabPalette.current
    var input by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = p.background) {
        Column(modifier = Modifier.padding(HisaabSpacing.gutter).fillMaxWidth().imePadding()) {
            MidnightTextField(
                value = input,
                onValueChange = { input = it },
                label = "Tags",
                placeholder = "Add a tag",
                imeAction = ImeAction.Done,
                onImeAction = { if (input.isNotBlank()) { onAdd(input.trim()); input = "" } },
            )
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tags.forEach { tag ->
                        Row(
                            modifier = Modifier
                                .clip(HisaabShapes.pill)
                                .background(p.surface)
                                .clickable { onRemove(tag) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(tag, color = p.onBackground, fontSize = 13.sp)
                            HisaabIcon("close", tint = p.muted, size = 13.dp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * "When" sheet with quick relative-day picks (Now / Yesterday / N days ago). A full calendar is
 * deferred; these cover the common backfill cases via the existing [EntryViewModel.setWhen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryWhenSheet(currentMs: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val p = LocalHisaabPalette.current
    val now = Clock.System.now().toEpochMilliseconds()
    val options = listOf(
        "Now" to now,
        "Yesterday" to now - DAY_MS,
        "2 days ago" to now - 2 * DAY_MS,
        "3 days ago" to now - 3 * DAY_MS,
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(), containerColor = p.background) {
        Column(modifier = Modifier.padding(HisaabSpacing.gutter).fillMaxWidth()) {
            Text("When", color = p.accent, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text(whenFull(currentMs), color = p.muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            options.forEach { (label, ms) ->
                val selected = sameDay(ms, currentMs)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(ms) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, color = p.onBackground, modifier = Modifier.weight(1f))
                    if (selected) HisaabIcon("check", tint = p.accent, size = 18.dp, strokeWidth = 2.4f)
                }
                HorizontalDivider(color = p.rule)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun two(n: Int): String = if (n < 10) "0$n" else "$n"

private fun whenChipLabel(ms: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val d = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz).date
    val today = Clock.System.now().toLocalDateTime(tz).date
    return when (today.toEpochDays() - d.toEpochDays()) {
        0 -> "Today"
        1 -> "Yesterday"
        else -> "${d.year}-${two(d.monthNumber)}-${two(d.dayOfMonth)}"
    }
}

private fun whenFull(ms: Long): String {
    val ldt = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${ldt.year}-${two(ldt.monthNumber)}-${two(ldt.dayOfMonth)} ${two(ldt.hour)}:${two(ldt.minute)}"
}

private fun sameDay(a: Long, b: Long): Boolean {
    val tz = TimeZone.currentSystemDefault()
    return Instant.fromEpochMilliseconds(a).toLocalDateTime(tz).date ==
        Instant.fromEpochMilliseconds(b).toLocalDateTime(tz).date
}
