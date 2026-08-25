package kz.maestrosultan.fitjournal.kmp.time

import kotlin.time.Instant
import kz.maestrosultan.fitjournal.ui.format.formatDuration

/**
 * How long a running workout has been running, for surfaces written NATIVELY.
 *
 * The workout screen's session bar already renders this, but it lives in shared
 * Compose and formats through `ui/format/formatDuration`, which is `internal` to
 * this module. Home is native on both platforms (SwiftUI/UIKit cell on iOS,
 * Compose item on Android) and so cannot reach it — and the one thing worse than
 * a second formatter is two of them disagreeing about the same session on two
 * screens of the same app. These delegate rather than reimplement.
 */

/** Seconds since [startedAt], clamped at 0 so a clock change cannot show a negative. */
fun elapsedSecondsSince(startedAt: Instant, now: Instant): Long =
    (now - startedAt).inWholeSeconds.coerceAtLeast(0L)

/** `m:ss`, or `h:mm:ss` once past the hour — byte-identical to the session bar's. */
fun formatElapsed(seconds: Long): String = formatDuration(seconds)
