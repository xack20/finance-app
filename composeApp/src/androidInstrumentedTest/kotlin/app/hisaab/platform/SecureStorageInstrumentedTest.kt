package app.hisaab.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Instrumented round-trip tests for [SecureStorage] (Android EncryptedSharedPreferences + Keystore).
 *
 * These run on a device/emulator because EncryptedSharedPreferences needs the hardware-backed
 * Android Keystore (unavailable in plain JVM unit tests). [SecureStorage] persists the `master_secret`
 * that the entire lock/unlock model depends on — `AppViewModel` routes a cold start to `Locked`
 * (vs `Onboarding`) ONLY when `loadMasterSecret() != null`, so a silent storage regression would
 * either lock users out or skip the lock screen entirely. The cross-instance test below is the direct
 * analogue of a real cold start: a fresh process must read what a prior session wrote.
 */
@RunWith(AndroidJUnit4::class)
class SecureStorageInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clean() {
        context.deleteSharedPreferences(PREFS)
    }

    @After
    fun cleanup() {
        context.deleteSharedPreferences(PREFS)
    }

    private fun storage() = SecureStorage(context)

    @Test
    fun storeString_then_loadString_round_trips() {
        val s = storage()
        s.storeString("llm_api_key_CLAUDE", "value-123")
        assertEquals("value-123", s.loadString("llm_api_key_CLAUDE"))
    }

    @Test
    fun loadString_returns_null_for_an_unknown_key() {
        assertNull(storage().loadString("never-written"))
    }

    @Test
    fun master_secret_round_trips_byte_for_byte() {
        val s = storage()
        // Full byte range, including values that go negative as signed bytes — guards the Base64 NO_WRAP
        // encode/decode used internally.
        val secret = ByteArray(32) { (it * 7 + 3).toByte() }
        s.storeMasterSecret(secret)

        val loaded = s.loadMasterSecret()
        assertNotNull(loaded)
        assertEquals(32, loaded.size)
        assertTrue(secret.contentEquals(loaded), "loaded master_secret must equal the stored bytes")
    }

    @Test
    fun loadMasterSecret_returns_null_when_nothing_is_stored() {
        assertNull(storage().loadMasterSecret())
    }

    @Test
    fun clearMasterSecret_removes_the_secret() {
        val s = storage()
        s.storeMasterSecret(ByteArray(32) { 1 })
        s.clearMasterSecret()
        assertNull(s.loadMasterSecret())
    }

    @Test
    fun storeMasterSecret_overwrites_a_previous_secret() {
        val s = storage()
        s.storeMasterSecret(ByteArray(32) { 0xAA.toByte() })
        val second = ByteArray(32) { 0x55.toByte() }
        s.storeMasterSecret(second)
        assertTrue(second.contentEquals(s.loadMasterSecret()), "latest write must win")
    }

    @Test
    fun secret_persists_across_SecureStorage_instances() {
        // Cold-start lock model: a NEW instance (≈ a new process after the app was killed) must read
        // the master_secret a prior session persisted — otherwise the app can't unlock the SQLCipher DB.
        val secret = ByteArray(32) { (255 - it).toByte() }
        storage().storeMasterSecret(secret)

        val reloaded = storage().loadMasterSecret() // fresh instance, same on-disk prefs
        assertNotNull(reloaded)
        assertTrue(secret.contentEquals(reloaded), "a new instance must read the persisted secret")
    }

    private companion object {
        // Must match SecureStorage's EncryptedSharedPreferences file name.
        const val PREFS = "hisaab_secure_prefs"
    }
}
