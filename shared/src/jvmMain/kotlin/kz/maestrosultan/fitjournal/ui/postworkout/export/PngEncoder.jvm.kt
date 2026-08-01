package kz.maestrosultan.fitjournal.ui.postworkout.export

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

internal actual fun ImageBitmap.encodeToPng(): ByteArray {
    val data = Image.makeFromBitmap(asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
    return checkNotNull(data) { "PNG encoding failed" }.bytes
}
