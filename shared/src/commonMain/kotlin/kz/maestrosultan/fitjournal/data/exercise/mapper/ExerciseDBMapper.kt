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
        // mapNotNull, NOT error(): `secondaryCategoryUuids` is a ';'-joined blob
        // written verbatim from AWS with no foreign key behind it (only
        // primaryCategoryUuid has one). A server row referencing a muscle group
        // this device hasn't seeded yet is therefore reachable and normal — and
        // throwing here aborts the WHOLE query, not just this row, taking down
        // the exercise picker, workouts, history and stats at once. On iOS an
        // unbridged Kotlin throw is an uncatchable SIGABRT, and since the bad row
        // is already persisted it would abort on every launch. Dropping the
        // unresolvable id degrades to "one missing secondary muscle" instead.
        val secondaryCategories = if (secondaryCategoryUuids.isNullOrEmpty()) {
            emptyList()
        } else {
            secondaryCategoryUuids
                .split(";")
                .mapNotNull { secondaryUuid ->
                    categoryDataSource.getCategoryByUuidBlocking(secondaryUuid)
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
