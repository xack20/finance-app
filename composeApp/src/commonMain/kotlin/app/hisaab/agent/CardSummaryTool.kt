package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.domain.AccountKind
import app.hisaab.domain.CardSummaryCalculator
import app.hisaab.llm.LlmJson
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CardSummaryTool(
    private val accounts: AccountRepository,
    private val today: () -> LocalDate = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    },
) : ReadTool {
    override val name = "card_summary"
    override val description = "Outstanding balance, available credit and next due date for a credit card"
    override val paramsDoc = "{ \"card\": string }"

    override suspend fun execute(args: JsonObject): String {
        val cardName = (args["card"] as? JsonPrimitive)?.content?.lowercase().orEmpty()
        val account = accounts.observeActive().first()
            .firstOrNull { it.kind == AccountKind.CARD && it.name.lowercase() == cardName }
            ?: return LlmJson.json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("error", "no card named '$cardName'")
            })
        val outstanding = accounts.cardOutstanding(account.id)
        val limit = account.creditLimit
        val available = limit?.let { it - outstanding }
        val sd = account.statementDay
        val dd = account.dueDay
        val nextDue = if (sd != null && dd != null)
            CardSummaryCalculator.nextDueDate(today(), sd, dd).toString() else null
        return LlmJson.json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("name", account.name)
            put("outstanding", outstanding)
            limit?.let { put("creditLimit", it) }
            available?.let { put("availableCredit", it) }
            nextDue?.let { put("nextDueDate", it) }
        })
    }
}
