package kz.maestrosultan.fitjournal.ui.workout.share.composer

import kotlinx.serialization.Serializable

/** Which share-card layout the composer renders. */
enum class ShareLayoutKind { Stats, Receipt, Muscles, NewBest }

/** What sits behind the card block. */
enum class BackdropKind { Photo, Brand, Transparent }

/** Stats the user can pick onto the Stats layout. */
enum class StatKind { Duration, Sets, Exercises, BestSet, TotalReps }

/**
 * Placement of the draggable card block on the canvas. [cx]/[cy] are the block
 * center in canvas-relative coordinates (0..1), [scale] is relative to the
 * layout's natural size, [rotationDeg] is clockwise degrees.
 */
@Serializable
data class BlockTransform(
    val cx: Float,
    val cy: Float,
    val scale: Float,
    val rotationDeg: Float,
)

/**
 * Last-used composer setup, JSON-persisted via
 * [kz.maestrosultan.fitjournal.ui.workout.share.seams.ComposerDefaultsStore].
 * [transform] is null while the user never moved the block (layout default
 * placement applies); [blockRemoved] survives so a photo-only card stays
 * photo-only next time.
 */
@Serializable
data class ComposerDefaults(
    val layout: ShareLayoutKind,
    val backdropKind: BackdropKind,
    val statsPick: List<StatKind>,
    val scrim: Float,
    val transform: BlockTransform?,
    val blockRemoved: Boolean,
)
