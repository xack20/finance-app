package app.hisaab.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.hisaab.design.HisaabShapes
import app.hisaab.design.HisaabSpacing
import app.hisaab.design.LocalHisaabPalette

/**
 * Midnight bottom sheet: 26dp top radius, background fill, hairline grab handle, optional eyebrow
 * title. Drop-in replacement for raw ModalBottomSheet call sites. [content] is a ColumnScope.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MidnightSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = LocalHisaabPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = p.background,
        shape = HisaabShapes.sheet,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = HisaabSpacing.md), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 38.dp, height = 4.dp).clip(RoundedCornerShape(99.dp)).background(p.hair))
            }
        },
    ) {
        Column(modifier.fillMaxWidth().padding(horizontal = HisaabSpacing.gutter).padding(bottom = HisaabSpacing.xl)) {
            if (title != null) {
                Eyebrow(title, Modifier.padding(bottom = HisaabSpacing.sm))
            }
            content()
        }
    }
}
