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
import kz.maestrosultan.fitjournal.ui.postworkout.seams.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.postworkout.seams.formatDuration
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter

/**
 * Presentation for the post-workout SUCCESS screen — strictly read-only (the
 * offline-first repos are never written; there is no mutation path here).
 *
 * In init the ended session is RE-READ from [sessionRepository] (by its page —
 * userId/journalId/date/workoutNumber from [FinishResult.context]; the repo has
 * no by-uuid read) and the summary is REBUILT via [buildSummary] with the
 * default `includeBest`, so [WorkoutSuccessUiState.personalRecord] is populated
 * even though the confirm sheet's snapshot skipped PR detection. PR detection
 * is composed INSIDE the use case — never call DetectSessionBestUseCase here.
 *
 * Failure ladder (local SQLite reads virtually never fail, but a crash on the
 * celebration screen is the worst possible place): rebuild throws → render the
 * finish-time snapshot ([FinishResult.summary]); that throws too → bare
 * fallback (localized fallback title, every section hidden). Both are logged.
 *
 * Haptics: the VM owns only the one-shot [WorkoutSuccessUiState.playSuccessHaptic]
 * flag (consumed via [onSuccessHapticPlayed]); the host's PostWorkoutHaptics
 * seam is triggered by the composable, not injected here.
 */
internal class WorkoutSuccessViewModel(
    private val result: FinishResult,
    private val buildSummary: BuildSessionSummaryUseCase,
    private val sessionRepository: WorkoutSessionRepository,
    private val muscleTitleFormatter: MuscleTitleFormatter,
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutSuccessUiState(loading = true))
    val uiState: StateFlow<WorkoutSuccessUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                stateFor(rebuildFinalSummary())
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

    /** Consume the one-shot success haptic after the composable has played it. */
    fun onSuccessHapticPlayed() {
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

    private suspend fun stateFor(summary: SessionSummary): WorkoutSuccessUiState {
        val units = result.context.units
        val session = summary.session
        val now = clock.now()
        val maxMuscleSets = summary.muscles.maxOfOrNull { it.loggedSets } ?: 0
        return WorkoutSuccessUiState(
            loading = false,
            title = muscleTitleFormatter.title(summary.muscles),
            dateLine = LocaleFormatters.formatFullDate(session.date) + " · " +
                LocaleFormatters.formatTimeShort(session.startedAt, timeZone) + "–" +
                LocaleFormatters.formatTimeShort(session.endedAt ?: now, timeZone),
            tonnageText = if (summary.loggedSets > 0) {
                WorkoutValueFormatter.value(summary.tonnageKg, ResultType.WEIGHT_REPS, units)
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
        // ExerciseLine populates exactly one aggregate family (or none).
        aggregate = when {
            line.tonnageKg != null ->
                RailAggregate.Tonnage(WorkoutValueFormatter.value(line.tonnageKg, ResultType.WEIGHT_REPS, units))
            line.totalReps != null -> RailAggregate.Reps(line.totalReps)
            line.totalDistance != null || line.totalDurationSec != null -> RailAggregate.DistanceDuration(
                distanceText = WorkoutValueFormatter.value(line.totalDistance ?: 0.0, ResultType.DISTANCE_DURATION, units),
                durationSec = line.totalDurationSec ?: 0,
            )
            else -> null
        },
    )

    /** Worst case: even the snapshot didn't render. Fallback title, everything hidden. */
    private suspend fun bareFallbackState(): WorkoutSuccessUiState {
        val title = try {
            muscleTitleFormatter.title(emptyList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
        return WorkoutSuccessUiState(loading = false, title = title, playSuccessHaptic = true)
    }

    private fun log(message: String, error: Exception) {
        println("[FJ_POSTWORKOUT] WorkoutSuccessViewModel: $message: $error")
    }
}
