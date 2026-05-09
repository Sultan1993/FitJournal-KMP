package kz.maestrosultan.fitjournal.kmp.exercises.entity

import kotlin.time.Instant

data class DBExerciseObject(
    val uuid: String,
    val remoteId: String,
    val nameEn: String,
    val nameRu: String,
    val nameUk: String?,
    val image1: String?,
    val image2: String?,
    val details: String?,
    val resultType: Int,
    val primaryCategory: DBCategoryObject,
    val secondaryCategories: List<DBCategoryObject>?,
    val isGlobal: Boolean,
    val userId: String? = null,
    val pendingUpload: Boolean = false,
    val deletedAt: Instant? = null,
)
