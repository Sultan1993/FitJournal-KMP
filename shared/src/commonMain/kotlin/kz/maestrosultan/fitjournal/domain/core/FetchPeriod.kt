package kz.maestrosultan.fitjournal.domain.core

/**
 * Time-window selector for history pages — "last week", "last month",
 * etc. Carries the simple `daysAgo` integer; the actual
 * `from`/`to` date strings are computed by callers (or by the
 * `getRecordsByPeriod` use case) using their preferred clock + timezone.
 *
 * Localized titles live as platform extensions:
 *  - iOS:     `extension FetchPeriod { var title: String }`
 *  - Android: `val FetchPeriod.titleResId: Int`
 */
enum class FetchPeriod(val daysAgo: Int) {
    WEEK(7),
    TWO_WEEKS(14),
    MONTH(30),
    THREE_MONTHS(90),
    HALF_YEAR(180),
    YEAR(365)
}
