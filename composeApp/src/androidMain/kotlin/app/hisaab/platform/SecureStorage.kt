package app.hisaab.platform

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

actual class SecureStorage(private val context: Context) {

    private val prefs by lazy {
        val masterKey = try {
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(true)
                .build()
        } catch (e: Exception) {
            // Fallback: StrongBox not available on this device
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
        }
        EncryptedSharedPreferences.create(
            context,
            "hisaab_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    actual fun storeMasterSecret(secret: ByteArray) {
        prefs.edit()
            .putString(KEY_MASTER_SECRET, Base64.encodeToString(secret, Base64.NO_WRAP))
            .apply()
    }

    actual fun loadMasterSecret(): ByteArray? =
        prefs.getString(KEY_MASTER_SECRET, null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) }

    actual fun clearMasterSecret() {
        prefs.edit().remove(KEY_MASTER_SECRET).apply()
    }

    actual fun storeString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    actual fun loadString(key: String): String? = prefs.getString(key, null)

    companion object {
        private const val KEY_MASTER_SECRET = "master_secret"
    }
}
