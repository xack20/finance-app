package app.hisaab.platform

actual class SecureStorage {
    actual fun storeMasterSecret(secret: ByteArray) {
        kotlinx.browser.sessionStorage.setItem(KEY_MASTER_SECRET, secret.joinToString(","))
    }

    actual fun loadMasterSecret(): ByteArray? =
        kotlinx.browser.sessionStorage.getItem(KEY_MASTER_SECRET)
            ?.split(",")?.map { it.toByte() }?.toByteArray()

    actual fun clearMasterSecret() {
        kotlinx.browser.sessionStorage.removeItem(KEY_MASTER_SECRET)
    }

    actual fun storeString(key: String, value: String) {
        kotlinx.browser.sessionStorage.setItem(key, value)
    }

    actual fun loadString(key: String): String? =
        kotlinx.browser.sessionStorage.getItem(key)

    companion object {
        private const val KEY_MASTER_SECRET = "master_secret"
    }
}
