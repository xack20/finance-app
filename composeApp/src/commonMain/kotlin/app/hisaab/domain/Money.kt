package app.hisaab.domain

data class MoneyTotals(
    val income: Double,
    val expense: Double,
    val net: Double,
)

data class MonthlyTotals(
    val yearMonth: YearMonth,
    val income: Double,
    val expense: Double,
    val net: Double,
    val previousMonthNet: Double,
)

data class CategorySlice(
    val categoryId: String,
    val categoryName: String,
    val categoryColor: String?,
    val total: Double,
    val percent: Double,
)

data class DayBucket(
    val epochDay: Long,
    val total: Double,
)

data class RecurringHit(
    val merchantId: String,
    val merchantName: String,
    val occurrenceCount: Long,
    val avgAmount: Double,
    val lastSeenTs: Long,
)
