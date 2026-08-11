package kz.maestrosultan.fitjournal.ui.workoutdetails.components

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
import kz.maestrosultan.fitjournal.shared.generated.resources.history_exercise_count
import kz.maestrosultan.fitjournal.shared.generated.resources.history_set_count
import kz.maestrosultan.fitjournal.shared.generated.resources.history_workout_count
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_day_volume
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_total_volume
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.format.formatDuration
import kz.maestrosultan.fitjournal.ui.postworkout.format.MuscleTitleFormatter
import kz.maestrosultan.fitjournal.ui.workout.WorkoutValueFormatter
import kz.maestrosultan.fitjournal.ui.workoutdetails.WorkoutDetailsContract
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString

/**
 * The compose-resource-dependent lookups [buildWorkoutDetailsUi] needs beyond
 * [MuscleTitleFormatter] (muscle-title joining is that class's own job).
 * Injectable the same way [MuscleTitleFormatter] is: production resolves
 * through the real shared `Res` bundle — the exact plural resources
 * [kz.maestrosultan.fitjournal.ui.workoutlist.components.WorkoutListDayRow]
 * uses for its own meta rows — while jvmTest injects fixed strings so
 * assertions don't depend on the test JVM's locale or resource loading.
 */
internal class WorkoutDetailsStrings(
    val totalVolumeLabel: suspend () -> String = { getString(Res.string.workout_details_total_volume) },
    val dayVolumeLabel: suspend () -> String = { getString(Res.string.workout_details_day_volume) },
    val workoutCount: suspend (Int) -> String = { getPluralString(Res.plurals.history_workout_count, it, it) },
    val exerciseCount: suspend (Int) -> String = { getPluralString(Res.plurals.history_exercise_count, it, it) },
    val setCount: suspend (Int) -> String = { getPluralString(Res.plurals.history_set_count, it, it) },
)

/**
 * Pure builder for the WorkoutDetails screen (WD1/WD2/WD3, design spec
 * §4.2-§4.3): folds one day's already-loaded [records] (each carrying
 * [WorkoutExercise.lastOccurrence]) + that day's [sessions] + the per-workout
 * NEW BEST detections into [WorkoutDetailsContract.Content.Loaded]. Every
 * figure comes from an existing calculator/formatter (§6.1 of the design
 * spec) — this function never re-derives a number with raw arithmetic or
 * formats a date/number with a literal pattern.
 *
 * [records] must be non-empty — the VM dismisses the screen on an empty day
 * instead of calling this. Grouped by [WorkoutRecord.workoutNumber]: a single
 * group renders WD1/WD2 (day == the one workout, no stack); more than one
 * renders WD3 (a day-scoped hero + [WorkoutDetailsContract.Content.Loaded.stack],
 * with every other section scoped to [focusedWorkoutNumber]).
 *
 * [sessions] are joined to their record group by [WorkoutSession.workoutNumber];
 * a session with no matching record group is ignored — defensive, since no
 * production path should produce that orphan (§6.1's join rule).
 *
 * [sessionBests] carries the already-detected [SessionBest] per workout number
 * (or null when that workout set no record) — [buildWorkoutDetailsUi] never
 * runs history detection itself, it only renders what it is handed.
 *
 * [focusedWorkoutNumber] selects the WD3 stack's lifted row; when it does not
 * match any of [records]' workout numbers (a stale focus after a delete) this
 * falls back to the lowest present workout number, mirroring the VM's own
 * refocus rule (§6 `DeleteConfirmed`).
 */
