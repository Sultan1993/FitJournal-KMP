package kz.maestrosultan.fitjournal.kmp.time

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Read an `Instant` back from a SQLite TEXT column.
 *
 * The unified storage format is ISO 8601 with explicit UTC zone (the
 * default `kotlin.time.Instant.toString()` output, e.g.
 * `"2024-01-15T10:30:00.123Z"`).
 *
 * Pre-FJ-2.0 rows wrote `kotlinx.datetime.LocalDateTime.toString()` which
 * has no zone (e.g. `"2024-01-15T10:30:00.123"`). We anchor those at UTC
 * so they round-trip without a DB migration.
 */
internal fun parseStoredInstant(text: String): Instant =
    runCatching { Instant.parse(text) }
        .getOrElse { LocalDateTime.parse(text).toInstant(TimeZone.UTC) }

/**
 * Format an `Instant` for a SQLite TEXT column. Always ISO 8601 with
 * explicit zone — making the storage contract obvious at call sites
 * rather than relying on `Instant.toString()`'s default.
 */
internal fun Instant.toStoredString(): String = this.toString()
