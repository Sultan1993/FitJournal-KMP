package kz.maestrosultan.fitjournal.ui.workout.repeat

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.quota.FreeQuotaSettings
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.summary.WeightedSetOccurrence
import kz.maestrosultan.fitjournal.domain.workout.usecase.RepeatWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.workout.MuscleTitleFormatter

/**
 * [RepeatPickerViewModel] orchestration. Repositories are hand-rolled in-memory
 * fakes rather than the real SQLite ones: what is under test is which options the
 * sheet offers and how it degrades, and half of that is about reads that FAIL or
 * arrive LATE — neither of which a real repository can be asked for.
 *
 * The use case is REAL ([RepeatWorkoutUseCase] is a final class), driven over the
 * same fake, so the outcome mapping is proven through the actual pipeline. That
 * makes its default [kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate]
 * read this fake's quota surface too.
 *
 * [FreeQuotaSettings] is a global `object` and jvmTest runs every class in one
 * JVM, so BOTH [BeforeTest] and [AfterTest] reset it — discipline copied verbatim
 * from `WorkoutQuotaGateTest`. On reset defaults the gate answers Unlimited
 * without touching the repository, which is why only the refusal case calls
 * [meterOn]: without it that test would pass for the wrong reason.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RepeatPickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val created = mutableListOf<RepeatPickerViewModel>()

    @BeforeTest
    fun setUp() {
        FreeQuotaSettings.reset()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        created.forEach { it.dispose() }
        created.clear()
        FreeQuotaSettings.reset()
        Dispatchers.resetMain()
    }

    /** Meter a NEVER-SUBSCRIBER. Copied verbatim from `WorkoutQuotaGateTest`. */
    private fun meterOn(limit: Long = 10) {
        FreeQuotaSettings.setLimit(limit)
        FreeQuotaSettings.setHasEverSubscribed(false)
    }

    // ─── Harness ────────────────────────────────────────────────────────

    private val records = FakeRecordRepository()
    private val sessions = FakeSessionRepository()
    private val syncTrigger = FakeSyncTrigger()
    private val outcomes = mutableListOf<RepeatPickerContract.Outcome>()

    /** Deterministic: no compose-resource loading, no locale dependence. */
    private val formatter = MuscleTitleFormatter(
        categoryName = { it.name },
        fallbackTitle = { FALLBACK_TITLE },
    )

    private fun viewModel(initialDate: LocalDate = SOURCE_DATE) = RepeatPickerViewModel(
        recordRepository = records,
        sessionRepository = sessions,
        repeatWorkout = RepeatWorkoutUseCase(records, syncTrigger),
        userId = USER,
        journalId = JOURNAL,
        sourceDate = SOURCE_DATE,
        sourceWorkoutNumber = 1,
        initialDate = initialDate,
        onOutcome = { outcomes += it },
        muscleTitleFormatter = formatter,
    ).also { created += it }

    private fun choiceOf(vm: RepeatPickerViewModel): RepeatPickerContract.Content.Choice =
        assertIs<RepeatPickerContract.Content.Choice>(vm.viewState.value.content)

    // ─── Opening ────────────────────────────────────────────────────────

    @Test
    fun opensOnTheDayItWasHandedWithTheDestinationPane() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        val vm = viewModel(initialDate = SOURCE_DATE)

        val settled = vm.viewState.first { it.content !is RepeatPickerContract.Content.Loading }

        assertEquals(SOURCE_DATE, settled.selectedDate)
        assertEquals(RepeatPickerContract.Pane.Destination, settled.pane)
        assertTrue(settled.canAdd)
    }

    // ─── Records gate the list ──────────────────────────────────────────

    @Test
    fun aDayWithRecordsOffersItsOwnPagesPlusATrailingNewRow() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(
            chestRecord(SOURCE_DATE, workoutNumber = 1),
            chestRecord(SOURCE_DATE, workoutNumber = 2, id = "r2"),
        )
        val vm = viewModel()
        advanceUntilIdle()

        val choice = choiceOf(vm)
        assertEquals(listOf(1, 2, 3), choice.rows.map { it.destination.workoutNumber })
        assertTrue(
            choice.rows.take(2).none { it.destination.isNewWorkout },
            "the source page IS offered — repeating a workout into itself is now the user's call",
        )
        assertTrue(choice.rows.last().destination.isNewWorkout, "the new page is the trailing row")
        assertNull(choice.rows.last().title, "the New-workout row draws its own static strings")
    }

    @Test
    fun aSessionOnlyDayIsSingleOnTheStartedPage() = runTest(dispatcher) {
        val day = OTHER_DATE
        sessions.byDate[day] = listOf(session(day, workoutNumber = 2, running = true))
        val vm = viewModel(initialDate = day)
        advanceUntilIdle()

        val single = assertIs<RepeatPickerContract.Content.Single>(vm.viewState.value.content)
        assertEquals(
            2,
            single.destination.workoutNumber,
            "nothing is logged, so there is nothing to choose between — but the copy must join the started page",
        )
        assertFalse(single.destination.isNewWorkout)
        assertTrue(single.destination.isRunning)
    }

    @Test
    fun anEmptyDayThatReadCleanlyIsSingleOnPageOne() = runTest(dispatcher) {
        val vm = viewModel(initialDate = OTHER_DATE)
        advanceUntilIdle()

        val single = assertIs<RepeatPickerContract.Content.Single>(vm.viewState.value.content)
        assertEquals(1, single.destination.workoutNumber)
        assertTrue(single.destination.isNewWorkout)
        assertTrue(vm.viewState.value.canAdd)
    }

    // ─── Row titles ─────────────────────────────────────────────────────

    @Test
    fun aBlankTemplatePageIsTitledByItsExercisesNotByLoggedSets() = runTest(dispatcher) {
        // Page 1: copied template — four exercises, not one set logged. The shared
        // rankedMuscles returns EMPTY for it, so without the exercise-count ranking
        // this row would read as the generic fallback.
        records.recordsByDate[SOURCE_DATE] = listOf(
            record(
                SOURCE_DATE, workoutNumber = 1, id = "blank",
                exercises = listOf(
                    unloggedExercise("we-1", "Bench", CategoryType.CHEST),
                    unloggedExercise("we-2", "Fly", CategoryType.CHEST),
                    unloggedExercise("we-3", "Pushdown", CategoryType.TRICEPS),
                    unloggedExercise("we-4", "Crunch", CategoryType.ABS),
                ),
            ),
        )
        // Page 2 exists only as a session, so it owns no exercises at all.
        sessions.byDate[SOURCE_DATE] = listOf(session(SOURCE_DATE, workoutNumber = 2, running = false))
        val vm = viewModel()
        advanceUntilIdle()

        val choice = choiceOf(vm)
        assertEquals(
            "${CategoryType.CHEST.name} · ${CategoryType.TRICEPS.name} · ${CategoryType.ABS.name}",
            choice.rows.first { it.destination.workoutNumber == 1 }.title,
            "ranked by exercise count, ties keeping day order",
        )
        assertEquals(
            FALLBACK_TITLE,
            choice.rows.first { it.destination.workoutNumber == 2 }.title,
            "a page with no exercises has nothing to name, so it takes the formatter's fallback",
        )
        assertEquals(4, choice.rows.first { it.destination.workoutNumber == 1 }.destination.exerciseCount)
    }

    @Test
    fun aPageWithLoggedSetsIsTitledByTheSharedLoggedSetRanking() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(
            record(
                SOURCE_DATE, workoutNumber = 1, id = "logged",
                exercises = listOf(
                    // One chest exercise with 3 logged sets outranks two triceps
                    // exercises with 1 each — which the exercise-count ranking would
                    // have reversed, so this proves rankedMuscles still wins when it
                    // has an answer.
                    loggedExercise("we-1", "Bench", CategoryType.CHEST, setCount = 3),
                    loggedExercise("we-2", "Pushdown", CategoryType.TRICEPS, setCount = 1),
                    loggedExercise("we-3", "Dip", CategoryType.TRICEPS, setCount = 1),
                ),
            ),
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            "${CategoryType.CHEST.name} · ${CategoryType.TRICEPS.name}",
            choiceOf(vm).rows.first { it.destination.workoutNumber == 1 }.title,
        )
    }

    // ─── Preselection ───────────────────────────────────────────────────

    @Test
    fun theRunningPageIsTaggedAndPreselected() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(
            chestRecord(SOURCE_DATE, workoutNumber = 1),
            chestRecord(SOURCE_DATE, workoutNumber = 2, id = "r2"),
        )
        sessions.byDate[SOURCE_DATE] = listOf(session(SOURCE_DATE, workoutNumber = 2, running = true))
        val vm = viewModel()
        advanceUntilIdle()

        val choice = choiceOf(vm)
        assertEquals(2, choice.selectedWorkoutNumber)
        assertEquals(
            listOf(false, true, false),
            choice.rows.map { it.destination.isRunning },
            "only the running page is tagged, and the tag is visible and refusable",
        )
    }

    @Test
    fun withNothingRunningTheNewPageIsPreselected() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        val vm = viewModel()
        advanceUntilIdle()

        val choice = choiceOf(vm)
        assertEquals(2, choice.selectedWorkoutNumber)
        assertTrue(choice.rows.single { it.destination.workoutNumber == 2 }.destination.isNewWorkout)
    }

    @Test
    fun selectRowMovesTheSelection_andIgnoresAPageThatIsNotOffered() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        val vm = viewModel()
        advanceUntilIdle()

        vm.dispatch(RepeatPickerContract.ViewAction.SelectRow(1))
        assertEquals(1, choiceOf(vm).selectedWorkoutNumber)

        vm.dispatch(RepeatPickerContract.ViewAction.SelectRow(99))
        assertEquals(1, choiceOf(vm).selectedWorkoutNumber, "a page nobody offered cannot be selected")
    }

    // ─── Day selection ──────────────────────────────────────────────────

    @Test
    fun changingTheDayResetsTheSelectionToTheNewDaysPreselection() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(
            chestRecord(SOURCE_DATE, workoutNumber = 1),
            chestRecord(SOURCE_DATE, workoutNumber = 2, id = "r2"),
        )
        records.recordsByDate[OTHER_DATE] = listOf(chestRecord(OTHER_DATE, workoutNumber = 1, id = "o1"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.dispatch(RepeatPickerContract.ViewAction.SelectRow(1))
        assertEquals(1, choiceOf(vm).selectedWorkoutNumber)

        vm.dispatch(RepeatPickerContract.ViewAction.DateSelected(OTHER_DATE))
        assertEquals(
            RepeatPickerContract.Content.Loading,
            vm.viewState.value.content,
            "Loading is published synchronously so the new day's header never sits over the old day's rows",
        )
        advanceUntilIdle()

        assertEquals(OTHER_DATE, vm.viewState.value.selectedDate)
        val choice = choiceOf(vm)
        assertEquals(listOf(1, 2), choice.rows.map { it.destination.workoutNumber })
        assertEquals(2, choice.selectedWorkoutNumber, "the new day's own preselection, not the tapped row")
    }

    @Test
    fun aFutureDayIsIgnored() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        val vm = viewModel()
        advanceUntilIdle()
        val callsBefore = records.recordsByDateCalls.size

        val tomorrow = Clock.System.todayIn(TimeZone.currentSystemDefault()).plus(1, DateTimeUnit.DAY)
        vm.dispatch(RepeatPickerContract.ViewAction.DateSelected(tomorrow))
        advanceUntilIdle()

        assertEquals(SOURCE_DATE, vm.viewState.value.selectedDate, "you cannot have worked out tomorrow")
        assertEquals(callsBefore, records.recordsByDateCalls.size, "and no read was issued for it")
    }

    @Test
    fun reselectingTheSameDayIsANoOp() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        val vm = viewModel()
        advanceUntilIdle()
        val callsBefore = records.recordsByDateCalls.size

        vm.dispatch(RepeatPickerContract.ViewAction.DateSelected(SOURCE_DATE))
        advanceUntilIdle()

        assertEquals(callsBefore, records.recordsByDateCalls.size)
    }

    @Test
    fun aSlowOldDayReadNeverPublishesOverANewerSelection() = runTest(dispatcher) {
        // The old day would answer with a 3-page Choice — but only after a long read.
        records.recordsByDate[SOURCE_DATE] = listOf(
            chestRecord(SOURCE_DATE, workoutNumber = 1),
            chestRecord(SOURCE_DATE, workoutNumber = 2, id = "r2"),
        )
        records.readDelayByDate[SOURCE_DATE] = 5_000
        records.recordsByDate[OTHER_DATE] = listOf(chestRecord(OTHER_DATE, workoutNumber = 1, id = "o1"))
        val vm = viewModel()

        // Let the slow read actually start, then switch days while it is in flight.
        advanceTimeBy(1_000)
        assertTrue(records.recordsByDateCalls.contains(SOURCE_DATE), "the old day's read is genuinely in flight")
        vm.dispatch(RepeatPickerContract.ViewAction.DateSelected(OTHER_DATE))
        // Advance far past the slow read's completion: even fully finished, it must
        // not land — its day is no longer the selected one.
        advanceUntilIdle()

        assertEquals(OTHER_DATE, vm.viewState.value.selectedDate)
        assertEquals(
            listOf(1, 2),
            choiceOf(vm).rows.map { it.destination.workoutNumber },
            "the new day's two rows, never the old day's three",
        )
    }

    /**
     * The guard itself, not the job cancellation that usually gets there first: the
     * day flips while the read is mid-flight but AFTER its last suspension point, so
     * the read runs to completion with a result nobody asked for any more.
     */
    @Test
    fun aReadThatCompletesAfterTheDayMovedIsDiscarded() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(
            chestRecord(SOURCE_DATE, workoutNumber = 1),
            chestRecord(SOURCE_DATE, workoutNumber = 2, id = "r2"),
        )
        records.recordsByDate[OTHER_DATE] = listOf(chestRecord(OTHER_DATE, workoutNumber = 1, id = "o1"))
        val vm = viewModel()
        // The init read is only QUEUED on the test dispatcher, so the hook is in
        // place before it runs.
        sessions.onRead = { date ->
            if (date == SOURCE_DATE) {
                sessions.onRead = null // once — the replacement read must not recurse
                vm.dispatch(RepeatPickerContract.ViewAction.DateSelected(OTHER_DATE))
            }
        }
        advanceUntilIdle()

        assertEquals(OTHER_DATE, vm.viewState.value.selectedDate)
        assertEquals(
            listOf(1, 2),
            choiceOf(vm).rows.map { it.destination.workoutNumber },
            "the superseded read's three rows were dropped, not published",
        )
    }

    // ─── Panes ──────────────────────────────────────────────────────────

    @Test
    fun theCalendarIsAPaneSwapOnOneViewStateStream() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        val vm = viewModel()
        advanceUntilIdle()
        val contentBefore = vm.viewState.value.content

        vm.dispatch(RepeatPickerContract.ViewAction.ChangeDayTapped)
        assertEquals(RepeatPickerContract.Pane.Calendar, vm.viewState.value.pane)
        assertEquals(contentBefore, vm.viewState.value.content, "the destination list is kept, not rebuilt")

        vm.dispatch(RepeatPickerContract.ViewAction.CalendarBackTapped)
        assertEquals(RepeatPickerContract.Pane.Destination, vm.viewState.value.pane)
        assertEquals(contentBefore, vm.viewState.value.content)
    }

    @Test
    fun changeDayLoadsTheSelectedMonthsDots() = runTest(dispatcher) {
        records.recordsByMonth[2026 to 5] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        val vm = viewModel()
        advanceUntilIdle()

        vm.dispatch(RepeatPickerContract.ViewAction.ChangeDayTapped)
        advanceUntilIdle()

        assertEquals(
            mapOf(SOURCE_DATE to listOf(CategoryType.CHEST)),
            vm.viewState.value.workoutDays,
        )
    }

    @Test
    fun refetchingAMonthDropsADayWhoseRecordsAreGone() = runTest(dispatcher) {
        // Dots are merged across months so paging back does not blank them — but the
        // re-fetched month must be REPLACED, not merged into. A merge can only ever
        // add days, so a day emptied between two fetches (a sync pull tombstoning its
        // last record while the sheet is open) would keep a dot for a day that no
        // longer has a workout.
        records.recordsByMonth[2026 to 5] = listOf(
            chestRecord(SOURCE_DATE, workoutNumber = 1),
            chestRecord(OTHER_DATE, workoutNumber = 1, id = "o1"),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.dispatch(RepeatPickerContract.ViewAction.ChangeDayTapped)
        advanceUntilIdle()
        assertEquals(setOf(SOURCE_DATE, OTHER_DATE), vm.viewState.value.workoutDays.keys)

        // OTHER_DATE's last record is gone; re-fetch the same month.
        records.recordsByMonth[2026 to 5] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        vm.dispatch(RepeatPickerContract.ViewAction.CalendarMonthChanged(2026, 5))
        advanceUntilIdle()

        assertEquals(
            setOf(SOURCE_DATE),
            vm.viewState.value.workoutDays.keys,
            "a day emptied since the last fetch must lose its dot",
        )
    }

    @Test
    fun aThrowingMonthLeavesTheDotsAndThePaneExactlyAsTheyWere() = runTest(dispatcher) {
        records.recordsByMonth[2026 to 5] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        records.throwingMonths += 2026 to 6
        records.recordsByDate[OTHER_DATE] = listOf(chestRecord(OTHER_DATE, workoutNumber = 1, id = "o1"))
        val vm = viewModel()
        advanceUntilIdle()
        vm.dispatch(RepeatPickerContract.ViewAction.ChangeDayTapped)
        advanceUntilIdle()
        val dotsAfterMay = vm.viewState.value.workoutDays

        vm.dispatch(RepeatPickerContract.ViewAction.CalendarMonthChanged(2026, 6))
        advanceUntilIdle()

        assertEquals(dotsAfterMay, vm.viewState.value.workoutDays, "May's dots survive June's failure")
        assertEquals(
            RepeatPickerContract.Pane.Calendar,
            vm.viewState.value.pane,
            "dots are decoration — a failure never throws the user out of the calendar",
        )
        assertTrue(
            vm.viewState.value.content !is RepeatPickerContract.Content.LoadFailed,
            "and it is not a destination failure",
        )

        // And the calendar still works: picking a day commits it.
        vm.dispatch(RepeatPickerContract.ViewAction.DateSelected(OTHER_DATE))
        advanceUntilIdle()
        assertEquals(OTHER_DATE, vm.viewState.value.selectedDate)
        assertEquals(RepeatPickerContract.Pane.Destination, vm.viewState.value.pane)
    }

    // ─── Load failure ───────────────────────────────────────────────────

    @Test
    fun aThrownDayReadIsExplicitFailure_notAnEmptyDay() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        records.throwingDates += SOURCE_DATE
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            RepeatPickerContract.Content.LoadFailed,
            vm.viewState.value.content,
            "a failed read must never be shown as an empty day — that would offer 'New workout' " +
                "on a day that really holds pages",
        )
        assertFalse(vm.viewState.value.canAdd)

        vm.dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()
        assertTrue(records.copyCalls.isEmpty(), "nothing was offered, so nothing can be copied")
        assertTrue(records.maxWorkoutNumberCalls.isEmpty(), "the use case is not even entered")
        assertTrue(outcomes.isEmpty())
    }

    @Test
    fun retryAfterAFailureRepublishesTheDaysRealChoice() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(
            chestRecord(SOURCE_DATE, workoutNumber = 1),
            chestRecord(SOURCE_DATE, workoutNumber = 2, id = "r2"),
        )
        records.throwingDates += SOURCE_DATE
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(RepeatPickerContract.Content.LoadFailed, vm.viewState.value.content)
        val callsBefore = records.recordsByDateCalls.size

        records.throwingDates.clear()
        vm.dispatch(RepeatPickerContract.ViewAction.RetryLoadTapped)
        assertEquals(RepeatPickerContract.Content.Loading, vm.viewState.value.content)
        advanceUntilIdle()

        assertEquals(callsBefore + 1, records.recordsByDateCalls.size, "retry re-issues the read")
        assertEquals(
            listOf(1, 2, 3),
            choiceOf(vm).rows.map { it.destination.workoutNumber },
            "the day's real pages — NOT the empty-day Single the failure could have been mistaken for",
        )
    }

    @Test
    fun retryDoesNothingWhenThereIsNoFailureToRetry() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        val vm = viewModel()
        advanceUntilIdle()
        val callsBefore = records.recordsByDateCalls.size

        vm.dispatch(RepeatPickerContract.ViewAction.RetryLoadTapped)
        advanceUntilIdle()

        assertEquals(callsBefore, records.recordsByDateCalls.size)
    }

    // ─── Add ────────────────────────────────────────────────────────────

    @Test
    fun addOnTheChosenRowCopiesThere_andMapsToCopied() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(
            chestRecord(SOURCE_DATE, workoutNumber = 1),
            chestRecord(SOURCE_DATE, workoutNumber = 2, id = "r2"),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.dispatch(RepeatPickerContract.ViewAction.SelectRow(2))

        vm.dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()

        val call = records.copyCalls.single()
        assertEquals(SOURCE_DATE, call.sourceDate)
        assertEquals(1, call.sourceWorkoutNumber)
        assertEquals(SOURCE_DATE, call.targetDate)
        assertEquals(2, call.targetWorkoutNumber, "the row the user picked, existing-page number trusted as-is")
        assertEquals(RepeatPickerContract.Outcome.Copied(SOURCE_DATE, 2), outcomes.single())
        assertEquals(listOf(SyncReason.PostWrite.WorkoutRecord), syncTrigger.reasons)
    }

    @Test
    fun addOnAnEmptyDaysSingleCopiesToTheResolvedNewPage() = runTest(dispatcher) {
        records.maxWorkoutNumber = 3 // a Start elsewhere moved the number since the sheet drew
        val vm = viewModel(initialDate = OTHER_DATE)
        advanceUntilIdle()

        vm.dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()

        assertEquals(4, records.copyCalls.single().targetWorkoutNumber)
        assertEquals(RepeatPickerContract.Outcome.Copied(OTHER_DATE, 4), outcomes.single())
    }

    @Test
    fun doubleTappedAddDispatchesExactlyOneUseCaseCall() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        val vm = viewModel()
        advanceUntilIdle()

        // dispatch() is synchronous and onAdd flips addInProgress before it returns,
        // so the second tap is guaranteed to hit the guard.
        vm.dispatch(RepeatPickerContract.ViewAction.AddTapped)
        assertFalse(vm.viewState.value.canAdd, "the button disables the moment the first tap lands")
        vm.dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()

        assertEquals(1, records.copyCalls.size)
        assertEquals(1, outcomes.size)
    }

    @Test
    fun aSourceWorkoutWithNothingInItMapsToNothingToCopy() = runTest(dispatcher) {
        records.copyResult = false
        val vm = viewModel(initialDate = OTHER_DATE)
        advanceUntilIdle()

        vm.dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()

        assertEquals(RepeatPickerContract.Outcome.NothingToCopy, outcomes.single())
        assertTrue(syncTrigger.reasons.isEmpty(), "nothing was written, so nothing to sync")
    }

    @Test
    fun anExhaustedQuotaOnAFreshSlotMapsToRefused() = runTest(dispatcher) {
        // Without meterOn the gate resolves Unlimited WITHOUT reading the repo, so
        // this case would pass for the wrong reason.
        meterOn(limit = 3)
        records.meteredCount = 3 // exhausted
        records.hasAnyRecordInWorkoutResult = false // and the resolved slot is genuinely new
        val vm = viewModel(initialDate = OTHER_DATE)
        advanceUntilIdle()

        vm.dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()

        assertEquals(RepeatPickerContract.Outcome.Refused, outcomes.single())
        assertTrue(records.copyCalls.isEmpty(), "a refusal writes NOTHING")
    }

    @Test
    fun aThrownCopyReopensTheButtonSoTheUserCanRetry() = runTest(dispatcher) {
        records.recordsByDate[SOURCE_DATE] = listOf(chestRecord(SOURCE_DATE, workoutNumber = 1))
        // The preselected row is the new page, which the use case re-resolves from
        // this: page 1 is taken, so the copy lands on 2.
        records.maxWorkoutNumber = 1
        records.copyThrows = IllegalStateException("database is locked")
        val vm = viewModel()
        advanceUntilIdle()

        vm.dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()

        assertTrue(outcomes.isEmpty(), "a failure is not an outcome — the sheet stays open")
        assertTrue(vm.viewState.value.canAdd, "and the button is live again rather than stranded disabled")

        records.copyThrows = null
        vm.dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()

        assertEquals(RepeatPickerContract.Outcome.Copied(SOURCE_DATE, 2), outcomes.single())
    }

    // ─── Fixtures ───────────────────────────────────────────────────────

    private fun exercise(name: String, type: CategoryType) = Exercise(
        uuid = "ex-$name",
        remoteId = null,
        name = name,
        details = null,
        primaryCategory = Category("cat-${type.name}", "remote-${type.name}", type.name, type, null),
        secondaryCategories = emptyList(),
        image1 = null,
        image2 = null,
        resultType = ResultType.WEIGHT_REPS,
        isPersonal = false,
    )

    private fun unloggedExercise(id: String, name: String, type: CategoryType) = WorkoutExercise(
        id = id,
        userId = USER,
        journalId = JOURNAL,
        date = SOURCE_DATE,
        exercise = exercise(name, type),
        // Rows exist (a copied template) but carry no numbers, so nothing is "logged".
        sets = listOf(workoutSet("$id-s0", weight = null)),
        comment = null,
    )

    private fun loggedExercise(id: String, name: String, type: CategoryType, setCount: Int) = WorkoutExercise(
        id = id,
        userId = USER,
        journalId = JOURNAL,
        date = SOURCE_DATE,
        exercise = exercise(name, type),
        sets = (0 until setCount).map { workoutSet("$id-s$it", weight = 60.0) },
        comment = null,
    )

    private fun workoutSet(id: String, weight: Double?) = WorkoutSet(
        id = id,
        userId = USER,
        journalId = JOURNAL,
        date = SOURCE_DATE,
        weight = weight,
        reps = if (weight == null) null else 8,
        distance = null,
        duration = null,
        resultType = ResultType.WEIGHT_REPS,
    )

    private fun record(
        date: LocalDate,
        workoutNumber: Int,
        id: String,
        exercises: List<WorkoutExercise>,
    ) = WorkoutRecord(
        id = id,
        userId = USER,
        journalId = JOURNAL,
        position = 0,
        workoutNumber = workoutNumber,
        date = date,
        exercises = exercises,
        createdDate = AT,
        updatedDate = AT,
    )

    private fun chestRecord(date: LocalDate, workoutNumber: Int, id: String = "r$workoutNumber") =
        record(date, workoutNumber, id, listOf(loggedExercise("we-$id", "Bench", CategoryType.CHEST, setCount = 2)))

    private fun session(date: LocalDate, workoutNumber: Int, running: Boolean) = WorkoutSession(
        id = "session-$date-$workoutNumber",
        userId = USER,
        journalId = JOURNAL,
        date = date,
        workoutNumber = workoutNumber,
        startedAt = AT,
        endedAt = if (running) null else AT,
    )

    private companion object {
        const val USER = "user-1"
        const val JOURNAL = "journal-1"
        const val FALLBACK_TITLE = "fallback"
        /** Nothing under test reads a timestamp; one fixed instant keeps fixtures honest. */
        val AT: Instant = Instant.parse("2026-05-10T09:00:00Z")

        /** Fixed calendar days in the settled past, so no test drifts as the clock moves. */
        val SOURCE_DATE = LocalDate(2026, 5, 10)
        val OTHER_DATE = LocalDate(2026, 5, 3)
    }
}

