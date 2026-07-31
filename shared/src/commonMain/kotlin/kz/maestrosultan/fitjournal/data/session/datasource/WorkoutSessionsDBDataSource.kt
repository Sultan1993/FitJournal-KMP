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
 * Local-only datasource for `workoutSessions` (no AWS sync in this increment).
 * `date` crosses this boundary as the stored TEXT form (`LocalDate.toString()`)
 * so the SQL layer never has to know about calendar types. `workoutNumber`
 * crosses as `Int` and is widened to the column's `Long` at the query call.
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
     * Idempotent start, and the enforcement point for "at most one running
     * session per user app-wide".
     *
     * All steps run in ONE synchronous
     * [app.cash.sqldelight.TransacterImpl.transactionWithResult] block — no
     * `suspend` call inside, because `NativeSqliteDriver` transactions are bound
     * to the calling thread. Ordering matters:
     *
     * 1. this page (userId, journalId, date, workoutNumber) already has a session
     *    -> return it UNCHANGED, running or finished (a double-start must not
     *    shift `startedAt`, and a finished workout stays finished);
     * 2. else a DIFFERENT workout is running app-wide -> BLOCKED: return that
     *    running row without inserting. Unlike the old single-session rule this
     *    does NOT auto-finish it — one running workout at a time, ended
     *    explicitly by the user;
     * 3. else insert the new running row and read it back.
     *
     * One transaction so a crash can't leave two running rows. The UNIQUE
     * (userId, journalId, date, workoutNumber) index is the backstop: step 1
     * already returned any existing row, so the insert can never collide.
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

            dao.createSession(
                uuid = uuid,
                userId = userId,
                journalId = journalId,
                date = date,
                workoutNumber = workoutNumberLong,
                startedAt = nowText,
            )
            // Read-your-writes inside the same transaction/connection.
            dao.getSessionById(uuid).executeAsOne().map()
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
}
