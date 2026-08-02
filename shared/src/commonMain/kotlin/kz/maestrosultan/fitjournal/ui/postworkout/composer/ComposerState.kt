package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.ui.graphics.ImageBitmap

/**
 * What sits behind the card block, at runtime. [Photo] carries the actual
 * picked bitmap, which is why [ComposerDefaults] persists only the flat
 * [BackdropKind] tag: bitmaps aren't persisted, so a saved Photo backdrop
 * restores as [Brand] (see ShareComposerViewModel's restore path).
 */
sealed interface ComposerBackdrop {

    /** The persisted tag for this backdrop — what [ComposerDefaults] stores. */
    val kind: BackdropKind

    data class Photo(val image: ImageBitmap) : ComposerBackdrop {
        override val kind: BackdropKind get() = BackdropKind.Photo
    }

    data object Brand : ComposerBackdrop {
        override val kind: BackdropKind get() = BackdropKind.Brand
    }

    data object Transparent : ComposerBackdrop {
        override val kind: BackdropKind get() = BackdropKind.Transparent
    }
}

/** Which editor panel is open, or null when the canvas has full focus. */
enum class ComposerEditor { Title, Layout, Backdrop, Stats, Scrim }

/**
 * Transient failure feedback chip. Shown by the ViewModel on a failed
 * export/share/save and auto-cleared ~2s later; never blocks the composer.
 */
enum class ComposerChip { ExportFailed, SaveFailed, SavePermission }