internal suspend fun buildWorkoutDetailsUi(
    date: LocalDate,
    records: List<WorkoutRecord>,
    sessions: List<WorkoutSession>,
    measurementSystem: MeasurementSystem,
    sessionBests: Map<Int, SessionBest?>,
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
    // Defensive join (§6.1): only sessions whose workoutNumber matches a loaded
    // record group ever feed a day-wide aggregate; an orphan is silently dropped.
    val matchedSessions = workoutNumbers.mapNotNull { sessionsByWorkout[it] }

    val workouts = workoutNumbers.map { number ->
        buildWorkoutUi(
            workoutNumber = number,
            workoutRecords = recordsByWorkout.getValue(number),
            session = sessionsByWorkout[number],
            best = sessionBests[number],
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
        buildDayHeader(date = date, matchedSessions = matchedSessions, workoutCount = workoutNumbers.size, now = now, strings = strings)
    } else {
        buildWorkoutHeader(
            date = date,
            workoutRecords = recordsByWorkout.getValue(workoutNumbers.single()),
            session = sessionsByWorkout[workoutNumbers.single()],
            timeZone = timeZone,
            now = now,
            muscleTitleFormatter = muscleTitleFormatter,
        )
    }

    val hero = if (isMultiWorkoutDay) {
        buildDayHero(records = records, measurementSystem = measurementSystem, strings = strings)
    } else {
        buildWorkoutHero(workoutRecords = recordsByWorkout.getValue(workoutNumbers.single()), measurementSystem = measurementSystem, strings = strings)
    }

    val effectiveFocusedWorkoutNumber = if (focusedWorkoutNumber in recordsByWorkout) focusedWorkoutNumber else workoutNumbers.first()

    return WorkoutDetailsContract.Content.Loaded(
        date = date,
        header = header,
        hero = hero,
        workouts = workouts,
        focusedWorkoutNumber = effectiveFocusedWorkoutNumber,
        stack = stack,
    )
}

// ─── Header ─────────────────────────────────────────────────────────────

private suspend fun buildWorkoutHeader(
    date: LocalDate,
    workoutRecords: List<WorkoutRecord>,
    session: WorkoutSession?,
    timeZone: TimeZone,
    now: Instant,
    muscleTitleFormatter: MuscleTitleFormatter,
): WorkoutDetailsContract.Header {
    val title = muscleTitleFormatter.title(rankedMuscles(workoutRecords.flatMap { it.exercises }))
    val dateText = LocaleFormatters.formatShortWeekdayDate(date)
    val subtitle = timeRangeText(session, timeZone, now)?.let { "$dateText · $it" } ?: dateText
    return WorkoutDetailsContract.Header(title = title, subtitle = subtitle)
}

private suspend fun buildDayHeader(
    date: LocalDate,
    matchedSessions: List<WorkoutSession>,
    workoutCount: Int,
    now: Instant,
    strings: WorkoutDetailsStrings,
): WorkoutDetailsContract.Header {
    val title = LocaleFormatters.formatFullDate(date)
    val workoutsText = strings.workoutCount(workoutCount)
    val subtitle = if (matchedSessions.isEmpty()) {
        workoutsText
    } else {
        val totalSeconds = matchedSessions.sumOf { it.durationSec(now) }
        "$workoutsText · ${formatDuration(totalSeconds)}"
    }
    return WorkoutDetailsContract.Header(title = title, subtitle = subtitle)
}

// ─── Hero ───────────────────────────────────────────────────────────────

private suspend fun buildWorkoutHero(
    workoutRecords: List<WorkoutRecord>,
    measurementSystem: MeasurementSystem,
    strings: WorkoutDetailsStrings,
): WorkoutDetailsContract.Hero = buildHero(workoutRecords, strings.totalVolumeLabel(), measurementSystem)

private suspend fun buildDayHero(
    records: List<WorkoutRecord>,
    measurementSystem: MeasurementSystem,
    strings: WorkoutDetailsStrings,
): WorkoutDetailsContract.Hero {
    val dayExercises = records.flatMap { it.exercises }
    val baseCaption = "${strings.dayVolumeLabel()} · " +
        "${strings.exerciseCount(performedExerciseCount(dayExercises))} · " +
        strings.setCount(loggedSetCount(dayExercises))
    return buildHero(records, baseCaption, measurementSystem)
}

/**
 * Shared value/caption/cardioText rules for both hero scopes (§6.1 "Mixed
 * hero"): [scopeRecords] is either the whole day (WD3) or the one focused
 * workout (WD1/WD2) — the rule is identical either way, only the caption text
 * (and which records it sums over) differs, which the two callers supply.
 */
private fun buildHero(
    scopeRecords: List<WorkoutRecord>,
    baseCaption: String,
    measurementSystem: MeasurementSystem,
): WorkoutDetailsContract.Hero {
    val tonnage = TonnageCalculator.forRecords(scopeRecords)
    val cardioMinutes = scopeRecords.sumOf { TonnageCalculator.cardioDurationSeconds(it) } / SECONDS_PER_MINUTE
    val cardioDistanceTotal = cardioDistance(scopeRecords)
    val hasCardio = cardioMinutes > 0
    val hasTonnage = tonnage > 0.0
    val cardioOnly = hasCardio && !hasTonnage

    if (cardioOnly) {
        val caption = if (cardioDistanceTotal > 0.0) {
            "$baseCaption · ${WorkoutValueFormatter.distance(cardioDistanceTotal, measurementSystem)}"
        } else {
            baseCaption
        }
        return WorkoutDetailsContract.Hero(
            valueText = WorkoutValueFormatter.duration(cardioMinutes),
            unitText = null,
            caption = caption,
            cardioText = null,
        )
    }

    val cardioText = if (hasCardio && hasTonnage) {
        val durationText = WorkoutValueFormatter.duration(cardioMinutes)
        if (cardioDistanceTotal > 0.0) {
            "$durationText · ${WorkoutValueFormatter.distance(cardioDistanceTotal, measurementSystem)}"
        } else {
            durationText
        }
    } else {
        null
    }
    return WorkoutDetailsContract.Hero(
        valueText = WorkoutValueFormatter.groupedTonnageNumber(tonnage),
        unitText = WorkoutValueFormatter.unit(ResultType.WEIGHT_REPS, measurementSystem),
        caption = baseCaption,
        cardioText = cardioText,
    )
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

/** WD3 stack row volume (§6.1): cardio-only shows duration, everything else — including
 *  mixed — shows tonnage (the day hero already carries the mixed scope's cardio aggregate). */
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
    measurementSystem: MeasurementSystem,
    now: Instant,
): WorkoutDetailsContract.WorkoutUi {
    val workoutExercises = workoutRecords.flatMap { it.exercises }
    return WorkoutDetailsContract.WorkoutUi(
        workoutNumber = workoutNumber,
        durationText = session?.let { formatDuration(it.durationSec(now)) },
        exerciseCount = performedExerciseCount(workoutExercises),
        setCount = loggedSetCount(workoutExercises),
        newBest = best?.let { newBestUi(it, measurementSystem) },
        note = session?.let { WorkoutDetailsContract.NoteUi(sessionUuid = it.id, text = it.comment) },
        workload = workloadRows(workoutRecords, measurementSystem),
        exerciseGroups = workoutRecords.map { record ->
            WorkoutDetailsContract.ExerciseGroup(
                recordId = record.id,
                members = record.exercises.map { we -> exerciseRow(we, measurementSystem) },
            )
        },
        canShare = session != null,
    )
}

/** "Machine Bench Press · 100 kg × 10" — value(weightKg) with an optional [× reps] (§6.1). */
private fun newBestUi(best: SessionBest, measurementSystem: MeasurementSystem): WorkoutDetailsContract.NewBestUi {
    val valueText = WorkoutValueFormatter.value(best.weightKg, ResultType.WEIGHT_REPS, measurementSystem)
    val repsText = best.reps?.let { WorkoutValueFormatter.reps(it, ResultType.WEIGHT_REPS) }
    val pair = listOfNotNull(valueText, repsText).joinToString(" ")
    return WorkoutDetailsContract.NewBestUi(text = "${best.exerciseName} · $pair")
}

/**
 * WORKLOAD kg per bucket (§6.1): [WorkloadCalculator] decides the buckets
 * (order + percentage, sets-based, OTHER collapses everything under its
 * threshold); this only attaches a kg amount per returned bucket —
 * [TonnageCalculator.forExercise] summed over that category's members, with
 * OTHER taking whatever tonnage isn't accounted for by an explicit
 * non-OTHER bucket (the collapsed remainder).
 */
private fun workloadRows(
    workoutRecords: List<WorkoutRecord>,
    measurementSystem: MeasurementSystem,
): List<WorkoutDetailsContract.WorkloadRow> {
    val entries = WorkloadCalculator.calculate(workoutRecords, showOther = true)
    if (entries.isEmpty()) return emptyList()

    val exercisesByCategory: Map<CategoryType, List<WorkoutExercise>> =
        workoutRecords.flatMap { it.exercises }.groupBy { it.exercise.primaryCategory.type }
    val directTonnageByCategory: Map<CategoryType, Double> = entries
        .filter { it.category != CategoryType.OTHER }
        .associate { entry ->
            entry.category to exercisesByCategory[entry.category].orEmpty().sumOf { we -> TonnageCalculator.forExercise(we) }
        }
    val totalTonnage = TonnageCalculator.forRecords(workoutRecords)

    return entries.map { entry ->
        val tonnage = if (entry.category != CategoryType.OTHER) {
            directTonnageByCategory.getValue(entry.category)
        } else {
            totalTonnage - directTonnageByCategory.values.sum()
        }
        WorkoutDetailsContract.WorkloadRow(
            category = entry.category,
            percentage = entry.percentage,
            tonnageText = if (tonnage > 0.0) WorkoutValueFormatter.groupedTonnage(tonnage, measurementSystem) else null,
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

/** Per-exercise total (§6.1): tonnage for WEIGHT_REPS, else logged distance (falling
 *  back to logged duration when nothing was covered) — null when nothing was logged. */
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
 * Delta pill vs [WorkoutExercise.lastOccurrence] (§6.1, Assumption 2): tonnage
 * delta for WEIGHT_REPS; a distance delta for cardio, but ONLY when both sides
 * logged a distance (a duration-only cardio exercise carries no pill). No
 * prior occurrence at all -> no pill, for either kind.
 */
private fun exerciseDelta(we: WorkoutExercise, measurementSystem: MeasurementSystem): WorkoutDetailsContract.DeltaUi? {
    val prior = we.lastOccurrence ?: return null
    return when (we.exercise.resultType) {
        ResultType.WEIGHT_REPS -> {
            val delta = TonnageCalculator.forExercise(we) - TonnageCalculator.forSets(prior.sets)
            val positive = delta >= 0
            WorkoutDetailsContract.DeltaUi(
                positive = positive,
                text = "${deltaSign(positive)}${WorkoutValueFormatter.groupedTonnage(abs(delta), measurementSystem)}",
            )
        }
        ResultType.DISTANCE_DURATION -> {
            val current = we.sets.filter { it.isLogged }.sumOf { it.distance ?: 0.0 }
            val previous = prior.sets.sumOf { it.distance ?: 0.0 }
            if (current <= 0.0 || previous <= 0.0) return null
            val delta = current - previous
            val positive = delta >= 0
            WorkoutDetailsContract.DeltaUi(
                positive = positive,
                text = "${deltaSign(positive)}${WorkoutValueFormatter.distance(abs(delta), measurementSystem)}",
            )
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
            repsText = WorkoutValueFormatter.reps(set.displayReps, we.exercise.resultType),
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
 * Exercises actually PERFORMED (matches `SessionSummary.exerciseCount`,
 * §6.1): catalog occurrences merged by [kz.maestrosultan.fitjournal.domain.exercise.Exercise.uuid]
 * first, then counted only where the merged group logged at least one set —
 * so the same catalog exercise split across two records in one workout still
 * counts once.
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
