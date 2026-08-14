package kz.maestrosultan.fitjournal.ui.workout.share.export

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Encodes a rendered share card into PNG bytes. Throws if the platform encoder
 * rejects the bitmap — the export pipeline catches and maps failures to
 * [ExportResult.Failure]; the error must never escape to Swift (an unbridged
 * Kotlin exception aborts the iOS app).
 */
internal expect fun ImageBitmap.encodeToPng(): ByteArray
