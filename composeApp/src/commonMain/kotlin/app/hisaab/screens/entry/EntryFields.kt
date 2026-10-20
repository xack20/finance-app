package app.hisaab.screens.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.categoryHue
import app.hisaab.design.components.midnightOutlinedColors
import app.hisaab.domain.Account
import app.hisaab.domain.Category

/** Seeded category id -> (HisaabIcons name, categoryHue key) for the quick-row (neo.jsx:175). */
private val QUICK_CATEGORY_TILES: List<Triple<String, String, String>> = listOf(
    Triple("food", "food", "rose"),
    Triple("shopping", "bag", "amber"),
    Triple("transport", "car", "blue"),
    Triple("bills", "receipt", "violet"),
    Triple("health", "health", "teal"),
    Triple("entertainment", "film", "pink"),
)

/**
 * Horizontal no-scrollbar row of 50dp category quick-tiles: glyph at hue over a 16%-hue fill
 * (radius 15), 2dp hue border + full opacity when selected (else .55). Stateless — [onSelect].
 */
@Composable
fun CategoryQuickRow(
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onMore: (() -> Unit)? = null,
) {
    val palette = LocalHisaabPalette.current
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QUICK_CATEGORY_TILES.forEach { (id, iconName, hueKey) ->
            val hue = categoryHue(hueKey)
            val selected = selectedId == id
            Column(
                modifier = Modifier.alpha(if (selected) 1f else 0.55f).clickable { onSelect(id) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(hue.copy(alpha = 0.16f))
                        .border(2.dp, if (selected) hue else Color.Transparent, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) { HisaabIcon(iconName, tint = hue, size = 22.dp, strokeWidth = 1.9f) }
                Text(
                    id.replaceFirstChar { it.uppercase() },
                    color = palette.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Trailing "More" tile opens the full category picker (the quick row is a fixed shortlist).
        if (onMore != null) {
            Column(
                modifier = Modifier.clickable { onMore() },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.hair, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) { HisaabIcon("plus", tint = palette.muted, size = 22.dp, strokeWidth = 1.9f) }
                Text(
                    "More",
                    color = palette.muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

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
        HisaabIcon("chevron-right", tint = palette.muted, size = 18.dp)
    }
    HorizontalDivider(color = palette.rule)

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = palette.background,
        ) {
            Column(modifier = Modifier.padding(HisaabSpacing.gutter).fillMaxWidth()) {
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
                        if (acc.id == selectedId) HisaabIcon("check", tint = palette.accent, size = 18.dp, strokeWidth = 2.4f)
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
        HisaabIcon("chevron-right", tint = palette.muted, size = 18.dp)
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
                    .padding(HisaabSpacing.gutter)
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
                        if (cat.id == selectedId) HisaabIcon("check", tint = palette.accent, size = 18.dp, strokeWidth = 2.4f)
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
        shape = HisaabShapes.field,
        colors = midnightOutlinedColors(),
    )
}
