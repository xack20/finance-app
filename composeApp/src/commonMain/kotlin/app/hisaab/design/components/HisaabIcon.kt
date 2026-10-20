package app.hisaab.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Midnight stroke icon set, ported verbatim from the design handoff
 * (docs/design/design_handoff_hisaab_midnight/src/ui.jsx:8-52). Each entry is an SVG `d` path on a
 * 24×24 grid, rendered round-capped/round-joined exactly as the reference `<Icon>` component.
 *
 * Use [HisaabIcon] instead of Material icons or emoji/Unicode text glyphs anywhere the design
 * specifies `Icon name="…"`. Replacing the previous text glyphs (•☷○⚙ ✦ 🎙 ↑ ☝ ⚠ ✉ › ✓) with
 * these gives platform-independent, on-brand vectors.
 */
object HisaabIcons {
    val paths: Map<String, String> = mapOf(
        "chevron-right" to "M9 5l7 7-7 7",
        "chevron-left" to "M15 5l-7 7 7 7",
        "chevron-down" to "M5 9l7 7 7-7",
        "plus" to "M12 5v14M5 12h14",
        "check" to "M4 12.5l5 5 11-11",
        "close" to "M5 5l14 14M19 5L5 19",
        "arrow-up" to "M12 19V5M5 12l7-7 7 7",
        "arrow-down" to "M12 5v14M5 12l7 7 7-7",
        "arrow-right" to "M5 12h14M13 6l6 6-6 6",
        "lend" to "M6 18L18 6M10 6h8v8",
        "borrow" to "M18 6L6 18M6 10v8h8",
        "swap" to "M7 7h11l-3-3M17 17H6l3 3",
        "lock" to "M6 10V8a6 6 0 0112 0v2M5 10h14v10H5z",
        "finger" to "M8 11a4 4 0 018 0v3M8 14v2a4 4 0 004 4M12 11v5M16 13v2",
        "mic" to "M12 3a3 3 0 00-3 3v5a3 3 0 006 0V6a3 3 0 00-3-3zM5 11a7 7 0 0014 0M12 18v3",
        "gear" to "M12 9a3 3 0 100 6 3 3 0 000-6zM19.4 13a1 1 0 00.2 1.1l.1.1a2 2 0 11-2.8 2.8l-.1-.1a1 1 0 00-1.1-.2 1 1 0 00-.6.9V19a2 2 0 11-4 0v-.1a1 1 0 00-.6-.9 1 1 0 00-1.1.2l-.1.1a2 2 0 11-2.8-2.8l.1-.1a1 1 0 00.2-1.1 1 1 0 00-.9-.6H5a2 2 0 110-4h.1a1 1 0 00.9-.6 1 1 0 00-.2-1.1l-.1-.1a2 2 0 112.8-2.8l.1.1a1 1 0 001.1.2H12a1 1 0 00.6-.9V5a2 2 0 114 0v.1a1 1 0 00.6.9 1 1 0 001.1-.2l.1-.1a2 2 0 112.8 2.8l-.1.1a1 1 0 00-.2 1.1V12a1 1 0 00.9.6H19a2 2 0 110 4h-.1a1 1 0 00-.9.6z",
        "today" to "M12 12m-2 0a2 2 0 104 0a2 2 0 10-4 0",
        "month" to "M4 6h7v5H4zM13 6h7v5h-7zM4 13h7v5H4zM13 13h7v5h-7z",
        "people" to "M9 11a3.5 3.5 0 100-7 3.5 3.5 0 000 7zM3 20a6 6 0 0112 0M17 11a3 3 0 100-6M21 20a6 6 0 00-4-5.6",
        "search" to "M11 11m-7 0a7 7 0 1014 0a7 7 0 10-14 0M20 20l-4-4",
        "camera" to "M3 8h3l1.5-2h9L17 8h4v11H3zM12 16a3.5 3.5 0 100-7 3.5 3.5 0 000 7z",
        "warn" to "M12 4l9 16H3zM12 10v4M12 17.5v.5",
        "shield" to "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z",
        "eye" to "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7zM12 9a3 3 0 100 6 3 3 0 000-6z",
        "trash" to "M5 7h14M10 7V5h4v2M6 7l1 13h10l1-13",
        "edit" to "M4 20h4L19 9l-4-4L4 16zM14 6l4 4",
        "tag" to "M3 12V4h8l9 9-8 8zM7.5 7.5h.01",
        "calendar" to "M5 7h14v13H5zM5 11h14M8 4v4M16 4v4",
        "receipt" to "M6 3h12v18l-2.5-1.5L13 21l-2.5-1.5L8 21 6 19.5V3zM9 8h6M9 12h6",
        "book" to "M5 5a2 2 0 012-2h11v15H7a2 2 0 00-2 2zM5 19a2 2 0 012-2h11",
        "film" to "M4 4h16v16H4zM4 8h16M4 16h16M8 4v16M16 4v16",
        "food" to "M5 3v7a2 2 0 004 0V3M7 12v9M17 3c-1.5 0-2.5 2-2.5 5s1 4 2.5 4v9",
        "cart" to "M4 5h2l2 11h10l2-8H7M9 20a1 1 0 100-2 1 1 0 000 2zM18 20a1 1 0 100-2 1 1 0 000 2z",
        // Plus inside a full circle. The original used mismatched arc flags (large=1 then large=0)
        // which the path parser rendered as a half circle; two equal sweep=1 semicircles close it.
        "health" to "M12 8v8M8 12h8M12 3a9 9 0 0 1 0 18a9 9 0 0 1 0 -18z",
        "case" to "M3 8h18v11H3zM8 8V6a2 2 0 012-2h4a2 2 0 012 2v2",
        "bag" to "M5 8h14l-1 12H6zM9 8V6a3 3 0 016 0v2",
        "car" to "M4 13l1.5-5h13L20 13M4 13h16v5H4zM7 18v1M17 18v1M7 15.5h.01M17 15.5h.01",
        "dot" to "M12 12m-3 0a3 3 0 106 0a3 3 0 10-6 0",
        "sparkle" to "M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8zM18 16l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z",
        "sms" to "M4 5h16v11H8l-4 4zM8 9h8M8 12h5",
        "in" to "M12 5v9M7 11l5 4 5-4M5 19h14",
        "cloud" to "M7 18a4 4 0 010-8 5 5 0 019.6-1.3A3.5 3.5 0 0117 18z",
        "key" to "M14 7a3 3 0 11-2 5l-7 7H3v-2l7-7a3 3 0 014-3zM15.5 8.5h.01",
        "wallet" to "M4 7h13a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V6a2 2 0 012-2h11M17 13h.01",
    )
}

