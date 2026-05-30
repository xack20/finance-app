package app.hisaab.util

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Multiplatform money formatting.
 *
 * `String.format` / `"%.2f".format(x)` is a JVM-only stdlib extension (it delegates to
 * `java.util.Formatter`) and does not exist on Kotlin/Native (iOS) or Kotlin/Wasm, so we
 * format manually. Rounds the magnitude half-away-from-zero to [decimals] places and then
 * applies the sign, keeping behaviour symmetric across positive and negative values.
 */
fun Double.toMoneyString(decimals: Int = 2): String {
    require(decimals in 0..6) { "decimals must be in 0..6, was $decimals" }

    var factor = 1L
    repeat(decimals) { factor *= 10 }

    val scaled = (abs(this) * factor).roundToLong()
    val whole = scaled / factor
    val sign = if (this < 0 && scaled != 0L) "-" else ""

    if (decimals == 0) return "$sign$whole"

    val frac = scaled % factor
    return "$sign$whole.${frac.toString().padStart(decimals, '0')}"
}
