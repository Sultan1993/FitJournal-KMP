package kz.maestrosultan.fitjournal.data.notes.datasource

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
import kz.maestrosultan.fitjournal.data.db.NotesQueries
import kz.maestrosultan.fitjournal.data.notes.entity.DBNoteObject
import kz.maestrosultan.fitjournal.data.notes.entity.map
import kz.maestrosultan.fitjournal.data.time.toStoredString

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

    suspend fun getPinnedNotes(userId: String): List<DBNoteObject> = withContext(Dispatchers.IO) {
        dao.getPinnedNotes(userId)
            .executeAsList()
            .map { it.map() }
    }

    fun getPinnedNotesFlow(userId: String): Flow<List<DBNoteObject>> {
        return dao.getPinnedNotes(userId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
            .flowOn(Dispatchers.IO)
    }

    /**
     * UI-facing single-row read; null if missing or soft-deleted — a note
     * tombstoned by sync from another device looks like "not found" to the
     * UI, the right semantic. Sync paths needing tombstones use
     * [getNoteByIdIncludingDeleted].
     */
    suspend fun getNoteById(uuid: String): DBNoteObject? = withContext(Dispatchers.IO) {
        dao.getNoteById(uuid).executeAsOneOrNull()?.map()
    }

    /**
     * Sync-only: sees soft-deleted rows so the orchestrator can compare
     * local vs remote `deletedAt` on pull and propagate tombstones to
     * AWS on push. UI code must use [getNoteById].
     */
    suspend fun getNoteByIdIncludingDeleted(uuid: String): DBNoteObject? = withContext(Dispatchers.IO) {
        dao.getNoteByIdIncludingDeleted(uuid).executeAsOneOrNull()?.map()
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
    ) = withContext(Dispatchers.IO) {
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
    }

    suspend fun updateNote(
        uuid: String,
        text: String,
        isPinned: Boolean,
        updatedDate: Instant = Clock.System.now(),
    ) = withContext(Dispatchers.IO) {
        dao.updateNote(
            text = text,
            isPinned = isPinned,
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    suspend fun getPendingUploads(userId: String): List<DBNoteObject> = withContext(Dispatchers.IO) {
        dao.getPendingUploads(userId).executeAsList().map { it.map() }
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
     * via the `deletedAt IS NULL` predicate).
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

    suspend fun deleteUserNotes(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteUserNotes(userId)
    }
}
