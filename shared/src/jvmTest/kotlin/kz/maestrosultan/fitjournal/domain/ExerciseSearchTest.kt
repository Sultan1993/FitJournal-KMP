package kz.maestrosultan.fitjournal.domain

import kz.maestrosultan.fitjournal.domain.exercise.Category
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.exercise.ExerciseSearch
import kz.maestrosultan.fitjournal.domain.workout.ResultType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExerciseSearchTest {

    private val category = Category("c", "c", "Chest", CategoryType.CHEST, null)

    private fun exercise(name: String) = Exercise(
        uuid = name,
        remoteId = null,
        name = name,
        details = null,
        primaryCategory = category,
        secondaryCategories = emptyList(),
        image1 = null,
        image2 = null,
        resultType = ResultType.WEIGHT_REPS,
        isPersonal = false,
    )

    private fun rank(query: String, vararg names: String): List<String> =
        ExerciseSearch.rank(names.map(::exercise), query).map { it.name }

    @Test
    fun blankQueryReturnsInputUnchanged() {
        val names = listOf("Bench Press", "Squat")
        assertEquals(names, ExerciseSearch.rank(names.map(::exercise), "   ").map { it.name })
    }

    @Test
    fun tokenPrefixMatchesAcrossWordsOrderIndependent() {
        // The headline "smart" behaviour: non-contiguous, out-of-order tokens.
        val result = rank("bench dumb", "Dumbbell Bench Press", "Bench Press", "Squat")
        assertEquals(listOf("Dumbbell Bench Press"), result)
    }

    @Test
    fun exactAndPrefixOutrankSubstring() {
        val result = rank("press", "Compression Wrap", "Bench Press", "Press")
        // exact "Press" first, then word-prefix "Bench Press", substring "Compression Wrap" last.
        assertEquals(listOf("Press", "Bench Press", "Compression Wrap"), result)
    }

    @Test
    fun shorterNameWinsTieForSameTier() {
        val result = rank("squat", "Bulgarian Split Squat", "Squat")
        assertEquals(listOf("Squat", "Bulgarian Split Squat"), result)
    }

    @Test
    fun substringRecallIsPreserved() {
        // "ress" is not a word prefix but must still find "Bench Press" (old behaviour).
        assertTrue(rank("ress", "Bench Press", "Squat").contains("Bench Press"))
    }

    @Test
    fun nonMatchesAreExcluded() {
        assertEquals(emptyList(), rank("deadlift", "Bench Press", "Squat"))
    }
}
