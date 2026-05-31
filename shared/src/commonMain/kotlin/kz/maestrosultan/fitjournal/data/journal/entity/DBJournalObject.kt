package kz.maestrosultan.fitjournal.data.journal.entity

import kotlin.time.Instant
import kz.maestrosultan.fitjournal.data.db.Journals
import kz.maestrosultan.fitjournal.data.time.parseStoredInstant

data class DBJournalObject(
    val uuid: String,
    val remoteId: String?,
    val userId: String,
    val name: String,
    val comments: String?,
    val isPersonal: Boolean,
    val workoutGoal: Int?,
    val deletedAt: Instant?,
    val pendingUpload: Boolean,
    val createdDate: Instant,
    val updatedDate: Instant,
)

fun Journals.map(): DBJournalObject = DBJournalObject(
    uuid = uuid,
    remoteId = remoteId,
    userId = userId,
    name = name,
    comments = comments,
    isPersonal = isPersonal,
    workoutGoal = workoutGoal?.toInt(),
    deletedAt = deletedAt?.let(::parseStoredInstant),
    pendingUpload = pendingUpload,
    createdDate = parseStoredInstant(createdDate),
    updatedDate = parseStoredInstant(updatedDate),
)
