package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

/** Segmented OTP code field: [length] cells, active cell lime-bordered, mono digits. Pass-through:
 *  the caller applies its own digit/length filter in [onValueChange]. */
@Composable
fun OtpCells(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
) {
    val p = LocalHisaabPalette.current
    val focus = remember { FocusRequester() }
    Box(modifier.fillMaxWidth().clickable { focus.requestFocus() }) {
        // Hidden field owns focus + the system numeric keyboard.
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            modifier = Modifier.focusRequester(focus).size(1.dp).alpha(0f),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HisaabSpacing.sm)) {
            repeat(length) { i ->
                val filled = i < value.length
                val active = i == value.length
                val cell = when {
                    isError -> p.negative
                    active -> p.accent
                    else -> p.hair
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(HisaabShapes.field)
                        .background(p.surface, HisaabShapes.field)
                        .border(1.5.dp, cell, HisaabShapes.field),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (filled) value[i].toString() else "",
                        color = p.onBackground,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
