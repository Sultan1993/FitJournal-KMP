package kz.maestrosultan.fitjournal.data.record.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.data.time.parseStoredInstant
import kz.maestrosultan.fitjournal.data.db.WorkoutExercisesQueries
import kz.maestrosultan.fitjournal.data.db.WorkoutRecordsQueries
import kz.maestrosultan.fitjournal.data.db.WorkoutSetsQueries
import kz.maestrosultan.fitjournal.data.time.toStoredString
import kz.maestrosultan.fitjournal.data.record.entity.DBLastOccurrence
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutExerciseObject
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutExerciseWithSets
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutRecord
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutRecordRow
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutSetObject
import kz.maestrosultan.fitjournal.data.record.entity.map

class WorkoutsDBDataSource(
    private val recordsDao: WorkoutRecordsQueries,
    private val exercisesDao: WorkoutExercisesQueries,
    private val setsDao: WorkoutSetsQueries,
) {

    companion object {
        const val EPOCH: String = "0000-01-01"
        const val FAR_FUTURE: String = "9999-12-31"
    }

    // ─── Reads ────────────────────────────────────────────────────────────

    // MAX(updatedDate) for the journal, re-emitted on every write — home
    // screen's reactive "workouts changed" trigger.
    fun observeJournalRecordsSignal(userId: String, journalId: String): Flow<String> =
        recordsDao.observeJournalRecordsSignal(userId, journalId)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .flowOn(Dispatchers.IO)

    // ─── Free-quota reads (see domain/quota/WorkoutQuotaGate) ─────────────

    suspend fun countMeteredWorkouts(userId: String): Int =
        withContext(Dispatchers.IO) {
            recordsDao.countMeteredWorkouts(userId)
                .executeAsOne()
                .toInt()
        }

    fun countMeteredWorkoutsFlow(userId: String): Flow<Int> =
        recordsDao.countMeteredWorkouts(userId)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.toInt() }
            .flowOn(Dispatchers.IO)

    suspend fun hasAnyRecordInWorkout(
        userId: String,
        journalId: String,
        date: String,
        workoutNumber: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        recordsDao.workoutSlotExists(userId, journalId, date, workoutNumber.toLong())
            .executeAsOne()
    }

    /**
     * Highest workoutNumber this day already uses across records AND sessions, or
     * 0 when empty — see the note on `maxWorkoutNumberOnDate`.
     */
    suspend fun maxWorkoutNumberOnDate(
        userId: String,
        journalId: String,
        date: String,
    ): Int = withContext(Dispatchers.IO) {
        recordsDao.maxWorkoutNumberOnDate(userId, journalId, date)
            .executeAsOne()
            .maxNumber
            ?.toInt()
            ?: 0
    }

    /** Tombstone-EXCLUSIVE twin of [hasAnyRecordInWorkout] — see the note on `liveWorkoutRecordExists`. */
    suspend fun hasLiveRecordInWorkout(
        userId: String,
        journalId: String,
        date: String,
        workoutNumber: Int,
    ): Boolean = withContext(Dispatchers.IO) {
        recordsDao.liveWorkoutRecordExists(userId, journalId, date, workoutNumber.toLong())
            .executeAsOne()
    }

    /**
     * Last time anything was written into this workout, or null when it holds no
     * records. Drives the forgotten-session rule — see [WorkoutSessionActivity].
     *
     * `MAX(updatedDate)` comes back as a nullable TEXT (SQL MAX over zero rows is
     * NULL), hence executeAsOne().MAX plus the null check rather than a bare map.
     */
    suspend fun lastActivityInWorkout(
        userId: String,
        journalId: String,
        date: String,
        workoutNumber: Int,
    ): Instant? = withContext(Dispatchers.IO) {
        recordsDao.lastActivityInWorkout(userId, journalId, date, workoutNumber.toLong())
            .executeAsOne()
            .MAX
            ?.let(::parseStoredInstant)
    }

    suspend fun getPendingUploads(userId: String): List<DBWorkoutRecordRow> = withContext(Dispatchers.IO) {
        recordsDao.getPendingUploads(userId).executeAsList().map { it.map() }
    }

    suspend fun getWorkoutRecordById(uuid: String): DBWorkoutRecord? = withContext(Dispatchers.IO) {
        val row = recordsDao.getWorkoutRecordById(uuid).executeAsOneOrNull()?.map()
            ?: return@withContext null
        DBWorkoutRecord(row = row, exercises = childrenOf(uuid))
    }

    suspend fun getWorkoutRecordByIdIncludingDeleted(uuid: String): DBWorkoutRecord? =
        withContext(Dispatchers.IO) {
            val row = recordsDao.getWorkoutRecordByIdIncludingDeleted(uuid)
                .executeAsOneOrNull()
                ?.map() ?: return@withContext null
            DBWorkoutRecord(row = row, exercises = childrenOf(uuid))
        }

    suspend fun getWorkoutRecordsByJournal(
        userId: String,
        journalId: String,
        from: String = EPOCH,
        to: String = FAR_FUTURE,
    ): List<DBWorkoutRecord> = withContext(Dispatchers.IO) {
        val rows = recordsDao
            .getWorkoutRecordsByJournal(userId, journalId, from, to)
            .executeAsList()
            .map { it.map() }
        if (rows.isEmpty()) return@withContext emptyList()
        val exercises: List<DBWorkoutExerciseObject> = exercisesDao
            .getWorkoutExercisesByJournal(userId, journalId, from, to)
            .executeAsList()
            .map { it.map() }
        val sets: List<DBWorkoutSetObject> = setsDao
            .getWorkoutSetsByJournal(userId, journalId, from, to)
            .executeAsList()
            .map { it.map() }
        val setsByExerciseUuid = sets.groupBy { it.workoutExerciseUuid }
        val exercisesByRecordUuid = exercises
            .groupBy { it.workoutRecordUuid }
            .mapValues { (_, list) ->
                list.map { ex ->
                    DBWorkoutExerciseWithSets(
                        exercise = ex,
                        sets = setsByExerciseUuid[ex.uuid] ?: emptyList(),
                    )
                }
            }
        rows.map { row ->
            DBWorkoutRecord(
                row = row,
                exercises = exercisesByRecordUuid[row.uuid] ?: emptyList(),
            )
        }
    }

    /**
     * One row per set, with the parent record's `date` and the parent
     * workoutExercise's `comment` projected from the JOIN. Caller picks the
     * row shape via [mapper] so the data layer doesn't invent a one-off
     * projection type.
     */
    suspend fun <T : Any> getSetsForExerciseInJournal(
        exerciseUuid: String,
        userId: String,
        journalId: String,
        from: String = EPOCH,
        to: String = FAR_FUTURE,
        mapper: (
            set: DBWorkoutSetObject,
            recordDate: String,
            workoutExerciseComment: String?,
        ) -> T,
    ): List<T> = withContext(Dispatchers.IO) {
        setsDao
            .getWorkoutSetsForExerciseInJournal(
                exerciseUuid = exerciseUuid,
                userId = userId,
                journalId = journalId,
                date = from,
                date_ = to,
            ) { uuid, weUuid, position, weight, reps, distance, duration, _, completed, recordDate, weComment ->
                mapper(
                    DBWorkoutSetObject(
                        uuid = uuid,
                        workoutExerciseUuid = weUuid,
                        position = position.toInt(),
                        weight = weight,
                        reps = reps?.toInt(),
                        distance = distance,
                        duration = duration?.toInt(),
                        completed = completed,
                    ),
                    recordDate,
                    weComment,
                )
            }
            .executeAsList()
    }

    /**
     * One row per WEIGHTED set of [exerciseUuid] in (user, journal) on live
     * records up to and including [upToDate] (stored TEXT form), with the
     * parent record's identity (uuid, workoutNumber, date) projected from the
     * JOIN. Weight-less rows excluded at the SQL level. Same [mapper]
     * convention as [getSetsForExerciseInJournal].
     */
    suspend fun <T : Any> getWeightedSetHistoryForExercise(
        userId: String,
        journalId: String,
        exerciseUuid: String,
        upToDate: String,
        mapper: (
            recordUuid: String,
            workoutNumber: Int,
            recordDate: String,
            weight: Double?,
            reps: Int?,
        ) -> T,
    ): List<T> = withContext(Dispatchers.IO) {
        setsDao
            .getWeightedSetHistoryForExercise(
                userId,
                journalId,
                exerciseUuid,
                upToDate,
            ) { recordUuid, workoutNumber, recordDate, weight, reps ->
                mapper(recordUuid, workoutNumber.toInt(), recordDate, weight, reps?.toInt())
            }
            .executeAsList()
    }

    // For catalog exercises sharing one cutoff date, returns ALL sets
    // (position-ordered) of the most recent prior workoutExercise per exercise
    // uuid — two queries total, not one per exercise. Full set list lets the
    // caller align the hint per position instead of stamping the last set everywhere.
    suspend fun getLastOccurrenceForExercisesBeforeDate(
        exerciseUuids: Collection<String>,
        userId: String,
        journalId: String,
        beforeDateString: String,
    ): Map<String, DBLastOccurrence> = withContext(Dispatchers.IO) {
        if (exerciseUuids.isEmpty()) return@withContext emptyMap()
        val weRows = setsDao
            .getLastWorkoutExercisesForExercisesBeforeDate(exerciseUuids, userId, journalId, beforeDateString)
            .executeAsList()
        if (weRows.isEmpty()) return@withContext emptyMap()
        val weUuidToExerciseUuid = weRows.associate { it.weUuid to it.exerciseUuid }
        // The occurrence's own record date, so domain LastOccurrence can say WHEN.
        val exerciseUuidToDate = weRows.associate { it.exerciseUuid to it.recordDate }
        val setsByExerciseUuid = setsDao
            .getSetsForWorkoutExercises(weUuidToExerciseUuid.keys)
            .executeAsList()
            .mapNotNull { row -> weUuidToExerciseUuid[row.workoutExerciseUuid]?.let { exUuid -> exUuid to row.map() } }
            .groupBy({ it.first }, { it.second })
        setsByExerciseUuid.mapNotNull { (exUuid, sets) ->
            exerciseUuidToDate[exUuid]?.let { date -> exUuid to DBLastOccurrence(date, sets) }
        }.toMap()
    }

    private fun childrenOf(workoutRecordUuid: String): List<DBWorkoutExerciseWithSets> {
        // Two SQL calls instead of 1 + N: all exercises for the record, then
        // one bulk-join load of every set, grouped by `workoutExerciseUuid`
        // (already position-ordered via `idx_workoutSets_exercise`). Cheaper
        // on the sync push loop, which fires this per pending record.
        val exercises = exercisesDao
            .getWorkoutExercisesByRecord(workoutRecordUuid)
            .executeAsList()
            .map { it.map() }
        if (exercises.isEmpty()) return emptyList()
        val setsByExerciseUuid = setsDao
            .getWorkoutSetsByRecord(workoutRecordUuid)
            .executeAsList()
            .map { it.map() }
            .groupBy { it.workoutExerciseUuid }
        return exercises.map { ex ->
            DBWorkoutExerciseWithSets(
                exercise = ex,
                sets = setsByExerciseUuid[ex.uuid] ?: emptyList(),
            )
        }
    }

    // ─── Writes ───────────────────────────────────────────────────────────

    suspend fun createWorkoutRecordIfMissing(record: DBWorkoutRecord): Boolean = withContext(Dispatchers.IO) {
        // Atomic check-and-insert — an existence check outside the transaction
        // would TOCTOU-race two concurrent migrator runs into a double insert.
        recordsDao.transactionWithResult {
            if (recordsDao.getWorkoutRecordByIdIncludingDeleted(record.row.uuid).executeAsOneOrNull() != null) {
                return@transactionWithResult false
            }
            insertRecord(record)
            true
        }
    }

    suspend fun createWorkoutRecordsIfMissing(records: List<DBWorkoutRecord>): Int = withContext(Dispatchers.IO) {
        if (records.isEmpty()) return@withContext 0
        recordsDao.transactionWithResult {
            var inserted = 0
            for (record in records) {
                if (recordsDao.getWorkoutRecordByIdIncludingDeleted(record.row.uuid)
                        .executeAsOneOrNull() != null) {
                    continue
                }
                insertRecord(record)
                inserted++
            }
            inserted
        }
    }

    suspend fun replaceWorkoutRecord(record: DBWorkoutRecord) = withContext(Dispatchers.IO) {
        recordsDao.transaction {
            updateRecordRow(record.row)
            exercisesDao.deleteWorkoutExercisesByRecord(record.row.uuid)
            insertChildren(record)
        }
    }

    // Atomic "remove from superset" split. The removed exercise's rows are
    // deleted (replacing the source record's children) and reinserted under
    // the new record with their original uuids — as separate transactions, a
    // crash or concurrent sync push could observe (and upload) the exercise
    // existing in NEITHER record, i.e. permanent data loss. Sibling shifts
    // only touch the record row (pendingUpload=1), never their children.
    suspend fun splitWorkoutRecord(
        source: DBWorkoutRecord,
        newRecord: DBWorkoutRecord,
        shiftedUpdatedDate: Instant,
    ) = withContext(Dispatchers.IO) {
        recordsDao.transaction {
            updateRecordRow(source.row)
            exercisesDao.deleteWorkoutExercisesByRecord(source.row.uuid)
            insertChildren(source)
            // Records in the SAME workout after the source shift +1 for the
            // split-off record. Scoped to source.workoutNumber since position is
            // page-relative. Read INSIDE the transaction — a sync pull landing
            // between an outside read and the commit would write stale positions.
            recordsDao
                .getWorkoutRecordsByJournal(source.row.userId, source.row.journalId, EPOCH, FAR_FUTURE)
                .executeAsList()
                .map { it.map() }
                .filter {
                    it.date == source.row.date &&
                        it.workoutNumber == source.row.workoutNumber &&
                        it.uuid != source.row.uuid &&
                        it.position > source.row.position
                }
                .forEach { row ->
                    updateRecordRow(row.copy(position = row.position + 1, updatedDate = shiftedUpdatedDate))
                }
            insertRecord(newRecord)
        }
    }

    // Atomic superset merge — replaces the target record's children AND
    // tombstones the absorbed record in one transaction; as two separate
    // transactions, a crash between them left both live with the absorbed
    // exercises duplicated (and both queued for push).
    suspend fun mergeWorkoutRecords(
        merged: DBWorkoutRecord,
        tombstoneUuid: String,
        deletedAt: Instant = Clock.System.now(),
    ) = withContext(Dispatchers.IO) {
        recordsDao.transaction {
            updateRecordRow(merged.row)
            exercisesDao.deleteWorkoutExercisesByRecord(merged.row.uuid)
            insertChildren(merged)
            recordsDao.softDeleteWorkoutRecord(
                deletedAt = deletedAt.toStoredString(),
                updatedDate = deletedAt.toStoredString(),
                uuid = tombstoneUuid,
            )
        }
    }

    suspend fun softDeleteWorkoutRecord(
        uuid: String,
        deletedAt: Instant = Clock.System.now(),
        updatedDate: Instant = deletedAt,
    ) = withContext(Dispatchers.IO) {
        recordsDao.softDeleteWorkoutRecord(
            deletedAt = deletedAt.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    /**
     * Push ack. [updatedDate] must come from the tree that was actually uploaded
     * (push re-reads it before sending), not the enumerated snapshot — otherwise
     * a set logged mid-flight has its pendingUpload cleared, and the same tick's
     * pull rebuilds the tree from the server copy and the set is gone.
     */
    suspend fun markUploaded(uuid: String, remoteId: String, updatedDate: Instant) =
        withContext(Dispatchers.IO) {
            recordsDao.updateWorkoutRecordRemoteId(
                remoteId = remoteId,
                uuid = uuid,
                updatedDate = updatedDate.toStoredString(),
            )
        }

    suspend fun replaceWorkoutRecordFromRemote(record: DBWorkoutRecord) = withContext(Dispatchers.IO) {
        recordsDao.transaction {
            recordsDao.upsertWorkoutRecordFromRemote(
                uuid = record.row.uuid,
                remoteId = record.row.remoteId ?: record.row.uuid,
                userId = record.row.userId,
                journalId = record.row.journalId,
                date = record.row.date,
                position = record.row.position.toLong(),
                comment = record.row.comment,
                startedAt = record.row.startedAt?.toStoredString(),
                durationSec = record.row.durationSec?.toLong(),
                deletedAt = record.row.deletedAt?.toStoredString(),
                schemaVersion = record.row.schemaVersion.toLong(),
                createdDate = record.row.createdDate.toStoredString(),
                updatedDate = record.row.updatedDate.toStoredString(),
                workoutNumber = record.row.workoutNumber.toLong(),
            )
            insertChildren(record)
        }
    }

    // Orphan-reparent path: same upsert shape, but pendingUpload=1 so the
    // corrected journalId reaches AWS next tick. Caller already swapped
    // `record.row.journalId` to the personal journal.
    suspend fun replaceWorkoutRecordFromRemoteAsPending(record: DBWorkoutRecord) = withContext(Dispatchers.IO) {
        recordsDao.transaction {
            recordsDao.upsertWorkoutRecordFromRemoteAsPending(
                uuid = record.row.uuid,
                remoteId = record.row.remoteId ?: record.row.uuid,
                userId = record.row.userId,
                journalId = record.row.journalId,
                date = record.row.date,
                position = record.row.position.toLong(),
                comment = record.row.comment,
                startedAt = record.row.startedAt?.toStoredString(),
                durationSec = record.row.durationSec?.toLong(),
                deletedAt = record.row.deletedAt?.toStoredString(),
                schemaVersion = record.row.schemaVersion.toLong(),
                createdDate = record.row.createdDate.toStoredString(),
                updatedDate = record.row.updatedDate.toStoredString(),
                workoutNumber = record.row.workoutNumber.toLong(),
            )
            insertChildren(record)
        }
    }

    suspend fun deleteAllForUser(userId: String) = withContext(Dispatchers.IO) {
        recordsDao.transaction {
            recordsDao.deleteWorkoutRecordsByUserId(userId)
        }
    }

    private fun updateRecordRow(row: DBWorkoutRecordRow) {
        recordsDao.updateWorkoutRecord(
            position = row.position.toLong(),
            comment = row.comment,
            startedAt = row.startedAt?.toStoredString(),
            durationSec = row.durationSec?.toLong(),
            updatedDate = row.updatedDate.toStoredString(),
            uuid = row.uuid,
        )
    }

    private fun insertRecord(record: DBWorkoutRecord) {
        recordsDao.createWorkoutRecord(
            uuid = record.row.uuid,
            remoteId = record.row.remoteId,
            userId = record.row.userId,
            journalId = record.row.journalId,
            date = record.row.date,
            position = record.row.position.toLong(),
            comment = record.row.comment,
            startedAt = record.row.startedAt?.toStoredString(),
            durationSec = record.row.durationSec?.toLong(),
            pendingUpload = record.row.pendingUpload,
            createdDate = record.row.createdDate.toStoredString(),
            updatedDate = record.row.updatedDate.toStoredString(),
            workoutNumber = record.row.workoutNumber.toLong(),
        )
        insertChildren(record)
    }

    private fun insertChildren(record: DBWorkoutRecord) {
        for (exWithSets in record.exercises) {
            exercisesDao.createWorkoutExercise(
                uuid = exWithSets.exercise.uuid,
                workoutRecordUuid = record.row.uuid,
                exerciseUuid = exWithSets.exercise.exerciseUuid,
                position = exWithSets.exercise.position.toLong(),
                comment = exWithSets.exercise.comment,
            )
            for (set in exWithSets.sets) {
                setsDao.createWorkoutSet(
                    uuid = set.uuid,
                    workoutExerciseUuid = exWithSets.exercise.uuid,
                    position = set.position.toLong(),
                    weight = set.weight,
                    reps = set.reps?.toLong(),
                    distance = set.distance,
                    duration = set.duration?.toLong(),
                    completed = set.completed,
                )
            }
        }
    }
}
