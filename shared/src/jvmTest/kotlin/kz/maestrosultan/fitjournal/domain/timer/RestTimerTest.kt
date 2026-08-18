@file:OptIn(ExperimentalCoroutinesApi::class)

package kz.maestrosultan.fitjournal.domain.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent

/**
 * Engine behaviour, spec §13 cases 20-26 — the part that "must not change" when both
 * platforms' countdowns are deleted. Injected [FakeClock] + [TimerBed]'s
 * `StandardTestDispatcher` lane: nothing here sleeps, and every case opens the config gate
 * with an explicit `applyConfig` first (cases 41-42 in [RestTimerConfigGateTest] are the
 * ones that deliberately do not).
 */
class RestTimerTest {

    private val info = RestPresentationInfo(
        nameLine = "Bench Press",
        imageNames = listOf("bench_1"),
        setLine = "Set 2 of 4",
        nextLine = "Last: 70 kg × 8",
    )

    // ─── 20 ───────────────────────────────────────────────────────────────

    @Test
    fun case20_start_publishesRunning_andTheNextTickDerivesTheRemainder() = restTimerTest { bed ->
        bed.openGate(RestTimerConfig(durationSeconds = 90, soundAndVibrationOn = true))
        bed.timer.start(info)
        bed.timer.awaitPending()

        val end = T0_MILLIS + 90_000
        assertEquals(RestTimerState.Running(90, end), bed.timer.state.value)
        assertTrue(bed.timer.isRunning)
        assertEquals(90, bed.timer.secondsLeft)
        assertEquals(listOf("restStarted(total=90,end=$end)"), bed.presenter.log)
        assertEquals(info, bed.timer.currentInfo())
        assertEquals(info, bed.presenter.lastStartedInfo)
        assertTrue(bed.presenter.lastSoundOn)

        bed.clock.nowMillis += 1_000
        advanceTimeBy(1_000)
        runCurrent()

        // Re-derived from the deadline, not decremented.
        assertEquals(RestTimerState.Running(89, end), bed.timer.state.value)
        assertEquals(89, bed.timer.secondsLeft)
        assertEquals("restTicked(89)", bed.presenter.log.last())
    }

    // ─── 21 ───────────────────────────────────────────────────────────────

    @Test
    fun case21_clockJumpedPastTheDeadline_expiresExactlyOnceOnTheNextTick() = restTimerTest { bed ->
        bed.openGate()
        bed.timer.start()
        bed.timer.awaitPending()
        val end = T0_MILLIS + 90_000

        // The process was suspended/dozing through the whole rest (locked phone).
        bed.clock.nowMillis = end + 5_000
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(RestTimerState.Idle(90), bed.timer.state.value)
        assertEquals("restEnded(Expired)", bed.presenter.log.last())
        assertEquals(1, bed.presenter.log.count { it.startsWith("restEnded") })
        // Expiry keeps the platform's pending rest-end notification scheduled on purpose.
        assertTrue(bed.presenter.scheduled)

        // The tick job was retired with the rest: no further ticks, no second end.
        bed.clock.nowMillis += 10_000
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(1, bed.presenter.log.count { it.startsWith("restEnded") })
        assertEquals(RestTimerState.Idle(90), bed.timer.state.value)
    }

    // ─── 22 ───────────────────────────────────────────────────────────────

    @Test
    fun case22_stopWhileRunning_resetsToIdle_andEndsStopped() = restTimerTest { bed ->
        bed.openGate()
        bed.timer.start()
        bed.timer.awaitPending()

        bed.clock.nowMillis += 12_000
        bed.timer.stop()
        bed.timer.awaitPending()

        assertEquals(RestTimerState.Idle(90), bed.timer.state.value)
        assertFalse(bed.timer.isRunning)
        assertEquals("restEnded(Stopped)", bed.presenter.log.last())
        // An explicit stop cancels the pending notification; expiry does not.
        assertFalse(bed.presenter.scheduled)
        assertFalse(bed.presenter.persisted)
    }

    // ─── 23 ───────────────────────────────────────────────────────────────

