package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.core.FetchPeriod
import kz.maestrosultan.fitjournal.domain.workout.DifficultyType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet

/**
 * Local-first record repository (KMP shared). The single source of truth
 * is the on-device SQLite database — no network calls in this contract.
 *
 * Reads come in suspend + Flow pairs. The suspend variant returns a
 * one-shot snapshot (preferred for use cases that want a fresh read);
 * the Flow variant currently emits the same snapshot once. The `Flow`
 * surface is in place so call sites that want reactive reads have the
 * shape they expect — bodies will swap to reactive SQLDelight queries
 * once `WorkoutsDBDataSource` exposes a Flow over the joined tree
 * (record + exercises + sets), with no caller change required.
 *
 * Writes return `Unit` and bump `pendingUpload=1` on the parent
 * `workoutRecord` so the SyncWorker re-encodes the JSON tree on next
 * push. Children of a record (workoutExercise, workoutSet) don't carry
 * their own `pendingUpload` — every child mutation bubbles up via the
 * parent's `replaceWorkoutRecord` call.
 */
interface RecordRepository {

    // ─── Reads ─────────────────────────────────────────────────────────

    suspend fun getAllRecords(userId: String, diaryId: String): List<WorkoutRecord>
    fun getAllRecordsFlow(userId: String, diaryId: String): Flow<List<WorkoutRecord>>

    suspend fun getRecordsByDate(
        userId: String,
        diaryId: String,
        date: LocalDate,
    ): List<WorkoutRecord>

    fun getRecordsByDateFlow(
        userId: String,
        diaryId: String,
        date: LocalDate,
    ): Flow<List<WorkoutRecord>>

    suspend fun getRecordsByMonth(
        userId: String,
        diaryId: String,
        month: String,
        year: String,
    ): List<WorkoutRecord>

    fun getRecordsByMonthFlow(
        userId: String,
        diaryId: String,
        month: String,
        year: String,
    ): Flow<List<WorkoutRecord>>

    suspend fun getRecordsByBatch(
        userId: String,
        diaryId: String,
        batchSize: Int,
        forceLoad: Boolean,
    ): Pair<List<WorkoutRecord>, Boolean>

    suspend fun getRecordsByPeriod(
        userId: String,
        diaryId: String,
        period: FetchPeriod,
        forceLoad: Boolean,
    ): List<WorkoutRecord>

    /**
     * Narrow read for stats / best-result panels. Returns just the
     * `WorkoutSet`s in (userId, diaryId) belonging to [exerciseId]
     * (catalog uuid). Avoids loading and filtering the entire diary tree.
     */
    suspend fun getSetsForExercise(
        userId: String,
        diaryId: String,
        exerciseId: String,
    ): List<WorkoutSet>

    // ─── Writes ────────────────────────────────────────────────────────

    suspend fun saveWorkoutExerciseComment(
        userId: String,
        diaryId: String,
        workoutExerciseId: String,
        comment: String?,
    )

    suspend fun refreshRecordPositions(
        userId: String,
        diaryId: String,
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
        diaryId: String,
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
        diaryId: String,
        record: WorkoutRecord,
        exercise: WorkoutExercise,
    ): List<WorkoutRecord>

    suspend fun deleteRecord(userId: String, diaryId: String, record: WorkoutRecord)

    suspend fun deleteRecordsForDate(userId: String, diaryId: String, date: LocalDate)

    suspend fun deleteUserRecords(userId: String)

    suspend fun addSet(
        userId: String,
        diaryId: String,
        workoutExerciseId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
        difficultyType: DifficultyType,
    )

    suspend fun updateSet(
        userId: String,
        diaryId: String,
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
        diaryId: String,
        workoutExerciseId: String,
        setId: String,
    )
}
