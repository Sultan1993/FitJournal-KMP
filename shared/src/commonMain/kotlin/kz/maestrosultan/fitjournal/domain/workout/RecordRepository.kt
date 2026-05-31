package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.DifficultyType
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

    suspend fun getAllRecords(userId: String, journalId: String): List<WorkoutRecord>

    suspend fun getRecordsByDate(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): List<WorkoutRecord>

    suspend fun getRecordsByMonth(
        userId: String,
        journalId: String,
        month: String,
        year: String,
    ): List<WorkoutRecord>

    /**
     * Most recent workouts (capped at last 1 year). Drives the linear
     * workout-history list. Older sessions remain reachable via calendar
     * pickers but don't render here.
     *
     * `WorkoutSet.previousWeight` / `previousDistance` /
     * `previousDifficultyType` on the returned records are intentionally
     * left null — the workout-history list cells don't render those
     * hints, and computing them would add a bounded but real cost
     * (in-memory sort + ~30 SQL fallback calls for boundary cases).
     * Detail-screen consumers must use [getRecordsByDate] / its Flow
     * variant, which populates them.
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
     * then by we.position. `WorkoutSet.previous*` fields are left null
     * — the history / stats cells don't render them.
     */
    suspend fun getExerciseOccurrences(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutExercise>

    // ─── Writes ────────────────────────────────────────────────────────

    /**
     * Creates one new record per catalog exercise on [date], each holding
     * that exercise with no sets. New records are appended after any
     * existing records for the date. Parents get `pendingUpload=1`.
     * This is the offline-first "add exercises from the catalog" write.
     */
    suspend fun addExercisesToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        exerciseIds: List<String>,
    )

    /**
     * Copies [records] onto [date] as brand-new records (fresh uuids).
     * Each source exercise is recreated with its set count preserved but
     * `weight`/`distance` cleared and difficulty reset — a "repeat this
     * session" template. Previous-set hints surface automatically on read.
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
     * Replaces the contents of [recordId] with a single new workoutExercise
     * pointing at catalog [newExerciseId], dropping the record's previous
     * exercises and sets. Bumps `pendingUpload=1`.
     */
    suspend fun replaceExerciseInRecord(
        userId: String,
        journalId: String,
        recordId: String,
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
     * [secondRecord]. Returns the freshly-mapped trees for the calendar
     * day [firstRecord] lives on so the caller can update the UI in one
     * shot. Empty list when either record is missing.
     */
    suspend fun mergeRecords(
        userId: String,
        journalId: String,
        firstRecord: WorkoutRecord,
        secondRecord: WorkoutRecord,
    ): List<WorkoutRecord>

    /**
     * Removes [exercise] from [record]. If it was the last exercise the
     * record itself is tombstoned. Returns the freshly-mapped trees for
     * the calendar day [record] lives on.
     */
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
        difficultyType: DifficultyType,
    )

    suspend fun updateSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
        difficultyType: DifficultyType,
    )

    suspend fun deleteSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
    )
}
