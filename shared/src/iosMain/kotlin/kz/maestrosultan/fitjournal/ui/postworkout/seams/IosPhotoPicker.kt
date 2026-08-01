package kz.maestrosultan.fitjournal.ui.postworkout.seams

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * iOS [PhotoPicker]: Swift supplies only [pickEncodedPhoto] — a closure that
 * presents PHPickerViewController and resolves to the picked photo's
 * JPEG/PNG-encoded bytes (or nil on cancel/failure). All decoding stays on the
 * Kotlin side so the Swift surface remains a single NSData handoff.
 */
class IosPhotoPicker(
    private val pickEncodedPhoto: suspend () -> NSData?,
) : PhotoPicker {

    override suspend fun pickPhoto(): ImageBitmap? =
        pickEncodedPhoto()?.toByteArray()?.decodeToImageBitmap()
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
