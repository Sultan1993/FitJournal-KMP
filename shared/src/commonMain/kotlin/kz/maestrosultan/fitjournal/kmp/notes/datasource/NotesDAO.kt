package kz.maestrosultan.fitjournal.kmp.notes.datasource

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

    /**
     * This method is more oriented towards local usage. Meaning it will be
     * user when user creates a note on the device. We don't have back4AppId yet
     */
    fun createNote(uuid: String, userId: String, text: String): DBNoteObject {
        dao.createNote(uuid, null, userId, text, false)
        return dao.getNoteById(uuid).executeAsOne().map()
    }

    /**
     * This method is more oriented towards sync usage. Meaning it will be
     * user when we don't have anything locally and we fetch all notes from Back4App
     */
    fun createNote(
        uuid: String,
        back4AppId: String,
        userId: String,
        text: String,
        isPinned: Boolean
    ): DBNoteObject {
        dao.createNote(uuid, back4AppId, userId, text, isPinned)
        return dao.getNoteById(uuid).executeAsOne().map()
    }

    /**
     * This method is for batch insert from Back4App
     */
    fun createNotes(notes: List<DBNoteObject>) {
        dao.transaction {
            notes.forEach {
                createNote(it.uuid, it.back4AppId!!, it.userId, it.text, it.isPinned)
            }
        }
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
            isPinned = this.isPinned
        )
    }
}
