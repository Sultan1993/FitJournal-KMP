package kz.maestrosultan.fitjournal.ui.workout.details.components

import kotlin.math.abs
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.domain.calculation.TonnageCalculator
import kz.maestrosultan.fitjournal.domain.calculation.WorkloadCalculator
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.summary.MuscleLoad
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionBest
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.history_cardio
import kz.maestrosultan.fitjournal.shared.generated.resources.history_exercise_count
import kz.maestrosultan.fitjournal.shared.generated.resources.history_set_count
import kz.maestrosultan.fitjournal.shared.generated.resources.history_workout_count
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_total_volume
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.formatDuration
import kz.maestrosultan.fitjournal.ui.workout.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsContract
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * Compose-resource lookups injected (like [MuscleTitleFormatter]) so jvmTest
 * can supply fixed strings instead of depending on the test JVM's locale/resources.
 */
internal class WorkoutDetailsStrings(
    val totalVolumeLabel: suspend () -> String = { getString(Res.string.workout_details_total_volume) },
    val cardioLabel: suspend () -> String = { getString(Res.string.history_cardio) },
    val workoutCount: suspend (Int) -> String = { getPluralString(Res.plurals.history_workout_count, it, it) },
    val exerciseCount: suspend (Int) -> String = { getPluralString(Res.plurals.history_exercise_count, it, it) },
    val setCount: suspend (Int) -> String = { getPluralString(Res.plurals.history_set_count, it, it) },
)

/**
 * Pure builder for the WorkoutDetails screen (WD1/WD2/WD3). Folds one day's
 * loaded [records] + [sessions] + already-detected [sessionBests] into
 * [WorkoutDetailsContract.Content.Loaded]; never runs history detection or
 * re-derives a figure itself, only renders what it's handed.
 *
 * [records] must be non-empty — the VM dismisses the screen on an empty day
 * instead of calling this. Grouped by [WorkoutRecord.workoutNumber]: a single
 * group renders WD1/WD2; more than one renders WD3 (day-scoped hero + stack,
 * with every other section scoped to [focusedWorkoutNumber]).
 *
 * [sessions] join by workoutNumber; an orphan with no matching record group
 * is silently dropped (defensive — no production path should produce one).
 *
 * [focusedWorkoutNumber] falls back to the lowest present workout number when
 * stale (e.g. after a delete), mirroring the VM's own refocus rule.
 */
internal suspend fun buildWorkoutDetailsUi(
    date: LocalDate,
    records: List<WorkoutRecord>,
    sessions: List<WorkoutSession>,
    measurementSystem: MeasurementSystem,
    sessionBests: Map<Int, SessionBest?>,
    notesByWorkout: Map<Int, String>,
    focusedWorkoutNumber: Int,
    timeZone: TimeZone,
    now: Instant,
    muscleTitleFormatter: MuscleTitleFormatter = MuscleTitleFormatter(),
    strings: WorkoutDetailsStrings = WorkoutDetailsStrings(),
): WorkoutDetailsContract.Content.Loaded {
    require(records.isNotEmpty()) { "buildWorkoutDetailsUi requires at least one record for $date" }

    val recordsByWorkout: Map<Int, List<WorkoutRecord>> = records
        .groupBy { it.workoutNumber }
        .mapValues { (_, group) -> group.sortedBy { it.position } }
    val workoutNumbers = recordsByWorkout.keys.sorted()
    val sessionsByWorkout: Map<Int, WorkoutSession> = sessions.associateBy { it.workoutNumber }
    // Defensive: orphan sessions (no matching record group) drop silently.
    val matchedSessions = workoutNumbers.mapNotNull { sessionsByWorkout[it] }

    val workouts = workoutNumbers.map { number ->
        buildWorkoutUi(
            workoutNumber = number,
            workoutRecords = recordsByWorkout.getValue(number),
            session = sessionsByWorkout[number],
            best = sessionBests[number],
            noteText = notesByWorkout[number],
            measurementSystem = measurementSystem,
            now = now,
        )
    }

    val isMultiWorkoutDay = workoutNumbers.size > 1
    val stack = if (isMultiWorkoutDay) {
        workoutNumbers.map { number ->
            buildStackRow(
                workoutNumber = number,
                workoutRecords = recordsByWorkout.getValue(number),
                session = sessionsByWorkout[number],
                measurementSystem = measurementSystem,
                timeZone = timeZone,
                now = now,
                muscleTitleFormatter = muscleTitleFormatter,
                strings = strings,
            )
        }
    } else {
        emptyList()
    }

    val header = if (isMultiWorkoutDay) {
        buildDayHeader(date = date, workoutCount = workoutNumbers.size, strings = strings)
    } else {
        buildWorkoutHeader(
            date = date,
            session = sessionsByWorkout[workoutNumbers.single()],
            timeZone = timeZone,
            now = now,
        )
    }

    // Multi-workout days headline the whole day; a single-workout day is the same sum.
    val heroRecords = if (isMultiWorkoutDay) records else recordsByWorkout.getValue(workoutNumbers.single())
    val hero = buildHero(scopeRecords = heroRecords, measurementSystem = measurementSystem, strings = strings)

    val effectiveFocusedWorkoutNumber = if (focusedWorkoutNumber in recordsByWorkout) focusedWorkoutNumber else workoutNumbers.first()

    return WorkoutDetailsContract.Content.Loaded(
        date = date,
        header = header,
        hero = hero,
        workouts = workouts,
        focusedWorkoutNumber = effectiveFocusedWorkoutNumber,
        // Exact, not "is today": these sessions are already this date's, so a
        // session still running from last night matches the workout it belongs to
        // and nothing else. A session running on a DIFFERENT page of this day is
        // not this workout, and repeating into it is the rule working as intended.
        focusedWorkoutIsRunning =
            sessionsByWorkout[effectiveFocusedWorkoutNumber]?.isRunning == true,
        stack = stack,
    )
}

