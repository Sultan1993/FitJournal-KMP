### Task 4: KMP WorkoutQuotaGate

**Goal:** Implement the single quota decision point: quota state, its Flow, and the write precondition.

**Files:**
- Create `Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt`

**Steps:**

0. **Cases you are answerable for (Task 9 proves them):** spec §12 cases 7, **7b** (the `9999` sentinel must yield `Unlimited`, not `Metered(0, 10)`), 7c, 8, 9, 10. Case 7b is why the future-cutoff clause exists; without it the meter card appears throughout the deliberately-unmetered rollout phase.

Create the file with exactly this content:

```kotlin
package kz.maestrosultan.fitjournal.domain.quota

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository

/**
 * The ONE place that answers "how much free logging is left" and "may this
 * workout write proceed". A concrete class, not an interface — there is exactly
 * one implementation, and its only dependency is [RecordRepository], which is
 * already injected at both construction sites of the shared Workout ViewModel,
 * so wiring it costs no DI change on either platform.
 *
 * Nothing here throws: every method returns a value. An unhandled Kotlin throw
 * crossing into Swift is an uncatchable SIGABRT, and this code runs on a tap.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutQuotaGate(
    private val records: RecordRepository,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {

    suspend fun getQuota(userId: String): WorkoutQuota {
        val cutoff = meteredCutoff() ?: return WorkoutQuota.Unlimited
        return WorkoutQuota.Metered(
            used = records.countMeteredWorkoutDays(userId, cutoff),
            limit = FreeQuotaSettings.config.value.limit,
        )
    }

    /**
     * Reactive quota. Re-emits on every `workoutRecords` write (SQLDelight table
     * invalidation) and on every entitlement / config change. `flatMapLatest`
     * because the underlying count query is parameterised by the cutoff, so a
     * config change must re-subscribe rather than reuse a stale query.
     */
    fun getQuotaFlow(userId: String): Flow<WorkoutQuota> =
        combine(FreeQuotaSettings.config, FreeQuotaSettings.isEntitled) { _, _ -> Unit }
            .flatMapLatest {
                val cutoff = meteredCutoff()
                if (cutoff == null) {
                    flowOf<WorkoutQuota>(WorkoutQuota.Unlimited)
                } else {
                    val limit = FreeQuotaSettings.config.value.limit
                    records.countMeteredWorkoutDaysFlow(userId, cutoff)
                        .map { used -> WorkoutQuota.Metered(used, limit) }
                }
            }

    /**
     * THE precondition every workout write asks. Allowed when ANY of:
     *  1. quota is Unlimited (entitled / metering off / not started / limit <= 0)
     *  2. [isSessionRunningOnDate] — carve-out C1a: never amputate a running
     *     workout. The caller scopes this to the session's OWN date, so a session
     *     running across midnight keeps its date, not today's (leak L8).
     *  3. remaining > 0
     *  4. [date] is today AND today already holds a record — carve-out C1b: the
     *     date the user was mid-way through when they hit exhaustion stays
     *     writable. Bounded to one calendar date: after rollover today holds
     *     nothing, and rule 3 is false, so no new date can be opened.
     * Everything else — including editing any earlier date — is blocked.
     */
    suspend fun canWriteWorkout(
        userId: String,
        journalId: String,
        date: LocalDate,
        isSessionRunningOnDate: Boolean,
    ): Boolean {
        val quota = getQuota(userId)
        if (quota is WorkoutQuota.Unlimited) return true
        if (isSessionRunningOnDate) return true
        val metered = quota as WorkoutQuota.Metered
        if (!metered.isExhausted) return true
        if (date != clock.todayIn(timeZone)) return false
        return records.hasAnyRecordOnDay(userId, journalId, date)
    }

    /**
     * The cutoff to count from, or null when metering must not apply at all:
     *  - entitled                       → never metered
     *  - no global cutoff               → metering off / unresolved / unparseable
     *  - limit <= 0                     → kill switch
     *  - effective cutoff in the FUTURE  → metering has not started yet. This is
     *    what makes the bundled 9999-01-01 sentinel actually mean "off" instead
     *    of producing Metered(0, 10) and showing a meter card throughout the
     *    deliberately-unmetered rollout phase. It also keeps a still-paid-through
     *    user unmetered for the remainder of their window, because their personal
     *    cutoff is their future expirationDate.
     */
    private fun meteredCutoff(): Instant? {
        if (FreeQuotaSettings.isEntitled.value) return null
        if (FreeQuotaSettings.config.value.limit <= 0) return null
        val cutoff = FreeQuotaSettings.effectiveCutoff ?: return null
        if (cutoff > clock.now()) return null
        return cutoff
    }
}
```

**Acceptance Criteria:**
- `getQuota` returns `Unlimited` for each of: entitled, null global cutoff, `limit <= 0`, **effective cutoff in the future**.
- `getQuota` otherwise returns `Metered(used = countMeteredWorkoutDays(userId, effectiveCutoff), limit)`.
- `canWriteWorkout` implements exactly the four allow-rules in order, and returns `false` for a non-today date when exhausted.
- No method declares `@Throws`; no normal path can throw.
- `:shared:assemble` succeeds.

**Verify:** `cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble`

```json:metadata
{"files":["Multiplatform/shared/src/commonMain/kotlin/kz/maestrosultan/fitjournal/domain/quota/WorkoutQuotaGate.kt"],"modelTier":"mechanical","verifyCommand":"cd /Users/sultan/Development/FitJournal-paywall/Multiplatform && ./gradlew :shared:assemble","acceptanceCriteria":["getQuota returns Unlimited for entitled, null global cutoff, limit<=0, and future effective cutoff","getQuota otherwise returns Metered(countMeteredWorkoutDays(userId, effectiveCutoff), limit)","canWriteWorkout implements the four allow-rules in order and returns false for a non-today date when exhausted","No @Throws and no throwing normal path",":shared:assemble succeeds"],"blockedBy":[1,2]}
```

---

