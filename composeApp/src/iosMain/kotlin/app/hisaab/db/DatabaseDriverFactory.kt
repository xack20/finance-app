package app.hisaab.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration

actual class DatabaseDriverFactory {
    actual fun createDriver(dbKey: ByteArray): SqlDriver {
        val keyHex = dbKey.joinToString("") { "%02x".format(it) }
        val config = DatabaseConfiguration(
            name = "hisaab.db",
            version = HisaabDatabase.Schema.version.toInt(),
            create = { connection ->
                wrapConnection(connection) { HisaabDatabase.Schema.create(it) }
            },
            upgrade = { connection, oldVersion, newVersion ->
                wrapConnection(connection) {
                    HisaabDatabase.Schema.migrate(it, oldVersion.toLong(), newVersion.toLong())
                }
            },
            extendedConfig = DatabaseConfiguration.Extended(
                foreignKeyConstraints = true,
                encryptionSpec = DatabaseConfiguration.Extended.EncryptionSpec(keyHex),
            ),
        )
        return NativeSqliteDriver(config)
    }
}
