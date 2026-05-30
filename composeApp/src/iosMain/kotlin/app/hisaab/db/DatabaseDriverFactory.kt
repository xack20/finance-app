package app.hisaab.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration

actual class DatabaseDriverFactory {
    actual fun createDriver(dbKey: ByteArray): SqlDriver {
        val keyHex = dbKey.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        dbKey.fill(0)  // zero caller-supplied key; keyHex String cannot be zeroed (JVM limitation)
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
            ),
            // sqliter 1.3.3's encryptionConfig runs `PRAGMA key` on open. IMPORTANT: this only
            // ACTUALLY encrypts when SQLCipher is linked (CocoaPods `pod 'SQLCipher'` + SQLDelight
            // `linkSqlite = false`). Against the default system libsqlite3, `PRAGMA key` is a no-op
            // and the DB is NOT encrypted — so iOS must NOT ship to users until SQLCipher is linked
            // and verified on a simulator (tracked: tech-debt M4 / the M4-0 Xcode-wrapper slice).
            encryptionConfig = DatabaseConfiguration.Encryption(key = keyHex),
        )
        return NativeSqliteDriver(config)
    }
}
