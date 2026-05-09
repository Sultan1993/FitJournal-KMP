package kz.maestrosultan.fitjournal.data.diary

import kotlinx.coroutines.flow.Flow
import kz.maestrosultan.fitjournal.domain.diary.Diary

interface DiaryRepository {

    suspend fun getDiaries(userId: String): List<Diary>
    fun getDiariesFlow(userId: String): Flow<List<Diary>>

    suspend fun getDiaryById(uuid: String): Diary?
    fun getDiaryByIdFlow(uuid: String): Flow<Diary?>

    suspend fun getPersonalDiary(userId: String): Diary?
    fun getPersonalDiaryFlow(userId: String): Flow<Diary?>

    suspend fun createDiary(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int? = null,
    )

    suspend fun updateDiary(
        uuid: String,
        name: String,
        comments: String?,
        workoutGoal: Int? = null,
    )

    suspend fun deleteDiary(uuid: String)

    suspend fun deleteUserDiaries(userId: String)
}
