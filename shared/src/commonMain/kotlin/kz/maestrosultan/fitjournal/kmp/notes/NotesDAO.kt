package kz.maestrosultan.fitjournal.kmp.notes

import kz.maestrosultan.fitjournal.kmp.FitJournalDatabaseQueries

class NotesDao(private val queries: FitJournalDatabaseQueries) {

    fun getNotes(userId: String): List<DBNoteObject> {
        return queries.getNotes(userId).executeAsList().map {
            val isPinned = it.isPinned == 1L
            DBNoteObject(it.uuid, it.back4AppId, it.userId, it.text, isPinned)
        }
    }
}
