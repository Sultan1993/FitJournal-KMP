package kz.maestrosultan.fitjournal.kmp.measurements.entity

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kz.maestrosultan.fitjournal.kmp.PhotoMeasurements

data class DBPhotoMeasurementObject(
    val uuid: String,
    val userId: String,
    val diaryId: String,
    val path: String,
    val url: String,
    val type: String,
    val date: LocalDate,
    val createdDate: LocalDateTime,
    val updatedDate: LocalDateTime
)

internal fun PhotoMeasurements.map(): DBPhotoMeasurementObject {
    return DBPhotoMeasurementObject(
        uuid = uuid,
        userId = userId,
        diaryId = diaryId,
        path = path,
        url = url,
        type = type,
        date = LocalDate.parse(date),
        createdDate = LocalDateTime.parse(createdDate),
        updatedDate = LocalDateTime.parse(updatedDate)
    )
}