// ─── Fakes ──────────────────────────────────────────────────────────────

private class FakeSyncTrigger : SyncTrigger {
    val reasons = mutableListOf<SyncReason>()
    override fun requestTick(reason: SyncReason) {
        reasons += reason
    }
}

/**
 * Settable reads + a settable quota surface. The quota members exist because the
 * suite drives a REAL [RepeatWorkoutUseCase], whose default
 * [kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate] wraps this same
 * repository. Everything the picker never touches is [unsupported].
 */
private class FakeRecordRepository : RecordRepository {

    val recordsByDate = mutableMapOf<LocalDate, List<WorkoutRecord>>()
    val throwingDates = mutableSetOf<LocalDate>()
    val readDelayByDate = mutableMapOf<LocalDate, Long>()
    val recordsByDateCalls = mutableListOf<LocalDate>()

    /** Keyed `year to month` (1-based month), matching the picker's call. */
    val recordsByMonth = mutableMapOf<Pair<Int, Int>, List<WorkoutRecord>>()
    val throwingMonths = mutableSetOf<Pair<Int, Int>>()

    // Quota surface.
    var meteredCount: Int = 0
    var hasAnyRecordInWorkoutResult: Boolean = false

    var maxWorkoutNumber: Int = 0
    val maxWorkoutNumberCalls = mutableListOf<LocalDate>()

