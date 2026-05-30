package app.hisaab.screens.entry

import app.hisaab.data.AttachmentRepository
import app.hisaab.data.CaptureInboxRepository
import app.hisaab.data.LendBorrowRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.domain.Direction
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
    private val inboxRepo: CaptureInboxRepository? = null,     // optional; required for candidate prefill
    /**
     * M3-int Fix 5: atomic insert+confirm seam. When non-null and a candidateId is present,
     * save() delegates to this lambda instead of calling txnRepo.add + inboxRepo.markConfirmed
     * separately (which would be non-atomic). Returns the new transaction's ID.
     */
    private val confirmWithEdits: (suspend (newTxn: NewTransaction, candidateId: String) -> String)? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
) {
    private val _state = MutableStateFlow(EntryFormState())
    val state: StateFlow<EntryFormState> = _state

    /** Set when this entry was opened from a pending candidate (Edit flow in Review Inbox). */
    private var prefilledCandidateId: String? = null

    /** Loads a pending candidate and seeds the entry fields for one-tap confirmation-with-edits. */
    fun prefillFromCandidate(candidateId: String) {
        scope.launch {
            val inbox = inboxRepo ?: return@launch
            val c = inbox.getById(candidateId) ?: return@launch
            prefilledCandidateId = candidateId
            c.amount?.let { setAmount(it.toString()) }
            c.proposedAccountId?.let { setAccount(it) }
            c.proposedCategoryId?.let { setCategory(it) }
            c.proposedMerchant?.let { setMerchant(it) }
            when (c.direction) {
                Direction.DEBIT -> setKind(TxnKind.EXPENSE)
                Direction.CREDIT -> setKind(TxnKind.INCOME)
                null -> {}
            }
        }
    }

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
    fun setToAccount(id: String) { _state.update { it.copy(toAccountId = id) } }
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
                    TxnKind.TRANSFER -> {
                        val fromId = requireNotNull(s.accountId) { "From account required for TRANSFER" }
                        val toId = requireNotNull(s.toAccountId) { "To account required for TRANSFER" }
                        val amount = s.amount.toDoubleOrNull()
                            ?: error("Invalid amount for TRANSFER")
                        txnRepo.transfer(
                            fromAccountId = fromId,
                            toAccountId = toId,
                            amount = amount,
                            ts = s.whenMs,
                            notes = s.notes.ifBlank { null },
                        )
                    }
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
                            accountId = requireNotNull(s.accountId) { "Account required for ${s.kind}" },
                            purpose = s.notes.ifBlank { null },
                            ts = s.whenMs,
                            dueDate = s.dueDate,
                        ))
                        linkedTxnId
                    }
                    else -> {
                        val capId = prefilledCandidateId
                        val newTxn = NewTransaction(
                            accountId = requireNotNull(s.accountId) { "Account required for ${s.kind}" },
                            amount = amount,
                            ts = s.whenMs,
                            merchantName = s.merchantName.ifBlank { null },
                            categoryId = s.categoryId,
                            notes = s.notes.ifBlank { null },
                            kind = s.kind,
                            tagNames = s.tagNames,
                            // Link at insert time via captureId (R1: no link method).
                            captureId = capId,
                        )
                        val newTxnId: String = if (capId != null && confirmWithEdits != null) {
                            // M3-int Fix 5: atomic insert + confirm in one db.transaction.
                            confirmWithEdits(newTxn, capId)
                        } else {
                            val id = txnRepo.add(newTxn)
                            // Non-atomic fallback (no confirmWithEdits seam): mark confirmed separately.
                            if (capId != null) {
                                inboxRepo?.markConfirmed(capId)
                            }
                            id
                        }
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
