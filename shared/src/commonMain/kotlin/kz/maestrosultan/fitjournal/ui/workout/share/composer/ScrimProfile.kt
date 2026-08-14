package kz.maestrosultan.fitjournal.ui.workout.share.composer

/**
 * Vertical scrim spec for a share-card layout: a gradient rising from the
 * bottom edge of the canvas that guarantees text legibility over arbitrary
 * photos.
 *
 * [heightFraction] is how much of the canvas height the scrim covers (from the
 * bottom). [stops] are `position to alpha` pairs where position 0f is the top
 * edge of the scrim (fully clear) and 1f its bottom edge; alpha is the black
 * overlay opacity at that stop. Layout tasks turn this into a
 * `Brush.verticalGradient` sized to `canvasHeight * heightFraction`.
 */
internal data class ScrimProfile(
    val heightFraction: Float,
    val stops: List<Pair<Float, Float>>,
)

/** Scrim used by the compact card layouts (block hugs the lower canvas). */
private val BlockScrim = ScrimProfile(
    heightFraction = 0.46f,
    stops = listOf(0f to 0f, 0.62f to 0.72f, 1f to 0.9f),
)

/** Taller, denser scrim behind the Receipt layout's long text column. */
private val ReceiptScrim = ScrimProfile(
    heightFraction = 0.78f,
    stops = listOf(0f to 0f, 0.44f to 0.78f, 1f to 0.92f),
)

internal val ShareLayoutKind.scrimProfile: ScrimProfile
    get() = when (this) {
        ShareLayoutKind.Stats,
        ShareLayoutKind.Muscles,
        ShareLayoutKind.NewBest,
        -> BlockScrim

        ShareLayoutKind.Receipt -> ReceiptScrim
    }