// ─── Header ─────────────────────────────────────────────────────────────

private fun buildWorkoutHeader(
    date: LocalDate,
    session: WorkoutSession?,
    timeZone: TimeZone,
    now: Instant,
): WorkoutDetailsContract.Header {
    // Nav bar: full-weekday date, with the session's time range under it. No session,
    // no subtitle — there is nothing else a single workout needs to say up there.
    return WorkoutDetailsContract.Header(
        title = LocaleFormatters.formatFullDate(date),
        subtitle = timeRangeText(session, timeZone, now),
    )
}

/** Several workouts in a day: the count replaces the time range, which no longer describes one thing. */
private suspend fun buildDayHeader(
    date: LocalDate,
    workoutCount: Int,
    strings: WorkoutDetailsStrings,
): WorkoutDetailsContract.Header = WorkoutDetailsContract.Header(
    title = LocaleFormatters.formatFullDate(date),
    subtitle = strings.workoutCount(workoutCount),
)

// ─── Hero ───────────────────────────────────────────────────────────────

/**
 * Volume and cardio as two side-by-side stats; either half is null when the day has
 * none of it, and the UI drops the divider with it. The two callers differ only in
 * which records they sum (one workout vs the whole day). The cardio label carries the
 * distance ("Cardio · 5.1 km") because the value slot is the duration.
 */
private suspend fun buildHero(
    scopeRecords: List<WorkoutRecord>,
    measurementSystem: MeasurementSystem,
    strings: WorkoutDetailsStrings,
): WorkoutDetailsContract.Hero {
    val tonnage = TonnageCalculator.forRecords(scopeRecords)
    val cardioMinutes = scopeRecords.sumOf { TonnageCalculator.cardioDurationSeconds(it) } / SECONDS_PER_MINUTE
    val cardioDistanceTotal = cardioDistance(scopeRecords)
    val distanceText = cardioDistanceTotal
        .takeIf { it > 0.0 }
        ?.let { WorkoutValueFormatter.distance(it, measurementSystem) }

    val volume = tonnage.takeIf { it > 0.0 }?.let {
        WorkoutDetailsContract.HeroStat(
            value = WorkoutValueFormatter.groupedTonnageNumber(it),
            unit = WorkoutValueFormatter.unit(ResultType.WEIGHT_REPS, measurementSystem),
            label = strings.totalVolumeLabel(),
        )
    }

    // Duration is the headline figure; a run logged as distance only promotes the
    // distance into the value slot rather than showing an empty stat.
    val cardio = when {
        cardioMinutes > 0 -> {
            val (value, unit) = WorkoutValueFormatter.durationParts(cardioMinutes)
            WorkoutDetailsContract.HeroStat(
                value = value,
                unit = unit,
                label = listOfNotNull(strings.cardioLabel(), distanceText).joinToString(" · "),
            )
        }
        distanceText != null -> WorkoutDetailsContract.HeroStat(
            value = distanceText,
            unit = null,
            label = strings.cardioLabel(),
        )
        else -> null
    }
    return WorkoutDetailsContract.Hero(volume = volume, cardio = cardio)
}

