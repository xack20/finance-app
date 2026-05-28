package app.hisaab.platform

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first

actual class ImagePicker(private val activity: FragmentActivity) {
    private val resultFlow = MutableSharedFlow<PickedImage?>(replay = 0, extraBufferCapacity = 1)

    private val launcher: ActivityResultLauncher<PickVisualMediaRequest> =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri == null) {
                resultFlow.tryEmit(null)
                return@registerForActivityResult
            }
            val resolver = activity.contentResolver
            val mime = resolver.getType(uri) ?: "image/jpeg"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                resultFlow.tryEmit(null)
            } else {
                resultFlow.tryEmit(PickedImage(bytes, mime))
            }
        }

    actual fun isAvailable(): Boolean = true

    actual suspend fun pickFromGallery(): PickedImage? {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        return resultFlow.first()
    }

    actual suspend fun captureFromCamera(): PickedImage? {
        // For P0c, alias to gallery picker; real camera capture comes in P0d.
        return pickFromGallery()
    }
}
