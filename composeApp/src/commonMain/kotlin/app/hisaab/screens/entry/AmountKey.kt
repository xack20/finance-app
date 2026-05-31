package app.hisaab.screens.entry

/** Backspace key char emitted by the numeric keypad. */
const val BACKSPACE = '⌫' // ⌫

/**
 * Apply one keypad [key] to the current amount [current] and return the new amount string.
 * Rules: a digit replaces a sole leading "0"; only one '.'; max 2 decimals; integer part capped
 * at 12 digits; backspace drops the last char. Pure — no side effects.
 */
fun applyAmountKey(current: String, key: Char): String = when (key) {
    BACKSPACE -> current.dropLast(1)
    '.' -> when {
        current.contains('.') -> current
        current.isEmpty() -> "0."
        else -> "$current."
    }
    in '0'..'9' -> {
        val dot = current.indexOf('.')
        when {
            dot >= 0 && current.length - dot - 1 >= 2 -> current
            dot < 0 && current.length >= 12 -> current
            current == "0" -> key.toString()
            else -> current + key
        }
    }
    else -> current
}
