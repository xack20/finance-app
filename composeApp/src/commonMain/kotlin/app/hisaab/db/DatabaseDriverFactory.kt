package app.hisaab.db

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(dbKey: ByteArray): SqlDriver
}
