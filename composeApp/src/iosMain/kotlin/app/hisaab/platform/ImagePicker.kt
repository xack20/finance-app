package app.hisaab.platform

actual class ImagePicker {
    actual fun isAvailable(): Boolean = false
    actual suspend fun pickFromGallery(): PickedImage? = null
    actual suspend fun captureFromCamera(): PickedImage? = null
}
