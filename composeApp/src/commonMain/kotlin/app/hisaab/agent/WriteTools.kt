// WriteTools.kt
package app.hisaab.agent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content
private fun JsonObject.dbl(key: String): Double? =
    (this[key] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
private fun JsonObject.lng(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

/** Parsed, typed form of a ProposedWrite. The committer consumes these; the loop never does. */
sealed interface WriteIntent {
    data class CreateAccount(
        val name: String, val kind: String,
        val creditLimit: Double? = null, val statementDay: Int? = null, val dueDay: Int? = null,
    ) : WriteIntent
    data class CreateCategory(val name: String, val parent: String?) : WriteIntent
    data class AddTransaction(
        val account: String, val amount: Double, val kind: String,
        val category: String?, val merchant: String?, val notes: String?, val ts: Long?,
    ) : WriteIntent
    data class RecordLendBorrow(
        val person: String, val amount: Double, val kind: String, val fromAccount: String, val purpose: String?,
    ) : WriteIntent
    data class Transfer(val fromAccount: String, val toAccount: String, val amount: Double, val notes: String?) : WriteIntent
    data class CardPayment(val fromAccount: String, val card: String, val amount: Double) : WriteIntent
    data class SetBudget(val category: String, val amount: Double, val month: String?) : WriteIntent
    data class Recategorize(val transactionId: String, val category: String) : WriteIntent
    data class SplitLeg(val category: String?, val amount: Double, val notes: String? = null)
    data class AddSplitTransaction(
        val account: String, val amount: Double, val kind: String, val splits: List<SplitLeg>,
    ) : WriteIntent

    companion object {
        /** Returns null for unknown tools or missing required fields (committer skips/reports null). */
        fun parse(w: ProposedWrite): WriteIntent? {
            val a = w.args
            return when (w.tool) {
                "create_account" -> a.str("name")?.let {
                    CreateAccount(it, a.str("kind") ?: "CASH",
                        a.dbl("creditLimit"), a.dbl("statementDay")?.toInt(), a.dbl("dueDay")?.toInt())
                }
                "create_category" -> a.str("name")?.let { CreateCategory(it, a.str("parent")) }
                "add_transaction" -> {
                    val account = a.str("account"); val amount = a.dbl("amount")
                    if (account != null && amount != null)
                        AddTransaction(account, amount, a.str("kind") ?: "EXPENSE",
                            a.str("category"), a.str("merchant"), a.str("notes"), a.lng("ts"))
                    else null
                }
                "record_lend_borrow" -> {
                    val person = a.str("person"); val amount = a.dbl("amount"); val from = a.str("fromAccount")
                    if (person != null && amount != null && from != null)
                        RecordLendBorrow(person, amount, a.str("kind") ?: "LENT", from, a.str("purpose"))
                    else null
                }
                "transfer" -> {
                    val from = a.str("fromAccount"); val to = a.str("toAccount"); val amount = a.dbl("amount")
                    if (from != null && to != null && amount != null) Transfer(from, to, amount, a.str("notes")) else null
                }
                "record_card_payment" -> {
                    val from = a.str("fromAccount"); val card = a.str("card"); val amount = a.dbl("amount")
                    if (from != null && card != null && amount != null) CardPayment(from, card, amount) else null
                }
                "set_budget" -> {
                    val cat = a.str("category"); val amount = a.dbl("amount")
                    if (cat != null && amount != null) SetBudget(cat, amount, a.str("month")) else null
                }
                "recategorize" -> {
                    val tid = a.str("transactionId"); val cat = a.str("category")
                    if (tid != null && cat != null) Recategorize(tid, cat) else null
                }
                "add_split_transaction" -> {
                    val account = a.str("account"); val total = a.dbl("amount")
                    val splits = a.arr("splits")?.mapNotNull { el ->
                        (el as? JsonObject)?.let { o ->
                            o.dbl("amount")?.let { amt -> SplitLeg(o.str("category"), amt, o.str("notes")) }
                        }
                    }
                    if (account != null && total != null && !splits.isNullOrEmpty())
                        AddSplitTransaction(account, total, a.str("kind") ?: "EXPENSE", splits) else null
                }
                else -> null
            }
        }
    }
}

/** Prompt-facing catalog of the write tools this slice supports. */
fun agentWriteDescriptors(): List<WriteDescriptor> = listOf(
    WriteDescriptor("create_account", "Create a new account (cards may set creditLimit/statementDay/dueDay)",
        "{ \"name\": string, \"kind\": \"CASH|BANK|MFS|CARD\", \"creditLimit\": number?, \"statementDay\": 1-28?, \"dueDay\": 1-28? }"),
    WriteDescriptor("create_category", "Create a spending category", "{ \"name\": string, \"parent\": string? }"),
    WriteDescriptor("add_transaction", "Record an expense or income",
        "{ \"account\": string, \"amount\": number, \"kind\": \"EXPENSE|INCOME\", \"category\": string?, \"merchant\": string?, \"notes\": string? }"),
    WriteDescriptor("record_lend_borrow", "Record money lent to / borrowed from a person",
        "{ \"person\": string, \"amount\": number, \"kind\": \"LENT|BORROWED\", \"fromAccount\": string, \"purpose\": string? }"),
    WriteDescriptor("transfer", "Move money between two accounts",
        "{ \"fromAccount\": string, \"toAccount\": string, \"amount\": number }"),
    WriteDescriptor("record_card_payment", "Pay a credit-card bill from an account",
        "{ \"fromAccount\": string, \"card\": string, \"amount\": number }"),
    WriteDescriptor("set_budget", "Set a monthly spending cap for a category (month defaults to the current one)",
        "{ \"category\": string, \"amount\": number, \"month\": \"YYYY-MM\"? }"),
    WriteDescriptor("recategorize", "Change the category of an existing transaction (use the id from query_transactions)",
        "{ \"transactionId\": string, \"category\": string }"),
    WriteDescriptor("add_split_transaction", "Record one expense split across multiple categories",
        "{ \"account\": string, \"amount\": number, \"kind\": \"EXPENSE|INCOME\", \"splits\": [ { \"category\": string, \"amount\": number } ] }"),
)