    @Test
    fun case23_startWhileRunning_restartsFullDuration_withoutAnyRestEnded() = restTimerTest { bed ->
        bed.openGate()
        bed.timer.start(info)
        bed.timer.awaitPending()
        val firstEnd = T0_MILLIS + 90_000

        bed.clock.nowMillis += 30_000
        bed.timer.start()          // null info == "keep the current info"
        bed.timer.awaitPending()
        val secondEnd = T0_MILLIS + 30_000 + 90_000

        assertEquals(RestTimerState.Running(90, secondEnd), bed.timer.state.value)
        assertEquals(
            listOf("restStarted(total=90,end=$firstEnd)", "restStarted(total=90,end=$secondEnd)"),
            bed.presenter.log,
        )
        assertFalse(bed.presenter.log.any { it.startsWith("restEnded") })
        assertEquals(info, bed.timer.currentInfo())

        // updateInfo only swaps the context — no state change, no presenter call.
        val next = info.copy(setLine = "Set 3 of 4")
        bed.timer.updateInfo(next)
        bed.timer.awaitPending()
        assertEquals(next, bed.timer.currentInfo())
        assertEquals(RestTimerState.Running(90, secondEnd), bed.timer.state.value)
        assertEquals(2, bed.presenter.log.size)
    }

    // ─── 24 ───────────────────────────────────────────────────────────────

    @Test
    fun case24_applyConfig_neverYanksARunningRest_butRefreshesTheIdleDisplay() = restTimerTest { bed ->
        bed.openGate(RestTimerConfig(durationSeconds = 60))
        bed.timer.start()
        bed.timer.awaitPending()
        val end = T0_MILLIS + 60_000

        bed.timer.applyConfig(RestTimerConfig(durationSeconds = 120))
        bed.timer.awaitPending()

        assertEquals(RestTimerState.Running(60, end), bed.timer.state.value)
        assertEquals(120, bed.timer.config.durationSeconds)
        assertEquals(1, bed.presenter.log.size)          // applyConfig never calls the presenter

        // The new duration applies from the next reset onwards.
        bed.timer.stop()
        bed.timer.awaitPending()
        assertEquals(RestTimerState.Idle(120), bed.timer.state.value)

        bed.timer.applyConfig(RestTimerConfig(durationSeconds = 45))
        bed.timer.awaitPending()
        assertEquals(RestTimerState.Idle(45), bed.timer.state.value)
        assertEquals(2, bed.presenter.log.size)
    }

    // ─── 25 ───────────────────────────────────────────────────────────────

    @Test
    fun case25_zeroConfiguredDuration_makesStartANoOp() = restTimerTest { bed ->
        bed.openGate(RestTimerConfig(durationSeconds = 0))
        bed.timer.start(info)
        bed.timer.awaitPending()

        assertEquals(RestTimerState.Idle(0), bed.timer.state.value)
        assertFalse(bed.timer.isRunning)
        assertTrue(bed.presenter.log.isEmpty())
    }

    // ─── 26 ───────────────────────────────────────────────────────────────

    @Test
    fun case26_resumeAdoptsAFutureDeadline_andIgnoresAPassedOne() = restTimerTest { bed ->
        bed.openGate()

        // A passed deadline is a no-op — the caller clears its stale stored value.
        bed.timer.resume(T0_MILLIS - 1_000, info)
        bed.timer.awaitPending()
        assertEquals(RestTimerState.Idle(90), bed.timer.state.value)
        assertTrue(bed.presenter.log.isEmpty())

        val end = T0_MILLIS + 42_500
        bed.timer.resume(end, info)
        bed.timer.awaitPending()

        // ceil(42_500 / 1000) = 43, and restStarted reports the CONFIGURED total.
        assertEquals(RestTimerState.Running(43, end), bed.timer.state.value)
        assertEquals(listOf("restStarted(total=90,end=$end)"), bed.presenter.log)
        assertEquals(info, bed.timer.currentInfo())

        bed.clock.nowMillis += 500
        advanceTimeBy(500)
        runCurrent()
        assertEquals(RestTimerState.Running(42, end), bed.timer.state.value)
    }
}
