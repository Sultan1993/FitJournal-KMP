package kz.maestrosultan.fitjournal.domain.exercise

import kotlinx.coroutines.flow.Flow
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.ResultType

interface ExerciseRepository {

    /**
     * Every live exercise visible to [userId] (globals + this user's
     * customs), sorted personal-first then alphabetically by display
     * name. Sort happens in the repo so callers don't re-sort.
     */
    suspend fun getExercises(userId: String): List<Exercise>

    /**
     * Reactive variant of [getExercises]. SQLDelight emits a fresh
     * snapshot on any `exercises`-table mutation; UI subscribes and
     * picks up create/delete writes without a manual refresh.
     */
    fun getExercisesFlow(userId: String): Flow<List<Exercise>>

    suspend fun getExerciseById(uuid: String): Exercise?

    fun getExerciseByIdFlow(uuid: String): Flow<Exercise?>

    suspend fun getExercisesByCategory(userId: String, categoryUuid: String): List<Exercise>

    fun getExercisesByCategoryFlow(userId: String, categoryUuid: String): Flow<List<Exercise>>

    suspend fun createExercise(
        uuid: String,
        userId: String,
        name: String,
        categoryUuid: String,
        resultType: ResultType,
    )

    suspend fun updateExerciseName(uuid: String, name: String)

    suspend fun deleteExercise(uuid: String)

    suspend fun deleteUserExercises(userId: String)
}
