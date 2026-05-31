package kz.maestrosultan.fitjournal.domain.measurement

import kotlin.time.Instant

data class BodyMeasurement(
    val id: String,
    val userId: String,
    val journalId: String,
    val type: BodyMeasurementType,
    val value: Double,
    val comment: String?,
    val date: Instant,
)
