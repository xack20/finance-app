package app.hisaab.screens.entry

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.domain.Account
import app.hisaab.domain.Category

/**
 * Hero amount input — large numeric field with currency symbol.
 * Stateless: caller owns [value]; filtered to digits and '.' via [onChange].
 */
@Composable
fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHisaabPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("৳ ", color = palette.accent, fontSize = 48.sp)
        Box(modifier = Modifier.fillMaxWidth()) {
            BasicTextField(
                value = value,
                onValueChange = { input -> onChange(input.filter { it.isDigit() || it == '.' }) },
                textStyle = TextStyle(
                    color = palette.onBackground,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (value.isBlank()) {
                Text(
                    "0",
                    color = palette.muted,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/**
 * Account selector — shows the currently selected account name as a tappable row,
 * opening a bottom sheet with the full account list.
 * Stateless: caller owns [selectedId]; [onSelect] receives the chosen account id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPicker(
    accounts: List<Account>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Account",
) {
    val palette = LocalHisaabPalette.current
    var showSheet by remember { mutableStateOf(false) }
    val selectedName = accounts.firstOrNull { it.id == selectedId }?.name ?: "Select"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showSheet = true }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.muted, modifier = Modifier.weight(1f))
        Text(selectedName, color = palette.onBackground)
        Spacer(Modifier.width(6.dp))
        Text("›", color = palette.muted)
    }
    HorizontalDivider(color = palette.rule)

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = palette.background,
        ) {
            Column(modifier = Modifier.padding(22.dp).fillMaxWidth()) {
                Text("Account", color = palette.accent, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                accounts.forEach { acc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(acc.id)
                                showSheet = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(acc.name, color = palette.onBackground, modifier = Modifier.weight(1f))
                        if (acc.id == selectedId) Text("✓", color = palette.accent)
                    }
                    HorizontalDivider(color = palette.rule)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Category selector — shows the currently selected category name as a tappable row,
 * opening a scrollable bottom sheet with the full category list.
 * Stateless: caller owns [selectedId]; [onSelect] receives the chosen category id.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHisaabPalette.current
    var showSheet by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == selectedId }?.name ?: "Select"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showSheet = true }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Category", color = palette.muted, modifier = Modifier.weight(1f))
        Text(selectedName, color = palette.onBackground)
        Spacer(Modifier.width(6.dp))
        Text("›", color = palette.muted)
    }
    HorizontalDivider(color = palette.rule)

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = palette.background,
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("Category", color = palette.accent, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                categories.forEach { cat ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(cat.id)
                                showSheet = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        cat.icon?.let { Text(it, modifier = Modifier.width(28.dp)) }
                        Text(cat.name, color = palette.onBackground, modifier = Modifier.weight(1f))
                        if (cat.id == selectedId) Text("✓", color = palette.accent)
                    }
                    HorizontalDivider(color = palette.rule)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Notes multi-line text field.
 * Stateless: caller owns [value]; [onChange] receives the updated string.
 */
@Composable
fun NotesField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHisaabPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text("Notes", color = palette.muted) },
        modifier = modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 4,
    )
}
