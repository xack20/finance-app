package app.hisaab.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CryptoServiceInstrumentedTest {

    @Test
    fun deriveDbKey_returns_32_bytes() {
        val service = CryptoService()
        val key = service.deriveDbKey(ByteArray(32) { it.toByte() })
        assertEquals(32, key.size)
    }

    @Test
    fun deriveDbKey_is_deterministic_for_same_input() {
        val service = CryptoService()
        val secret = ByteArray(32) { 42 }
        val key1 = service.deriveDbKey(secret)
        val key2 = service.deriveDbKey(secret)
        assertTrue(key1.contentEquals(key2))
    }

    @Test
    fun deriveDbKey_differs_for_different_inputs() {
        val service = CryptoService()
        val k1 = service.deriveDbKey(ByteArray(32) { it.toByte() })
        val k2 = service.deriveDbKey(ByteArray(32) { (it + 1).toByte() })
        assertTrue(!k1.contentEquals(k2))
    }

    @Test
    fun generateMasterSecret_returns_32_random_bytes() {
        val service = CryptoService()
        val secret = service.generateMasterSecret()
        assertEquals(32, secret.size)
        assertTrue(secret.any { it != 0.toByte() })
    }
}
