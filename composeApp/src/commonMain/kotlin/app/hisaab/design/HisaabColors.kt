package app.hisaab.design

import androidx.compose.ui.graphics.Color

object HisaabColors {

    data class Palette(
        val background: Color,
        val surface: Color,
        val onBackground: Color,
        val muted: Color,
        val rule: Color,
        val accent: Color,
        val gold: Color,
        val positive: Color,
        val negative: Color,
    )

    val Light = Palette(
        background = Color(0xFFFAF7F2),
        surface    = Color(0xFFFFFFFF),
        onBackground = Color(0xFF1A1A1A),
        muted      = Color(0xFF6F6453),
        rule       = Color(0xFFE6DCCB),
        accent     = Color(0xFFAD6B2A),
        gold       = Color(0xFFC8964A),
        positive   = Color(0xFF2E7D4F),
        negative   = Color(0xFFB5402C),
    )

    val Dark = Palette(
        background = Color(0xFF0F0C08),
        surface    = Color(0xFF1A1410),
        onBackground = Color(0xFFF5EDE0),
        muted      = Color(0xFFB3A288),
        rule       = Color(0xFF2A2218),
        accent     = Color(0xFFD68945),
        gold       = Color(0xFFD8A05A),
        positive   = Color(0xFF2E7D4F),
        negative   = Color(0xFFE26B57),
    )
}
