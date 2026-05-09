package kz.maestrosultan.fitjournal.kmp.diaries.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.kmp.DiariesQueries
import kz.maestrosultan.fitjournal.kmp.diaries.entity.DBDiaryObject
import kz.maestrosultan.fitjournal.kmp.diaries.entity.map
import kz.maestrosultan.fitjournal.kmp.time.toStoredString

/**
 * KMP wrapper around the `diaries` SQLDelight table. Sole source of truth for
 * diary state on-device. Writes mark `pendingUpload=1`; the sync worker drains
 * those rows to AWS in the background. Reads filter out tombstones
 * (`deletedAt IS NOT NULL`) so the UI never sees soft-deleted rows.
 */
class DiariesDBDataSource(private val dao: DiariesQueries) {

    suspend fun getDiaries(userId: String): List<DBDiaryObject> = withContext(Dispatchers.IO) {
        dao.getDiaries(userId).executeAsList().map { it.map() }
    }

    fun getDiariesFlow(userId: String): Flow<List<DBDiaryObject>> =
        dao.getDiaries(userId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { rows -> rows.map { it.map() } }
            .flowOn(Dispatchers.IO)

    /**
     * Includes tombstoned rows. Sync paths use this so they can see
     * soft-deleted-but-pending diaries (the local-wins guard checks
     * `pendingUpload` before deciding to skip). UI repos must use
     * [getDiaryByIdIfLive] instead.
     */
    suspend fun getDiaryById(uuid: String): DBDiaryObject? = withContext(Dispatchers.IO) {
        dao.getDiaryById(uuid).executeAsOneOrNull()?.map()
    }

    /**
     * Tombstone-filtered (`deletedAt IS NULL`). UI reads — repo's
     * `getDiaryById` / `getDiaryByIdFlow` route through here so a
     * deleted diary doesn't render as live.
     */
    suspend fun getDiaryByIdIfLive(uuid: String): DBDiaryObject? = withContext(Dispatchers.IO) {
        dao.getDiaryByIdIfLive(uuid).executeAsOneOrNull()?.map()
    }

    fun getDiaryByIdIfLiveFlow(uuid: String): Flow<DBDiaryObject?> =
        dao.getDiaryByIdIfLive(uuid)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.map() }
            .flowOn(Dispatchers.IO)

    fun getDiaryByIdFlow(uuid: String): Flow<DBDiaryObject?> =
        dao.getDiaryById(uuid)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.map() }
            .flowOn(Dispatchers.IO)

    suspend fun getPersonalDiary(userId: String): DBDiaryObject? = withContext(Dispatchers.IO) {
        dao.getPersonalDiary(userId).executeAsOneOrNull()?.map()
    }

    fun getPersonalDiaryFlow(userId: String): Flow<DBDiaryObject?> =
        dao.getPersonalDiary(userId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.map() }
            .flowOn(Dispatchers.IO)

    suspend fun getPendingUploads(): List<DBDiaryObject> = withContext(Dispatchers.IO) {
        dao.getPendingUploads().executeAsList().map { it.map() }
    }

    /**
     * Insert a new diary that originated locally (or from a Parse migration).
     * `pendingUpload=true` so the sync worker pushes it to AWS on next tick.
     */
    suspend fun createDiary(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int? = null,
        remoteId: String? = null,
        pendingUpload: Boolean = true,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
    ) = withContext(Dispatchers.IO) {
        dao.createDiary(
            uuid = uuid,
            remoteId = remoteId,
            userId = userId,
            name = name,
            comments = comments,
            isPersonal = isPersonal,
            workoutGoal = workoutGoal?.toLong(),
            pendingUpload = pendingUpload,
            createdDate = createdDate.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
        )
    }

    /**
     * Insert-or-ignore: used by the Parse → SQLite migrator so a partial
     * earlier run doesn't trip on duplicate uuids. NOTE: a row that exists
     * with `deletedAt IS NOT NULL` (locally tombstoned) is treated as
     * existing — the migrator does NOT resurrect it. Rationale: if the
     * user soft-deleted the diary post-migration, the local SQLite +
     * outbound sync are the source of truth, not the now-stale Parse
     * snapshot. To force re-import, bump the diaries-migration RC version.
     */
    suspend fun createDiaryIfMissing(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int? = null,
        remoteId: String? = null,
        pendingUpload: Boolean = true,
        createdDate: Instant = Clock.System.now(),
        updatedDate: Instant = createdDate,
    ): Boolean = withContext(Dispatchers.IO) {
        // Wrap in a transaction so the existence check and the insert are
        // atomic — otherwise two concurrent migrators on `Dispatchers.IO`
        // could both observe "missing" and double-insert (TOCTOU). Mirrors
        // `createNoteIfMissing` / `createBodyMeasurementIfMissing`.
        dao.transactionWithResult {
            if (dao.getDiaryById(uuid).executeAsOneOrNull() != null) return@transactionWithResult false
            dao.createDiary(
                uuid = uuid,
                remoteId = remoteId,
                userId = userId,
                name = name,
                comments = comments,
                isPersonal = isPersonal,
                workoutGoal = workoutGoal?.toLong(),
                pendingUpload = pendingUpload,
                createdDate = createdDate.toStoredString(),
                updatedDate = updatedDate.toStoredString(),
            )
            true
        }
    }

    suspend fun updateDiary(
        uuid: String,
        name: String,
        comments: String?,
        workoutGoal: Int? = null,
        updatedDate: Instant = Clock.System.now(),
    ) = withContext(Dispatchers.IO) {
        dao.updateDiary(
            name = name,
            comments = comments,
            workoutGoal = workoutGoal?.toLong(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    suspend fun softDeleteDiary(
        uuid: String,
        deletedAt: Instant = Clock.System.now(),
        updatedDate: Instant = deletedAt,
    ) = withContext(Dispatchers.IO) {
        dao.softDeleteDiary(
            deletedAt = deletedAt.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    suspend fun markUploaded(uuid: String, remoteId: String) = withContext(Dispatchers.IO) {
        dao.updateDiaryRemoteId(remoteId = remoteId, uuid = uuid)
    }

    /**
     * Apply a row pulled from AWS, clearing pendingUpload. Caller must
     * already have decided not to skip (i.e. local row's pendingUpload is
     * 0 OR remote `updatedAt` newer than local). The remote-wins / LWW
     * semantics live in the SyncOrchestrator pull path; this method just
     * persists.
     */
    suspend fun upsertFromRemote(
        uuid: String,
        userId: String,
        name: String,
        comments: String?,
        isPersonal: Boolean,
        workoutGoal: Int?,
        deletedAt: Instant?,
        createdDate: Instant,
        updatedDate: Instant,
        remoteId: String? = uuid,
    ) = withContext(Dispatchers.IO) {
        dao.upsertDiaryFromRemote(
            uuid = uuid,
            remoteId = remoteId,
            userId = userId,
            name = name,
            comments = comments,
            isPersonal = isPersonal,
            workoutGoal = workoutGoal?.toLong(),
            deletedAt = deletedAt?.toStoredString(),
            createdDate = createdDate.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
        )
    }

    suspend fun deleteUserDiaries(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteUserDiaries(userId)
    }

    suspend fun deleteAllDiaries() = withContext(Dispatchers.IO) {
        dao.deleteAllDiaries()
    }
}
