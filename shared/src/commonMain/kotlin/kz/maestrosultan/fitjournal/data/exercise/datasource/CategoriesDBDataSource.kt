package kz.maestrosultan.fitjournal.data.exercise.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kz.maestrosultan.fitjournal.data.db.CategoryQueries
import kz.maestrosultan.fitjournal.data.exercise.entity.DBCategoryObject
import kz.maestrosultan.fitjournal.data.exercise.entity.map

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

    suspend fun getCategoryByUuid(uuid: String): DBCategoryObject? = withContext(Dispatchers.IO) {
        dao.getCategoryByUuid(uuid).executeAsOneOrNull()?.map()
    }

    fun getCategoryByUuidFlow(uuid: String): Flow<DBCategoryObject> {
        return dao.getCategoryByUuid(uuid)
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

    /**
     * Sync-pull upsert for the global catalog. Idempotent — safe to call every tick.
     * Caller must not set pendingUpload (categories are read-only from the app side).
     */
    suspend fun upsertFromRemote(
        uuid: String,
        remoteId: String,
        nameEn: String,
        nameRu: String,
        nameUk: String,
        type: Int,
        details: String?,
    ) = withContext(Dispatchers.IO) {
        dao.upsertCategoryFromRemote(
            uuid = uuid,
            remoteId = remoteId,
            nameEn = nameEn,
            nameRu = nameRu,
            nameUk = nameUk,
            type = type.toLong(),
            details = details,
        )
    }

    internal fun getAllCategoriesBlocking(): List<DBCategoryObject> =
        dao.getAllCategories().executeAsList().map { it.map() }

    internal fun getCategoryByUuidBlocking(uuid: String): DBCategoryObject? =
        dao.getCategoryByUuid(uuid).executeAsOneOrNull()?.map()
}
