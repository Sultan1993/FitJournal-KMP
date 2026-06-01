package kz.maestrosultan.fitjournal.data.record.repository

import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.DifficultyType
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.domain.identifier.randomUuid
import kz.maestrosultan.fitjournal.data.exercise.entity.DBExerciseObject
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutExerciseObject
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutExerciseWithSets
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutRecord
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutRecordRow
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutSetObject

/**
 * Local-first RecordRepository (KMP shared). KMP SQLite is the only
 * source of truth.
 *
 * Reads come from `WorkoutsDBDataSource` — the joined tree (record +
 * exercises + sets), filtered by `deletedAt IS NULL` at the SQL level.
 * Each read pre-fetches the user's exercise catalog ([exerciseLookupForRead])
 * once and resolves `WorkoutExercise.exercise` via an in-memory map,
 * avoiding per-row catalog SELECTs.
 *
 * `previousWeight` / `previousDistance` / `previousDifficultyType` on
 * `WorkoutSet` are derived per read: one extra query per workoutExercise
 * pulls the most recent set with this exercise BEFORE the record's
 * date — all sets in the workoutExercise share the same previous values
 * (matches pre-FJ-2.0 Parse semantics).
 *
 * Writes update SQLite atomically via `replaceWorkoutRecord` and bump
 * `pendingUpload=1` on the parent. Children of a workoutRecord don't
 * carry their own `pendingUpload` — child mutations bubble up via the
 * parent so the SyncWorker re-encodes the full JSON tree on push.
 *
 * No network calls. The legacy `WorkoutRecordsRemoteDataSource` is only
 * consumed by the one-shot `WorkoutsMigrator` import.
 */
