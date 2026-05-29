package app.hisaab.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage

/**
 * Live SMS path (latency bonus). Receives the system `SMS_RECEIVED` broadcast, decodes its PDUs via
 * [Telephony.Sms.Intents.getMessagesFromIntent], reduces multipart parts to (sender, joined body,
 * earliest timestamp), and delegates the channel-agnostic mapping to the pure [smsPartsToRawCapture]
 * (commonMain, unit-tested). Publishes results on [CaptureBus]. It never touches the DB or the
 * ledger directly — the coordinator (only alive while unlocked) consumes the bus.
 *
 * Declared in the manifest guarded by `android.permission.BROADCAST_SMS` so only the OS can
 * deliver to it. It does NOT abort the broadcast (non-default SMS app behavior).
 */
class SmsBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages: Array<SmsMessage> =
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        for (raw in reduceToCaptures(messages)) {
            CaptureBus.publish(raw)
        }
    }

    private fun reduceToCaptures(messages: Array<SmsMessage>) = buildList {
        // Group multipart parts that share an originating address; preserve arrival order.
        val grouped = LinkedHashMap<String, MutableList<SmsMessage>>()
        for (m in messages) {
            val sender = m.originatingAddress ?: m.displayOriginatingAddress ?: ""
            grouped.getOrPut(sender) { mutableListOf() }.add(m)
        }
        for ((sender, parts) in grouped) {
            val body = parts.joinToString(separator = "") {
                it.messageBody ?: it.displayMessageBody ?: ""
            }
            val receivedAt = parts.minOf { it.timestampMillis }
            smsPartsToRawCapture(sender = sender, body = body, receivedAt = receivedAt)
                ?.let { add(it) }
        }
    }
}
