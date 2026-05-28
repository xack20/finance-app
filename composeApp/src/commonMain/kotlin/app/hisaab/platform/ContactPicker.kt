package app.hisaab.platform

data class ContactPick(val displayName: String, val phone: String?)

expect class ContactPicker {
    /** Returns null if user cancelled or permission denied. */
    suspend fun pickContact(): ContactPick?
    fun isAvailable(): Boolean
}
