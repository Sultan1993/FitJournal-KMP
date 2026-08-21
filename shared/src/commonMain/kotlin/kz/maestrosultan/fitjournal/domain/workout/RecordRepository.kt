package kz.maestrosultan.fitjournal.domain.workout

import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.summary.WeightedSetOccurrence

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
     *
     * [includeLastOccurrence] defaults to true (the logging flow needs the
     * hints). Pass false for read-only consumers that never render "last
     * session" hints — e.g. the import picker — to skip the per-exercise
     * "previous occurrence" SQL fallbacks, which are otherwise pure overhead.
     */
    suspend fun getRecordsByDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        includeLastOccurrence: Boolean = true,
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

    /**
     * Flat weighted-set history for PR detection: every set of catalog
     * [exerciseUuid] in (userId, journalId) that carries a weight, on live
     * records dated up to and INCLUDING [upToDate] — each with its parent
     * record's identity (recordUuid + workoutNumber + date). Weight-less sets
     * (cardio / never-filled) are excluded at the SQL level. Unordered:
     * callers aggregate (max weight, per-record grouping), never render rows
     * directly.
     */
    @Throws(Exception::class)
    suspend fun getWeightedSetHistoryForExercise(
        userId: String,
        journalId: String,
        exerciseUuid: String,
        upToDate: LocalDate,
    ): List<WeightedSetOccurrence>

    // ─── Free-quota reads ──────────────────────────────────────────────

    /**
     * Number of distinct WORKOUTS — (journalId, date, workoutNumber) across ALL
     * of the user's journals — that this user has EVER logged. Tombstoned
     * records count: deleting a workout must not refund quota.
     *
     * All of history, with no start boundary. Only a never-subscriber is ever
     * counted (see [kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate]),
     * and under the hard wall a never-subscriber could not log at all — so there
     * is nothing for a cutoff instant to protect.
     *
     * Default returns 0 so the jvmTest fakes need no edit (same trick
     * [addRecordsToWorkout] uses) and so a fake fails OPEN.
     */
    suspend fun countMeteredWorkouts(userId: String): Int = 0

    /** Reactive [countMeteredWorkouts] — re-emits on every workoutRecords write. */
    fun countMeteredWorkoutsFlow(userId: String): Flow<Int> = flowOf(0)

    /**
     * True when the ([journalId], [date], [workoutNumber]) workout slot already
     * holds any record, live OR tombstoned — i.e. that workout exists, so the
     * quota gate lets writes into it through. Default fails OPEN.
     */
    suspend fun hasAnyRecordInWorkout(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): Boolean = true

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
     * Copies [records] onto [date] as brand-new records (fresh uuids), each keeping
     * its source workoutNumber ("copy as is": a 2-workout day copies back as 2
     * workouts). Each source exercise is recreated with its set count preserved but
     * `weight`/`distance`/reps/duration cleared — a "repeat this session" template.
     * The copied session then shows up as the next read's
     * `WorkoutExercise.lastOccurrence`, so the cleared rows render per-position
     * hints with no extra work.
     */
    suspend fun addRecordsToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        records: List<WorkoutRecord>,
    )

    /**
     * Like [addRecordsToDate] but forces every copy onto [date]'s workout
     * [workoutNumber] (import-into-a-page, regardless of the sources' own numbers).
     * The default delegates to the copy-as-is [addRecordsToDate] so existing fakes
     * need no change; the real repository overrides it to target the page.
     */
    suspend fun addRecordsToWorkout(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        records: List<WorkoutRecord>,
    ) = addRecordsToDate(userId, journalId, date, records)

    /** Copies every record on [date] onto today (see [addRecordsToDate]). */
    suspend fun addRecordsFromDateToToday(
        userId: String,
        journalId: String,
        date: LocalDate,
    )

    /**
     * Copies workout [workoutNumber] of [date] onto TODAY as a brand-new page
     * (today's max workoutNumber + 1), clearing weights/reps to a "do it again"
     * template (see [addRecordsToDate]). Returns the new page's workoutNumber, or
     * null when the source workout has no records. Default no-ops for fakes.
     */
    suspend fun copyWorkoutToTodayAsNewPage(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): Int? = null

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

    /**
     * Deletes an ENTIRE workout ([workoutNumber] on [date]) atomically: every
     * live record on that page gets the same tombstone [deleteRecord] applies
     * (deletedAt + `pendingUpload=1`, so sync pushes the tombstones), AND the
     * workout's session row (local-only, no tombstone) is hard-deleted — both
     * in ONE transaction, so a mid-write failure leaves nothing observably
     * changed: no half-tombstoned workout, no session orphaned against a gone
     * workout (an orphan would corrupt
     * `WorkoutSessionRepository.countCompletedSessionsBetween`'s weekly
     * ordinals).
     *
     * The default composes from [getRecordsByDate] + [deleteRecord] so any
     * fake that doesn't override this needs no change — it is NOT atomic and
     * does NOT touch the session table. The real repository overrides it with
     * the single-transaction implementation.
     */
    suspend fun deleteWorkoutAtomic(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ) {
        getRecordsByDate(userId, journalId, date, includeLastOccurrence = false)
            .filter { it.workoutNumber == workoutNumber }
            .forEach { deleteRecord(userId, journalId, it) }
    }

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

    // ─── Workout notes (per-page free-text, decoupled from sessions) ─────
    //
    // A note is keyed by the workout PAGE (date, workoutNumber), not by a
    // session, so ANY workout can carry one — timed or logged straight into the
    // day. Kept here (not on WorkoutSessionRepository) so the details pipeline
    // reads note + records from one repo, and so the page-empty cleanup can
    // tombstone the note in the same place records are deleted.
    //
    // Defaults are no-ops so test fakes of RecordRepository need no change; the
    // real repository overrides all four.

    /** The live note text for one workout page, or null when there is none. */
    suspend fun getWorkoutNote(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): String? = null

    /** Live notes for a day as `workoutNumber -> text`, reactive (re-emits on edit). */
    fun getWorkoutNotesForDayFlow(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): Flow<Map<Int, String>> = flowOf(emptyMap())

    /** Upsert a page's note; a blank [text] clears it (tombstone). */
    suspend fun setWorkoutNote(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        text: String,
    ) {}

    /** Tombstone a page's note — the page emptied, or is being reused for a new workout. */
    suspend fun clearWorkoutNote(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ) {}
}
