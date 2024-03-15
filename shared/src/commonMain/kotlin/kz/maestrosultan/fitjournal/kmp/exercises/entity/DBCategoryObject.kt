package kz.maestrosultan.fitjournal.kmp.exercises.entity

import kz.maestrosultan.fitjournal.kmp.Categories

data class DBCategoryObject(
    val uuid: String,
    val remoteId: String,
    val nameEn: String,
    val nameRu: String,
    val nameUk: String,
    val type: Int,
    val details: String?
)

internal fun Categories.map(): DBCategoryObject {
    return DBCategoryObject(
        uuid = uuid,
        remoteId = remoteId,
        nameEn = nameEn,
        nameRu = nameRu,
        nameUk = nameUk,
        type = type.toInt(),
        details = details
    )
}
