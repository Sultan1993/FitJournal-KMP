package kz.maestrosultan.fitjournal.data.exercise.mapper

import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.entity.DBCategoryObject
import kz.maestrosultan.fitjournal.data.exercise.entity.DBExerciseObject

class ExerciseDBMapper(private val categoryDataSource: CategoriesDBDataSource) {

    fun allCategoriesByUuid(): Map<String, DBCategoryObject> {
        return categoryDataSource.getAllCategoriesBlocking().associateBy { it.uuid }
    }

    fun map(
        uuid: String,
        remoteId: String,
        nameEn: String,
        nameRu: String,
        nameUk: String?,
        details: String?,
        image1: String?,
        image2: String?,
        resultType: Int,
        primaryCategoryUuid: String,
        secondaryCategoryUuids: String?,
        isGlobal: Boolean
    ): DBExerciseObject {
        val primaryCategory = categoryDataSource.getCategoryByUuidBlocking(primaryCategoryUuid)
            ?: error("Catalog category not found for exercise '$uuid': '$primaryCategoryUuid'")
        val secondaryCategories = if (secondaryCategoryUuids.isNullOrEmpty()) {
            emptyList()
        } else {
            secondaryCategoryUuids
                .split(";")
                .map { secondaryUuid ->
                    categoryDataSource.getCategoryByUuidBlocking(secondaryUuid)
                        ?: error("Secondary category not found for exercise '$uuid': '$secondaryUuid'")
                }
        }

        return DBExerciseObject(
            uuid = uuid,
            remoteId = remoteId,
            nameEn = nameEn,
            nameRu = nameRu,
            nameUk = nameUk,
            image1 = image1,
            image2 = image2,
            details = details,
            resultType = resultType,
            primaryCategory = primaryCategory,
            secondaryCategories = secondaryCategories,
            isGlobal = isGlobal
        )
    }
}
