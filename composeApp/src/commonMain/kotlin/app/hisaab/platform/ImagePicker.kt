package app.hisaab.platform

data class PickedImage(val bytes: ByteArray, val mimeType: String)

expect class ImagePicker {
    /** Returns null if user cancelled. */
    suspend fun pickFromGallery(): PickedImage?
    suspend fun captureFromCamera(): PickedImage?
    fun isAvailable(): Boolean
}
