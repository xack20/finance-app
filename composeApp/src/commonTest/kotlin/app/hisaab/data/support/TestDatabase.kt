package app.hisaab.data.support

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.hisaab.db.HisaabDatabase

object TestDatabase {
    fun create(): HisaabDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HisaabDatabase.Schema.create(driver)
        return HisaabDatabase(driver)
    }
}
