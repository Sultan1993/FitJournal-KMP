package kz.maestrosultan.fitjournal.kmp.notes.entity

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import kz.maestrosultan.fitjournal.kmp.Notes

data class DBNoteObject(
    val uuid: String,
    val userId: String,
    val text: String,
    val isPinned: Boolean,
    val createdDate: LocalDateTime,
    val updatedDate: LocalDateTime
)

internal fun Notes.map() = DBNoteObject(
    uuid = uuid,
    userId = userId,
    text = text,
    isPinned = isPinned,
    createdDate = createdDate.toLocalDateTime(),
    updatedDate = updatedDate.toLocalDateTime(),
)
