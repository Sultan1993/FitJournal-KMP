package kz.maestrosultan.fitjournal.data.notes.repository

import kz.maestrosultan.fitjournal.domain.notes.NotesRepository

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kz.maestrosultan.fitjournal.domain.notes.Note
import kz.maestrosultan.fitjournal.data.notes.datasource.NotesDBDataSource
import kz.maestrosultan.fitjournal.data.notes.entity.DBNoteObject

class DefaultNotesRepository(
    private val localDataSource: NotesDBDataSource,
) : NotesRepository {

    override suspend fun getNotes(userId: String): List<Note> =
        localDataSource.getNotes(userId).map { it.toDomain() }

    override fun getNotesFlow(userId: String): Flow<List<Note>> =
        localDataSource.getNotesFlow(userId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getNoteById(uuid: String): Note =
        localDataSource.getNoteById(uuid).toDomain()

    override fun getNoteByIdFlow(uuid: String): Flow<Note> =
        localDataSource.getNoteByIdFlow(uuid).map { it.toDomain() }

    override suspend fun createNote(uuid: String, userId: String, text: String, isPinned: Boolean) {
        val now = Clock.System.now()
        localDataSource.createNote(
            uuid = uuid,
            userId = userId,
            text = text,
            isPinned = isPinned,
            createdDate = now,
            updatedDate = now,
            remoteId = null,
            pendingUpload = true,
        )
    }

    override suspend fun updateNote(uuid: String, text: String, isPinned: Boolean) {
        localDataSource.updateNote(
            uuid = uuid,
            text = text,
            isPinned = isPinned,
            updatedDate = Clock.System.now(),
        )
    }

    override suspend fun deleteNote(uuid: String) {
        // Soft delete: tombstone + pendingUpload=1 so the SyncOrchestrator
        // propagates the deletion to AWS on its next tick. Hard delete would
        // strand the AWS row alive forever.
        localDataSource.softDeleteNote(uuid)
    }

    override suspend fun deleteUserNotes(userId: String) {
        localDataSource.deleteUserNotes(userId)
    }
}

private fun DBNoteObject.toDomain(): Note = Note(
    id = uuid,
    userId = userId,
    text = text,
    isPinned = isPinned,
    createdDate = createdDate,
    updatedDate = updatedDate,
)
