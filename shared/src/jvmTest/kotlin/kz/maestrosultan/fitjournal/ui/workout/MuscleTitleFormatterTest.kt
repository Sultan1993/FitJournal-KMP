package kz.maestrosultan.fitjournal.ui.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.summary.MuscleLoad
import kz.maestrosultan.fitjournal.ui.workout.MuscleTitleFormatter

/**
 * Injects name/fallback lookups instead of the compose-resources defaults so
 * assertions don't depend on the test JVM's locale or resource loading — the
 * join/order/fallback logic is what's under test here.
 */
class MuscleTitleFormatterTest {

    private val formatter = MuscleTitleFormatter(
        categoryName = { it.identifier },
        fallbackTitle = { FALLBACK },
    )

    @Test
    fun topThree_joinedInGivenRankedOrder() = runTest {
        val ranked = listOf(
            MuscleLoad(category = CategoryType.SHOULDERS, loggedSets = 9),
            MuscleLoad(category = CategoryType.CHEST, loggedSets = 5),
            MuscleLoad(category = CategoryType.TRICEPS, loggedSets = 3),
            MuscleLoad(category = CategoryType.ABS, loggedSets = 1),
        )

        assertEquals("shoulders · chest · triceps", formatter.title(ranked))
    }

    @Test
    fun givenOrderIsTrusted_neverReSorted() = runTest {
        // Deliberately NOT ordered by loggedSets: the formatter must trust the
        // caller's ranking as-is.
        val ranked = listOf(
            MuscleLoad(category = CategoryType.CALVES, loggedSets = 1),
            MuscleLoad(category = CategoryType.BACK, loggedSets = 7),
        )

        assertEquals("calves · back", formatter.title(ranked))
    }

    @Test
    fun singleMuscle_noSeparator() = runTest {
        val single = listOf(MuscleLoad(category = CategoryType.GLUTES, loggedSets = 4))

        assertEquals("glutes", formatter.title(single))
    }

    @Test
    fun emptyList_returnsFallbackTitle() = runTest {
        assertEquals(FALLBACK, formatter.title(emptyList()))
    }

    private companion object {
        const val FALLBACK = "Workout"
    }
}
