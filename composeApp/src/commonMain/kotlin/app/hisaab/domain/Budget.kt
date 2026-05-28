package app.hisaab.domain

data class BudgetRow(
    val id: String,
    val categoryId: String,
    val categoryName: String,
    val monthlyCapAmount: Double,
    val currency: String,
    val startsMonth: YearMonth,
    val archivedAt: Long?,
    val createdAt: Long,
)

data class BudgetProgress(
    val budget: BudgetRow,
    val spent: Double,
    val percent: Double,
)
