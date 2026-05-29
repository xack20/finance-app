package app.hisaab.platform

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.hisaab.capture.HisaabNotificationListenerService
import app.hisaab.capture.CaptureBus
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

actual class CaptureService(
    private val context: Context,
    private val activity: FragmentActivity,
) : app.hisaab.capture.CaptureSource {

    private val permissionResult = MutableSharedFlow<Boolean>(replay = 0, extraBufferCapacity = 1)

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val allGranted = REQUIRED_SMS_PERMISSIONS.all { grants[it] == true }
            permissionResult.tryEmit(allGranted)
        }

    actual override fun capabilities(): Set<CaptureChannel> =
        setOf(CaptureChannel.SMS, CaptureChannel.NOTIFICATION)

    actual suspend fun hasSmsPermission(): Boolean = REQUIRED_SMS_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    actual suspend fun requestSmsPermission(): Boolean {
        if (hasSmsPermission()) return true
        permissionLauncher.launch(REQUIRED_SMS_PERMISSIONS)
        return permissionResult.first()
    }

    /** Live stream is the process-global [CaptureBus] populated by the receiver/service. */
    actual override fun observeIncoming(): Flow<RawCapture> = CaptureBus.captures

    /**
     * Catch-up / one-time backfill: every inbox SMS with `date > cursorMs`, oldest-first.
     * Reads only the inbox; bounded projection; never logs bodies. The coordinator's
     * `runInitialBackfill(now)` calls this with `now - 90 days` on first grant.
     */
    actual override suspend fun backfillSince(cursorMs: Long): List<RawCapture> =
        withContext(Dispatchers.Default) {
            if (!hasSmsPermission()) return@withContext emptyList()
            val results = mutableListOf<RawCapture>()
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            )
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                "${Telephony.Sms.DATE} > ?",
                arrayOf(cursorMs.toString()),
                "${Telephony.Sms.DATE} ASC",
            )?.use { cursor ->
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                while (cursor.moveToNext()) {
                    val address = cursor.getString(addressIdx) ?: continue
                    val body = cursor.getString(bodyIdx) ?: continue
                    if (body.isBlank()) continue
                    val date = cursor.getLong(dateIdx)
                    results.add(
                        RawCapture(
                            sender = address,
                            body = body,
                            receivedAt = date,
                            channel = CaptureChannel.SMS,
                        ),
                    )
                }
            }
            results
        }

    /** Deep-links to the OS notification-access screen so the user can grant the fallback path. */
    actual fun openNotificationAccessSettings() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** True if the user has granted notification access to our listener service. */
    fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val component = ComponentName(context, HisaabNotificationListenerService::class.java)
        return enabled.split(":").any {
            ComponentName.unflattenFromString(it) == component
        }
    }

    private companion object {
        val REQUIRED_SMS_PERMISSIONS = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        )
    }
}
