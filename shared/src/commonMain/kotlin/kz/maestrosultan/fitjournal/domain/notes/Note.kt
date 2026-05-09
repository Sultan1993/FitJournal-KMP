package kz.maestrosultan.fitjournal.domain.notes

import kotlin.time.Instant

data class Note(
    val id: String,
    val userId: String,
    val text: String,
    val isPinned: Boolean,
    val createdDate: Instant,
    val updatedDate: Instant,
)
