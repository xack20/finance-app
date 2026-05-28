package app.hisaab.platform

import androidx.fragment.app.FragmentActivity

/**
 * P0c-1 ships the expect/actual contract. Actual ActivityResultContracts.PickVisualMedia
 * integration is wired in P0c-2 Task 19 when EntryScreen needs an attachment.
 */
actual class ImagePicker(private val activity: FragmentActivity) {
    actual fun isAvailable(): Boolean = true

    actual suspend fun pickFromGallery(): PickedImage? {
        throw NotImplementedError("ImagePicker.pickFromGallery() is wired in P0c-2 Task 19")
    }

    actual suspend fun captureFromCamera(): PickedImage? {
        throw NotImplementedError("ImagePicker.captureFromCamera() is wired in P0c-2 Task 19")
    }
}
