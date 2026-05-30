package app.hisaab.data.support

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.hisaab.db.HisaabDatabase

object TestDatabase {
    fun create(): HisaabDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HisaabDatabase.Schema.create(driver)
        // Match production: the on-device SQLCipher driver enables FK enforcement in its
        // Callback.onOpen (DatabaseDriverFactory.kt). SQLite defaults this OFF, so without it the
        // in-memory test driver silently tolerates FK-violating insert orders that crash on-device
        // — exactly the divergence that hid the capture auto-post FK bug.
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        return HisaabDatabase(driver)
    }
}
