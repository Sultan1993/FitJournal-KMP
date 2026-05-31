package kz.maestrosultan.fitjournal.domain.journal

data class Journal(
    val id: String,
    val name: String,
    val comments: String?,
    val isPersonal: Boolean,
    // Overrides AWSUser.workoutGoal when non-null. Per-journal weekly workout
    // target; nullable so the per-journal override is opt-in.
    val workoutGoal: Int? = null,
)
