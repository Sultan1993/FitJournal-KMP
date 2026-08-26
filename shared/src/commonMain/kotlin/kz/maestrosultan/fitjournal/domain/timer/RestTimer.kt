package kz.maestrosultan.fitjournal.domain.timer

import kotlin.time.Clock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One lane command. Private: the channel is the only way anything reaches the writer. */
private sealed interface Cmd {
    data class Start(val info: RestPresentationInfo?) : Cmd
    data object Stop : Cmd
    data class Toggle(val info: RestPresentationInfo?) : Cmd
    data class AutoStart(val info: RestPresentationInfo?) : Cmd
    data class Resume(val end: Long, val info: RestPresentationInfo?) : Cmd
    data class Apply(val config: RestTimerConfig) : Cmd
    data class Info(val info: RestPresentationInfo?) : Cmd
    /** Carries the deadline it was scheduled for, so a tick from a retired job is dropped. */
    data class Tick(val forDeadline: Long) : Cmd
    data class Barrier(val done: CompletableDeferred<Unit>) : Cmd
}

/**
 * The one rest countdown, shared by both apps: one serialized command lane behind one
 * config gate.
 *
 * The engine is called from more than one dispatcher. Android's
 * `WorkoutTimerBroadcastReceiver` (the Stop button on the ongoing rest notification) calls
 * `stop()` from `CoroutineScope(Dispatchers.Default)`, while every other call site is
 * main-thread. So nothing is assumed about the caller's thread, and side effects are never
 * `launch`ed per call — launched work runs concurrently and need not start immediately, so a
 * start and a stop racing that way could leave a stopped rest persisted, or an alarm
 * scheduled for a rest nobody is taking. Instead every mutator ENQUEUES on an UNLIMITED
 * channel and returns, and exactly one coroutine (the lane, [apply]) writes [state], [config]
 * and the current info. FIFO through one channel is the whole mechanism: no `Mutex`, no
 * atomics, no thread assumptions.
 *
 * Nothing is applied before a configuration arrives (§7.4): a `start()` enqueued during
 * launch waits in the channel until the first [applyConfig] lands, so a cold-started app can
 * neither rest for the compile-time default duration nor lose a persisted `autoStart`. Two
 * consequences to accept: [state] publishes `Idle(RestTimerConfig().durationSeconds)` for the
 * few milliseconds before the gate opens (an idle card that corrects itself — no rest can be
 * running yet), and [awaitPending] resumes only after the gate opens, which is why the launch
 * `applyConfig` is mandatory on every outcome including a failed load.
 *
 * @param scope the lane's home. Defaults to a main-dispatched supervisor scope; one app-wide
 *   instance per platform, so in production it is never cancelled.
 * @param clock injected so tests don't sleep.
 */
