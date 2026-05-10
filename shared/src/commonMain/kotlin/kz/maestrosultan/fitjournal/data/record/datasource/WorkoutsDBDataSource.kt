package kz.maestrosultan.fitjournal.data.record.datasource

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.data.db.WorkoutExercisesQueries
import kz.maestrosultan.fitjournal.data.db.WorkoutRecordsQueries
import kz.maestrosultan.fitjournal.data.db.WorkoutSetsQueries
import kz.maestrosultan.fitjournal.data.time.toStoredString
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

    suspend fun getWorkoutRecordRow(uuid: String): DBWorkoutRecordRow? = withContext(Dispatchers.IO) {
        recordsDao.getWorkoutRecordById(uuid).executeAsOneOrNull()?.map()
    }

    suspend fun getWorkoutRecordRowsByDiary(
        userId: String,
        diaryId: String,
        from: String = EPOCH,
        to: String = FAR_FUTURE,
    ): List<DBWorkoutRecordRow> = withContext(Dispatchers.IO) {
        recordsDao
            .getWorkoutRecordsByDiary(userId, diaryId, from, to)
            .executeAsList()
            .map { it.map() }
    }

    suspend fun getPendingUploads(): List<DBWorkoutRecordRow> = withContext(Dispatchers.IO) {
        recordsDao.getPendingUploads().executeAsList().map { it.map() }
    }

    suspend fun getWorkoutRecord(uuid: String): DBWorkoutRecord? = withContext(Dispatchers.IO) {
        val row = recordsDao.getWorkoutRecordById(uuid).executeAsOneOrNull()?.map()
            ?: return@withContext null
        DBWorkoutRecord(row = row, exercises = childrenOf(uuid))
    }

    suspend fun getWorkoutRecordIncludingDeleted(uuid: String): DBWorkoutRecord? =
        withContext(Dispatchers.IO) {
            val row = recordsDao.getWorkoutRecordByIdIncludingDeleted(uuid)
                .executeAsOneOrNull()
                ?.map() ?: return@withContext null
            DBWorkoutRecord(row = row, exercises = childrenOf(uuid))
        }

    suspend fun getWorkoutRecordsByDiary(
        userId: String,
        diaryId: String,
        from: String = EPOCH,
        to: String = FAR_FUTURE,
    ): List<DBWorkoutRecord> = withContext(Dispatchers.IO) {
        val rows = recordsDao
            .getWorkoutRecordsByDiary(userId, diaryId, from, to)
            .executeAsList()
            .map { it.map() }
        if (rows.isEmpty()) return@withContext emptyList()
        val exercises: List<DBWorkoutExerciseObject> = exercisesDao
            .getWorkoutExercisesByDiary(userId, diaryId, from, to)
            .executeAsList()
            .map { it.map() }
        val sets: List<DBWorkoutSetObject> = setsDao
            .getWorkoutSetsByDiary(userId, diaryId, from, to)
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

    suspend fun getWorkoutExercisesByExerciseUuid(exerciseUuid: String): List<DBWorkoutExerciseObject> =
        withContext(Dispatchers.IO) {
            exercisesDao.getWorkoutExercisesByExerciseUuid(exerciseUuid)
                .executeAsList()
                .map { it.map() }
        }

    suspend fun getSetsForExerciseInDiary(
        exerciseUuid: String,
        userId: String,
        diaryId: String,
    ): List<DBWorkoutSetObject> = withContext(Dispatchers.IO) {
        setsDao
            .getWorkoutSetsForExerciseInDiary(exerciseUuid, userId, diaryId)
            .executeAsList()
            .map { it.map() }
    }

    suspend fun getLastSetForExerciseBeforeDate(
        exerciseUuid: String,
        userId: String,
        diaryId: String,
        beforeDateString: String,
    ): DBWorkoutSetObject? = withContext(Dispatchers.IO) {
        setsDao
            .getLastSetForExerciseBeforeDate(exerciseUuid, userId, diaryId, beforeDateString)
            .executeAsOneOrNull()
            ?.map()
    }

    private fun childrenOf(workoutRecordUuid: String): List<DBWorkoutExerciseWithSets> {
        val exercises = exercisesDao
            .getWorkoutExercisesByRecord(workoutRecordUuid)
            .executeAsList()
            .map { it.map() }
        return exercises.map { ex ->
            val sets = setsDao
                .getWorkoutSetsByExercise(ex.uuid)
                .executeAsList()
                .map { it.map() }
            DBWorkoutExerciseWithSets(exercise = ex, sets = sets)
        }
    }

    // ─── Writes ───────────────────────────────────────────────────────────

    suspend fun createWorkoutRecordIfMissing(record: DBWorkoutRecord): Boolean = withContext(Dispatchers.IO) {
        if (recordsDao.getWorkoutRecordByIdIncludingDeleted(record.row.uuid).executeAsOneOrNull() != null) {
            return@withContext false
        }
        recordsDao.transactionWithResult {
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
            recordsDao.updateWorkoutRecord(
                position = record.row.position.toLong(),
                comment = record.row.comment,
                startedAt = record.row.startedAt?.toStoredString(),
                durationSec = record.row.durationSec?.toLong(),
                updatedDate = record.row.updatedDate.toStoredString(),
                uuid = record.row.uuid,
            )
            exercisesDao.deleteWorkoutExercisesByRecord(record.row.uuid)
            insertChildren(record)
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

    suspend fun markUploaded(uuid: String, remoteId: String) = withContext(Dispatchers.IO) {
        recordsDao.updateWorkoutRecordRemoteId(remoteId = remoteId, uuid = uuid)
    }

    suspend fun replaceWorkoutRecordFromRemote(record: DBWorkoutRecord) = withContext(Dispatchers.IO) {
        recordsDao.transaction {
            recordsDao.upsertWorkoutRecordFromRemote(
                uuid = record.row.uuid,
                remoteId = record.row.remoteId ?: record.row.uuid,
                userId = record.row.userId,
                diaryId = record.row.diaryId,
                date = record.row.date,
                position = record.row.position.toLong(),
                comment = record.row.comment,
                startedAt = record.row.startedAt?.toStoredString(),
                durationSec = record.row.durationSec?.toLong(),
                deletedAt = record.row.deletedAt?.toStoredString(),
                schemaVersion = record.row.schemaVersion.toLong(),
                createdDate = record.row.createdDate.toStoredString(),
                updatedDate = record.row.updatedDate.toStoredString(),
            )
            insertChildren(record)
        }
    }

    suspend fun deleteAllForUser(userId: String) = withContext(Dispatchers.IO) {
        recordsDao.transaction {
            recordsDao.deleteWorkoutRecordsByUserId(userId)
        }
    }

    private fun insertRecord(record: DBWorkoutRecord) {
        recordsDao.createWorkoutRecord(
            uuid = record.row.uuid,
            remoteId = record.row.remoteId,
            userId = record.row.userId,
            diaryId = record.row.diaryId,
            date = record.row.date,
            position = record.row.position.toLong(),
            comment = record.row.comment,
            startedAt = record.row.startedAt?.toStoredString(),
            durationSec = record.row.durationSec?.toLong(),
            pendingUpload = record.row.pendingUpload,
            createdDate = record.row.createdDate.toStoredString(),
            updatedDate = record.row.updatedDate.toStoredString(),
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
                    difficultyType = set.difficultyType.toLong(),
                    completed = set.completed,
                )
            }
        }
    }
}
