package kz.maestrosultan.fitjournal.kmp.notes

import kz.maestrosultan.fitjournal.kmp.FitJournalDatabaseQueries
import kz.maestrosultan.fitjournal.kmp.Notes

class NotesDao(private val queries: FitJournalDatabaseQueries) {

    fun getNotes(userId: String): List<DBNoteObject> {
        return queries.getNotes(userId).executeAsList().map { it.map() }
    }

    fun getNoteById(uuid: String): DBNoteObject? {
        return queries.getNoteById(uuid).executeAsOneOrNull()?.map()
    }

    /**
     * This method is more oriented towards local usage. Meaning it will be
     * user when user creates a note on the device. We don't have back4AppId yet
     */
    fun createNote(uuid: String, userId: String, text: String): DBNoteObject {
        queries.createNote(uuid, null, userId, text, false)
        return queries.getNoteById(uuid).executeAsOne().map()
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
        queries.createNote(uuid, back4AppId, userId, text, isPinned)
        return queries.getNoteById(uuid).executeAsOne().map()
    }

    /**
     * This method is for batch insert from Back4App
     */
    fun createNotes(notes: List<DBNoteObject>) {
        queries.transaction {
            notes.forEach {
                createNote(it.uuid, it.back4AppId!!, it.userId, it.text, it.isPinned)
            }
        }
    }

    fun updateNote(
        uuid: String,
        back4AppId: String?,
        text: String,
        isPinned: Boolean
    ): DBNoteObject {
        queries.updateNote(back4AppId, text, isPinned, uuid)
        return queries.getNoteById(uuid).executeAsOne().map()
    }

    fun deleteNote(uuid: String) {
        queries.deleteNote(uuid)
    }

    fun deleteAllNotes() {
        queries.deleteAllNotes()
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
