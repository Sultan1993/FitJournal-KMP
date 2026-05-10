package kz.maestrosultan.fitjournal.domain.exercise

import kotlinx.coroutines.flow.Flow
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.domain.workout.ResultType

interface ExerciseRepository {

    suspend fun getExercises(userId: String): List<Exercise>
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
