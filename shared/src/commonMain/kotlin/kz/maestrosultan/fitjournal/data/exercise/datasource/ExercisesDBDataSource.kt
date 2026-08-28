package kz.maestrosultan.fitjournal.data.exercise.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.data.db.ExercisesQueries
import kz.maestrosultan.fitjournal.data.exercise.entity.DBCategoryObject
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
     * UI-facing single-row read; null if missing or soft-deleted (a tombstoned
     * custom drops cleanly instead of surfacing a ghost). Sync paths needing
     * tombstones use [getExerciseByUuidIncludingDeleted].
     *
     * Categories are pre-fetched outside the row mapper — a nested SQL call
     * mid-iteration deadlocks when sqliter has only one connection (DELETE
     * mode); fine under WAL's multiple readers.
     */
    suspend fun getExerciseByUuid(uuid: String): DBExerciseObject? = withContext(Dispatchers.IO) {
        val categoryByUuid = mapper.allCategoriesByUuid()
        dao
            .getExerciseByUuid(
                uuid = uuid,
                mapper = { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
                           resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
                           _, _, _, _ ->
                    val primary = categoryByUuid[primaryCategoryUuid]
                        ?: error("Catalog category not found for exercise '$uuid': '$primaryCategoryUuid'")
                    val secondary = secondaryCategoryUuids
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
                        isGlobal = global,
                    )
                }
            )
            .executeAsOneOrNull()
    }

    /**
     * Sync-only: sees soft-deleted rows so the orchestrator can diff local vs
     * remote `deletedAt` on pull and propagate tombstones on push. Without
     * this a tombstoned-and-pending row looks "missing", the local-wins guard
     * misses, and the remote upsert stomps the tombstone (data loss). UI/repo
     * paths use [getExerciseByUuid].
     *
     * Lean mapper — no category resolution (sync only needs uuid/remoteId/pendingUpload).
     */
    suspend fun getExerciseByUuidIncludingDeleted(uuid: String): DBExerciseObject? = withContext(Dispatchers.IO) {
        dao
            .getExerciseByUuidIncludingDeleted(
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
                        isGlobal = global,
                    )
                }
            )
            .executeAsOneOrNull()
    }

    /**
     * UI/sync canonical remote-id lookup, live only. Used by the sync
     * orchestrator's fallback path when a pulled AWS row doesn't match
     * by uuid (e.g. legacy data where uuid != remoteId).
     */
    suspend fun getExerciseByRemoteId(remoteId: String): DBExerciseObject? = withContext(Dispatchers.IO) {
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
                        isGlobal = global,
                    )
                }
            )
            .executeAsOneOrNull()
    }

    /**
     * Sees tombstones. Used by sync (local-wins guard via remoteId
     * fallback) and the legacy migrator's "do I already have a row for
     * this Parse objectId, even if soft-deleted?" check.
     */
    suspend fun getExerciseByRemoteIdIncludingDeleted(remoteId: String): DBExerciseObject? = withContext(Dispatchers.IO) {
        dao
            .getExerciseByRemoteIdIncludingDeleted(
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
                        isGlobal = global,
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
    ) = withContext(Dispatchers.IO) {
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
    }

    /**
     * Insert if uuid not already present (true=inserted, false=skipped). Lets
     * a migrator re-run after a partial crash without duplicating rows or
     * overwriting an already-edited custom (`pendingUpload=1`).
     *
     * Defaults `pendingUpload=false`: globals are already in AWS via the
     * manual seed, imported customs haven't been edited yet. The repo write
     * path bumps it on actual edits.
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
            // Sees tombstones so a deliberately-deleted row isn't re-imported on re-run.
            if (dao.getExerciseByUuidIncludingDeleted(
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

    /**
     * Name-only update for the UI rename flow. Bumps pendingUpload so the
     * SyncWorker will push it to AWS. All three locale columns are written
     * because custom exercises don't have per-locale source data — the
     * user types one name and we mirror it across locales (legacy parity).
     */
    suspend fun renameExercise(
        uuid: String,
        nameEn: String,
        nameRu: String,
        nameUk: String?,
        updatedDate: Instant = Clock.System.now(),
    ) = withContext(Dispatchers.IO) {
        dao.renameExercise(
            nameEn = nameEn,
            nameRu = nameRu,
            nameUk = nameUk,
            updatedDate = updatedDate.toStoredString(),
            uuid = uuid,
        )
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
     * Account deletion — bulk tombstone so the blocking drain has something to
     * push. See the query comment in Exercises.sq for why this is not a hard purge.
     */
    suspend fun softDeleteCustomExercisesByUserId(
        userId: String,
        deletedAt: Instant = Clock.System.now(),
        updatedDate: Instant = deletedAt,
    ) = withContext(Dispatchers.IO) {
        dao.softDeleteCustomExercisesByUserId(
            deletedAt = deletedAt.toStoredString(),
            updatedDate = updatedDate.toStoredString(),
            userId = userId,
        )
    }

    /**
     * Hot-read-path: every live exercise the user can see (catalog + this
     * user's customs) with categories resolved, in 2 SQL calls — categories
     * loaded into one map, exercises read once and joined in code. User-scoped
     * so customs don't leak across account switches.
     *
     * [ExerciseDBMapper]'s per-row mapper does one SELECT per category
     * (~600 calls for a 200-row catalog) and freezes hot paths like the
     * workout-list screen — use this batch for any full-Exercise read instead.
     */
    suspend fun getAllExercisesWithCategoriesBatch(userId: String): List<DBExerciseObject> =
        withContext(Dispatchers.IO) {
            val categoryByUuid = mapper.allCategoriesByUuid()
            buildExercisesQuery(userId, categoryByUuid).executeAsList()
        }

    /**
     * Reactive variant of [getAllExercisesWithCategoriesBatch]; SQLDelight
     * emits a fresh snapshot on any `exercises` table mutation.
     *
     * Categories are captured once at flow construction — the catalog is
     * admin-managed (seeded via `scripts/seed_aws_global_catalog.py`), so one
     * snapshot suffices for the flow's lifetime. Combine with
     * `CategoriesDBDataSource.getAllCategoriesFlow` if categories ever need
     * to participate in reactive updates.
     */
    fun getAllExercisesForUserFlow(userId: String): Flow<List<DBExerciseObject>> {
        val categoryByUuid = mapper.allCategoriesByUuid()
        return buildExercisesQuery(userId, categoryByUuid)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .flowOn(Dispatchers.IO)
    }

    fun getExerciseByUuidFlow(uuid: String): Flow<DBExerciseObject?> {
        val categoryByUuid = mapper.allCategoriesByUuid()
        return dao.getExerciseByUuid(
            uuid = uuid,
            mapper = exerciseRowMapper(categoryByUuid),
        ).asFlow().mapToOneOrNull(Dispatchers.IO).flowOn(Dispatchers.IO)
    }

    private fun buildExercisesQuery(
        userId: String,
        categoryByUuid: Map<String, DBCategoryObject>,
    ) = dao.getAllExercisesForUser(
        userId = userId,
        mapper = exerciseRowMapper(categoryByUuid),
    )

    private fun exerciseRowMapper(
        categoryByUuid: Map<String, DBCategoryObject>,
    ): (String, String, String, String, String?, String?, String?, String?, Long, String, String?, Boolean, String?, Boolean, String?, String?) -> DBExerciseObject =
        { uuid, remoteId, nameEn, nameRu, nameUk, details, image1, image2,
          resultType, primaryCategoryUuid, secondaryCategoryUuids, global,
          _, _, _, _ ->
            val primary = categoryByUuid[primaryCategoryUuid]
                ?: error("Catalog category not found for exercise '$uuid': '$primaryCategoryUuid'")
            val secondary: List<DBCategoryObject>? = secondaryCategoryUuids
                ?.takeIf { it.isNotEmpty() }
                ?.split(";")
                // mapNotNull, NOT error(): secondaryCategoryUuids is a ';'-joined
                // blob written verbatim from AWS with no foreign key behind it, so
                // an id this device hasn't seeded yet is reachable and normal.
                // Throwing from inside a row mapper aborts the WHOLE query — and
                // this one feeds every record read — so one server row would take
                // down exercises, workouts, history and stats at once (an
                // uncatchable SIGABRT on iOS, on every launch, since the row is
                // already local). Drop the unresolvable id instead.
                ?.mapNotNull { secondaryUuid -> categoryByUuid[secondaryUuid] }
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
                isGlobal = global,
            )
        }

    /**
     * Lean `remoteId → uuid` lookup (Parse objectId → local uuid) for every
     * live exercise, for the workouts migrator.
     *
     * Skips [ExerciseDBMapper] — its per-row category SELECTs collapse to
     * seconds of blocking on real iOS hardware for a 200-exercise catalog,
     * and the migrator only needs `uuid`. One SQL query, no category traversal.
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
    suspend fun getPendingUploads(userId: String): List<DBExerciseObject> = withContext(Dispatchers.IO) {
        dao
            .getPendingUploads(
                userId = userId,
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
    /**
     * Push ack — see [kz.maestrosultan.fitjournal.data.journal.datasource.JournalsDBDataSource.markUploaded].
     * Pass the SNAPSHOT that was pushed; a rename or tombstone landing during the
     * round trip makes this a no-op so the row stays pending.
     */
    suspend fun markUploaded(exercise: DBExerciseObject, remoteId: String) =
        withContext(Dispatchers.IO) {
            dao.updateExerciseRemoteId(
                remoteId = remoteId,
                uuid = exercise.uuid,
                nameEn = exercise.nameEn,
                nameRu = exercise.nameRu,
                nameUk = exercise.nameUk,
                deletedAt = exercise.deletedAt?.toStoredString(),
            )
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
}
