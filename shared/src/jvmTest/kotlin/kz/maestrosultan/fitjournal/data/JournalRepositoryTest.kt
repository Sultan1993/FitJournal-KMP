package kz.maestrosultan.fitjournal.data

import kotlinx.coroutines.runBlocking
import kz.maestrosultan.fitjournal.data.journal.datasource.JournalsDBDataSource
import kz.maestrosultan.fitjournal.data.journal.repository.DefaultJournalRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JournalRepositoryTest {
    private val db = newTestDb()
    private val ds = JournalsDBDataSource(db.journalsQueries)
    private val repo = DefaultJournalRepository(ds)
    private val userId = "user-1"

    @Test
    fun create_readsBack_andIsPendingUpload(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createJournal(id, userId, "Legs", comments = "leg day", isPersonal = false, workoutGoal = 3)

        val j = assertNotNull(repo.getJournalById(id))
        assertEquals("Legs", j.name)
        assertEquals(3, j.workoutGoal)
        assertEquals(false, j.isPersonal)
        assertTrue(ds.getPendingUploads().any { it.uuid == id }, "a new journal must be queued for upload")
    }

    @Test
    fun update_changesNameCommentsGoal(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createJournal(id, userId, "A", comments = null, isPersonal = false, workoutGoal = null)
        repo.updateJournal(id, "B", comments = "notes", workoutGoal = 5)

        val j = assertNotNull(repo.getJournalById(id))
        assertEquals("B", j.name)
        assertEquals("notes", j.comments)
        assertEquals(5, j.workoutGoal)
    }

    @Test
    fun softDelete_hidesFromReads_butKeepsTombstoneForSync(): Unit = runBlocking {
        val id = UUID.randomUUID().toString()
        repo.createJournal(id, userId, "Temp", comments = null, isPersonal = false, workoutGoal = null)
        repo.deleteJournal(id)

        assertNull(repo.getJournalById(id))
        assertTrue(repo.getJournals(userId).none { it.id == id })
        assertNotNull(ds.getJournalByIdIncludingDeleted(id), "tombstone row must remain for sync")
    }

    @Test
    fun getOrCreatePersonal_isIdempotent(): Unit = runBlocking {
        val first = repo.getOrCreatePersonalJournal(UUID.randomUUID().toString(), userId, "Me", null, null)
        val second = repo.getOrCreatePersonalJournal(UUID.randomUUID().toString(), userId, "Me", null, null)

        assertEquals(first.id, second.id, "must return the existing personal journal, never create a duplicate")
        assertEquals(true, first.isPersonal)
        assertEquals(1, repo.getJournals(userId).count { it.isPersonal })
    }
}
