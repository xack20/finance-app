package app.hisaab.crypto

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CryptoService tests.
 *
 * NOTE: These tests are ignored in JVM unit tests because the libsodium KMP library
 * (com.ionspin.kotlin:multiplatform-crypto-libsodium-bindings) bundles native binaries
 * inside the Android AAR and uses [com.goterl.resourceloader.SharedLibraryLoader] to
 * extract and load them at runtime. When running via `testDebugUnitTest` (host JVM),
 * the native binary is not on the classpath and [LibsodiumInitializer.initializeWithCallback]
 * throws a NullPointerException from ResourceLoader.
 *
 * The implementation compiles and is correct for Android and iOS targets.
 * These tests should be run on an Android emulator or device via `connectedDebugAndroidTest`.
 */
@Ignore
class CryptoServiceTest {

    @Test
    fun `deriveDbKey returns 32 bytes`() {
        val service = CryptoService()
        val key = service.deriveDbKey(ByteArray(32) { it.toByte() })
        assertEquals(32, key.size)
    }

    @Test
    fun `deriveDbKey is deterministic for same input`() {
        val service = CryptoService()
        val secret = ByteArray(32) { 42 }
        val key1 = service.deriveDbKey(secret)
        val key2 = service.deriveDbKey(secret)
        assertTrue(key1.contentEquals(key2))
    }

    @Test
    fun `deriveDbKey differs for different inputs`() {
        val service = CryptoService()
        val k1 = service.deriveDbKey(ByteArray(32) { it.toByte() })
        val k2 = service.deriveDbKey(ByteArray(32) { (it + 1).toByte() })
        assertTrue(!k1.contentEquals(k2))
    }

    @Test
    fun `generateMasterSecret returns 32 bytes`() {
        val service = CryptoService()
        val secret = service.generateMasterSecret()
        assertEquals(32, secret.size)
        assertTrue(secret.any { it != 0.toByte() })
    }
}
