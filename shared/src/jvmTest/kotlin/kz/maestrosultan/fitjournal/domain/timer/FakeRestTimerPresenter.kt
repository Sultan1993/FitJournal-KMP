@file:OptIn(ExperimentalCoroutinesApi::class)

package kz.maestrosultan.fitjournal.domain.timer

import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

/**
 * Records the platform surface as an ORDERED log, because the engine's contract (§12.14)
 * is about order, not about final state: "stopped but still scheduled" and "restStarted
 * finishing after restEnded" are both invisible to a final-state assertion.
 *
 * The log line is appended as the LAST statement of every callback, so [log] is
 * simultaneously the call order and the order the presenter FINISHED its work — which is
 * what §13 case 32 asserts. All work here is synchronous: this fake is itself an instance
 * of the presenter contract ("finish before returning"), never a `launch` per call.
 *
 * Never throws — a throw from a presenter would cross the SKIE boundary (SIGABRT on iOS).
 */
class FakeRestTimerPresenter : RestTimerPresenter {
    val log = mutableListOf<String>()
    var scheduled = false      // set true on restStarted, false on a Stopped end
    var persisted = false      // same lifecycle; mirrors Android's stored deadline
    var endLatch: CompletableDeferred<Unit>? = null
    var lastStartedInfo: RestPresentationInfo? = null
    var lastSoundOn = false

    override fun restStarted(endEpochMillis: Long, totalSeconds: Int, info: RestPresentationInfo?, soundOn: Boolean) {
        scheduled = true; persisted = true
        lastStartedInfo = info; lastSoundOn = soundOn
        log += "restStarted(total=$totalSeconds,end=$endEpochMillis)"
    }

    override fun restTicked(secondsLeft: Int) { log += "restTicked($secondsLeft)" }

    override fun restEnded(reason: RestEndReason) {
        // Expired deliberately leaves `scheduled` alone: the platform keeps its pending
        // rest-end notification, which fires on its own wall clock at the same deadline.
        if (reason == RestEndReason.Stopped) { scheduled = false }
        persisted = false
        log += "restEnded($reason)"
        endLatch?.complete(Unit)
    }
}

/**
 * The lifecycle callbacks only. Cases that hand a mutator to a REAL dispatcher have to
 * suspend the test coroutine on it, and `runTest` may auto-advance virtual time while it
 * waits — which can let the 1 Hz job land extra `restTicked` lines. Those are noise for an
 * ordering assertion about start/end, so those cases assert on this view of [log].
 */
val FakeRestTimerPresenter.lifecycle: List<String>
    get() = log.filterNot { it.startsWith("restTicked") }

/** Steppable clock so tests never sleep: "now" moves only when a test writes [nowMillis]. */
class FakeClock(var nowMillis: Long) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(nowMillis)
}

/** Wall-clock origin shared by the engine suites. Arbitrary, but on a second boundary. */
const val T0_MILLIS: Long = 1_770_000_000_000L

/**
 * One engine plus the doubles driving it. The lane runs on a [StandardTestDispatcher]
 * sharing the test's scheduler, so `advanceTimeBy`/`runCurrent` drive both the lane and
 * the 1 Hz tick job, and the fake clock is stepped independently (the engine derives
 * `secondsLeft` from the clock, not from virtual time).
 */
class TimerBed(scheduler: TestCoroutineScheduler, startMillis: Long) {
    val presenter = FakeRestTimerPresenter()
    val clock = FakeClock(startMillis)
    val scope = CoroutineScope(StandardTestDispatcher(scheduler))
    val timer = RestTimer(presenter = presenter, scope = scope, clock = clock)

    /** Opens the config gate (§7.4). Every case except 41-42 does this first. */
    fun openGate(config: RestTimerConfig = RestTimerConfig()) = timer.applyConfig(config)
}

/**
 * `runTest` with a bed whose scope is cancelled in a `finally`.
 *
 * Unconditional and inside the test body on purpose: the engine's tick job is an infinite
 * `delay` loop on the test scheduler, and `runTest` will not return while that loop is
 * alive. Cancelling in an `@AfterTest` would be too late — a failed assertion would hang
 * the suite instead of reporting the failure (the same trap documented in
 * `WorkoutFinishViewModelTest`).
 */
fun restTimerTest(
    startMillis: Long = T0_MILLIS,
    body: suspend TestScope.(TimerBed) -> Unit,
) = runTest {
    val bed = TimerBed(testScheduler, startMillis)
    try {
        body(this, bed)
    } finally {
        bed.scope.cancel()
    }
}

/**
 * Calls a (non-suspending) engine mutator from a real [dispatcher] — the point of §13.27-28,
 * because Android's `WorkoutTimerBroadcastReceiver` calls `stop()` from
 * `CoroutineScope(Dispatchers.Default)`.
 *
 * `runBlocking` deliberately: it blocks the test thread until the hop returns, which keeps
 * the virtual clock still. Suspending on the real dispatcher instead would let `runTest`
 * auto-advance virtual time with nothing else runnable, and a 1 Hz tick loop can burn a
 * whole 90 s rest — expiring it mid-hop — in less time than the thread pool takes to warm
 * up. The block only calls `trySend`, so it cannot suspend or deadlock on the scheduler.
 */
fun callFrom(dispatcher: CoroutineDispatcher, block: () -> Unit) {
    runBlocking(dispatcher) { block() }
}
