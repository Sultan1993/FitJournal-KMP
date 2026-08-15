package kz.maestrosultan.fitjournal.ui.workout.details.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsContract

// SessionNoteEditorSheet has no preview on purpose: it is a ModalBottomSheet, which
// renders as an empty frame in the static preview renderer.

/**
 * Deterministic sample data for the WorkoutDetails `@Preview` functions — no `Clock.System`,
 * no random. Built with real [WorkoutDetailsContract] constructors, never a shadow type.
 * One shoulders/triceps push day, 10 August 2026.
 */
internal object WorkoutDetailsPreviewData {

    val date: LocalDate = LocalDate(2026, 8, 10)

    val header = WorkoutDetailsContract.Header(
        title = "Shoulders · Triceps",
        subtitle = "09:38–10:42 · 5 exercises",
    )

    /** Both stats: volume, then cardio with its distance in the label. */
    val hero = WorkoutDetailsContract.Hero(
        volume = WorkoutDetailsContract.HeroStat(value = "6 638", unit = "kg", label = "Total volume"),
        cardio = WorkoutDetailsContract.HeroStat(value = "30", unit = "min", label = "Cardio · 5 km"),
    )

    /** No cardio: the second stat and its divider are dropped. */
    val volumeOnlyHero = WorkoutDetailsContract.Hero(volume = hero.volume, cardio = null)

    /** Cardio-only day: nothing was lifted, so the cardio stat stands alone. */
    val cardioOnlyHero = WorkoutDetailsContract.Hero(
        volume = null,
        cardio = WorkoutDetailsContract.HeroStat(value = "42", unit = "min", label = "Cardio · 7.2 km"),
    )

    val newBest = WorkoutDetailsContract.NewBestUi(text = "Seated Dumbbell Press · 42.5 kg × 8")

    val note = WorkoutDetailsContract.NoteUi(
        sessionUuid = "session-1",
        text = "Shoulders felt strong today. Went up 2.5 kg on the press and still hit all 8 reps clean.",
    )

    val emptyNote = WorkoutDetailsContract.NoteUi(sessionUuid = "session-1", text = null)

    val workload = listOf(
        WorkoutDetailsContract.WorkloadRow(CategoryType.SHOULDERS, percentage = 42.0, amountText = "2 780 kg"),
        WorkoutDetailsContract.WorkloadRow(CategoryType.TRICEPS, percentage = 31.0, amountText = "2 060 kg"),
        WorkoutDetailsContract.WorkloadRow(CategoryType.CHEST, percentage = 17.0, amountText = "1 798 kg"),
        WorkoutDetailsContract.WorkloadRow(CategoryType.CARDIO, percentage = 10.0, amountText = "30 min"),
    )

    private fun category(type: CategoryType) = Category(
        uuid = "cat-${type.id}",
        remoteId = "cat-${type.id}",
        name = type.name,
        type = type,
        details = null,
    )

    private fun exercise(
        id: String,
        name: String,
        type: CategoryType,
        resultType: ResultType = ResultType.WEIGHT_REPS,
    ) = Exercise(
        uuid = id,
        remoteId = id,
        name = name,
        details = null,
        primaryCategory = category(type),
        secondaryCategories = emptyList(),
        image1 = null,
        image2 = null,
        resultType = resultType,
        isPersonal = false,
    )

    private fun chips(vararg pairs: Pair<String, String>) =
        pairs.map { (value, reps) -> WorkoutDetailsContract.SetChip(valueText = value, repsText = reps) }

    private val press = WorkoutDetailsContract.ExerciseRow(
        workoutExerciseId = "we-1",
        exercise = exercise("ex-1", "Seated Dumbbell Press", CategoryType.SHOULDERS),
        name = "Seated Dumbbell Press",
        volumeText = "2 950 kg",
        delta = WorkoutDetailsContract.DeltaUi(positive = true, text = "+180 kg"),
        sets = chips("40 kg" to "×10", "42.5 kg" to "×8", "42.5 kg" to "×8", "40 kg" to "×9"),
        comment = "Last set was a grind — keep 42.5 kg next week.",
    )

    private val pushdown = WorkoutDetailsContract.ExerciseRow(
        workoutExerciseId = "we-2",
        exercise = exercise("ex-2", "Cable Pushdown", CategoryType.TRICEPS),
        name = "Cable Pushdown",
        volumeText = "1 620 kg",
        delta = WorkoutDetailsContract.DeltaUi(positive = false, text = "−90 kg"),
        sets = chips("30 kg" to "×12", "35 kg" to "×10", "35 kg" to "×9"),
        comment = null,
    )

    private val lateralRaise = WorkoutDetailsContract.ExerciseRow(
        workoutExerciseId = "we-3",
        exercise = exercise("ex-3", "Lateral Raise", CategoryType.SHOULDERS),
        name = "Lateral Raise",
        volumeText = "660 kg",
        delta = null,
        sets = chips("12 kg" to "×15", "12 kg" to "×14", "10 kg" to "×15"),
        comment = null,
    )

