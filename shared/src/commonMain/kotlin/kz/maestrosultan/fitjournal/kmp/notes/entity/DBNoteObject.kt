package kz.maestrosultan.fitjournal.kmp.notes.entity

data class DBNoteObject(
    val uuid: String,
    val userId: String,
    val text: String,
    val isPinned: Boolean,
    val isSynced: Boolean
)
