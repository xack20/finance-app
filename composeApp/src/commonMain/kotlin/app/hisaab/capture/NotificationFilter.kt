package app.hisaab.capture

// A written amount (Tk 500 / ৳1,200 / 500 টাকা / BDT 3,000) or a transaction id.
private val moneySignal = Regex(
    """(?i)(?:\btk\.?|\bbdt|৳|\btaka)\s*[0-9০-৯]|[0-9০-৯][0-9০-৯,.]*\s*(?:tk\b|bdt\b|৳|টাকা)|\b(?:trx|txn)\s*id\b""",
)

/**
 * Privacy guard for the notification fallback, not a judgment about payments: only notifications that mention
 * money leave the phone. Hisaab's own notifications (its sync status would feed itself) and ongoing / foreground
 * notifications are never captured; chats without an amount stay on the phone.
 */
fun shouldCaptureNotification(packageName: String, ownPackage: String, ongoing: Boolean, text: String): Boolean =
    packageName != ownPackage && !ongoing && moneySignal.containsMatchIn(text)