// ─── WD3 stack row ──────────────────────────────────────────────────────

private suspend fun buildStackRow(
    workoutNumber: Int,
    workoutRecords: List<WorkoutRecord>,
    session: WorkoutSession?,
    measurementSystem: MeasurementSystem,
    timeZone: TimeZone,
    now: Instant,
    muscleTitleFormatter: MuscleTitleFormatter,
    strings: WorkoutDetailsStrings,
): WorkoutDetailsContract.StackRow {
    val workoutExercises = workoutRecords.flatMap { it.exercises }
    val title = muscleTitleFormatter.title(rankedMuscles(workoutExercises))
    val subtitle = listOfNotNull(
        timeRangeText(session, timeZone, now),
        strings.exerciseCount(performedExerciseCount(workoutExercises)),
    ).joinToString(" · ")
    return WorkoutDetailsContract.StackRow(
        workoutNumber = workoutNumber,
        title = title,
        subtitle = subtitle,
        volumeText = workoutVolumeText(workoutRecords, measurementSystem),
    )
}

/** Stack row: cardio-only shows duration; everything else (incl. mixed) shows tonnage —
 *  the day hero already carries the cardio aggregate. */
private fun workoutVolumeText(workoutRecords: List<WorkoutRecord>, measurementSystem: MeasurementSystem): String {
    val tonnage = TonnageCalculator.forRecords(workoutRecords)
    val cardioMinutes = workoutRecords.sumOf { TonnageCalculator.cardioDurationSeconds(it) } / SECONDS_PER_MINUTE
    val cardioOnly = cardioMinutes > 0 && tonnage <= 0.0
    return if (cardioOnly) {
        WorkoutValueFormatter.duration(cardioMinutes)
    } else {
        WorkoutValueFormatter.groupedTonnage(tonnage, measurementSystem)
    }
}

// ─── Per-workout body (tiles, NEW BEST, note, workload, exercises) ───────

private fun buildWorkoutUi(
    workoutNumber: Int,
    workoutRecords: List<WorkoutRecord>,
    session: WorkoutSession?,
    best: SessionBest?,
    noteText: String?,
    measurementSystem: MeasurementSystem,
    now: Instant,
): WorkoutDetailsContract.WorkoutUi {
    val workoutExercises = workoutRecords.flatMap { it.exercises }
    // Skipped = every member logged no sets. A partial superset (>=1 member
    // logged) stays whole in exerciseGroups.
    val (skipped, performed) = workoutRecords
        .map { record ->
            WorkoutDetailsContract.ExerciseGroup(
                recordId = record.id,
                members = record.exercises.map { we -> exerciseRow(we, measurementSystem) },
            )
        }
        .partition { group -> group.members.all { it.sets.isEmpty() } }
    return WorkoutDetailsContract.WorkoutUi(
        workoutNumber = workoutNumber,
        durationText = session?.let { formatDuration(it.durationSec(now)) },
        exerciseCount = performedExerciseCount(workoutExercises),
        setCount = loggedSetCount(workoutExercises),
        newBest = best?.let { newBestUi(it, measurementSystem) },
        // Always present: any workout can carry a note, keyed by its page. Blank/absent
        // text renders the add-note placeholder.
        note = WorkoutDetailsContract.NoteUi(workoutNumber = workoutNumber, text = noteText?.takeIf { it.isNotBlank() }),
        workload = workloadRows(workoutRecords, measurementSystem),
        exerciseGroups = performed,
        skippedGroups = skipped,
        // The share card is built from records (summary), not the session — a
        // directly-logged workout has everything the composer needs.
        canShare = workoutRecords.isNotEmpty(),
    )
}

