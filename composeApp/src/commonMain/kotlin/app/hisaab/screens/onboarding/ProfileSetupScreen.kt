package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.hisaab.design.LocalHisaabPalette

@Composable
fun ProfileSetupScreen(
    onComplete: (name: String, locale: String) -> Unit,
    isLoading: Boolean = false,
) {
    val palette = LocalHisaabPalette.current
    var name by remember { mutableStateOf("") }
    var locale by remember { mutableStateOf("en") }

    Column(
        modifier = Modifier.fillMaxSize().background(palette.background).padding(22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text("Almost done", color = palette.accent)
            Spacer(Modifier.height(12.dp))
            Text(
                "What should\nwe call you?",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.onBackground,
            )
            Spacer(Modifier.height(24.dp))
            Text("Your name", color = palette.muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Name", color = palette.muted) },
            )
            Spacer(Modifier.height(20.dp))
            Text("Language", color = palette.muted, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { locale = "en" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (locale == "en") palette.accent else palette.surface,
                        contentColor = if (locale == "en") palette.background else palette.onBackground,
                    ),
                ) { Text("English") }
                Button(
                    onClick = { locale = "bn" },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (locale == "bn") palette.accent else palette.surface,
                        contentColor = if (locale == "bn") palette.background else palette.onBackground,
                    ),
                ) { Text("বাংলা") }
            }
        }
        Button(
            onClick = { onComplete(name, locale) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = name.isNotBlank() && !isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = palette.accent),
        ) {
            if (isLoading) CircularProgressIndicator(Modifier.size(20.dp), color = palette.background)
            else Text("Start Hisaab →", color = palette.background)
        }
    }
}
