package kz.maestrosultan.fitjournal.data.record.repository

import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.LastOccurrence
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.summary.WeightedSetOccurrence
import kz.maestrosultan.fitjournal.data.db.FitJournalDatabase
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.domain.identifier.randomUuid
import kz.maestrosultan.fitjournal.data.exercise.entity.DBExerciseObject
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.entity.DBLastOccurrence
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutExerciseObject
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutExerciseWithSets
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutRecord
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutRecordRow
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutSetObject
import kz.maestrosultan.fitjournal.data.time.toStoredString

/**
 * Local-first RecordRepository (KMP shared). KMP SQLite is the only source
 * of truth; no network calls.
 *
 * Reads come from `WorkoutsDBDataSource` — the joined tree (record +
 * exercises + sets), filtered by `deletedAt IS NULL` at the SQL level. Each
 * read pre-fetches the user's exercise catalog ([exerciseLookupForRead])
 * once and resolves `WorkoutExercise.exercise` via an in-memory map,
 * avoiding per-row catalog SELECTs.
 *
 * `WorkoutExercise.lastOccurrence` is derived per read, never stored: for
 * each catalog exercise, the most recent prior occurrence resolves in-memory
 * from the already-loaded trees, with "oldest in window" cases falling
 * through to one batched query per distinct record date. It carries the
 * prior occurrence's date and full set list; aligning a current set against
 * it is `LastOccurrence.setAt(position)`, shared by both platforms.
 * `getRecentRecords` skips the computation entirely (`includeLastOccurrence
 * = false`) since the history feed doesn't render hints.
 *
 * Writes update SQLite atomically via `replaceWorkoutRecord` and bump
 * `pendingUpload=1` on the parent — children don't carry their own flag;
 * mutations bubble up so the SyncWorker re-encodes the full JSON tree.
 *
 * [database] backs ONLY [deleteWorkoutAtomic]; every other method goes
 * through [workoutsDB]/[exercisesDB]. It's nullable so the legacy 3-arg
 * constructor below can pass `null` — [deleteWorkoutAtomic] throws without
 * it. [afterRecordTombstones] is an `internal` mid-transaction test seam for
 * jvmTest to force a rollback (see [deleteWorkoutAtomic]) — always `null`
 * outside tests.
 */
