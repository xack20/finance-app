package app.hisaab.domain

enum class TxnKind { EXPENSE, INCOME, TRANSFER, LEND, BORROW, SETTLEMENT }

enum class TxnSource { SMS, EMAIL, VOICE, MANUAL, OCR, RECURRING }

data class NewTransaction(
    val accountId: String,
    val amount: Double,
    val currency: String = "BDT",
    val ts: Long,
    val merchantName: String?,
    val categoryId: String?,
    val source: TxnSource = TxnSource.MANUAL,
    val notes: String?,
    val kind: TxnKind,
    val tagNames: List<String> = emptyList(),
)

data class NewSplitTransaction(
    val amount: Double,
    val categoryId: String?,
    val notes: String?,
    val kind: TxnKind,
)

data class TransactionPatch(
    val amount: Double? = null,
    val ts: Long? = null,
    val merchantName: String? = null,
    val categoryId: String? = null,
    val notes: String? = null,
    val kind: TxnKind? = null,
)

data class TransactionRow(
    val id: String,
    val accountId: String,
    val accountName: String,
    val amount: Double,
    val currency: String,
    val ts: Long,
    val merchantId: String?,
    val merchantName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryColor: String?,
    val source: TxnSource,
    val notes: String?,
    val kind: TxnKind,
    val parentTxnId: String?,
)
