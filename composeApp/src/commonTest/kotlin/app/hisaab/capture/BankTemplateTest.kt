package app.hisaab.capture

import app.hisaab.domain.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankTemplateTest {

    private fun extract(sample: SmsCorpus.Sample): TemplateExtraction {
        val template = BankTemplates.forKey(sample.templateKey)
        assertNotNull(template, "No template for key='${sample.templateKey}' (${sample.name})")
        return template.extract(BanglaNumerals.normalize(sample.body))
    }

    @Test
    fun `every complete corpus sample extracts amount direction and ref`() {
        SmsCorpus.samples
            .filter { it.expectedComplete }
            .forEach { sample ->
                val r = extract(sample)
                assertEquals(sample.expectedAmount, r.amount, "amount mismatch: ${sample.name}")
                assertEquals(sample.expectedDirection, r.direction, "direction mismatch: ${sample.name}")
                assertEquals(sample.expectedRefNo, r.refNo, "refNo mismatch: ${sample.name}")
                assertEquals(sample.expectedBalanceAfter, r.balanceAfter, "balance mismatch: ${sample.name}")
                assertTrue(r.complete, "should be complete: ${sample.name}")
            }
    }

    @Test
    fun `bkash promo without amount is not complete`() {
        val r = extract(SmsCorpus.byName("bkash_promo_no_amount"))
        assertNull(r.amount)
        assertNull(r.direction)
        assertTrue(!r.complete)
    }

    @Test
    fun `bkash payment captures merchant name`() {
        val r = extract(SmsCorpus.byName("bkash_payment"))
        assertEquals("SHWAPNO", r.merchant)
        assertEquals(Direction.DEBIT, r.direction)
    }

    @Test
    fun `nagad payment captures merchant name`() {
        val r = extract(SmsCorpus.byName("nagad_payment"))
        assertEquals("DARAZ", r.merchant)
    }

    @Test
    fun `forKey returns null for unknown key`() {
        assertNull(BankTemplates.forKey("unknown_bank"))
    }

    @Test
    fun `keys lists all six seeded templates`() {
        assertEquals(
            setOf("bkash", "nagad", "rocket", "citybank", "bracbank", "dbbl"),
            BankTemplates.keys.toSet(),
        )
    }
}