/** "Machine Bench Press · 100 kg × 10" — value(weightKg) with an optional reps. */
private fun newBestUi(best: SessionBest, measurementSystem: MeasurementSystem): WorkoutDetailsContract.NewBestUi {
    val valueText = WorkoutValueFormatter.value(best.weightKg, ResultType.WEIGHT_REPS, measurementSystem)
    val repsText = best.reps?.let { WorkoutValueFormatter.reps(it, ResultType.WEIGHT_REPS) }
    val pair = listOfNotNull(valueText, repsText).joinToString(" ")
    return WorkoutDetailsContract.NewBestUi(text = "${best.exerciseName} · $pair")
}

/**
 * Every trained category gets its own row (`showOther = false`): this screen is a
 * single workout, where the collapsed OTHER bucket hid whole exercises — a lone
 * cardio slot read as "Other 10%". [WorkloadCalculator] still decides order and
 * percentage; this only attaches the kg amount. OTHER appears only when it is a
 * real category on an exercise, never as a remainder.
 */
private fun workloadRows(
    workoutRecords: List<WorkoutRecord>,
    measurementSystem: MeasurementSystem,
): List<WorkoutDetailsContract.WorkloadRow> {
    val entries = WorkloadCalculator.calculate(workoutRecords, showOther = false)
    if (entries.isEmpty()) return emptyList()

    val exercisesByCategory: Map<CategoryType, List<WorkoutExercise>> =
        workoutRecords.flatMap { it.exercises }.groupBy { it.exercise.primaryCategory.type }

    return entries.map { entry ->
        val exercises = exercisesByCategory[entry.category].orEmpty()
        val tonnage = exercises.sumOf { we -> TonnageCalculator.forExercise(we) }
        // Cardio buckets carry no tonnage, so they report their logged minutes
        // instead of an empty amount column. WorkoutSet.duration is in minutes.
        val minutes = exercises
            .filter { it.exercise.resultType == ResultType.DISTANCE_DURATION }
            .sumOf { we -> we.sets.sumOf { it.duration ?: 0 } }
        WorkoutDetailsContract.WorkloadRow(
            category = entry.category,
            percentage = entry.percentage,
            amountText = when {
                tonnage > 0.0 -> WorkoutValueFormatter.groupedTonnage(tonnage, measurementSystem)
                minutes > 0 -> WorkoutValueFormatter.duration(minutes)
                else -> null
            },
        )
    }
}

// ─── Exercise rows ────────────────────────────────────────────────────────

private fun exerciseRow(we: WorkoutExercise, measurementSystem: MeasurementSystem) = WorkoutDetailsContract.ExerciseRow(
    workoutExerciseId = we.id,
    exercise = we.exercise,
    name = we.exercise.name,
    volumeText = exerciseVolumeText(we, measurementSystem),
    delta = exerciseDelta(we, measurementSystem),
    sets = setChips(we, measurementSystem),
    comment = we.comment,
)

/** Tonnage for WEIGHT_REPS, else logged distance (falling back to logged duration
 *  when nothing was covered) — null when nothing was logged. */
private fun exerciseVolumeText(we: WorkoutExercise, measurementSystem: MeasurementSystem): String? {
    if (!we.hasLoggedSets) return null
    return when (we.exercise.resultType) {
        ResultType.WEIGHT_REPS -> WorkoutValueFormatter.groupedTonnage(TonnageCalculator.forExercise(we), measurementSystem)
        ResultType.DISTANCE_DURATION -> {
            val logged = we.sets.filter { it.isLogged }
            val distance = logged.sumOf { it.distance ?: 0.0 }
            val minutes = logged.sumOf { it.duration ?: 0 }
            if (distance > 0.0) WorkoutValueFormatter.distance(distance, measurementSystem) else WorkoutValueFormatter.duration(minutes)
        }
    }
}

/**
 * Distance delta for cardio only when BOTH sides logged a distance — a
 * duration-only cardio exercise carries no pill. No prior occurrence -> no pill.
 * A change too small to render ("+0 kg") gets no pill either: repeating a workout
 * exactly is not progress, and the pill's whole job is to report change.
 */
