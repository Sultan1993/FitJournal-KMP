package kz.maestrosultan.fitjournal.kmp.notes

import kz.maestrosultan.fitjournal.kmp.FitJournalDatabase
import kz.maestrosultan.fitjournal.kmp.FitJournalDatabaseQueries

class NotesDao(database: FitJournalDatabase) {

    private val queries: FitJournalDatabaseQueries = database.fitJournalDatabaseQueries

    fun getNotes(userId: String): List<DBNoteObject> {
        return queries.getNotes(userId).executeAsList().map {
            val isPinned = it.isPinned == 1L
            DBNoteObject(it.uuid, it.back4AppId, it.userId, it.text, isPinned)
        }
    }
}
