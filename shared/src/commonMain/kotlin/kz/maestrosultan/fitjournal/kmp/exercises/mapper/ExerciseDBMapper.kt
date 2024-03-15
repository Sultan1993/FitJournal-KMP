package kz.maestrosultan.fitjournal.kmp.exercises.mapper

import kz.maestrosultan.fitjournal.kmp.exercises.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.kmp.exercises.entity.DBExerciseObject

class ExerciseDBMapper(private val categoryDataSource: CategoriesDBDataSource) {

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
        val primaryCategory = categoryDataSource.getCategoryByUuid(primaryCategoryUuid)
        val secondaryCategories = if (secondaryCategoryUuids.isNullOrEmpty()) {
            emptyList()
        } else {
            val uuidList = secondaryCategoryUuids.split(";")
            categoryDataSource.getCategoriesByUuids(uuidList)
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
