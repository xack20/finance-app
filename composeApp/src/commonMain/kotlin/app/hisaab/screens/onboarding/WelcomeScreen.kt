package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

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
            .padding(HisaabSpacing.gutter),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("হিসাব · Hisaab", color = palette.accent, fontSize = 12.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Your money,\nonly yours.",
                color = palette.onBackground,
                style = MaterialTheme.typography.displaySmall,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Privacy-first finance for Bangladesh. Everything stays on your device.",
                color = palette.muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Column {
            error?.let {
                Text(it, color = palette.negative, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
            }
            Text("Phone number", color = palette.muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("1X XXXX XXXX", color = palette.muted) },
                prefix = { Text("+880 ", color = palette.accent) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (phone.length >= 10) onSendOtp("+880$phone")
                }),
                singleLine = true,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSendOtp("+880$phone") },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                enabled = phone.length >= 10 && !isLoading,
                colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
            ) {
                if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = palette.background)
                else Text("Continue", color = palette.background)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
