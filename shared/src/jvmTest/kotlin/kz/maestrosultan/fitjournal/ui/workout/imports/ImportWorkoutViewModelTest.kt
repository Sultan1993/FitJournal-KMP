package kz.maestrosultan.fitjournal.ui.workout.imports

import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.data.newTestDb
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.testExerciseMapper
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.LengthMeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.UserSessionState
import kz.maestrosultan.fitjournal.domain.workout.ResultType

/**
 * VM + real repository (in-memory SQLite) integration. Covers the behaviors the
 * repo alone can't: every source record pre-selected on load, and the in-progress
 * guard making a double-tapped Import write (and dismiss) exactly once.
 *
 * Uses runBlocking + a real Main dispatcher and waits on the StateFlow — the repo
 * reads hop to Dispatchers.IO, so a virtual-time test dispatcher wouldn't drive them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportWorkoutViewModelTest {

    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val repo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper)
    private val user = "user-1"
    private val journal = "journal-1"

    @BeforeTest fun setUp() = Dispatchers.setMain(Dispatchers.Default)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private suspend fun seedExercise(): String {
        val catUuid = UUID.randomUUID().toString()
        catDs.createCategory(catUuid, catUuid, "Legs", "Ноги", "Ноги", CategoryType.QUADRICEPS.id, null)
        val exId = UUID.randomUUID().toString()
        exRepo.createExercise(exId, user, "Squat", catUuid, ResultType.WEIGHT_REPS)
        return exId
    }

    private fun vm(destDate: LocalDate, destWorkoutNumber: Int) = ImportWorkoutViewModel(
        recordRepository = repo,
        syncTrigger = object : SyncTrigger {
            override fun requestTick(reason: SyncReason) = Unit
        },
        destinationDate = destDate,
        destinationWorkoutNumber = destWorkoutNumber,
        awaitSession = {
            UserSessionState(user, journal, MeasurementSystem.KG_KM, LengthMeasurementSystem.CENTIMETERS)
        },
    )

    /**
     * Import copies the page on screen, not the whole source day: a 2-workout source
     * day imports one workout's records, and swiping picks which one.
     */
    @Test
    fun importCopiesOnlyTheVisiblePage(): Unit = runBlocking {
        val first = seedExercise()
        val second = seedExercise()
        val src = LocalDate(2026, 5, 10)
        val dest = LocalDate(2026, 5, 11)
        repo.addExercisesToDate(user, journal, src, 1, listOf(first))
        repo.addExercisesToDate(user, journal, src, 2, listOf(second))
        val model = vm(dest, 1)

        // The VM opens on the (empty) destination day; Empty only lands once the
        // session resolved, so it doubles as "ready for a source-date switch".
        withTimeout(5000) { model.viewState.first { it.content is ImportContent.Empty } }
        model.dispatch(ImportWorkoutContract.ViewAction.SelectSourceDate(src))
        val loaded = withTimeout(5000) {
            model.viewState.first { it.content is ImportContent.Loaded }.content as ImportContent.Loaded
        }
        assertEquals(2, loaded.pages.size, "source day has two workouts to page between")
        model.dispatch(ImportWorkoutContract.ViewAction.SelectPage(1))
        model.dispatch(ImportWorkoutContract.ViewAction.Import)
        withTimeout(5000) { model.viewEffect.first() }

        val landed = repo.getRecordsByDate(user, journal, dest, includeLastOccurrence = false)
        assertEquals(1, landed.size, "only the visible page's single record is copied")
        assertEquals(
            second,
            landed.single().exercises.single().exercise.uuid,
            "page 1 was showing, so workout 2's exercise is the one copied",
        )
        model.dispose()
    }

    /** A two-workout source day must page — that page count is what drives the dots. */
    @Test
    fun sourceDayWithTwoWorkouts_yieldsTwoPages(): Unit = runBlocking {
        val exId = seedExercise()
        val src = LocalDate(2026, 5, 10)
        repo.addExercisesToDate(user, journal, src, 1, listOf(exId))
        repo.addExercisesToDate(user, journal, src, 2, listOf(exId))
        val model = vm(src, 1)

        val state = withTimeout(5000) { model.viewState.first { it.content is ImportContent.Loaded } }
        val loaded = state.content as ImportContent.Loaded
        assertEquals(listOf(1, 2), loaded.pages.map { it.workoutNumber })
        model.dispose()
    }

    @Test
    fun opensWithEverySourceRecordPreselected(): Unit = runBlocking {
        val exId = seedExercise()
        val src = LocalDate(2026, 5, 10)
        repo.addExercisesToDate(user, journal, src, 1, listOf(exId, exId))
        val model = vm(src, 1)

        val state = withTimeout(5000) { model.viewState.first { it.content is ImportContent.Loaded } }
        val loaded = state.content as ImportContent.Loaded
        assertEquals(2, loaded.pages.single().records.size)
        assertEquals(
            loaded.pages.single().records.map { it.id }.toSet(),
            loaded.selectedRecordIds,
            "every source record is pre-selected",
        )
        assertTrue(state.canImport)
        model.dispose()
    }

    @Test
    fun doubleTapImport_writesOnce_andDismissesOnce(): Unit = runBlocking {
        val exId = seedExercise()
        val src = LocalDate(2026, 5, 10)
        val dest = LocalDate(2026, 5, 20)
        repo.addExercisesToDate(user, journal, src, 1, listOf(exId, exId))
        val model = vm(dest, 2)
        // initial (dest is empty → Empty, not Loading)
        withTimeout(5000) { model.viewState.first { it.content !is ImportContent.Loading } }

        model.dispatch(ImportWorkoutContract.ViewAction.SelectSourceDate(src))
        val state = withTimeout(5000) { model.viewState.first { it.content is ImportContent.Loaded } }
        val loaded = state.content as ImportContent.Loaded
        assertEquals(2, loaded.selectedRecordIds.size)

        // dispatch() is synchronous, and onImport flips importInProgress before it
        // returns, so the second tap is guaranteed to hit the guard.
        model.dispatch(ImportWorkoutContract.ViewAction.Import)
        model.dispatch(ImportWorkoutContract.ViewAction.Import)

        val effect = withTimeout(5000) { model.viewEffect.first() }
        assertEquals(ImportWorkoutContract.ViewEffect.Dismiss, effect)

        val onDest = repo.getRecordsByDate(user, journal, dest)
        assertEquals(2, onDest.size, "one import, not two")
        assertTrue(onDest.all { it.workoutNumber == 2 }, "copies land on the destination page")
        model.dispose()
    }
}
