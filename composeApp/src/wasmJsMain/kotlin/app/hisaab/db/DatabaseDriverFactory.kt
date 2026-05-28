package app.hisaab.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.worker.WebWorkerDriver
import org.w3c.dom.Worker

actual class DatabaseDriverFactory {
    // Web is viewer-only; no SQLCipher on WASM. dbKey is ignored.
    actual fun createDriver(dbKey: ByteArray): SqlDriver =
        WebWorkerDriver(Worker(js("new URL('sqljs.worker.js', import.meta.url).href") as String))
}
