package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.quota.FreeQuotaSettings
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.RepeatDestination
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSet
import kz.maestrosultan.fitjournal.domain.workout.summary.WeightedSetOccurrence

private class FakeSyncTrigger : SyncTrigger {
    val reasons = mutableListOf<SyncReason>()
    override fun requestTick(reason: SyncReason) { reasons.add(reason) }
}

/**
 * Hand-rolled fake — unlike [kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGateTest]'s
 * real-SQL repo (which already proves the counting SQL), the pipeline under
 * test here is [RepeatWorkoutUseCase]'s resolve → gate-once → copy → tick
 * wiring, so a settable fake is the right tool. Everything beyond what the
 * quota surface + this use case touch is [unsupported].
 */
private class FakeRecordRepository : RecordRepository {

    /** Settable [maxWorkoutNumberOnDate] answer. */
    var maxWorkoutNumber: Int = 0
    val maxWorkoutNumberCalls = mutableListOf<Triple<String, String, LocalDate>>()

    /** Settable [copyWorkoutTo] answer. */
    var copyResult: Boolean = true
    data class CopyCall(
        val userId: String,
        val journalId: String,
        val sourceDate: LocalDate,
        val sourceWorkoutNumber: Int,
        val targetDate: LocalDate,
        val targetWorkoutNumber: Int,
    )
    val copyCalls = mutableListOf<CopyCall>()

    /** Settable [countMeteredWorkouts] answer; set [countMeteredWorkoutsThrows] to force a throw. */
    var meteredCount: Int = 0
    var countMeteredWorkoutsThrows: Throwable? = null

    /** Settable [hasAnyRecordInWorkout] answer. */
    var hasAnyRecordInWorkoutResult: Boolean = true
    data class SlotCall(val userId: String, val journalId: String, val date: LocalDate, val workoutNumber: Int)
    val hasAnyRecordInWorkoutCalls = mutableListOf<SlotCall>()

    override suspend fun countMeteredWorkouts(userId: String): Int {
        countMeteredWorkoutsThrows?.let { throw it }
        return meteredCount
    }

    override suspend fun hasAnyRecordInWorkout(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): Boolean {
        hasAnyRecordInWorkoutCalls += SlotCall(userId, journalId, date, workoutNumber)
        return hasAnyRecordInWorkoutResult
    }

    override suspend fun maxWorkoutNumberOnDate(userId: String, journalId: String, date: LocalDate): Int {
        maxWorkoutNumberCalls += Triple(userId, journalId, date)
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
        copyCalls += CopyCall(userId, journalId, sourceDate, sourceWorkoutNumber, targetDate, targetWorkoutNumber)
        return copyResult
    }

    private fun unsupported(): Nothing = throw UnsupportedOperationException("not needed by RepeatWorkoutUseCaseTest")

    override fun observeRecordsChanged(userId: String, journalId: String): Flow<String> = flowOf()
    override suspend fun getAllRecords(userId: String, journalId: String): List<WorkoutRecord> = unsupported()
    override suspend fun getRecordsByDate(
        userId: String,
        journalId: String,
        date: LocalDate,
        includeLastOccurrence: Boolean,
    ): List<WorkoutRecord> = unsupported()
    override suspend fun getRecordsByMonth(userId: String, journalId: String, month: String, year: String): List<WorkoutRecord> = unsupported()
    override suspend fun getRecentRecords(userId: String, journalId: String): List<WorkoutRecord> = unsupported()
    override suspend fun getSetsForExercise(userId: String, journalId: String, exerciseId: String): List<WorkoutSet> = unsupported()
    override suspend fun getExerciseOccurrences(userId: String, journalId: String, exerciseId: String): List<WorkoutExercise> = unsupported()
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
    override suspend fun addRecordsToDate(userId: String, journalId: String, date: LocalDate, records: List<WorkoutRecord>): Unit = unsupported()
    override suspend fun addRecordsFromDateToToday(userId: String, journalId: String, date: LocalDate): Unit = unsupported()
    override suspend fun saveWorkoutExerciseComment(userId: String, journalId: String, workoutExerciseId: String, comment: String?): Unit = unsupported()
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
    override suspend fun deleteSet(userId: String, journalId: String, workoutExerciseId: String, setId: String): Boolean = unsupported()
}

