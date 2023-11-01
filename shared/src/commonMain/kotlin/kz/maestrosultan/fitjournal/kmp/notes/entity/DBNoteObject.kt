package kz.maestrosultan.fitjournal.kmp.notes.entity

import kotlinx.datetime.LocalDateTime

data class DBNoteObject(
    val uuid: String,
    val userId: String,
    val text: String,
    val isPinned: Boolean,
    val isSynced: Boolean,
    val createdDate: LocalDateTime,
    val updatedDate: LocalDateTime
)
