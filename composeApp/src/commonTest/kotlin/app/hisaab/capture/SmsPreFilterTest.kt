package app.hisaab.capture

import app.hisaab.data.SenderRepository
import app.hisaab.data.support.TestDatabase
import app.hisaab.domain.BankType
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import app.hisaab.domain.SenderMapping
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmsPreFilterTest {

    private fun raw(sender: String, body: String) =
        RawCapture(sender = sender, body = body, receivedAt = 1000L, channel = CaptureChannel.SMS)

    private fun mapping(
        sender: String,
        bankType: BankType,
        isFinancial: Boolean,
        templateKey: String?,
    ) = SenderMapping(
        id = "m_$sender",
        senderId = sender,
        displayName = sender,
        bankType = bankType,
        isFinancial = isFinancial,
        templateKey = templateKey,
        accountId = null,
        createdAt = 1000L,
    )

    @Test
    fun `known template sender with money signal classifies KnownTemplate`() = runTest {
        val filter = SmsPreFilter(SenderRepository(TestDatabase.create()))
        val sample = SmsCorpus.byName("bkash_received_money")
        val result = filter.classify(
            raw(sample.sender, sample.body),
            mapping(sample.sender, BankType.BKASH, isFinancial = true, templateKey = "bkash"),
        )
        assertTrue(result is PreFilterResult.KnownTemplate)
        assertEquals("bkash", (result as PreFilterResult.KnownTemplate).templateKey)
    }

    @Test
    fun `promo sender flagged not financial is dropped`() = runTest {
        val filter = SmsPreFilter(SenderRepository(TestDatabase.create()))
        val sample = SmsCorpus.byName("robi_promo_nonfinancial")
        val result = filter.classify(
            raw(sample.sender, sample.body),
            mapping(sample.sender, BankType.OTHER, isFinancial = false, templateKey = null),
        )
        assertEquals(PreFilterResult.NotFinancial, result)
    }

    @Test
    fun `unknown sender with money signal classifies UnknownFinancial`() = runTest {
        val filter = SmsPreFilter(SenderRepository(TestDatabase.create()))
        val result = filter.classify(
            raw("0152233", "BDT 1,200.00 debited TrxID AB12"),
            mapping = null,
        )
        assertTrue(result is PreFilterResult.UnknownFinancial)
    }

    @Test
    fun `unknown sender without money signal is dropped`() = runTest {
        val filter = SmsPreFilter(SenderRepository(TestDatabase.create()))
        val result = filter.classify(
            raw("FRIEND", "are you coming to dinner tonight?"),
            mapping = null,
        )
        assertEquals(PreFilterResult.NotFinancial, result)
    }

    @Test
    fun `financial mapped sender without template falls to UnknownFinancial`() = runTest {
        val filter = SmsPreFilter(SenderRepository(TestDatabase.create()))
        val result = filter.classify(
            raw("SOMEBANK", "Your account credited BDT 500.00 TxnID 9"),
            mapping("SOMEBANK", BankType.BANK, isFinancial = true, templateKey = null),
        )
        assertTrue(result is PreFilterResult.UnknownFinancial)
    }
}
