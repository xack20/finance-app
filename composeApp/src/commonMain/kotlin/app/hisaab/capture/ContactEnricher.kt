package app.hisaab.capture

// Same shape the Redactor masks: optional +88 / 88, then an 11-digit BD mobile number.
private val phoneRegex = Regex("""(?:\+?88)?01[3-9]\d{8}""")

/**
 * Adds the phone-book name after each BD mobile number ("to 01712345678 (contact: Rahim)") so the agent can tell
 * who money went to. Runs on the phone before redaction, which then masks the number but keeps the name.
 * [lookup] receives the local 11-digit form (01XXXXXXXXX).
 */
fun enrichWithContacts(text: String, lookup: (String) -> String?): String =
    phoneRegex.replace(text) { m ->
        val name = lookup(m.value.filter { it.isDigit() }.takeLast(11))
        if (name.isNullOrBlank()) m.value else "${m.value} (contact: $name)"
    }