class DefaultRecordRepository(
    private val workoutsDB: WorkoutsDBDataSource,
    private val exercisesDB: ExercisesDBDataSource,
    private val mapper: (DBExerciseObject) -> Exercise,
    private val database: FitJournalDatabase?,
    internal val afterRecordTombstones: (() -> Unit)? = null,
) : RecordRepository {

    /**
     * Swift-facing entry point (Kotlin/Native doesn't reliably bridge default
     * parameter values to Swift), mirroring
     * `DefaultWorkoutSessionRepository`'s secondary-constructor pattern.
     * [database] has no default on the primary constructor deliberately, so
     * this 3-arg overload's arity never overlaps the primary's (4-5).
     * [deleteWorkoutAtomic] throws through this constructor until the caller
     * migrates to the primary one with a real [database].
     */
    constructor(
        workoutsDB: WorkoutsDBDataSource,
        exercisesDB: ExercisesDBDataSource,
        mapper: (DBExerciseObject) -> Exercise,
    ) : this(workoutsDB, exercisesDB, mapper, database = null)

    // ─── Reads ─────────────────────────────────────────────────────────

    override fun observeRecordsChanged(userId: String, journalId: String): Flow<String> =
        workoutsDB.observeJournalRecordsSignal(userId, journalId)

    override suspend fun getAllRecords(
        userId: String,
        journalId: String,
    ): List<WorkoutRecord> {
        val exerciseLookup = exerciseLookupForRead(userId)
        val trees = workoutsDB.getWorkoutRecordsByJournal(userId, journalId)
        // Only unbounded read in the app; consumers (workload aggregate,
        // comment editor) don't render hints, so skip the whole computation.
        return toDomainList(trees, exerciseLookup, includeLastOccurrence = false)
    }

    override suspend fun getRecordsByDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        includeLastOccurrence: Boolean,
    ): List<WorkoutRecord> {
        val exerciseLookup = exerciseLookupForRead(userId)
        // Half-open one-day window: [date, date+1). LocalDate.toString()
        // renders ISO yyyy-MM-dd, which is what the SQL filter compares
        // lexicographically.
        val from = date.toString()
        val to = date.plusDaysSafe(1).toString()
        val trees = workoutsDB.getWorkoutRecordsByJournal(userId, journalId, from, to)
        return toDomainList(trees, exerciseLookup, includeLastOccurrence = includeLastOccurrence)
    }

    override suspend fun getRecordsByMonth(
        userId: String,
        journalId: String,
        month: String,
        year: String,
    ): List<WorkoutRecord> {
        val exerciseLookup = exerciseLookupForRead(userId)
        // Inclusive range using exclusive next-month upper bound. For
        // December, wraps to next year-01-01. SQLite's BETWEEN on
        // ISO-8601 yyyy-MM-dd is lex-correct.
        val mm = month.padStart(2, '0')
        val from = "$year-$mm-01"
        val nextMonthInt = (month.toIntOrNull() ?: 1) + 1
        val yearInt = year.toIntOrNull() ?: 0
        val to = if (nextMonthInt > 12) {
            "${yearInt + 1}-01-01"
        } else {
            val nm = nextMonthInt.toString().padStart(2, '0')
            "$year-$nm-01"
        }
        val trees = workoutsDB.getWorkoutRecordsByJournal(userId, journalId, from, to)
        // Calendar dots only — no hints rendered, so skip the computation.
        return toDomainList(trees, exerciseLookup, includeLastOccurrence = false)
    }

    override suspend fun getRecentRecords(
        userId: String,
        journalId: String,
    ): List<WorkoutRecord> {
        val exerciseLookup = exerciseLookupForRead(userId)
        // Caps at 3 years — older sessions stay reachable via the uncapped
        // calendar reads. The partial index on (userId, journalId, date DESC)
        // WHERE deletedAt IS NULL loads the window in one shot, and
        // toDomainList maps it off the UI thread, so this never blocks the UI.
        val today = todayInSystemTz()
        val windowStart = today.minusYearsSafe(3).toString()
        val to = "9999-12-31"
        val trees = workoutsDB.getWorkoutRecordsByJournal(userId, journalId, windowStart, to)
        // History list cells don't render hints — skip the computation
        // entirely. Saves ~5ms on a 3-year window for a heavy user.
        return toDomainList(trees, exerciseLookup, includeLastOccurrence = false)
    }

    override suspend fun getExerciseOccurrences(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutExercise> {
        // Null if the catalog row was soft-deleted — nothing legitimate to render.
        val dbExercise = exercisesDB.getExerciseByUuid(exerciseId)
            ?: return emptyList()
        val domainExercise = mapper(dbExercise)
        // Every set for this exercise in the 3-year window, recordDate +
        // comment surfaced via the mapper; group by workoutExerciseUuid to
        // rebuild the per-occurrence shape History/Stats expect.
        val rows = exerciseDetailsWindow().let { (from, to) ->
            workoutsDB.getSetsForExerciseInJournal(
                exerciseUuid = exerciseId,
                userId = userId,
                journalId = journalId,
                from = from,
                to = to,
            ) { set, recordDate, weComment -> OccurrenceRow(set, recordDate, weComment) }
        }
        if (rows.isEmpty()) return emptyList()
        return rows
            .groupBy { it.set.workoutExerciseUuid }
            .mapNotNull { (weUuid, group) ->
                val first = group.first()
                // A malformed stored date (e.g. a legacy "…Z" value) must not
                // throw and sink the whole list — skip just that occurrence.
                val recordDate = runCatching { LocalDate.parse(first.recordDate) }.getOrNull()
                    ?: return@mapNotNull null
                WorkoutExercise(
                    id = weUuid,
                    userId = userId,
                    journalId = journalId,
                    date = recordDate,
                    exercise = domainExercise,
                    sets = group.map { row ->
                        mapSet(
                            set = row.set,
                            userId = userId,
                            journalId = journalId,
                            recordDate = recordDate,
                            resultType = domainExercise.resultType,
                        )
                    },
                    comment = first.workoutExerciseComment,
                )
            }
    }

    override suspend fun getSetsForExercise(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutSet> {
        val today = todayInSystemTz()
        val (from, to) = exerciseDetailsWindow()
        // Same SQL as `getExerciseOccurrences`; Info-tab wants a flat list of
        // sets in the window, so workoutExercise.comment is dropped.
        return workoutsDB.getSetsForExerciseInJournal(
            exerciseUuid = exerciseId,
            userId = userId,
            journalId = journalId,
            from = from,
            to = to,
        ) { set, recordDate, _ ->
            WorkoutSet(
                id = set.uuid,
                userId = userId,
                journalId = journalId,
                date = runCatching { LocalDate.parse(recordDate) }.getOrDefault(today),
                weight = set.weight,
                reps = set.reps,
                distance = set.distance,
                duration = set.duration,
                resultType = ResultType.WEIGHT_REPS,
            )
        }
    }

    override suspend fun getWeightedSetHistoryForExercise(
        userId: String,
        journalId: String,
        exerciseUuid: String,
        upToDate: LocalDate,
    ): List<WeightedSetOccurrence> = workoutsDB
        .getWeightedSetHistoryForExercise(
            userId = userId,
            journalId = journalId,
            exerciseUuid = exerciseUuid,
            upToDate = upToDate.toString(),
        ) { recordUuid, workoutNumber, recordDate, weight, reps ->
            WeightedHistoryRow(recordUuid, workoutNumber, recordDate, weight, reps)
        }
        .mapNotNull { row ->
            // SQL already filters `weight IS NOT NULL` — this is the type-level
            // seam into the non-null domain field, not a second filter. A
            // malformed stored date skips just that row (mirrors getExerciseOccurrences).
            val weight = row.weight ?: return@mapNotNull null
            val date = runCatching { LocalDate.parse(row.recordDate) }.getOrNull()
                ?: return@mapNotNull null
            WeightedSetOccurrence(
                recordUuid = row.recordUuid,
                workoutNumber = row.workoutNumber,
                date = date,
                weightKg = weight,
                reps = row.reps,
            )
        }

    /**
     * 3-year window (`[threeYearsAgo, far-future)`), matching `getRecentRecords`
     * so the exercise details page never surfaces older history than the rest of the app.
     */
    private fun exerciseDetailsWindow(): Pair<String, String> {
        val from = todayInSystemTz().minusYearsSafe(3).toString()
        val to = "9999-12-31"
        return from to to
    }

    /**
     * One SQL call to the exercise catalog (categories pre-joined), shaped
     * into a `[uuid: Exercise]` lookup so toDomain resolves
     * `WorkoutExercise.exercise` without per-row SELECTs. User-scoped so
     * customs from a previous account don't leak in after a switch.
     */
    private suspend fun exerciseLookupForRead(userId: String): Map<String, Exercise> {
        val dbExercises = exercisesDB.getAllExercisesWithCategoriesBatch(userId)
        val lookup = HashMap<String, Exercise>(dbExercises.size)
        for (db in dbExercises) {
            lookup[db.uuid] = mapper(db)
        }
        return lookup
    }

    // ─── Writes ────────────────────────────────────────────────────────

    override suspend fun addExercisesToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        exerciseIds: List<String>,
    ) {
        if (exerciseIds.isEmpty()) return
        val lastPosition = lastRecordPositionForDate(userId, journalId, date, workoutNumber)
        val now = Clock.System.now()
        val dateStr = date.toString()
        // Clear-on-begin: wipe any meta a prior (deleted) occupant stranded at this
        // page so a reused (date, workoutNumber) never inherits a stale note/duration.
        // No-op when appending to an existing workout (page still has live records).
        clearStalePageMetaForNewWorkouts(userId, journalId, date, setOf(workoutNumber))
        val trees = exerciseIds.mapIndexed { index, exerciseId ->
            val recordUuid = randomUuid()
            DBWorkoutRecord(
                row = newRecordRow(recordUuid, userId, journalId, dateStr, lastPosition + index + 1, workoutNumber, now),
                exercises = listOf(
                    DBWorkoutExerciseWithSets(
                        exercise = DBWorkoutExerciseObject(
                            uuid = randomUuid(),
                            workoutRecordUuid = recordUuid,
                            exerciseUuid = exerciseId,
                            position = 0,
                            comment = null,
                        ),
                        sets = emptyList(),
                    ),
                ),
            )
        }
        workoutsDB.createWorkoutRecordsIfMissing(trees)
    }

    override suspend fun addRecordsToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        records: List<WorkoutRecord>,
    ) {
        insertCopiedRecords(userId, journalId, date, records, targetWorkoutNumber = null)
    }

    override suspend fun addRecordsToWorkout(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        records: List<WorkoutRecord>,
    ) {
        insertCopiedRecords(userId, journalId, date, records, targetWorkoutNumber = workoutNumber)
    }

    override suspend fun addRecordsFromDateToToday(
        userId: String,
        journalId: String,
        date: LocalDate,
    ) {
        val source = getRecordsByDate(userId, journalId, date)
        // Repeat-workout copies the whole day "as is": keep each source's page.
        insertCopiedRecords(userId, journalId, todayInSystemTz(), source, targetWorkoutNumber = null)
    }

    override suspend fun copyWorkoutToTodayAsNewPage(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): Int? {
        val source = getRecordsByDate(userId, journalId, date, includeLastOccurrence = false)
            .filter { it.workoutNumber == workoutNumber }
        if (source.isEmpty()) return null
        val today = todayInSystemTz()
        // A brand-new page: one past today's highest workout number (gap-safe —
        // numbers are distinct ordinals, never renumbered).
        val newPage = (getRecordsByDate(userId, journalId, today, includeLastOccurrence = false)
            .maxOfOrNull { it.workoutNumber } ?: 0) + 1
        insertCopiedRecords(userId, journalId, today, source, targetWorkoutNumber = newPage)
        return newPage
    }

    override suspend fun replaceExerciseInRecord(
        userId: String,
        journalId: String,
        recordId: String,
        targetWorkoutExerciseId: String,
        newExerciseId: String,
    ) {
        val tree = workoutsDB.getWorkoutRecordById(recordId) ?: return
        // Replace exactly the targeted member, in place, keeping its slot and
        // every other member of a superset. No-op if it's already gone.
        val targetIndex = tree.exercises.indexOfFirst { it.exercise.uuid == targetWorkoutExerciseId }
        if (targetIndex < 0) return
        val replacement = DBWorkoutExerciseWithSets(
            exercise = DBWorkoutExerciseObject(
                uuid = randomUuid(),
                workoutRecordUuid = tree.row.uuid,
                exerciseUuid = newExerciseId,
                position = tree.exercises[targetIndex].exercise.position,
                comment = null,
            ),
            sets = emptyList(),
        )
        val exercises = tree.exercises.toMutableList().also { it[targetIndex] = replacement }
        workoutsDB.replaceWorkoutRecord(
            tree.copy(
                row = tree.row.copy(updatedDate = Clock.System.now()),
                exercises = exercises,
            ),
        )
    }

    /**
     * Recreates [sources] as fresh records on [date], preserving the exercise
     * list, their comments and each exercise's SET COUNT — but no values.
     *
     * Reps/duration are cleared along with weight/distance, deliberately: they
     * used to carry over, which made a copied row a hybrid — reps from the
     * copied date, ghost weight from `lastOccurrence` (a different, more
     * recent day). Importing 24 July after training on 27 July rendered
     * "22 kg × 12", a set that never happened. An empty row instead shows the
     * last real session's pair, whole.
     */
    private suspend fun insertCopiedRecords(
        userId: String,
        journalId: String,
        date: LocalDate,
        sources: List<WorkoutRecord>,
        targetWorkoutNumber: Int?,
    ) {
        if (sources.isEmpty()) return
        val now = Clock.System.now()
        val dateStr = date.toString()
        // Clear-on-begin for import / repeat / copy: same guarantee as the direct-add
        // path — a reused empty page must not inherit a stranded note/finished session.
        clearStalePageMetaForNewWorkouts(
            userId, journalId, date,
            sources.map { targetWorkoutNumber ?: it.workoutNumber }.toSet(),
        )
        // [targetWorkoutNumber] set → every copy lands on that page; null
        // (Repeat-workout) → each keeps its source's workoutNumber. Position is
        // page-relative: seed each workout's counter from existing records, then increment.
        val from = dateStr
        val to = date.plusDaysSafe(1).toString()
        val nextPosition = mutableMapOf<Int, Int>()
        workoutsDB.getWorkoutRecordsByJournal(userId, journalId, from, to).forEach { row ->
            val wn = row.row.workoutNumber
            nextPosition[wn] = maxOf(nextPosition[wn] ?: -1, row.row.position)
        }
        val trees = sources.map { source ->
            val workoutNumber = targetWorkoutNumber ?: source.workoutNumber
            val recordUuid = randomUuid()
            val position = (nextPosition[workoutNumber] ?: -1) + 1
            nextPosition[workoutNumber] = position
            DBWorkoutRecord(
                row = newRecordRow(recordUuid, userId, journalId, dateStr, position, workoutNumber, now),
                exercises = source.exercises.mapIndexed { exIndex, srcEx ->
                    val weUuid = randomUuid()
                    DBWorkoutExerciseWithSets(
                        exercise = DBWorkoutExerciseObject(
                            uuid = weUuid,
                            workoutRecordUuid = recordUuid,
                            exerciseUuid = srcEx.exercise.uuid,
                            position = exIndex,
                            comment = srcEx.comment,
                        ),
                        sets = srcEx.sets.mapIndexed { setIndex, srcSet ->
                            DBWorkoutSetObject(
                                uuid = randomUuid(),
                                workoutExerciseUuid = weUuid,
                                position = setIndex,
                                weight = null,
                                reps = null,
                                distance = null,
                                duration = null,
                                completed = false,
                            )
                        },
                    )
                },
            )
        }
        workoutsDB.createWorkoutRecordsIfMissing(trees)
    }

    /**
     * Highest `position` among live records on [date] for [workoutNumber]
     * (position is page-relative). -1 when empty, so the first record lands at 0.
     */
    private suspend fun lastRecordPositionForDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): Int {
        val from = date.toString()
        val to = date.plusDaysSafe(1).toString()
        return workoutsDB.getWorkoutRecordsByJournal(userId, journalId, from, to)
            .filter { it.row.workoutNumber == workoutNumber }
            .maxOfOrNull { it.row.position } ?: -1
    }

    private fun newRecordRow(
        uuid: String,
        userId: String,
        journalId: String,
        date: String,
        position: Int,
        workoutNumber: Int,
        now: Instant,
    ): DBWorkoutRecordRow = DBWorkoutRecordRow(
        uuid = uuid,
        remoteId = null,
        userId = userId,
        journalId = journalId,
        date = date,
        position = position,
        comment = null,
        startedAt = null,
        durationSec = null,
        deletedAt = null,
        pendingUpload = true,
        schemaVersion = 1,
        createdDate = now,
        updatedDate = now,
        workoutNumber = workoutNumber,
    )

    override suspend fun saveWorkoutExerciseComment(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        comment: String?,
    ) {
        val tree = findTreeContainingExercise(userId, journalId, workoutExerciseId) ?: return
        val updatedExercises = tree.exercises.map { exWithSets ->
            if (exWithSets.exercise.uuid == workoutExerciseId) {
                exWithSets.copy(exercise = exWithSets.exercise.copy(comment = comment))
            } else {
                exWithSets
            }
        }
        workoutsDB.replaceWorkoutRecord(
            tree.copy(
                row = tree.row.copy(updatedDate = Clock.System.now()),
                exercises = updatedExercises,
            ),
        )
    }

    override suspend fun refreshRecordPositions(
        userId: String,
        journalId: String,
        records: List<WorkoutRecord>,
    ) {
        // For each record, if its position changed, replace the tree
        // with the updated position. Marks parent pendingUpload=1.
        for (rec in records) {
            val tree = workoutsDB.getWorkoutRecordById(rec.id) ?: continue
            if (tree.row.position == rec.position) continue
            workoutsDB.replaceWorkoutRecord(
                tree.copy(
                    row = tree.row.copy(
                        position = rec.position,
                        updatedDate = Clock.System.now(),
                    ),
                ),
            )
        }
    }

    override suspend fun mergeRecords(
        userId: String,
        journalId: String,
        firstRecord: WorkoutRecord,
        secondRecord: WorkoutRecord,
    ): List<WorkoutRecord> {
        val firstTree = workoutsDB.getWorkoutRecordById(firstRecord.id) ?: return emptyList()
        val secondTree = workoutsDB.getWorkoutRecordById(secondRecord.id) ?: return emptyList()

        val basePosition = firstTree.exercises.size
        val mergedTail = secondTree.exercises.mapIndexed { idx, exWithSets ->
            // Re-parent: new uuid + updated workoutRecordUuid + bumped position.
            // Sets need fresh uuids too — softDeleteWorkoutRecord only
            // tombstones the source record row, so its workoutSets rows
            // physically remain; reusing their uuids collides on the PK.
            val newExUuid = randomUuid()
            val newSets = exWithSets.sets.map {
                it.copy(uuid = randomUuid(), workoutExerciseUuid = newExUuid)
            }
            DBWorkoutExerciseWithSets(
                exercise = exWithSets.exercise.copy(
                    uuid = newExUuid,
                    workoutRecordUuid = firstTree.row.uuid,
                    position = basePosition + idx,
                ),
                sets = newSets,
            )
        }
        val mergedExercises = firstTree.exercises + mergedTail
        // One transaction: a crash between absorbing and tombstoning would
        // leave duplicate live copies.
        workoutsDB.mergeWorkoutRecords(
            merged = firstTree.copy(
                row = firstTree.row.copy(updatedDate = Clock.System.now()),
                exercises = mergedExercises,
            ),
            tombstoneUuid = secondRecord.id,
        )

        val dateStr = firstRecord.date.toString()
        val exerciseLookup = exerciseLookupForRead(userId)
        val trees = workoutsDB.getWorkoutRecordsByJournal(userId, journalId)
            .filter { it.row.date == dateStr }
        return toDomainList(trees, exerciseLookup)
    }

    override suspend fun removeExerciseFromRecord(
        userId: String,
        journalId: String,
        record: WorkoutRecord,
        exercise: WorkoutExercise,
    ): List<WorkoutRecord> {
        val tree = workoutsDB.getWorkoutRecordById(record.id) ?: return emptyList()
        val removed = tree.exercises.firstOrNull { it.exercise.uuid == exercise.id }
        val remaining = tree.exercises.filterNot { it.exercise.uuid == exercise.id }
        // "Remove from superset" is a SPLIT (mergeRecords' inverse), not a
        // delete. A record with a single exercise isn't a superset — nothing to split.
        if (removed != null && remaining.isNotEmpty()) {
            val now = Clock.System.now()
            val repositioned = remaining.mapIndexed { idx, exWithSets ->
                exWithSets.copy(exercise = exWithSets.exercise.copy(position = idx))
            }
            // The split-off exercise keeps its uuid and sets in a new record.
            // splitWorkoutRecord runs reposition + sibling shift + insert in
            // ONE transaction — the exercise must never be observable as
            // missing from both records (crash / concurrent sync push).
            val newRecordUuid = randomUuid()
            workoutsDB.splitWorkoutRecord(
                source = tree.copy(
                    row = tree.row.copy(updatedDate = now),
                    exercises = repositioned,
                ),
                shiftedUpdatedDate = now,
                newRecord = DBWorkoutRecord(
                    row = newRecordRow(
                        newRecordUuid,
                        userId,
                        journalId,
                        tree.row.date,
                        tree.row.position + 1,
                        tree.row.workoutNumber,
                        now,
                    ),
                    exercises = listOf(
                        removed.copy(
                            exercise = removed.exercise.copy(
                                workoutRecordUuid = newRecordUuid,
                                position = 0,
                            ),
                        ),
                    ),
                ),
            )
        }
        val dateStr = record.date.toString()
        val exerciseLookup = exerciseLookupForRead(userId)
        val trees = workoutsDB.getWorkoutRecordsByJournal(userId, journalId)
            .filter { it.row.date == dateStr }
        return toDomainList(trees, exerciseLookup)
    }

    override suspend fun deleteRecord(
        userId: String,
        journalId: String,
        record: WorkoutRecord,
    ) {
        workoutsDB.softDeleteWorkoutRecord(record.id)
    }

    override suspend fun deleteRecordsForDate(
        userId: String,
        journalId: String,
        date: LocalDate,
    ) {
        val dateStr = date.toString()
        workoutsDB.getWorkoutRecordsByJournal(userId, journalId)
            .filter { it.row.date == dateStr }
            .forEach { workoutsDB.softDeleteWorkoutRecord(it.row.uuid) }
        // Every page of the day is now empty → tombstone its sessions and notes
        // too, so a later workout on this date can't inherit them.
        //
        // The sessions half is load-bearing beyond tidiness: `getRunningSession`
        // is user-scoped, not date-scoped, so a session left RUNNING here becomes
        // a ghost that `startSession` returns for every page — blocking Start
        // app-wide until the user ends a workout whose records no longer exist.
        // (`clearStalePageMetaForNewWorkouts` can't rescue it either: that only
        // touches sessions with endedAt != null.) Tombstoned, not deleted, so the
        // removal reaches AWS instead of being resurrected by the next pull.
        database?.let { db ->
            withContext(Dispatchers.IO) {
                val nowText = Clock.System.now().toStoredString()
                db.workoutSessionsQueries.getSessionsForDay(userId, journalId, dateStr)
                    .executeAsList()
                    .forEach { session ->
                        db.workoutSessionsQueries
                            .softDeleteWorkoutSessionByUuid(nowText, session.uuid, userId)
                    }
                db.workoutNotesQueries.getNotesForDay(userId, journalId, dateStr)
                    .executeAsList()
                    .forEach { note ->
                        db.workoutNotesQueries.softDeleteNoteByPage(nowText, userId, journalId, dateStr, note.workoutNumber)
                    }
            }
        }
    }

    override suspend fun deleteUserRecords(userId: String) {
        workoutsDB.deleteAllForUser(userId)
        // Workout notes are owned here now — purge them in the same account-delete.
        database?.let { db -> withContext(Dispatchers.IO) { db.workoutNotesQueries.deleteNotesByUserId(userId) } }
    }

    /**
     * ONE `database.transaction {}` covering both tables: every live record on
     * (date, workoutNumber) gets the same tombstone [deleteRecord] applies,
     * then the page's session row and note (if any) are tombstoned too — soft,
     * so the removals propagate to AWS instead of being resurrected by the next
     * pull. Any throw rolls
     * back BOTH. This is the only place cross-table atomicity for
     * record+session lives, because SQLDelight transactions don't compose
     * across suspend repository-method calls — true atomicity needs raw
     * same-transaction `Queries` calls, which only a method holding
     * [database] directly can make.
     *
     * [afterRecordTombstones] fires between the record loop and the session
     * lookup — the `internal` jvmTest seam forcing a mid-transaction throw to
     * prove the rollback (see `RecordRepositoryTest`). Always `null` in production.
     */
    override suspend fun deleteWorkoutAtomic(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ) {
        val db = checkNotNull(database) {
            "DefaultRecordRepository.deleteWorkoutAtomic requires the primary constructor's " +
                "`database` parameter — this instance was built via the legacy 3-arg constructor."
        }
        val dateStr = date.toString()
        val nextDayStr = date.plusDaysSafe(1).toString()
        val workoutNumberLong = workoutNumber.toLong()
        val nowText = Clock.System.now().toStoredString()
        withContext(Dispatchers.IO) {
            db.transaction {
                db.workoutRecordsQueries
                    .getWorkoutRecordsByJournal(userId, journalId, dateStr, nextDayStr)
                    .executeAsList()
                    .filter { it.workoutNumber == workoutNumberLong }
                    .forEach { record ->
                        db.workoutRecordsQueries.softDeleteWorkoutRecord(
                            deletedAt = nowText,
                            updatedDate = nowText,
                            uuid = record.uuid,
                        )
                    }
                afterRecordTombstones?.invoke()
                db.workoutSessionsQueries
                    .getSessionByWorkoutNumber(userId, journalId, dateStr, workoutNumberLong)
                    .executeAsOneOrNull()
                    ?.let { session ->
                        db.workoutSessionsQueries
                            .softDeleteWorkoutSessionByUuid(nowText, session.uuid, userId)
                    }
                // The page is gone → its note goes with it (same transaction), so a
                // later workout reusing this (date, workoutNumber) never inherits it.
                db.workoutNotesQueries.softDeleteNoteByPage(nowText, userId, journalId, dateStr, workoutNumberLong)
            }
        }
    }

    /**
     * Before a NEW workout is written to a page, wipe any metadata a prior
     * (deleted) occupant stranded at that (date, workoutNumber) so it can't bleed
     * into the new one. Applies ONLY to pages with no live records (a fresh
     * workout, not an append). A RUNNING session is the current timer workout —
     * never touched; only a finished session is stale. All in one transaction.
     * Shared by the direct-add and copy/import/repeat write paths.
     */
    private suspend fun clearStalePageMetaForNewWorkouts(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumbers: Set<Int>,
    ) {
        if (workoutNumbers.isEmpty()) return
        val db = database ?: return
        val dateStr = date.toString()
        val nextDayStr = date.plusDaysSafe(1).toString()
        val nowText = Clock.System.now().toStoredString()
        withContext(Dispatchers.IO) {
            db.transaction {
                val liveNumbers = db.workoutRecordsQueries
                    .getWorkoutRecordsByJournal(userId, journalId, dateStr, nextDayStr)
                    .executeAsList()
                    .map { it.workoutNumber }
                    .toSet()
                workoutNumbers.forEach { wn ->
                    val wnLong = wn.toLong()
                    if (wnLong in liveNumbers) return@forEach // appending to an existing workout
                    db.workoutSessionsQueries
                        .getSessionByWorkoutNumber(userId, journalId, dateStr, wnLong)
                        .executeAsOneOrNull()
                        ?.takeIf { it.endedAt != null }
                        ?.let {
                            db.workoutSessionsQueries
                                .softDeleteWorkoutSessionByUuid(nowText, it.uuid, userId)
                        }
                    db.workoutNotesQueries.softDeleteNoteByPage(nowText, userId, journalId, dateStr, wnLong)
                }
            }
        }
    }

    // ─── Workout notes (per-page, decoupled from sessions) ───────────────

    override suspend fun getWorkoutNote(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): String? = withContext(Dispatchers.IO) {
        val db = database ?: return@withContext null
        db.workoutNotesQueries
            .getNoteByPage(userId, journalId, date.toString(), workoutNumber.toLong())
            .executeAsOneOrNull()
    }

    override fun getWorkoutNotesForDayFlow(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): Flow<Map<Int, String>> {
        val db = database ?: return flowOf(emptyMap())
        return db.workoutNotesQueries
            .getNotesForDay(userId, journalId, date.toString())
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.associate { it.workoutNumber.toInt() to it.comment } }
    }

    /** Blank clears (tombstone); otherwise upsert — a reused page revives its row. */
    override suspend fun setWorkoutNote(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        text: String,
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            clearWorkoutNote(userId, journalId, date, workoutNumber)
            return
        }
        val db = checkNotNull(database) {
            "DefaultRecordRepository.setWorkoutNote requires the primary constructor's `database`."
        }
        val dateStr = date.toString()
        val nextDayStr = date.plusDaysSafe(1).toString()
        val workoutNumberLong = workoutNumber.toLong()
        withContext(Dispatchers.IO) {
            // Manual upsert (the dialect has no ON CONFLICT): revive the page's
            // existing row if any (keeping its uuid/remoteId for sync), else insert.
            db.transaction {
                // Guard the save/delete race: if the page has no live records the
                // workout was deleted mid-save — don't resurrect a note for a gone page.
                val pageHasRecords = db.workoutRecordsQueries
                    .getWorkoutRecordsByJournal(userId, journalId, dateStr, nextDayStr)
                    .executeAsList()
                    .any { it.workoutNumber == workoutNumberLong }
                if (!pageHasRecords) return@transaction
                val existing = db.workoutNotesQueries
                    .getNoteUuidByPage(userId, journalId, dateStr, workoutNumberLong)
                    .executeAsOneOrNull()
                if (existing != null) {
                    db.workoutNotesQueries.reviveNoteByPage(trimmed, userId, journalId, dateStr, workoutNumberLong)
                } else {
                    db.workoutNotesQueries.insertNote(
                        uuid = randomUuid(),
                        userId = userId,
                        journalId = journalId,
                        date = dateStr,
                        workoutNumber = workoutNumberLong,
                        comment = trimmed,
                    )
                }
            }
        }
    }

    override suspend fun clearWorkoutNote(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ) {
        val db = database ?: return
        withContext(Dispatchers.IO) {
            db.workoutNotesQueries.softDeleteNoteByPage(
                deletedAt = Clock.System.now().toStoredString(),
                userId = userId,
                journalId = journalId,
                date = date.toString(),
                workoutNumber = workoutNumber.toLong(),
            )
        }
    }

    override suspend fun addSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
    ) {
        val tree = findTreeContainingExercise(userId, journalId, workoutExerciseId) ?: return
        val updatedExercises = tree.exercises.map { exWithSets ->
            if (exWithSets.exercise.uuid != workoutExerciseId) return@map exWithSets
            val newSet = DBWorkoutSetObject(
                uuid = randomUuid(),
                workoutExerciseUuid = workoutExerciseId,
                position = exWithSets.sets.size,
                weight = weight,
                reps = reps,
                distance = distance,
                duration = duration,
                completed = true,
            )
            exWithSets.copy(sets = exWithSets.sets + newSet)
        }
        workoutsDB.replaceWorkoutRecord(
            tree.copy(
                row = tree.row.copy(updatedDate = Clock.System.now()),
                exercises = updatedExercises,
            ),
        )
    }

    override suspend fun updateSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
    ): Boolean {
        val tree = findTreeContainingExercise(userId, journalId, workoutExerciseId) ?: return false
        var matched = false
        val updatedExercises = tree.exercises.map { exWithSets ->
            if (exWithSets.exercise.uuid != workoutExerciseId) return@map exWithSets
            val updatedSets = exWithSets.sets.map { set ->
                if (set.uuid != setId) {
                    set
                } else {
                    matched = true
                    set.copy(
                        weight = weight,
                        reps = reps,
                        distance = distance,
                        duration = duration,
                    )
                }
            }
            exWithSets.copy(sets = updatedSets)
        }
        // Set already gone (e.g. concurrent sync pull): don't write — a no-op
        // replaceWorkoutRecord would still push a spurious, stale record.
        if (!matched) return false
        workoutsDB.replaceWorkoutRecord(
            tree.copy(
                row = tree.row.copy(updatedDate = Clock.System.now()),
                exercises = updatedExercises,
            ),
        )
        return true
    }

    override suspend fun deleteSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
    ): Boolean {
        val tree = findTreeContainingExercise(userId, journalId, workoutExerciseId) ?: return false
        var matched = false
        val updatedExercises = tree.exercises.map { exWithSets ->
            if (exWithSets.exercise.uuid != workoutExerciseId) return@map exWithSets
            val survivors = exWithSets.sets.filterNot { it.uuid == setId }
            if (survivors.size != exWithSets.sets.size) matched = true
            // Re-position survivors so positions stay contiguous.
            val repositioned = survivors.mapIndexed { idx, set -> set.copy(position = idx) }
            exWithSets.copy(sets = repositioned)
        }
        if (!matched) return false
        workoutsDB.replaceWorkoutRecord(
            tree.copy(
                row = tree.row.copy(updatedDate = Clock.System.now()),
                exercises = updatedExercises,
            ),
        )
        return true
    }

    // ─── Mapping ───────────────────────────────────────────────────────

    /**
     * Maps every tree → domain `WorkoutRecord`, dropping any whose children
     * fail to resolve (e.g. catalog row hard-deleted).
     *
     * @param includeLastOccurrence When true, computes the "you did 100kg×10
     * last time" hint on every set (in-memory pass + a bounded SQL fallback
     * for exercises whose oldest occurrence sits at the window boundary).
     * List/aggregate paths (history, workload) set it false and skip the
     * whole computation; only `getRecordsByDate` (+ its Flow) leaves it true.
     */
    private suspend fun toDomainList(
        trees: List<DBWorkoutRecord>,
        exerciseLookup: Map<String, Exercise>,
        includeLastOccurrence: Boolean = true,
    ): List<WorkoutRecord> = withContext(Dispatchers.Default) {
        // The O(records×exercises×sets) mapping + buildLastOccurrenceMap's
        // sort/group are the dominant cost here. Forced onto Default so they
        // never run on the caller's dispatcher — several Android callers
        // reach this via a dispatcher-less viewModelScope.launch (Main) that
        // a downstream .flowOn() can't rescue. Keeps every record read off
        // the UI thread regardless of row count.
        if (trees.isEmpty()) return@withContext emptyList()
        val lastOccurrenceMap = if (includeLastOccurrence) {
            buildLastOccurrenceMap(trees)
        } else {
            emptyMap()
        }
        val out = ArrayList<WorkoutRecord>(trees.size)
        for (tree in trees) {
            val mapped = runCatching { toDomain(tree, exerciseLookup, lastOccurrenceMap) }.getOrNull()
            if (mapped != null) out.add(mapped)
        }
        out
    }

    /**
     * Builds `weUuid -> DBLastOccurrence` for every workoutExercise inside
     * [trees]. For each catalog exercise, the last occurrence is the most
     * recent prior one in chronological order
     * `(record.date DESC, record.position DESC, we.position DESC)`; its full
     * set list is carried so `LastOccurrence.setAt` can align per position
     * instead of stamping the last set everywhere. Most lookups resolve
     * in-memory; "oldest in window per exercise" cases fall through to
     * [WorkoutsDBDataSource.getLastOccurrenceForExercisesBeforeDate].
     */
    private suspend fun buildLastOccurrenceMap(
        trees: List<DBWorkoutRecord>,
    ): Map<String, DBLastOccurrence> {
        // Group by catalog exerciseUuid: each group is a chronological chain.
        data class WeContext(
            val tree: DBWorkoutRecord,
            val exWithSets: DBWorkoutExerciseWithSets,
        )
        val byExerciseUuid = trees
            .flatMap { tree -> tree.exercises.map { WeContext(tree, it) } }
            .groupBy { it.exWithSets.exercise.exerciseUuid }

        val lastByWeUuid = HashMap<String, DBLastOccurrence>(
            byExerciseUuid.values.sumOf { it.size }
        )
        val boundary = ArrayList<WeContext>()

        for ((_, contexts) in byExerciseUuid) {
            // Sort newest → oldest so each entry's "previous" is the next one;
            // matches `getLastWorkoutExercisesForExercisesBeforeDate`'s tiebreaker chain.
            //
            // workoutNumber is load-bearing and must stay ABOVE position: since
            // 9.sqm, `position` is page-relative (it restarts at 0 for each
            // workout of the day), so on a two-workout day both records sit at
            // position 0 and tie. The stable sort then keeps input order — which
            // is workoutNumber ASC, exactly backwards for a newest-first chain —
            // and the morning row would take the EVENING session as its "last
            // time", i.e. a hint from a session that hadn't happened yet.
            val sorted = contexts.sortedWith(
                compareByDescending<WeContext> { it.tree.row.date }
                    .thenByDescending { it.tree.row.workoutNumber }
                    .thenByDescending { it.tree.row.position }
                    .thenByDescending { it.exWithSets.exercise.position }
            )
            for (i in sorted.indices) {
                val current = sorted[i]
                val prior = sorted.getOrNull(i + 1)
                if (prior != null) {
                    lastByWeUuid[current.exWithSets.exercise.uuid] = DBLastOccurrence(
                        recordDate = prior.tree.row.date,
                        sets = prior.exWithSets.sets.sortedBy { it.position },
                    )
                } else {
                    // Oldest in this window — the real prior occurrence, if any,
                    // is outside the loaded trees. Defer to SQL.
                    boundary.add(current)
                }
            }
        }

        // Batch boundary lookups by date: one round-trip per distinct record
        // date instead of one query per exercise.
        for ((date, contexts) in boundary.groupBy { it.tree.row.date }) {
            val sample = contexts.first()
            val lastByExerciseUuid = workoutsDB.getLastOccurrenceForExercisesBeforeDate(
                exerciseUuids = contexts.map { it.exWithSets.exercise.exerciseUuid }.distinct(),
                userId = sample.tree.row.userId,
                journalId = sample.tree.row.journalId,
                beforeDateString = date,
            )
            for (b in contexts) {
                lastByExerciseUuid[b.exWithSets.exercise.exerciseUuid]?.let {
                    lastByWeUuid[b.exWithSets.exercise.uuid] = it
                }
            }
        }

        return lastByWeUuid
    }

    private fun toDomain(
        tree: DBWorkoutRecord,
        exerciseLookup: Map<String, Exercise>,
        lastOccurrenceMap: Map<String, DBLastOccurrence>,
    ): WorkoutRecord {
        val recordDate = LocalDate.parse(tree.row.date)
        val mappedExercises = tree.exercises.mapNotNull { exWithSets ->
            // A soft-deleted catalog entry is legitimate, not a crash —
            // drop just that one exercise from the rendered tree.
            runCatching {
                mapExercise(
                    exWithSets = exWithSets,
                    userId = tree.row.userId,
                    journalId = tree.row.journalId,
                    recordDate = recordDate,
                    exerciseLookup = exerciseLookup,
                    lastOccurrenceMap = lastOccurrenceMap,
                )
            }.getOrNull()
        }
        return WorkoutRecord(
            id = tree.row.uuid,
            userId = tree.row.userId,
            journalId = tree.row.journalId,
            position = tree.row.position,
            workoutNumber = tree.row.workoutNumber,
            date = recordDate,
            exercises = mappedExercises,
            createdDate = tree.row.createdDate,
            updatedDate = tree.row.updatedDate,
        )
    }

    private fun mapExercise(
        exWithSets: DBWorkoutExerciseWithSets,
        userId: String,
        journalId: String,
        recordDate: LocalDate,
        exerciseLookup: Map<String, Exercise>,
        lastOccurrenceMap: Map<String, DBLastOccurrence>,
    ): WorkoutExercise {
        // O(1) lookup against the per-call exercise dict.
        val domainExercise = exerciseLookup[exWithSets.exercise.exerciseUuid]
            ?: error("Catalog exercise not found: ${exWithSets.exercise.exerciseUuid}")
        val sets = exWithSets.sets.map { set ->
            mapSet(
                set = set,
                userId = userId,
                journalId = journalId,
                recordDate = recordDate,
                resultType = domainExercise.resultType,
            )
        }
        // Pre-computed by `buildLastOccurrenceMap` — no per-we SQL call.
        // Per-position alignment (incl. overflow rule) is
        // `LastOccurrence.setAt`, shared by both platforms.
        val lastOccurrence = lastOccurrenceMap[exWithSets.exercise.uuid]?.let { db ->
            // mapExercise runs inside runCatching{}.getOrNull() in toDomain, so
            // throwing on a malformed date would drop the CURRENT exercise too.
            // Losing just the hint is the correct degradation.
            val occurrenceDate = runCatching { LocalDate.parse(db.recordDate) }.getOrNull()
                ?: return@let null
            LastOccurrence(
                date = occurrenceDate,
                sets = db.sets.map { set ->
                    mapSet(
                        set = set,
                        userId = userId,
                        journalId = journalId,
                        recordDate = occurrenceDate,
                        resultType = domainExercise.resultType,
                    )
                },
            )
        }
        return WorkoutExercise(
            id = exWithSets.exercise.uuid,
            userId = userId,
            journalId = journalId,
            date = recordDate,
            exercise = domainExercise,
            sets = sets,
            comment = exWithSets.exercise.comment,
            lastOccurrence = lastOccurrence,
        )
    }

    private fun mapSet(
        set: DBWorkoutSetObject,
        userId: String,
        journalId: String,
        recordDate: LocalDate,
        resultType: ResultType,
    ): WorkoutSet = WorkoutSet(
        id = set.uuid,
        userId = userId,
        journalId = journalId,
        date = recordDate,
        weight = set.weight,
        reps = set.reps,
        distance = set.distance,
        duration = set.duration,
        resultType = resultType,
    )

    // ─── Helpers ──────────────────────────────────────────────────────

    private suspend fun findTreeContainingExercise(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
    ): DBWorkoutRecord? = workoutsDB.getWorkoutRecordsByJournal(userId, journalId)
        .firstOrNull { tree -> tree.exercises.any { it.exercise.uuid == workoutExerciseId } }

    private fun todayInSystemTz(): LocalDate =
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

    private fun LocalDate.plusDaysSafe(days: Int): LocalDate =
        this.plus(days, DateTimeUnit.DAY)

    private fun LocalDate.minusYearsSafe(years: Int): LocalDate =
        this.minus(years, DateTimeUnit.YEAR)
}

/**
 * Repository-internal projection used to group sets by their parent
 * `workoutExerciseUuid` while building the per-occurrence
 * [WorkoutExercise] list for History/Stats. Lives next to its single
 * caller — it's not a data-layer entity and shouldn't be reused.
 */
private data class OccurrenceRow(
    val set: DBWorkoutSetObject,
    val recordDate: String,
    val workoutExerciseComment: String?,
)

/**
 * Repository-internal projection for
 * [DefaultRecordRepository.getWeightedSetHistoryForExercise] — carries the
 * joined row across the datasource boundary before the date-parse and the
 * weight non-null narrowing into the domain
 * [kz.maestrosultan.fitjournal.domain.workout.summary.WeightedSetOccurrence].
 * Lives next to its single caller — not a data-layer entity, don't reuse.
 */
private data class WeightedHistoryRow(
    val recordUuid: String,
    val workoutNumber: Int,
    val recordDate: String,
    val weight: Double?,
    val reps: Int?,
)
