package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.HCheck
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.RecoveryWordGrid
import app.hisaab.design.components.SectionHeader
import app.hisaab.design.components.SurfaceCard

@Composable
fun RecoveryPhraseScreen(
    words: List<String>,
    onAcknowledged: () -> Unit,
) {
    val palette = LocalHisaabPalette.current
    var acknowledged by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
            .padding(horizontal = HisaabSpacing.gutter, vertical = HisaabSpacing.gutter),
    ) {
        SectionHeader(
            title = "Write these 24 words down",
            eyebrow = "Recovery",
        )

        Spacer(Modifier.height(HisaabSpacing.md))

        // Warning card
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                "⚠ Lose these = lose your data",
                color = palette.negative,
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Spacer(Modifier.height(HisaabSpacing.md))

        // Word grid — weight(1f) pins warning + ack + CTA below the fold
        RecoveryWordGrid(
            words = words,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.height(HisaabSpacing.md))

        // Acknowledgement inside a SurfaceCard
        SurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HCheck(
                    checked = acknowledged,
                    onCheckedChange = { acknowledged = it },
                )
                Spacer(Modifier.width(HisaabSpacing.sm))
                Text(
                    "I've written down all 24 words in a safe place",
                    color = palette.onBackground,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Spacer(Modifier.height(HisaabSpacing.md))

        PrimaryButton(
            text = "I've saved them",
            onClick = onAcknowledged,
            enabled = acknowledged,
        )
    }
}
