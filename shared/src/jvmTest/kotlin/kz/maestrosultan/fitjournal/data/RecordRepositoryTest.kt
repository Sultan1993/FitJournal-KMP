package kz.maestrosultan.fitjournal.data

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordRepositoryTest {
    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val repo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper)
    private val userId = "user-1"
    private val journalId = "journal-1"
    private val date = LocalDate(2026, 1, 15)

    private suspend fun seedCatalogExercise(): String {
        val catUuid = UUID.randomUUID().toString()
        catDs.createCategory(catUuid, catUuid, "Legs", "Ноги", "Ноги", CategoryType.QUADRICEPS.id, null)
        val exId = UUID.randomUUID().toString()
        exRepo.createExercise(exId, userId, "Squat", catUuid, ResultType.WEIGHT_REPS)
        return exId
    }

    @Test
    fun createRecord_viaAddExercises_readsBack_andIsPendingUpload(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))

        val records = repo.getRecordsByDate(userId, journalId, date)
        assertEquals(1, records.size)
        val rec = records.single()
        assertEquals(1, rec.exercises.size)
        assertEquals(exId, rec.exercises.single().exercise.uuid)
        assertEquals(0, rec.exercises.single().sets.size)
        assertTrue(workoutsDB.getPendingUploads(userId).any { it.uuid == rec.id }, "a new record must be queued for upload")
    }

    @Test
    fun syncPull_preservesWorkoutNumber_notResetToOne(): Unit = runBlocking {
        // Regression: the pull upsert (INSERT OR REPLACE) used to omit the
        // workoutNumber column, so every pull reset it to the table DEFAULT (1),
        // collapsing multi-workout days onto workout 1.
        val exId = seedCatalogExercise()
        // A record logged as the day's SECOND workout.
        repo.addExercisesToDate(userId, journalId, date, 2, listOf(exId))
        val tree = workoutsDB.getWorkoutRecordById(
            repo.getRecordsByDate(userId, journalId, date).single().id,
        )!!
        assertEquals(2, tree.row.workoutNumber)

        // Simulate a sync pull re-applying the same record — both upsert paths.
        workoutsDB.replaceWorkoutRecordFromRemote(tree)
        assertEquals(
            2,
            workoutsDB.getWorkoutRecordById(tree.row.uuid)!!.row.workoutNumber,
            "a sync pull must preserve workoutNumber, not reset it to 1",
        )

        workoutsDB.replaceWorkoutRecordFromRemoteAsPending(tree)
        assertEquals(
            2,
            workoutsDB.getWorkoutRecordById(tree.row.uuid)!!.row.workoutNumber,
            "the orphan-reparent pull path must also preserve workoutNumber",
        )

        // The domain read still groups it on page 2.
        assertEquals(2, repo.getRecordsByDate(userId, journalId, date).single().workoutNumber)
    }

    @Test
    fun addSet_thenUpdate_thenDelete(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))
        val weId = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().id

        repo.addSet(userId, journalId, weId, weight = 100.0, reps = 5, distance = null, duration = null)
        var set = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets.single()
        assertEquals(100.0, set.weight)
        assertEquals(5, set.reps)

        repo.updateSet(userId, journalId, weId, set.id, weight = 110.0, reps = 3, distance = null, duration = null)
        set = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets.single()
        assertEquals(110.0, set.weight)
        assertEquals(3, set.reps)

        repo.deleteSet(userId, journalId, weId, set.id)
        assertEquals(0, repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets.size)
    }

    @Test
    fun updateOrDeleteMissingSet_returnsFalse_andDoesNotQueuePush(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))
        val rec = repo.getRecordsByDate(userId, journalId, date).single()
        val weId = rec.exercises.single().id
        // Drain the record's initial pendingUpload so we can assert the no-op
        // doesn't re-queue it.
        workoutsDB.markUploaded(rec.id, "remote-1")
        assertTrue(workoutsDB.getPendingUploads(userId).none { it.uuid == rec.id })

        val updated = repo.updateSet(userId, journalId, weId, "ghost-set", 100.0, 5, null, null)
        val deleted = repo.deleteSet(userId, journalId, weId, "ghost-set")

        assertEquals(false, updated, "updating a vanished set is a no-op")
        assertEquals(false, deleted, "deleting a vanished set is a no-op")
        assertTrue(
            workoutsDB.getPendingUploads(userId).none { it.uuid == rec.id },
            "a no-op set write must not re-queue the record for push",
        )
    }

    @Test
    fun addingTwoSets_requiresFkCascade_andKeepsBoth(): Unit = runBlocking {
        // Each addSet round-trips through replaceWorkoutRecord (delete children
        // → reinsert, reusing the workoutExercise uuid). Without ON DELETE
        // CASCADE the prior set is orphaned and the reinsert hits a PK
        // conflict — so two sets surviving proves the cascade is active.
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))
        val weId = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().id

        repo.addSet(userId, journalId, weId, 60.0, 10, null, null)
        repo.addSet(userId, journalId, weId, 80.0, 8, null, null)

        val sets = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets
        assertEquals(2, sets.size)
        assertEquals(listOf(60.0, 80.0), sets.map { it.weight })
    }

    @Test
    fun lastOccurrence_alignsPerPosition_notLastSetOnEverySet(): Unit = runBlocking {
        // Regression: copying / repeating a workout (and the previous-set hint
        // in general) used to take the prior occurrence's LAST set and stamp
        // its weight onto every set. Each set must instead show the weight from
        // the matching position last time.
        val exId = seedCatalogExercise()
        val prevDate = LocalDate(2026, 1, 10)
        val curDate = LocalDate(2026, 1, 17)

        // Previous occurrence: 3 sets at distinct weights.
        repo.addExercisesToDate(userId, journalId, prevDate, 1, listOf(exId))
        val prevWeId = repo.getRecordsByDate(userId, journalId, prevDate).single().exercises.single().id
        repo.addSet(userId, journalId, prevWeId, 100.0, 10, null, null)
        repo.addSet(userId, journalId, prevWeId, 110.0, 8, null, null)
        repo.addSet(userId, journalId, prevWeId, 120.0, 6, null, null)

        // Current occurrence: 4 sets — values irrelevant, we assert the hint.
        repo.addExercisesToDate(userId, journalId, curDate, 1, listOf(exId))
        val curWeId = repo.getRecordsByDate(userId, journalId, curDate).single().exercises.single().id
        repeat(4) { repo.addSet(userId, journalId, curWeId, 0.0, 1, null, null) }

        val exercise = repo.getRecordsByDate(userId, journalId, curDate).single().exercises.single()
        val last = requireNotNull(exercise.lastOccurrence) { "prior occurrence should be attached" }
        assertEquals(prevDate, last.date)
        // set N ← prior occurrence's set N; the 4th overflows → falls back to last (120).
        assertEquals(
            listOf(100.0, 110.0, 120.0, 120.0),
            exercise.sets.indices.map { last.setAt(it)?.weight },
        )
        // The overflow rule is the bit that regressed before — assert it explicitly
        // rather than only via the 4th element above.
        assertEquals(120.0, last.setAt(99)?.weight)
        // A negative position clamps to the FIRST set. Without the clamp it falls
        // through to the overflow branch and returns the heaviest set instead.
        assertEquals(100.0, last.setAt(-1)?.weight)
    }

    @Test
    fun copyingAnOlderWorkout_showsTheLatestSessionWhole_notAHybrid(): Unit = runBlocking {
        // The reported bug, end to end. Trained 24 July at 20×12 ×3, then 27 July
        // at 22×10 / 22×9 / 22×9. Importing the 24th onto today used to carry the
        // 24th's REPS while the ghost weight came from the 27th (the most recent
        // occurrence), rendering "22 kg × 12" — a set that never happened.
        val exId = seedCatalogExercise()
        val jul24 = LocalDate(2026, 7, 24)
        val jul27 = LocalDate(2026, 7, 27)
        val today = LocalDate(2026, 7, 28)

        repo.addExercisesToDate(userId, journalId, jul24, 1, listOf(exId))
        val we24 = repo.getRecordsByDate(userId, journalId, jul24).single().exercises.single().id
        repeat(3) { repo.addSet(userId, journalId, we24, 20.0, 12, null, null) }

        repo.addExercisesToDate(userId, journalId, jul27, 1, listOf(exId))
        val we27 = repo.getRecordsByDate(userId, journalId, jul27).single().exercises.single().id
        repo.addSet(userId, journalId, we27, 22.0, 10, null, null)
        repo.addSet(userId, journalId, we27, 22.0, 9, null, null)
        repo.addSet(userId, journalId, we27, 22.0, 9, null, null)

        val source = repo.getRecordsByDate(userId, journalId, jul24)
        repo.addRecordsToDate(userId, journalId, today, source)

        val copied = repo.getRecordsByDate(userId, journalId, today).single().exercises.single()
        // The copy keeps the set COUNT and nothing else — no reps carried over.
        assertEquals(3, copied.sets.size)
        assertTrue(copied.sets.all { it.weight == null && it.reps == null }, "a copied row must be empty")

        // So every row renders the 27th's pair, whole.
        assertEquals(jul27, copied.lastOccurrence?.date)
        val shown = copied.sets.indices.map { copied.displayValuesAt(it, false) }
        assertEquals(listOf(22.0, 22.0, 22.0), shown.map { it.value })
        assertEquals(listOf(10, 9, 9), shown.map { it.reps })
    }

    @Test
    fun addRecordsToWorkout_forcesEveryCopyOntoTheTargetPage_appendingPositions(): Unit = runBlocking {
        // The "copy from a workout onto the tapped page" path: sources spanning
        // several source workouts all collapse onto ONE target page, appended
        // after that page's existing rows (no position collision).
        val exId = seedCatalogExercise()
        val src = LocalDate(2026, 3, 10)
        val target = LocalDate(2026, 3, 20)

        repo.addExercisesToDate(userId, journalId, src, 1, listOf(exId))
        repo.addExercisesToDate(userId, journalId, src, 3, listOf(exId))
        val source = repo.getRecordsByDate(userId, journalId, src)
        assertEquals(setOf(1, 3), source.map { it.workoutNumber }.toSet())

        // Target already has a record on workout 2 — copies must APPEND, not collide.
        repo.addExercisesToDate(userId, journalId, target, 2, listOf(exId))

        repo.addRecordsToWorkout(userId, journalId, target, 2, source)

        val onTarget = repo.getRecordsByDate(userId, journalId, target)
        assertEquals(3, onTarget.size, "1 pre-existing + 2 copies")
        assertTrue(onTarget.all { it.workoutNumber == 2 }, "every copy lands on the target page")
        assertEquals(listOf(0, 1, 2), onTarget.map { it.position }.sorted(), "positions append, no collision")
    }

    @Test
    fun addRecordsToDate_stillCopiesAsIs_preservingSourceWorkoutNumbers(): Unit = runBlocking {
        // The Repeat-workout path (targetWorkoutNumber = null) must be unaffected by
        // the forced-target variant: a 2-workout source day copies back as 2 workouts.
        val exId = seedCatalogExercise()
        val src = LocalDate(2026, 4, 10)
        val target = LocalDate(2026, 4, 20)
        repo.addExercisesToDate(userId, journalId, src, 1, listOf(exId))
        repo.addExercisesToDate(userId, journalId, src, 2, listOf(exId))
        val source = repo.getRecordsByDate(userId, journalId, src)

        repo.addRecordsToDate(userId, journalId, target, source)

        val onTarget = repo.getRecordsByDate(userId, journalId, target)
        assertEquals(setOf(1, 2), onTarget.map { it.workoutNumber }.toSet(), "copy-as-is keeps source pages")
    }

    @Test
    fun mergeRecords_intoSuperset_doesNotCollideOnSetUuids(): Unit = runBlocking {
        // Regression: creating a superset crashed with `UNIQUE constraint
        // failed: workoutSets.uuid`. mergeRecords re-parented the second
        // record's sets onto a new exercise uuid but reused the set uuids;
        // softDeleteWorkoutRecord only tombstones the record row, so those
        // set rows physically remained and the reinsert hit the PK.
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId, exId))
        val records = repo.getRecordsByDate(userId, journalId, date).sortedBy { it.position }
        assertEquals(2, records.size)
        val (first, second) = records
        // Both records need sets — the crash only fires when the merged-in
        // exercise carries sets to reinsert.
        repo.addSet(userId, journalId, first.exercises.single().id, 100.0, 5, null, null)
        repo.addSet(userId, journalId, second.exercises.single().id, 60.0, 12, null, null)
        repo.addSet(userId, journalId, second.exercises.single().id, 70.0, 10, null, null)

        val merged = repo.mergeRecords(userId, journalId, first, second)

        // Second record tombstoned → one live superset record with both exercises.
        assertEquals(1, merged.size)
        val superset = merged.single()
        assertEquals(2, superset.exercises.size)
        val allSetIds = superset.exercises.flatMap { it.sets }.map { it.id }
        assertEquals(3, allSetIds.size)
        assertEquals(allSetIds.size, allSetIds.toSet().size, "merged set uuids must be unique")
    }

    @Test
    fun replaceExerciseInRecord_swapsOnlyTargetMember_keepsSuperset(): Unit = runBlocking {
        // Data-loss regression: replacing a superset member used to always
        // rebuild the FIRST exercise, destroying the wrong member and its sets.
        val exA = seedCatalogExercise()
        val exB = seedCatalogExercise()
        val exC = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exA, exB))
        val records = repo.getRecordsByDate(userId, journalId, date).sortedBy { it.position }
        val (first, second) = records
        repo.addSet(userId, journalId, first.exercises.single().id, 100.0, 5, null, null)
        repo.addSet(userId, journalId, second.exercises.single().id, 60.0, 12, null, null)
        val superset = repo.mergeRecords(userId, journalId, first, second).single()
        assertEquals(2, superset.exercises.size)
        val memberB = superset.exercises.first { it.exercise.uuid == exB }

        // Replace member B (NOT the first) with catalog exercise C.
        repo.replaceExerciseInRecord(userId, journalId, superset.id, memberB.id, exC)

        val updated = repo.getRecordsByDate(userId, journalId, date).single()
        assertEquals(2, updated.exercises.size, "superset must keep both members")
        val newA = updated.exercises.first { it.exercise.uuid == exA }
        assertEquals(1, newA.sets.size, "the untouched member must keep its sets")
        assertEquals(100.0, newA.sets.single().weight)
        assertTrue(updated.exercises.none { it.exercise.uuid == exB }, "old member B must be gone")
        val newC = updated.exercises.first { it.exercise.uuid == exC }
        assertEquals(0, newC.sets.size, "the replaced member starts with no sets")
        assertTrue(newC.id != memberB.id, "replacement gets a new workoutExerciseId")
    }

    @Test
    fun removeExerciseFromSuperset_splitsIntoOwnRecord(): Unit = runBlocking {
        // Regression: "Remove from superset" used to DELETE the exercise (and
        // tombstone the record when it was the last one) instead of splitting
        // it into its own record — user-visible data loss.
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId, exId))
        val records = repo.getRecordsByDate(userId, journalId, date).sortedBy { it.position }
        val (first, second) = records
        repo.addSet(userId, journalId, second.exercises.single().id, 60.0, 12, null, null)
        val superset = repo.mergeRecords(userId, journalId, first, second).single()
        assertEquals(2, superset.exercises.size)
        val removedExercise = superset.exercises.last()
        val removedSetValues = removedExercise.sets.map { it.weight to it.reps }

        // Remove one exercise → the superset SPLITS: two live records,
        // the split-off one right after the source, sets intact.
        val afterRemoval = repo.removeExerciseFromRecord(userId, journalId, superset, removedExercise)
        assertEquals(2, afterRemoval.size, "split must leave two live records")
        val sorted = afterRemoval.sortedBy { it.position }
        val source = sorted.first { it.id == superset.id }
        val split = sorted.single { it.id != superset.id }
        assertEquals(1, source.exercises.size)
        assertEquals(1, split.exercises.size)
        assertEquals(removedExercise.id, split.exercises.single().id, "split keeps the exercise")
        assertEquals(
            removedSetValues,
            split.exercises.single().sets.map { it.weight to it.reps },
            "split keeps the exercise's sets",
        )
        assertEquals(source.position + 1, split.position, "split lands right after the source record")
        val pending = workoutsDB.getPendingUploads(userId).map { it.uuid }
        assertTrue(source.id in pending, "shrunk source record must be queued for push")
        assertTrue(split.id in pending, "split-off record must be queued for push")
        assertNull(workoutsDB.getWorkoutRecordById(split.id)?.row?.remoteId, "split-off record is a new row, no remoteId yet")

        // Removing the only exercise of a non-superset record is a no-op
        // (a 1-exercise record isn't a superset; deletion is deleteRecord's job).
        val afterNoOp = repo.removeExerciseFromRecord(userId, journalId, split, split.exercises.single())
        assertEquals(2, afterNoOp.size, "no-op: nothing deleted")
    }

    @Test
    fun removeExerciseFromSuperset_shiftsLaterSameDayRecords(): Unit = runBlocking {
        // The split inserts a new record at source.position + 1 — any later
        // same-day record must shift +1 (and be re-queued for push) so the
        // day's ordering stays: source, split-off, everything after.
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId, exId, exId))
        val records = repo.getRecordsByDate(userId, journalId, date).sortedBy { it.position }
        assertEquals(3, records.size)
        val superset = repo.mergeRecords(userId, journalId, records[0], records[1])
            .single { it.exercises.size == 2 }
        val third = repo.getRecordsByDate(userId, journalId, date).single { it.id == records[2].id }
        val thirdPositionBefore = third.position

        val after = repo.removeExerciseFromRecord(userId, journalId, superset, superset.exercises.last())

        assertEquals(3, after.size, "source + split + shifted third record")
        val sorted = after.sortedBy { it.position }
        assertEquals(superset.id, sorted[0].id, "source record stays first")
        assertEquals(superset.exercises.first().id, sorted[0].exercises.single().id)
        assertEquals(superset.exercises.last().id, sorted[1].exercises.single().id, "split-off lands second")
        assertEquals(third.id, sorted[2].id, "later record stays after the split-off")
        assertEquals(thirdPositionBefore + 1, sorted[2].position, "later record shifted +1")
        assertTrue(
            workoutsDB.getPendingUploads(userId).any { it.uuid == third.id },
            "shifted record must be re-queued for push",
        )
    }

    @Test
    fun removingMiddleExercise_ofThreeMemberSuperset_keepsSurvivorOrder(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId, exId, exId))
        val records = repo.getRecordsByDate(userId, journalId, date).sortedBy { it.position }
        val two = repo.mergeRecords(userId, journalId, records[0], records[1])
            .single { it.exercises.size == 2 }
        val third = repo.getRecordsByDate(userId, journalId, date).single { it.id != two.id }
        val superset = repo.mergeRecords(userId, journalId, two, third)
            .single { it.exercises.size == 3 }
        val (first, middle, last) = superset.exercises

        val after = repo.removeExerciseFromRecord(userId, journalId, superset, middle)

        val sorted = after.sortedBy { it.position }
        assertEquals(2, sorted.size)
        val source = sorted.single { it.id == superset.id }
        val split = sorted.single { it.id != superset.id }
        // Domain exercises come back position-ordered — order proves the renumbering.
        assertEquals(listOf(first.id, last.id), source.exercises.map { it.id }, "survivors keep order")
        assertEquals(middle.id, split.exercises.single().id)
    }

    @Test
    fun deleteMiddleSet_ofThree_keepsOthersInOrder_andRenumbers(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))
        val weId = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().id
        repo.addSet(userId, journalId, weId, 100.0, 10, null, null)
        repo.addSet(userId, journalId, weId, 110.0, 8, null, null)
        repo.addSet(userId, journalId, weId, 120.0, 6, null, null)

        val middle = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets[1]
        assertEquals(true, repo.deleteSet(userId, journalId, weId, middle.id))

        val sets = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets
        assertEquals(listOf(100.0, 120.0), sets.map { it.weight }, "middle removed, survivors keep order")

        // A new set lands after the survivors — proves positions renumbered to
        // 0,1 (otherwise it would collide with a survivor still at position 2).
        repo.addSet(userId, journalId, weId, 130.0, 5, null, null)
        val after = repo.getRecordsByDate(userId, journalId, date).single().exercises.single().sets
        assertEquals(listOf(100.0, 120.0, 130.0), after.map { it.weight })
    }

    @Test
    fun deleteRecord_tombstones_andKeepsPendingForSync(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))
        val rec = repo.getRecordsByDate(userId, journalId, date).single()

        repo.deleteRecord(userId, journalId, rec)

        assertTrue(repo.getRecordsByDate(userId, journalId, date).isEmpty(), "live read must hide a deleted record")
        val tombstone = assertNotNull(workoutsDB.getWorkoutRecordByIdIncludingDeleted(rec.id))
        assertNotNull(tombstone.row.deletedAt, "deletedAt must be stamped")
        assertTrue(workoutsDB.getPendingUploads(userId).any { it.uuid == rec.id }, "tombstone must be queued for push")
    }

    @Test
    fun removingOnlyExercise_isNoOp_recordSurvives(): Unit = runBlocking {
        // Under split semantics, removeExerciseFromRecord on a 1-exercise
        // record does nothing — record deletion is deleteRecord's job.
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))
        val rec = repo.getRecordsByDate(userId, journalId, date).single()
        val we = rec.exercises.single()

        val remaining = repo.removeExerciseFromRecord(userId, journalId, rec, we)

        assertEquals(1, remaining.size, "record must survive")
        assertEquals(1, remaining.single().exercises.size, "exercise must survive")
        assertNull(workoutsDB.getWorkoutRecordByIdIncludingDeleted(rec.id)?.row?.deletedAt)
    }

    @Test
    fun addExercises_defaultsToWorkout1_andPositionIsPageRelative(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId, exId))

        val records = repo.getRecordsByDate(userId, journalId, date)
        assertEquals(listOf(1, 1), records.map { it.workoutNumber })
        assertEquals(listOf(0, 1), records.map { it.position }, "first workout's positions are 0-based")
    }

    @Test
    fun secondWorkout_isPageRelative_andOrdersAfterWorkout1(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        // Two exercises in workout 1, then two in workout 2 the same day.
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId, exId))
        repo.addExercisesToDate(userId, journalId, date, 2, listOf(exId, exId))

        val records = repo.getRecordsByDate(userId, journalId, date)
        // Grouped by workoutNumber, position restarts at 0 in workout 2.
        assertEquals(listOf(1, 1, 2, 2), records.map { it.workoutNumber })
        assertEquals(listOf(0, 1, 0, 1), records.map { it.position })
    }

    @Test
    fun addingToWorkout1_afterWorkout2Exists_appendsWithinWorkout1(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))
        repo.addExercisesToDate(userId, journalId, date, 2, listOf(exId))
        // Go back and add to workout 1 — page-relative, so it appends at pos 1
        // within workout 1, NOT at a day-global next position.
        repo.addExercisesToDate(userId, journalId, date, 1, listOf(exId))

        val w1 = repo.getRecordsByDate(userId, journalId, date).filter { it.workoutNumber == 1 }
        assertEquals(listOf(0, 1), w1.map { it.position }, "second workout-1 record appends at pos 1, not a day-global value")
    }

    @Test
    fun copy_preservesSourceWorkoutNumber(): Unit = runBlocking {
        val exId = seedCatalogExercise()
        val src = LocalDate(2026, 1, 10)
        val target = LocalDate(2026, 2, 20)
        // A 2-workout source day.
        repo.addExercisesToDate(userId, journalId, src, 1, listOf(exId))
        repo.addExercisesToDate(userId, journalId, src, 2, listOf(exId))

        repo.addRecordsToDate(userId, journalId, target, repo.getRecordsByDate(userId, journalId, src))

        val copied = repo.getRecordsByDate(userId, journalId, target)
        assertEquals(listOf(1, 2), copied.map { it.workoutNumber }, "a 2-workout day copies back as 2 workouts")
        assertEquals(listOf(0, 0), copied.map { it.position }, "each copied workout starts page-relative at 0")
    }
}
