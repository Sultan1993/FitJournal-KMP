package kz.maestrosultan.fitjournal.data.notes.entity

import kotlin.time.Instant
import kz.maestrosultan.fitjournal.data.db.Notes
import kz.maestrosultan.fitjournal.data.time.parseStoredInstant

data class DBNoteObject(
    val uuid: String,
    val userId: String,
    val text: String,
    val isPinned: Boolean,
    val createdDate: Instant,
    val updatedDate: Instant,
    val remoteId: String? = null,
    val pendingUpload: Boolean = false,
    val deletedAt: Instant? = null,
)

internal fun Notes.map() = DBNoteObject(
    uuid = uuid,
    userId = userId,
    text = text,
    isPinned = isPinned,
    createdDate = parseStoredInstant(createdDate),
    updatedDate = parseStoredInstant(updatedDate),
    remoteId = remoteId,
    pendingUpload = pendingUpload,
    deletedAt = deletedAt?.let(::parseStoredInstant),
)
