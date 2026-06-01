package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.GlassButton
import app.hisaab.design.components.NumericKeypad
import app.hisaab.design.components.OtpCells
import app.hisaab.design.components.PrimaryButton

@Composable
fun OtpScreen(
    phone: String,
    onVerify: (token: String) -> Unit,
    onResend: () -> Unit,
    isLoading: Boolean = false,
    error: String? = null,
) {
    val palette = LocalHisaabPalette.current
    var otp by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(HisaabSpacing.gutter),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Eyebrow("Verify", color = palette.accent)
            Spacer(Modifier.height(12.dp))
            Text(
                "Enter the code\nwe sent you",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 38.sp),
                color = palette.onBackground,
            )
            Spacer(Modifier.height(12.dp))
            // "Sent to {phone}" — all mono/muted (design does not lime-highlight the number).
            Text(
                "Sent to $phone",
                color = palette.muted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal),
            )
            Spacer(Modifier.height(28.dp))
            OtpCells(value = otp, modifier = Modifier.fillMaxWidth(), isError = error != null)
            error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = palette.negative, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(26.dp))
            // On-screen numeric keypad — no OS keyboard (also fixes the "numpad won't dismiss" issue).
            NumericKeypad(
                onDigit = { d -> if (otp.length < 6) otp += d },
                onBackspace = { otp = otp.dropLast(1) },
            )
        }
        Column {
            PrimaryButton(
                text = "Verify",
                onClick = { onVerify(otp) },
                modifier = Modifier.fillMaxWidth(),
                enabled = otp.length == 6 && !isLoading,
                loading = isLoading,
                fillMaxWidth = true,
            )
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassButton(
                    text = "Resend code",
                    onClick = onResend,
                    fillMaxWidth = false,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
