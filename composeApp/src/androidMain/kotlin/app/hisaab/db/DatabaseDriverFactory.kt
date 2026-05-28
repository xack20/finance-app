package app.hisaab.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(dbKey: ByteArray): SqlDriver {
        val factory = SupportOpenHelperFactory(dbKey)
        dbKey.fill(0)  // zero key; SQLCipher has already copied it into the factory
        return AndroidSqliteDriver(
            schema = HisaabDatabase.Schema,
            context = context,
            name = "hisaab.db",
            factory = factory,
            callback = object : AndroidSqliteDriver.Callback(HisaabDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            },
        )
    }
}
