package app.hisaab.design

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** WCAG 2.x relative-luminance contrast ratio between two opaque colors (1.0–21.0). */
object Wcag {
    private fun lin(c: Float): Float =
        if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    private fun luminance(c: Color): Float =
        0.2126f * lin(c.red) + 0.7152f * lin(c.green) + 0.0722f * lin(c.blue)

    fun ratio(a: Color, b: Color): Float {
        val la = luminance(a); val lb = luminance(b)
        val hi = maxOf(la, lb); val lo = minOf(la, lb)
        return (hi + 0.05f) / (lo + 0.05f)
    }
}
