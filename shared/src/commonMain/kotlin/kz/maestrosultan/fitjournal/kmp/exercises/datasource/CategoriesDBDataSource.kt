package kz.maestrosultan.fitjournal.kmp.exercises.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.kmp.CategoryQueries
import kz.maestrosultan.fitjournal.kmp.exercises.entity.DBCategoryObject
import kz.maestrosultan.fitjournal.kmp.exercises.entity.map

class CategoriesDBDataSource(private val dao: CategoryQueries) {

    suspend fun getAllCategories(): List<DBCategoryObject> = withContext(Dispatchers.IO) {
        dao.getAllCategories()
            .executeAsList()
            .map { it.map() }
    }

    fun getAllCategoriesFlow(): Flow<List<DBCategoryObject>> {
        return dao.getAllCategories()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getCategoryByUuid(uuid: String): DBCategoryObject = withContext(Dispatchers.IO) {
        dao.getCategoryByUuid(uuid)
            .executeAsOne()
            .map()
    }

    /**
     * Nullable variant — returns null if no row exists. Use this from
     * Swift; `executeAsOne` raises `NSException` on missing row, which
     * Swift `try?` doesn't catch (Swift only catches `Swift.Error`).
     */
    suspend fun getCategoryByUuidOrNull(uuid: String): DBCategoryObject? = withContext(Dispatchers.IO) {
        dao.getCategoryByUuid(uuid).executeAsOneOrNull()?.map()
    }

    suspend fun getCategoriesByUuids(uuids: List<String>): List<DBCategoryObject> =
        withContext(Dispatchers.IO) {
            dao.transactionWithResult {
                uuids.map { uuid ->
                    dao.getCategoryByUuid(uuid).executeAsOne().map()
                }
            }
        }

    fun getCategoryByUuidFlow(uuid: String): Flow<DBCategoryObject> {
        return dao.getCategoryByUuid(uuid)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.map() }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getCategoryByRemoteId(remoteId: String): DBCategoryObject = withContext(Dispatchers.IO) {
        dao.getCategoryByRemoteId(remoteId)
            .executeAsOne()
            .map()
    }

    fun getCategoryByRemoteIdFlow(remoteId: String): Flow<DBCategoryObject> {
        return dao.getCategoryByRemoteId(remoteId)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.map() }
            .flowOn(Dispatchers.IO)
    }

    suspend fun createCategory(
        uuid: String,
        remoteId: String,
        nameEn: String,
        nameRu: String,
        nameUk: String,
        type: Int,
        details: String?
    ): DBCategoryObject = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            dao.createCategory(
                uuid = uuid,
                remoteId = remoteId,
                nameEn = nameEn,
                nameRu = nameRu,
                nameUk = nameUk,
                type = type.toLong(),
                details = details
            )
            dao.getCategoryByUuid(uuid).executeAsOne().map()
        }
    }

    /**
     * Insert if uuid not already present. Returns true on insert, false on
     * skip. Used by `DefaultCategoriesMigrator` so re-running after a
     * partial crash doesn't duplicate. Categories are global catalog rows
     * (already in AWS via the manual seed) so no `pendingUpload` flag here.
     */
    suspend fun createCategoryIfMissing(
        uuid: String,
        remoteId: String,
        nameEn: String,
        nameRu: String,
        nameUk: String,
        type: Int,
        details: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        dao.transactionWithResult {
            if (dao.getCategoryByUuid(uuid).executeAsOneOrNull() != null) {
                return@transactionWithResult false
            }
            dao.createCategory(
                uuid = uuid,
                remoteId = remoteId,
                nameEn = nameEn,
                nameRu = nameRu,
                nameUk = nameUk,
                type = type.toLong(),
                details = details,
            )
            true
        }
    }

    suspend fun deleteAllCategories() = withContext(Dispatchers.IO) {
        dao.deleteAllCategories()
    }

    // Synchronous helpers used inside SQLDelight row-mapper closures
    // (e.g. ExerciseDBMapper) where the surrounding callback cannot suspend.
    internal fun getAllCategoriesBlocking(): List<DBCategoryObject> =
        dao.getAllCategories().executeAsList().map { it.map() }

    internal fun getCategoryByUuidOrNullBlocking(uuid: String): DBCategoryObject? =
        dao.getCategoryByUuid(uuid).executeAsOneOrNull()?.map()
}
