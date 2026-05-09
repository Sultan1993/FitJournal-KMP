package kz.maestrosultan.fitjournal.kmp.measurements.entity

import kotlin.time.Instant
import kz.maestrosultan.fitjournal.kmp.BodyMeasurements
import kz.maestrosultan.fitjournal.kmp.time.parseStoredInstant

data class DBBodyMeasurementObject(
    val uuid: String,
    val remoteId: String?,
    val userId: String,
    val diaryId: String,
    val type: String,
    val value: Double,
    val comment: String?,
    val measurementDate: Instant,
    val createdDate: Instant,
    val updatedDate: Instant,
    val pendingUpload: Boolean = false,
    val deletedAt: Instant? = null,
)

internal fun BodyMeasurements.map() = DBBodyMeasurementObject(
    uuid = uuid,
    remoteId = remoteId,
    userId = userId,
    diaryId = diaryId,
    type = type,
    value = value_,
    comment = comment,
    measurementDate = parseStoredInstant(measurementDate),
    createdDate = parseStoredInstant(createdDate),
    updatedDate = parseStoredInstant(updatedDate),
    pendingUpload = pendingUpload,
    deletedAt = deletedAt?.let(::parseStoredInstant),
)
