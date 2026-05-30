// ReadTools.kt
package app.hisaab.agent
import app.hisaab.data.AccountRepository
import app.hisaab.data.CategoryRepository
import app.hisaab.data.InsightRepository
import app.hisaab.data.MerchantRepository
import app.hisaab.data.PersonRepository
import app.hisaab.data.TransactionRepository
import app.hisaab.domain.YearMonth
import app.hisaab.llm.LlmJson
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun jsonArray(block: kotlinx.serialization.json.JsonArrayBuilder.() -> Unit): String =
    LlmJson.json.encodeToString(JsonArray.serializer(), buildJsonArray(block))
private fun argStr(args: JsonObject, key: String): String? = (args[key] as? JsonPrimitive)?.content
private fun currentYearMonth(): YearMonth {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return YearMonth.of(now.year, now.monthNumber)
}

class ListAccountsTool(private val repo: AccountRepository) : ReadTool {
    override val name = "list_accounts"; override val description = "List the user's accounts"
    override val paramsDoc = "{}"
    override suspend fun execute(args: JsonObject): String {
        val accounts = repo.observeActive().first()
        return jsonArray {
            accounts.forEach { a ->
                addJsonObject { put("id", a.id); put("name", a.name); put("kind", a.kind.name) }
            }
        }
    }
}

class AccountBalancesTool(private val repo: AccountRepository) : ReadTool {
    override val name = "account_balances"; override val description = "Current net balance per account"
    override val paramsDoc = "{}"
    override suspend fun execute(args: JsonObject): String {
        val balances = repo.accountBalances()
        val accounts = repo.observeActive().first()
        return jsonArray {
            accounts.forEach { a ->
                addJsonObject { put("name", a.name); put("balance", balances[a.id] ?: 0.0) }
            }
        }
    }
}

class ListCategoriesTool(private val repo: CategoryRepository) : ReadTool {
    override val name = "list_categories"; override val description = "List spending categories"
    override val paramsDoc = "{}"
    override suspend fun execute(args: JsonObject): String {
        val categories = repo.observeAll().first()
        return jsonArray {
            categories.forEach { c -> addJsonObject { put("id", c.id); put("name", c.name) } }
        }
    }
}

class FindMerchantTool(private val repo: MerchantRepository) : ReadTool {
    override val name = "find_merchant"; override val description = "Find merchants by name substring"
    override val paramsDoc = "{ \"query\": string }"
    override suspend fun execute(args: JsonObject): String {
        val q = argStr(args, "query")?.lowercase().orEmpty()
        val merchants = repo.observeAll().first().filter { it.name.lowercase().contains(q) }
        return jsonArray {
            merchants.forEach { m -> addJsonObject { put("id", m.id); put("name", m.name) } }
        }
    }
}

class QueryTransactionsTool(private val repo: TransactionRepository) : ReadTool {
    override val name = "query_transactions"; override val description = "Recent transactions (most recent first)"
    override val paramsDoc = "{ \"limit\": number }"
    override suspend fun execute(args: JsonObject): String {
        val limit = (argStr(args, "limit")?.toIntOrNull() ?: 20).coerceIn(1, 200)
        val txns = repo.observeRecent(limit).first()
        return jsonArray {
            txns.forEach { t ->
                addJsonObject { put("amount", t.amount); put("kind", t.kind.name); put("ts", t.ts) }
            }
        }
    }
}

class SpendByCategoryTool(private val repo: InsightRepository) : ReadTool {
    override val name = "spend_by_category"; override val description = "Spend per category for a month (YYYY-MM)"
    override val paramsDoc = "{ \"month\": \"YYYY-MM\" }"
    override suspend fun execute(args: JsonObject): String {
        val month = argStr(args, "month")?.let { runCatching { YearMonth(it) }.getOrNull() } ?: currentYearMonth()
        val slices = repo.computeCategoryBreakdown(month).first()
        return jsonArray {
            slices.forEach { s ->
                addJsonObject { put("category", s.categoryName); put("total", s.total) }
            }
        }
    }
}

class PersonBalanceTool(private val repo: PersonRepository) : ReadTool {
    override val name = "person_balance"; override val description = "Net lend/borrow balance for a person by name"
    override val paramsDoc = "{ \"name\": string }"
    override suspend fun execute(args: JsonObject): String {
        val name = argStr(args, "name")?.lowercase().orEmpty()
        val match = repo.observeAll().first().firstOrNull { it.person.name.lowercase() == name }
        return LlmJson.json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("name", match?.person?.name ?: name); put("balance", match?.balance ?: 0.0)
        })
    }
}