private fun exerciseDelta(we: WorkoutExercise, measurementSystem: MeasurementSystem): WorkoutDetailsContract.DeltaUi? {
    val prior = we.lastOccurrence ?: return null
    return when (we.exercise.resultType) {
        ResultType.WEIGHT_REPS -> {
            val delta = TonnageCalculator.forExercise(we) - TonnageCalculator.forSets(prior.sets)
            val magnitude = WorkoutValueFormatter.groupedTonnage(abs(delta), measurementSystem)
            // Compare formatted, not raw: the pill is suppressed exactly when it would read zero.
            if (magnitude == WorkoutValueFormatter.groupedTonnage(0.0, measurementSystem)) return null
            val positive = delta >= 0
            WorkoutDetailsContract.DeltaUi(positive = positive, text = "${deltaSign(positive)}$magnitude")
        }
        ResultType.DISTANCE_DURATION -> {
            val current = we.sets.filter { it.isLogged }.sumOf { it.distance ?: 0.0 }
            val previous = prior.sets.sumOf { it.distance ?: 0.0 }
            if (current <= 0.0 || previous <= 0.0) return null
            val delta = current - previous
            val magnitude = WorkoutValueFormatter.distance(abs(delta), measurementSystem)
            if (magnitude == WorkoutValueFormatter.distance(0.0, measurementSystem)) return null
            val positive = delta >= 0
            WorkoutDetailsContract.DeltaUi(positive = positive, text = "${deltaSign(positive)}$magnitude")
        }
    }
}

/** Same sign convention as the shipped `WorkoutListDeltaPill`: "+" / U+2212 MINUS SIGN. */
private fun deltaSign(positive: Boolean): String = if (positive) "+" else "−"

/** Own numbers only (Assumption 11) — no [WorkoutExercise.lastOccurrence] ghost values here. */
private fun setChips(we: WorkoutExercise, measurementSystem: MeasurementSystem): List<WorkoutDetailsContract.SetChip> =
    we.sets.filter { it.hasOwnNumbers }.map { set ->
        WorkoutDetailsContract.SetChip(
            valueText = WorkoutValueFormatter.value(set.displayValue, we.exercise.resultType, measurementSystem),
            repsText = WorkoutValueFormatter.reps(set.displayReps, we.exercise.resultType, spaced = false),
        )
    }

// ─── Small shared helpers ─────────────────────────────────────────────────

private fun timeRangeText(session: WorkoutSession?, timeZone: TimeZone, now: Instant): String? =
    session?.let {
        "${LocaleFormatters.formatTimeShort(it.startedAt, timeZone)}–${LocaleFormatters.formatTimeShort(it.endedAt ?: now, timeZone)}"
    }

/** Sum of cardio (DISTANCE_DURATION) set distances, raw stored unit — mirrors WorkoutListFeed's helper. */
private fun cardioDistance(records: List<WorkoutRecord>): Double =
    records.flatMap { it.exercises }.flatMap { it.sets }
        .filter { it.resultType == ResultType.DISTANCE_DURATION }
        .sumOf { it.distance ?: 0.0 }

/**
 * Matches `SessionSummary.exerciseCount`: merges occurrences by exercise uuid
 * first, then counts groups with at least one logged set — so the same
 * exercise split across two records in one workout still counts once.
 */
private fun performedExerciseCount(workoutExercises: List<WorkoutExercise>): Int {
    val byExercise = LinkedHashMap<String, MutableList<WorkoutExercise>>()
    workoutExercises.forEach { byExercise.getOrPut(it.exercise.uuid) { mutableListOf() }.add(it) }
    return byExercise.values.count { occurrences -> occurrences.any { it.hasLoggedSets } }
}

/** SETS tile / total: logged sets ([kz.maestrosultan.fitjournal.domain.workout.WorkoutSet.isLogged]), no merging needed. */
private fun loggedSetCount(workoutExercises: List<WorkoutExercise>): Int =
    workoutExercises.sumOf { we -> we.sets.count { it.isLogged } }

/** Logged sets per muscle, ranked desc; ties keep day order (matches `SessionSummary.muscles`). */
private fun rankedMuscles(workoutExercises: List<WorkoutExercise>): List<MuscleLoad> {
    val counts = LinkedHashMap<CategoryType, Int>()
    workoutExercises.forEach { we ->
        val logged = we.sets.count { it.isLogged }
        if (logged > 0) {
            val category = we.exercise.primaryCategory.type
            counts[category] = (counts[category] ?: 0) + logged
        }
    }
    return counts.entries.sortedByDescending { it.value }.map { MuscleLoad(it.key, it.value) }
}

private const val SECONDS_PER_MINUTE = 60
