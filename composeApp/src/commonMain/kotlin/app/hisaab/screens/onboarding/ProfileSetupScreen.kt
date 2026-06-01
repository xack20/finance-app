package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.MidnightTextField
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.SectionHeader

@Composable
fun ProfileSetupScreen(
    onComplete: (name: String, locale: String) -> Unit,
    isLoading: Boolean = false,
) {
    val palette = LocalHisaabPalette.current
    var name by remember { mutableStateOf("") }
    var locale by remember { mutableStateOf("en") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(HisaabSpacing.gutter),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            SectionHeader(title = "What should we call you?", eyebrow = "Almost done")
            Spacer(Modifier.height(HisaabSpacing.xl))
            MidnightTextField(
                value = name,
                onValueChange = { name = it },
                label = "Your name",
                placeholder = "Name",
                imeAction = ImeAction.Next,
                big = true,
            )
            Spacer(Modifier.height(HisaabSpacing.lg))
            // Inline 2-up segmented locale control (screen-local, not a new primitive).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                listOf("English" to "en", "বাংলা" to "bn").forEach { (label, value) ->
                    val selected = locale == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .clip(HisaabShapes.pill)
                            .background(if (selected) palette.accent else palette.glass)
                            .then(
                                if (!selected) Modifier.border(1.dp, palette.hair, HisaabShapes.pill)
                                else Modifier
                            )
                            .clickable { locale = value },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = if (selected) palette.onAccent else palette.onBackground,
                        )
                    }
                }
            }
        }
        PrimaryButton(
            text = "Start Hisaab",
            trailingGlyph = "→",
            onClick = { onComplete(name, locale) },
            enabled = name.isNotBlank() && !isLoading,
            loading = isLoading,
        )
    }
}
