package kz.maestrosultan.fitjournal.kmp.notes.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.kmp.NotesQueries
import kz.maestrosultan.fitjournal.kmp.notes.entity.DBNoteObject
import kz.maestrosultan.fitjournal.kmp.notes.entity.map
import kz.maestrosultan.fitjournal.kmp.time.toStoredString

class NotesDBDataSource(private val dao: NotesQueries) {

    suspend fun getNotes(userId: String): List<DBNoteObject> = withContext(Dispatchers.IO) {
        dao.getNotes(userId)
            .executeAsList()
            .map { it.map() }
    }

    fun getNotesFlow(userId: String): Flow<List<DBNoteObject>> {
        return dao.getNotes(userId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getNoteById(uuid: String): DBNoteObject = withContext(Dispatchers.IO) {
        dao.getNoteById(uuid).executeAsOne().map()
    }

    fun getNoteByIdFlow(uuid: String): Flow<DBNoteObject> {
        return dao.getNoteById(uuid)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.map() }
            .flowOn(Dispatchers.IO)
    }

    suspend fun createNote(
        uuid: String,
        userId: String,
        text: String,
        isPinned: Boolean,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
        remoteId: String? = null,
        pendingUpload: Boolean = false,
    ): DBNoteObject = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            dao.createNote(
                uuid = uuid,
                userId = userId,
                text = text,
                isPinned = isPinned,
                createdDate = createdDate.toStoredString(),
                updatedDate = updatedDate.toStoredString(),
                remoteId = remoteId,
                pendingUpload = pendingUpload,
            )
            dao.getNoteById(uuid).executeAsOne().map()
        }
    }

    /**
     * Insert if uuid not already present. Returns true on insert, false on
     * skip. Used by `DefaultNotesMigrator` so re-running after a partial
     * crash doesn't duplicate. New rows ship with `pendingUpload=true` so
     * SyncOrchestrator pushes them on its next tick.
     */
    suspend fun createNoteIfMissing(
        uuid: String,
        userId: String,
        text: String,
        isPinned: Boolean,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
        remoteId: String? = null,
        pendingUpload: Boolean = true,
    ): Boolean = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            if (dao.getNoteById(uuid).executeAsOneOrNull() != null) {
                return@transactionWithResult false
            }
            dao.createNote(
                uuid = uuid,
                userId = userId,
                text = text,
                isPinned = isPinned,
                createdDate = createdDate.toStoredString(),
                updatedDate = updatedDate.toStoredString(),
                remoteId = remoteId,
                pendingUpload = pendingUpload,
            )
            true
        }
    }

    suspend fun updateNote(
        uuid: String,
        text: String,
        isPinned: Boolean,
        updatedDate: Instant = Clock.System.now(),
    ): DBNoteObject = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            dao.updateNote(
                text = text,
                isPinned = isPinned,
                updatedDate = updatedDate.toStoredString(),
                uuid = uuid,
            )
            dao.getNoteById(uuid).executeAsOne().map()
        }
    }

    suspend fun getNoteByIdOrNull(uuid: String): DBNoteObject? = withContext(Dispatchers.IO) {
        dao.getNoteById(uuid).executeAsOneOrNull()?.map()
    }

    suspend fun getPendingUploads(): List<DBNoteObject> = withContext(Dispatchers.IO) {
        dao.getPendingUploads().executeAsList().map { it.map() }
    }

    suspend fun markUploaded(uuid: String, remoteId: String) = withContext(Dispatchers.IO) {
        dao.updateNoteRemoteId(remoteId = remoteId, uuid = uuid)
    }

    /**
     * Apply a row pulled from AWS, clearing pendingUpload. Caller must
     * already have decided not to skip (i.e. local row's pendingUpload is 0
     * OR remote `updatedAt` newer than local). The remote-wins / LWW
     * semantics live in the SyncOrchestrator pull path; this method just
     * persists.
     */
    suspend fun upsertFromRemote(
        uuid: String,
        userId: String,
        text: String,
        isPinned: Boolean,
        createdDate: Instant,
        updatedDate: Instant,
        remoteId: String? = uuid,
        deletedAt: Instant? = null,
    ) = withContext(Dispatchers.IO) {
        dao.upsertNoteFromRemote(
            uuid = uuid,
            userId = userId,
            text = text,
            isPinned = isPinned,
            createdDate = createdDate.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            remoteId = remoteId,
            deletedAt = deletedAt?.toStoredString(),
        )
    }

    /**
     * Soft delete: stamps `deletedAt`, bumps `updatedDate`, and flips
     * `pendingUpload=1` so the SyncOrchestrator pushes the tombstone to AWS
     * on its next tick. The row stays in SQLite (filtered out by `getNotes`
     * and `getNoteByIdFlow` via the `deletedAt IS NULL` predicates).
     */
    suspend fun softDeleteNote(
        uuid: String,
        deletedAt: Instant = Clock.System.now(),
        updatedDate: Instant = deletedAt,
    ) = withContext(Dispatchers.IO) {
        dao.softDeleteNote(
            deletedAt = deletedAt.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    suspend fun deleteNote(uuid: String) = withContext(Dispatchers.IO) {
        dao.deleteNote(uuid)
    }

    suspend fun deleteUserNotes(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteUserNotes(userId)
    }

    suspend fun deleteAllNotes() = withContext(Dispatchers.IO) {
        dao.deleteAllNotes()
    }
}
