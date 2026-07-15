package kz.maestrosultan.fitjournal.domain.workout

/**
 * Pure record-reordering logic shared by both platforms. There is no shared
 * use-case here on purpose: persisting a reorder also needs the user/journal id
 * source and the [SyncTrigger], both of which are deliberately platform-specific
 * (iOS `UserStore` / `sharedSyncTrigger`, Android `UserManager` /
 * `WorkManagerSyncTrigger`). Only this computation is platform-agnostic, so only
 * this is shared — the thin per-platform use case wraps it with those deps.
 *
 * [WorkoutRecord.position] is an opaque relative sort key, so a consistent
 * 0-based reindex is all the persistence layer needs.
 */
object WorkoutRecordOrdering {

    /** Reindex [records] with 0-based [WorkoutRecord.position] in list order. */
    fun reindexed(records: List<WorkoutRecord>): List<WorkoutRecord> =
        records.mapIndexed { index, record -> record.copy(position = index) }

    /**
     * Reorder [records] to match [orderedIds], then reindex 0-based. Any record
     * whose id is absent from [orderedIds] is kept, appended in its existing
     * relative order (a safety net — callers pass the full id set).
     */
    fun reordered(records: List<WorkoutRecord>, orderedIds: List<String>): List<WorkoutRecord> {
        val byId = records.associateBy { it.id }
        val listed = orderedIds.toSet()
        val ordered = orderedIds.mapNotNull { byId[it] } + records.filterNot { it.id in listed }
        return reindexed(ordered)
    }
}
