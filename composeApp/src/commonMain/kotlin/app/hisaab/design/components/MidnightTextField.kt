package app.hisaab.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.HisaabTypography
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.LocalReduceMotion

/** Midnight text field: surface row, hairline border → lime on focus, eyebrow label, optional
 *  mono lime [prefix], faint placeholder, accent cursor. [big] = 62dp hero variant. */
@Composable
fun MidnightTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    singleLine: Boolean = true,
    big: Boolean = false,
    isError: Boolean = false,
    enabled: Boolean = true,
) {
    val p = LocalHisaabPalette.current
    val monoFamily = HisaabTypography.families().mono
    var focused by remember { mutableStateOf(false) }
    val targetBorder = when {
        isError -> p.negative
        focused -> p.accent
        else -> p.hair
    }
    val border = if (LocalReduceMotion.current) targetBorder else animateColorAsState(targetBorder, label = "fieldBorder").value
    Column(modifier) {
        if (label != null) {
            Eyebrow(label)
            Spacer(Modifier.height(HisaabSpacing.sm))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (big) 62.dp else 54.dp)
                .clip(HisaabShapes.field)
                .background(p.surface, HisaabShapes.field)
                .border(1.5.dp, border, HisaabShapes.field)
                .padding(horizontal = HisaabSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (prefix != null) {
                // Mono lime prefix (e.g. "+880"); uses the same mono FontFamily as MoneyText.
                Text(prefix, color = p.accent, fontFamily = monoFamily)
                Spacer(Modifier.width(HisaabSpacing.sm))
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, color = p.faint)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = LocalTextStyle.current.copy(color = p.onBackground),
                    cursorBrush = SolidColor(p.accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    keyboardActions = KeyboardActions(
                        onDone = { onImeAction() },
                        onNext = { onImeAction() },
                        onGo = { onImeAction() },
                        onSend = { onImeAction() },
                    ),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                )
            }
        }
    }
}
