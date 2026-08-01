package kz.maestrosultan.fitjournal.ui.postworkout.seams

/** Outcome of writing an exported card into the platform photo library. */
enum class SaveResult { Saved, PermissionDenied, Failed }

/**
 * Platform share/save surface for the exported card PNG.
 *
 * [presentShareSheet] suspends until the sheet has been presented (not until
 * the user completes or cancels sharing — platforms don't report that
 * reliably). [saveToPhotos] suspends until the write finished and reports the
 * outcome so the UI can confirm or route the user to permission settings.
 */
interface SharePresenter {
    suspend fun presentShareSheet(png: ByteArray)
    suspend fun saveToPhotos(png: ByteArray): SaveResult
}
