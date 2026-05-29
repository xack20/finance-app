package app.hisaab.capture

import app.hisaab.domain.CaptureChannel
import app.hisaab.domain.RawCapture

/** Sentinel used when a received SMS has no resolvable originating address. */
const val UNKNOWN_SMS_SENDER: String = "unknown"

/**
 * Pure mapping from already-reduced SMS parts to a [RawCapture].
 *
 * The androidMain receiver does the platform-specific PDU→[android.telephony.SmsMessage] decode,
 * concatenates the multipart bodies into a single [body] (in arrival order), takes the earliest
 * part timestamp as [receivedAt], and resolves the [sender] (originating address). This function
 * owns the channel-agnostic, deterministic rest: blank-body dropping, sender fallback, and
 * stamping the SMS channel. Keeping it here makes it unit-testable on the JVM with synthetic
 * inputs — no `android.telephony` types, no device, no PDU hex.
 *
 * @return a [RawCapture] with `channel = SMS`, or `null` if [body] is blank (nothing to capture).
 */
fun smsPartsToRawCapture(sender: String, body: String, receivedAt: Long): RawCapture? {
    if (body.isBlank()) return null
    return RawCapture(
        sender = sender.ifBlank { UNKNOWN_SMS_SENDER },
        body = body,
        receivedAt = receivedAt,
        channel = CaptureChannel.SMS,
    )
}
