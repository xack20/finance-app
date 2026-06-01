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

    /** Non-suspending category insert (for committer use inside a db.transaction). Returns the new id. */
    fun addBlocking(name: String, color: String?, icon: String?, parentId: String?): String {
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
        // color = a HisaabColors.categoryHues key, icon = a HisaabIcons name (mirrors neo.jsx NCAT).
        // This makes GlyphChip render the on-brand stroke icon + hue everywhere (Today/Detail/Review/Categories).
        val DEFAULT_CATEGORIES = listOf(
            DefaultCategory("food",          "Food & dining",  "rose",   "food"),
            DefaultCategory("transport",     "Transport",      "blue",   "car"),
            DefaultCategory("bills",         "Bills",          "violet", "receipt"),
            DefaultCategory("salary",        "Salary",         "lime",   "case"),
            DefaultCategory("lend",          "Lent",           "lime",   "lend"),
            DefaultCategory("borrow",        "Borrowed",       "amber",  "borrow"),
            DefaultCategory("health",        "Health",         "teal",   "health"),
            DefaultCategory("education",     "Education",      "blue",   "book"),
            DefaultCategory("shopping",      "Shopping",       "amber",  "bag"),
            DefaultCategory("entertainment", "Entertainment",  "pink",   "film"),
            DefaultCategory("other",         "Other",          "slate",  "dot"),
            DefaultCategory("transfer",      "Transfer",       "slate",  "swap"),
        )
    }
}
