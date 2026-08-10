package kz.maestrosultan.fitjournal.ui.postworkout.success

import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.exercise.repository.DefaultExerciseRepository
import kz.maestrosultan.fitjournal.data.newTestDb
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.session.datasource.WorkoutSessionsDBDataSource
import kz.maestrosultan.fitjournal.data.session.repository.DefaultWorkoutSessionRepository
import kz.maestrosultan.fitjournal.data.testExerciseMapper
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.summary.BuildSessionSummaryUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.DetectSessionBestUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionSummary
import kz.maestrosultan.fitjournal.ui.postworkout.FinishResult
import kz.maestrosultan.fitjournal.ui.postworkout.PostWorkoutContext
import kz.maestrosultan.fitjournal.ui.postworkout.format.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters

/**
 * [WorkoutSuccessViewModel] end-to-end through in-memory SQLite (mirroring
 * BuildSessionSummaryUseCaseTest's fixtures): the ended session is re-read at
 * HEAD via `getSessionByWorkoutNumber`, the summary is REBUILT with the
 * default `includeBest` (so the state carries the PR even though the finish
 * snapshot was built without it), titles come from the injected
 * [MuscleTitleFormatter] lambdas for determinism, and every read failure
 * degrades to the finish snapshot / bare fallback without a crash.
 *
 * The screen is read-only: recording repository wrappers assert ZERO writes.
 */
class WorkoutSuccessViewModelTest {

    /** Steppable fake so tests control "now" precisely instead of racing the wall clock. */
    private class StepClock(var instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun installMain() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun uninstallMain() {
        Dispatchers.resetMain()
    }

    // ─── Fixture (mirrors BuildSessionSummaryUseCaseTest) ─────────────────

    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val repo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper)
    private val sessionsDB = WorkoutSessionsDBDataSource(db.workoutSessionsQueries)
    private val clock = StepClock(START)
    private val sessionRepo = DefaultWorkoutSessionRepository(sessionsDB, clock)
    private val buildSummary = BuildSessionSummaryUseCase(repo, sessionRepo, DetectSessionBestUseCase(repo))

    private val userId = "user-1"
    private val journalId = "journal-1"
    private val date = LocalDate(2026, 1, 15)

    /** Deterministic title lambdas — no compose-resource loading, no locale. */
    private val formatter = MuscleTitleFormatter(
        categoryName = { "cat-${it.name}" },
        fallbackTitle = { "fallback-title" },
    )

    private val categoryUuids = mutableMapOf<CategoryType, String>()

    private suspend fun categoryUuid(type: CategoryType): String =
        categoryUuids[type] ?: run {
            val uuid = UUID.randomUUID().toString()
            catDs.createCategory(uuid, uuid, type.name, type.name, type.name, type.id, null)
            categoryUuids[type] = uuid
            uuid
        }

    private suspend fun seedExercise(
        name: String,
        category: CategoryType,
        resultType: ResultType = ResultType.WEIGHT_REPS,
    ): String {
        val exId = UUID.randomUUID().toString()
        exRepo.createExercise(exId, userId, name, categoryUuid(category), resultType)
        return exId
    }

    private suspend fun addOccurrence(exId: String, date: LocalDate, workoutNumber: Int = 1): String {
        repo.addExercisesToDate(userId, journalId, date, workoutNumber, listOf(exId))
        return repo.getRecordsByDate(userId, journalId, date)
            .first { rec -> rec.workoutNumber == workoutNumber && rec.exercises.any { it.exercise.uuid == exId } }
            .exercises.first { it.exercise.uuid == exId }
            .id
    }

    /** Starts at [START], steps the clock to [END], ends — a 1:04 session. */
    private suspend fun endedSession(workoutNumber: Int = 1): WorkoutSession {
        clock.instant = START
        sessionRepo.startSession(userId, journalId, date, workoutNumber)
        clock.instant = END
        return checkNotNull(sessionRepo.endSession(userId))
    }

