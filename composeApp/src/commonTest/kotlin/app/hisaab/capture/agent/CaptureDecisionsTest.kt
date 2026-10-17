package app.hisaab.capture.agent

import app.hisaab.agent.ProposedWrite
import app.hisaab.domain.AccountKind
import app.hisaab.domain.TxnKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CaptureDecisionsTest {

    private fun w(tool: String, args: String) = ProposedWrite(tool, Json.parseToJsonElement(args).jsonObject)

    private val batch = setOf("m1", "m2", "m3")
    private val accounts = setOf("bKash", "City Bank")

    @Test
    fun `a valid batch parses into decisions and covers every message`() {
        val result = parseDecisions(
            listOf(
                w("post_transaction", """{"messages":["m1"],"account":"bkash","amount":500,"kind":"EXPENSE","category":"food","merchant":"Foodpanda"}"""),
                w("skip", """{"messages":["m2"],"reason":"promo"}"""),
                w("ask", """{"messages":["m3"],"question":"Whose card is this?","options":["Mine","My wife's"]}"""),
                w("remember", """{"fact":"16216 is bKash"}"""),
            ),
            batch, accounts,
        )

        assertTrue(result.errors.isEmpty(), result.errors.toString())
        assertTrue(result.undecided.isEmpty())
        val post = result.decisions.first() as CaptureDecision.PostTransaction
        assertEquals(listOf("m1"), post.messageIds)
        assertEquals(500.0, post.amount)
        assertEquals(TxnKind.EXPENSE, post.kind)
        assertEquals(CaptureDecision.Ask(listOf("m3"), "Whose card is this?", listOf("Mine", "My wife's")), result.decisions[2])
        assertEquals(CaptureDecision.Remember("16216 is bKash"), result.decisions[3])
    }

    @Test
    fun `the same payment by SMS and email is one decision`() {
        val result = parseDecisions(
            listOf(w("post_transaction", """{"messages":["m1","m2"],"account":"City Bank","amount":1200,"kind":"EXPENSE"}""")),
            batch, accounts,
        )
        assertEquals(listOf("m1", "m2"), result.decisions.single().messageIds)
        assertEquals(setOf("m3"), result.undecided)
    }

    @Test
    fun `messages outside the batch are rejected`() {
        val result = parseDecisions(listOf(w("skip", """{"messages":["m9"],"reason":"promo"}""")), batch, accounts)
        assertTrue(result.decisions.isEmpty())
        assertTrue("m9" in result.errors.single(), result.errors.toString())
    }

    @Test
    fun `a message decided twice is rejected`() {
        val result = parseDecisions(
            listOf(
                w("skip", """{"messages":["m1"],"reason":"promo"}"""),
                w("post_transaction", """{"messages":["m1"],"account":"bKash","amount":5,"kind":"EXPENSE"}"""),
            ),
            batch, accounts,
        )
        assertEquals(1, result.decisions.size)
        assertTrue("m1" in result.errors.single(), result.errors.toString())
    }

    @Test
    fun `a non-positive amount is rejected`() {
        val result = parseDecisions(
            listOf(w("post_transaction", """{"messages":["m1"],"account":"bKash","amount":0,"kind":"EXPENSE"}""")),
            batch, accounts,
        )
        assertTrue(result.decisions.isEmpty())
        assertEquals(1, result.errors.size)
        assertTrue("m1" in result.undecided)
    }

    @Test
    fun `an unknown account is rejected unless created in the same batch`() {
        val unknown = parseDecisions(
            listOf(w("post_transaction", """{"messages":["m1"],"account":"Nagad","amount":50,"kind":"INCOME"}""")),
            batch, accounts,
        )
        assertTrue("Nagad" in unknown.errors.single(), unknown.errors.toString())

        val created = parseDecisions(
            listOf(
                w("create_account", """{"name":"Nagad","kind":"MFS"}"""),
                w("post_transaction", """{"messages":["m1"],"account":"nagad","amount":50,"kind":"INCOME"}"""),
            ),
            batch, accounts,
        )
        assertTrue(created.errors.isEmpty(), created.errors.toString())
        assertEquals(CaptureDecision.CreateAccount("Nagad", AccountKind.MFS, null), created.decisions.first())
    }

    @Test
    fun `an unknown decision is reported`() {
        val result = parseDecisions(listOf(w("delete_everything", """{}""")), batch, accounts)
        assertTrue("delete_everything" in result.errors.single())
        assertEquals(batch, result.undecided)
    }
}
