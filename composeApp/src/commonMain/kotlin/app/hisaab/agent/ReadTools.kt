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
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Instant
import kotlinx.datetime.DateTimeUnit
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
                addJsonObject {
                    put("id", t.id)                                  // needed by the recategorize write tool
                    put("amount", t.amount)
                    put("kind", t.kind.name)
                    t.merchantName?.let { put("merchant", it) }
                    t.categoryName?.let { put("category", it) }
                    put("ts", t.ts)
                }
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

/** Capture agent's duplicate check: same amount within two days of a date, so a payment isn't posted twice. */
class FindSimilarTransactionsTool(
    private val repo: TransactionRepository,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ReadTool {
    override val name = "find_similar_transactions"
    override val description =
        "Ledger entries with exactly the amount within 2 days of the date — pass every candidate as items in one call"
    override val paramsDoc = "{ \"items\": [ { \"amount\": number, \"date\": \"YYYY-MM-DD\" } ] }"

    override suspend fun execute(args: JsonObject): String {
        // One {amount, date} at the top level is accepted too.
        val items = (args["items"] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: listOf(args)
        if (items.size > MAX_ITEMS) return "ERROR: at most $MAX_ITEMS items per lookup"
        // Query first: the JSON builders below can't suspend.
        val results = items.map { item ->
            val amount = argStr(item, "amount")?.toDoubleOrNull()
            val date = argStr(item, "date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val matches = if (amount == null || date == null) {
                null
            } else {
                val from = date.minus(WINDOW_DAYS, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
                val to = date.plus(WINDOW_DAYS + 1, DateTimeUnit.DAY).atStartOfDayIn(timeZone).toEpochMilliseconds()
                repo.findSimilar(amount, from, to)
            }
            Triple(argStr(item, "amount"), argStr(item, "date"), matches)
        }
        return jsonArray {
            results.forEach { (amount, date, matches) ->
                addJsonObject {
                    put("amount", amount)
                    put("date", date)
                    if (matches == null) {
                        put("error", "amount and date (YYYY-MM-DD) are required")
                    } else {
                        put("matches", buildJsonArray {
                            matches.forEach { t ->
                                addJsonObject {
                                    put("kind", t.kind.name)
                                    put("when", Instant.fromEpochMilliseconds(t.ts).toLocalDateTime(timeZone).toString().take(16))
                                    put("account", t.account)
                                    t.merchant?.let { put("merchant", it) }
                                    put("fromMessage", t.fromMessage)
                                }
                            }
                        })
                    }
                }
            }
        }
    }

    private companion object {
        const val WINDOW_DAYS = 2
        const val MAX_ITEMS = 30
    }
}
