package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.MidnightTextField
import app.hisaab.design.components.PrimaryButton

/** Bangladesh numbers are 10 local digits, but the design enables Continue at ≥9 (neo-onboarding.jsx:25). */
private const val MIN_PHONE_DIGITS = 9

@Composable
fun WelcomeScreen(
    onSendOtp: (phone: String) -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
) {
    val palette = LocalHisaabPalette.current
    var phone by remember { mutableStateOf("") }
    val valid = phone.length >= MIN_PHONE_DIGITS && !isLoading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .imePadding()
            .padding(HisaabSpacing.gutter),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            // Two-tone wordmark eyebrow: হিসাব (lime display 26) + "HISAAB" tracked eyebrow (.28em).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "হিসাব",
                    color = palette.accent,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 28.sp,
                    ),
                )
                Text(
                    text = "HISAAB",
                    color = palette.faint,
                    style = MaterialTheme.typography.labelLarge,
                    letterSpacing = 3.08.sp, // .28em × 11sp (neo-onboarding.jsx:36)
                )
            }
            Spacer(Modifier.height(30.dp))
            Text(
                text = "Your money,\nonly yours.",
                color = palette.onBackground,
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 46.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 48.sp,
                ),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Privacy-first finance for Bangladesh. Everything stays on your device.",
                color = palette.muted,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 17.sp, lineHeight = 26.sp),
                modifier = Modifier.widthIn(max = 300.dp),
            )
            Spacer(Modifier.height(22.dp))
            // Privacy chip: lime-soft pill + shield icon + lime label (neo-onboarding.jsx:40-42).
            Row(
                modifier = Modifier
                    .clip(HisaabShapes.pill)
                    .background(palette.accentSoft)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HisaabIcon("shield", tint = palette.accent, size = 15.dp)
                Text(
                    text = "End-to-end private, on-device",
                    color = palette.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Column {
            error?.let {
                Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
            }
            MidnightTextField(
                value = phone,
                onValueChange = { phone = it.filter(Char::isDigit) },
                label = "Phone number",
                placeholder = "1X XXXX XXXX",
                prefix = "+880",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                onImeAction = { if (valid) onSendOtp("+880$phone") },
                big = true,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryButton(
                text = "Continue",
                onClick = { onSendOtp("+880$phone") },
                enabled = valid,
                loading = isLoading,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