/**
 * Render a Midnight stroke icon by [name] (see [HisaabIcons.paths]). Mirrors the reference SVG:
 * paths live on a 24-unit grid, scaled to [size], drawn with round caps/joins. When [filled] is
 * true the path is filled instead of stroked (used for the active "today" dock tab and the FAB
 * plus). [strokeWidth] is in the same 24-unit space as the design's `stroke` prop (default 1.8).
 * Unknown names fall back to "dot", matching the reference component.
 */
@Composable
fun HisaabIcon(
    name: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    strokeWidth: Float = 1.8f,
    filled: Boolean = false,
) {
    val pathData = HisaabIcons.paths[name] ?: HisaabIcons.paths.getValue("dot")
    val parser = remember(pathData) { PathParser().parsePathString(pathData) }
    Canvas(modifier.size(size)) {
        val scale = this.size.minDimension / 24f
        // Rebuild a fresh Path each draw, then scale the 24-grid geometry to fit [size]. Re-parsing
        // avoids mutating a shared, remembered Path in place (which would compound across frames).
        val scaled = parser.toPath().apply { transform(Matrix().apply { scale(scale, scale) }) }
        if (filled) {
            drawPath(scaled, tint, style = Fill)
        } else {
            drawPath(
                scaled,
                tint,
                style = Stroke(width = strokeWidth * scale, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}
