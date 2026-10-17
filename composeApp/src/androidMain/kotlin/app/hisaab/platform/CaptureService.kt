package app.hisaab.platform

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import app.hisaab.capture.HisaabNotificationListenerService
import app.hisaab.capture.SmsCaptureSource
import app.hisaab.capture.SmsCaptureSource.Companion.REQUIRED_SMS_PERMISSIONS
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Activity-bound side of Android capture: the SMS permission request (needs registerForActivityResult) and
 * notification-access settings. Reading SMS is delegated to [SmsCaptureSource], which the process-scoped
 * engine also uses for background capture.
 */
actual class CaptureService(
    private val context: Context,
    private val activity: FragmentActivity,
) : app.hisaab.capture.CaptureSource {

    private val sms = SmsCaptureSource(context)

    private val permissionResult = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1)
    private val permissionMutex = Mutex()

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val allGranted = REQUIRED_SMS_PERMISSIONS.all { grants[it] == true }
            permissionResult.tryEmit(allGranted)
        }

    actual override fun capabilities(): Set<CaptureChannel> = sms.capabilities()

    actual suspend fun hasSmsPermission(): Boolean = sms.hasSmsPermission()

    actual suspend fun requestSmsPermission(): Boolean = permissionMutex.withLock {
        if (hasSmsPermission()) return@withLock true
        permissionLauncher.launch(REQUIRED_SMS_PERMISSIONS)
        permissionResult.first()
    }

    actual override fun observeIncoming(): Flow<RawCapture> = sms.observeIncoming()

    actual override suspend fun backfillSince(cursorMs: Long): List<RawCapture> = sms.backfillSince(cursorMs)

    /** Deep-links to the OS notification-access screen so the user can grant the fallback path. */
    actual fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** True if the user has granted notification access to our listener service. */
    actual fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val component = ComponentName(context, HisaabNotificationListenerService::class.java)
        return enabled.split(":").any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    /** Mirrors the persisted notification flag to the OS-bound listener's process-global gate. */
    actual fun setNotificationListenerEnabled(enabled: Boolean) {
        app.hisaab.capture.NotificationCaptureGate.enabled = enabled
    }
}
