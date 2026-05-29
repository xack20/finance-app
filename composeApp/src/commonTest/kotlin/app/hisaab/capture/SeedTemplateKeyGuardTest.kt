package app.hisaab.capture

import app.hisaab.data.SenderRepository
import app.hisaab.data.support.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * Guard test: every seeded financial sender that carries a non-null template_key must resolve
 * to a known BankTemplate. Prevents SenderRepository.SEED from drifting out of sync with
 * BankTemplates keys (the M3-3 City/BRAC "city"/"brac" mismatch regression).
 */
class SeedTemplateKeyGuardTest {

    @Test
    fun `every seeded template_key resolves in BankTemplates`() = runTest {
        val db = TestDatabase.create()
        val repo = SenderRepository(db)
        repo.seedKnownSenders()

        val senders = repo.observeAll().first()
        val financial = senders.filter { it.isFinancial && it.templateKey != null }

        for (sender in financial) {
            val key = sender.templateKey!!
            assertNotNull(
                BankTemplates.forKey(key),
                "Seeded sender '${sender.senderId}' has templateKey='$key' " +
                    "which is not registered in BankTemplates. " +
                    "Known keys: ${BankTemplates.keys}",
            )
        }
    }
}