class RestTimer(
    private val presenter: RestTimerPresenter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    private val clock: Clock = Clock.System,
) {

    private val commands = Channel<Cmd>(Channel.UNLIMITED)   // UNLIMITED => trySend never fails

    /** The config gate: completed by the FIRST applyConfig(...). Nothing is applied before it. */
    private val initialConfig = CompletableDeferred<RestTimerConfig>()

    private val _state = MutableStateFlow<RestTimerState>(RestTimerState.Idle(RestTimerConfig().durationSeconds))

    /**
     * The countdown as the UI reads it. **Eventually consistent**: it reflects the last
     * *applied* command, so a read taken immediately after enqueuing may still see
     * pre-command state. That is safe because the presenter is the last writer — a caller that
     * stops and then reconciles its platform surface may see the rest still running and
     * refresh it, and the lane's subsequent `restEnded` then ends it; the platform surface
     * converges. A caller that cannot tolerate the lag calls [awaitPending] first.
     */
    val state: StateFlow<RestTimerState> = _state

    private var appliedConfig = RestTimerConfig()

    /**
     * The last APPLIED config. Diagnostics and display only — no behavioural branch may read
     * this (that is what [autoStart] is for), and like [state] it is eventually consistent.
     */
    val config: RestTimerConfig get() = appliedConfig

    private var info: RestPresentationInfo? = null

    private var tickJob: Job? = null

    /** Convenience mirror of [state]; same eventual-consistency rule. */
    val isRunning: Boolean get() = _state.value is RestTimerState.Running

    /** Convenience mirror of [state]; same eventual-consistency rule. */
    val secondsLeft: Int
        get() = when (val current = _state.value) {
            is RestTimerState.Idle -> current.secondsLeft
            is RestTimerState.Running -> current.secondsLeft
        }

    /** The rest context the platform surface was last driven with; same eventual-consistency rule. */
    fun currentInfo(): RestPresentationInfo? = info

    init {
        scope.launch {
            appliedConfig = initialConfig.await()                // gate (§7.4)
            _state.value = RestTimerState.Idle(appliedConfig.durationSeconds)
            for (cmd in commands) apply(cmd)                     // the lane: the single writer
        }
    }

    /** First call opens the config gate; later calls are ordinary lane commands. */
    fun applyConfig(config: RestTimerConfig) {
        // The first call opens the gate directly — it must NOT queue behind commands already
        // sitting in the channel, or a start() issued during launch would be applied first.
        if (!initialConfig.complete(config)) commands.trySend(Cmd.Apply(config))
    }

    fun updateInfo(info: RestPresentationInfo?) {
        commands.trySend(Cmd.Info(info))
    }

    /** `info == null` means "keep the current info" — resolved inside the lane. */
    fun start(info: RestPresentationInfo? = null) {
        commands.trySend(Cmd.Start(info))
    }

    fun stop() {
        commands.trySend(Cmd.Stop)
    }

    fun toggle(info: RestPresentationInfo? = null) {
        commands.trySend(Cmd.Toggle(info))
    }

    /**
     * Starts a full rest only if the APPLIED config has `autoStart` on; the decision is made
     * inside the lane, so it can never be taken against a pre-gate default (§7.5).
     */
    fun autoStart(info: RestPresentationInfo? = null) {
        commands.trySend(Cmd.AutoStart(info))
    }

    /** Android-only app-start path: adopt a persisted, still-future deadline. */
    fun resume(endEpochMillis: Long, info: RestPresentationInfo? = null) {
        commands.trySend(Cmd.Resume(endEpochMillis, info))
    }

    /**
     * Suspends until every command enqueued before this call has been applied and its
     * presenter callbacks have returned. The lane is FIFO and single-consumer, so the barrier
     * completes only after all of them.
     *
     * Resumes only once the config gate has opened (§7.1) — a barrier is an ordinary lane
     * command, and the lane consumes nothing before the first [applyConfig].
     */
    suspend fun awaitPending() {
        CompletableDeferred<Unit>().also { commands.trySend(Cmd.Barrier(it)) }.await()
    }

    /**
     * The single writer. Runs on the lane, in enqueue order; each command's presenter call
     * happens after that command's own mutation, so the platform surface is driven in exactly
     * the order the callers asked for.
     */
    private fun apply(cmd: Cmd) {
        when (cmd) {
            is Cmd.Start -> {
                // Stop, not pause: a start while running silently restarts a FULL rest, with no
                // intervening restEnded (the auto-start-after-logged-set flow relies on that).
                val duration = appliedConfig.durationSeconds
                if (duration <= 0) return
                info = cmd.info ?: info
                val end = clock.now().toEpochMilliseconds() + duration * 1000L
                tickJob?.cancel()
                _state.value = RestTimerState.Running(duration, end)
                tickJob = startTicking(end)
                presenter.restStarted(end, duration, info, appliedConfig.soundAndVibrationOn)
            }

            is Cmd.Stop -> {
                // Already idle: no second restEnded, so an expiry racing a stop ends once.
                if (_state.value !is RestTimerState.Running) return
                tickJob?.cancel()
                _state.value = RestTimerState.Idle(appliedConfig.durationSeconds)
                presenter.restEnded(RestEndReason.Stopped)
            }

            is Cmd.Toggle ->
                if (_state.value is RestTimerState.Running) apply(Cmd.Stop) else apply(Cmd.Start(cmd.info))

            is Cmd.AutoStart ->
                if (appliedConfig.autoStart) apply(Cmd.Start(cmd.info))

            is Cmd.Resume -> {
                val now = clock.now().toEpochMilliseconds()
                // A passed deadline is a no-op; the caller clears its stale stored value.
                if (cmd.end <= now) return
                info = cmd.info ?: info
                val remaining = ceilSeconds(cmd.end - now)
                tickJob?.cancel()
                _state.value = RestTimerState.Running(remaining, cmd.end)
                tickJob = startTicking(cmd.end)
                // The REMAINING span, not appliedConfig.durationSeconds: the total is
                // what the platform's progress bar divides by (Android computes
                // elapsed = total - secondsLeft), and a duration the user changed
                // between starting this rest and the process dying would skew it, or
                // clamp it to 0. Resuming at 0% of what is left is the honest reading
                // — the span already served died with the process.
                presenter.restStarted(cmd.end, remaining, info, appliedConfig.soundAndVibrationOn)
            }

            is Cmd.Apply -> {
                // Never yanks a running rest; the new duration applies from the next start.
                appliedConfig = cmd.config
                if (_state.value is RestTimerState.Idle) {
                    _state.value = RestTimerState.Idle(cmd.config.durationSeconds)
                }
            }

            // Exact parity: Android consumes WorkoutTimerNotificationInfo only at start (there
            // is no refresh path), and iOS reads currentInfo() when it reconciles.
            is Cmd.Info -> info = cmd.info

            is Cmd.Tick -> {
                val current = _state.value
                if (current !is RestTimerState.Running || current.endEpochMillis != cmd.forDeadline) return
                // Re-derived from the deadline, never decremented: ticks freeze while the
                // process is suspended, and the first tick after resume snaps back to truth.
                val left = ceilSeconds(cmd.forDeadline - clock.now().toEpochMilliseconds())
                if (left <= 0) {
                    tickJob?.cancel()
                    _state.value = RestTimerState.Idle(appliedConfig.durationSeconds)
                    // Expired, not Stopped: the platform keeps its pending rest-end
                    // notification, which fires on its own wall clock at the same deadline.
                    presenter.restEnded(RestEndReason.Expired)
                } else {
                    _state.value = RestTimerState.Running(left, cmd.forDeadline)
                    presenter.restTicked(left)
                }
            }

            is Cmd.Barrier -> cmd.done.complete(Unit)
        }
    }

    /**
     * The 1 Hz display tick, itself a command source: the job holds its deadline in its
     * closure and never reads or writes [state]. [start]/[resume] cancel the previous job
     * **and** the `forDeadline` stamp makes any tick already in flight from the retired job a
     * no-op, so a racing natural expiry cannot clear a new deadline — by construction, not by
     * timing.
     */
    private fun startTicking(end: Long): Job = scope.launch {
        while (true) {
            // Land on the second boundary the display changes on.
            val rem = (end - clock.now().toEpochMilliseconds()) % 1000L
            delay(if (rem > 0) rem else 1000L)
            enqueueTick(end)
        }
    }

    /**
     * The tick job's only interaction with the lane. `internal` rather than private so the
     * engine tests can deliver the retired-job tick that §13 cases 29-30 pin: on a
     * single-threaded lane a `Start` always cancels the previous job before it can fire, so the
     * stale tick the `forDeadline` guard exists for is unreachable through the public API.
     */
    internal fun enqueueTick(forDeadline: Long) {
        commands.trySend(Cmd.Tick(forDeadline))
    }

    private fun ceilSeconds(ms: Long): Int = ((ms + 999) / 1000).toInt()
}

/**
 * Swift-facing constructor. SKIE's default-argument interop is NOT enabled in this
 * project (`shared/build.gradle.kts` configures only `skie { analytics { … } }`), so
 * Swift sees no one-argument `RestTimer(presenter:)` init and cannot supply the Kotlin
 * `scope`/`clock` defaults. Bridges bare as `createRestTimer(presenter:)`.
 *
 * iOS-interop shim only: Kotlin callers (Android's Hilt provision, every test)
 * construct `RestTimer(presenter = …)` directly. Keep this body a single expression so
 * the two paths cannot diverge.
 */
fun createRestTimer(presenter: RestTimerPresenter): RestTimer = RestTimer(presenter = presenter)
