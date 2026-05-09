package kz.maestrosultan.fitjournal.kmp.diaries.entity

import kotlin.time.Instant
import kz.maestrosultan.fitjournal.kmp.Diaries
import kz.maestrosultan.fitjournal.kmp.time.parseStoredInstant

data class DBDiaryObject(
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

fun Diaries.map(): DBDiaryObject = DBDiaryObject(
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
