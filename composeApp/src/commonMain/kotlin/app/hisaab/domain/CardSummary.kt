package app.hisaab.domain
import kotlinx.datetime.LocalDate

/** Pure next-due-date math. statementDay/dueDay are expected in 1..28 but the day is clamped defensively. */
object CardSummaryCalculator {
    fun nextDueDate(today: LocalDate, statementDay: Int, dueDay: Int): LocalDate {
        // 1. month of the most recent statement close (on statementDay) that is <= today
        var closeYear = today.year
        var closeMonth = today.monthNumber
        if (today.dayOfMonth < statementDay) {
            if (closeMonth == 1) { closeYear -= 1; closeMonth = 12 } else closeMonth -= 1
        }
        // 2. due date is in the close month, advancing one month when dueDay <= statementDay
        var dueYear = closeYear
        var dueMonth = closeMonth
        if (dueDay <= statementDay) {
            if (dueMonth == 12) { dueYear += 1; dueMonth = 1 } else dueMonth += 1
        }
        // 3. clamp the day to the month length
        val day = minOf(dueDay, lastDayOfMonth(dueYear, dueMonth))
        return LocalDate(dueYear, dueMonth, day)
    }

    private fun lastDayOfMonth(year: Int, month: Int): Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        else -> if (isLeapYear(year)) 29 else 28
    }

    private fun isLeapYear(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)
}
