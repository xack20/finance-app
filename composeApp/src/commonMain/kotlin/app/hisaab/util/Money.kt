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

private const val TAKA = "৳"   // ৳  BENGALI RUPEE SIGN  U+09F3
private const val MINUS = "−"  // −  MINUS SIGN           U+2212

/** Group a non-negative integer magnitude with Western thousands separators: 62250 -> "62,250". */
fun Long.grouped(): String {
    val s = (if (this < 0) -this else this).toString()
    if (s.length <= 3) return s
    val sb = StringBuilder()
    val lead = s.length % 3
    var i = 0
    if (lead > 0) { sb.append(s, 0, lead); i = lead; if (i < s.length) sb.append(',') }
    while (i < s.length) {
        sb.append(s, i, i + 3)
        i += 3
        if (i < s.length) sb.append(',')
    }
    return sb.toString()
}

/**
 * Format an amount as Taka for display: grouped thousands, ৳ mark, optional sign.
 *  - `1200.0.toTaka()` -> "৳1,200"
 *  - `(-1250.0).toTaka()` -> "−৳1,250" (U+2212 minus)
 *  - `62250.0.toTaka(signed = true)` -> "+৳62,250"; zero is never signed
 * Magnitude is rounded to [decimals] (default 0 — paisa is rarely shown in BD UI) via [toMoneyString].
 */
fun Double.toTaka(signed: Boolean = false, decimals: Int = 0): String {
    val mag = kotlin.math.abs(this).toMoneyString(decimals) // "1200" or "1200.50", unsigned
    val dot = mag.indexOf('.')
    val whole = (if (dot >= 0) mag.substring(0, dot) else mag).toLong()
    val body = if (dot >= 0) "${whole.grouped()}${mag.substring(dot)}" else whole.grouped()
    val isZero = mag.all { it == '0' || it == '.' }
    val sign = when {
        this < 0 && !isZero -> MINUS
        signed && !isZero -> "+"
        else -> ""
    }
    return "$sign$TAKA$body"
}
