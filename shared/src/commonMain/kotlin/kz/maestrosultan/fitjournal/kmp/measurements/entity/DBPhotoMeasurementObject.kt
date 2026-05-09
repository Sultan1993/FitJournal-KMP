package kz.maestrosultan.fitjournal.kmp.measurements.entity

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.kmp.PhotoMeasurements
import kz.maestrosultan.fitjournal.kmp.time.parseStoredInstant

data class DBPhotoMeasurementObject(
    val uuid: String,
    val userId: String,
    val diaryId: String,
    val path: String,
    val url: String,
    val type: String,
    /**
     * Calendar day the photo was taken — kept as `LocalDate` (no zone) to
     * match how the user thinks of it ("photo from May 8") rather than as
     * an absolute moment.
     */
    val date: LocalDate,
    val createdDate: Instant,
    val updatedDate: Instant,
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
        createdDate = parseStoredInstant(createdDate),
        updatedDate = parseStoredInstant(updatedDate),
    )
}
