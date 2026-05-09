package kz.maestrosultan.fitjournal.data.notes

import kotlinx.coroutines.flow.Flow
import kz.maestrosultan.fitjournal.domain.notes.Note

interface NotesRepository {

    suspend fun getNotes(userId: String): List<Note>
    fun getNotesFlow(userId: String): Flow<List<Note>>

    suspend fun getNoteById(uuid: String): Note
    fun getNoteByIdFlow(uuid: String): Flow<Note>

    suspend fun createNote(uuid: String, userId: String, text: String, isPinned: Boolean)

    suspend fun updateNote(uuid: String, text: String, isPinned: Boolean)

    suspend fun deleteNote(uuid: String)

    suspend fun deleteUserNotes(userId: String)
}
