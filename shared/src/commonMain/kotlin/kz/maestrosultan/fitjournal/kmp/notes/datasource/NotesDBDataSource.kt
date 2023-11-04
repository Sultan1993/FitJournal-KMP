package kz.maestrosultan.fitjournal.kmp.notes.datasource

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
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
        userId: String,
        back4AppId: String? = null,
        text: String,
        isPinned: Boolean,
        createdDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        updatedDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    ): DBNoteObject {
        dao.createNote(
            uuid = uuid,
            back4AppId = back4AppId,
            userId = userId,
            text = text,
            isPinned = isPinned,
            createdDate = createdDate.toString(),
            updatedDate = updatedDate.toString()
        )
        return dao.getNoteById(uuid).executeAsOne().map()
    }

    fun createNotes(notes: List<DBNoteObject>): List<DBNoteObject> {
        val createdNotes = mutableListOf<DBNoteObject>()
        dao.transaction {
            notes.forEach {
                val createdNote = createNote(
                    uuid = it.uuid,
                    back4AppId = it.back4AppId,
                    userId = it.userId,
                    text = it.text,
                    isPinned = it.isPinned,
                    createdDate = it.createdDate,
                    updatedDate = it.updatedDate
                )
                createdNotes.add(createdNote)
            }
        }
        return createdNotes
    }

    fun updateNote(
        uuid: String,
        back4AppId: String?,
        text: String,
        isPinned: Boolean,
        updatedDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    ): DBNoteObject {
        dao.updateNote(
            back4AppId = back4AppId,
            text = text,
            isPinned = isPinned,
            updatedDate = updatedDate.toString(),
            uuid = uuid
        )
        return dao.getNoteById(uuid).executeAsOne().map()
    }

    fun updateNotes(notes: List<DBNoteObject>): List<DBNoteObject> {
        val updatedNotes = mutableListOf<DBNoteObject>()
        dao.transaction {
             notes.forEach {
                val updatedNote = updateNote(
                    uuid = it.uuid,
                    back4AppId = it.back4AppId,
                    text = it.text,
                    isPinned = it.isPinned,
                    updatedDate = it.updatedDate
                )
                updatedNotes.add(updatedNote)
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
            uuid = uuid,
            back4AppId = back4AppId,
            userId = userId,
            text = text,
            isPinned = isPinned,
            createdDate = createdDate.toLocalDateTime(),
            updatedDate = updatedDate.toLocalDateTime(),
        )
    }
}
