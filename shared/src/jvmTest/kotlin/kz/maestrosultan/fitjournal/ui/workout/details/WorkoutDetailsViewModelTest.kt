package kz.maestrosultan.fitjournal.ui.workout.details

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kz.maestrosultan.fitjournal.domain.quota.FreeQuotaSettings
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.summary.DetectSessionBestUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.WeightedSetOccurrence
import kz.maestrosultan.fitjournal.domain.workout.usecase.DeleteWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.RepeatWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.SetWorkoutNoteUseCase
import kz.maestrosultan.fitjournal.ui.workout.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.workout.WorkoutUserContext
import kz.maestrosultan.fitjournal.ui.workout.details.components.WorkoutDetailsStrings
import kz.maestrosultan.fitjournal.ui.workout.repeat.RepeatPickerContract

/**
 * Pure-Kotlin (no SQLite) coverage of [WorkoutDetailsViewModel]'s ORCHESTRATION —
 * the reactive pipeline, action handling, and strand-proofing (design spec §6,
 * §13, §15). Repositories are hand-rolled in-memory fakes rather than the real
 * SQLite-backed ones: this test is about the VM wiring, not repository SQL
 * (that is `DeleteWorkoutUseCaseTest` / `RecordRepositoryTest`'s job), and a
 * fake gives full deterministic control over WHEN a reactive signal re-fires —
 * essential for the strand-proofing test. [MuscleTitleFormatter] /
 * [WorkoutDetailsStrings] are injected deterministically (no compose-resource
 * loading, no locale dependence), mirroring `WorkoutSuccessViewModelTest`.
 */
class WorkoutDetailsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val createdViewModels = mutableListOf<WorkoutDetailsViewModel>()

    @BeforeTest
    fun installMain() {
        Dispatchers.setMain(dispatcher)
        // FreeQuotaSettings is a global object and jvmTest runs every class in one
        // JVM — a leaked metered state would silently refuse a later class's writes.
        // Reset on BOTH sides so neither this suite's order nor another suite's
        // leakage can change an answer (same discipline as WorkoutQuotaGateTest).
        FreeQuotaSettings.reset()
    }

    @AfterTest
    fun tearDown() {
        createdViewModels.forEach { it.dispose() }
        createdViewModels.clear()
        FreeQuotaSettings.reset()
        Dispatchers.resetMain()
    }

    /** Meter a NEVER-SUBSCRIBER with the shipping limit. */
    private fun meterOn(limit: Long = FREE_LIMIT) {
        FreeQuotaSettings.setLimit(limit)
        FreeQuotaSettings.setHasEverSubscribed(false)
    }

    // ─── Fixture ────────────────────────────────────────────────────────

    private val formatter = MuscleTitleFormatter(
        categoryName = { "cat-${it.name}" },
        fallbackTitle = { "fallback" },
    )
    private val strings = WorkoutDetailsStrings(
        workoutCount = { "$it workouts" },
        exerciseCount = { "$it exercises" },
        setCount = { "$it sets" },
    )

    private fun viewModel(
        records: FakeRecordRepository,
        sessions: FakeWorkoutSessionRepository,
        syncTrigger: SyncTrigger = FakeSyncTrigger(),
        initialWorkoutNumber: Int? = null,
        headerNav: WorkoutDetailsContract.HeaderNav = WorkoutDetailsContract.HeaderNav.Back,
        variant: WorkoutDetailsContract.Variant = WorkoutDetailsContract.Variant.Details,
    ): WorkoutDetailsViewModel = WorkoutDetailsViewModel(
        recordRepository = records,
        sessionRepository = sessions,
        detectSessionBest = DetectSessionBestUseCase(records),
        deleteWorkout = DeleteWorkoutUseCase(records, syncTrigger),
        repeatWorkout = RepeatWorkoutUseCase(records, syncTrigger),
        setWorkoutNote = SetWorkoutNoteUseCase(records, syncTrigger),
        userContext = FakeUserContext(USER_ID, JOURNAL_ID, MeasurementSystem.KG_KM),
        date = DATE,
        initialWorkoutNumber = initialWorkoutNumber,
        headerNav = headerNav,
        variant = variant,
        muscleTitleFormatter = formatter,
        strings = strings,
        // The Repeat picker opens on TODAY, so today has to be a fixture value: with
        // the real clock the suite would behave differently on 15 January, the one
        // day where today IS the day under test and already holds records.
        clock = object : Clock { override fun now(): Instant = NOW },
        timeZone = TimeZone.UTC,
    ).also { createdViewModels += it }

    private suspend fun awaitLoaded(vm: WorkoutDetailsViewModel): WorkoutDetailsContract.Content.Loaded =
        vm.viewState.first { it.content is WorkoutDetailsContract.Content.Loaded }
            .content as WorkoutDetailsContract.Content.Loaded

    /**
     * Taps Repeat and waits for the child picker's own day load to resolve — Add is
     * refused until it has, so `canAdd` IS the "the sheet is ready" signal.
     */
    private suspend fun openRepeatPicker(vm: WorkoutDetailsViewModel): RepeatPickerContract.ViewModel {
        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatTapped)
        val picker = vm.viewState.first { it.repeatPicker != null }.repeatPicker!!
        assertFalse(picker.closing, "a freshly opened sheet is not closing")
        picker.viewModel.viewState.first { it.canAdd }
        return picker.viewModel
    }

    /**
     * A never-subscriber who has spent the whole free allowance, on a destination
     * workout that does not exist yet — the one combination the gate refuses (rule 3
     * keeps an EXISTING workout writable however exhausted the user is).
     */
    private fun exhaustedRecords() = FakeRecordRepository(listOf(squatRecord(1))).apply {
        meteredWorkoutCount = FREE_LIMIT.toInt()
        slotHoldsRecords = false
    }

    // ─── Happy path ─────────────────────────────────────────────────────

    @Test
    fun loadHappyPath_singleWorkout_rendersLoadedContent() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(workoutNumber = 1)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", workoutNumber = 1)))

        val loaded = awaitLoaded(viewModel(records, sessions))

        assertEquals(DATE, loaded.date)
        assertEquals(1, loaded.focusedWorkoutNumber)
        assertEquals(listOf(1), loaded.workouts.map { it.workoutNumber })
        assertTrue(loaded.stack.isEmpty(), "a single-workout day has no WD3 stack")
        val workout = loaded.workouts.single()
        assertEquals("1:04:00", workout.durationText, "09:38–10:42 session, formatDuration rule (h:mm:ss at/above an hour)")
        assertTrue(workout.canShare, "records present, so the share composer can be built")
        assertEquals(1, workout.note.workoutNumber, "the note is keyed by the workout page")
        assertNull(workout.note.text, "no note set -> add-note placeholder")
    }

    // ─── SelectWorkout ──────────────────────────────────────────────────

    @Test
    fun selectWorkout_refocusesToTheTappedWorkout() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1), benchRecord(2)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1), session("session-2", 2)))
        val vm = viewModel(records, sessions)
        val initial = awaitLoaded(vm)
        assertEquals(1, initial.focusedWorkoutNumber, "fixture sanity: starts focused on the lowest workout")

        vm.dispatch(WorkoutDetailsContract.ViewAction.SelectWorkout(2))

        val loaded = vm.viewState
            .first { (it.content as? WorkoutDetailsContract.Content.Loaded)?.focusedWorkoutNumber == 2 }
            .content as WorkoutDetailsContract.Content.Loaded
        assertEquals(2, loaded.focusedWorkoutNumber)
        assertEquals(2, loaded.workouts.size, "both workouts stay rendered — only the focus moved")
        assertEquals(2, loaded.stack.size, "WD3 stack lists every workout of the day")
    }

    // ─── Delete ─────────────────────────────────────────────────────────

    @Test
    fun deleteConfirmed_twoWorkoutDay_keepsScreen_andRefocusesToLowestRemaining() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1), benchRecord(2)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1), session("session-2", 2)))
        val trigger = FakeSyncTrigger()
        val vm = viewModel(records, sessions, syncTrigger = trigger)
        awaitLoaded(vm) // focused = 1 (the lowest)

        vm.dispatch(WorkoutDetailsContract.ViewAction.DeleteTapped)
        assertTrue(vm.viewState.first { it.confirmingDelete }.confirmingDelete)
        vm.dispatch(WorkoutDetailsContract.ViewAction.DeleteConfirmed)

        val loaded = vm.viewState
            .first { (it.content as? WorkoutDetailsContract.Content.Loaded)?.workouts?.size == 1 }
            .content as WorkoutDetailsContract.Content.Loaded
        assertEquals(listOf(2), loaded.workouts.map { it.workoutNumber }, "workout #1 is gone, #2 survives")
        assertEquals(2, loaded.focusedWorkoutNumber, "focus falls back to the lowest remaining workout")
        assertFalse(vm.viewState.value.confirmingDelete, "the confirm sheet closed on tap, before the write even started")
        assertEquals(listOf<SyncReason>(SyncReason.PostWrite.WorkoutRecord), trigger.reasons)
    }

    @Test
    fun deleteConfirmed_lastWorkout_emitsDismiss() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1)))
        val vm = viewModel(records, sessions)
        awaitLoaded(vm)

        vm.dispatch(WorkoutDetailsContract.ViewAction.DeleteConfirmed)

        assertEquals(WorkoutDetailsContract.ViewEffect.Dismiss, vm.viewEffect.first())
    }

    // ─── Note ───────────────────────────────────────────────────────────

    @Test
    fun noteSaved_writesThroughToTheRecordRepo_andClearsTheEditor() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1)))
        val trigger = FakeSyncTrigger()
        val vm = viewModel(records, sessions, trigger)
        awaitLoaded(vm)

        vm.dispatch(WorkoutDetailsContract.ViewAction.NoteTapped)
        val editor = vm.viewState.first { it.noteEditor != null }.noteEditor
        assertEquals(1, editor?.workoutNumber, "the editor is keyed by the workout page, not a session")
        assertEquals("", editor?.initialText, "no note yet — the empty state seeds an empty editor")

        vm.dispatch(WorkoutDetailsContract.ViewAction.NoteSaved("Felt strong today"))

        assertNull(vm.viewState.first { it.noteEditor == null }.noteEditor)
        assertEquals("Felt strong today", records.getWorkoutNote(USER_ID, JOURNAL_ID, DATE, 1), "note saved to the record repo, not the session")
        val loaded = vm.viewState
            .first { (it.content as? WorkoutDetailsContract.Content.Loaded)?.workouts?.single()?.note?.text == "Felt strong today" }
            .content as WorkoutDetailsContract.Content.Loaded
        assertEquals("Felt strong today", loaded.workouts.single().note.text, "the notes flow re-emitted the new text")
        // The whole reason the save goes through SetWorkoutNoteUseCase: without
        // the tick a note only reaches AWS on the next cold start / foreground /
        // periodic tick.
        assertEquals(listOf<SyncReason>(SyncReason.PostWrite.WorkoutNote), trigger.reasons)
    }

    // ─── Edit / Share carry the focused workout ────────────────────────

    @Test
    fun shareAndEditTapped_carryTheFocusedWorkoutNumber() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1), benchRecord(2)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1), session("session-2", 2)))
        val vm = viewModel(records, sessions)
        awaitLoaded(vm)
        vm.dispatch(WorkoutDetailsContract.ViewAction.SelectWorkout(2))
        vm.viewState.first { (it.content as? WorkoutDetailsContract.Content.Loaded)?.focusedWorkoutNumber == 2 }

        vm.dispatch(WorkoutDetailsContract.ViewAction.EditTapped)
        assertEquals(WorkoutDetailsContract.ViewEffect.OpenEditWorkout(DATE, 2), vm.viewEffect.first())

        vm.dispatch(WorkoutDetailsContract.ViewAction.ShareTapped)
        assertEquals(WorkoutDetailsContract.ViewEffect.OpenShareComposer(DATE, 2), vm.viewEffect.first())
    }

    // ─── Repeat: the close handshake ────────────────────────────────────
    //
    // Repeat opens a PICKER; this screen owns only the sheet's lifecycle and the
    // outcome it hands back. The outcome is never acted on when it arrives — it is
    // parked until the screen reports the sheet has finished hiding
    // (RepeatPickerClosed), so a paywall can never appear over a visible sheet.
    // Every case below therefore runs the REAL picker VM, the REAL
    // RepeatWorkoutUseCase and the REAL quota gate over the fake repository.

    @Test
    fun repeatTapped_opensThePicker_andASecondTapDoesNotStackASecondSheet() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1)))
        val vm = viewModel(records, FakeWorkoutSessionRepository(listOf(session("session-1", 1))))
        awaitLoaded(vm)

        val picker = openRepeatPicker(vm)

        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatTapped)
        advanceUntilIdle()

        assertSame(picker, vm.viewState.value.repeatPicker?.viewModel, "a second tap must not build a second picker")
    }

    @Test
    fun repeatRefused_marksTheSheetClosing_andHOLDSThePaywallUntilItHasHidden() = runTest(dispatcher) {
        meterOn()
        val records = exhaustedRecords()
        val vm = viewModel(records, FakeWorkoutSessionRepository(listOf(session("session-1", 1))))
        awaitLoaded(vm)
        val effects = mutableListOf<WorkoutDetailsContract.ViewEffect>()
        val job = launch { vm.viewEffect.collect { effects += it } }

        val picker = openRepeatPicker(vm)
        picker.dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()

        val closing = vm.viewState.value.repeatPicker
        assertNotNull(closing, "the sheet stays composed so the screen can animate it out")
        assertTrue(closing.closing, "an outcome arrived, so the sheet is on its way out")
        assertTrue(effects.isEmpty(), "NO paywall while the sheet is still on screen")
        assertEquals(0, records.repeatCount, "refused: nothing may be copied")

        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerClosed)
        advanceUntilIdle()

        assertNull(vm.viewState.value.repeatPicker, "the acknowledgement tears the picker down")
        assertEquals(listOf<WorkoutDetailsContract.ViewEffect>(WorkoutDetailsContract.ViewEffect.ShowPaywall), effects)
        job.cancel()
    }

    @Test
    fun repeatPickerClosed_twice_raisesThePaywallEXACTLYonce() = runTest(dispatcher) {
        // Both the sheet's own onDismiss and the host's animation callback can land,
        // so the acknowledgement has to be idempotent: the pending outcome is cleared
        // before anything is emitted, which is what makes the duplicate emit nothing.
        meterOn()
        val vm = viewModel(exhaustedRecords(), FakeWorkoutSessionRepository(listOf(session("session-1", 1))))
        awaitLoaded(vm)
        val effects = mutableListOf<WorkoutDetailsContract.ViewEffect>()
        val job = launch { vm.viewEffect.collect { effects += it } }

        openRepeatPicker(vm).dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()
        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerClosed)
        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerClosed)
        advanceUntilIdle()

        assertEquals(listOf<WorkoutDetailsContract.ViewEffect>(WorkoutDetailsContract.ViewEffect.ShowPaywall), effects)
        job.cancel()
    }

    @Test
    fun repeatCopied_opensWhereItLanded_onlyAfterTheSheetHasHidden() = runTest(dispatcher) {
        // Reset settings mean an unmetered user, so the gate inside the use case allows
        // it. Nothing is logged on TODAY, so the picker's one destination is a new page.
        val records = FakeRecordRepository(listOf(squatRecord(1)))
        val vm = viewModel(records, FakeWorkoutSessionRepository(listOf(session("session-1", 1))))
        awaitLoaded(vm)
        val effects = mutableListOf<WorkoutDetailsContract.ViewEffect>()
        val job = launch { vm.viewEffect.collect { effects += it } }

        openRepeatPicker(vm).dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()

        assertEquals(DATE, records.repeatedFrom, "copies the page the screen is showing")
        assertEquals(1, records.repeatedWorkoutNumber)
        assertEquals(TODAY, records.copiedToDate, "the picker's only destination is a new page on today")
        assertEquals(1, records.copiedToWorkoutNumber, "today holds nothing, so the new page is #1")
        assertEquals(true, vm.viewState.value.repeatPicker?.closing)
        assertTrue(effects.isEmpty(), "the copy landed, but the sheet is still up")

        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerClosed)
        advanceUntilIdle()

        assertNull(vm.viewState.value.repeatPicker)
        assertEquals(
            listOf<WorkoutDetailsContract.ViewEffect>(WorkoutDetailsContract.ViewEffect.OpenEditWorkout(TODAY, 1)),
            effects,
            "opens the slot the copy actually landed in",
        )
        job.cancel()
    }

    @Test
    fun repeatWithNothingToCopy_closesTheSheet_andEmitsNOTHING() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1))).apply { copySucceeds = false }
        val vm = viewModel(records, FakeWorkoutSessionRepository(listOf(session("session-1", 1))))
        awaitLoaded(vm)
        val effects = mutableListOf<WorkoutDetailsContract.ViewEffect>()
        val job = launch { vm.viewEffect.collect { effects += it } }

        openRepeatPicker(vm).dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()
        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerClosed)
        advanceUntilIdle()

        assertNull(vm.viewState.value.repeatPicker)
        assertTrue(effects.isEmpty(), "nothing was written and nothing was refused — the sheet just goes away")
        job.cancel()
    }

    @Test
    fun repeatPickerDismissed_whileClosing_isIGNORED_andThePaywallStillArrives() = runTest(dispatcher) {
        // Hosts fire their dismiss callback as part of the very exit animation the
        // outcome started. Honouring it would null the picker before the
        // acknowledgement arrives — and drop the pending paywall on the floor.
        meterOn()
        val vm = viewModel(exhaustedRecords(), FakeWorkoutSessionRepository(listOf(session("session-1", 1))))
        awaitLoaded(vm)
        val effects = mutableListOf<WorkoutDetailsContract.ViewEffect>()
        val job = launch { vm.viewEffect.collect { effects += it } }

        openRepeatPicker(vm).dispatch(RepeatPickerContract.ViewAction.AddTapped)
        advanceUntilIdle()
        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerDismissed)
        advanceUntilIdle()

        val stillThere = vm.viewState.value.repeatPicker
        assertNotNull(stillThere, "the handshake owns teardown once an outcome is pending")
        assertTrue(stillThere.closing)
        assertTrue(effects.isEmpty())

        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerClosed)
        advanceUntilIdle()

        assertNull(vm.viewState.value.repeatPicker)
        assertEquals(
            listOf<WorkoutDetailsContract.ViewEffect>(WorkoutDetailsContract.ViewEffect.ShowPaywall),
            effects,
            "the outcome survived the racing dismiss",
        )
        job.cancel()
    }

    @Test
    fun repeatPickerDismissed_withNoOutcome_closesIt_andAStrayClosedNoOps() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1)))
        val vm = viewModel(records, FakeWorkoutSessionRepository(listOf(session("session-1", 1))))
        awaitLoaded(vm)
        val effects = mutableListOf<WorkoutDetailsContract.ViewEffect>()
        val job = launch { vm.viewEffect.collect { effects += it } }

        openRepeatPicker(vm)
        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerDismissed)
        advanceUntilIdle()
        assertNull(vm.viewState.value.repeatPicker, "the user swiped it away — no outcome to wait for")

        vm.dispatch(WorkoutDetailsContract.ViewAction.RepeatPickerClosed)
        advanceUntilIdle()

        assertNull(vm.viewState.value.repeatPicker)
        assertTrue(effects.isEmpty(), "a stray acknowledgement finds no picker and consumes nothing")
        assertEquals(0, records.repeatCount)
        job.cancel()
    }

    // ─── Summary vs Details variant ─────────────────────────────────────

    @Test
    fun summaryVariant_showsOnlyTheFinishedWorkout_noPickerNoActions() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1), benchRecord(2)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1), session("session-2", 2)))
        val vm = viewModel(records, sessions, initialWorkoutNumber = 2, variant = WorkoutDetailsContract.Variant.Summary)
        val loaded = awaitLoaded(vm)

        assertEquals(listOf(2), loaded.workouts.map { it.workoutNumber }, "only the finished workout is shown")
        assertTrue(loaded.stack.isEmpty(), "no picker in Summary")
        assertEquals(false, vm.viewState.value.showActions, "no Edit/Repeat/Delete in Summary")
    }

    @Test
    fun detailsVariant_showsTheWholeDay_withPickerAndActions() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1), benchRecord(2)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1), session("session-2", 2)))
        val vm = viewModel(records, sessions, initialWorkoutNumber = 2) // Details is the default

        val loaded = awaitLoaded(vm)
        assertEquals(listOf(1, 2), loaded.workouts.map { it.workoutNumber }, "the whole day")
        assertTrue(loaded.stack.isNotEmpty(), "picker present for a multi-workout day")
        assertEquals(true, vm.viewState.value.showActions, "actions shown in Details")
    }

    @Test
    fun navTapped_dismissesOnce_ignoresRapidDoubleTap() = runTest(dispatcher) {
        // A rapid double Close (or Close then system back) during the exit
        // animation must emit ONE Dismiss — a second would over-pop the back stack.
        val records = FakeRecordRepository(listOf(squatRecord(1)))
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1)))
        val vm = viewModel(records, sessions)
        awaitLoaded(vm)

        val effects = mutableListOf<WorkoutDetailsContract.ViewEffect>()
        val job = launch { vm.viewEffect.collect { effects.add(it) } }

        vm.dispatch(WorkoutDetailsContract.ViewAction.NavTapped)
        vm.dispatch(WorkoutDetailsContract.ViewAction.NavTapped)
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf<WorkoutDetailsContract.ViewEffect>(WorkoutDetailsContract.ViewEffect.Dismiss), effects)
    }

    // ─── Empty day ──────────────────────────────────────────────────────

    @Test
    fun emptyFirstLoad_dismissesImmediately() = runTest(dispatcher) {
        val records = FakeRecordRepository(emptyList())
        val sessions = FakeWorkoutSessionRepository(emptyList())

        val vm = viewModel(records, sessions)

        assertEquals(WorkoutDetailsContract.ViewEffect.Dismiss, vm.viewEffect.first())
    }

    // ─── Strand-proofing (§13) ──────────────────────────────────────────

    /**
     * Fault-primes the builder's OWN input: [FakeRecordRepository.getWeightedSetHistoryForExercise]
     * (a real dependency [DetectSessionBestUseCase] calls while assembling
     * `sessionBests`, INSIDE the pipeline's per-emission `runCatching`) throws
     * for the first emission. The state must stay at [WorkoutDetailsContract.Content.Loading]
     * (never crash, never strand) and the NEXT repository signal — after the
     * fault is cleared — must still yield [WorkoutDetailsContract.Content.Loaded].
     */
    @Test
    fun recoveryAfterAFailedRefresh_pipelineSurvives_nextSignalYieldsLoaded() = runTest(dispatcher) {
        val records = FakeRecordRepository(listOf(squatRecord(1))).apply { failWeightedHistoryLookup = true }
        val sessions = FakeWorkoutSessionRepository(listOf(session("session-1", 1)))

        val vm = viewModel(records, sessions)

        records.historyLookupAttempts.first { it >= 1 }
        assertEquals(
            WorkoutDetailsContract.Content.Loading,
            vm.viewState.value.content,
            "a failed rebuild must drop only that emission, not crash or replace Loading",
        )

        records.failWeightedHistoryLookup = false
        records.bump()

        val loaded = awaitLoaded(vm)
        assertEquals(listOf(1), loaded.workouts.map { it.workoutNumber }, "the next signal recovers to a normal Loaded state")
    }

    // ─── Fixture builders ───────────────────────────────────────────────

    private fun category(type: CategoryType) = Category(
        uuid = "cat-${type.name}",
        remoteId = "remote-cat-${type.name}",
        name = type.name,
        type = type,
        details = null,
    )

    private fun exercise(name: String, type: CategoryType) = Exercise(
        uuid = "ex-$name",
        remoteId = null,
        name = name,
        details = null,
        primaryCategory = category(type),
        secondaryCategories = emptyList(),
        image1 = null,
        image2 = null,
        resultType = ResultType.WEIGHT_REPS,
        isPersonal = false,
    )

    private fun loggedSet(id: String, weight: Double, reps: Int) = WorkoutSet(
        id = id,
        userId = USER_ID,
        journalId = JOURNAL_ID,
        date = DATE,
        weight = weight,
        reps = reps,
        distance = null,
        duration = null,
        resultType = ResultType.WEIGHT_REPS,
    )

    private fun workoutExercise(id: String, ex: Exercise, sets: List<WorkoutSet>) = WorkoutExercise(
        id = id,
        userId = USER_ID,
        journalId = JOURNAL_ID,
        date = DATE,
        exercise = ex,
        sets = sets,
        comment = null,
    )

    private fun record(id: String, workoutNumber: Int, exercises: List<WorkoutExercise>) = WorkoutRecord(
        id = id,
        userId = USER_ID,
        journalId = JOURNAL_ID,
        position = 0,
        workoutNumber = workoutNumber,
        date = DATE,
        exercises = exercises,
        createdDate = START,
        updatedDate = START,
    )

    private fun session(id: String, workoutNumber: Int) = WorkoutSession(
        id = id,
        userId = USER_ID,
        journalId = JOURNAL_ID,
        date = DATE,
        workoutNumber = workoutNumber,
        startedAt = START,
        endedAt = END,
    )

    private fun squatRecord(workoutNumber: Int) = record(
        id = "record-squat-$workoutNumber",
        workoutNumber = workoutNumber,
        exercises = listOf(
            workoutExercise(
                "we-squat-$workoutNumber",
                exercise("Squat", CategoryType.QUADRICEPS),
                listOf(loggedSet("set-squat-$workoutNumber", 100.0, 5)),
            ),
        ),
    )

    private fun benchRecord(workoutNumber: Int) = record(
        id = "record-bench-$workoutNumber",
        workoutNumber = workoutNumber,
        exercises = listOf(
            workoutExercise(
                "we-bench-$workoutNumber",
                exercise("Bench Press", CategoryType.CHEST),
                listOf(loggedSet("set-bench-$workoutNumber", 60.0, 10)),
            ),
        ),
    )

    // ─── Fakes ──────────────────────────────────────────────────────────

    private class FakeUserContext(
        private val userId: String,
        private val journalId: String,
        private val measurementSystem: MeasurementSystem,
    ) : WorkoutUserContext {
        override suspend fun userId(): String = userId
        override suspend fun journalId(): String = journalId
        override suspend fun measurementSystem(): MeasurementSystem = measurementSystem
    }

    private class FakeSyncTrigger : SyncTrigger {
        val reasons = mutableListOf<SyncReason>()
        override fun requestTick(reason: SyncReason) {
            reasons.add(reason)
        }
    }

    /**
     * In-memory [RecordRepository]. [observeRecordsChanged] is backed by its OWN
     * monotonic counter ([bump]/writes) rather than derived from [records]'
     * content-equality, so a test can force a fresh reactive signal without
     * needing the underlying data to actually change (the recovery test).
     * Every method beyond what the VM's pipeline + [DeleteWorkoutUseCase]'s
     * default `deleteWorkoutAtomic` composition touch is intentionally
     * [unsupported] — this fake is scoped to what this test exercises.
     */
    private class FakeRecordRepository(
        initial: List<WorkoutRecord>,
    ) : RecordRepository {

        private val records = MutableStateFlow(initial)
        private val changeSignal = MutableStateFlow(0)

        /** Set true to make [getWeightedSetHistoryForExercise] fail (the strand-proofing test). */
        var failWeightedHistoryLookup = false

        /** How many times [getWeightedSetHistoryForExercise] has been entered — proves an attempt actually ran. */
        val historyLookupAttempts = MutableStateFlow(0)

        /** Source date of the last copy. */
        var repeatedFrom: LocalDate? = null

        /** Forces a fresh [observeRecordsChanged] emission without mutating [records]. */
        fun bump() {
            changeSignal.update { it + 1 }
        }

        override fun observeRecordsChanged(userId: String, journalId: String): Flow<String> =
            changeSignal.map { it.toString() }

        override suspend fun getRecordsByDate(
            userId: String,
            journalId: String,
            date: LocalDate,
            includeLastOccurrence: Boolean,
        ): List<WorkoutRecord> =
            records.value.filter { it.userId == userId && it.journalId == journalId && it.date == date }

        override suspend fun getWeightedSetHistoryForExercise(
            userId: String,
            journalId: String,
            exerciseUuid: String,
            upToDate: LocalDate,
        ): List<WeightedSetOccurrence> {
            historyLookupAttempts.update { it + 1 }
            if (failWeightedHistoryLookup) throw IllegalStateException("forced failure for the recovery test")
            return emptyList()
        }

        override suspend fun deleteRecord(userId: String, journalId: String, record: WorkoutRecord) {
            records.value = records.value.filterNot { it.id == record.id }
            changeSignal.update { it + 1 }
        }

        // In-memory workout notes keyed by workoutNumber (the screen is one day).
        private val notes = MutableStateFlow<Map<Int, String>>(emptyMap())
        override fun getWorkoutNotesForDayFlow(userId: String, journalId: String, date: LocalDate): Flow<Map<Int, String>> = notes
        override suspend fun getWorkoutNote(userId: String, journalId: String, date: LocalDate, workoutNumber: Int): String? =
            notes.value[workoutNumber]
        override suspend fun setWorkoutNote(userId: String, journalId: String, date: LocalDate, workoutNumber: Int, text: String) {
            notes.update { if (text.isBlank()) it - workoutNumber else it + (workoutNumber to text.trim()) }
        }
        override suspend fun clearWorkoutNote(userId: String, journalId: String, date: LocalDate, workoutNumber: Int) {
            notes.update { it - workoutNumber }
        }

        override suspend fun getAllRecords(userId: String, journalId: String): List<WorkoutRecord> = unsupported()

        // The Repeat picker's calendar reads by month; empty months are normal here.
        override suspend fun getRecordsByMonth(userId: String, journalId: String, month: String, year: String): List<WorkoutRecord> =
            records.value.filter {
                it.userId == userId && it.journalId == journalId &&
                    it.date.monthNumber == month.toInt() && it.date.year == year.toInt()
            }
        override suspend fun getRecentRecords(userId: String, journalId: String): List<WorkoutRecord> = unsupported()
        override suspend fun getSetsForExercise(userId: String, journalId: String, exerciseId: String): List<WorkoutSet> = unsupported()
        override suspend fun getExerciseOccurrences(userId: String, journalId: String, exerciseId: String): List<WorkoutExercise> = unsupported()

        override suspend fun addExercisesToDate(
            userId: String,
            journalId: String,
            date: LocalDate,
            workoutNumber: Int,
            exerciseIds: List<String>,
        ): Unit = unsupported()

        override suspend fun addRecordsToDate(userId: String, journalId: String, date: LocalDate, records: List<WorkoutRecord>): Unit = unsupported()
        override suspend fun addRecordsFromDateToToday(userId: String, journalId: String, date: LocalDate) {
            repeatedFrom = date
        }

        // ─── Quota surface ─────────────────────────────────────────────
        // The picker VM, RepeatWorkoutUseCase and WorkoutQuotaGate the VM builds are
        // all REAL, and every question the gate asks lands here.

        /** How many workouts the metered user has already spent. */
        var meteredWorkoutCount = 0

        /** Gate rule 3: does the destination workout already exist? False = a new page. */
        var slotHoldsRecords = true

        override suspend fun countMeteredWorkouts(userId: String): Int = meteredWorkoutCount

        override suspend fun hasAnyRecordInWorkout(
            userId: String,
            journalId: String,
            date: LocalDate,
            workoutNumber: Int,
        ): Boolean = slotHoldsRecords

        // ─── Repeat surface ────────────────────────────────────────────

        /** Which SOURCE page Repeat copied. */
        var repeatedWorkoutNumber: Int? = null

        /** False makes the copy report "the source workout holds no records". */
        var copySucceeds = true

        /** The slot the copy actually landed in — what OpenEditWorkout must name. */
        var copiedToDate: LocalDate? = null
        var copiedToWorkoutNumber: Int? = null

        /** How many times Repeat actually copied — proves a refusal writes nothing. */
        var repeatCount = 0

        override suspend fun maxWorkoutNumberOnDate(userId: String, journalId: String, date: LocalDate): Int =
            records.value
                .filter { it.userId == userId && it.journalId == journalId && it.date == date }
                .maxOfOrNull { it.workoutNumber } ?: 0

        override suspend fun copyWorkoutTo(
            userId: String,
            journalId: String,
            sourceDate: LocalDate,
            sourceWorkoutNumber: Int,
            targetDate: LocalDate,
            targetWorkoutNumber: Int,
        ): Boolean {
            if (!copySucceeds) return false
            repeatedFrom = sourceDate
            repeatedWorkoutNumber = sourceWorkoutNumber
            copiedToDate = targetDate
            copiedToWorkoutNumber = targetWorkoutNumber
            repeatCount++
            return true
        }

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

        override suspend fun refreshRecordPositions(userId: String, journalId: String, records: List<WorkoutRecord>): Unit = unsupported()

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

        override suspend fun deleteSet(userId: String, journalId: String, workoutExerciseId: String, setId: String): Boolean = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by WorkoutDetailsViewModelTest")
    }

    /** In-memory [WorkoutSessionRepository]; scoped to what the VM's pipeline + NoteSaved touch. */
    private class FakeWorkoutSessionRepository(
        initial: List<WorkoutSession>,
    ) : WorkoutSessionRepository {
        val sessions = MutableStateFlow(initial)

        override fun getSessionsForDayFlow(userId: String, journalId: String, date: LocalDate): Flow<List<WorkoutSession>> =
            sessions.map { list -> list.filter { it.userId == userId && it.journalId == journalId && it.date == date } }

        /** The Repeat picker reads the destination day to build its rows. */
        override suspend fun getSessionsForDay(userId: String, journalId: String, date: LocalDate): List<WorkoutSession> =
            sessions.value.filter { it.userId == userId && it.journalId == journalId && it.date == date }

        override suspend fun getSessionByWorkoutNumber(userId: String, journalId: String, date: LocalDate, workoutNumber: Int): WorkoutSession? = unsupported()
        override suspend fun getRunningSession(userId: String): WorkoutSession? = unsupported()
        override fun getRunningSessionFlow(userId: String): Flow<WorkoutSession?> = unsupported()

        override suspend fun countCompletedSessionsBetween(
            userId: String,
            journalId: String,
            from: LocalDate,
            to: LocalDate,
            excludeSessionUuid: String,
        ): Int = unsupported()

        override suspend fun startSession(userId: String, journalId: String, date: LocalDate, workoutNumber: Int): WorkoutSession = unsupported()
        override suspend fun endSession(userId: String, endedAt: Instant?): WorkoutSession? = unsupported()
        override suspend fun deleteSession(userId: String, sessionUuid: String): Unit = unsupported()
        override suspend fun deleteUserSessions(userId: String): Unit = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by WorkoutDetailsViewModelTest")
    }

    private companion object {
        const val USER_ID = "user-1"
        const val JOURNAL_ID = "journal-1"
        const val FREE_LIMIT = 10L
        val DATE = LocalDate(2026, 1, 15)
        /** "Now" for the injected clock, and the day the Repeat picker opens on. */
        val NOW: Instant = Instant.parse("2026-03-20T12:00:00Z")
        val TODAY = LocalDate(2026, 3, 20)
        val START: Instant = Instant.parse("2026-01-15T09:38:00Z")
        val END: Instant = Instant.parse("2026-01-15T10:42:00Z")
    }
}
