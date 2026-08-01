package kz.maestrosultan.fitjournal.ui.postworkout.seams

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

/**
 * Decodes encoded image bytes (JPEG/PNG — Swift's picker transcodes HEIC
 * before handing bytes over) into an [ImageBitmap].
 *
 * Returns null on any decode failure — callers treat that as "no usable
 * photo". Never throws: an unbridged Kotlin exception would SIGABRT the iOS
 * app.
 */
fun ByteArray.decodeToImageBitmap(): ImageBitmap? {
    if (isEmpty()) return null
    // Typed as Image? so this compiles against both skiko signatures of
    // makeFromEncoded (throwing non-null vs nullable return).
    val image: Image? = runCatching { Image.makeFromEncoded(this) }.getOrNull()
    return runCatching { image?.toComposeImageBitmap() }.getOrNull()
}