    /**
     * The finish-time snapshot exactly as the end-workout confirm sheet builds
     * it: `includeBest = false`, so `summary.best` is null — the SUCCESS screen
     * is the one that rebuilds with the default and gains the PR.
     */
    private suspend fun finishResult(session: WorkoutSession): FinishResult = FinishResult(
        context = PostWorkoutContext(userId, journalId, session.date, session.workoutNumber, MeasurementSystem.KG_KM),
        summary = buildSummary(session, includeBest = false),
    )

    private fun viewModel(
        result: FinishResult,
        summaryUseCase: BuildSessionSummaryUseCase = buildSummary,
        sessions: WorkoutSessionRepository = sessionRepo,
        titleFormatter: MuscleTitleFormatter = formatter,
    ) = WorkoutSuccessViewModel(
        result = result,
        buildSummary = summaryUseCase,
        sessionRepository = sessions,
        muscleTitleFormatter = titleFormatter,
        clock = clock,
        timeZone = TimeZone.UTC,
    )

    private suspend fun awaitState(vm: WorkoutSuccessViewModel): WorkoutSuccessContract.ViewState =
        vm.viewState.first { !it.loading }

    private fun expectedDateLine(start: Instant = START, end: Instant = END): String =
        LocaleFormatters.formatFullDate(date) + " · " +
            LocaleFormatters.formatTimeShort(start, TimeZone.UTC) + "–" +
            LocaleFormatters.formatTimeShort(end, TimeZone.UTC)

    // ─── Full assembly ────────────────────────────────────────────────────

