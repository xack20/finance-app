package app.hisaab.platform

actual class ContactPicker {
    actual fun isAvailable(): Boolean = false
    actual suspend fun pickContact(): ContactPick? = null
}
