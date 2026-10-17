package app.hisaab.data

import app.hisaab.domain.BankType
import app.hisaab.domain.SenderMapping
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * Registers the senders of Review items that have no account and no registry entry (captured before unknown
 * senders were auto-registered), so they show under "New senders detected" and can be mapped. Known senders
 * are left untouched.
 */
suspend fun registerWaitingSenders(inbox: CaptureInboxRepository, senders: SenderRepository) {
    val now = Clock.System.now().toEpochMilliseconds()
    inbox.waitingSendersWithoutAccount()
        .filter { senders.findBySenderId(it) == null }
        .forEach { sender ->
            senders.upsert(
                SenderMapping(
                    id = Random.nextLong().toULong().toString(16), senderId = sender, displayName = sender,
                    bankType = BankType.OTHER, isFinancial = true, templateKey = null, accountId = null, createdAt = now,
                ),
            )
        }
}
