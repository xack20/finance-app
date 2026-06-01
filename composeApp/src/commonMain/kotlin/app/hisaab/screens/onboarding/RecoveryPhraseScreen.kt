package app.hisaab.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette
import app.hisaab.design.components.HCheck
import app.hisaab.design.components.HisaabIcon
import app.hisaab.design.components.PrimaryButton
import app.hisaab.design.components.RecoveryWordGrid
import app.hisaab.design.components.SectionHeader

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
            eyebrowColor = palette.accent,
        )

        Spacer(Modifier.height(HisaabSpacing.md))

        // Inline warning — warn icon + neg text directly on the canvas (no card).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            HisaabIcon("warn", tint = palette.negative, size = 16.dp)
            Text(
                "Lose these = lose your data",
                color = palette.negative,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }

        Spacer(Modifier.height(HisaabSpacing.md))

        // Word grid — weight(1f) pins warning + ack + CTA below the fold
        RecoveryWordGrid(
            words = words,
            modifier = Modifier.weight(1f),
        )

        Spacer(Modifier.height(HisaabSpacing.md))

        // Full-width tappable acknowledgement row (the whole row toggles ack).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.hair, RoundedCornerShape(14.dp))
                .clickable { acknowledged = !acknowledged }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HCheck(
                checked = acknowledged,
                onCheckedChange = { acknowledged = it },
            )
            Text(
                "I've written down all 24 words in a safe place",
                color = palette.onBackground,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
        }

        Spacer(Modifier.height(HisaabSpacing.md))

        PrimaryButton(
            text = "I've saved them",
            onClick = onAcknowledged,
            enabled = acknowledged,
        )
    }
}
