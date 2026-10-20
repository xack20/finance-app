package app.hisaab.platform

import android.provider.ContactsContract
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

actual class ContactPicker(private val activity: FragmentActivity) {
    private val resultFlow = MutableSharedFlow<ContactPick?>(replay = 0, extraBufferCapacity = 1)

    private val launcher: ActivityResultLauncher<Void?> =
        activity.registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
            if (uri == null) {
                resultFlow.tryEmit(null)
                return@registerForActivityResult
            }
            val resolver = activity.contentResolver
            var name: String? = null
            var contactId: String? = null
            resolver.query(
                uri,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts._ID),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME))
                    contactId = c.getString(c.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                }
            }
            val phone = contactId?.let { id ->
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                    arrayOf(id),
                    null,
                )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }
            val resolvedName = name
            if (resolvedName != null) {
                resultFlow.tryEmit(ContactPick(displayName = resolvedName, phone = phone))
            } else {
                resultFlow.tryEmit(null)
            }
        }

    actual fun isAvailable(): Boolean = true

    actual suspend fun pickContact(): ContactPick? {
        launcher.launch(null)
        return resultFlow.first()
    }
}
