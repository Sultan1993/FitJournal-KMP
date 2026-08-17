package kz.maestrosultan.fitjournal.data.record.datasource

import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.data.db.WorkoutNotesQueries
import kz.maestrosultan.fitjournal.data.record.entity.DBWorkoutNoteObject
import kz.maestrosultan.fitjournal.data.record.entity.map
import kz.maestrosultan.fitjournal.data.time.toStoredString

/**
 * Sync-side accessor for `workoutNotes`, used by SyncOrchestrator only — the
 * read/write path the UI uses runs through `RecordRepository` (which owns the
 * page semantics: revive vs insert, tombstone when the text is cleared).
 */
class WorkoutNotesDBDataSource(
    private val dao: WorkoutNotesQueries,
) {

    /** Unpushed rows for the signed-in user — tombstones included. */
    suspend fun getPendingUploads(userId: String): List<DBWorkoutNoteObject> =
        withContext(Dispatchers.IO) {
            dao.getPendingUploads(userId).executeAsList().map { it.map() }
        }

    /**
     * Push ack: stamp the remote id and clear the pending flag — but only if the
     * row still holds what was uploaded. Pass the SNAPSHOT that was pushed (the
     * object [getPendingUploads] returned), not the current row: an edit landing
     * during the network round trip must leave the row pending so the next tick
     * pushes it, instead of being dropped and then reverted by the pull.
     */
    suspend fun markUploaded(note: DBWorkoutNoteObject, remoteId: String) = withContext(Dispatchers.IO) {
        dao.updateWorkoutNoteRemoteId(
            remoteId = remoteId,
            uuid = note.uuid,
            comment = note.comment,
            deletedAt = note.deletedAt?.toStoredString(),
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
        comment: String,
        deletedAt: Instant?,
    ): Boolean = withContext(Dispatchers.IO) {
        val workoutNumberLong = workoutNumber.toLong()
        dao.transactionWithResult {
            val pendingLocally = dao
                .getNotePendingByPage(userId, journalId, date, workoutNumberLong)
                .executeAsOneOrNull()
            if (pendingLocally == 1L) return@transactionWithResult false
            dao.upsertNoteFromRemote(
                uuid = uuid,
                remoteId = remoteId,
                userId = userId,
                journalId = journalId,
                date = date,
                workoutNumber = workoutNumberLong,
                comment = comment,
                deletedAt = deletedAt?.toStoredString(),
            )
            true
        }
    }
}
