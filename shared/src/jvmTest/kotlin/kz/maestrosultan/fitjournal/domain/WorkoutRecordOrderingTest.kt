package kz.maestrosultan.fitjournal.domain

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecordOrdering
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutRecordOrderingTest {

    private fun record(id: String, position: Int) = WorkoutRecord(
        id = id,
        userId = "u",
        journalId = "j",
        position = position,
        date = LocalDate(2026, 1, 1),
        exercises = emptyList(),
        createdDate = Instant.fromEpochSeconds(0),
        updatedDate = Instant.fromEpochSeconds(0),
    )

    @Test
    fun reindexedIsZeroBasedInListOrder() {
        val out = WorkoutRecordOrdering.reindexed(listOf(record("a", 5), record("b", 2), record("c", 9)))
        assertEquals(listOf("a" to 0, "b" to 1, "c" to 2), out.map { it.id to it.position })
    }

    @Test
    fun reorderedFollowsIdOrderAndReindexes() {
        val records = listOf(record("a", 0), record("b", 1), record("c", 2))
        val out = WorkoutRecordOrdering.reordered(records, listOf("c", "a", "b"))
        assertEquals(listOf("c" to 0, "a" to 1, "b" to 2), out.map { it.id to it.position })
    }

    @Test
    fun reorderedKeepsUnlistedRecordsAppended() {
        // "b" omitted from the id list — kept, appended after the listed ones.
        val records = listOf(record("a", 0), record("b", 1), record("c", 2))
        val out = WorkoutRecordOrdering.reordered(records, listOf("c", "a"))
        assertEquals(listOf("c" to 0, "a" to 1, "b" to 2), out.map { it.id to it.position })
    }
}
