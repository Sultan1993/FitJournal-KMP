package kz.maestrosultan.fitjournal.ui.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.summary.SessionSummary
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutExerciseItem
import kz.maestrosultan.fitjournal.ui.workout.focus.focusCatalog
import kz.maestrosultan.fitjournal.ui.workout.focus.focusMember
import kz.maestrosultan.fitjournal.ui.workout.focus.focusSet
import kz.maestrosultan.fitjournal.ui.workout.list.WorkoutListPreviewData
import kz.maestrosultan.fitjournal.ui.workout.list.WorkoutListPreviewSurface
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListDayRow
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListEmptyState
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListHero
import kz.maestrosultan.fitjournal.ui.workout.list.components.WorkoutListWeekHeader
import kz.maestrosultan.fitjournal.ui.workout.share.composer.StatKind
import kz.maestrosultan.fitjournal.ui.workout.share.composer.shareCardData

/**
 * One composition per composable that prints a measurement label, asserting the
 * label on screen came out of `composeResources` and not out of a Kotlin
 * literal.
 *
 * **Why imperial, and why "lbs".** These run under the test JVM's own locale,
 * so a metric assertion ("kg") could not tell a resolved `measurement_kg` apart
 * from the literal `"kg"` [WorkoutValueFormatter] used to embed — they are the
 * same string in English. The imperial weight can: every shipped locale
 * resolves `measurement_lbs` to "lbs" (ru "фт"), while the literal the
 * formatter hardcoded was "lb". So "lbs" on screen proves the resource was
 * read, and a regression to the literal fails here rather than only in Russian.
 *
 * The pure builders and mappers are covered the other way round — they inject
 * [russianUnitStrings], where any English label in an assertion is the failure.
 */
@OptIn(ExperimentalTestApi::class)
class WorkoutUnitLabelCompositionTest {

    // ── Workout screen ──────────────────────────────────────────────────

    @Test
    fun workoutExerciseItem_labelsSetsFromResources() = runComposeUiTest {
        val exercise = focusMember(
            id = "we-1",
            catalog = focusCatalog(name = "Bench Press"),
            sets = listOf(focusSet("s1", weight = 155.0, reps = 8)),
        )
        setContent {
            Surface {
                WorkoutExerciseItem(
                    exercise = exercise,
                    measurementSystem = MeasurementSystem.LB_MI,
                    onSetClick = {},
                    onAddSet = {},
                    onMenu = {},
                )
            }
        }

        // The rail draws the unit as its own Text beside the set's number.
        onNodeWithText("lbs").assertExists()
    }

    // ── History list ────────────────────────────────────────────────────

    @Test
    fun workoutListHero_labelsWeeklyVolumeFromResources() = runComposeUiTest {
        setContent {
            WorkoutListPreviewSurface(darkTheme = false) {
                WorkoutListHero(
                    hero = WorkoutListPreviewData.hero,
                    measurementSystem = MeasurementSystem.LB_MI,
                )
            }
        }

        onNodeWithText("lbs").assertExists()
    }

    @Test
    fun workoutListEmptyState_ghostHeroLabelsFromResources() = runComposeUiTest {
        setContent {
            WorkoutListPreviewSurface(darkTheme = false) {
                WorkoutListEmptyState(measurementSystem = MeasurementSystem.LB_MI)
            }
        }

        onNodeWithText("lbs").assertExists()
    }

    @Test
    fun workoutListWeekHeader_labelsSectionTonnageFromResources() = runComposeUiTest {
        setContent {
            WorkoutListPreviewSurface(darkTheme = false) {
                WorkoutListWeekHeader(
                    section = WorkoutListPreviewData.thisWeek,
                    measurementSystem = MeasurementSystem.LB_MI,
                )
            }
        }

        // "3 workouts · 8,600 lbs · 1h 05m" — the label rides inside the summary
        // line, and again inside the delta pill ("+1,000 lbs"). Both are real
        // sites, so match all and assert the first rather than demanding one.
        onAllNodes(hasText("lbs", substring = true)).onFirst().assertExists()
    }

    @Test
    fun workoutListDayRow_labelsDayTonnageFromResources() = runComposeUiTest {
        setContent {
            WorkoutListPreviewSurface(darkTheme = false) {
                WorkoutListDayRow(
                    day = WorkoutListPreviewData.thisWeek.days.first(),
                    measurementSystem = MeasurementSystem.LB_MI,
                    onClick = {},
                )
            }
        }

        onNodeWithText("lbs", substring = true).assertExists()
    }

    // ── Share card ──────────────────────────────────────────────────────

    /**
     * `shareCardData` is a @Composable data builder, so this reads the value it
     * returns rather than the screen: the canvas that draws it is covered by
     * its own layout tests, which take already-built data.
     */
    @Test
    fun shareCardData_labelsTonnageFromResources() = runComposeUiTest {
        var unit: String? = null
        setContent {
            FitJournalTheme {
                unit = shareCardData(
                    summary = shareSummary(),
                    title = "Push day",
                    statsPick = listOf(StatKind.Duration),
                    units = MeasurementSystem.LB_MI,
                ).tonnageUnit
            }
        }

        assertEquals("lbs", unit)
    }

    // ── Fixtures ────────────────────────────────────────────────────────

    @Composable
    private fun Surface(content: @Composable () -> Unit) {
        FitJournalTheme {
            Box(modifier = Modifier.background(FjTheme.colors.background).padding(16.dp)) { content() }
        }
    }

    private fun shareSummary(): SessionSummary = SessionSummary(
        session = WorkoutSession(
            id = "session-1",
            userId = "user-1",
            journalId = "journal-1",
            date = LocalDate(2026, 7, 31),
            workoutNumber = 1,
            startedAt = Instant.fromEpochMilliseconds(0),
            endedAt = Instant.fromEpochMilliseconds(3_600_000),
        ),
        muscles = emptyList(),
        exercises = emptyList(),
        tonnageKg = 1_000.0,
        loggedSets = 17,
        exerciseCount = 4,
        weekOrdinal = 2,
        best = null,
        sessionRecordUuids = emptySet(),
    )
}
