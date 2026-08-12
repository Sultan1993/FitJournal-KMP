package kz.maestrosultan.fitjournal.data.journal.repository

import kz.maestrosultan.fitjournal.domain.journal.JournalRepository

import kotlin.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kz.maestrosultan.fitjournal.domain.journal.Journal
import kz.maestrosultan.fitjournal.data.journal.datasource.JournalsDBDataSource
import kz.maestrosultan.fitjournal.data.journal.entity.DBJournalObject

class DefaultJournalRepository(
    private val localDataSource: JournalsDBDataSource,
) : JournalRepository {

    override suspend fun getJournals(userId: String): List<Journal> =
        localDataSource.getJournals(userId).map { it.toDomain() }

    override fun getJournalsFlow(userId: String): Flow<List<Journal>> =
        localDataSource.getJournalsFlow(userId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getJournalById(uuid: String): Journal? =
        localDataSource.getJournalById(uuid)?.toDomain()

    override suspend fun getPersonalJournal(userId: String): Journal? =
        localDataSource.getPersonalJournal(userId)?.toDomain()

    override suspend fun getOrCreatePersonalJournal(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        workoutGoal: Int?,
    ): Journal = localDataSource.getOrCreatePersonalJournal(
        uuid = uuid,
        userId = userId,
        name = name,
        comments = comments,
        workoutGoal = workoutGoal,
    ).toDomain()

    override suspend fun createJournal(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int?,
    ) {
        val now = Clock.System.now()
        localDataSource.createJournal(
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

    override suspend fun updateJournal(
        uuid: String,
        name: String,
        comments: String?,
        workoutGoal: Int?,
    ) {
        localDataSource.updateJournal(
            uuid = uuid,
            name = name,
            comments = comments,
            workoutGoal = workoutGoal,
            updatedDate = Clock.System.now(),
        )
    }

    override suspend fun deleteJournal(uuid: String) {
        // Live-only read: a missing/tombstoned journal has nothing to cascade.
        val journal = localDataSource.getJournalById(uuid) ?: return
        val now = Clock.System.now()
        localDataSource.softDeleteJournalCascade(
            uuid = uuid,
            userId = journal.userId,
            deletedAt = now,
            updatedDate = now,
        )
    }

    override suspend fun deleteUserJournals(userId: String) {
        val now = Clock.System.now()
        // Single bulk UPDATE under the partial `idx_diaries_user_live` index —
        // O(rows-touched), no per-row round-trip.
        localDataSource.softDeleteJournalsByUserId(userId, deletedAt = now, updatedDate = now)
    }
}

private fun DBJournalObject.toDomain(): Journal = Journal(
    id = uuid,
    name = name,
    comments = comments,
    isPersonal = isPersonal,
    workoutGoal = workoutGoal,
)
