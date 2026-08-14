package kz.maestrosultan.fitjournal.ui.workout.share.seams

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * The Swift-facing halves of the composer's seams.
 *
 * Callback-shaped rather than `suspend` on purpose. SKIE bridges Kotlin suspend
 * *members* into Swift `async`, but a suspend *function type* used as a
 * parameter stays a raw `KotlinSuspendFunction0/1`, which Swift cannot satisfy
 * with a closure. Plain protocols with completion handlers cross the boundary
 * cleanly in the Swift-implements-Kotlin direction, and the adapters below turn
 * them back into the suspend seams the shared ViewModel expects — so the
 * awkwardness is confined to this file instead of every call site.
 */

/**
 * Presents PHPicker and hands back the picked photo's encoded bytes, or null on
 * cancel.
 *
 * Returns a [IosCancellable] so a cancelled Kotlin coroutine can tear the
 * native picker down. Without it, disposing the composer mid-pick left the
 * PHPicker on screen with nothing waiting for its result.
 */
interface IosPhotoPickerBridge {
    fun pickEncodedPhoto(onResult: (NSData?) -> Unit): IosCancellable
}

/** Handle to in-flight native work, so Kotlin cancellation can reach it. */
interface IosCancellable {
    fun cancel()
}

/** Presents the share sheet and writes to the photo library. */
interface IosSharePresenterBridge {
    /**
     * Resumes once the sheet is PRESENTED, not once sharing completes — see
     * [SharePresenter].
     *
     * [onPresented] carries whether the sheet actually appeared. It used to be
     * a bare `() -> Unit`, which the Swift side called even when it had bailed
     * out (no presenting controller, or the temp-file write failed) — so the
     * ViewModel recorded a success, persisted defaults and showed no error for
     * a share that never happened. Android reports the same condition as a
     * failure chip; this is what makes the two agree.
     */
    fun presentShareSheet(png: NSData, onPresented: (Boolean) -> Unit): IosCancellable
    fun saveToPhotos(png: NSData, onResult: (SaveResult) -> Unit): IosCancellable
}

/** A `UserDefaults` string slot. Synchronous, because `UserDefaults` is. */
interface IosComposerDefaultsBridge {
    fun read(): String?
    fun write(json: String)
}

/** iOS [PhotoPicker]: all decoding stays here, so Swift only hands over bytes. */
class IosPhotoPicker(
    private val bridge: IosPhotoPickerBridge,
) : PhotoPicker {

    override suspend fun pickPhoto(): ImageBitmap? {
        val data = suspendCancellableCoroutine { continuation ->
            val work = bridge.pickEncodedPhoto { data -> continuation.resume(data) }
            continuation.invokeOnCancellation { work.cancel() }
        }
        val bytes = data?.toByteArray() ?: return null
        // Off the main thread: the only caller is a ViewModel running on
        // Dispatchers.Main.immediate, and decodeToImageBitmap rasterizes the
        // whole image (width * height * 4 bytes). A 12-megapixel camera photo
        // is a ~48 MB allocation plus the decode — several hundred ms of frozen
        // composer if it lands on the UI thread.
        return withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
    }
}

/** iOS [SharePresenter] over the Swift presenter. */
class IosSharePresenter(
    private val bridge: IosSharePresenterBridge,
) : SharePresenter {

    override suspend fun presentShareSheet(png: ByteArray) {
        val data = png.toNSData()
        val presented = suspendCancellableCoroutine { continuation ->
            val work = bridge.presentShareSheet(data) { presented -> continuation.resume(presented) }
            continuation.invokeOnCancellation { work.cancel() }
        }
        // The seam signals failure by throwing — `SharePresenter` returns Unit,
        // and the ViewModel's `guarded {}` already turns a throw into the
        // "Couldn't export" chip. Same user-visible outcome as Android.
        check(presented) { "share sheet could not be presented" }
    }

    override suspend fun saveToPhotos(png: ByteArray): SaveResult {
        val data = png.toNSData()
        return suspendCancellableCoroutine { continuation ->
            val work = bridge.saveToPhotos(data) { result -> continuation.resume(result) }
            continuation.invokeOnCancellation { work.cancel() }
        }
    }
}

/** iOS [ComposerDefaultsStorage]; the JSON itself is shared's business. */
class IosComposerDefaultsStorage(
    private val bridge: IosComposerDefaultsBridge,
) : ComposerDefaultsStorage {

    override suspend fun read(): String? = bridge.read()

    override suspend fun write(json: String) = bridge.write(json)
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
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
