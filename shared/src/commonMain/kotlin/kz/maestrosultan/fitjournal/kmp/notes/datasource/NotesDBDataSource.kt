package kz.maestrosultan.fitjournal.kmp.notes.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.kmp.NotesQueries
import kz.maestrosultan.fitjournal.kmp.notes.entity.DBNoteObject
import kz.maestrosultan.fitjournal.kmp.notes.entity.map

class NotesDBDataSource(private val dao: NotesQueries) {

    fun getNotes(userId: String): List<DBNoteObject> {
        return dao.getNotes(userId)
            .executeAsList()
            .map { it.map() }
    }

    fun getNotesFlow(userId: String): Flow<List<DBNoteObject>> {
        return dao.getNotes(userId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
    }

    fun getNoteById(uuid: String): DBNoteObject {
        return dao.getNoteById(uuid).executeAsOne().map()
    }

    fun getNoteByIdFlow(uuid: String): Flow<DBNoteObject> {
        return dao.getNoteById(uuid)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.map() }
    }

    fun createNote(
        uuid: String,
        remoteId: String?,
        userId: String,
        text: String,
        isPinned: Boolean,
        createdDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC),
        updatedDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    ): DBNoteObject {
        dao.createNote(
            uuid = uuid,
            remoteId = remoteId,
            userId = userId,
            text = text,
            isPinned = isPinned,
            createdDate = createdDate.toString(),
            updatedDate = updatedDate.toString()
        )
        return getNoteById(uuid)
    }

    fun createNotes(notes: List<DBNoteObject>): List<DBNoteObject> {
        val createdNotes = mutableListOf<DBNoteObject>()
        dao.transaction {
            notes.forEach {
                val createdNote = createNote(
                    uuid = it.uuid,
                    remoteId = it.remoteId,
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
        text: String,
        isPinned: Boolean,
        updatedDate: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    ): DBNoteObject {
        dao.updateNote(
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
                    text = it.text,
                    isPinned = it.isPinned,
                    updatedDate = it.updatedDate
                )
                updatedNotes.add(updatedNote)
            }
        }
        return updatedNotes
    }

    fun updateRemoteId(uuid: String, remoteId: String): DBNoteObject {
        dao.updateRemoteId(remoteId, uuid)
        return dao.getNoteById(uuid).executeAsOne().map()
    }

    fun deleteNote(uuid: String) {
        dao.deleteNote(uuid)
    }

    fun deleteAllNotes(userId: String) {
        dao.deleteAllNotes(userId)
    }

    fun deleteAllNotes() {
        dao.clearNotes()
    }
}
