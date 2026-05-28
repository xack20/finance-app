package app.hisaab.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(dbKey: ByteArray): SqlDriver {
        System.loadLibrary("sqlcipher")
        val factory = SupportOpenHelperFactory(dbKey)
        val driver = AndroidSqliteDriver(
            schema = HisaabDatabase.Schema,
            context = context,
            name = "hisaab.db",
            factory = factory,
        )
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        return driver
    }
}
