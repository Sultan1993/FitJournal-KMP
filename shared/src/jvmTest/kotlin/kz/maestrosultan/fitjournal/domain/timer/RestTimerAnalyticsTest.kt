@file:OptIn(ExperimentalCoroutinesApi::class)

package kz.maestrosultan.fitjournal.domain.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kz.maestrosultan.fitjournal.analytics.AnalyticsEvent
import kz.maestrosultan.fitjournal.analytics.RecordingTracker
import kz.maestrosultan.fitjournal.analytics.RestOutcome
import kz.maestrosultan.fitjournal.analytics.RestTrigger

/**
 * `rest_timer_started` / `rest_timer_ended` accounting inside the lane: one `ended` per
 * `started` within a process, and a resumed rest (start moment and trigger unknown)
 * tracks nothing at all. Same fake clock + steppable lane as the engine suites.
 */
class RestTimerAnalyticsTest {

    private class Bed(scheduler: TestCoroutineScheduler, startMillis: Long) {
        val presenter = FakeRestTimerPresenter()
        val clock = FakeClock(startMillis)
        val tracker = RecordingTracker()
        val scope = CoroutineScope(StandardTestDispatcher(scheduler))
        val timer = RestTimer(presenter = presenter, scope = scope, clock = clock, tracker = tracker)
    }

    private fun analyticsTest(body: suspend TestScope.(Bed) -> Unit) = runTest {
        val bed = Bed(testScheduler, T0_MILLIS)
        try {
            bed.timer.applyConfig(RestTimerConfig(durationSeconds = 90, autoStart = true))
            body(this, bed)
        } finally {
            bed.scope.cancel()
        }
    }

    @Test
    fun start_tracksTrigger_manualVsAuto() = analyticsTest { bed ->
        bed.timer.start()
        bed.timer.awaitPending()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.RestTimerStarted(90, RestTrigger.MANUAL)), bed.tracker.events.toList())

        bed.timer.stop()
        bed.timer.autoStart()
        bed.timer.awaitPending()
        assertEquals(AnalyticsEvent.RestTimerStarted(90, RestTrigger.AUTO), bed.tracker.events.last())

        bed.timer.stop()
        bed.timer.toggle()
        bed.timer.awaitPending()
        assertEquals(AnalyticsEvent.RestTimerStarted(90, RestTrigger.MANUAL), bed.tracker.events.last())
    }

    @Test
    fun stop_tracksEnded_stopped_withElapsed() = analyticsTest { bed ->
        bed.timer.autoStart()
        bed.timer.awaitPending()
        bed.clock.nowMillis += 40_400
        bed.timer.stop()
        bed.timer.awaitPending()

        assertEquals(
            AnalyticsEvent.RestTimerEnded(RestOutcome.STOPPED, elapsedSec = 40, durationSec = 90, trigger = RestTrigger.AUTO),
            bed.tracker.events.last(),
        )
        assertEquals(2, bed.tracker.events.size, "started + ended")

        // Stopping an idle timer ends nothing twice.
        bed.timer.stop()
        bed.timer.awaitPending()
        assertEquals(2, bed.tracker.events.size)
    }

    @Test
    fun expiry_tracksEnded_finished() = analyticsTest { bed ->
        bed.timer.start()
        bed.timer.awaitPending()
        bed.clock.nowMillis += 90_000
        advanceTimeBy(90_000)
        runCurrent()

        assertEquals(
            AnalyticsEvent.RestTimerEnded(RestOutcome.FINISHED, elapsedSec = 90, durationSec = 90, trigger = RestTrigger.MANUAL),
            bed.tracker.events.last(),
        )
        assertEquals(2, bed.tracker.events.size)
    }

    @Test
    fun startWhileRunning_tracksEnded_restarted_thenStarted() = analyticsTest { bed ->
        bed.timer.start()
        bed.timer.awaitPending()
        bed.clock.nowMillis += 25_000
        bed.timer.autoStart()
        bed.timer.awaitPending()

        assertEquals(
            listOf<AnalyticsEvent>(
                AnalyticsEvent.RestTimerStarted(90, RestTrigger.MANUAL),
                AnalyticsEvent.RestTimerEnded(RestOutcome.RESTARTED, elapsedSec = 25, durationSec = 90, trigger = RestTrigger.MANUAL),
                AnalyticsEvent.RestTimerStarted(90, RestTrigger.AUTO),
            ),
            bed.tracker.events.toList(),
        )
    }

    @Test
    fun resume_tracksNothing_andItsEndTracksNothing() = analyticsTest { bed ->
        bed.timer.resume(T0_MILLIS + 30_000)
        bed.timer.awaitPending()
        assertTrue(bed.tracker.events.isEmpty(), "resume adopts a rest; it is not a start")

        bed.clock.nowMillis += 10_000
        bed.timer.stop()
        bed.timer.awaitPending()
        assertTrue(bed.tracker.events.isEmpty(), "stopping a resumed rest has no known start or trigger")

        bed.timer.resume(bed.clock.nowMillis + 30_000)
        bed.timer.awaitPending()
        bed.clock.nowMillis += 30_000
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(bed.tracker.events.isEmpty(), "expiry of a resumed rest tracks nothing")

        // A fresh start after a resumed rest is accounted normally again.
        bed.timer.start()
        bed.timer.awaitPending()
        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.RestTimerStarted(90, RestTrigger.MANUAL)), bed.tracker.events.toList())
    }
}
