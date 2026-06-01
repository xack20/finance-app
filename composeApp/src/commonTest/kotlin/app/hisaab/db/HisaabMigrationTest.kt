package app.hisaab.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * Runtime verification of the SQLDelight migration chain (`1.sqm` … `6.sqm`).
 *
 * `verifyMigrations=true` validates the migrations at COMPILE time, but nothing exercised the
 * upgrade paths at RUNTIME against a populated database — a broken `.sqm` would only surface on a
 * real user's on-device (SQLCipher-encrypted) DB during an app update, where it would be unrecoverable.
 * These tests apply the migrations on an in-memory SQLite DB, with and without data, so a regression
 * is caught in CI instead of on a user's phone.
 *
 * Schema model: `deriveSchemaFromMigrations=true`, so `1.sqm` builds the original v1 schema (the
 * `CREATE TABLE`s) and `2.sqm`…`6.sqm` evolve it. `Schema.create()` materialises the latest derived
 * schema directly; `Schema.migrate(driver, old, new)` runs the actual `.sqm` files between versions.
 */
class HisaabMigrationTest {

    private fun freshDriver(): SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    @Test
    fun `schema version is the empty baseline plus six migrations`() {
        // deriveSchemaFromMigrations: version 1 is the empty baseline; 1.sqm..6.sqm bring it to 7.
        assertEquals(7L, HisaabDatabase.Schema.version)
    }

    @Test
    fun `full migration chain from empty to latest applies cleanly and is queryable`() {
        val driver = freshDriver()
        HisaabDatabase.Schema.migrate(driver, 0L, HisaabDatabase.Schema.version)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)

        // Insert via raw SQL, then read back through the GENERATED queries: this proves the migrated
        // schema actually matches what the .sq query layer selects (every column added across 2..6 is
        // present), not just that the DDL ran.
        driver.execute(
            null,
            "INSERT INTO account(id,name,kind,currency,balance_tracking,created_at) " +
                "VALUES('a1','Cash','CASH','BDT',1,0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO txn(id,account_id,amount,ts,source) VALUES('t1','a1',150.0,1000,'MANUAL')",
            0,
        )

        val db = HisaabDatabase(driver)
        assertNotNull(
            db.accountQueriesQueries.getAccountById("a1").executeAsOneOrNull(),
            "accountQueries must run against the migrated schema",
        )
        assertNotNull(
            db.transactionQueriesQueries.getTxn("t1").executeAsOneOrNull(),
            "transactionQueries must run against the migrated schema",
        )
    }

    @Test
    fun `every incremental migration step applies cleanly`() {
        val version = HisaabDatabase.Schema.version
        // Step n-1 -> n runs (n-1).sqm. Version 1 is the empty baseline, so the first real file is
        // 1.sqm (the 1 -> 2 step); the loop covers 1.sqm … 6.sqm.
        for (target in 2L..version) {
            val driver = freshDriver()
            try {
                if (target > 2L) HisaabDatabase.Schema.migrate(driver, 0L, target - 1L)
                HisaabDatabase.Schema.migrate(driver, target - 1L, target)
            } catch (t: Throwable) {
                fail("migration step ${target - 1} -> $target (${target - 1}.sqm) failed: ${t.message}")
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun `data written on the base schema survives the upgrade to latest with new columns defaulted`() {
        val driver = freshDriver()
        HisaabDatabase.Schema.migrate(driver, 0L, 2L) // build the base schema (1.sqm), before later migrations
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        driver.execute(
            null,
            "INSERT INTO account(id,name,kind,currency,balance_tracking,created_at) " +
                "VALUES('a1','Cash','CASH','BDT',1,0)",
            0,
        )
        driver.execute(
            null,
            "INSERT INTO txn(id,account_id,amount,currency,ts,source,notes) " +
                "VALUES('t1','a1',150.0,'BDT',1000,'MANUAL','lunch')",
            0,
        )

        HisaabDatabase.Schema.migrate(driver, 2L, HisaabDatabase.Schema.version) // upgrade base -> latest (2.sqm..6.sqm)

        // The row survived the upgrade...
        assertEquals("t1", driver.scalarText("SELECT id FROM txn WHERE id='t1'"))
        assertEquals("lunch", driver.scalarText("SELECT notes FROM txn WHERE id='t1'"))
        // ...and the columns added by later migrations received their declared defaults / nulls.
        assertEquals("EXPENSE", driver.scalarText("SELECT kind FROM txn WHERE id='t1'")) // 2.sqm DEFAULT 'EXPENSE'
        assertNull(driver.scalarText("SELECT capture_id FROM txn WHERE id='t1'")) // 3.sqm
        assertNull(driver.scalarText("SELECT transfer_group_id FROM txn WHERE id='t1'")) // 4.sqm
        assertNull(driver.scalarText("SELECT credit_limit FROM account WHERE id='a1'")) // 6.sqm
    }

    @Test
    fun `migrated schema matches a freshly created schema`() {
        val migrated = freshDriver().also { HisaabDatabase.Schema.migrate(it, 0L, HisaabDatabase.Schema.version) }
        val created = freshDriver().also { HisaabDatabase.Schema.create(it) }
        assertEquals(
            created.tableNames(),
            migrated.tableNames(),
            "the migration chain drifted from the derived schema (a .sqm is missing a table the queries expect)",
        )
    }

    // ── raw read helpers (avoid coupling assertions to generated row property names) ──────────────

    private fun SqlDriver.scalarText(sql: String): String? =
        executeQuery(null, sql, { cursor ->
            QueryResult.Value(if (cursor.next().value) cursor.getString(0) else null)
        }, 0).value

    private fun SqlDriver.tableNames(): Set<String> =
        executeQuery(null, "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'", { cursor ->
            val names = mutableSetOf<String>()
            while (cursor.next().value) cursor.getString(0)?.let { names += it }
            QueryResult.Value(names)
        }, 0).value
}
