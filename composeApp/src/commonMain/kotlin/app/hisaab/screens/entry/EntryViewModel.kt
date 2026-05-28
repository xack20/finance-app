package app.hisaab.screens.entry

import app.hisaab.data.AttachmentRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.domain.LendBorrowDirection
import app.hisaab.domain.NewLendBorrow
import app.hisaab.domain.NewSplitTransaction
import app.hisaab.domain.NewTransaction
import app.hisaab.domain.TxnKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EntryViewModel(
    private val txnRepo: TransactionRepository,
    private val lendBorrowRepo: LendBorrowRepository,
    private val personRepo: PersonRepository,
    private val attachmentRepo: AttachmentRepository? = null,  // optional in tests
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow(EntryFormState())
    val state: StateFlow<EntryFormState> = _state

    fun setKind(k: TxnKind) {
        _state.update {
            // Clear person/dueDate when leaving lend/borrow.
            if (k !in setOf(TxnKind.LEND, TxnKind.BORROW)) {
                it.copy(kind = k, personId = null, newPersonName = null, newPersonPhone = null, dueDate = null)
            } else it.copy(kind = k)
        }
    }

    fun setAmount(a: String) { _state.update { it.copy(amount = a) } }
    fun setAccount(id: String) { _state.update { it.copy(accountId = id) } }
    fun setCategory(id: String) { _state.update { it.copy(categoryId = id) } }
    fun setWhen(ms: Long) { _state.update { it.copy(whenMs = ms) } }
    fun setMerchant(name: String) { _state.update { it.copy(merchantName = name) } }
    fun setNotes(n: String) { _state.update { it.copy(notes = n) } }

    fun addTag(name: String) {
        _state.update { if (name in it.tagNames) it else it.copy(tagNames = it.tagNames + name) }
    }
    fun removeTag(name: String) { _state.update { it.copy(tagNames = it.tagNames - name) } }

    fun setAttachment(bytes: ByteArray, mimeType: String) {
        _state.update { it.copy(attachmentBytes = bytes, attachmentMimeType = mimeType) }
    }
    fun clearAttachment() {
        _state.update { it.copy(attachmentBytes = null, attachmentMimeType = null) }
    }

    fun setSplits(splits: List<NewSplitTransaction>) {
        _state.update { it.copy(splits = splits) }
    }

    fun setExistingPerson(id: String) {
        _state.update { it.copy(personId = id, newPersonName = null, newPersonPhone = null) }
    }
    fun setNewPerson(name: String, phone: String?) {
        _state.update { it.copy(personId = null, newPersonName = name, newPersonPhone = phone) }
    }
    fun setDueDate(ms: Long?) { _state.update { it.copy(dueDate = ms) } }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.isValid) return
        val amount = s.amount.toDoubleOrNull() ?: return
        _state.update { it.copy(isSaving = true, error = null) }

        scope.launch {
            try {
                val txnId: String? = when (s.kind) {
                    TxnKind.LEND, TxnKind.BORROW -> {
                        val personId = s.personId ?: when {
                            !s.newPersonName.isNullOrBlank() && !s.newPersonPhone.isNullOrBlank() ->
                                personRepo.upsertFromContact(s.newPersonName, s.newPersonPhone)
                            !s.newPersonName.isNullOrBlank() ->
                                personRepo.addManual(s.newPersonName)
                            else -> error("Person required for ${s.kind}")
                        }
                        val direction = if (s.kind == TxnKind.LEND) LendBorrowDirection.LENT else LendBorrowDirection.BORROWED
                        val (_, linkedTxnId) = lendBorrowRepo.record(NewLendBorrow(
                            personId = personId,
                            amount = amount,
                            direction = direction,
                            accountId = s.accountId!!,
                            purpose = s.notes.ifBlank { null },
                            ts = s.whenMs,
                            dueDate = s.dueDate,
                        ))
                        linkedTxnId
                    }
                    else -> {
                        val newTxnId = txnRepo.add(NewTransaction(
                            accountId = s.accountId!!,
                            amount = amount,
                            ts = s.whenMs,
                            merchantName = s.merchantName.ifBlank { null },
                            categoryId = s.categoryId,
                            notes = s.notes.ifBlank { null },
                            kind = s.kind,
                            tagNames = s.tagNames,
                        ))
                        if (s.splits.isNotEmpty()) {
                            txnRepo.addSplits(newTxnId, s.splits)
                        }
                        newTxnId
                    }
                }
                if (s.attachmentBytes != null && txnId != null && attachmentRepo != null) {
                    attachmentRepo.attach(txnId, s.attachmentBytes, s.attachmentMimeType ?: "image/jpeg")
                }
                _state.update { it.copy(isSaving = false) }
                onDone()
            } catch (e: Throwable) {
                _state.update { it.copy(isSaving = false, error = e.message ?: "Save failed") }
            }
        }
    }
}
