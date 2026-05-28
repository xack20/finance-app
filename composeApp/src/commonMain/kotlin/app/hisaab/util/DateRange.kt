package app.hisaab.util

import app.hisaab.domain.YearMonth
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/** Returns (startMs, endMsExclusive) for the UTC epoch-day containing nowMs. */
fun todayRangeMs(nowMs: Long = Clock.System.now().toEpochMilliseconds()): Pair<Long, Long> {
    val start = (nowMs / 86_400_000L) * 86_400_000L
    return start to (start + 86_400_000L)
}

/** Returns (startMs, endMsExclusive) for the UTC epoch-day containing dayMs. */
fun dayRangeMs(dayMs: Long): Pair<Long, Long> {
    val start = (dayMs / 86_400_000L) * 86_400_000L
    return start to (start + 86_400_000L)
}

/** Returns (startMs, endMsExclusive) for the calendar month in the system default TZ. */
fun monthRangeMs(yearMonth: YearMonth): Pair<Long, Long> {
    val tz = TimeZone.currentSystemDefault()
    val firstDay = LocalDate(yearMonth.year, yearMonth.month, 1)
    val start = firstDay.atStartOfDayIn(tz).toEpochMilliseconds()
    val nextFirstDay = if (yearMonth.month == 12) {
        LocalDate(yearMonth.year + 1, 1, 1)
    } else {
        LocalDate(yearMonth.year, yearMonth.month + 1, 1)
    }
    val end = nextFirstDay.atStartOfDayIn(tz).toEpochMilliseconds()
    return start to end
}
