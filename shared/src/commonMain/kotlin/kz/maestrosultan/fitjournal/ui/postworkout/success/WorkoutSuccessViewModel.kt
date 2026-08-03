package kz.maestrosultan.fitjournal.ui.postworkout.success

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.summary.BuildSessionSummaryUseCase
import kz.maestrosultan.fitjournal.domain.workout.summary.ExerciseLine
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionSummary
import kz.maestrosultan.fitjournal.ui.postworkout.FinishResult
import kz.maestrosultan.fitjournal.ui.postworkout.format.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.formatDuration
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter

/**
 * Presentation for the post-workout SUCCESS screen — strictly read-only (the
 * offline-first repos are never written; there is no mutation path here).
 *
 * In init the ended session is RE-READ from [sessionRepository] (by its page —
 * userId/journalId/date/workoutNumber from [FinishResult.context]; the repo has
 * no by-uuid read) and the summary is REBUILT via [buildSummary] with the
 * default `includeBest`, so [WorkoutSuccessContract.ViewState.personalRecord] is populated
 * even though the confirm sheet's snapshot skipped PR detection. PR detection
 * is composed INSIDE the use case — never call DetectSessionBestUseCase here.
 *
 * Failure ladder (local SQLite reads virtually never fail, but a crash on the
 * celebration screen is the worst possible place): rebuild throws → render the
 * finish-time snapshot ([FinishResult.summary]); that throws too → bare
 * fallback (localized fallback title, every section hidden). Both are logged.
 *
 * Haptics: the VM owns only the one-shot [WorkoutSuccessContract.ViewState.playSuccessHaptic]
 * flag (consumed via [WorkoutSuccessContract.ViewAction.SuccessHapticPlayed]); the host's
 * PostWorkoutHaptics seam is triggered by the composable, not injected here.
 */
