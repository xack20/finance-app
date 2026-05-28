package app.hisaab.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

actual class DatabaseDriverFactory {
    @Suppress("UNUSED_PARAMETER")
    actual fun createDriver(dbKey: ByteArray): SqlDriver =
        // Web is viewer-only; SQLCipher not available in WASM. dbKey intentionally unused.
        WebWorkerDriver(Worker(js("new URL('sqljs.worker.js', import.meta.url).href") as String))
}
