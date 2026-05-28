package app.hisaab.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.HisaabTypography
import app.hisaab.design.LocalHisaabPalette

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    val palette = LocalHisaabPalette.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = HisaabSpacing.gutter),
        ) {
            Spacer(Modifier.height(HisaabSpacing.lg))
            Text(
                text = "Today",
                style = HisaabTypography.title,
                color = palette.onBackground,
            )
            Spacer(Modifier.height(HisaabSpacing.xs))
            Text(
                text = "Your ledger lives here. Empty for now — first entries arrive in P1.",
                style = HisaabTypography.body,
                color = palette.muted,
            )
        }
    }
}
