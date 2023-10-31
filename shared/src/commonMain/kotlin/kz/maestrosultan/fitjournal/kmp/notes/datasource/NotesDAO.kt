package kz.maestrosultan.fitjournal.kmp.notes.datasource

import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.kmp.FitJournalDatabaseQueries
import kz.maestrosultan.fitjournal.kmp.Notes
import kz.maestrosultan.fitjournal.kmp.notes.entity.DBNoteObject

class NotesDBDataSource(private val dao: FitJournalDatabaseQueries) {

    fun getNotes(userId: String): List<DBNoteObject> {
        return dao.getNotes(userId).executeAsList().map { it.map() }
    }

    fun getNoteById(uuid: String): DBNoteObject {
        return dao.getNoteById(uuid).executeAsOne().map()
    }

    fun createNote(
        uuid: String,
        back4AppId: String?,
        userId: String,
        text: String,
        isPinned: Boolean,
        isSynced: Boolean
    ): DBNoteObject {
        dao.createNote(uuid, back4AppId, userId, text, isPinned, isSynced)
        return dao.getNoteById(uuid).executeAsOne().map()
    }

    fun createNotes(notes: List<DBNoteObject>): List<DBNoteObject> {
        var createdNotes = emptyList<DBNoteObject>()
        dao.transaction {
            createdNotes = notes.map {
                createNote(it.uuid, it.back4AppId, it.userId, it.text, it.isPinned, it.isSynced)
            }
        }
        return createdNotes
    }

    fun updateNote(
        uuid: String,
        back4AppId: String?,
        text: String,
        isPinned: Boolean,
        isSynced: Boolean
    ): DBNoteObject {
        dao.updateNote(back4AppId, text, isPinned, isSynced, uuid)
        return dao.getNoteById(uuid).executeAsOne().map()
    }

    fun updateNotes(notes: List<DBNoteObject>): List<DBNoteObject> {
        var updatedNotes = emptyList<DBNoteObject>()
        dao.transaction {
            updatedNotes = notes.map {
                updateNote(it.uuid, it.back4AppId, it.text, it.isPinned, it.isSynced)
            }
        }
        return updatedNotes
    }

    fun deleteNote(uuid: String) {
        dao.deleteNote(uuid)
    }

    fun deleteAllNotes(userId: String) {
        dao.deleteAllNotes(userId)
    }

    private fun Notes.map(): DBNoteObject {
        return DBNoteObject(
            uuid = this.uuid,
            back4AppId = this.back4AppId,
            userId = this.userId,
            text = this.text,
            isPinned = this.isPinned,
            isSynced = this.isSynced,
            createdAt = this.createdAt.toLocalDateTime(),
            updatedAt = this.updatedAt.toLocalDateTime()
        )
    }
}
