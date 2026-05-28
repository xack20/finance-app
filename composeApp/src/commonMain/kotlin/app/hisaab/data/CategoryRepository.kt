package app.hisaab.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.hisaab.db.HisaabDatabase
import app.hisaab.domain.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CategoryRepository(private val db: HisaabDatabase) {

    fun observeAll(): Flow<List<Category>> =
        db.insightQueriesQueries.observeAllCategories().asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun ensureDefaults() {
        DEFAULT_CATEGORIES.forEach { default ->
            db.insightQueriesQueries.insertCategoryIfMissing(
                id = default.id,
                name = default.name,
                parent_id = null,
                color = default.color,
                icon = default.icon,
                is_default = 1L,
            )
        }
    }

    suspend fun add(name: String, color: String?, icon: String?, parentId: String?): String {
        val id = randomId()
        db.insightQueriesQueries.insertCategoryIfMissing(
            id = id,
            name = name,
            parent_id = parentId,
            color = color,
            icon = icon,
            is_default = 0L,
        )
        return id
    }

    private fun migrations.Category.toDomain(): Category = Category(
        id = id,
        name = name,
        parentId = parent_id,
        color = color,
        icon = icon,
        isDefault = is_default == 1L,
    )

    private fun randomId(): String {
        val bytes = kotlin.random.Random.Default.nextBytes(16)
        return bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    private data class DefaultCategory(
        val id: String, val name: String, val color: String, val icon: String,
    )

    private companion object {
        val DEFAULT_CATEGORIES = listOf(
            DefaultCategory("food",          "Food & dining",  "#ad6b2a", "🍽"),
            DefaultCategory("transport",     "Transport",      "#5b8fb9", "🚗"),
            DefaultCategory("bills",         "Bills",          "#7a5c9e", "🧾"),
            DefaultCategory("salary",        "Salary",         "#2e7d4f", "💼"),
            DefaultCategory("lend",          "Lent",           "#c8964a", "↗"),
            DefaultCategory("borrow",        "Borrowed",       "#b5402c", "↙"),
            DefaultCategory("health",        "Health",         "#d68945", "🩺"),
            DefaultCategory("education",     "Education",      "#6b8e23", "📚"),
            DefaultCategory("shopping",      "Shopping",       "#9e6b9e", "🛍"),
            DefaultCategory("entertainment", "Entertainment",  "#c8964a", "🎬"),
            DefaultCategory("other",         "Other",          "#6f6453", "•"),
            DefaultCategory("transfer",      "Transfer",       "#6f6453", "⇄"),
        )
    }
}
