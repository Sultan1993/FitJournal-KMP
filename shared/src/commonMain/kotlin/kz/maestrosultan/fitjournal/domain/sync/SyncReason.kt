package kz.maestrosultan.fitjournal.domain.sync

/**
 * Why a sync tick was requested. The closed set lives here so call sites
 * can't invent freshly-spelled reasons that break telemetry. Logged via
 * [tag] on `[FJ_SYNC]` lines for traceability. Add new variants here, not
 * at call sites.
 *
 * SKIE bridges the sealed hierarchy to Swift as nested types
 * (`SyncReason.ColdStart.shared`, `SyncReason.PostWrite.Notes.shared`).
 * iOS uses dot-syntax facades (`.coldStart`, `.postWriteNotes`) that
 * live in `iOS/FitJournal/Sync/Domain/SyncReason+Convenience.swift` —
 * **add a matching `static var` line there for every new variant**,
 * otherwise iOS call sites won't compile.
 */
sealed class SyncReason(val tag: String) {

    object ColdStart : SyncReason("cold_start")
    object Foreground : SyncReason("foreground")
    object Periodic : SyncReason("periodic")
    object PostMigration : SyncReason("post_migration")

    /**
     * The user explicitly asked for fresh data (pull-to-refresh on a
     * list screen). Distinct from `Foreground` (auto on app re-entry)
     * and `PostWrite.*` (auto after a write) — logged as such.
     */
    object UserRefresh : SyncReason("user_refresh")

    /**
     * A repository write just landed; SyncOrchestrator should drain
     * `pendingUpload=1` rows for this table promptly. The granularity is
     * per-table so logs identify which write caused the tick.
     */
    sealed class PostWrite(suffix: String) : SyncReason("post_write_$suffix") {
        object Notes : PostWrite("notes")
        object Journal : PostWrite("journal")
        object Exercise : PostWrite("exercise")
        object BodyMeasurement : PostWrite("body_measurement")
        object PhotoMeasurement : PostWrite("photo_measurement")
        object WorkoutRecord : PostWrite("workout_record")
        object WorkoutSession : PostWrite("workout_session")
        object User : PostWrite("user")
    }

    override fun toString(): String = tag
}
