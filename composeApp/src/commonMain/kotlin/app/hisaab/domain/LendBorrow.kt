package app.hisaab.domain

enum class LendBorrowDirection { LENT, BORROWED }
enum class LendBorrowStatus { OPEN, SETTLED, PARTIAL }

data class NewLendBorrow(
    val personId: String,
    val amount: Double,
    val direction: LendBorrowDirection,
    val accountId: String,
    val purpose: String?,
    val ts: Long,
    val dueDate: Long?,
)

data class LendBorrowRow(
    val id: String,
    val personId: String,
    val personName: String,
    val amount: Double,
    val direction: LendBorrowDirection,
    val purpose: String?,
    val ts: Long,
    val dueDate: Long?,
    val status: LendBorrowStatus,
)
