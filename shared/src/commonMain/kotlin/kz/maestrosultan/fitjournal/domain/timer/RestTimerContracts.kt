package kz.maestrosultan.fitjournal.domain.timer

/**
 * The rest countdown as the UI sees it. Deadline-based: [Running.secondsLeft] is always
 * re-derived from [Running.endEpochMillis], never decremented, so a suspended/dozing
 * process snaps back to the truth on its first tick after resume.
 */
sealed interface RestTimerState {
    /** secondsLeft = the configured duration (what the idle card shows). */
    data class Idle(val secondsLeft: Int) : RestTimerState
    data class Running(val secondsLeft: Int, val endEpochMillis: Long) : RestTimerState
}

data class RestTimerConfig(
    val durationSeconds: Int = 90,
    val soundAndVibrationOn: Boolean = false,
    val autoStart: Boolean = false,
)

/** Superset of both platforms' rest context. iOS maps it to RestActivityInfo, Android to WorkoutTimerNotificationInfo. */
data class RestPresentationInfo(
    val nameLine: String?,
    val imageNames: List<String>,
    val setLine: String?,
    val nextLine: String?,
)

enum class RestEndReason { Stopped, Expired }

/**
 * Platform side of a rest. Called by the engine's lane, in enqueue order, on Dispatchers.Main.
 *
 * CONTRACT (§3.2): an implementation must either finish its work before returning or
 * append it SYNCHRONOUSLY to a FIFO queue of its own. It must NOT start an unordered
 * coroutine/Task per call, and must not defer the append itself — either reorders
 * persistence and alarm work relative to the engine's calls.
 * Errors must be swallowed (§11); a throw here would cross the SKIE boundary.
 */
interface RestTimerPresenter {
    fun restStarted(endEpochMillis: Long, totalSeconds: Int, info: RestPresentationInfo?, soundOn: Boolean)
    /** Called on every 1 Hz tick. iOS: no-op (the island is deadline-driven). Android: re-post to advance the progress bar. */
    fun restTicked(secondsLeft: Int)
    fun restEnded(reason: RestEndReason)
}
