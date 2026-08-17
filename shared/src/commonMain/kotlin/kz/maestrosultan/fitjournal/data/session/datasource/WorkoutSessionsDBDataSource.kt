package kz.maestrosultan.fitjournal.data.session.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.data.db.WorkoutSessionsQueries
import kz.maestrosultan.fitjournal.data.session.entity.DBWorkoutSessionObject
import kz.maestrosultan.fitjournal.data.session.entity.map
import kz.maestrosultan.fitjournal.data.time.toStoredString

/**
 * Datasource for `workoutSessions`. `date` crosses this boundary as the stored
 * TEXT form (`LocalDate.toString()`) so the SQL layer never has to know about
 * calendar types. `workoutNumber` crosses as `Int` and is widened to the
 * column's `Long` at the query call.
 *
 * Rows sync to `AWSWorkoutSession`: every write leaves `pendingUpload = 1` for
 * SyncOrchestrator to drain, and discards are tombstones — see the sync block
 * at the bottom.
 */
class WorkoutSessionsDBDataSource(
    private val dao: WorkoutSessionsQueries,
) {

    suspend fun getSessionByWorkoutNumber(
        userId: String,
        journalId: String,
        date: String,
        workoutNumber: Int,
    ): DBWorkoutSessionObject? = withContext(Dispatchers.IO) {
        dao.getSessionByWorkoutNumber(userId, journalId, date, workoutNumber.toLong())
            .executeAsOneOrNull()?.map()
    }

    suspend fun getSessionsForDay(
        userId: String,
        journalId: String,
        date: String,
    ): List<DBWorkoutSessionObject> = withContext(Dispatchers.IO) {
        dao.getSessionsForDay(userId, journalId, date).executeAsList().map { it.map() }
    }

    fun getSessionsForDayFlow(
        userId: String,
        journalId: String,
        date: String,
    ): Flow<List<DBWorkoutSessionObject>> =
        dao.getSessionsForDay(userId, journalId, date)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.map() } }
            .flowOn(Dispatchers.IO)

    suspend fun getRunningSession(userId: String): DBWorkoutSessionObject? = withContext(Dispatchers.IO) {
        dao.getRunningSession(userId).executeAsOneOrNull()?.map()
    }

    fun getRunningSessionFlow(userId: String): Flow<DBWorkoutSessionObject?> =
        dao.getRunningSession(userId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { row -> row?.map() }
            .flowOn(Dispatchers.IO)

    /**
     * Completed (`endedAt IS NOT NULL`) sessions in (user, journal) within the
     * INCLUSIVE `[from, to]` date range (stored TEXT form), excluding
     * [excludeSessionUuid]. COUNT-only — no rows are materialized.
     */
    suspend fun countCompletedSessionsBetween(
        userId: String,
        journalId: String,
        from: String,
        to: String,
        excludeSessionUuid: String,
    ): Int = withContext(Dispatchers.IO) {
        dao.countCompletedSessionsBetween(userId, journalId, from, to, excludeSessionUuid)
            .executeAsOne()
            .toInt()
    }

    /**
     * Idempotent start, and the enforcement point for "at most one running
     * session per user app-wide".
     *
     * All steps run in ONE synchronous
     * [app.cash.sqldelight.TransacterImpl.transactionWithResult] block — no
     * `suspend` call inside, since `NativeSqliteDriver` transactions are bound
     * to the calling thread. Ordering matters:
     *
     * 1. this page already has a LIVE session -> return it UNCHANGED (a
     *    double-start must not shift `startedAt`, a finished workout stays
     *    finished);
     * 2. else a DIFFERENT workout is running app-wide -> BLOCKED: return that
     *    running row without inserting or auto-finishing it — one running
     *    workout at a time, ended explicitly by the user;
     * 3. else start the page: revive its tombstone if one exists (the page
     *    index is unconditional, so a discarded workout's row still occupies
     *    the page), otherwise insert a new running row. Read it back either way.
     *
     * One transaction so a crash can't leave two running rows; the UNIQUE
     * (userId, journalId, date, workoutNumber) index is the backstop.
     */
    suspend fun startSession(
        uuid: String,
        userId: String,
        journalId: String,
        date: String,
        workoutNumber: Int,
        now: Instant,
    ): DBWorkoutSessionObject = withContext(Dispatchers.IO) {
        val nowText = now.toStoredString()
        val workoutNumberLong = workoutNumber.toLong()
        dao.transactionWithResult {
            val existing = dao.getSessionByWorkoutNumber(userId, journalId, date, workoutNumberLong)
                .executeAsOneOrNull()
            if (existing != null) return@transactionWithResult existing.map()

            val running = dao.getRunningSession(userId).executeAsOneOrNull()
            if (running != null) return@transactionWithResult running.map()

            val tombstoned = dao.getSessionUuidByPage(userId, journalId, date, workoutNumberLong)
                .executeAsOneOrNull()
            val liveUuid = if (tombstoned != null) {
                dao.reviveSessionByPage(
                    startedAt = nowText,
                    userId = userId,
                    journalId = journalId,
                    date = date,
                    workoutNumber = workoutNumberLong,
                )
                tombstoned
            } else {
                dao.createSession(
                    uuid = uuid,
                    userId = userId,
                    journalId = journalId,
                    date = date,
                    workoutNumber = workoutNumberLong,
                    startedAt = nowText,
                )
                uuid
            }
            // Read-your-writes inside the same transaction/connection.
            dao.getSessionById(liveUuid).executeAsOne().map()
        }
    }

    /**
     * Stamps `endedAt = now` on the user's running session and returns the
     * finished row, or `null` when nothing was running (a no-op, never an error
     * — see the SIGABRT rule in the repository KDoc). Read + update are one
     * transaction so a concurrent end can't double-stamp: `endSessionById`
     * matches `endedAt IS NULL` only.
     */
    suspend fun endRunningSession(
        userId: String,
        now: Instant,
    ): DBWorkoutSessionObject? = withContext(Dispatchers.IO) {
        val nowText = now.toStoredString()
        dao.transactionWithResult<DBWorkoutSessionObject?> {
            val running = dao.getRunningSession(userId).executeAsOneOrNull()
                ?: return@transactionWithResult null
            dao.endSessionById(endedAt = nowText, uuid = running.uuid)
            dao.getSessionById(running.uuid).executeAsOneOrNull()?.map()
        }
    }

    /**
     * Discard one session by id (empty-workout cleanup); userId-scoped
     * defensively. Tombstone, not DELETE — the removal has to reach AWS, and a
     * hard-deleted row would be resurrected by the next pull.
     */
    suspend fun deleteByUuid(uuid: String, userId: String, now: Instant) =
        withContext(Dispatchers.IO) {
            dao.softDeleteWorkoutSessionByUuid(now.toStoredString(), uuid, userId)
            Unit
        }

    /** Hard purge for the delete-account flow. */
    suspend fun deleteByUserId(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteWorkoutSessionsByUserId(userId)
        Unit
    }

    /** FJ2.0 wipe-gate parity — see `DefaultLocalDbWiper`. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        dao.wipeAll()
        Unit
    }

    // ─── Sync (SyncOrchestrator only) ─────────────────────────────────────

    /** Unpushed rows for the signed-in user — tombstones included. */
    suspend fun getPendingUploads(userId: String): List<DBWorkoutSessionObject> =
        withContext(Dispatchers.IO) {
            dao.getPendingUploads(userId).executeAsList().map { it.map() }
        }

    /**
     * Push ack: stamp the remote id and clear the pending flag — but only if the
     * row still holds what was uploaded. Pass the SNAPSHOT that was pushed (the
     * object [getPendingUploads] returned), not the current row: an End or a
     * discard landing during the network round trip must leave the row pending
     * so the next tick pushes it, instead of being dropped and then reverted by
     * the pull. A no-op ack simply leaves the row pending.
     */
    suspend fun markUploaded(session: DBWorkoutSessionObject, remoteId: String) =
        withContext(Dispatchers.IO) {
            dao.updateWorkoutSessionRemoteId(
                remoteId = remoteId,
                uuid = session.uuid,
                startedAt = session.startedAt.toStoredString(),
                endedAt = session.endedAt?.toStoredString(),
                deletedAt = session.deletedAt?.toStoredString(),
            )
            Unit
        }

    /**
     * Apply one pulled row, unless the local row for that page has unpushed
     * writes (local wins; the caller still advances its cursor). Returns true
     * when the row was written. [date] is the stored TEXT form, like everywhere
     * else on this boundary — the platform pull hands over the AWS string as-is.
     */
    suspend fun upsertFromRemote(
        uuid: String,
        remoteId: String,
        userId: String,
        journalId: String,
        date: String,
        workoutNumber: Int,
        startedAt: Instant,
        endedAt: Instant?,
        deletedAt: Instant?,
    ): Boolean = withContext(Dispatchers.IO) {
        val workoutNumberLong = workoutNumber.toLong()
        dao.transactionWithResult {
            val pendingLocally = dao
                .getSessionPendingByPage(userId, journalId, date, workoutNumberLong)
                .executeAsOneOrNull()
            if (pendingLocally == 1L) return@transactionWithResult false
            // A running row from another device means the user moved on — finish
            // any other still-running row so "one running workout app-wide"
            // survives the pull (see finishOtherRunningSessions).
            if (endedAt == null && deletedAt == null) {
                dao.finishOtherRunningSessions(
                    endedAt = startedAt.toStoredString(),
                    userId = userId,
                    uuid = uuid,
                )
            }
            dao.upsertSessionFromRemote(
                uuid = uuid,
                remoteId = remoteId,
                userId = userId,
                journalId = journalId,
                date = date,
                workoutNumber = workoutNumberLong,
                startedAt = startedAt.toStoredString(),
                endedAt = endedAt?.toStoredString(),
                deletedAt = deletedAt?.toStoredString(),
            )
            true
        }
    }
}
