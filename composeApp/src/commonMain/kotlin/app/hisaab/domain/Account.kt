package app.hisaab.domain

enum class AccountKind { CASH, BANK, MFS, CARD, GOAL }

data class Account(
    val id: String,
    val name: String,
    val kind: AccountKind,
    val institution: String?,
    val currency: String,
    val balanceTracking: Boolean,
    val createdAt: Long,
    val archivedAt: Long?,
)
