package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.MidnightTextField
import app.hisaab.design.components.PrimaryButton

@Composable
fun WelcomeScreen(
    onSendOtp: (phone: String) -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
) {
    val palette = LocalHisaabPalette.current
    var phone by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .imePadding()
            .padding(HisaabSpacing.gutter),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            // Bespoke lime brand line — do NOT replace with Eyebrow (uppercases + uses faint,
            // which mangles the Bengali glyph and drops the lime).
            Text(
                text = "হিসাব · Hisaab",
                color = palette.accent,
                fontSize = 12.sp,
                letterSpacing = 3.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Your money,\nonly yours.",
                color = palette.onBackground,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Privacy-first finance for Bangladesh. Everything stays on your device.",
                color = palette.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column {
            error?.let {
                Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
            }
            MidnightTextField(
                value = phone,
                onValueChange = { phone = it },
                label = "Phone number",
                placeholder = "1X XXXX XXXX",
                prefix = "+880",
                keyboardType = KeyboardType.Phone,
                imeAction = ImeAction.Done,
                onImeAction = { if (phone.length >= 10) onSendOtp("+880$phone") },
                big = true,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                text = "Continue",
                onClick = { onSendOtp("+880$phone") },
                enabled = phone.length >= 10 && !isLoading,
                loading = isLoading,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
