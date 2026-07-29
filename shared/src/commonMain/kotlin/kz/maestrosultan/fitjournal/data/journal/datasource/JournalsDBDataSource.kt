package kz.maestrosultan.fitjournal.data.journal.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.data.db.BodyMeasurementsQueries
import kz.maestrosultan.fitjournal.data.db.JournalsQueries
import kz.maestrosultan.fitjournal.data.db.WorkoutRecordsQueries
import kz.maestrosultan.fitjournal.data.journal.entity.DBJournalObject
import kz.maestrosultan.fitjournal.data.journal.entity.map
import kz.maestrosultan.fitjournal.data.time.toStoredString

/**
 * @param recordsDao / @param measurementsDao are here only for
 * [softDeleteJournalCascade]. A journal's children live in other tables with no
 * FOREIGN KEY back to `journals`, so the cascade has to be written by hand — and
 * it has to be one transaction, which means one owner holding all three Queries.
 */
class JournalsDBDataSource(
    private val dao: JournalsQueries,
    private val recordsDao: WorkoutRecordsQueries,
    private val measurementsDao: BodyMeasurementsQueries,
) {

    suspend fun getJournals(userId: String): List<DBJournalObject> = withContext(Dispatchers.IO) {
        dao.getJournals(userId).executeAsList().map { it.map() }
    }

    fun getJournalsFlow(userId: String): Flow<List<DBJournalObject>> =
        dao.getJournals(userId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.map() } }
            .flowOn(Dispatchers.IO)

    /**
     * UI-facing single-row read. Null when missing or tombstoned.
     * Sync paths needing to see tombstones use [getJournalByIdIncludingDeleted].
     */
    suspend fun getJournalById(uuid: String): DBJournalObject? = withContext(Dispatchers.IO) {
        dao.getJournalById(uuid).executeAsOneOrNull()?.map()
    }

    /**
     * Sync-only: sees tombstones for the local-wins guard.
     */
    suspend fun getJournalByIdIncludingDeleted(uuid: String): DBJournalObject? = withContext(Dispatchers.IO) {
        dao.getJournalByIdIncludingDeleted(uuid).executeAsOneOrNull()?.map()
    }

    suspend fun getPersonalJournal(userId: String): DBJournalObject? = withContext(Dispatchers.IO) {
        dao.getPersonalJournal(userId).executeAsOneOrNull()?.map()
    }

    suspend fun getPendingUploads(userId: String): List<DBJournalObject> = withContext(Dispatchers.IO) {
        dao.getPendingUploads(userId).executeAsList().map { it.map() }
    }

    suspend fun createJournal(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int? = null,
        remoteId: String? = null,
        pendingUpload: Boolean = true,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
    ) = withContext(Dispatchers.IO) {
        dao.createJournal(
            uuid = uuid,
            remoteId = remoteId,
            userId = userId,
            name = name,
            comments = comments,
            isPersonal = isPersonal,
            workoutGoal = workoutGoal?.toLong(),
            pendingUpload = pendingUpload,
            createdDate = createdDate.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
        )
    }

    suspend fun createJournalIfMissing(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int? = null,
        remoteId: String? = null,
        pendingUpload: Boolean = true,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
    ): Boolean = withContext(Dispatchers.IO) {
        // Atomic check-and-insert to prevent TOCTOU races during migration.
        dao.transactionWithResult {
            if (dao.getJournalById(uuid).executeAsOneOrNull() != null) return@transactionWithResult false
            dao.createJournal(
                uuid = uuid,
                remoteId = remoteId,
                userId = userId,
                name = name,
                comments = comments,
                isPersonal = isPersonal,
                workoutGoal = workoutGoal?.toLong(),
                pendingUpload = pendingUpload,
                createdDate = createdDate.toStoredString(),
                updatedDate = updatedDate.toStoredString(),
            )
            true
        }
    }

    /**
     * Atomic "select-or-insert" for the user's personal journal. Two concurrent
     * callers (e.g. a double first-boot `ensureDefaultJournal`) can't each insert
     * a personal journal with a *different* uuid: the transaction serialises
     * against other writers, so the second caller observes the first's row and
     * returns it instead of inserting. Guarantees at most one live personal
     * journal per user without a schema-level unique constraint.
     */
    suspend fun getOrCreatePersonalJournal(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        workoutGoal: Int? = null,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
    ): DBJournalObject = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            val existing = dao.getPersonalJournal(userId).executeAsOneOrNull()
            if (existing != null) {
                existing.map()
            } else {
                dao.createJournal(
                    uuid = uuid,
                    remoteId = null,
                    userId = userId,
                    name = name,
                    comments = comments,
                    isPersonal = true,
                    workoutGoal = workoutGoal?.toLong(),
                    pendingUpload = true,
                    createdDate = createdDate.toStoredString(),
                    updatedDate = updatedDate.toStoredString(),
                )
                dao.getPersonalJournal(userId).executeAsOne().map()
            }
        }
    }

    suspend fun updateJournal(
        uuid: String,
        name: String,
        comments: String?,
        workoutGoal: Int? = null,
        updatedDate: Instant = Clock.System.now(),
    ) = withContext(Dispatchers.IO) {
        dao.updateJournal(
            name = name,
            comments = comments,
            workoutGoal = workoutGoal?.toLong(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    suspend fun softDeleteJournal(
        uuid: String,
        deletedAt: Instant = Clock.System.now(),
        updatedDate: Instant = deletedAt,
    ) = withContext(Dispatchers.IO) {
        dao.softDeleteJournal(
            deletedAt = deletedAt.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    /**
     * Soft-delete a journal AND everything scoped to it, atomically.
     *
     * Deleting the journal row alone is not enough: `workoutRecords` and
     * `bodyMeasurements` carry `journalId` with no FK, so the children outlive
     * the parent. That is not merely untidy — the sync pull treats a tombstoned
     * parent as invalid and reparents surviving workout records into the
     * personal journal, so a deleted journal's workouts reappear there on the
     * next device to sync. Measurements have no reparent path and just become
     * unreachable.
     *
     * One transaction because a partial cascade has no retry: the journal row
     * would already be tombstoned, so [softDeleteJournal]'s `deletedAt IS NULL`
     * predicate makes a second attempt a no-op and the live children are
     * stranded permanently. Every statement stamps `pendingUpload = 1` so the
     * tombstones reach AWS on the next tick.
     */
    suspend fun softDeleteJournalCascade(
        uuid: String,
        userId: String,
        deletedAt: Instant = Clock.System.now(),
        updatedDate: Instant = deletedAt,
    ) = withContext(Dispatchers.IO) {
        val deletedAtText = deletedAt.toStoredString()
        val updatedDateText = updatedDate.toStoredString()
        // Transactions are per-connection in SQLDelight, so opening on one
        // Queries object covers statements issued through the others.
        dao.transaction {
            recordsDao.softDeleteWorkoutRecordsByJournal(
                deletedAt = deletedAtText,
                updatedDate = updatedDateText,
                userId = userId,
                journalId = uuid,
            )
            measurementsDao.softDeleteBodyMeasurementsByJournal(
                deletedAt = deletedAtText,
                updatedDate = updatedDateText,
                userId = userId,
                journalId = uuid,
            )
            dao.softDeleteJournal(
                deletedAt = deletedAtText,
                updatedDate = updatedDateText,
                uuid = uuid,
            )
        }
    }

    suspend fun softDeleteJournalsByUserId(
        userId: String,
        deletedAt: Instant = Clock.System.now(),
        updatedDate: Instant = deletedAt,
    ) = withContext(Dispatchers.IO) {
        dao.softDeleteJournalsByUserId(
            deletedAt = deletedAt.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            userId = userId,
        )
    }

    suspend fun markUploaded(uuid: String, remoteId: String) = withContext(Dispatchers.IO) {
        dao.updateJournalRemoteId(remoteId = remoteId, uuid = uuid)
    }

    suspend fun upsertFromRemote(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int?,
        deletedAt: Instant?,
        createdDate: Instant,
        updatedDate: Instant,
        remoteId: String? = uuid,
    ) = withContext(Dispatchers.IO) {
        dao.upsertJournalFromRemote(
            uuid = uuid,
            remoteId = remoteId,
            userId = userId,
            name = name,
            comments = comments,
            isPersonal = isPersonal,
            workoutGoal = workoutGoal?.toLong(),
            deletedAt = deletedAt?.toStoredString(),
            createdDate = createdDate.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
        )
    }

}