/**
 * [RepeatWorkoutUseCase] pipeline coverage: resolve the final page number,
 * consult the quota gate EXACTLY ONCE against the resolved slot, copy, tick.
 *
 * Uses a REAL [WorkoutQuotaGate] over the hand-rolled [FakeRecordRepository]
 * above — the gate's own semantics are exhaustively covered by
 * [kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGateTest] against real
 * SQL; this suite is only about how the use case drives it.
 *
 * [FreeQuotaSettings] is a global `object` and jvmTest runs every class in one
 * JVM, so both [BeforeTest] and [AfterTest] reset it — mirrors
 * `WorkoutQuotaGateTest`'s discipline exactly.
 */
class RepeatWorkoutUseCaseTest {

    private val userId = "user-1"
    private val journalId = "journal-1"
    private val sourceDate = LocalDate(2026, 2, 10)
    private val sourceWorkoutNumber = 1

    @BeforeTest
    fun resetSettingsBefore() = FreeQuotaSettings.reset()

    @AfterTest
    fun resetSettingsAfter() = FreeQuotaSettings.reset()

    /** Meter a NEVER-SUBSCRIBER with the shipping limit. Copied verbatim from WorkoutQuotaGateTest. */
    private fun meterOn(limit: Long = 10) {
        FreeQuotaSettings.setLimit(limit)
        FreeQuotaSettings.setHasEverSubscribed(false)
    }

    private fun newUseCase(repo: FakeRecordRepository, trigger: FakeSyncTrigger) =
        RepeatWorkoutUseCase(repo, trigger, WorkoutQuotaGate(records = repo))

    @Test
    fun existingRowDestination_neverRecomputesTheWorkoutNumber(): Unit = runBlocking {
        val repo = FakeRecordRepository()
        val trigger = FakeSyncTrigger()
        val useCase = newUseCase(repo, trigger)
        val destination = RepeatDestination(
            date = LocalDate(2026, 2, 12),
            workoutNumber = 4,
            isNewWorkout = false,
            isRunning = false,
            exerciseCount = 2,
        )

        val result = useCase(userId, journalId, sourceDate, sourceWorkoutNumber, destination)

        assertTrue(repo.maxWorkoutNumberCalls.isEmpty(), "an existing-row destination must not be recomputed")
        assertEquals(1, repo.copyCalls.size)
        assertEquals(4, repo.copyCalls.single().targetWorkoutNumber)
        assertEquals(RepeatWorkoutUseCase.Result.Copied(destination.date, 4), result)
    }

    @Test
    fun newRowDestination_recomputesFromAFreshMaxWorkoutNumber(): Unit = runBlocking {
        val repo = FakeRecordRepository().apply { maxWorkoutNumber = 7 }
        val trigger = FakeSyncTrigger()
        val useCase = newUseCase(repo, trigger)
        // The sheet drew this destination as "page 3", but the source has moved on.
        val destination = RepeatDestination(
            date = LocalDate(2026, 2, 12),
            workoutNumber = 3,
            isNewWorkout = true,
            isRunning = false,
            exerciseCount = 0,
        )

        val result = useCase(userId, journalId, sourceDate, sourceWorkoutNumber, destination)

        assertEquals(1, repo.maxWorkoutNumberCalls.size)
        assertEquals(1, repo.copyCalls.size)
        assertEquals(8, repo.copyCalls.single().targetWorkoutNumber, "must recompute as max + 1, not trust the stale 3")
        assertEquals(RepeatWorkoutUseCase.Result.Copied(destination.date, 8), result)
    }