    var copyResult: Boolean = true
    var copyThrows: Throwable? = null

    data class CopyCall(
        val sourceDate: LocalDate,
        val sourceWorkoutNumber: Int,
        val targetDate: LocalDate,
        val targetWorkoutNumber: Int,
    )

    val copyCalls = mutableListOf<CopyCall>()

    override suspend fun getRecordsByDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        includeLastOccurrence: Boolean,
    ): List<WorkoutRecord> {
        recordsByDateCalls += date
        readDelayByDate[date]?.let { delay(it) }
        if (date in throwingDates) throw IllegalStateException("read failed for $date")
        return recordsByDate[date].orEmpty()
    }

    override suspend fun getRecordsByMonth(
        userId: String,
        journalId: String,
        month: String,
        year: String,
    ): List<WorkoutRecord> {
        val key = year.toInt() to month.toInt()
        if (key in throwingMonths) throw IllegalStateException("month read failed for $key")
        return recordsByMonth[key].orEmpty()
    }

    override suspend fun countMeteredWorkouts(userId: String): Int = meteredCount

    override suspend fun hasAnyRecordInWorkout(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): Boolean = hasAnyRecordInWorkoutResult

    override suspend fun maxWorkoutNumberOnDate(userId: String, journalId: String, date: LocalDate): Int {
        maxWorkoutNumberCalls += date
        return maxWorkoutNumber
    }

    override suspend fun copyWorkoutTo(
        userId: String,
        journalId: String,
        sourceDate: LocalDate,
        sourceWorkoutNumber: Int,
        targetDate: LocalDate,
        targetWorkoutNumber: Int,
    ): Boolean {
        copyThrows?.let { throw it }
        copyCalls += CopyCall(sourceDate, sourceWorkoutNumber, targetDate, targetWorkoutNumber)
        return copyResult
    }

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("not needed by RepeatPickerViewModelTest")

    override fun observeRecordsChanged(userId: String, journalId: String): Flow<String> = flowOf()
    override suspend fun getAllRecords(userId: String, journalId: String): List<WorkoutRecord> = unsupported()
    override suspend fun getRecentRecords(userId: String, journalId: String): List<WorkoutRecord> = unsupported()
    override suspend fun getSetsForExercise(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutSet> = unsupported()

    override suspend fun getExerciseOccurrences(
        userId: String,
        journalId: String,
        exerciseId: String,
    ): List<WorkoutExercise> = unsupported()

    override suspend fun getWeightedSetHistoryForExercise(
        userId: String,
        journalId: String,
        exerciseUuid: String,
        upToDate: LocalDate,
    ): List<WeightedSetOccurrence> = unsupported()

    override suspend fun addExercisesToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
        exerciseIds: List<String>,
    ): Unit = unsupported()

    override suspend fun addRecordsToDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        records: List<WorkoutRecord>,
    ): Unit = unsupported()

    override suspend fun addRecordsFromDateToToday(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): Unit = unsupported()

    override suspend fun replaceExerciseInRecord(
        userId: String,
        journalId: String,
        recordId: String,
        targetWorkoutExerciseId: String,
        newExerciseId: String,
    ): Unit = unsupported()

    override suspend fun saveWorkoutExerciseComment(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        comment: String?,
    ): Unit = unsupported()

    override suspend fun refreshRecordPositions(
        userId: String,
        journalId: String,
        records: List<WorkoutRecord>,
    ): Unit = unsupported()

    override suspend fun mergeRecords(
        userId: String,
        journalId: String,
        firstRecord: WorkoutRecord,
        secondRecord: WorkoutRecord,
    ): List<WorkoutRecord> = unsupported()

    override suspend fun removeExerciseFromRecord(
        userId: String,
        journalId: String,
        record: WorkoutRecord,
        exercise: WorkoutExercise,
    ): List<WorkoutRecord> = unsupported()

    override suspend fun deleteRecord(userId: String, journalId: String, record: WorkoutRecord): Unit = unsupported()
    override suspend fun deleteRecordsForDate(userId: String, journalId: String, date: LocalDate): Unit = unsupported()
    override suspend fun deleteUserRecords(userId: String): Unit = unsupported()

    override suspend fun addSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
    ): Unit = unsupported()

    override suspend fun updateSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
        weight: Double?,
        reps: Int?,
        distance: Double?,
        duration: Int?,
    ): Boolean = unsupported()

    override suspend fun deleteSet(
        userId: String,
        journalId: String,
        workoutExerciseId: String,
        setId: String,
    ): Boolean = unsupported()
}