class DefaultRecordRepository(
    private val workoutsDB: WorkoutsDBDataSource,
    private val exercisesDB: ExercisesDBDataSource,
    private val mapper: (DBExerciseObject) -> Exercise,
) : RecordRepository {

    // ─── Reads ─────────────────────────────────────────────────────────

    override fun observeRecordsChanged(userId: String, journalId: String): Flow<String> =
        workoutsDB.observeJournalRecordsSignal(userId, journalId)

    override suspend fun getAllRecords(
        userId: String,
        journalId: String,
    ): List<WorkoutRecord> {
        val exerciseLookup = exerciseLookupForRead(userId)
        val trees = workoutsDB.getWorkoutRecordsByJournal(userId, journalId)
        return toDomainList(trees, exerciseLookup)
    }

    override suspend fun getRecordsByDate(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): List<WorkoutRecord> {
        val exerciseLookup = exerciseLookupForRead(userId)
        // Half-open one-day window: [date, date+1). LocalDate.toString()
        // renders ISO yyyy-MM-dd, which is what the SQL filter compares
        // lexicographically.
        val from = date.toString()
        val to = date.plusDaysSafe(1).toString()
        val trees = workoutsDB.getWorkoutRecordsByJournal(userId, journalId, from, to)
        return toDomainList(trees, exerciseLookup)
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
        return toDomainList(trees, exerciseLookup)
    }

    override suspend fun getRecentRecords(
        userId: String,
        journalId: String,
    ): List<WorkoutRecord> {
        val exerciseLookup = exerciseLookupForRead(userId)
        // Caps at the last 1 year. The partial index on
        // (userId, journalId, date DESC) WHERE deletedAt IS NULL plus the
        // JOIN-based bulk read load the whole window in one shot — no
        // pagination needed even on heavy users (1000+ records).
        val today = todayInSystemTz()
        val oneYearAgo = today.minusYearsSafe(1).toString()
        val to = "9999-12-31"
        val trees = workoutsDB.getWorkoutRecordsByJournal(userId, journalId, oneYearAgo, to)
        // Workout-history list cells don't render previous-set hints —
        // skip the bulk compute + boundary SQL fallback entirely. Saves
        // ~5ms on a 1-year window for a heavy user and avoids ~30
        // single-purpose `getLastSetForExerciseBeforeDate` calls.
        return toDomainList(trees, exerciseLookup, includePreviousSet = false)
    }

    override suspend fun getExerciseOccurrences(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutExercise> {
        // 1 SQL: catalog row (with categories). uuid PK lookup. Returns
        // null if the catalog row was soft-deleted; in that case the
        // screen has nothing legitimate to render, so bail.
        val dbExercise = exercisesDB.getExerciseByUuid(exerciseId)
            ?: return emptyList()
        val domainExercise = mapper(dbExercise)
        // 1 SQL: every set for this exercise in the 1-year window, with
        // recordDate + workoutExercise.comment surfaced via the mapper.
        // Group by workoutExerciseUuid in Kotlin to rebuild the
        // per-occurrence shape History/Stats expect.
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
                // Defensive: a malformed stored date (e.g. a legacy "…Z" value
                // written before the pull-side normalization) must not throw and
                // sink the whole occurrence list — skip just that occurrence.
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
                            previousSet = null,
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
        // Same SQL as `getExerciseOccurrences`. Info-tab "best weight /
        // best cardio" just wants a flat list of sets in the window;
        // date is per-row and surfaces on the returned `WorkoutSet`.
        // workoutExercise.comment is irrelevant here, so we drop it.
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
                difficultyType = DifficultyType.create(set.difficultyType),
                resultType = ResultType.WEIGHT_REPS,
                previousWeight = null,
                previousDistance = null,
                previousDifficultyType = DifficultyType.NONE,
            )
        }
    }

    /**
     * 1-year window (`[oneYearAgo, far-future)`). Matches the app-wide
     * windowing rule applied by `getRecentRecords` so the exercise
     * details page never surfaces history older than the rest of the
     * app shows.
     */
    private fun exerciseDetailsWindow(): Pair<String, String> {
        val from = todayInSystemTz().minusYearsSafe(1).toString()
        val to = "9999-12-31"
        return from to to
    }

    /**
     * One SQL call to the exercise catalog (with categories pre-joined),
     * shaped into a `[uuid: Exercise]` lookup so toDomain can resolve
     * `WorkoutExercise.exercise` without per-row SELECTs. Pure read; no
     * state retained between calls — SQLite is the source of truth.
     * User-scoped so customs from a previous logged-in account don't
     * leak in after an account switch.
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
        exerciseIds: List<String>,
    ) {
        if (exerciseIds.isEmpty()) return
        val lastPosition = lastRecordPositionForDate(userId, journalId, date)
        val now = Clock.System.now()
        val dateStr = date.toString()
        val trees = exerciseIds.mapIndexed { index, exerciseId ->
            val recordUuid = randomUuid()
            DBWorkoutRecord(
                row = newRecordRow(recordUuid, userId, journalId, dateStr, lastPosition + index + 1, now),
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
        insertCopiedRecords(userId, journalId, date, records)
    }

    override suspend fun addRecordsFromDateToToday(
        userId: String,
        journalId: String,
        date: LocalDate,
    ) {
        val source = getRecordsByDate(userId, journalId, date)
        insertCopiedRecords(userId, journalId, todayInSystemTz(), source)
    }

    override suspend fun replaceExerciseInRecord(
        userId: String,
        journalId: String,
        recordId: String,
        newExerciseId: String,
    ) {
        val tree = workoutsDB.getWorkoutRecordById(recordId) ?: return
        val newFirst = DBWorkoutExerciseWithSets(
            exercise = DBWorkoutExerciseObject(
                uuid = randomUuid(),
                workoutRecordUuid = tree.row.uuid,
                exerciseUuid = newExerciseId,
                position = 0,
                comment = null,
            ),
            sets = emptyList(),
        )
        // Replace the first exercise; keep any others (a superset record)
        // re-positioned after it, matching the iOS import repository.
        val tail = tree.exercises.drop(1).mapIndexed { index, exWithSets ->
            exWithSets.copy(exercise = exWithSets.exercise.copy(position = index + 1))
        }
        workoutsDB.replaceWorkoutRecord(
            tree.copy(
                row = tree.row.copy(updatedDate = Clock.System.now()),
                exercises = listOf(newFirst) + tail,
            ),
        )
    }

    /**
     * Recreates [sources] as fresh records on [date]. Sets keep their
     * reps/duration but drop weight/distance and difficulty (a re-do
     * template); previous-set hints are computed on read, not stored.
     */
    private suspend fun insertCopiedRecords(
        userId: String,
        journalId: String,
        date: LocalDate,
        sources: List<WorkoutRecord>,
    ) {
        if (sources.isEmpty()) return
        val lastPosition = lastRecordPositionForDate(userId, journalId, date)
        val now = Clock.System.now()
        val dateStr = date.toString()
        val trees = sources.mapIndexed { index, source ->
            val recordUuid = randomUuid()
            DBWorkoutRecord(
                row = newRecordRow(recordUuid, userId, journalId, dateStr, lastPosition + index + 1, now),
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
                                reps = srcSet.reps,
                                distance = null,
                                duration = srcSet.duration,
                                difficultyType = DifficultyType.NONE.id,
                                completed = false,
                            )
                        },
                    )
                },
            )
        }
        workoutsDB.createWorkoutRecordsIfMissing(trees)
    }

    private suspend fun lastRecordPositionForDate(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): Int {
        val from = date.toString()
        val to = date.plusDaysSafe(1).toString()
        // -1 when the date has no records, so the first new record lands at
        // position 0 (matches the iOS import repository's base).
        return workoutsDB.getWorkoutRecordsByJournal(userId, journalId, from, to)
            .maxOfOrNull { it.row.position } ?: -1
    }

    private fun newRecordRow(
        uuid: String,
        userId: String,
        journalId: String,
        date: String,
        position: Int,
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
            // Re-parent each merged exercise: new uuid (so AWS doesn't
            // confuse it with the source record's child) + updated
            // workoutRecordUuid + bumped position.
            val newExUuid = randomUuid()
            val newSets = exWithSets.sets.map { it.copy(workoutExerciseUuid = newExUuid) }
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
        workoutsDB.replaceWorkoutRecord(
            firstTree.copy(
                row = firstTree.row.copy(updatedDate = Clock.System.now()),
                exercises = mergedExercises,
            ),
        )
        workoutsDB.softDeleteWorkoutRecord(secondRecord.id)

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
        val remaining = tree.exercises.filterNot { it.exercise.uuid == exercise.id }
        if (remaining.isEmpty()) {
            // Last exercise removed → tombstone the (now empty) record
            // so it doesn't show up as a content-less workout.
            workoutsDB.softDeleteWorkoutRecord(record.id)
        } else {
            // Re-position survivors so positions stay contiguous.
            val repositioned = remaining.mapIndexed { idx, exWithSets ->
                exWithSets.copy(exercise = exWithSets.exercise.copy(position = idx))
            }
            workoutsDB.replaceWorkoutRecord(
                tree.copy(
                    row = tree.row.copy(updatedDate = Clock.System.now()),
                    exercises = repositioned,
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
    }

    override suspend fun deleteUserRecords(userId: String) {
        workoutsDB.deleteAllForUser(userId)
    }

    override suspend fun addSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
        difficultyType: DifficultyType,
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
                difficultyType = difficultyType.id,
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
        difficultyType: DifficultyType,
    ) {
        val tree = findTreeContainingExercise(userId, journalId, workoutExerciseId) ?: return
        val updatedExercises = tree.exercises.map { exWithSets ->
            if (exWithSets.exercise.uuid != workoutExerciseId) return@map exWithSets
            val updatedSets = exWithSets.sets.map { set ->
                if (set.uuid != setId) {
                    set
                } else {
                    set.copy(
                        weight = weight,
                        reps = reps,
                        distance = distance,
                        duration = duration,
                        difficultyType = difficultyType.id,
                    )
                }
            }
            exWithSets.copy(sets = updatedSets)
        }
        workoutsDB.replaceWorkoutRecord(
            tree.copy(
                row = tree.row.copy(updatedDate = Clock.System.now()),
                exercises = updatedExercises,
            ),
        )
    }

    override suspend fun deleteSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
    ) {
        val tree = findTreeContainingExercise(userId, journalId, workoutExerciseId) ?: return
        val updatedExercises = tree.exercises.map { exWithSets ->
            if (exWithSets.exercise.uuid != workoutExerciseId) return@map exWithSets
            val survivors = exWithSets.sets.filterNot { it.uuid == setId }
            // Re-position survivors so positions stay contiguous.
            val repositioned = survivors.mapIndexed { idx, set -> set.copy(position = idx) }
            exWithSets.copy(sets = repositioned)
        }
        workoutsDB.replaceWorkoutRecord(
            tree.copy(
                row = tree.row.copy(updatedDate = Clock.System.now()),
                exercises = updatedExercises,
            ),
        )
    }

    // ─── Mapping ───────────────────────────────────────────────────────

    /**
     * Maps every tree → domain `WorkoutRecord`, dropping any whose
     * children fail to resolve (e.g. catalog row hard-deleted).
     */
    /**
     * @param includePreviousSet When true, computes the "you did 100kg×10
     * last time" hint on every set. Uses an in-memory pass over the
     * loaded trees + a bounded SQL fallback (one call per unique catalog
     * exercise whose oldest occurrence lies at the window boundary).
     * Set to false on list / aggregate paths (workout history, workload
     * muscle data) where the hint is never rendered — those callers save
     * the entire previous-set computation including the boundary SQL
     * calls. Detail paths (`getRecordsByDate`) leave it true.
     */
    private suspend fun toDomainList(
        trees: List<DBWorkoutRecord>,
        exerciseLookup: Map<String, Exercise>,
        includePreviousSet: Boolean = true,
    ): List<WorkoutRecord> {
        if (trees.isEmpty()) return emptyList()
        val previousSetMap = if (includePreviousSet) {
            buildPreviousSetMap(trees)
        } else {
            emptyMap()
        }
        val out = ArrayList<WorkoutRecord>(trees.size)
        for (tree in trees) {
            val mapped = runCatching { toDomain(tree, exerciseLookup, previousSetMap) }.getOrNull()
            if (mapped != null) out.add(mapped)
        }
        return out
    }

    /**
     * Builds `weUuid -> previousSet?` for every workoutExercise inside
     * [trees]. For each catalog exercise, "previous" is the LAST set of
     * the most recent prior occurrence in chronological order
     * `(record.date DESC, record.position DESC, we.position DESC)`.
     * Most lookups resolve in-memory from the already-loaded trees.
     * The remaining "oldest in window per exercise" cases fall through
     * to [WorkoutsDBDataSource.getLastSetForExerciseBeforeDate].
     */
    private suspend fun buildPreviousSetMap(
        trees: List<DBWorkoutRecord>,
    ): Map<String, DBWorkoutSetObject?> {
        // Flatten and group by catalog exerciseUuid so each group is a
        // chronological chain of occurrences for one exercise.
        data class WeContext(
            val tree: DBWorkoutRecord,
            val exWithSets: DBWorkoutExerciseWithSets,
        )
        val byExerciseUuid = trees
            .flatMap { tree -> tree.exercises.map { WeContext(tree, it) } }
            .groupBy { it.exWithSets.exercise.exerciseUuid }

        val previousByWeUuid = HashMap<String, DBWorkoutSetObject?>(
            byExerciseUuid.values.sumOf { it.size }
        )
        val boundary = ArrayList<WeContext>()

        for ((_, contexts) in byExerciseUuid) {
            // Sort newest → oldest so each entry's "previous" is the
            // NEXT one in the sorted list. Ordering matches
            // `getLastSetForExerciseBeforeDate`'s tiebreaker chain.
            val sorted = contexts.sortedWith(
                compareByDescending<WeContext> { it.tree.row.date }
                    .thenByDescending { it.tree.row.position }
                    .thenByDescending { it.exWithSets.exercise.position }
            )
            for (i in sorted.indices) {
                val current = sorted[i]
                val previous = sorted.getOrNull(i + 1)
                if (previous != null) {
                    previousByWeUuid[current.exWithSets.exercise.uuid] =
                        previous.exWithSets.sets.maxByOrNull { it.position }
                } else {
                    // Oldest occurrence in this window — the real previous
                    // (if any) is outside the loaded trees. Defer to SQL.
                    boundary.add(current)
                }
            }
        }

        // Batch the boundary lookups by date: one round-trip per distinct
        // record date (a single-day read → one query) instead of one query
        // per exercise. userId/journalId are constant across a single read.
        for ((date, contexts) in boundary.groupBy { it.tree.row.date }) {
            val sample = contexts.first()
            val previousByExerciseUuid = workoutsDB.getLastSetsForExercisesBeforeDate(
                exerciseUuids = contexts.map { it.exWithSets.exercise.exerciseUuid }.distinct(),
                userId = sample.tree.row.userId,
                journalId = sample.tree.row.journalId,
                beforeDateString = date,
            )
            for (b in contexts) {
                previousByWeUuid[b.exWithSets.exercise.uuid] =
                    previousByExerciseUuid[b.exWithSets.exercise.exerciseUuid]
            }
        }

        return previousByWeUuid
    }

    private fun toDomain(
        tree: DBWorkoutRecord,
        exerciseLookup: Map<String, Exercise>,
        previousSetMap: Map<String, DBWorkoutSetObject?>,
    ): WorkoutRecord {
        val recordDate = LocalDate.parse(tree.row.date)
        val mappedExercises = tree.exercises.mapNotNull { exWithSets ->
            // A soft-deleted catalog entry is a legitimate state, not a
            // crash — drop just that one exercise from the rendered tree.
            runCatching {
                mapExercise(
                    exWithSets = exWithSets,
                    userId = tree.row.userId,
                    journalId = tree.row.journalId,
                    recordDate = recordDate,
                    exerciseLookup = exerciseLookup,
                    previousSetMap = previousSetMap,
                )
            }.getOrNull()
        }
        return WorkoutRecord(
            id = tree.row.uuid,
            userId = tree.row.userId,
            journalId = tree.row.journalId,
            position = tree.row.position,
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
        previousSetMap: Map<String, DBWorkoutSetObject?>,
    ): WorkoutExercise {
        // O(1) lookup against the per-call exercise dict (one SELECT off
        // the catalog table, shaped for resolution).
        val domainExercise = exerciseLookup[exWithSets.exercise.exerciseUuid]
            ?: error("Catalog exercise not found: ${exWithSets.exercise.exerciseUuid}")
        // Pre-computed by `buildPreviousSetMap` — no per-we SQL call.
        // All sets in this workoutExercise share the same previous values
        // (matches pre-FJ-2.0 semantics).
        val previousSet = previousSetMap[exWithSets.exercise.uuid]
        val sets = exWithSets.sets.map { set ->
            mapSet(
                set = set,
                userId = userId,
                journalId = journalId,
                recordDate = recordDate,
                resultType = domainExercise.resultType,
                previousSet = previousSet,
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
        )
    }

    private fun mapSet(
        set: DBWorkoutSetObject,
        userId: String,
        journalId: String,
        recordDate: LocalDate,
        resultType: ResultType,
        previousSet: DBWorkoutSetObject?,
    ): WorkoutSet = WorkoutSet(
        id = set.uuid,
        userId = userId,
        journalId = journalId,
        date = recordDate,
        weight = set.weight,
        reps = set.reps,
        distance = set.distance,
        duration = set.duration,
        difficultyType = DifficultyType.create(set.difficultyType),
        resultType = resultType,
        previousWeight = previousSet?.weight,
        previousDistance = previousSet?.distance,
        previousDifficultyType = previousSet?.difficultyType?.let(DifficultyType::create)
            ?: DifficultyType.NONE,
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
