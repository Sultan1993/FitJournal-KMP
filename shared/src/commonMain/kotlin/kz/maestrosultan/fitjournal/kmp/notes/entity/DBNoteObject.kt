package kz.maestrosultan.fitjournal.kmp.notes.entity

import kotlinx.datetime.LocalDateTime

data class DBNoteObject(
    val uuid: String,
    val back4AppId: String?,
    val userId: String,
    val text: String,
    val isPinned: Boolean,
    val isSynced: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