private class FakeSessionRepository : WorkoutSessionRepository {

    val byDate = mutableMapOf<LocalDate, List<WorkoutSession>>()

    /**
     * Fired at the END of a day read, so a test can move the selection at the one
     * moment the in-flight job cannot be cancelled out of publishing.
     */
    var onRead: ((LocalDate) -> Unit)? = null

    override suspend fun getSessionsForDay(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): List<WorkoutSession> {
        val day = byDate[date].orEmpty().sortedBy { it.workoutNumber }
        onRead?.invoke(date)
        return day
    }

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("not needed by RepeatPickerViewModelTest")

    override suspend fun getSessionByWorkoutNumber(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): WorkoutSession? = unsupported()

    override fun getSessionsForDayFlow(
        userId: String,
        journalId: String,
        date: LocalDate,
    ): Flow<List<WorkoutSession>> = unsupported()

    override suspend fun getRunningSession(userId: String): WorkoutSession? = unsupported()
    override fun getRunningSessionFlow(userId: String): Flow<WorkoutSession?> = unsupported()

    override suspend fun countCompletedSessionsBetween(
        userId: String,
        journalId: String,
        from: LocalDate,
        to: LocalDate,
        excludeSessionUuid: String,
    ): Int = unsupported()

    override suspend fun startSession(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): WorkoutSession = unsupported()

    override suspend fun endSession(userId: String, endedAt: Instant?): WorkoutSession? = unsupported()
    override suspend fun deleteSession(userId: String, sessionUuid: String): Unit = unsupported()
    override suspend fun deleteUserSessions(userId: String): Unit = unsupported()
}
