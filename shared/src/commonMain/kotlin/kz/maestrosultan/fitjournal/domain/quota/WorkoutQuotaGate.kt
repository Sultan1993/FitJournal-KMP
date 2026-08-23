package kz.maestrosultan.fitjournal.domain.quota

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * The ONE place that answers "how much free logging is left" and "may this
 * workout write proceed". A concrete class, not an interface — there is exactly
 * one implementation, and its only dependency is [RecordRepository], which is
 * already injected at both construction sites of the shared Workout ViewModel,
 * so wiring it costs no DI change on either platform.
 *
 * No DECISION here throws — entitlement, config and arithmetic all resolve to a
 * value, and every failure direction resolves to [WorkoutQuota.Unlimited] rather
 * than to "exhausted". The reads underneath still hit SQLite, though, so a locked
 * or corrupt database can surface as an exception: callers must treat that as
 * ALLOW. An unhandled Kotlin throw crossing into Swift is an uncatchable SIGABRT,
 * and this code runs on a tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutQuotaGate(
    private val records: RecordRepository,
) {

    suspend fun getQuota(userId: String): WorkoutQuota = when (val state = meteringState()) {
        MeteringState.Unmetered -> WorkoutQuota.Unlimited
        // Already had the product — a subscription or a trial. No free allowance,
        // and a surface of its own: the lapsed card speaks about their whole
        // library rather than a meter, so it needs the TOTAL count, not the
        // remaining one.
        MeteringState.NoAllowance ->
            WorkoutQuota.Lapsed(totalWorkouts = records.countMeteredWorkouts(userId))
        is MeteringState.Counted ->
            WorkoutQuota.Metered(used = records.countMeteredWorkouts(userId), limit = state.limit)
    }

    /**
     * Reactive quota. Re-emits on every `workoutRecords` write (SQLDelight table
     * invalidation) and on every entitlement / config change.
     */
    fun getQuotaFlow(userId: String): Flow<WorkoutQuota> =
        combine(FreeQuotaSettings.config, FreeQuotaSettings.isEntitled) { _, _ -> Unit }
            .flatMapLatest {
                when (val state = meteringState()) {
                    MeteringState.Unmetered -> flowOf<WorkoutQuota>(WorkoutQuota.Unlimited)
                    MeteringState.NoAllowance ->
                        records.countMeteredWorkoutsFlow(userId)
                            .map { total -> WorkoutQuota.Lapsed(total) }
                    is MeteringState.Counted ->
                        records.countMeteredWorkoutsFlow(userId)
                            .map { used -> WorkoutQuota.Metered(used, state.limit) }
                }
            }
            // Same fail-open contract the suspend path gets from its callers'
            // runCatching. A Flow that throws is TERMINATED — it does not retry on
            // the next table invalidation — so without this a transient SQLite
            // error freezes the quota for the rest of the process, and on iOS an
            // unbridged Kotlin throw crossing SKIE is an uncatchable SIGABRT.
            // `catch` is cancellation-transparent, so this does not swallow it.
            .catch { emit(WorkoutQuota.Unlimited) }

    private sealed interface MeteringState {
        data object Unmetered : MeteringState
        data object NoAllowance : MeteringState
        data class Counted(val limit: Int) : MeteringState
    }

    /**
     * Who is metered, and how. Deliberately reads as three plain cases:
     *
     *  - entitled, or the `limit <= 0` kill switch  → never metered;
     *  - hasEverSubscribed == null (not yet known)  → never metered. Unknown FAILS
     *    OPEN: a device that cannot reach Qonversion usually cannot reach Superwall
     *    either, so metering it would block the user with no way to buy. Harmless
     *    because the count is derived, so the first definitive answer applies
     *    retroactively — nothing is minted in the meantime;
     *  - hasEverSubscribed == true  → they have already had their free ride (a paid
     *    period OR a trial), so there is no allowance left to spend;
     *  - hasEverSubscribed == false → count every workout they have ever logged.
     *    No cutoff instant: under the hard wall a never-subscriber could not log at
     *    all, so "all of history" and "since metering began" are the same set for
     *    everyone except the pre-paywall free era, whose accounts have been walled
     *    out for years.
     */
    private fun meteringState(): MeteringState {
        if (FreeQuotaSettings.isEntitled.value == true) return MeteringState.Unmetered
        val config = FreeQuotaSettings.config.value
        if (config.limit <= 0) return MeteringState.Unmetered
        return when (config.hasEverSubscribed) {
            null -> MeteringState.Unmetered
            // Only wall them on an AUTHORITATIVE "not entitled". Unknown means the
            // subscription layer has not reported yet, and refusing a paying
            // subscriber a write is the one direction that must never happen.
            true ->
                if (FreeQuotaSettings.isEntitled.value == null) MeteringState.Unmetered
                else MeteringState.NoAllowance
            false -> MeteringState.Counted(config.limit)
        }
    }

    /**
     * THE precondition every workout write asks, and it is ONE rule: **you may
     * write in a workout that already exists; you may not open a new one.**
     * True when ANY of:
     *  1. quota is Unlimited (entitled / metering off / not started / limit <= 0)
     *  2. the quota is not exhausted
     *  3. the ([journalId], [date], [workoutNumber]) slot already holds at least
     *     one record, live or tombstoned
     *
     * Two properties worth naming fall out of rule 3 instead of needing clauses
     * of their own:
     *  - an empty started-but-unlogged workout consumes nothing — quota is spent
     *    by the FIRST record, not by pressing Start;
     *  - nobody is amputated mid-workout — once their first record exists the
     *    slot passes rule 3, whatever the date and whether or not a session is
     *    running.
     * Everything else (a fresh date, or a fresh workoutNumber on a date that
     * already has workouts) is refused at exhaustion.
     */
    suspend fun canWriteWorkout(
        userId: String,
        journalId: String,
        date: LocalDate,
        workoutNumber: Int,
    ): Boolean = when (val quota = getQuota(userId)) {
        WorkoutQuota.Unlimited -> true
        is WorkoutQuota.Metered ->
            !quota.isExhausted || records.hasAnyRecordInWorkout(userId, journalId, date, workoutNumber)
        // Rule 3 still applies to a lapsed subscriber: a workout that already
        // exists stays writable, so nobody is amputated mid-session the moment
        // their subscription runs out.
        is WorkoutQuota.Lapsed -> records.hasAnyRecordInWorkout(userId, journalId, date, workoutNumber)
    }

    /**
     * May the user open a workout that does not exist YET? Rules 1 and 2 of
     * [canWriteWorkout] without rule 3.
     *
     * For callers whose target slot is guaranteed to be new, so there is no slot
     * to ask rule 3 about: Repeat copies onto today as `max(workoutNumber) + 1`,
     * and the number is only known after the write. Asking [canWriteWorkout]
     * about a slot that does not exist would work by accident (rule 3 can never
     * fire on it) but reads as if the slot mattered, and it would need a made-up
     * date and workoutNumber to do it.
     *
     * Same fail-open contract: Unlimited passes, and the SQLite read underneath
     * can still throw, so callers must treat a thrown gate as ALLOW.
     */
    suspend fun canOpenNewWorkout(userId: String): Boolean = when (val quota = getQuota(userId)) {
        WorkoutQuota.Unlimited -> true
        is WorkoutQuota.Metered -> !quota.isExhausted
        is WorkoutQuota.Lapsed -> false
    }

}
