package kz.maestrosultan.fitjournal.data.session.datasource

import app.cash.sqldelight.coroutines.asFlow
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
 * Local-only datasource for `workoutSessions` (no AWS counterpart in
 * iteration 1). `date` crosses this boundary as the stored TEXT form
 * (`LocalDate.toString()`) so the SQL layer never has to know about
 * calendar types.
 */
class WorkoutSessionsDBDataSource(
    private val dao: WorkoutSessionsQueries,
) {

    suspend fun getSession(
        userId: String,
        journalId: String,
        date: String,
    ): DBWorkoutSessionObject? = withContext(Dispatchers.IO) {
        dao.getSession(userId, journalId, date).executeAsOneOrNull()?.map()
    }

    fun getSessionFlow(
        userId: String,
        journalId: String,
        date: String,
    ): Flow<DBWorkoutSessionObject?> =
        dao.getSession(userId, journalId, date)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { row -> row?.map() }
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
     * All three steps run in ONE synchronous [app.cash.sqldelight.TransacterImpl.transactionWithResult]
     * block — no `suspend` call inside, because `NativeSqliteDriver` transactions
     * are bound to the calling thread. Ordering matters:
     *
     * 1. a row already exists for (userId, journalId, date) → return it
     *    **unchanged**, running or finished (decision 5: finished is final, and
     *    a double-start must not shift `startedAt`);
     * 2. otherwise a session is running on some other journal/day → finish it
     *    with the **true** `now` (decision 4: no cap, no truncation);
     * 3. insert the new running row and return it.
     *
     * Doing 2 and 3 in separate transactions could leave two running rows (crash
     * between them) or none (crash after a successful stale-finish), so they are
     * inseparable.
     */
    suspend fun startSession(
        uuid: String,
        userId: String,
        journalId: String,
        date: String,
        now: Instant,
    ): DBWorkoutSessionObject = withContext(Dispatchers.IO) {
        val nowText = now.toStoredString()
        dao.transactionWithResult {
            val existing = dao.getSession(userId, journalId, date).executeAsOneOrNull()
            if (existing != null) return@transactionWithResult existing.map()

            val running = dao.getRunningSession(userId).executeAsOneOrNull()
            if (running != null) {
                dao.endSessionById(endedAt = nowText, uuid = running.uuid)
            }
            dao.createSession(
                uuid = uuid,
                userId = userId,
                journalId = journalId,
                date = date,
                startedAt = nowText,
            )
            // Read-your-writes inside the same transaction/connection: the row
            // we just inserted is guaranteed visible, and the unique index on
            // (userId, journalId, date) guarantees it is the one we inserted.
            dao.getSession(userId, journalId, date).executeAsOne().map()
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
            dao.getSession(running.userId, running.journalId, running.date)
                .executeAsOneOrNull()
                ?.map()
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
