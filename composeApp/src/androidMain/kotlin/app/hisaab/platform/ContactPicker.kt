package app.hisaab.platform

import androidx.fragment.app.FragmentActivity

/**
 * P0c-1 ships the expect/actual contract and isAvailable() check.
 * The actual launcher integration is wired in P0c-3 Task 21 when PeopleListScreen
 * needs it; pickContact() throws if called before then.
 */
actual class ContactPicker(private val activity: FragmentActivity) {
    actual fun isAvailable(): Boolean = true

    actual suspend fun pickContact(): ContactPick? {
        throw NotImplementedError("ContactPicker.pickContact() is wired in P0c-3 Task 21")
    }
}