    private val overheadExtension = WorkoutDetailsContract.ExerciseRow(
        workoutExerciseId = "we-4",
        exercise = exercise("ex-4", "Overhead Rope Extension", CategoryType.TRICEPS),
        name = "Overhead Rope Extension",
        volumeText = "440 kg",
        delta = WorkoutDetailsContract.DeltaUi(positive = true, text = "+40 kg"),
        sets = chips("25 kg" to "×12", "25 kg" to "×12", "22.5 kg" to "×12"),
        comment = null,
    )

    private val treadmill = WorkoutDetailsContract.ExerciseRow(
        workoutExerciseId = "we-5",
        exercise = exercise("ex-5", "Treadmill", CategoryType.CARDIO, ResultType.DISTANCE_DURATION),
        name = "Treadmill",
        volumeText = "5.1 km",
        delta = WorkoutDetailsContract.DeltaUi(positive = false, text = "−0.4 km"),
        sets = chips("5.1 km" to "30 min"),
        comment = null,
    )

    private val facePull = WorkoutDetailsContract.ExerciseRow(
        workoutExerciseId = "we-6",
        exercise = exercise("ex-6", "Face Pull", CategoryType.SHOULDERS),
        name = "Face Pull",
        volumeText = null,
        delta = null,
        sets = emptyList(),
        comment = null,
    )

    val plainGroups = listOf(
        WorkoutDetailsContract.ExerciseGroup(recordId = "rec-1", members = listOf(press)),
        WorkoutDetailsContract.ExerciseGroup(recordId = "rec-2", members = listOf(pushdown)),
        WorkoutDetailsContract.ExerciseGroup(recordId = "rec-4", members = listOf(treadmill)),
    )

    /** Two members in one record — the violet superset rail plus the layers badge. */
    val supersetGroups = listOf(
        WorkoutDetailsContract.ExerciseGroup(
            recordId = "rec-3",
            members = listOf(lateralRaise, overheadExtension),
        ),
    )

    val skippedGroups = listOf(
        WorkoutDetailsContract.ExerciseGroup(recordId = "rec-5", members = listOf(facePull)),
    )

    val stack = listOf(
        WorkoutDetailsContract.StackRow(
            workoutNumber = 1,
            title = "Chest · Triceps",
            subtitle = "07:15–07:52 · 3 exercises",
            volumeText = "3 402 kg",
        ),
        WorkoutDetailsContract.StackRow(
            workoutNumber = 2,
            title = "Shoulders · Triceps",
            subtitle = "09:38–10:42 · 5 exercises",
            volumeText = "6 638 kg",
        ),
        WorkoutDetailsContract.StackRow(
            workoutNumber = 3,
            title = "Cardio",
            subtitle = "18:20–18:50 · 1 exercise",
            volumeText = "30 min",
        ),
    )

    val workout = WorkoutDetailsContract.WorkoutUi(
        workoutNumber = 1,
        durationText = "1h 04m",
        exerciseCount = 5,
        setCount = 18,
        newBest = newBest,
        note = note,
        workload = workload,
        exerciseGroups = plainGroups + supersetGroups,
        skippedGroups = skippedGroups,
        canShare = true,
    )

    /** No session recorded: no duration tile, no NOTE card, nothing to share. */
    val sessionlessWorkout = workout.copy(
        durationText = null,
        newBest = null,
        note = null,
        canShare = false,
    )

    val loadedViewState = WorkoutDetailsContract.ViewState(
        headerNav = WorkoutDetailsContract.HeaderNav.Back,
        content = WorkoutDetailsContract.Content.Loaded(
            date = date,
            header = header,
            hero = hero,
            workouts = listOf(workout),
            focusedWorkoutNumber = 1,
            stack = emptyList(),
        ),
        noteEditor = null,
        confirmingDelete = false,
    )

    val multiWorkoutViewState = WorkoutDetailsContract.ViewState(
        headerNav = WorkoutDetailsContract.HeaderNav.Close,
        content = WorkoutDetailsContract.Content.Loaded(
            date = date,
            header = header,
            hero = hero,
            workouts = listOf(
                sessionlessWorkout.copy(workoutNumber = 1, exerciseCount = 3, setCount = 9),
                workout.copy(workoutNumber = 2),
                workout.copy(
                    workoutNumber = 3,
                    durationText = "30 min",
                    exerciseCount = 1,
                    setCount = 1,
                    newBest = null,
                    note = emptyNote,
                    workload = listOf(workload.last()),
                    exerciseGroups = listOf(plainGroups.last()),
                    skippedGroups = emptyList(),
                ),
            ),
            focusedWorkoutNumber = 2,
            stack = stack,
        ),
        noteEditor = null,
        confirmingDelete = false,
    )
}

/**
 * Shared preview chrome: real [FitJournalTheme] forced light/dark over a
 * [FjTheme.colors.background] surface, matching how components actually render
 * inside [kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsScreen].
 */
@Composable
internal fun WorkoutDetailsPreviewSurface(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    FitJournalTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxWidth().background(FjTheme.colors.background).padding(20.dp)) {
            content()
        }
    }
}
