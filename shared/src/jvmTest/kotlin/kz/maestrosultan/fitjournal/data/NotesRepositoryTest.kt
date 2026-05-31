package kz.maestrosultan.fitjournal.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kz.maestrosultan.fitjournal.data.notes.datasource.NotesDBDataSource
import kz.maestrosultan.fitjournal.data.notes.repository.DefaultNotesRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NotesRepositoryTest {
    private val db = newTestDb()
    private val ds = NotesDBDataSource(db.notesQueries)
    private val repo = DefaultNotesRepository(ds)
    private val userId = "user-1"

    @Test
    fun create_readsBack_andIsPendingUpload(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createNote(id, userId, "hello", isPinned = false)

        val note = assertNotNull(repo.getNoteById(id))
        assertEquals("hello", note.text)
        assertEquals(false, note.isPinned)
        assertEquals(listOf(id), repo.getNotes(userId).map { it.id })
        assertTrue(ds.getPendingUploads(userId).any { it.uuid == id }, "a new note must be queued for upload")
    }

    @Test
    fun update_changesTextAndPin(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createNote(id, userId, "v1", isPinned = false)
        repo.updateNote(id, "v2", isPinned = true)

        val note = assertNotNull(repo.getNoteById(id))
        assertEquals("v2", note.text)
        assertEquals(true, note.isPinned)
    }

    @Test
    fun softDelete_hidesFromReads_butKeepsTombstoneForSync(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createNote(id, userId, "bye", isPinned = false)
        repo.deleteNote(id)

        assertNull(repo.getNoteById(id), "live read must hide a soft-deleted note")
        assertTrue(repo.getNotes(userId).none { it.id == id })

        val tombstone = assertNotNull(
            ds.getNoteByIdIncludingDeleted(id),
            "tombstone row must remain so the orchestrator can push the deletion",
        )
        assertNotNull(tombstone.deletedAt, "deletedAt must be stamped on soft delete")
        assertTrue(ds.getPendingUploads(userId).any { it.uuid == id }, "tombstone must be queued for upload")
    }

    @Test
    fun pinnedNotes_returnsOnlyPinned(): Unit = runBlocking {
        val plain = UUID.randomUUID().toString()
        val pinned = UUID.randomUUID().toString()
        repo.createNote(plain, userId, "plain", isPinned = false)
        repo.createNote(pinned, userId, "pinned", isPinned = true)

        assertEquals(listOf(pinned), repo.getPinnedNotes(userId).map { it.id })
    }

    @Test
    fun notesFlow_reflectsCurrentState(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createNote(id, userId, "f", isPinned = false)
        assertEquals(1, repo.getNotesFlow(userId).first().size)
    }
}
