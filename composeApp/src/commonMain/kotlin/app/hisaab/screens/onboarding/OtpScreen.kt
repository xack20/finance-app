package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.Eyebrow
import app.hisaab.design.components.GlassButton
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
            Eyebrow("Verify")
            Spacer(Modifier.height(12.dp))
            Text(
                "Enter the code\nwe sent you",
                style = MaterialTheme.typography.displaySmall,
                color = palette.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                buildAnnotatedString {
                    append("Sent to ")
                    withStyle(SpanStyle(color = palette.accent)) { append(phone) }
                },
                color = palette.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(32.dp))
            OtpCells(
                value = otp,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otp = it },
                modifier = Modifier.fillMaxWidth(),
                isError = error != null,
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
            }
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
