package app.hisaab.domain

import kotlin.jvm.JvmInline

@JvmInline
value class YearMonth(val value: String) {
    init {
        require(value.length == 7 && value[4] == '-') { "YearMonth must be 'YYYY-MM', got '$value'" }
    }

    val year: Int get() = value.substring(0, 4).toInt()
    val month: Int get() = value.substring(5, 7).toInt()

    fun previous(): YearMonth {
        val y = year
        val m = month
        return if (m == 1) of(y - 1, 12) else of(y, m - 1)
    }

    companion object {
        fun of(year: Int, month: Int): YearMonth {
            require(month in 1..12) { "month must be 1..12" }
            return YearMonth("$year-${month.toString().padStart(2, '0')}")
        }
    }
}
