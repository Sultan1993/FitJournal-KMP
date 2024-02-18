package kz.maestrosultan.fitjournal.kmp.exercises.datasource

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kz.maestrosultan.fitjournal.kmp.CategoryQueries
import kz.maestrosultan.fitjournal.kmp.exercises.entity.DBCategoryObject
import kz.maestrosultan.fitjournal.kmp.exercises.entity.map

class CategoriesDBDataSource(private val dao: CategoryQueries) {

    fun getAllCategories(): List<DBCategoryObject> {
        return dao.getAllCategories()
            .executeAsList()
            .map { it.map() }
    }

    fun getAllCategoriesFlow(): Flow<List<DBCategoryObject>> {
        return dao.getAllCategories()
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { it.map { it.map() } }
    }

    fun getCategoryByUuid(uuid: String): DBCategoryObject {
        return dao.getCategoryByUuid(uuid)
            .executeAsOne()
            .map()
    }

    fun getCategoriesByUuids(uuids: List<String>): List<DBCategoryObject> {
        return dao.transactionWithResult {
            uuids.map { getCategoryByUuid(it) }
        }
    }

    fun getCategoryByUuidFlow(uuid: String): Flow<DBCategoryObject> {
        return dao.getCategoryByUuid(uuid)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.map() }
    }

    fun getCategoryByRemoteId(remoteId: String): DBCategoryObject {
        return dao.getCategoryByRemoteId(remoteId)
            .executeAsOne()
            .map()
    }

    fun getCategoryByRemoteIdFlow(remoteId: String): Flow<DBCategoryObject> {
        return dao.getCategoryByRemoteId(remoteId)
            .asFlow()
            .mapToOne(Dispatchers.IO)
            .map { it.map() }
    }
}
