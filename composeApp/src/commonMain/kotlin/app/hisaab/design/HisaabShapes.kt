package app.hisaab.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object HisaabShapes {
    val card  = RoundedCornerShape(10.dp)
    val sheet = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
    val pill  = RoundedCornerShape(999.dp)

    fun material3(): Shapes = Shapes(
        small  = RoundedCornerShape(6.dp),
        medium = RoundedCornerShape(10.dp),
        large  = RoundedCornerShape(18.dp),
    )
}
