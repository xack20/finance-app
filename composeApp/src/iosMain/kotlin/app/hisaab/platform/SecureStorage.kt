@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package app.hisaab.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * iOS Keychain-backed secure storage — parity with Android's EncryptedSharedPreferences.
 *
 * Items are `kSecClassGenericPassword` under a fixed service, keyed by the caller's [account] string,
 * and marked `AfterFirstUnlockThisDeviceOnly` (readable by a background unlock, never synced to iCloud,
 * non-extractable to another device). This persists the `master_secret` (the cold-start lock/unlock
 * model) and the BYO cloud LLM API keys the assistant authenticates with — both of which the previous
 * stub silently dropped.
 */
actual class SecureStorage {

    actual fun storeMasterSecret(secret: ByteArray) = set(KEY_MASTER_SECRET, secret)
    actual fun loadMasterSecret(): ByteArray? = get(KEY_MASTER_SECRET)
    actual fun clearMasterSecret() = delete(KEY_MASTER_SECRET)

    actual fun storeString(key: String, value: String) = set(key, value.encodeToByteArray())
    actual fun loadString(key: String): String? = get(key)?.decodeToString()

    private fun set(account: String, bytes: ByteArray) {
        delete(account) // upsert — Keychain SecItemAdd fails on a duplicate item
        val query = baseQuery(account)
        val data = CFBridgingRetain(bytes.toNSData())
        CFDictionaryAddValue(query, kSecValueData, data)
        CFRelease(data)
        CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
        SecItemAdd(query, null)
        CFRelease(query)
    }

    private fun get(account: String): ByteArray? = memScoped {
        val query = baseQuery(account)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped null
        (CFBridgingRelease(result.value) as? NSData)?.toByteArray()
    }

    private fun delete(account: String) {
        val query = baseQuery(account)
        SecItemDelete(query)
        CFRelease(query)
    }

    private fun baseQuery(account: String): CFMutableDictionaryRef? {
        val query = CFDictionaryCreateMutable(
            null, 0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        val service = CFStringCreateWithCString(null, SERVICE, kCFStringEncodingUTF8)
        CFDictionaryAddValue(query, kSecAttrService, service)
        CFRelease(service)
        val acct = CFStringCreateWithCString(null, account, kCFStringEncodingUTF8)
        CFDictionaryAddValue(query, kSecAttrAccount, acct)
        CFRelease(acct)
        return query
    }

    private fun ByteArray.toNSData(): NSData =
        if (isEmpty()) {
            NSData()
        } else {
            usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }
        }

    private fun NSData.toByteArray(): ByteArray {
        val len = length.toInt()
        if (len == 0) return ByteArray(0)
        val out = ByteArray(len)
        out.usePinned { dst -> memcpy(dst.addressOf(0), bytes, length) }
        return out
    }

    private companion object {
        const val SERVICE = "app.hisaab.secure"
        const val KEY_MASTER_SECRET = "master_secret"
    }
}
