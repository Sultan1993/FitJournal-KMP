package kz.maestrosultan.fitjournal.data.record

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.domain.core.FetchPeriod
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.DifficultyType
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.kmp.exercises.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.kmp.identifier.randomUuid
import kz.maestrosultan.fitjournal.kmp.exercises.entity.DBExerciseObject
import kz.maestrosultan.fitjournal.kmp.workouts.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.kmp.workouts.entity.DBWorkoutExerciseWithSets
import kz.maestrosultan.fitjournal.kmp.workouts.entity.DBWorkoutRecord
import kz.maestrosultan.fitjournal.kmp.workouts.entity.DBWorkoutSetObject

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

    override suspend fun getAllRecords(
        userId: String,
        diaryId: String,
    ): List<WorkoutRecord> {
        val exerciseLookup = exerciseLookupForRead(userId)
        val trees = workoutsDB.getWorkoutRecordsByDiary(userId, diaryId)
        return toDomainList(trees, exerciseLookup)
    }

    override fun getAllRecordsFlow(
        userId: String,
        diaryId: String,
    ): Flow<List<WorkoutRecord>> = flow {
        emit(getAllRecords(userId, diaryId))
    }

    override suspend fun getRecordsByDate(
        userId: String,
        diaryId: String,
        date: LocalDate,
    ): List<WorkoutRecord> {
        val exerciseLookup = exerciseLookupForRead(userId)
        // Half-open one-day window: [date, date+1). LocalDate.toString()
        // renders ISO yyyy-MM-dd, which is what the SQL filter compares
        // lexicographically.
        val from = date.toString()
        val to = date.plusDaysSafe(1).toString()
        val trees = workoutsDB.getWorkoutRecordsByDiary(userId, diaryId, from, to)
        return toDomainList(trees, exerciseLookup)
    }

    override fun getRecordsByDateFlow(
        userId: String,
        diaryId: String,
        date: LocalDate,
    ): Flow<List<WorkoutRecord>> = flow {
        emit(getRecordsByDate(userId, diaryId, date))
    }

    override suspend fun getRecordsByMonth(
        userId: String,
        diaryId: String,
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
        val trees = workoutsDB.getWorkoutRecordsByDiary(userId, diaryId, from, to)
        return toDomainList(trees, exerciseLookup)
    }

    override fun getRecordsByMonthFlow(
        userId: String,
        diaryId: String,
        month: String,
        year: String,
    ): Flow<List<WorkoutRecord>> = flow {
        emit(getRecordsByMonth(userId, diaryId, month, year))
    }

    override suspend fun getRecordsByBatch(
        userId: String,
        diaryId: String,
        batchSize: Int,
        forceLoad: Boolean,
    ): Pair<List<WorkoutRecord>, Boolean> {
        val exerciseLookup = exerciseLookupForRead(userId)
        // The workout-list page caps at the last 1 year of records (older
        // sessions remain reachable via the calendar pickers but don't
        // render in the linear list). With the partial index on
        // (userId, diaryId, date DESC) WHERE deletedAt IS NULL and the
        // JOIN-based bulk read, the year window loads in one shot, so
        // pagination is a no-op: `batchSize` is ignored, `isLastBatch`
        // is always true.
        val today = todayInSystemTz()
        val oneYearAgo = today.minusYearsSafe(1).toString()
        val to = "9999-12-31"
        val trees = workoutsDB.getWorkoutRecordsByDiary(userId, diaryId, oneYearAgo, to)
        val records = toDomainList(trees, exerciseLookup)
        return records to true
    }

    override suspend fun getRecordsByPeriod(
        userId: String,
        diaryId: String,
        period: FetchPeriod,
        forceLoad: Boolean,
    ): List<WorkoutRecord> {
        val exerciseLookup = exerciseLookupForRead(userId)
        val today = todayInSystemTz()
        val from = today.minusDaysSafe(period.daysAgo).toString()
        val to = "9999-12-31"
        val trees = workoutsDB.getWorkoutRecordsByDiary(userId, diaryId, from, to)
        return toDomainList(trees, exerciseLookup)
    }

    override suspend fun getSetsForExercise(
        userId: String,
        diaryId: String,
        exerciseId: String,
    ): List<WorkoutSet> {
        val dbSets = workoutsDB.getSetsForExerciseInDiary(
            exerciseUuid = exerciseId,
            userId = userId,
            diaryId = diaryId,
        )
        // Best-result use cases only read weight/reps/distance/duration
        // off each set. date/userId/diaryId on `WorkoutSet` are required
        // by the domain type but unused here — fill with stable
        // placeholders so callers that pass them through (rare) don't
        // break.
        val today = todayInSystemTz()
        return dbSets.map { set ->
            WorkoutSet(
                id = set.uuid,
                userId = userId,
                diaryId = diaryId,
                date = today,
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

    override suspend fun saveWorkoutExerciseComment(
        userId: String,
        diaryId: String,
        workoutExerciseId: String,
        comment: String?,
    ) {
        val tree = findTreeContainingExercise(userId, diaryId, workoutExerciseId) ?: return
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
        diaryId: String,
        records: List<WorkoutRecord>,
    ) {
        // For each record, if its position changed, replace the tree
        // with the updated position. Marks parent pendingUpload=1.
        for (rec in records) {
            val tree = workoutsDB.getWorkoutRecord(rec.id) ?: continue
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
        diaryId: String,
        firstRecord: WorkoutRecord,
        secondRecord: WorkoutRecord,
    ): List<WorkoutRecord> {
        val firstTree = workoutsDB.getWorkoutRecord(firstRecord.id) ?: return emptyList()
        val secondTree = workoutsDB.getWorkoutRecord(secondRecord.id) ?: return emptyList()

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
        val trees = workoutsDB.getWorkoutRecordsByDiary(userId, diaryId)
            .filter { it.row.date == dateStr }
        return toDomainList(trees, exerciseLookup)
    }

    override suspend fun removeExerciseFromRecord(
        userId: String,
        diaryId: String,
        record: WorkoutRecord,
        exercise: WorkoutExercise,
    ): List<WorkoutRecord> {
        val tree = workoutsDB.getWorkoutRecord(record.id) ?: return emptyList()
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
        val trees = workoutsDB.getWorkoutRecordsByDiary(userId, diaryId)
            .filter { it.row.date == dateStr }
        return toDomainList(trees, exerciseLookup)
    }

    override suspend fun deleteRecord(
        userId: String,
        diaryId: String,
        record: WorkoutRecord,
    ) {
        workoutsDB.softDeleteWorkoutRecord(record.id)
    }

    override suspend fun deleteRecordsForDate(
        userId: String,
        diaryId: String,
        date: LocalDate,
    ) {
        val dateStr = date.toString()
        workoutsDB.getWorkoutRecordsByDiary(userId, diaryId)
            .filter { it.row.date == dateStr }
            .forEach { workoutsDB.softDeleteWorkoutRecord(it.row.uuid) }
    }

    override suspend fun deleteUserRecords(userId: String) {
        workoutsDB.deleteAllForUser(userId)
    }

    override suspend fun addSet(
        userId: String,
        diaryId: String,
        workoutExerciseId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
        difficultyType: DifficultyType,
    ) {
        val tree = findTreeContainingExercise(userId, diaryId, workoutExerciseId) ?: return
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
        diaryId: String,
        workoutExerciseId: String,
        setId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
        difficultyType: DifficultyType,
    ) {
        val tree = findTreeContainingExercise(userId, diaryId, workoutExerciseId) ?: return
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
        diaryId: String,
        workoutExerciseId: String,
        setId: String,
    ) {
        val tree = findTreeContainingExercise(userId, diaryId, workoutExerciseId) ?: return
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
    private suspend fun toDomainList(
        trees: List<DBWorkoutRecord>,
        exerciseLookup: Map<String, Exercise>,
    ): List<WorkoutRecord> {
        val out = ArrayList<WorkoutRecord>(trees.size)
        for (tree in trees) {
            val mapped = runCatching { toDomain(tree, exerciseLookup) }.getOrNull()
            if (mapped != null) out.add(mapped)
        }
        return out
    }

    private suspend fun toDomain(
        tree: DBWorkoutRecord,
        exerciseLookup: Map<String, Exercise>,
    ): WorkoutRecord {
        val recordDate = LocalDate.parse(tree.row.date)
        val recordDateString = tree.row.date
        val mappedExercises = tree.exercises.mapNotNull { exWithSets ->
            // A soft-deleted catalog entry is a legitimate state, not a
            // crash — drop just that one exercise from the rendered tree.
            runCatching {
                mapExercise(
                    exWithSets = exWithSets,
                    userId = tree.row.userId,
                    diaryId = tree.row.diaryId,
                    recordDate = recordDate,
                    recordDateString = recordDateString,
                    exerciseLookup = exerciseLookup,
                )
            }.getOrNull()
        }
        return WorkoutRecord(
            id = tree.row.uuid,
            userId = tree.row.userId,
            diaryId = tree.row.diaryId,
            position = tree.row.position,
            date = recordDate,
            exercises = mappedExercises,
            createdDate = tree.row.createdDate,
            updatedDate = tree.row.updatedDate,
        )
    }

    private suspend fun mapExercise(
        exWithSets: DBWorkoutExerciseWithSets,
        userId: String,
        diaryId: String,
        recordDate: LocalDate,
        recordDateString: String,
        exerciseLookup: Map<String, Exercise>,
    ): WorkoutExercise {
        // O(1) lookup against the per-call exercise dict (one SELECT off
        // the catalog table, shaped for resolution).
        val domainExercise = exerciseLookup[exWithSets.exercise.exerciseUuid]
            ?: error("Catalog exercise not found: ${exWithSets.exercise.exerciseUuid}")
        // Look up the most recent set with this exercise BEFORE this
        // record's date so the cell can show "you did 100kg×10 last
        // time" hints. One query per workoutExercise; null on the user's
        // first time logging this exercise. All sets in this
        // workoutExercise share the same previous values — matches
        // pre-FJ-2.0 semantics.
        val previousSet = workoutsDB.getLastSetForExerciseBeforeDate(
            exerciseUuid = exWithSets.exercise.exerciseUuid,
            userId = userId,
            diaryId = diaryId,
            beforeDateString = recordDateString,
        )
        val sets = exWithSets.sets.map { set ->
            mapSet(
                set = set,
                userId = userId,
                diaryId = diaryId,
                recordDate = recordDate,
                resultType = domainExercise.resultType,
                previousSet = previousSet,
            )
        }
        return WorkoutExercise(
            id = exWithSets.exercise.uuid,
            userId = userId,
            diaryId = diaryId,
            date = recordDate,
            exercise = domainExercise,
            sets = sets,
            comment = exWithSets.exercise.comment,
        )
    }

    private fun mapSet(
        set: DBWorkoutSetObject,
        userId: String,
        diaryId: String,
        recordDate: LocalDate,
        resultType: ResultType,
        previousSet: DBWorkoutSetObject?,
    ): WorkoutSet = WorkoutSet(
        id = set.uuid,
        userId = userId,
        diaryId = diaryId,
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
        diaryId: String,
        workoutExerciseId: String,
    ): DBWorkoutRecord? = workoutsDB.getWorkoutRecordsByDiary(userId, diaryId)
        .firstOrNull { tree -> tree.exercises.any { it.exercise.uuid == workoutExerciseId } }

    private fun todayInSystemTz(): LocalDate =
        Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .date

    private fun LocalDate.plusDaysSafe(days: Int): LocalDate =
        this.plus(days, DateTimeUnit.DAY)

    private fun LocalDate.minusDaysSafe(days: Int): LocalDate =
        this.minus(days, DateTimeUnit.DAY)

    private fun LocalDate.minusYearsSafe(years: Int): LocalDate =
        this.minus(years, DateTimeUnit.YEAR)
}
