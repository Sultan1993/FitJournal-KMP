package kz.maestrosultan.fitjournal.sync

/**
 * Why a sync tick was requested. The closed set lives here so call sites
 * can't invent freshly-spelled reasons that break telemetry. Logged via
 * [tag] on `[FJ_SYNC]` lines for traceability. Add new variants here, not
 * at call sites.
 *
 * SKIE bridges the sealed hierarchy to Swift; iOS call sites use the
 * generated Swift mirror (e.g. `SyncReason.ColdStart()` /
 * `SyncReason.PostWrite.Notes()`).
 */
sealed class SyncReason(val tag: String) {

    object ColdStart : SyncReason("cold_start")
    object Foreground : SyncReason("foreground")
    object Periodic : SyncReason("periodic")
    object PostMigration : SyncReason("post_migration")

    /**
     * A repository write just landed; SyncOrchestrator should drain
     * `pendingUpload=1` rows for this table promptly. The granularity is
     * per-table so logs identify which write caused the tick.
     */
    sealed class PostWrite(suffix: String) : SyncReason("post_write_$suffix") {
        object Notes : PostWrite("notes")
        object Diary : PostWrite("diary")
        object Exercise : PostWrite("exercise")
        object BodyMeasurement : PostWrite("body_measurement")
        object PhotoMeasurement : PostWrite("photo_measurement")
        object WorkoutRecord : PostWrite("workout_record")
        object User : PostWrite("user")
    }

    override fun toString(): String = tag
}