    @Test
    fun stateAssembled_withPersonalRecord_rebuiltWithDefaultIncludeBest(): Unit = runTest(dispatcher) {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, LocalDate(2026, 1, 10)), 100.0, 5, null, null)
        val squatWe = addOccurrence(squat, date)
        val benchWe = addOccurrence(seedExercise("Bench Press", CategoryType.CHEST), date)
        repo.addSet(userId, journalId, squatWe, 105.0, 3, null, null)
        repo.addSet(userId, journalId, squatWe, 90.0, 5, null, null)
        repo.addSet(userId, journalId, benchWe, 60.0, 10, null, null)
        val result = finishResult(endedSession())
        assertNull(result.summary.best, "fixture sanity: the finish snapshot has NO PR (includeBest=false)")

        val state = awaitState(viewModel(result))

        assertEquals("cat-QUADRICEPS · cat-CHEST", state.title)
        assertEquals(expectedDateLine(), state.dateLine)
        assertEquals(
            "1,365 kg",
            state.tonnageText,
            "105×3 + 90×5 + 60×10, unit from result.context, grouped as design W4b shows it",
        )
        assertEquals(3, state.loggedSets)
        assertEquals(2, state.exerciseCount)
        assertEquals(
            SuccessTiles(durationText = "1:04", sets = 3, weekOrdinalText = LocaleFormatters.ordinal(1)),
            state.tiles,
        )
        assertEquals(
            PersonalRecordUi(
                exerciseName = "Squat",
                weightText = "105 kg",
                reps = 3,
                previousBestText = "100 kg",
                previousBestDate = LocalDate(2026, 1, 10),
            ),
            state.personalRecord,
            "the success screen REBUILDS the summary with the default includeBest — the PR appears",
        )
        assertEquals(
            listOf(
                MuscleBarUi(CategoryType.QUADRICEPS, loggedSets = 2, fraction = 1.0f, rampIndex = 0),
                MuscleBarUi(CategoryType.CHEST, loggedSets = 1, fraction = 0.5f, rampIndex = 1),
            ),
            state.muscles,
        )
        assertEquals(
            listOf(
                RailLineUi("Squat", loggedSets = 2, totalSets = 2, aggregate = RailAggregate.Tonnage("765 kg")),
                RailLineUi("Bench Press", loggedSets = 1, totalSets = 1, aggregate = RailAggregate.Tonnage("600 kg")),
            ),
            state.exercises,
        )
        assertTrue(state.playSuccessHaptic)
    }

    @Test
    fun stateAssembled_noPr_hidesPrCard(): Unit = runTest(dispatcher) {
        val squatWe = addOccurrence(seedExercise("Squat", CategoryType.QUADRICEPS), date)
        repo.addSet(userId, journalId, squatWe, 100.0, 5, null, null)
        val state = awaitState(viewModel(finishResult(endedSession())))

        assertNull(state.personalRecord, "a first-ever exercise has nothing to beat — no PR card")
        assertEquals("cat-QUADRICEPS", state.title)
        assertEquals("500 kg", state.tonnageText)
    }

    // ─── Title ────────────────────────────────────────────────────────────

    @Test
    fun title_isLocalizedTopThreeJoin_fourthMuscleExcluded(): Unit = runTest(dispatcher) {
        suspend fun seedSets(name: String, category: CategoryType, sets: Int) {
            val we = addOccurrence(seedExercise(name, category), date)
            repeat(sets) { repo.addSet(userId, journalId, we, 50.0, 5, null, null) }
        }
        seedSets("Squat", CategoryType.QUADRICEPS, 4)
        seedSets("Bench Press", CategoryType.CHEST, 3)
        seedSets("Row", CategoryType.BACK, 2)
        seedSets("Curl", CategoryType.BICEPS, 1)

        val state = awaitState(viewModel(finishResult(endedSession())))

        assertEquals("cat-QUADRICEPS · cat-CHEST · cat-BACK", state.title, "top 3 by logged sets, ' · ' join")
        assertEquals(
            listOf(
                MuscleBarUi(CategoryType.QUADRICEPS, 4, fraction = 1.0f, rampIndex = 0),
                MuscleBarUi(CategoryType.CHEST, 3, fraction = 0.75f, rampIndex = 1),
                MuscleBarUi(CategoryType.BACK, 2, fraction = 0.5f, rampIndex = 2),
                MuscleBarUi(CategoryType.BICEPS, 1, fraction = 0.25f, rampIndex = 3),
            ),
            state.muscles,
            "all muscles still bar-charted — only the TITLE caps at 3",
        )
    }

    @Test
    fun title_fallsBack_whenNoLoggedSets(): Unit = runTest(dispatcher) {
        val we = addOccurrence(seedExercise("Squat", CategoryType.QUADRICEPS), date)
        repo.addSet(userId, journalId, we, null, 12, null, null) // planned only — never logged

        val state = awaitState(viewModel(finishResult(endedSession())))

        assertEquals("fallback-title", state.title)
        assertNull(state.tonnageText, "zero logged sets — the tonnage block is hidden")
        assertTrue(state.muscles.isEmpty())
        assertEquals(0, state.exerciseCount, "nothing was performed, so nothing is counted")
        assertEquals(
            listOf(RailLineUi("Squat", loggedSets = 0, totalSets = 1, aggregate = null)),
            state.exercises,
            "the planned line still rails, as 0 of 1, carrying no aggregate",
        )
    }

    // ─── PR nullable reps ─────────────────────────────────────────────────

    @Test
    fun prWithNullReps_exposesNullSoTheTimesNPartIsOmitted(): Unit = runTest(dispatcher) {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, LocalDate(2026, 1, 10)), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, date), 105.0, null, null, null) // weight-only record

        val state = awaitState(viewModel(finishResult(endedSession())))

        val pr = assertNotNull(state.personalRecord)
        assertEquals("105 kg", pr.weightText)
        assertNull(pr.reps, "null reps means the composable omits the '× n' part entirely")
        assertEquals("100 kg", pr.previousBestText)
    }

    // ─── Week ordinal ─────────────────────────────────────────────────────

    @Test
    fun weekOrdinal_inState_equalsTheFinishResultSummarys(): Unit = runTest(dispatcher) {
        // Two completed sessions earlier in the same Mon-based ISO week (Mon 12 Jan).
        sessionRepo.startSession(userId, journalId, LocalDate(2026, 1, 12), 1)
        sessionRepo.endSession(userId)
        sessionRepo.startSession(userId, journalId, LocalDate(2026, 1, 13), 1)
        sessionRepo.endSession(userId)
        val result = finishResult(endedSession())
        assertEquals(3, result.summary.weekOrdinal, "fixture sanity: workout 3 of the week")

        val state = awaitState(viewModel(result))

        assertEquals(
            LocaleFormatters.ordinal(result.summary.weekOrdinal),
            assertNotNull(state.tiles).weekOrdinalText,
            "the rebuilt ordinal must equal the snapshot's (identical before/after ending, by construction)",
        )
    }

    // ─── Read-failure fallbacks ───────────────────────────────────────────

    @Test
    fun sessionReadThrows_fallsBackToTheFinishSnapshot_emptyState_noCrash(): Unit = runTest(dispatcher) {
        val snapshotSession = WorkoutSession("session-1", userId, journalId, date, 1, START, END)
        val result = FinishResult(
            context = PostWorkoutContext(userId, journalId, date, 1, MeasurementSystem.KG_KM),
            summary = emptySnapshot(snapshotSession),
        )

        val state = awaitState(viewModel(result, sessions = ThrowingSessionRepository()))

        assertEquals("fallback-title", state.title)
        assertNull(state.tonnageText)
        assertTrue(state.muscles.isEmpty())
        assertTrue(state.exercises.isEmpty())
        assertNull(state.personalRecord)
        assertEquals(expectedDateLine(), state.dateLine, "the snapshot's session still dates the screen")
        assertFalse(state.loading)
    }

    @Test
    fun summaryRebuildThrows_rendersTheFinishSnapshotInstead(): Unit = runTest(dispatcher) {
        val squatWe = addOccurrence(seedExercise("Squat", CategoryType.QUADRICEPS), date)
        repo.addSet(userId, journalId, squatWe, 100.0, 5, null, null)
        val result = finishResult(endedSession())
        val throwingRebuild = BuildSessionSummaryUseCase(
            ThrowingReadsRecordRepository(repo),
            sessionRepo,
            DetectSessionBestUseCase(repo),
        )

        val state = awaitState(viewModel(result, summaryUseCase = throwingRebuild))

        assertEquals("cat-QUADRICEPS", state.title, "the usable snapshot renders — not the bare fallback")
        assertEquals("500 kg", state.tonnageText)
        assertNull(state.personalRecord, "the degradation: the snapshot was built without the PR")
    }

    @Test
    fun snapshotRenderThrowsToo_landsOnTheBareFallback(): Unit = runTest(dispatcher) {
        val squatWe = addOccurrence(seedExercise("Squat", CategoryType.QUADRICEPS), date)
        repo.addSet(userId, journalId, squatWe, 100.0, 5, null, null)
        val result = finishResult(endedSession())
        // categoryName throws, fallbackTitle succeeds: the session read and the
        // summary rebuild are FINE here, but with non-empty muscles BOTH stateFor
        // attempts (rebuilt summary, then the snapshot) hit categoryName and die,
        // while bareFallbackState's title(emptyList()) short-circuits to
        // fallbackTitle before ever touching categoryName — so rung 2 is
        // reachable AND the bare fallback still renders.
        val explodingCategoryNames = MuscleTitleFormatter(
            categoryName = { throw IllegalStateException("string resource loading failed") },
            fallbackTitle = { "fallback-title" },
        )

        val state = awaitState(viewModel(result, titleFormatter = explodingCategoryNames))

        assertEquals(
            WorkoutSuccessContract.ViewState(loading = false, title = "fallback-title", playSuccessHaptic = true),
            state,
            "bare fallback EXACTLY: fallback title, every section at its hidden default, haptic armed",
        )
    }

    /**
     * Design W4b's rail reads "4 sets · 4,320 kg". Per-exercise session totals
     * reach four and five digits just as the session total does, so the rail
     * groups too — it used to render a bare "2400 kg" beside a grouped hero.
     */
    @Test
    fun railTonnage_isGrouped_likeTheHeroAndTheCard(): Unit = runTest(dispatcher) {
        val press = seedExercise("Leg Press", CategoryType.QUADRICEPS)
        val pressWe = addOccurrence(press, date)
        repo.addSet(userId, journalId, pressWe, 100.0, 12, null, null)
        repo.addSet(userId, journalId, pressWe, 100.0, 12, null, null)

        val state = awaitState(viewModel(finishResult(endedSession())))

        val line = state.exercises.single { it.name == "Leg Press" }
        val aggregate = assertIs<RailAggregate.Tonnage>(line.aggregate)
        assertEquals("2,400 kg", aggregate.text, "100×12 twice, grouped as the frame shows it")
    }

    /**
     * A timed row with the distance left at 0, end to end through the real
     * BuildSessionSummaryUseCase and the real rail mapping — not a pre-nulled
     * fixture.
     *
     * The distance must be an explicit 0.0, not null: `WorkoutSet.isLogged` is
     * `displayValue != null`, which for DISTANCE_DURATION IS the distance, so a
     * null-distance set is not logged at all and produces no aggregate. Zero is
     * the reachable case (WorkoutSet documents 0 as 3.2% of production sets),
     * and it is what used to render "0 km · 0:32" instead of just the clock.
     *
     * Also pins the unit contract: 32 minutes in, 1920 seconds out.
     */
    @Test
    fun durationOnlyExercise_dropsTheZeroDistanceFromTheRail(): Unit = runTest(dispatcher) {
        val rower = seedExercise("Rowing Machine", CategoryType.QUADRICEPS, ResultType.DISTANCE_DURATION)
        val rowerWe = addOccurrence(rower, date)
        repo.addSet(userId, journalId, rowerWe, null, null, 0.0, 32)

        val state = awaitState(viewModel(finishResult(endedSession())))

        val line = state.exercises.single { it.name == "Rowing Machine" }
        val aggregate = assertIs<RailAggregate.DistanceDuration>(line.aggregate)
        assertNull(aggregate.distanceText, "a zero distance must not reach the UI as \"0 km\"")
        assertEquals(1920, aggregate.durationSec, "32 logged minutes must arrive as 1920 seconds")
    }

    // ─── Read-only contract ───────────────────────────────────────────────

    @Test
    fun assemblesState_withZeroRepositoryWrites(): Unit = runTest(dispatcher) {
        val squat = seedExercise("Squat", CategoryType.QUADRICEPS)
        repo.addSet(userId, journalId, addOccurrence(squat, LocalDate(2026, 1, 10)), 100.0, 5, null, null)
        repo.addSet(userId, journalId, addOccurrence(squat, date), 105.0, 3, null, null)
        val result = finishResult(endedSession())
        val recordingRecords = RecordingRecordRepository(repo)
        val recordingSessions = RecordingSessionRepository(sessionRepo)

        val state = awaitState(
            viewModel(
                result,
                summaryUseCase = BuildSessionSummaryUseCase(
                    recordingRecords,
                    recordingSessions,
                    DetectSessionBestUseCase(recordingRecords),
                ),
                sessions = recordingSessions,
            ),
        )

        assertNotNull(state.personalRecord, "fixture sanity: the FULL read path (incl. PR history) ran")
        assertEquals(0, recordingRecords.writeCalls, "success screen is strictly read-only")
        assertEquals(0, recordingSessions.writeCalls, "success screen is strictly read-only")
    }

    // ─── Haptic one-shot ──────────────────────────────────────────────────

    @Test
    fun successHaptic_isOneShot_consumedByCallback(): Unit = runTest(dispatcher) {
        val vm = viewModel(finishResult(endedSession()))

        assertTrue(awaitState(vm).playSuccessHaptic, "armed once the state lands")
        vm.dispatch(WorkoutSuccessContract.ViewAction.SuccessHapticPlayed)
        assertFalse(vm.viewState.value.playSuccessHaptic, "consumed — never fires twice")
    }

    // ─── Fakes ────────────────────────────────────────────────────────────

    private fun emptySnapshot(session: WorkoutSession) = SessionSummary(
        session = session,
        muscles = emptyList(),
        exercises = emptyList(),
        tonnageKg = 0.0,
        loggedSets = 0,
        exerciseCount = 0,
        weekOrdinal = 1,
        best = null,
        sessionRecordUuids = emptySet(),
    )

    /** Every method throws — the "local DB read failed" worst case. */
    private class ThrowingSessionRepository : WorkoutSessionRepository {
        private fun boom(): Nothing = throw IllegalStateException("session repository unavailable")
        override suspend fun getSessionByWorkoutNumber(
            userId: String,
            journalId: String,
            date: LocalDate,
            workoutNumber: Int,
        ): WorkoutSession? = boom()

        override suspend fun getSessionsForDay(userId: String, journalId: String, date: LocalDate): List<WorkoutSession> = boom()
        override fun getSessionsForDayFlow(userId: String, journalId: String, date: LocalDate): Flow<List<WorkoutSession>> = boom()
        override suspend fun getRunningSession(userId: String): WorkoutSession? = boom()
        override fun getRunningSessionFlow(userId: String): Flow<WorkoutSession?> = boom()
        override suspend fun countCompletedSessionsBetween(
            userId: String,
            journalId: String,
            from: LocalDate,
            to: LocalDate,
            excludeSessionUuid: String,
        ): Int = boom()

        override suspend fun startSession(
            userId: String,
            journalId: String,
            date: LocalDate,
            workoutNumber: Int,
        ): WorkoutSession = boom()

        override suspend fun endSession(userId: String): WorkoutSession? = boom()
        override suspend fun setSessionComment(userId: String, sessionUuid: String, comment: String?) = boom()
        override suspend fun deleteSession(userId: String, sessionUuid: String) = boom()
        override suspend fun deleteUserSessions(userId: String) = boom()
    }

    /** Reads delegate, except the summary's first read throws — rebuild fails mid-flight. */
    private class ThrowingReadsRecordRepository(
        private val delegate: RecordRepository,
    ) : RecordRepository by delegate {
        override suspend fun getRecordsByDate(userId: String, journalId: String, date: LocalDate, includeLastOccurrence: Boolean): List<WorkoutRecord> =
            throw IllegalStateException("record repository unavailable")
    }

    /** Delegates everything; counts calls to WRITE methods. Reads stay live. */
    private class RecordingSessionRepository(
        private val delegate: WorkoutSessionRepository,
    ) : WorkoutSessionRepository by delegate {
        var writeCalls = 0
        override suspend fun startSession(
            userId: String,
            journalId: String,
            date: LocalDate,
            workoutNumber: Int,
        ): WorkoutSession {
            writeCalls++
            return delegate.startSession(userId, journalId, date, workoutNumber)
        }

        override suspend fun endSession(userId: String): WorkoutSession? {
            writeCalls++
            return delegate.endSession(userId)
        }

        override suspend fun deleteUserSessions(userId: String) {
            writeCalls++
            delegate.deleteUserSessions(userId)
        }
    }

    /** Delegates everything; counts calls to WRITE methods. Reads stay live. */
    private class RecordingRecordRepository(
        private val delegate: RecordRepository,
    ) : RecordRepository by delegate {
        var writeCalls = 0

        override suspend fun addExercisesToDate(
            userId: String,
            journalId: String,
            date: LocalDate,
            workoutNumber: Int,
            exerciseIds: List<String>,
        ) {
            writeCalls++
            delegate.addExercisesToDate(userId, journalId, date, workoutNumber, exerciseIds)
        }

        override suspend fun addRecordsToDate(
            userId: String,
            journalId: String,
            date: LocalDate,
            records: List<WorkoutRecord>,
        ) {
            writeCalls++
            delegate.addRecordsToDate(userId, journalId, date, records)
        }

        override suspend fun addRecordsFromDateToToday(userId: String, journalId: String, date: LocalDate) {
            writeCalls++
            delegate.addRecordsFromDateToToday(userId, journalId, date)
        }

        override suspend fun replaceExerciseInRecord(
            userId: String,
            journalId: String,
            recordId: String,
            targetWorkoutExerciseId: String,
            newExerciseId: String,
        ) {
            writeCalls++
            delegate.replaceExerciseInRecord(userId, journalId, recordId, targetWorkoutExerciseId, newExerciseId)
        }

        override suspend fun saveWorkoutExerciseComment(
            userId: String,
            journalId: String,
            workoutExerciseId: String,
            comment: String?,
        ) {
            writeCalls++
            delegate.saveWorkoutExerciseComment(userId, journalId, workoutExerciseId, comment)
        }

        override suspend fun refreshRecordPositions(
            userId: String,
            journalId: String,
            records: List<WorkoutRecord>,
        ) {
            writeCalls++
            delegate.refreshRecordPositions(userId, journalId, records)
        }

        override suspend fun mergeRecords(
            userId: String,
            journalId: String,
            firstRecord: WorkoutRecord,
            secondRecord: WorkoutRecord,
        ): List<WorkoutRecord> {
            writeCalls++
            return delegate.mergeRecords(userId, journalId, firstRecord, secondRecord)
        }

        override suspend fun removeExerciseFromRecord(
            userId: String,
            journalId: String,
            record: WorkoutRecord,
            exercise: WorkoutExercise,
        ): List<WorkoutRecord> {
            writeCalls++
            return delegate.removeExerciseFromRecord(userId, journalId, record, exercise)
        }

        override suspend fun deleteRecord(userId: String, journalId: String, record: WorkoutRecord) {
            writeCalls++
            delegate.deleteRecord(userId, journalId, record)
        }

        override suspend fun deleteRecordsForDate(userId: String, journalId: String, date: LocalDate) {
            writeCalls++
            delegate.deleteRecordsForDate(userId, journalId, date)
        }

        override suspend fun deleteUserRecords(userId: String) {
            writeCalls++
            delegate.deleteUserRecords(userId)
        }

        override suspend fun addSet(
            userId: String,
            journalId: String,
            workoutExerciseId: String,
            weight: Double?,
            reps: Int?,
            distance: Double?,
            duration: Int?,
        ) {
            writeCalls++
            delegate.addSet(userId, journalId, workoutExerciseId, weight, reps, distance, duration)
        }

        override suspend fun updateSet(
            userId: String,
            journalId: String,
            workoutExerciseId: String,
            setId: String,
            weight: Double?,
            reps: Int?,
            distance: Double?,
            duration: Int?,
        ): Boolean {
            writeCalls++
            return delegate.updateSet(userId, journalId, workoutExerciseId, setId, weight, reps, distance, duration)
        }

        override suspend fun deleteSet(
            userId: String,
            journalId: String,
            workoutExerciseId: String,
            setId: String,
        ): Boolean {
            writeCalls++
            return delegate.deleteSet(userId, journalId, workoutExerciseId, setId)
        }
    }

    private companion object {
        val START: Instant = Instant.parse("2026-01-15T09:38:00Z")
        val END: Instant = Instant.parse("2026-01-15T10:42:00Z")
    }
}