    @Test
    fun gateIsConsultedExactlyOnce_againstTheResolvedSlot(): Unit = runBlocking {
        meterOn(limit = 1)
        val repo = FakeRecordRepository().apply {
            maxWorkoutNumber = 2
            meteredCount = 1 // exhausted
            hasAnyRecordInWorkoutResult = true
        }
        val trigger = FakeSyncTrigger()
        val useCase = newUseCase(repo, trigger)
        val destination = RepeatDestination(
            date = LocalDate(2026, 2, 12),
            workoutNumber = 1,
            isNewWorkout = true,
            isRunning = false,
            exerciseCount = 0,
        )

        val result = useCase(userId, journalId, sourceDate, sourceWorkoutNumber, destination)

        assertEquals(
            listOf(FakeRecordRepository.SlotCall(userId, journalId, destination.date, 3)),
            repo.hasAnyRecordInWorkoutCalls,
            "the gate must be consulted exactly once, against the RESOLVED slot (max 2 + 1 = 3)",
        )
        assertEquals(1, repo.copyCalls.size, "the copy must proceed: the slot already exists")
        assertIs<RepeatWorkoutUseCase.Result.Copied>(result)
    }

    @Test
    fun gateThrows_copyStillProceeds(): Unit = runBlocking {
        meterOn()
        val repo = FakeRecordRepository().apply { countMeteredWorkoutsThrows = RuntimeException("database is locked") }
        val trigger = FakeSyncTrigger()
        val useCase = newUseCase(repo, trigger)
        val destination = RepeatDestination(
            date = LocalDate(2026, 2, 12),
            workoutNumber = 1,
            isNewWorkout = false,
            isRunning = false,
            exerciseCount = 1,
        )

        val result = useCase(userId, journalId, sourceDate, sourceWorkoutNumber, destination)

        assertIs<RepeatWorkoutUseCase.Result.Copied>(result, "a thrown gate must fail OPEN")
        assertEquals(1, repo.copyCalls.size)
    }

    @Test
    fun refusal_writesNothing_andFiresNoTick(): Unit = runBlocking {
        meterOn(limit = 1)
        val repo = FakeRecordRepository().apply {
            maxWorkoutNumber = 0
            meteredCount = 1 // exhausted
            hasAnyRecordInWorkoutResult = false
        }
        val trigger = FakeSyncTrigger()
        val useCase = newUseCase(repo, trigger)
        val destination = RepeatDestination(
            date = LocalDate(2026, 2, 12),
            workoutNumber = 1,
            isNewWorkout = true,
            isRunning = false,
            exerciseCount = 0,
        )

        val result = useCase(userId, journalId, sourceDate, sourceWorkoutNumber, destination)

        assertEquals(RepeatWorkoutUseCase.Result.Refused, result)
        assertTrue(repo.copyCalls.isEmpty(), "a refusal must write nothing")
        assertTrue(trigger.reasons.isEmpty(), "a refusal must not tick")
    }

    @Test
    fun copyFalse_isNothingToCopy_andFiresNoTick(): Unit = runBlocking {
        val repo = FakeRecordRepository().apply { copyResult = false }
        val trigger = FakeSyncTrigger()
        val useCase = newUseCase(repo, trigger)
        val destination = RepeatDestination(
            date = LocalDate(2026, 2, 12),
            workoutNumber = 1,
            isNewWorkout = false,
            isRunning = false,
            exerciseCount = 0,
        )

        val result = useCase(userId, journalId, sourceDate, sourceWorkoutNumber, destination)

        assertEquals(RepeatWorkoutUseCase.Result.NothingToCopy, result)
        assertTrue(trigger.reasons.isEmpty(), "an empty source must not tick")
    }

    @Test
    fun success_firesExactlyOnePostWriteWorkoutRecordTick(): Unit = runBlocking {
        val repo = FakeRecordRepository()
        val trigger = FakeSyncTrigger()
        val useCase = newUseCase(repo, trigger)
        val destination = RepeatDestination(
            date = LocalDate(2026, 2, 12),
            workoutNumber = 1,
            isNewWorkout = false,
            isRunning = false,
            exerciseCount = 0,
        )

        useCase(userId, journalId, sourceDate, sourceWorkoutNumber, destination)

        assertEquals(listOf<SyncReason>(SyncReason.PostWrite.WorkoutRecord), trigger.reasons)
    }
}
