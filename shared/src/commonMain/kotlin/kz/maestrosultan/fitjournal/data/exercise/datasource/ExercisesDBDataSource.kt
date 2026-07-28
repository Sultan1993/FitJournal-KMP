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
     * UI-facing single-row read. Returns null if the row doesn't exist
     * OR has been soft-deleted — workouts that reference a tombstoned
     * custom exercise drop the reference cleanly rather than surfacing
     * a ghost. Sync code paths that need to see tombstones use
     * [getExerciseByUuidIncludingDeleted].
     *
     * Categories are pre-fetched OUTSIDE the row mapper. Doing the
     * category lookup inside the mapper triggers a nested SQL call
     * while the outer prepared statement is still mid-iteration — fine
     * when sqliter has multiple reader connections (WAL mode default),
     * a hard deadlock when there's only one connection (DELETE mode).
     * Pre-fetching matches the pattern used by the batch reader.
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
     * Sync-only: sees soft-deleted rows so the orchestrator can compare
     * local vs remote `deletedAt` on pull and propagate tombstones to
     * AWS on push. Without this, a tombstoned-and-pending local row
     * looks "missing" to the live read, the local-wins guard misses,
     * and the remote upsert stomps the tombstone (data-loss bug).
     * UI/repo paths must use [getExerciseByUuid].
     *
     * Lean mapper — no category resolution (sync only needs uuid /
     * remoteId / pendingUpload for the guard).
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
     * Insert if uuid not already present. Returns true on insert, false on
     * skip. Was used by the Parse `DefaultExercisesMigrator` so re-running after a
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
            // Sees tombstones — a row the user deliberately deleted
            // shouldn't be re-imported by a migrator re-run.
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
            val categoryByUuid = mapper.allCategoriesByUuid()
            buildExercisesQuery(userId, categoryByUuid).executeAsList()
        }

    /**
     * Reactive variant of [getAllExercisesWithCategoriesBatch]. SQLDelight
     * emits a fresh snapshot on any mutation of the `exercises` table —
     * powers Android's live exercise-list UI updates after create / delete
     * without re-querying from the use case.
     *
     * Categories are captured once at flow construction. The catalog is
     * admin-managed (seeded via `scripts/seed_aws_global_catalog.py`) so
     * one snapshot is good for the flow's lifetime. If you ever need
     * categories to participate in reactive updates, combine with
     * `CategoriesDBDataSource.getAllCategoriesFlow`.
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
                ?.map { secondaryUuid ->
                    categoryByUuid[secondaryUuid]
                        ?: error("Secondary category not found for exercise '$uuid': '$secondaryUuid'")
                }
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
}
