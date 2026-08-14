package kz.maestrosultan.fitjournal.ui.workout.share.seams

/**
 * Haptic feedback for the post-workout flow: [tick] on small selection changes
 * (layout/backdrop/stat toggles), [success] on completed save/share.
 * Fire-and-forget; implementations must never block.
 */
interface PostWorkoutHaptics {
    fun tick()
    fun success()
}
