package app.hisaab.capture

import app.hisaab.data.AccountRepository
import app.hisaab.data.SenderRepository
import app.hisaab.domain.AccountKind
import app.hisaab.domain.BankType
import app.hisaab.domain.SenderMapping

/**
 * Resolves the destination account for a captured SMS. If the sender is already
 * mapped to an account, returns it. Otherwise auto-creates an account
 * (MFS for bKash/Nagad/Rocket, BANK for banks/cards) and persists the mapping
 * so the next SMS from that sender resolves instantly. A null mapping (brand-new
 * unmapped sender) returns null, forcing the candidate to review.
 */
class AccountMatcher(
    private val accountRepo: AccountRepository,
    private val senderRepo: SenderRepository,
) {

    suspend fun resolve(mapping: SenderMapping?, bankType: BankType): String? {
        if (mapping == null) return null
        mapping.accountId?.takeIf { it.isNotBlank() }?.let { return it }

        val kind = accountKindFor(bankType)
        val accountId = accountRepo.add(
            name = mapping.displayName,
            kind = kind,
            institution = mapping.displayName,
        )
        senderRepo.setAccount(senderId = mapping.senderId, accountId = accountId)
        return accountId
    }

    private fun accountKindFor(bankType: BankType): AccountKind = when (bankType) {
        BankType.BKASH, BankType.NAGAD, BankType.ROCKET -> AccountKind.MFS
        BankType.BANK -> AccountKind.BANK
        BankType.CARD -> AccountKind.CARD
        BankType.OTHER -> AccountKind.BANK
    }
}
