package kz.maestrosultan.fitjournal.data.diary

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kz.maestrosultan.fitjournal.domain.diary.Diary
import kz.maestrosultan.fitjournal.kmp.diaries.datasource.DiariesDBDataSource
import kz.maestrosultan.fitjournal.kmp.diaries.entity.DBDiaryObject

class DefaultDiaryRepository(
    private val localDataSource: DiariesDBDataSource,
) : DiaryRepository {

    override suspend fun getDiaries(userId: String): List<Diary> =
        localDataSource.getDiaries(userId).map { it.toDomain() }

    override fun getDiariesFlow(userId: String): Flow<List<Diary>> =
        localDataSource.getDiariesFlow(userId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getDiaryById(uuid: String): Diary? =
        // Tombstone-filtered: a soft-deleted diary must not render as live
        // in the UI even if its row is still in SQLite (sync needs the row
        // for the local-wins guard, but the user-facing repo doesn't).
        localDataSource.getDiaryByIdIfLive(uuid)?.toDomain()

    override fun getDiaryByIdFlow(uuid: String): Flow<Diary?> =
        localDataSource.getDiaryByIdIfLiveFlow(uuid).map { it?.toDomain() }

    override suspend fun getPersonalDiary(userId: String): Diary? =
        localDataSource.getPersonalDiary(userId)?.toDomain()

    override fun getPersonalDiaryFlow(userId: String): Flow<Diary?> =
        localDataSource.getPersonalDiaryFlow(userId).map { it?.toDomain() }

    override suspend fun createDiary(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int?,
    ) {
        val now = Clock.System.now()
        localDataSource.createDiary(
            uuid = uuid,
            userId = userId,
            name = name,
            comments = comments,
            isPersonal = isPersonal,
            workoutGoal = workoutGoal,
            remoteId = null,
            pendingUpload = true,
            createdDate = now,
            updatedDate = now,
        )
    }

    override suspend fun updateDiary(
        uuid: String,
        name: String,
        comments: String?,
        workoutGoal: Int?,
    ) {
        localDataSource.updateDiary(
            uuid = uuid,
            name = name,
            comments = comments,
            workoutGoal = workoutGoal,
            updatedDate = Clock.System.now(),
        )
    }

    override suspend fun deleteDiary(uuid: String) {
        val now = Clock.System.now()
        localDataSource.softDeleteDiary(uuid = uuid, deletedAt = now, updatedDate = now)
    }

    override suspend fun deleteUserDiaries(userId: String) {
        val now = Clock.System.now()
        localDataSource.getDiaries(userId).forEach { diary ->
            localDataSource.softDeleteDiary(uuid = diary.uuid, deletedAt = now, updatedDate = now)
        }
    }
}

private fun DBDiaryObject.toDomain(): Diary = Diary(
    id = uuid,
    name = name,
    comments = comments,
    isPersonal = isPersonal,
)
