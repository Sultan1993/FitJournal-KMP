package kz.maestrosultan.fitjournal.ui.postworkout.composer

import androidx.compose.ui.graphics.ImageBitmap
import kz.maestrosultan.fitjournal.ui.postworkout.export.ExportRequest

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

/**
 * Everything the share-card composer renders. Constructor defaults are the
 * FIRST-RUN defaults — what a user with no saved [ComposerDefaults] (or a
 * store that failed to load) sees.
 */
data class ComposerState(
    /** Card headline; composed from the summary's muscles on init, user-editable, ≤ [MAX_TITLE_LENGTH]. */
    val title: String = "",
    val layout: ShareLayoutKind = ShareLayoutKind.Stats,
    val backdrop: ComposerBackdrop = ComposerBackdrop.Brand,
    /** Backdrop darkening strength, 0 (off) .. 1 (full). */
    val scrim: Float = FIRST_RUN_SCRIM,
    /**
     * The stats shown on the Stats layout — ALWAYS exactly [STATS_PICK_SIZE]
     * entries, ordered oldest selection first (selecting another stat replaces
     * the oldest; deselecting below three is a no-op).
     */
    val statsPick: List<StatKind> = FirstRunStatsPick,
    /** Null until the user moves the block — the layout's default placement applies. */
    val transform: BlockTransform? = null,
    /** True when the user removed the card block entirely (photo-only card). */
    val blockRemoved: Boolean = false,
    val activeEditor: ComposerEditor? = null,
    /**
     * The pinned export handshake: non-null while the composable is expected
     * to render + PNG-encode the card for this request and answer with an
     * ExportResult carrying the same id.
     */
    val exportRequest: ExportRequest? = null,
    val chip: ComposerChip? = null,
) {
    companion object {
        const val STATS_PICK_SIZE: Int = 3
        const val MAX_TITLE_LENGTH: Int = 60
        const val FIRST_RUN_SCRIM: Float = 1.0f
        val FirstRunStatsPick: List<StatKind> =
            listOf(StatKind.Duration, StatKind.Sets, StatKind.BestSet)
    }
}
