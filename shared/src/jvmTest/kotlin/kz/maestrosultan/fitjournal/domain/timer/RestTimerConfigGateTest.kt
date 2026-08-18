@file:OptIn(ExperimentalCoroutinesApi::class)

package kz.maestrosultan.fitjournal.domain.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent

/**
 * Spec §13 cases 41-43 — the config gate (§12.16): the engine applies NO command until a
 * config has been applied, so a cold-started app can never rest for the compile-time default
 * 90 s and can never lose a persisted `autoStart`. These are the only engine cases that do
 * **not** open the gate first, and they never `awaitPending()` while it is shut — the barrier
 * is a lane command like any other, so it cannot complete before the gate opens.
 */
class RestTimerConfigGateTest {

    // ─── 41 ───────────────────────────────────────────────────────────────

    @Test
    fun case41_coldStart_startBeforeConfig_restsForThePersistedDuration() = restTimerTest { bed ->
        bed.timer.start()
        runCurrent()

        // Gate shut: the placeholder Idle is published (an idle card that corrects itself),
        // and nothing at all has been applied.
        assertEquals(RestTimerState.Idle(90), bed.timer.state.value)
        assertTrue(bed.presenter.log.isEmpty())
        assertFalse(bed.timer.isRunning)

        bed.timer.applyConfig(RestTimerConfig(durationSeconds = 120, autoStart = true))
        bed.timer.awaitPending()

        // Config is applied first, so the queued start rests for 120 s. A 90 s rest is a bug.
        val end = T0_MILLIS + 120_000
        assertEquals(RestTimerState.Running(120, end), bed.timer.state.value)
        assertEquals(listOf("restStarted(total=120,end=$end)"), bed.presenter.log)
        assertEquals(120, bed.timer.config.durationSeconds)
    }

    // ─── 42 ───────────────────────────────────────────────────────────────

    @Test
    fun case42_coldStart_autoStartBeforeConfig_honoursThePersistedAutoStart() = restTimerTest { bed ->
        bed.timer.autoStart()
        runCurrent()
        assertEquals(RestTimerState.Idle(90), bed.timer.state.value)
        assertTrue(bed.presenter.log.isEmpty())

        bed.timer.applyConfig(RestTimerConfig(durationSeconds = 150, autoStart = true))
        bed.timer.awaitPending()

        val end = T0_MILLIS + 150_000
        assertEquals(RestTimerState.Running(150, end), bed.timer.state.value)
        assertEquals(listOf("restStarted(total=150,end=$end)"), bed.presenter.log)
    }

    @Test
    fun case42_coldStart_autoStartBeforeConfig_withAutoStartOff_isACompleteNoOp() = restTimerTest { bed ->
        bed.timer.autoStart()
        runCurrent()

        bed.timer.applyConfig(RestTimerConfig(durationSeconds = 150, autoStart = false))
        bed.timer.awaitPending()

        assertEquals(RestTimerState.Idle(150), bed.timer.state.value)
        assertFalse(bed.timer.isRunning)
        assertTrue(bed.presenter.log.isEmpty())
    }

    // ─── 43 ───────────────────────────────────────────────────────────────

    @Test
    fun case43_configSavedAfterTheGateOpened_travelsTheLaneLikeAnyCommand() = restTimerTest { bed ->
        bed.openGate(RestTimerConfig(durationSeconds = 60))
        bed.timer.awaitPending()
        assertEquals(RestTimerState.Idle(60), bed.timer.state.value)

        bed.timer.start()
        bed.timer.awaitPending()
        val end = T0_MILLIS + 60_000

        // Second call: an ordinary Cmd.Apply. It never yanks the running rest…
        bed.timer.applyConfig(RestTimerConfig(durationSeconds = 100, soundAndVibrationOn = true))
        bed.timer.awaitPending()
        assertEquals(RestTimerState.Running(60, end), bed.timer.state.value)
        // …but it was not swallowed either.
        assertEquals(100, bed.timer.config.durationSeconds)
        assertTrue(bed.timer.config.soundAndVibrationOn)

        bed.timer.stop()
        bed.timer.awaitPending()
        assertEquals(RestTimerState.Idle(100), bed.timer.state.value)

        // While idle it refreshes the displayed duration, still without a presenter call.
        bed.timer.applyConfig(RestTimerConfig(durationSeconds = 30))
        bed.timer.awaitPending()
        assertEquals(RestTimerState.Idle(30), bed.timer.state.value)
        assertEquals(2, bed.presenter.log.size)
    }
}
