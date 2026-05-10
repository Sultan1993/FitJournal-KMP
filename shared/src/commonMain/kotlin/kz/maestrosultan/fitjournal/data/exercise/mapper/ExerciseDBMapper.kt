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
        // Use OrNull to tolerate missing/stale categories; fallback placeholder prevents crashes.
        val primaryCategory = categoryDataSource.getCategoryByUuidOrNullBlocking(primaryCategoryUuid)
            ?: DBCategoryObject(
                uuid = primaryCategoryUuid,
                remoteId = primaryCategoryUuid,
                nameEn = "Unknown",
                nameRu = "Unknown",
                nameUk = "Unknown",
                type = 0,
                details = null
            )
        val secondaryCategories = if (secondaryCategoryUuids.isNullOrEmpty()) {
            emptyList()
        } else {
            secondaryCategoryUuids
                .split(";")
                .mapNotNull { categoryDataSource.getCategoryByUuidOrNullBlocking(it) }
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
