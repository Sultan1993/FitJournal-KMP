package kz.maestrosultan.fitjournal.domain.quota

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
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
import kz.maestrosultan.fitjournal.domain.workout.ResultType

/**
 * The whole semantics of the metered reverse trial, over the REAL
 * [DefaultRecordRepository] and a real (in-memory) SQLite database — the count
 * lives in SQL (`countMeteredWorkouts`, grouped by `(journalId, date,
 * workoutNumber)` with `HAVING MIN(createdDate) >= ?`), so a hand-written fake
 * repository would prove nothing.
 *
 * iOS has no test target, so this file is the only automated proof that the
 * gate's answers are right on either platform.
 *
 * The counting unit is the WORKOUT — a `(journalId, date, workoutNumber)` slot —
 * not the calendar day, and [WorkoutQuotaGate.canWriteWorkout] is ONE rule:
 * **you may write in a workout that already exists; you may not open a new one.**
 * What the cases below cover:
 *
 *  1  rows are not workouts — a 6-exercise session is ONE workout
 *  2  two workouts on ONE date are TWO — the unit is the workout, not the day
 *  3  the same slot in two journals is TWO (quota is per account)
 *  4  tombstoning never refunds quota
 *  7  every failure direction is OPEN — entitled / unknown history / limit <= 0
 *  7b already subscribed => Lapsed, carrying the WHOLE library, not a remainder
 *  7c an unresolved ENTITLEMENT never walls a payer (the one fail-CLOSED risk)
 *  7d hasEverSubscribed is sticky once true; reset() is the only clear
 *  7e canOpenNewWorkout ignores rule 3 (Repeat's slot is always new)
 *  9  at exhaustion: existing workouts stay writable, new ones are refused —
 *     including a fresh workoutNumber on a date that already has workouts
 *  9b a workout whose records are all tombstoned still counts as existing
 * 10  below exhaustion nothing is blocked
 * 11  the flow tracks workouts, not rows
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutQuotaGateTest {

    // ─── Harness: copied from RecordRepositoryTest, real repo over real SQL ───

    private val db = newTestDb()
    private val catDs = CategoriesDBDataSource(db.categoryQueries)
    private val exDs = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(catDs))
    private val exRepo = DefaultExerciseRepository(exDs, testExerciseMapper)
    private val workoutsDB =
        WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
    private val repo = DefaultRecordRepository(workoutsDB, exDs, testExerciseMapper)

    private val gate = WorkoutQuotaGate(records = repo)

    /**
     * [FreeQuotaSettings] is a global `object` and jvmTest runs every class in
     * one JVM — without this a leaked `setEntitled(true)` would silently turn
     * some later test's meter off and make it pass for the wrong reason. Reset
     * on BOTH sides so neither this suite's order nor another suite's leakage
     * can change an answer.
     */
    @BeforeTest
    fun resetSettingsBefore() = resetSettings()

    @AfterTest
    fun resetSettingsAfter() = resetSettings()

    private fun resetSettings() = FreeQuotaSettings.reset()

    /** Meter a NEVER-SUBSCRIBER with the shipping limit. */
    private fun meterOn(limit: Long = 10) {
        FreeQuotaSettings.setLimit(limit)
        FreeQuotaSettings.setHasEverSubscribed(false)
    }

    /**
     * A LAPSED user as production actually presents one: sticky history plus an
     * AUTHORITATIVE "not entitled". The explicit `setEntitled(false)` is the point
     * — without it entitlement is merely unresolved, which must resolve to
     * Unlimited so a payer is never walled mid-launch.
     */
    private fun lapsed(limit: Long = 10, endedAtIso: String? = null) {
        FreeQuotaSettings.setLimit(limit)
        FreeQuotaSettings.setHasEverSubscribed(true)
        FreeQuotaSettings.setSubscriptionEndedAt(endedAtIso)
        FreeQuotaSettings.setEntitled(false)
    }

    private var nextPosition = 0

    /**
     * Insert one `workoutRecords` row directly through the generated query,
     * because `createdDate` must be controllable — `addExercisesToDate` stamps
     * `Clock.System.now()`, and the cutoff cases are entirely about
     * `createdDate`. No child rows: both quota queries read the parent table
     * only, and cases that need the tree go through the repository instead.
     */
    private fun seedRecord(
        date: LocalDate,
        workoutNumber: Int = 1,
        createdAt: Instant = SEEDED_AT,
        journalId: String = J1,
        userId: String = USER,
    ): String {
        val uuid = UUID.randomUUID().toString()
        db.workoutRecordsQueries.createWorkoutRecord(
            uuid = uuid,
            remoteId = null,
            userId = userId,
            journalId = journalId,
            date = date.toString(),
            position = (nextPosition++).toLong(),
            comment = null,
            startedAt = null,
            durationSec = null,
            pendingUpload = true,
            createdDate = createdAt.toString(),
            updatedDate = createdAt.toString(),
            workoutNumber = workoutNumber.toLong(),
        )
        return uuid
    }

    /** Ten counted workouts — exhausts the shipping limit of 10. */
    private fun seedExhaustingHistory() = TEN_SPENT_WORKOUTS.forEach { (date, workoutNumber) ->
        seedRecord(date, workoutNumber)
    }

    private suspend fun seedCatalogExercise(): String {
        val catUuid = UUID.randomUUID().toString()
        catDs.createCategory(catUuid, catUuid, "Legs", "Ноги", "Ноги", CategoryType.QUADRICEPS.id, null)
        val exId = UUID.randomUUID().toString()
        exRepo.createExercise(exId, USER, "Squat", catUuid, ResultType.WEIGHT_REPS)
        return exId
    }

    // ─── 1. Rows are not workouts ────────────────────────────────────────────

    @Test
    fun sixRecordsInsideOneWorkout_countAsOneWorkout_notSixRows(): Unit = runBlocking {
        // Counting rows would charge a 6-exercise session six times over and
        // burn a 10-workout quota in two sessions.
        meterOn()
        repeat(6) { seedRecord(LocalDate(2026, 2, 10), workoutNumber = 1) }

        assertEquals(WorkoutQuota.Metered(used = 1, limit = 10), gate.getQuota(USER))
    }

    // ─── 2. The unit is the WORKOUT, not the day ─────────────────────────────

    @Test
    fun twoWorkoutsOnTheSameDate_countAsTwo_notOneDay(): Unit = runBlocking {
        // THE behaviour change: the query groups by (journalId, date,
        // workoutNumber), so a second workout logged on the same date spends a
        // second unit. Under the previous day-based unit this was 1.
        meterOn()
        seedRecord(LocalDate(2026, 2, 10), workoutNumber = 1)
        seedRecord(LocalDate(2026, 2, 10), workoutNumber = 2)

        assertEquals(
            WorkoutQuota.Metered(used = 2, limit = 10),
            gate.getQuota(USER),
            "each workoutNumber on a date is its own metered workout",
        )
    }

    // ─── 3. Quota is per account, spanning journals ──────────────────────────

    @Test
    fun theSameDateAndWorkoutNumberInTwoJournals_countsAsTwoWorkouts(): Unit = runBlocking {
        meterOn()
        seedRecord(LocalDate(2026, 2, 10), journalId = J1)
        seedRecord(LocalDate(2026, 2, 10), journalId = J2)

        assertEquals(
            WorkoutQuota.Metered(used = 2, limit = 10),
            gate.getQuota(USER),
            "journalId is part of the group key — a second journal is not a free second copy of the quota",
        )
    }

    @Test
    fun anotherUsersWorkouts_areNotCounted(): Unit = runBlocking {
        meterOn()
        seedRecord(LocalDate(2026, 2, 10))
        seedRecord(LocalDate(2026, 2, 11), userId = "someone-else")

        assertEquals(WorkoutQuota.Metered(used = 1, limit = 10), gate.getQuota(USER))
    }

    // ─── 4. Deleting must never refund quota ─────────────────────────────────

    @Test
    fun tombstoningEveryRecordOfACountedWorkout_doesNotReduceTheCount(): Unit = runBlocking {
        meterOn()
        val uuids = List(3) { seedRecord(LocalDate(2026, 2, 10), workoutNumber = 1) }
        assertEquals(WorkoutQuota.Metered(used = 1, limit = 10), gate.getQuota(USER))

        uuids.forEach { workoutsDB.softDeleteWorkoutRecord(it) }

        assertEquals(
            WorkoutQuota.Metered(used = 1, limit = 10),
            gate.getQuota(USER),
            "a workout already spent stays spent — deleting must never refund quota",
        )
    }
    // ─── 7. Every failure direction is OPEN, never "exhausted" ───────────────

    @Test
    fun entitled_unknownHistory_andNonPositiveLimit_areAllUnlimited(): Unit = runBlocking {
        meterOn()
        repeat(12) { seedRecord(LocalDate(2026, 2, 1 + it)) }
        assertIs<WorkoutQuota.Metered>(
            gate.getQuota(USER),
            "sanity: with a valid config these workouts ARE metered, so Unlimited below means something",
        )

        FreeQuotaSettings.setEntitled(true)
        assertEquals(WorkoutQuota.Unlimited, gate.getQuota(USER), "an entitled user is never metered")

        // Not yet known — offline, or Qonversion has not answered. Must fail OPEN:
        // the same device usually cannot reach Superwall either, so metering it
        // would block the user with no way to buy.
        FreeQuotaSettings.reset()
        FreeQuotaSettings.setLimit(10)
        assertEquals(
            WorkoutQuota.Unlimited,
            gate.getQuota(USER),
            "unknown subscription history must fail OPEN",
        )

        FreeQuotaSettings.setHasEverSubscribed(false)
        FreeQuotaSettings.setLimit(0)
        assertEquals(WorkoutQuota.Unlimited, gate.getQuota(USER), "limit 0 is the kill switch")

        FreeQuotaSettings.setLimit(-5)
        assertEquals(0, FreeQuotaSettings.config.value.limit, "a negative limit clamps to 0")
        assertEquals(WorkoutQuota.Unlimited, gate.getQuota(USER), "a negative limit must fail OPEN too")

        // And nothing above may leave a write blocked.
        assertTrue(gate.canWriteWorkout(USER, J1, FRESH_DATE, workoutNumber = 1))
    }

    // ─── 7c. An unresolved ENTITLEMENT must never wall a payer ───────────────

    @Test
    fun everSubscribed_withEntitlementNotYetResolved_isUnlimited_notLapsed(): Unit = runBlocking {
        // The one direction that fails CLOSED if entitlement is a plain Boolean.
        // A PAYING subscriber's sticky hasEverSubscribed is restored from disk with
        // no network, so it can land before Superwall reports. If "not reported yet"
        // looked like "not entitled", they would resolve to Lapsed and be refused a
        // write — a customer with an active subscription shown a paywall.
        FreeQuotaSettings.setLimit(10)
        FreeQuotaSettings.setHasEverSubscribed(true)

        assertEquals(null, FreeQuotaSettings.isEntitled.value, "precondition: entitlement unresolved")
        assertEquals(WorkoutQuota.Unlimited, gate.getQuota(USER), "unresolved entitlement must NOT wall them")
        assertTrue(gate.canOpenNewWorkout(USER))

        // An AUTHORITATIVE "not entitled" is what walls them, and it arrives on
        // every non-active path via deactivateSubscription().
        FreeQuotaSettings.setEntitled(false)
        assertIs<WorkoutQuota.Lapsed>(gate.getQuota(USER), "an explicit false DOES wall them")
        assertFalse(gate.canOpenNewWorkout(USER))

        // And a live entitlement wins outright.
        FreeQuotaSettings.setEntitled(true)
        assertEquals(WorkoutQuota.Unlimited, gate.getQuota(USER))
    }

    @Test
    fun neverSubscribed_isMetered_evenWhileEntitlementIsUnresolved(): Unit = runBlocking {
        // The mirror of the case above: unresolved entitlement must not switch
        // metering OFF for a never-subscriber either, or the meter would never
        // start. Safe because activateSubscription sets BOTH flags together, so
        // "subscriber with hasEverSubscribed == false" cannot occur.
        meterOn(limit = 2)
        assertEquals(null, FreeQuotaSettings.isEntitled.value, "precondition: entitlement unresolved")
        repeat(2) { seedRecord(LocalDate(2026, 2, 1 + it)) }

        assertEquals(WorkoutQuota.Metered(used = 2, limit = 2), gate.getQuota(USER))
        assertFalse(gate.canOpenNewWorkout(USER))
    }

    // ─── 7b. Already had the product ─────────────────────────────────────────

    @Test
    fun everSubscribed_isLapsed_carryingTheirWHOLElibrary_notARemainingCount(): Unit = runBlocking {
        // A subscription OR a trial. They have already had the free ride, so there
        // is nothing left to spend — but this is NOT a spent meter. The card it
        // drives says "Your N workouts are safe", so the state carries the TOTAL
        // they have ever logged, including everything logged while they were paying.
        lapsed(endedAtIso = "2026-08-12T00:00:00Z")
        repeat(3) { seedRecord(LocalDate(2026, 2, 1 + it)) }

        val quota = gate.getQuota(USER)
        assertEquals(WorkoutQuota.Lapsed(totalWorkouts = 3, endedAtIso = "2026-08-12T00:00:00Z"), quota)
        assertFalse(gate.canOpenNewWorkout(USER), "they may not open a new workout")

        // Rule 3 still applies: a workout that already exists stays writable, so
        // nobody is amputated mid-session the moment their subscription lapses.
        assertTrue(
            gate.canWriteWorkout(USER, J1, LocalDate(2026, 2, 1), workoutNumber = 1),
            "an existing workout stays writable",
        )
        assertFalse(gate.canWriteWorkout(USER, J1, FRESH_DATE, workoutNumber = 1))
    }

    @Test
    fun lapsedWithNoCachedExpiry_stillResolves_withAnUndatedEyebrow(): Unit = runBlocking {
        // The expiry is display-only. When we never cached one the card must drop
        // the date rather than invent one — so the state resolves with a null.
        lapsed()

        assertEquals(WorkoutQuota.Lapsed(totalWorkouts = 0, endedAtIso = null), gate.getQuota(USER))
    }

    @Test
    fun hasEverSubscribed_isStickyOnceTrue_soAFailedProbeCannotMintAnAllowance(): Unit = runBlocking {
        lapsed()

        // A later launch goes offline (null) or Qonversion answers "no entitlements"
        // before it has synced (false). Either would otherwise hand a former
        // subscriber a fresh 10 free workouts every launch.
        FreeQuotaSettings.setHasEverSubscribed(null)
        assertEquals(true, FreeQuotaSettings.config.value.hasEverSubscribed, "unknown must not erase a known true")
        FreeQuotaSettings.setHasEverSubscribed(false)
        assertEquals(true, FreeQuotaSettings.config.value.hasEverSubscribed, "false must not erase a known true")
        assertFalse(gate.canOpenNewWorkout(USER))

        // Only an explicit reset clears it — which is what logout does, so the next
        // account on this device does not inherit the previous one's answer.
        FreeQuotaSettings.reset()
        assertEquals(null, FreeQuotaSettings.config.value.hasEverSubscribed)
        assertEquals(null, FreeQuotaSettings.isEntitled.value, "reset() clears entitlement to UNRESOLVED, not to false")
    }

    // ─── 7e. Opening a slot that does not exist yet (Repeat) ─────────────────

    @Test
    fun canOpenNewWorkout_ignoresRule3_becauseTheTargetSlotIsAlwaysNew(): Unit = runBlocking {
        // Repeat copies onto today as max(workoutNumber) + 1, so its target slot
        // never exists at the time of asking and rule 3 could never fire. This is
        // the difference from canWriteWorkout: at exhaustion, writing INTO an
        // existing workout is still allowed, but opening a new one is not.
        meterOn(limit = 3)
        repeat(3) { seedRecord(LocalDate(2026, 2, 1 + it)) }

        assertTrue(gate.getQuota(USER).let { it is WorkoutQuota.Metered && it.isExhausted }, "precondition")
        assertFalse(gate.canOpenNewWorkout(USER), "exhausted: Repeat must be refused")
        assertTrue(
            gate.canWriteWorkout(USER, J1, LocalDate(2026, 2, 1), workoutNumber = 1),
            "...while the SAME user may still write into a workout that already exists",
        )

        // Entitled and unknown-history both fail open, exactly like canWriteWorkout.
        FreeQuotaSettings.setEntitled(true)
        assertTrue(gate.canOpenNewWorkout(USER), "an entitled user is never refused")
        FreeQuotaSettings.setEntitled(false)
        FreeQuotaSettings.reset()
        FreeQuotaSettings.setLimit(10)
        assertTrue(gate.canOpenNewWorkout(USER), "unknown subscription history is never refused")
    }

    // ─── 9. At exhaustion: existing workouts writable, new ones refused ──────

    @Test
    fun atExhaustion_existingWorkoutsStayWritable_andNewOnesAreRefused(): Unit = runBlocking {
        meterOn()
        seedExhaustingHistory()
        assertEquals(
            WorkoutQuota.Metered(used = 10, limit = 10),
            gate.getQuota(USER),
            "ten workouts across nine dates — one date carries two, which is the whole point of the unit",
        )

        // A brand-new slot on a date with no history — the wall.
        assertFalse(
            gate.canWriteWorkout(USER, J1, FRESH_DATE, workoutNumber = 1),
            "opening a new workout must be refused at exhaustion",
        )

        // A slot that already holds a live record stays writable, whatever its
        // date: nobody is amputated mid-workout, and old sessions stay editable.
        val (spentDate, spentNumber) = TEN_SPENT_WORKOUTS.first()
        assertTrue(
            repo.hasAnyRecordInWorkout(USER, J1, spentDate, spentNumber),
            "precondition: that slot holds records",
        )
        assertTrue(
            gate.canWriteWorkout(USER, J1, spentDate, spentNumber),
            "a workout that already exists stays writable at exhaustion",
        )
        assertTrue(
            gate.canWriteWorkout(USER, J1, DOUBLE_WORKOUT_DATE, workoutNumber = 2),
            "the second workout of a date is its own slot, and it exists, so it is writable too",
        )

        // A fresh workoutNumber on a date that already has workouts is still a
        // NEW workout — the slot, not the date, is what carves out the exception.
        assertFalse(
            gate.canWriteWorkout(USER, J1, SINGLE_WORKOUT_DATE, workoutNumber = 2),
            "a second workout on an already-used date is a new workout, so it is refused",
        )

        // The quota is per account, so an untouched journal is not a free slot.
        assertFalse(
            gate.canWriteWorkout(USER, J2, spentDate, spentNumber),
            "the same date and number in another journal is a different, brand-new workout",
        )
    }

    @Test
    fun atExhaustion_aWorkoutWhoseRecordsAreAllTombstoned_stillStaysWritable(): Unit = runBlocking {
        // `workoutSlotExists` is deliberately
        // deleted-inclusive: clearing a workout's rows must not hand back a
        // fresh writable slot, and must not lock the user out of it either.
        meterOn()
        seedExhaustingHistory()
        val extra = seedRecord(FRESH_DATE, workoutNumber = 1)
        workoutsDB.softDeleteWorkoutRecord(extra)

        assertEquals(
            WorkoutQuota.Metered(used = 11, limit = 10),
            gate.getQuota(USER),
            "the tombstoned workout is still counted",
        )
        assertTrue(
            gate.canWriteWorkout(USER, J1, FRESH_DATE, workoutNumber = 1),
            "that slot exists (deleted-inclusive), so it stays writable",
        )
    }
    // ─── 10. Below exhaustion nothing is blocked ─────────────────────────────

    @Test
    fun belowExhaustion_everyWorkoutIsWritable(): Unit = runBlocking {
        meterOn()
        TEN_SPENT_WORKOUTS.dropLast(1).forEach { (date, workoutNumber) -> seedRecord(date, workoutNumber) }
        assertEquals(WorkoutQuota.Metered(used = 9, limit = 10), gate.getQuota(USER))

        assertTrue(gate.canWriteWorkout(USER, J1, FRESH_DATE, workoutNumber = 1))
        assertTrue(gate.canWriteWorkout(USER, J1, ANOTHER_FRESH_DATE, workoutNumber = 1))
        assertTrue(gate.canWriteWorkout(USER, J1, SINGLE_WORKOUT_DATE, workoutNumber = 2))
        assertTrue(gate.canWriteWorkout(USER, J2, FRESH_DATE, workoutNumber = 1))
    }

    // ─── 11. The flow tracks workouts, not rows ──────────────────────────────

    @Test
    fun quotaFlow_reEmitsWhenANewWorkoutOpens_butNotWhenARecordJoinsAnExistingOne(): Unit = runTest {
        meterOn()
        val exId = seedCatalogExercise()
        val dayA = LocalDate(2026, 5, 1)
        val dayB = LocalDate(2026, 5, 2)

        val seen = CopyOnWriteArrayList<WorkoutQuota>()
        // Unconfined so emissions land as the write's table-invalidation fires,
        // rather than queuing behind this test coroutine's dispatcher.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            gate.getQuotaFlow(USER).collect { seen += it }
        }
        awaitQuota(seen, WorkoutQuota.Metered(used = 0, limit = 10))

        // A new workout is opened → the meter moves.
        repo.addExercisesToDate(USER, J1, dayA, 1, listOf(exId))
        awaitQuota(seen, WorkoutQuota.Metered(used = 1, limit = 10))
        val valuesSoFar = seen.distinct()
        assertEquals(
            listOf<WorkoutQuota>(WorkoutQuota.Metered(0, 10), WorkoutQuota.Metered(1, 10)),
            valuesSoFar,
        )

        // More rows inside THAT SAME workout: a second exercise, and a set.
        // These write `workoutRecords`, so the query DOES re-run — the point is
        // that the value it yields must not change.
        repo.addExercisesToDate(USER, J1, dayA, 1, listOf(exId))
        val weId = repo.getRecordsByDate(USER, J1, dayA).first().exercises.single().id
        repo.addSet(USER, J1, weId, weight = 100.0, reps = 5, distance = null, duration = null)
        settle()

        assertEquals(valuesSoFar, seen.distinct(), "extra records inside one workout must not move the meter")
        assertEquals(WorkoutQuota.Metered(used = 1, limit = 10), seen.last())

        // A SECOND workout on the SAME date is a new workout → the meter moves.
        repo.addExercisesToDate(USER, J1, dayA, 2, listOf(exId))
        awaitQuota(seen, WorkoutQuota.Metered(used = 2, limit = 10))

        // And so does a workout on a new date.
        repo.addExercisesToDate(USER, J1, dayB, 1, listOf(exId))
        awaitQuota(seen, WorkoutQuota.Metered(used = 3, limit = 10))
    }

    // ─── Flow test plumbing ──────────────────────────────────────────────────

    /**
     * The count flow runs on `Dispatchers.IO` with a real SQLDelight table
     * listener, so waiting on it is real-time waiting, not virtual.
     */
    private suspend fun awaitQuota(seen: List<WorkoutQuota>, expected: WorkoutQuota) {
        withContext(Dispatchers.Default) {
            runCatching { withTimeout(AWAIT_TIMEOUT_MS) { while (seen.lastOrNull() != expected) delay(20) } }
        }
        assertEquals(expected, seen.lastOrNull(), "flow never reached $expected; saw $seen")
    }

    private suspend fun settle() {
        withContext(Dispatchers.Default) { delay(SETTLE_MS) }
    }

    private companion object {
        const val USER = "user-1"
        const val J1 = "journal-1"
        const val J2 = "journal-2"

        /** Dates with no seeded history, for "opening a brand-new workout". */
        val FRESH_DATE: LocalDate = LocalDate(2026, 8, 3)
        val ANOTHER_FRESH_DATE: LocalDate = LocalDate(2026, 8, 4)

        /** Arbitrary fixed creation stamp — nothing depends on WHEN a workout was logged any more. */
        val SEEDED_AT: Instant = Instant.parse("2026-02-01T10:00:00Z")


        /** Carries workouts 1 AND 2 — the second is an existing slot. */
        val DOUBLE_WORKOUT_DATE: LocalDate = LocalDate(2026, 7, 24)

        /** Carries workout 1 only — asking for its workout 2 asks for a NEW workout. */
        val SINGLE_WORKOUT_DATE: LocalDate = LocalDate(2026, 7, 25)

        /**
         * Ten spent WORKOUTS over NINE dates — [DOUBLE_WORKOUT_DATE] holds two,
         * so exhaustion arrives a date earlier than a day-based unit would have
         * allowed. Deliberately excludes [FRESH_DATE].
         */
        val TEN_SPENT_WORKOUTS: List<Pair<LocalDate, Int>> = listOf(
            DOUBLE_WORKOUT_DATE to 1,
            DOUBLE_WORKOUT_DATE to 2,
            SINGLE_WORKOUT_DATE to 1,
            LocalDate(2026, 7, 26) to 1,
            LocalDate(2026, 7, 27) to 1,
            LocalDate(2026, 7, 28) to 1,
            LocalDate(2026, 7, 29) to 1,
            LocalDate(2026, 7, 30) to 1,
            LocalDate(2026, 7, 31) to 1,
            LocalDate(2026, 8, 1) to 1,
        )

        const val AWAIT_TIMEOUT_MS = 10_000L
        const val SETTLE_MS = 400L
    }
}
