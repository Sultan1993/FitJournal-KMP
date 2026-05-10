package kz.maestrosultan.fitjournal.data.exercise.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.data.db.ExercisesQueries
import kz.maestrosultan.fitjournal.data.exercise.entity.DBExerciseObject
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.time.parseStoredInstant
import kz.maestrosultan.fitjournal.data.time.toStoredString

class ExercisesDBDataSource(
    private val dao: ExercisesQueries,
    private val mapper: ExerciseDBMapper
) {

    suspend fun getAllExercises(): List<DBExerciseObject> = withContext(Dispatchers.IO) {
        dao
            .getAllExercises(
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .executeAsList()
    }

    /**
     * Returns global catalog rows + this user's custom rows. Use this in
     * UI exercise pickers — it correctly filters out custom rows belonging
     * to a previous account on the same device (e.g. after logout/login
     * with a different Firebase user).
     */
    fun getAllExercisesForUserFlow(userId: String): Flow<List<DBExerciseObject>> {
        return dao
            .getAllExercisesForUser(
                userId = userId,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .asFlow()
            .mapToList(Dispatchers.IO)
            .flowOn(Dispatchers.IO)
    }

    suspend fun getAllExercisesForUser(userId: String): List<DBExerciseObject> = withContext(Dispatchers.IO) {
        dao
            .getAllExercisesForUser(
                userId = userId,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .executeAsList()
    }

    fun getExercisesByCategoryForUserFlow(categoryUuid: String, userId: String): Flow<List<DBExerciseObject>> {
        return dao
            .getExercisesByCategoryForUser(
                primaryCategoryUuid = categoryUuid,
                userId = userId,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .asFlow()
            .mapToList(Dispatchers.IO)
            .flowOn(Dispatchers.IO)
    }

    suspend fun getExerciseByUuid(uuid: String): DBExerciseObject = withContext(Dispatchers.IO) {
        dao
            .getExerciseByUuid(
                uuid = uuid,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .executeAsOne()
    }

    /**
     * Nullable + tombstone-filtered variant. Returns null if the row
     * doesn't exist OR has been soft-deleted (deletedAt IS NOT NULL).
     * Repository reads use this so workouts referencing a deleted custom
     * exercise drop the reference cleanly. Sync code paths that need to
     * see tombstones should use [getExerciseByRemoteId] (no live filter).
     */
    suspend fun getExerciseByUuidOrNull(uuid: String): DBExerciseObject? = withContext(Dispatchers.IO) {
        dao
            .getExerciseByUuidIfLive(
                uuid = uuid,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .executeAsOneOrNull()
    }

    /**
     * Updates the user-facing fields on an existing exercise. Used by
     * ExerciseRepository's write path so a UI rename actually persists
     * locally. The .sq query bumps `pendingUpload=1` so the SyncWorker
     * pushes the change to AWS.
     */
    suspend fun updateExerciseFields(
        uuid: String,
        remoteId: String,
        nameEn: String,
        nameRu: String,
        nameUk: String?,
        details: String?,
        primaryCategoryUuid: String,
        secondaryCategoryUuids: List<String>?,
        resultType: Int
    ) = withContext(Dispatchers.IO) {
        dao.updateExercise(
            remoteId = remoteId,
            nameEn = nameEn,
            nameRu = nameRu,
            nameUk = nameUk,
            details = details,
            primaryCategoryUuid = primaryCategoryUuid,
            secondaryCategoryUuids = secondaryCategoryUuids?.joinToString(";"),
            resultType = resultType.toLong(),
            uuid = uuid
        )
    }

    fun getExerciseByUuidFlow(uuid: String): Flow<DBExerciseObject> {
        return dao
            .getExerciseByUuidIfLive(
                uuid = uuid,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .flowOn(Dispatchers.IO)
    }

    suspend fun getExerciseByRemoteId(remoteId: String): DBExerciseObject = withContext(Dispatchers.IO) {
        dao
            .getExerciseByRemoteId(
                remoteId = remoteId,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .executeAsOne()
    }

    /**
     * Nullable + tombstone-filtered remote-id lookup. Mirrors
     * [getExerciseByUuidOrNull] semantics: returns null if the row
     * doesn't exist OR has been soft-deleted. Repository reads use this
     * so a deleted exercise can't be resurrected via remoteId on pull;
     * sync code paths that need to see tombstones must call
     * [getExerciseByRemoteId] directly (no filter).
     */
    suspend fun getExerciseByRemoteIdOrNull(remoteId: String): DBExerciseObject? = withContext(Dispatchers.IO) {
        dao
            .getExerciseByRemoteIdIfLive(
                remoteId = remoteId,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                }
            )
            .executeAsOneOrNull()
    }

    suspend fun createExercise(
        uuid: String,
        remoteId: String,
        nameEn: String,
        nameRu: String,
        nameUk: String,
        details: String?,
        image1: String?,
        image2: String?,
        categoryUuid: String,
        secondaryCategoryUuids: List<String>?,
        resultType: Int,
        isGlobal: Boolean,
        userId: String? = null,
        pendingUpload: Boolean = false
    ): DBExerciseObject = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            dao.createExercise(
                uuid = uuid,
                remoteId = remoteId,
                nameEn = nameEn,
                nameRu = nameRu,
                nameUk = nameUk,
                details = details,
                image1 = image1,
                image2 = image2,
                primaryCategoryUuid = categoryUuid,
                secondaryCategoryUuids = secondaryCategoryUuids?.joinToString(";"),
                resultType = resultType.toLong(),
                global = isGlobal,
                userId = userId,
                pendingUpload = pendingUpload
            )
            dao.getExerciseByUuid(
                uuid = uuid,
                mapper = { u, rId, nEn, nRu, nUk, det, i1, i2,
                           rt, pcu, scu, g,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = u,
                        remoteId = rId,
                        nameEn = nEn,
                        nameRu = nRu,
                        nameUk = nUk,
                        details = det,
                        image1 = i1,
                        image2 = i2,
                        resultType = rt.toInt(),
                        primaryCategoryUuid = pcu,
                        secondaryCategoryUuids = scu,
                        isGlobal = g
                    )
                }
            ).executeAsOne()
        }
    }

    /**
     * Insert if uuid not already present. Returns true on insert, false on
     * skip. Used by `DefaultExercisesMigrator` so re-running after a
     * partial crash doesn't duplicate AND doesn't overwrite a user's
     * already-edited custom (which would have `pendingUpload=1`).
     *
     * Defaults `pendingUpload = false`: globals are already in AWS via the
     * manual seed; customs imported from Parse haven't been edited yet so
     * SyncWorker doesn't need to re-push them. The repo write path bumps
     * `pendingUpload=1` on actual edits.
     */
    suspend fun createExerciseIfMissing(
        uuid: String,
        remoteId: String,
        nameEn: String,
        nameRu: String,
        nameUk: String,
        details: String?,
        image1: String?,
        image2: String?,
        categoryUuid: String,
        secondaryCategoryUuids: List<String>?,
        resultType: Int,
        isGlobal: Boolean,
        userId: String? = null,
        pendingUpload: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            if (dao.getExerciseByUuid(
                    uuid = uuid,
                    mapper = { u, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> u }
                ).executeAsOneOrNull() != null
            ) {
                return@transactionWithResult false
            }
            dao.createExercise(
                uuid = uuid,
                remoteId = remoteId,
                nameEn = nameEn,
                nameRu = nameRu,
                nameUk = nameUk,
                details = details,
                image1 = image1,
                image2 = image2,
                primaryCategoryUuid = categoryUuid,
                secondaryCategoryUuids = secondaryCategoryUuids?.joinToString(";"),
                resultType = resultType.toLong(),
                global = isGlobal,
                userId = userId,
                pendingUpload = pendingUpload,
            )
            true
        }
    }

    suspend fun updateExercise(
        uuid: String,
        remoteId: String,
        nameEn: String,
        nameRu: String,
        nameUk: String,
        details: String?,
        categoryUuid: String,
        secondaryCategoryUuids: List<String>?,
        resultType: Int
    ): DBExerciseObject = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            dao.updateExercise(
                uuid = uuid,
                remoteId = remoteId,
                nameEn = nameEn,
                nameRu = nameRu,
                nameUk = nameUk,
                details = details,
                primaryCategoryUuid = categoryUuid,
                secondaryCategoryUuids = secondaryCategoryUuids?.joinToString(";"),
                resultType = resultType.toLong()
            )
            dao.getExerciseByUuid(
                uuid = uuid,
                mapper = { u, rId, nEn, nRu, nUk, det, i1, i2,
                           rt, pcu, scu, g,
                           _, _, _, _ ->
                    mapper.map(
                        uuid = u,
                        remoteId = rId,
                        nameEn = nEn,
                        nameRu = nRu,
                        nameUk = nUk,
                        details = det,
                        image1 = i1,
                        image2 = i2,
                        resultType = rt.toInt(),
                        primaryCategoryUuid = pcu,
                        secondaryCategoryUuids = scu,
                        isGlobal = g
                    )
                }
            ).executeAsOne()
        }
    }

    /**
     * Name-only update for the UI rename flow. Bumps pendingUpload so the
     * SyncWorker will push it to AWS. All three locale columns are written
     * because custom exercises don't have per-locale source data — the
     * user types one name and we mirror it across locales (legacy parity).
     */
    suspend fun renameExercise(uuid: String, nameEn: String, nameRu: String, nameUk: String?) =
        withContext(Dispatchers.IO) {
            dao.renameExercise(nameEn = nameEn, nameRu = nameRu, nameUk = nameUk, uuid = uuid)
        }

    suspend fun deleteExercise(uuid: String) = withContext(Dispatchers.IO) {
        dao.deleteExercise(uuid)
    }

    /**
     * Soft-delete: tombstone keyed by uuid; bumps `pendingUpload=1` so the
     * SyncWorker propagates the deletion to AWS. Use this from UI flows;
     * `deleteExercise` (hard delete) is reserved for sync code paths
     * confirming an already-pushed deletion.
     */
    suspend fun softDeleteExercise(
        uuid: String,
        deletedAt: Instant,
        updatedDate: Instant = Clock.System.now(),
    ) = withContext(Dispatchers.IO) {
        dao.softDeleteExercise(
            deletedAt = deletedAt.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
    }

    /**
     * Hot-read-path: every live exercise the user can see (global catalog
     * + this user's customs) with primary + secondary categories already
     * resolved, in 2 SQL calls — categories pre-loaded into one map, then
     * exercises read once and joined in code. User-scoped to prevent
     * customs from a previous logged-in account from leaking after an
     * account switch.
     *
     * The default per-row mapper in [ExerciseDBMapper] re-resolves
     * categories with one SELECT per row (≈600 SQL calls for a 200-row
     * catalog) and freezes hot paths like the workout-list screen — use
     * this batch instead from any read that needs full domain Exercises.
     *
     * Tolerant on missing primary category: substitutes an "Unknown"
     * placeholder rather than throwing, so dirty data (a primary category
     * uuid that doesn't resolve) doesn't crash the whole read.
     */
    suspend fun getAllExercisesWithCategoriesBatch(userId: String): List<DBExerciseObject> =
        withContext(Dispatchers.IO) {
            val categoryByUuid: Map<String, kz.maestrosultan.fitjournal.data.exercise.entity.DBCategoryObject> =
                mapper.allCategoriesByUuid()
            dao.getAllExercisesForUser(
                userId = userId,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    val primary = categoryByUuid[primaryCategoryUuid]
                        ?: kz.maestrosultan.fitjournal.data.exercise.entity.DBCategoryObject(
                            uuid = primaryCategoryUuid,
                            remoteId = primaryCategoryUuid,
                            nameEn = "Unknown",
                            nameRu = "Unknown",
                            nameUk = "Unknown",
                            type = 0,
                            details = null
                        )
                    val secondary: List<kz.maestrosultan.fitjournal.data.exercise.entity.DBCategoryObject>? =
                        secondaryCategoryUuids
                            ?.takeIf { it.isNotEmpty() }
                            ?.split(";")
                            ?.mapNotNull { categoryByUuid[it] }
                    DBExerciseObject(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        image1 = image1,
                        image2 = image2,
                        details = details,
                        resultType = resultType.toInt(),
                        primaryCategory = primary,
                        secondaryCategories = secondary,
                        isGlobal = global
                    )
                }
            ).executeAsList()
        }

    /**
     * Lean lookup table for the workouts migrator: returns a map of
     * `remoteId → uuid` (Parse objectId → local SQLite uuid) for every
     * live exercise (`deletedAt IS NULL`).
     *
     * Built without going through [ExerciseDBMapper] — that mapper does
     * one `getCategoryByUuid` SELECT per primary category and another
     * batch transaction per secondary category list, which on real iOS
     * hardware via NativeSqliteDriver collapses to seconds-of-blocking
     * for a 200-exercise catalog. The migrator only consumes
     * `localExercise.uuid` to stamp `DBWorkoutExerciseObject.exerciseUuid`,
     * so the categories are wasted work.
     *
     * One SQL query, no mapper traversal of categories.
     */
    suspend fun getRemoteIdToUuidMap(): Map<String, String> = withContext(Dispatchers.IO) {
        dao
            .getAllExercises(
                mapper = { uuid, remoteId, _, _, _, _, _, _, _, _, _, _, _, _, _, _ ->
                    uuid to remoteId
                }
            )
            .executeAsList()
            .associate { (uuid, remoteId) -> remoteId to uuid }
    }

    /**
     * Pending-upload drain for the SyncWorker. Returns every row with
     * `pendingUpload=1` regardless of `deletedAt` (tombstoned rows must
     * also propagate to AWS as soft-deletes).
     */
    suspend fun getPendingUploads(): List<DBExerciseObject> = withContext(Dispatchers.IO) {
        dao
            .getPendingUploads(
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           userId, _, deletedAt, _ ->
                    val mapped = mapper.map(
                        uuid = uuid,
                        remoteId = remoteId,
                        nameEn = nameEn,
                        nameRu = nameRu,
                        nameUk = nameUk,
                        details = details,
                        image1 = image1,
                        image2 = image2,
                        resultType = resultType.toInt(),
                        primaryCategoryUuid = primaryCategoryUuid,
                        secondaryCategoryUuids = secondaryCategoryUuids,
                        isGlobal = global
                    )
                    mapped.copy(
                        userId = userId,
                        deletedAt = deletedAt?.let(::parseStoredInstant),
                        pendingUpload = true,
                    )
                }
            )
            .executeAsList()
    }

    /**
     * Sync-success acknowledgement: clears `pendingUpload=1`, sets
     * `remoteId` (which equals `uuid` in our id-as-AWS-id model). Called
     * by the SyncOrchestrator after a successful AWS push.
     */
    suspend fun markUploaded(uuid: String, remoteId: String) = withContext(Dispatchers.IO) {
        dao.updateExerciseRemoteId(remoteId = remoteId, uuid = uuid)
    }

    /**
     * Apply a row pulled from AWS, clearing pendingUpload. Caller must
     * already have decided not to skip (the local-wins guard lives in the
     * SyncOrchestrator pull path).
     */
    suspend fun upsertFromRemote(
        uuid: String,
        nameEn: String,
        nameRu: String,
        nameUk: String?,
        details: String?,
        image1: String?,
        image2: String?,
        resultType: Int,
        primaryCategoryUuid: String,
        secondaryCategoryUuids: List<String>?,
        isGlobal: Boolean,
        userId: String?,
        deletedAt: Instant?,
        remoteId: String = uuid,
    ) = withContext(Dispatchers.IO) {
        dao.upsertExerciseFromRemote(
            uuid = uuid,
            remoteId = remoteId,
            nameEn = nameEn,
            nameRu = nameRu,
            nameUk = nameUk,
            details = details,
            image1 = image1,
            image2 = image2,
            resultType = resultType.toLong(),
            primaryCategoryUuid = primaryCategoryUuid,
            secondaryCategoryUuids = secondaryCategoryUuids?.joinToString(";"),
            global = isGlobal,
            userId = userId,
            deletedAt = deletedAt?.toStoredString(),
        )
    }

    /**
     * Returns this user's custom exercises (global=0, live). Used by the
     * delete-account flow to enumerate rows for purging.
     */
    suspend fun getUserCustomExercises(userId: String): List<DBExerciseObject> =
        withContext(Dispatchers.IO) {
            dao
                .getUserCustomExercises(
                    userId = userId,
                    mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                               resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                               _, _, _, _ ->
                        mapper.map(
                            uuid = uuid,
                            remoteId = remoteId,
                            nameEn = nameEn,
                            nameRu = nameRu,
                            nameUk = nameUk,
                            details = details,
                            image1 = image1,
                            image2 = image2,
                            resultType = resultType.toInt(),
                            primaryCategoryUuid = primaryCategoryUuid,
                            secondaryCategoryUuids = secondaryCategoryUuids,
                            isGlobal = global
                        )
                    }
                )
                .executeAsList()
        }

    suspend fun deleteAllExercises() = withContext(Dispatchers.IO) {
        dao.deleteAllExercises()
    }
}
