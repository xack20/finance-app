package app.hisaab.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * The SMS-reading half of capture: live SMS/notification captures from the process-global [CaptureBus] plus
 * inbox backfill via the ContentResolver. Needs only a Context (no Activity), so the process-scoped engine
 * can run it in the background; asking for the permission stays on the Activity-bound CaptureService.
 */
class SmsCaptureSource(private val context: Context) : CaptureSource {

    override fun capabilities(): Set<CaptureChannel> = setOf(CaptureChannel.SMS, CaptureChannel.NOTIFICATION)

    fun hasSmsPermission(): Boolean = REQUIRED_SMS_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Live stream is the process-global [CaptureBus] populated by the receiver/service. */
    override fun observeIncoming(): Flow<RawCapture> = CaptureBus.captures

    /**
     * Catch-up / one-time backfill: every inbox SMS with `date > cursorMs`, oldest-first.
     * Reads only the inbox; bounded projection; never logs bodies. The coordinator's
     * `CaptureCoordinator.importAllHistory()` calls this with 0 to import every SMS on the phone.
     */
    override suspend fun backfillSince(cursorMs: Long): List<RawCapture> =
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

    companion object {
        val REQUIRED_SMS_PERMISSIONS = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
        )
    }
}
