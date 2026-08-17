package kz.maestrosultan.fitjournal.data.record.entity

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.db.WorkoutNotes
import kz.maestrosultan.fitjournal.data.time.parseStoredInstant

/**
 * Typed mirror of a `workoutNotes` row — one free-text note per workout page
 * (`userId`, `journalId`, `date`, `workoutNumber`). Only the sync layer needs
 * the whole row; the UI reads the note text through `RecordRepository`.
 */
data class DBWorkoutNoteObject(
    val uuid: String,
    val userId: String,
    val journalId: String,
    val date: LocalDate,
    val workoutNumber: Int,
    val comment: String,
    val remoteId: String? = null,
    val deletedAt: Instant? = null,
)

fun WorkoutNotes.map(): DBWorkoutNoteObject = DBWorkoutNoteObject(
    uuid = uuid,
    userId = userId,
    journalId = journalId,
    date = LocalDate.parse(date),
    workoutNumber = workoutNumber.toInt(),
    comment = comment,
    remoteId = remoteId,
    deletedAt = deletedAt?.let(::parseStoredInstant),
)
