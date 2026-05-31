package app.hisaab.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object HisaabShapes {
    val field = RoundedCornerShape(16.dp)
    val card  = RoundedCornerShape(22.dp)
    val sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    val pill  = RoundedCornerShape(999.dp)

    fun material3(): Shapes = Shapes(
        small  = field,
        medium = card,
        large  = RoundedCornerShape(26.dp),
    )
}
