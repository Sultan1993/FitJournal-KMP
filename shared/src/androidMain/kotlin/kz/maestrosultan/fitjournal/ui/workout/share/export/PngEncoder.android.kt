package kz.maestrosultan.fitjournal.ui.workout.share.export

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.ByteArrayOutputStream

internal actual fun ImageBitmap.encodeToPng(): ByteArray {
    val stream = ByteArrayOutputStream()
    val ok = asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream)
    check(ok) { "PNG encoding failed" }
    return stream.toByteArray()
}
