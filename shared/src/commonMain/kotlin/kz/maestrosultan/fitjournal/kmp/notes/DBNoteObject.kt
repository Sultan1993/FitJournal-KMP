package kz.maestrosultan.fitjournal.kmp.notes

data class DBNoteObject(
    val uuid: String,
    val back4AppId: String?,
    val userId: String,
    val text: String,
    val isPinned: Boolean
)
