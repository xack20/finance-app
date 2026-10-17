package app.hisaab.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts background capture after a reboot or an app update; the service decides whether it's wanted. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            CaptureServiceStarter.start(context)
        }
    }
}
