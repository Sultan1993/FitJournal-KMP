package kz.maestrosultan.fitjournal.kmp.measurements.entity

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDate
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.kmp.BodyMeasurements

data class DBBodyMeasurementObject(
    val uuid: String,
    val remoteId: String?,
    val userId: String,
    val diaryId: String,
    val measurementDate: LocalDate,
    val type: String,
    val value: Double,
    val comment: String?,
    val createdDate: LocalDateTime,
    val updatedDate: LocalDateTime
)

internal fun BodyMeasurements.map() = DBBodyMeasurementObject(
    uuid = uuid,
    remoteId = remoteId,
    userId = userId,
    diaryId = diaryId,
    measurementDate = measurementDate.toLocalDate(),
    type = type,
    value = value_,
    comment = comment,
    createdDate = createdDate.toLocalDateTime(),
    updatedDate = updatedDate.toLocalDateTime(),
)
