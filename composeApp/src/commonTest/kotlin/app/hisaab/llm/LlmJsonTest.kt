package app.hisaab.llm

import app.hisaab.domain.Direction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LlmJsonTest {

    @Test
    fun `decodes a full financial result`() {
        val json = """
          {"amount":1500.0,"direction":"CREDIT","merchant":"Shwapno","categoryId":"food",
           "balanceAfter":3210.5,"refNo":"9AB12CD34","confidence":0.95,"isFinancial":true}
        """.trimIndent()
        val r = LlmJson.decodeResult(json)
        assertEquals(1500.0, r.amount)
        assertEquals(Direction.CREDIT, r.direction)
        assertEquals("Shwapno", r.merchant)
        assertEquals("food", r.categoryId)
        assertEquals(3210.5, r.balanceAfter)
        assertEquals("9AB12CD34", r.refNo)
        assertEquals(0.95, r.confidence)
        assertTrue(r.isFinancial)
    }

    @Test
    fun `tolerates json wrapped in markdown fences`() {
        val json = "```json\n{\"amount\":10.0,\"direction\":\"DEBIT\",\"confidence\":0.5,\"isFinancial\":true}\n```"
        val r = LlmJson.decodeResult(json)
        assertEquals(10.0, r.amount)
        assertEquals(Direction.DEBIT, r.direction)
    }

    @Test
    fun `non-financial result yields nulls`() {
        val json = """{"amount":null,"direction":null,"confidence":0.99,"isFinancial":false}"""
        val r = LlmJson.decodeResult(json)
        assertNull(r.amount)
        assertNull(r.direction)
        assertEquals(false, r.isFinancial)
    }

    @Test
    fun `negative or zero amount is sanitised to null`() {
        val json = """{"amount":-5.0,"direction":"DEBIT","confidence":0.8,"isFinancial":true}"""
        val r = LlmJson.decodeResult(json)
        assertNull(r.amount)
    }

    @Test
    fun `confidence is clamped to 0_1`() {
        val r = LlmJson.decodeResult("""{"amount":1.0,"direction":"DEBIT","confidence":5.0,"isFinancial":true}""")
        assertEquals(1.0, r.confidence)
        val r2 = LlmJson.decodeResult("""{"amount":1.0,"direction":"DEBIT","confidence":-2.0,"isFinancial":true}""")
        assertEquals(0.0, r2.confidence)
    }

    @Test
    fun `over-long merchant is truncated`() {
        val long = "x".repeat(200)
        val r = LlmJson.decodeResult("""{"merchant":"$long","confidence":0.5,"isFinancial":true}""")
        assertTrue((r.merchant?.length ?: 0) <= 64)
    }

    @Test
    fun `garbage throws decode LlmException`() {
        val ex = assertFailsWith<LlmException> { LlmJson.decodeResult("not json at all") }
        assertTrue(ex.error is LlmError.Decode)
    }
}