class WorkoutSuccessViewModel internal constructor(
    private val result: FinishResult,
    private val buildSummary: BuildSessionSummaryUseCase,
    private val sessionRepository: WorkoutSessionRepository,
    private val muscleTitleFormatter: MuscleTitleFormatter,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel(), WorkoutSuccessContract.ViewModel {

    /**
     * Public construction path — the Android app module (a separate compilation
     * unit consuming :shared via Hilt) builds VMs itself, so class + constructor
     * must be public while [MuscleTitleFormatter] stays internal (its defaults
     * touch generated compose resources). Production always uses the Res-backed
     * formatter; jvmTest injects a deterministic one via the internal primary.
     */
    constructor(
        result: FinishResult,
        buildSummary: BuildSessionSummaryUseCase,
        sessionRepository: WorkoutSessionRepository,
        clock: Clock = Clock.System,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ) : this(
        result = result,
        buildSummary = buildSummary,
        sessionRepository = sessionRepository,
        muscleTitleFormatter = MuscleTitleFormatter(),
        clock = clock,
        timeZone = timeZone,
    )

    private val _uiState = MutableStateFlow(WorkoutSuccessContract.ViewState(loading = true))
    override val viewState: StateFlow<WorkoutSuccessContract.ViewState> = _uiState.asStateFlow()

    /**
     * The summary the share composer must be built from — NOT [FinishResult.summary].
     *
     * The confirm sheet builds its snapshot with `includeBest = false` (PR
     * detection is a per-exercise history scan, far too slow to block the
     * finish tap), so `result.summary.best` is ALWAYS null. Only the rebuild
     * below carries the personal record. A composer handed the finish-time
     * snapshot therefore reports `hasPersonalRecord = false` forever and can
     * never offer the "New best" layout — one of its four layouts, silently
     * unreachable.
     *
     * Null until the rebuild lands; hosts fall back to the snapshot, which
     * costs the PR layout on that one share rather than the whole screen.
     */
    var finalSummary: SessionSummary? = null
        private set

    init {
        viewModelScope.launch {
            _uiState.value = try {
                val rebuilt = rebuildFinalSummary()
                finalSummary = rebuilt
                stateFor(rebuilt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("final summary rebuild failed, rendering the finish snapshot", e)
                try {
                    stateFor(result.summary)
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    log("finish snapshot unusable, rendering the bare fallback", e2)
                    bareFallbackState()
                }
            }
        }
    }

    // ─── MVI entry point ────────────────────────────────────────────────

    override fun dispatch(action: WorkoutSuccessContract.ViewAction) {
        when (action) {
            WorkoutSuccessContract.ViewAction.SuccessHapticPlayed -> onSuccessHapticPlayed()
        }
    }

    /** Consume the one-shot success haptic after the composable has played it. */
    private fun onSuccessHapticPlayed() {
        _uiState.update { it.copy(playSuccessHaptic = false) }
    }

    /**
     * Host-owned teardown, mirroring WorkoutViewModel: the post-workout flow is
     * driven by the native hosts, so this VM may live outside a ViewModelStore.
     */
    fun dispose() {
        viewModelScope.cancel()
    }

    // ─── Assembly ───────────────────────────────────────────────────────

    private suspend fun rebuildFinalSummary(): SessionSummary {
        val ctx = result.context
        val session = checkNotNull(
            sessionRepository.getSessionByWorkoutNumber(ctx.userId, ctx.journalId, ctx.date, ctx.workoutNumber),
        ) { "no session on (${ctx.date}, workout #${ctx.workoutNumber})" }
        // Default includeBest — the summary carries `best`.
        return buildSummary(session)
    }

    private suspend fun stateFor(summary: SessionSummary): WorkoutSuccessContract.ViewState {
        val units = result.context.units
        val session = summary.session
        val now = clock.now()
        val maxMuscleSets = summary.muscles.maxOfOrNull { it.loggedSets } ?: 0
        return WorkoutSuccessContract.ViewState(
            loading = false,
            title = muscleTitleFormatter.title(summary.muscles),
            dateLine = LocaleFormatters.formatFullDate(session.date) + " · " +
                LocaleFormatters.formatTimeShort(session.startedAt, timeZone) + "–" +
                LocaleFormatters.formatTimeShort(session.endedAt ?: now, timeZone),
            tonnageText = if (summary.loggedSets > 0) {
                // Grouped: this is the hero number of the screen and design W4b
                // shows "14,850". The share card already grouped, so an
                // ungrouped hero also disagreed with the image made from it.
                WorkoutValueFormatter.groupedTonnage(summary.tonnageKg, units)
            } else {
                null // nothing logged — a "0 kg" hero number celebrates nothing
            },
            loggedSets = summary.loggedSets,
            exerciseCount = summary.exerciseCount,
            tiles = SuccessTiles(
                durationText = formatDuration(session.durationSec(now)),
                sets = summary.loggedSets,
                weekOrdinalText = LocaleFormatters.ordinal(summary.weekOrdinal),
            ),
            personalRecord = summary.best?.let { best ->
                PersonalRecordUi(
                    exerciseName = best.exerciseName,
                    weightText = WorkoutValueFormatter.value(best.weightKg, ResultType.WEIGHT_REPS, units),
                    reps = best.reps,
                    previousBestText = WorkoutValueFormatter.value(best.previousBestKg, ResultType.WEIGHT_REPS, units),
                    previousBestDate = best.previousBestDate,
                )
            },
            muscles = summary.muscles.mapIndexed { index, load ->
                MuscleBarUi(
                    category = load.category,
                    loggedSets = load.loggedSets,
                    fraction = if (maxMuscleSets > 0) load.loggedSets.toFloat() / maxMuscleSets else 0f,
                    rampIndex = index,
                )
            },
            exercises = summary.exercises.map { railLine(it, units) },
            playSuccessHaptic = true,
        )
    }

    private fun railLine(line: ExerciseLine, units: MeasurementSystem) = RailLineUi(
        name = line.name,
        loggedSets = line.loggedSets,
        totalSets = line.totalSets,
        // Weighted rows carry BOTH tonnage and reps, so the row picks. Zero
        // tonnage means no weight was entered, and "0 kg" tells the reader
        // nothing — the reps do.
        aggregate = when {
            line.tonnageKg != null && line.tonnageKg > 0.0 ->
                RailAggregate.Tonnage(WorkoutValueFormatter.value(line.tonnageKg, ResultType.WEIGHT_REPS, units))
            line.totalReps != null -> RailAggregate.Reps(line.totalReps)
            line.totalDistance != null || line.totalDurationSec != null -> {
                // Same rule as the tonnage branch above: a zero distance is not
                // a fact worth printing. DISTANCE_DURATION lines always carry a
                // non-null (summed) distance, so a duration-only exercise —
                // a plank, a timed row — arrives here as 0.0 and would
                // otherwise render "0 km · 1:30".
                val distance = line.totalDistance?.takeIf { it > 0.0 }
                val durationSec = line.totalDurationSec ?: 0
                if (distance == null && durationSec <= 0) {
                    null
                } else {
                    RailAggregate.DistanceDuration(
                        distanceText = distance
                            ?.let { WorkoutValueFormatter.value(it, ResultType.DISTANCE_DURATION, units) },
                        durationSec = durationSec,
                    )
                }
            }
            else -> null
        },
    )

    /** Worst case: even the snapshot didn't render. Fallback title, everything hidden. */
    private suspend fun bareFallbackState(): WorkoutSuccessContract.ViewState {
        val title = try {
            muscleTitleFormatter.title(emptyList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
        return WorkoutSuccessContract.ViewState(loading = false, title = title, playSuccessHaptic = true)
    }

    private fun log(message: String, error: Exception) {
        println("[FJ_POSTWORKOUT] WorkoutSuccessViewModel: $message: $error")
    }
}
