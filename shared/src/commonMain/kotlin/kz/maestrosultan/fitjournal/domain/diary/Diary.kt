package kz.maestrosultan.fitjournal.domain.diary

data class Diary(
    val id: String,
    val name: String,
    val comments: String?,
    val isPersonal: Boolean,
)
