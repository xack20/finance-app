package app.hisaab.screens.entry

import app.hisaab.domain.NewSplitTransaction
import app.hisaab.domain.TxnKind
import kotlinx.datetime.Clock

data class EntryFormState(
    val kind: TxnKind = TxnKind.EXPENSE,
    val amount: String = "",
    val accountId: String? = null,
    val categoryId: String? = null,
    val whenMs: Long = Clock.System.now().toEpochMilliseconds(),
    val merchantName: String = "",
    val notes: String = "",
    val tagNames: List<String> = emptyList(),
    val attachmentBytes: ByteArray? = null,
    val attachmentMimeType: String? = null,
    val splits: List<NewSplitTransaction> = emptyList(),
    val personId: String? = null,
    val newPersonName: String? = null,
    val newPersonPhone: String? = null,
    val dueDate: Long? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    val isValid: Boolean
        get() {
            val amountValid = amount.toDoubleOrNull()?.let { it > 0 } == true
            val accountValid = accountId != null
            val personValid = kind !in setOf(TxnKind.LEND, TxnKind.BORROW) ||
                personId != null ||
                (newPersonName?.isNotBlank() == true)
            return amountValid && accountValid && personValid
        }

    // Data class with ByteArray needs custom equals/hashCode — but for our purposes the
    // form state is only ever compared by reference (single StateFlow source), so the
    // default generated implementations are OK.
}
