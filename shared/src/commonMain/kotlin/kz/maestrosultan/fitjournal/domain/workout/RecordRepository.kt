package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/**
 * Local-first record repository (KMP shared). The single source of truth
 * is the on-device SQLite database — no network calls in this contract.
 *
 * Writes return `Unit` and bump `pendingUpload=1` on the parent
 * `workoutRecord` so the SyncWorker re-encodes the JSON tree on next
 * push. Children of a record (workoutExercise, workoutSet) don't carry
 * their own `pendingUpload` — every child mutation bubbles up via the
 * parent's `replaceWorkoutRecord` call.
 */
interface RecordRepository {

    // ─── Reads ─────────────────────────────────────────────────────────

    /**
     * Reactive "workouts changed" signal for a journal. Emits once on
     * collection and re-emits on every record add / delete / set-edit
     * (backed by MAX(updatedDate), so it catches in-place edits, not just
     * count changes). The emitted string is opaque — consumers use it
     * purely as a trigger to re-read via the one-shot getters. Drives the
     * home screen's live refresh without a manual reload.
     */
    fun observeRecordsChanged(userId: String, journalId: String): Flow<String>

    /**
     * Every record in the journal, unbounded. `WorkoutExercise.lastOccurrence`
     * is left null — see [getRecordsByDate] if you need hints.
     */
    suspend fun getAllRecords(userId: String, journalId: String): List<WorkoutRecord>

    /**
     * The one read that populates `WorkoutExercise.lastOccurrence` (with its
     * Flow variant) — it backs the logging flow, which is the only consumer of
     * "what did I do last time".
     */

    suspend fun getRecordsByDate(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): List<WorkoutRecord>

    /**
     * Calendar-month window, for the calendar's has-workout dots.
     * `WorkoutExercise.lastOccurrence` is left null.
     */
    suspend fun getRecordsByMonth(
        userId: String,
        journalId: String,
        month: String,
        year: String,
    ): List<WorkoutRecord>

    /**
     * Most recent workouts (capped at last 3 years). Drives the linear
     * workout-history list. Older sessions remain reachable via calendar
     * pickers but don't render here.
     *
     * `WorkoutExercise.lastOccurrence` on the returned records is intentionally
     * left null — the workout-history list cells don't render hints, and
     * computing it would add a bounded but real cost (in-memory sort + ~30 SQL
     * fallback calls for boundary cases). Consumers that need "what did I do
     * last time" must use [getRecordsByDate] / its Flow variant, which
     * populates it.
     */
    suspend fun getRecentRecords(
        userId: String,
        journalId: String,
    ): List<WorkoutRecord>

    /**
     * Narrow read for stats / best-result panels. Returns just the
     * `WorkoutSet`s in (userId, journalId) belonging to [exerciseId]
     * (catalog uuid). Avoids loading and filtering the entire journal tree.
     */
    @Throws(Exception::class)
    suspend fun getSetsForExercise(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutSet>

    /**
     * Narrow read for the exercise details "History" / "Stats" tabs:
     * every `WorkoutExercise` in (userId, journalId) that points at the
     * catalog [exerciseId], each with its child sets and parent record
     * date. Replaces the older "load 1-year tree, filter in caller"
     * shape, which materialized the entire journal even though the screen
     * cares about a single catalog exercise.
     *
     * Sorted newest → oldest by record date, then by record position,
     * then by we.position. `WorkoutExercise.lastOccurrence` is left null —
     * this read IS the history, so a per-exercise "last time" would be
     * circular.
     */
    @Throws(Exception::class)
    suspend fun getExerciseOccurrences(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutExercise>

    // ─── Writes ────────────────────────────────────────────────────────

    /**
     * Creates one new record per catalog exercise on [date] under
     * [workoutNumber] (which workout of the day the caller is viewing —
     * 1 for the first/only one), each holding that exercise with no sets. New
     * records are appended after any existing records already in that workout
     * (position is page-relative). Parents get `pendingUpload=1`. This is the
     * offline-first "add exercises from the catalog" write.
     */
    suspend fun addExercisesToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        exerciseIds: List<String>,
    )

    /**
     * Copies [records] onto [date] as brand-new records (fresh uuids).
     * Each source exercise is recreated with its set count preserved but
     * `weight`/`distance`/reps/duration cleared — a "repeat this
     * session" template. The copied session then shows up as the next read's
     * `WorkoutExercise.lastOccurrence`, so the cleared rows render per-position
     * hints with no extra work.
     */
    suspend fun addRecordsToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        records: List<WorkoutRecord>,
    )

    /** Copies every record on [date] onto today (see [addRecordsToDate]). */
    suspend fun addRecordsFromDateToToday(
        userId: String,
        journalId: String,
        date: LocalDate,
    )

    /**
     * Replaces the single workoutExercise [targetWorkoutExerciseId] inside
     * [recordId] with a fresh one pointing at catalog [newExerciseId], dropping
     * that member's sets and keeping its slot. Any OTHER members of a superset
     * record are left untouched. No-op if the target member is absent (e.g. a
     * concurrent edit already changed it). Bumps `pendingUpload=1`.
     */
    suspend fun replaceExerciseInRecord(
        userId: String,
        journalId: String,
        recordId: String,
        targetWorkoutExerciseId: String,
        newExerciseId: String,
    )

    suspend fun saveWorkoutExerciseComment(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        comment: String?,
    )

    suspend fun refreshRecordPositions(
        userId: String,
        journalId: String,
        records: List<WorkoutRecord>,
    )

    /**
     * Combines [secondRecord]'s exercises into [firstRecord] and tombstones
     * [secondRecord], atomically. Returns the freshly-mapped trees for the
     * calendar day [firstRecord] lives on so the caller can update the UI in
     * one shot. Empty list when either record is missing.
     */
    @Throws(Exception::class)
    suspend fun mergeRecords(
        userId: String,
        journalId: String,
        firstRecord: WorkoutRecord,
        secondRecord: WorkoutRecord,
    ): List<WorkoutRecord>

    /**
     * "Remove from superset": splits [exercise] out of [record] into its own
     * new record right after it (the inverse of [mergeRecords]), atomically.
     * Removing the only exercise of a single-exercise record is a no-op —
     * deleting a whole record is [deleteRecord]'s job. Returns the freshly-
     * mapped trees for the calendar day [record] lives on.
     */
    @Throws(Exception::class)
    suspend fun removeExerciseFromRecord(
        userId: String,
        journalId: String,
        record: WorkoutRecord,
        exercise: WorkoutExercise,
    ): List<WorkoutRecord>

    suspend fun deleteRecord(userId: String, journalId: String, record: WorkoutRecord)

    suspend fun deleteRecordsForDate(userId: String, journalId: String, date: LocalDate)

    suspend fun deleteUserRecords(userId: String)

    suspend fun addSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
    )

    /** @return true if the target set existed and was updated; false if it
     *  was already gone (no write is performed in that case). */
    @Throws(Exception::class)
    suspend fun updateSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
    ): Boolean

    /** @return true if the target set existed and was deleted; false if it
     *  was already gone (no write is performed in that case). */
    @Throws(Exception::class)
    suspend fun deleteSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
    ): Boolean
}
