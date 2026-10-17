package app.hisaab.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.hisaab.MainActivity
import app.hisaab.capture.ChannelStatus
import app.hisaab.capture.SyncStatuses
import app.hisaab.capture.notificationText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Always-on capture: keeps the process alive so [HisaabEngine] can import history, process new SMS and poll
 * email / the Mac relay with the app minimized or the phone locked. `specialUse` (not `dataSync`, which Android
 * 15+ caps at 6 h/day). Holds a partial wake lock only while an import runs; stops itself when the engine's
 * background work stops (every channel off, signed out, DB closed).
 */
class CaptureForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val engine by lazy { HisaabEngine.get(applicationContext) }
    private var started = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start must reach startForeground within 5 s, even repeats. Android 12+ can refuse it (e.g. a
        // sticky restart from the background): stop quietly instead of crash-looping; the next trigger retries.
        val promoted = runCatching {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification(notificationText(engine.syncStatuses.value)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        }
        if (promoted.isFailure) {
            android.util.Log.w("HisaabCapture", "foreground start refused: ${promoted.exceptionOrNull()?.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch {
                if (!withContext(Dispatchers.Default) { engine.startBackground() }) {
                    stopSelf()
                    return@launch
                }
                launch {
                    engine.syncStatuses.collect { statuses ->
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, notification(notificationText(statuses)))
                        holdWakeLock(statuses.anyImporting())
                    }
                }
                engine.backgroundActive.first { !it }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        holdWakeLock(false)
        super.onDestroy()
    }

    private fun SyncStatuses.anyImporting() =
        listOf(sms, email, relay).any { it is ChannelStatus.Importing }

    private fun holdWakeLock(hold: Boolean) {
        val lock = wakeLock ?: getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).also { wakeLock = it }
        if (hold && !lock.isHeld) lock.acquire(WAKE_LOCK_TIMEOUT_MS) // safety cap; re-acquired on the next update
        if (!hold && lock.isHeld) lock.release()
    }

    private fun notification(text: String) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_notify_sync)
        .setContentTitle("Hisaab")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Background sync", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private companion object {
        const val CHANNEL_ID = "sync"
        const val NOTIFICATION_ID = 1
        const val WAKE_LOCK_TAG = "hisaab:import"
        const val WAKE_LOCK_TIMEOUT_MS = 2L * 60 * 60 * 1000
    }
}

/** Starts the capture service from anywhere; Android may refuse a background start — the next one recovers. */
object CaptureServiceStarter {
    fun start(context: Context) {
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, CaptureForegroundService::class.java))
        }.onFailure { android.util.Log.w("HisaabCapture", "service start refused: ${it.message}") }
    }
}
