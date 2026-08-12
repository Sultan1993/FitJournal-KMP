package kz.maestrosultan.fitjournal.domain.journal

data class Journal(
    val id: String,
    val name: String,
    val comments: String?,
    val isPersonal: Boolean,
    // Per-journal weekly workout target; overrides AWSUser.workoutGoal when set.
    val workoutGoal: Int? = null,
)
