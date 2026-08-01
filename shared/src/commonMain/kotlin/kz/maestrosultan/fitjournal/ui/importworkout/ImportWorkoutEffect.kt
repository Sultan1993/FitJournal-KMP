package kz.maestrosultan.fitjournal.ui.importworkout

/** One-shot outputs the native host performs. */
sealed interface ImportWorkoutEffect {
    /** Import succeeded — the host dismisses the picker. */
    data object Dismiss : ImportWorkoutEffect
}
