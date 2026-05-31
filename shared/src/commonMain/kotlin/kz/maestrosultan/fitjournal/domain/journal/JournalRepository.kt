package kz.maestrosultan.fitjournal.domain.journal

import kotlinx.coroutines.flow.Flow
import kz.maestrosultan.fitjournal.domain.journal.Journal

interface JournalRepository {

    suspend fun getJournals(userId: String): List<Journal>
    fun getJournalsFlow(userId: String): Flow<List<Journal>>

    suspend fun getJournalById(uuid: String): Journal?

    suspend fun getPersonalJournal(userId: String): Journal?

    /**
     * Returns the user's personal journal, creating it atomically if none
     * exists. Safe under concurrent first-boot calls — guarantees a single
     * live personal journal per user.
     */
    suspend fun getOrCreatePersonalJournal(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        workoutGoal: Int? = null,
    ): Journal

    suspend fun createJournal(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int? = null,
    )

    suspend fun updateJournal(
        uuid: String,
        name: String,
        comments: String?,
        workoutGoal: Int? = null,
    )

    suspend fun deleteJournal(uuid: String)

    suspend fun deleteUserJournals(userId: String)
}
