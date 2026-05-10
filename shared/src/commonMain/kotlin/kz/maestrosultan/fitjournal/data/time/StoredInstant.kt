package kz.maestrosultan.fitjournal.data.time

import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Parse a stored `Instant` from SQLite TEXT (ISO 8601 with UTC zone).
 * Handles pre-FJ-2.0 zone-less format by anchoring to UTC without migration.
 */
internal fun parseStoredInstant(text: String): Instant =
    runCatching { Instant.parse(text) }
        .getOrElse { LocalDateTime.parse(text).toInstant(TimeZone.UTC) }

/** Format `Instant` for SQLite as ISO 8601 with explicit zone. */
internal fun Instant.toStoredString(): String = this.toString()
