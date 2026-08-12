package kz.maestrosultan.fitjournal.domain.exercise

/**
 * Pure, in-memory ranked matcher for exercise search. Both apps' search use
 * cases run a name query through [rank] so results are ordered the same way on
 * iOS and Android.
 *
 * The catalog is small (hundreds of rows, already loaded in memory), so this is
 * a plain scan — no SQLite FTS. The value here is ranking quality, not speed:
 *
 *  - exact name match ranks first;
 *  - whole-name prefix next ("bench" -> "Bench Press");
 *  - **token-prefix** matching makes search feel "smart": every word in the
 *    query must prefix some word in the name, order-independent, so
 *    "bench dumb" finds "Dumbbell Bench Press";
 *  - contiguous substring anywhere is kept as a low-ranked fallback so nothing
 *    that matched before disappears (e.g. "ress" still finds "Bench Press").
 *
 * Ties break by shorter name (more specific) then alphabetically, so "Squat"
 * outranks "Bulgarian Split Squat" for the query "squat".
 *
 * A blank query returns the input list unchanged (matches the prior behaviour
 * of an empty `contains`).
 */
object ExerciseSearch {

    fun rank(exercises: List<Exercise>, query: String): List<Exercise> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return exercises

        val queryTokens = normalizedQuery.tokenize()

        return exercises
            .mapNotNull { exercise ->
                val score = score(exercise.name.trim().lowercase(), normalizedQuery, queryTokens)
                if (score <= 0) null else ScoredExercise(exercise, score)
            }
            .sortedWith(
                compareByDescending<ScoredExercise> { it.score }
                    .thenBy { it.exercise.name.length }
                    .thenBy { it.exercise.name.lowercase() }
            )
            .map { it.exercise }
    }

    private data class ScoredExercise(val exercise: Exercise, val score: Int)

    private fun score(name: String, query: String, queryTokens: List<String>): Int {
        if (name.isEmpty()) return 0
        if (name == query) return EXACT
        if (name.startsWith(query)) return NAME_PREFIX

        val nameTokens = name.tokenize()

        val allTokensAreWordPrefixes = queryTokens.all { token ->
            nameTokens.any { it.startsWith(token) }
        }
        if (allTokensAreWordPrefixes) {
            val firstWordMatches = nameTokens.firstOrNull()?.startsWith(queryTokens.first()) == true
            return if (firstWordMatches) TOKEN_PREFIX_LEADING else TOKEN_PREFIX
        }

        if (name.contains(query)) return SUBSTRING

        if (queryTokens.all { name.contains(it) }) return SCATTERED

        return 0
    }

    private fun String.tokenize(): List<String> =
        split(' ', '\t', '\n', '-', '/', ',').filter { it.isNotEmpty() }

    private const val EXACT = 1000
    private const val NAME_PREFIX = 800
    private const val TOKEN_PREFIX_LEADING = 600
    private const val TOKEN_PREFIX = 500
    private const val SUBSTRING = 200
    private const val SCATTERED = 100
}
