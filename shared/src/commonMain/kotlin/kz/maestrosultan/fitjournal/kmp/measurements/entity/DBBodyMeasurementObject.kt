package kz.maestrosultan.fitjournal.kmp.measurements.entity

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.kmp.BodyMeasurements

data class DBBodyMeasurementObject(
    val uuid: String,
    val remoteId: String?,
    val userId: String,
    val diaryId: String,
    val type: String,
    val value: Double,
    val comment: String?,
    val measurementDate: LocalDateTime,
    val createdDate: LocalDateTime,
    val updatedDate: LocalDateTime
)

internal fun BodyMeasurements.map() = DBBodyMeasurementObject(
    uuid = uuid,
    remoteId = remoteId,
    userId = userId,
    diaryId = diaryId,
    type = type,
    value = value_,
    comment = comment,
    measurementDate = measurementDate.toLocalDateTime(),
    createdDate = createdDate.toLocalDateTime(),
    updatedDate = updatedDate.toLocalDateTime(),
)
