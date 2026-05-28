package app.hisaab.platform

expect class SecureStorage {
    fun storeMasterSecret(secret: ByteArray)
    fun loadMasterSecret(): ByteArray?
    fun clearMasterSecret()
    fun storeString(key: String, value: String)
    fun loadString(key: String): String?
}
