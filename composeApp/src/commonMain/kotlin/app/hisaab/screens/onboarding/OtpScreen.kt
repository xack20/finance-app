package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

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
            Text("Verify", color = palette.accent)
            Spacer(Modifier.height(12.dp))
            Text(
                "Enter the code\nwe sent you",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text("Sent to $phone", color = palette.muted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) otp = it },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                placeholder = { Text("6-digit code", color = palette.muted) },
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
            }
        }
        Column {
            Button(
                onClick = { onVerify(otp) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = otp.length == 6 && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = palette.background)
                else Text("Verify", color = palette.background)
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onResend, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Resend code", color = palette.muted)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
