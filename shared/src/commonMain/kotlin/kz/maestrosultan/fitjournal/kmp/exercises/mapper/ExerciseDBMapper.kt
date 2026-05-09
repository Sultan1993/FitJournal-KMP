package kz.maestrosultan.fitjournal.kmp.exercises.mapper

import kz.maestrosultan.fitjournal.kmp.exercises.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.kmp.exercises.entity.DBCategoryObject
import kz.maestrosultan.fitjournal.kmp.exercises.entity.DBExerciseObject

class ExerciseDBMapper(private val categoryDataSource: CategoriesDBDataSource) {

    /**
     * Single SELECT into a uuid-keyed map. Used by hot read paths
     * (e.g. building [ExercisesDBDataSource.getAllExercisesWithCategoriesBatch])
     * to avoid per-exercise category lookups.
     */
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
        // Use OrNull lookups so a missing or stale category uuid (e.g.
        // legacy data from a now-deleted category) doesn't throw an
        // NSException at the iOS Swift boundary and crash the app. If
        // the primary is missing we fall back to a placeholder category
        // — the exercise still surfaces with a usable name; the user
        // sees the data instead of crashing. Secondaries silently
        // skip missing entries (mapNotNull).
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
