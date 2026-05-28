package app.hisaab.platform

import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
actual class SecureStorage {
    // Platform stub — full Keychain implementation requires a Swift/ObjC bridge
    // that is wired in the iosApp Swift layer. For P0b, this stub satisfies
    // the expect/actual contract for compilation.
    actual fun storeMasterSecret(secret: ByteArray) { /* TODO: wire Keychain via Swift bridge */ }
    actual fun loadMasterSecret(): ByteArray? = null
    actual fun clearMasterSecret() {}
    actual fun storeString(key: String, value: String) {}
    actual fun loadString(key: String): String? = null
}
