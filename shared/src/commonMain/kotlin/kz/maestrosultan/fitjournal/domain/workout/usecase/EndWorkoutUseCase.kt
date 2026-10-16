package kz.maestrosultan.fitjournal.domain.workout.usecase

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.analytics.Analytics
import kz.maestrosultan.fitjournal.analytics.AnalyticsEvent
import kz.maestrosultan.fitjournal.analytics.AnalyticsTracker
import kz.maestrosultan.fitjournal.analytics.SessionEndVia
import kz.maestrosultan.fitjournal.analytics.dayContextOf
import kz.maestrosultan.fitjournal.analytics.systemToday
import kz.maestrosultan.fitjournal.domain.sync.SyncReason
import kz.maestrosultan.fitjournal.domain.sync.SyncTrigger
import kz.maestrosultan.fitjournal.domain.workout.RecordRepository
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository

/**
 * Finish the user's running workout (from anywhere — the running session shows
 * End on every page and every date). Returns the finished session, or null if
 * nothing was running (a no-op, never an error).
 *
 * Discard-empty is deliberately NOT here yet: a workout ended with no records
 * logged is finalized like any other for now. See
 * `docs/workout-session-pager-open-items.md` §1 — the whole "session ↔ records
 * consistency" family is one deferred piece.
 */
class EndWorkoutUseCase(
    private val sessions: WorkoutSessionRepository,
    private val records: RecordRepository,
    private val syncTrigger: SyncTrigger,
    private val tracker: AnalyticsTracker = Analytics,
    private val today: () -> LocalDate = ::systemToday,
) {

    /** Swift-visible constructor — see [StartWorkoutUseCase]. */
    constructor(sessions: WorkoutSessionRepository, records: RecordRepository, syncTrigger: SyncTrigger) :
        this(sessions, records, syncTrigger, Analytics, ::systemToday)

    /** Swift-visible overload: the End button. */
    suspend operator fun invoke(userId: String): WorkoutSession? = invoke(userId, SessionEndVia.END_BUTTON)

    suspend operator fun invoke(userId: String, via: SessionEndVia): WorkoutSession? {
        val finished = sessions.endSession(userId) ?: return null
        syncTrigger.requestTick(SyncReason.PostWrite.WorkoutSession)
        val counts = records.endedSlotCounts(userId, finished)
        tracker.track(
            AnalyticsEvent.WorkoutSessionEnd(
                journalId = finished.journalId,
                workoutDate = finished.date,
                workoutNumber = finished.workoutNumber,
                durationSec = finished.durationSec(Clock.System.now()),
                via = via,
                day = dayContextOf(finished.date, today()),
                exerciseCount = counts?.first,
                setCount = counts?.second,
            ),
        )
        return finished
    }
}

/**
 * (exercise count, logged-set count) of the slot [session] just ended, or null when the
 * read throws — the caller then omits both counts rather than guessing.
 */
internal suspend fun RecordRepository.endedSlotCounts(userId: String, session: WorkoutSession): Pair<Int, Int>? =
    try {
        val exercises = getRecordsByDate(userId, session.journalId, session.date, includeLastOccurrence = false)
            .filter { it.workoutNumber == session.workoutNumber }
            .flatMap { it.exercises }
        exercises.size to exercises.sumOf { exercise -> exercise.sets.count { it.isLogged } }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
