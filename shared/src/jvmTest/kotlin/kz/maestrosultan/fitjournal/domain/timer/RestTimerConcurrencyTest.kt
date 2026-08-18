@file:OptIn(ExperimentalCoroutinesApi::class)

package kz.maestrosultan.fitjournal.domain.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/**
 * Spec §13 cases 27-32 — the serialization contract (§12.14): exactly one writer, mutators
 * legal from any thread/dispatcher, applied in enqueue order, each command's presenter
 * callback finishing before the next command's mutation. Every case asserts on the ordered
 * presenter log, not on final state alone: "stopped but still scheduled" and "restStarted
 * finishing after restEnded" are both invisible to a final-state assertion.
 */
class RestTimerConcurrencyTest {

    // ─── 27 ───────────────────────────────────────────────────────────────

    @Test
    fun case27_stopFromAnotherDispatcher_endsStopped_andLeavesNothingScheduled() = restTimerTest { bed ->
        bed.openGate()
        bed.timer.start()
        bed.timer.awaitPending()
        assertTrue(bed.presenter.scheduled)
        assertTrue(bed.presenter.persisted)

        // Mirrors WorkoutTimerBroadcastReceiver: the ongoing notification's Stop button
        // calls in from CoroutineScope(Dispatchers.Default), never from main.
        callFrom(Dispatchers.Default) { bed.timer.stop() }
        bed.timer.awaitPending()

        assertEquals(RestTimerState.Idle(90), bed.timer.state.value)
        assertEquals("restEnded(Stopped)", bed.presenter.lifecycle.last())
        // No stopped-but-still-scheduled end state.
        assertFalse(bed.presenter.scheduled)
        assertFalse(bed.presenter.persisted)
    }

    // ─── 28 ───────────────────────────────────────────────────────────────

    @Test
    fun case28_startStopStart_fromThreeDispatchers_appliesInIssueOrder() = restTimerTest { bed ->
        bed.openGate()

        callFrom(Dispatchers.Default) { bed.timer.start() }
        bed.timer.awaitPending()
        val firstEnd = T0_MILLIS + 90_000

        bed.clock.nowMillis += 20_000
        callFrom(Dispatchers.IO) { bed.timer.stop() }
        bed.timer.awaitPending()

        bed.clock.nowMillis += 5_000
        bed.timer.start()   // third caller: the lane's own (main-equivalent) dispatcher
        bed.timer.awaitPending()
        val newestEnd = T0_MILLIS + 25_000 + 90_000

        assertEquals(
            listOf(
                "restStarted(total=90,end=$firstEnd)",
                "restEnded(Stopped)",
                "restStarted(total=90,end=$newestEnd)",
            ),
            bed.presenter.lifecycle,
        )
        assertEquals(RestTimerState.Running(90, newestEnd), bed.timer.state.value)
        assertTrue(bed.presenter.scheduled)
        assertTrue(bed.presenter.persisted)
    }

    // ─── 29 ───────────────────────────────────────────────────────────────

    /**
     * The `forDeadline` stamp is what makes "a racing natural-expiry block cannot clear the
     * new deadline" true by construction. On a single-threaded lane a `Start` always cancels
     * the previous tick job before it can fire, so the stale tick is injected through the
     * same `enqueueTick` the job itself uses — the race only exists on real threads, but the
     * guard has to be pinned regardless.
     */
    @Test
    fun case29_aTickFromARetiredJob_isDroppedAndLeavesTheNewDeadlineIntact() = restTimerTest { bed ->
        bed.openGate()
        bed.timer.start()
        bed.timer.awaitPending()
        val retiredEnd = T0_MILLIS + 90_000

        // The first rest's deadline has passed; the user started a new one on top of it.
        bed.clock.nowMillis = retiredEnd + 500
        bed.timer.start()
        bed.timer.awaitPending()
        val currentEnd = retiredEnd + 500 + 90_000

        bed.timer.enqueueTick(retiredEnd)
        bed.timer.awaitPending()

        assertEquals(RestTimerState.Running(90, currentEnd), bed.timer.state.value)
        assertFalse(bed.presenter.log.any { it.startsWith("restEnded") })
        assertEquals(
            listOf("restStarted(total=90,end=$retiredEnd)", "restStarted(total=90,end=$currentEnd)"),
            bed.presenter.log,
        )
    }

    // ─── 30 ───────────────────────────────────────────────────────────────

    @Test
    fun case30_expiryRacingAnEnqueuedStop_emitsExactlyOneRestEnded() = restTimerTest { bed ->
        bed.openGate()
        bed.timer.start()
        bed.timer.awaitPending()
        val end = T0_MILLIS + 90_000

        // Both pending on the lane at once: the expiry tick in flight, a stop behind it.
        bed.clock.nowMillis = end + 250
        bed.timer.enqueueTick(end)
        bed.timer.stop()
        bed.timer.awaitPending()

        assertEquals(RestTimerState.Idle(90), bed.timer.state.value)
        assertEquals(listOf("restEnded(Expired)"), bed.presenter.log.filter { it.startsWith("restEnded") })

        // …and the other arrival order: the stop lands first, the expiry tick is dropped.
        bed.timer.start()
        bed.timer.awaitPending()
        val secondEnd = bed.clock.nowMillis + 90_000
        bed.clock.nowMillis = secondEnd + 250
        bed.timer.stop()
        bed.timer.enqueueTick(secondEnd)
        bed.timer.stop()                 // a Stop applied to an already-idle timer adds nothing
        bed.timer.awaitPending()

        assertEquals(RestTimerState.Idle(90), bed.timer.state.value)
        assertEquals(
            listOf("restEnded(Expired)", "restEnded(Stopped)"),
            bed.presenter.log.filter { it.startsWith("restEnded") },
        )
    }

    // ─── 31 ───────────────────────────────────────────────────────────────

    @Test
    fun case31_awaitPending_resumesOnlyAfterEveryPresenterCallbackReturned() = restTimerTest { bed ->
        bed.openGate()
        val latch = CompletableDeferred<Unit>()
        bed.presenter.endLatch = latch

        bed.timer.start()
        bed.timer.stop()
        // Mutators are pure enqueues: nothing has been applied yet.
        assertFalse(latch.isCompleted)
        assertTrue(bed.presenter.log.isEmpty())

        bed.timer.awaitPending()

        assertTrue(latch.isCompleted)
        assertEquals("restEnded(Stopped)", bed.presenter.log.last())
    }

    // ─── 32 ───────────────────────────────────────────────────────────────

    /**
     * The fake appends its log line as the LAST statement of every callback and does all of
     * its work synchronously (the presenter contract: finish before returning, or append to
     * your own FIFO — never a Task per call). So [FakeRestTimerPresenter.log] is also the
     * order the presenter FINISHED, and `restStarted` must never finish after `restEnded`.
     */
    @Test
    fun case32_restStartedNeverFinishesAfterRestEnded() = restTimerTest { bed ->
        bed.openGate()
        bed.timer.start()
        bed.timer.stop()
        bed.timer.awaitPending()

        assertEquals(
            listOf("restStarted(total=90,end=${T0_MILLIS + 90_000})", "restEnded(Stopped)"),
            bed.presenter.log,
        )
        // Had restStarted's work been deferred/launched instead of finished in-call, its
        // scheduling would have landed after restEnded cleared it and these would be true.
        assertFalse(bed.presenter.scheduled)
        assertFalse(bed.presenter.persisted)

        // No stray tick job survived the stop.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(2, bed.presenter.log.size)
    }
}
