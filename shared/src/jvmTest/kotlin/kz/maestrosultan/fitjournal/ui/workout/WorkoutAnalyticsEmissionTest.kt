package kz.maestrosultan.fitjournal.ui.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.analytics.AnalyticsEvent
import kz.maestrosultan.fitjournal.analytics.FocusEntry
import kz.maestrosultan.fitjournal.analytics.RecordingTracker
import kz.maestrosultan.fitjournal.analytics.RemoveEntry
import kz.maestrosultan.fitjournal.analytics.SessionEndVia
import kz.maestrosultan.fitjournal.analytics.SessionState
import kz.maestrosultan.fitjournal.analytics.SetAction
import kz.maestrosultan.fitjournal.analytics.SetInput
import kz.maestrosultan.fitjournal.analytics.WorkoutEntry
import kz.maestrosultan.fitjournal.analytics.dayContextOf
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate
import kz.maestrosultan.fitjournal.domain.user.LengthMeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.UserSessionState
import kz.maestrosultan.fitjournal.domain.workout.LastOccurrence
import kz.maestrosultan.fitjournal.domain.workout.StartedSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutExercise
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSessionRepository
import kz.maestrosultan.fitjournal.domain.workout.usecase.EndWorkoutUseCase
import kz.maestrosultan.fitjournal.domain.workout.usecase.StartWorkoutUseCase
import kz.maestrosultan.fitjournal.ui.workout.focus.FOCUS_DATE
import kz.maestrosultan.fitjournal.ui.workout.focus.FOCUS_JOURNAL_ID
import kz.maestrosultan.fitjournal.ui.workout.focus.FOCUS_USER_ID
import kz.maestrosultan.fitjournal.ui.workout.focus.FakeFocusSyncTrigger
import kz.maestrosultan.fitjournal.ui.workout.focus.FocusBed
import kz.maestrosultan.fitjournal.ui.workout.focus.RecordingRecordRepository
import kz.maestrosultan.fitjournal.ui.workout.focus.WorkoutFocusContract
import kz.maestrosultan.fitjournal.ui.workout.focus.awaitLoaded
import kz.maestrosultan.fitjournal.ui.workout.focus.focusCatalog
import kz.maestrosultan.fitjournal.ui.workout.focus.focusMember
import kz.maestrosultan.fitjournal.ui.workout.focus.focusRecord
import kz.maestrosultan.fitjournal.ui.workout.focus.focusSession
import kz.maestrosultan.fitjournal.ui.workout.focus.focusSet
import kz.maestrosultan.fitjournal.ui.workout.focus.focusTest
import kz.maestrosultan.fitjournal.ui.workout.main.WorkoutContract
import kz.maestrosultan.fitjournal.ui.workout.main.WorkoutViewModel

/**
 * VM-level emissions of the two shared workout ViewModels: what `WorkoutViewModel`
 * and `WorkoutFocusViewModel` hand the tracker, driven through `dispatch` only.
 * The use-case-level emissions (`set_logged` shape, `set_deleted`, session
 * start/end counts) are `AnalyticsEmissionTest`'s; this file pins what only the
 * VMs know — which open, which provenance, which slot, and whether the
 * repository said the write happened.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutAnalyticsEmissionTest {

    // ── workout_opened ──────────────────────────────────────────────────

    @Test
    fun workoutOpened_firesOncePerOpen_andOnDateChangeAsCalendar() = workoutTest(
        records = listOf(workoutRecord("r1", TODAY, 1)),
    ) { h ->
        val vm = h.viewModel(TODAY, WorkoutEntry.HOME_START)
        advanceUntilIdle()
        assertEquals(listOf(opened(TODAY, 1, WorkoutEntry.HOME_START, SessionState.NONE)), h.opens())

        // Re-selecting the shown day and a fresh state for it (a session write
        // re-emits the day) are not opens.
        vm.dispatch(WorkoutContract.ViewAction.SelectDate(TODAY))
        h.sessions.all.value = listOf(session("other", OTHER_PAST, 2, endedAt = NOW))
        advanceUntilIdle()
        assertEquals(1, h.opens().size, "same-date re-select and record/session changes emit nothing: ${h.opens()}")

        vm.dispatch(WorkoutContract.ViewAction.SelectDate(PAST))
        advanceUntilIdle()
        assertEquals(
            listOf(
                opened(TODAY, 1, WorkoutEntry.HOME_START, SessionState.NONE),
                opened(PAST, 1, WorkoutEntry.CALENDAR, SessionState.NONE),
            ),
            h.opens(),
        )
        assertEquals(dayContextOf(PAST, TODAY), h.opens().last().day, "past day carries its signed offset")
    }

    @Test
    fun workoutOpened_emitsEveryRequest_whenSelectionsOutrunTheRead() = workoutTest(
        sessions = listOf(
            session("running", TODAY, 1, endedAt = null),
            session("done", PAST, 1, endedAt = NOW),
        ),
    ) { h ->
        h.repository.readGate = CompletableDeferred()
        val vm = h.viewModel(TODAY, WorkoutEntry.HOME_START)
        advanceUntilIdle()
        vm.dispatch(WorkoutContract.ViewAction.SelectDate(PAST))
        vm.dispatch(WorkoutContract.ViewAction.SelectDate(OTHER_PAST))
        advanceUntilIdle()
        assertTrue(h.opens().isEmpty(), "nothing emits before a read completes: ${h.opens()}")

        h.repository.readGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf(
                // Superseded: page 0 by construction, state from the session table.
                opened(TODAY, 1, WorkoutEntry.HOME_START, SessionState.RUNNING),
                opened(PAST, 1, WorkoutEntry.CALENDAR, SessionState.FINISHED),
                // The shown day: from its page.
                opened(OTHER_PAST, 1, WorkoutEntry.CALENDAR, SessionState.NONE),
            ),
            h.opens(),
        )
    }

    @Test
    fun workoutOpened_supersededOpen_isNone_whenTheSessionReadThrows() = workoutTest(
        sessions = listOf(session("done", PAST, 1, endedAt = NOW)),
    ) { h ->
        h.repository.readGate = CompletableDeferred()
        h.sessions.failSessionReads = true
        val vm = h.viewModel(PAST, WorkoutEntry.DETAILS_EDIT)
        advanceUntilIdle()
        vm.dispatch(WorkoutContract.ViewAction.SelectDate(TODAY))
        advanceUntilIdle()
        h.repository.readGate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf(
                opened(PAST, 1, WorkoutEntry.DETAILS_EDIT, SessionState.NONE),
                opened(TODAY, 1, WorkoutEntry.CALENDAR, SessionState.NONE),
            ),
            h.opens(),
            "a failed read labels the open none instead of dropping it",
        )
    }

    @Test
    fun workoutOpened_sessionState_none_running_finished() = workoutTest(
        sessions = listOf(
            session("running", TODAY, 1, endedAt = null),
            session("done", PAST, 1, endedAt = NOW),
        ),
    ) { h ->
        val vm = h.viewModel(TODAY, WorkoutEntry.HOME_START)
        advanceUntilIdle()
        vm.dispatch(WorkoutContract.ViewAction.SelectDate(PAST))
        advanceUntilIdle()
        vm.dispatch(WorkoutContract.ViewAction.SelectDate(OTHER_PAST))
        advanceUntilIdle()

        assertEquals(
            listOf(SessionState.RUNNING, SessionState.FINISHED, SessionState.NONE),
            h.opens().map { it.sessionState },
        )
    }

    @Test
    fun workoutOpened_sessionState_isScopedToTheOpenedSlot() = workoutTest(
        records = listOf(workoutRecord("w1", TODAY, 1), workoutRecord("w2", TODAY, 2)),
        sessions = listOf(
            session("running", TODAY, 1, endedAt = null),
            session("done", PAST, 1, endedAt = NOW),
        ),
    ) { h ->
        // A finished past workout opened while today's session runs reads finished.
        h.viewModel(PAST, WorkoutEntry.DETAILS_EDIT)
        // The second workout of the running day is not the running one.
        h.viewModel(TODAY, WorkoutEntry.DETAILS_EDIT, initialWorkoutNumber = 2)
        advanceUntilIdle()

        assertEquals(
            setOf(
                opened(PAST, 1, WorkoutEntry.DETAILS_EDIT, SessionState.FINISHED),
                opened(TODAY, 2, WorkoutEntry.DETAILS_EDIT, SessionState.NONE),
            ),
            h.opens().toSet(),
        )
        assertEquals(2, h.opens().size)
    }

    // ── discard-empty workout_session_end ──────────────────────────────

    @Test
    fun discardEmpty_emitsSessionEnd_withZeroCounts_once() = workoutTest(
        sessions = listOf(session("running", TODAY, 1, endedAt = null)),
    ) { h ->
        val vm = h.viewModel(TODAY, WorkoutEntry.HOME_START)
        advanceUntilIdle()

        vm.dispatch(WorkoutContract.ViewAction.RequestEndSession)
        vm.dispatch(WorkoutContract.ViewAction.RequestEndSession)
        advanceUntilIdle()

        assertEquals(listOf("deleteSession(running)"), h.sessions.calls.filter { it.startsWith("deleteSession") })
        assertEquals(
            listOf(
                AnalyticsEvent.WorkoutSessionEnd(
                    journalId = FOCUS_JOURNAL_ID,
                    workoutDate = TODAY,
                    workoutNumber = 1,
                    durationSec = 1800,
                    via = SessionEndVia.DISCARD_EMPTY,
                    day = dayContextOf(TODAY, TODAY),
                    exerciseCount = 0,
                    setCount = 0,
                ),
            ),
            h.tracker.events.filterIsInstance<AnalyticsEvent.WorkoutSessionEnd>(),
        )
    }

    // ── focus_opened ────────────────────────────────────────────────────

    @Test
    fun focusOpened_firesOnLoad_andOnFinishExerciseAdvance() = focusTest(
        listOf(
            focusRecord("r1", 0, listOf(focusMember("we-1", focusCatalog("Bench")))),
            focusRecord("r2", 1, listOf(focusMember("we-2", focusCatalog("Row")))),
        ),
    ) { bed ->
        val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        vm.awaitLoaded()
        vm.dispatch(WorkoutFocusContract.ViewAction.TogglePicker)
        vm.dispatch(WorkoutFocusContract.ViewAction.TogglePicker)
        // A picker switch there and back is not an open.
        vm.dispatch(WorkoutFocusContract.ViewAction.SelectRecord("r2"))
        advanceUntilIdle()
        vm.dispatch(WorkoutFocusContract.ViewAction.SelectRecord("r1"))
        advanceUntilIdle()
        assertEquals(listOf(focusOpened("r1", "ex-Bench", FocusEntry.ROW_TAP)), bed.focusOpens())

        vm.dispatch(WorkoutFocusContract.ViewAction.FinishExercise)
        advanceUntilIdle()
        // On the last record Finish leaves Focus — no open.
        vm.dispatch(WorkoutFocusContract.ViewAction.FinishExercise)
        advanceUntilIdle()
        assertEquals(
            listOf(
                focusOpened("r1", "ex-Bench", FocusEntry.ROW_TAP),
                focusOpened("r2", "ex-Row", FocusEntry.NEXT_EXERCISE),
            ),
            bed.focusOpens(),
        )

        bed.tracker.events.clear()
        bed.viewModel(recordId = "r2", exerciseId = "we-2", startAddingSet = true).awaitLoaded()
        advanceUntilIdle()
        assertEquals(listOf(focusOpened("r2", "ex-Row", FocusEntry.ADD_SET)), bed.focusOpens())
        assertTrue(bed.focusOpens().single().startAddingSet)
    }

    // ── set_logged provenance ───────────────────────────────────────────

    @Test
    fun setInput_typedWhenEdited_elseSeedKind() = focusTest(
        listOf(
            focusRecord(
                "rA",
                0,
                listOf(focusMember("we-a", focusCatalog("A"), listOf(focusSet("a1", 80.0, 7), focusSet("a2")))),
            ),
            focusRecord("rB", 1, listOf(focusMember("we-b", focusCatalog("B")))),
            focusRecord(
                "rC",
                2,
                listOf(
                    focusMember("we-c", focusCatalog("C"), listOf(focusSet("c1"), focusSet("c2")))
                        .copy(lastOccurrence = PRIOR_SESSION),
                ),
            ),
        ),
    ) { bed ->
        val a = bed.viewModel(recordId = "rA", exerciseId = "we-a")
        a.awaitLoaded()

        // Edit an empty target seeded from the row above → prefill_previous_set.
        a.dispatch(WorkoutFocusContract.ViewAction.EditSet("a2"))
        a.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
        advanceUntilIdle()
        assertEquals(SetAction.UPDATE to SetInput.PREFILL_PREVIOUS_SET, bed.lastSetLogged().let { it.action to it.input })

        // Re-save a filled row untouched → unchanged.
        a.dispatch(WorkoutFocusContract.ViewAction.EditSet("a1"))
        a.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
        advanceUntilIdle()
        assertEquals(SetInput.UNCHANGED, bed.lastSetLogged().input)

        // Type into it → typed.
        a.dispatch(WorkoutFocusContract.ViewAction.EditSet("a1"))
        a.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("9"))
        a.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
        advanceUntilIdle()
        assertEquals(SetInput.TYPED, bed.lastSetLogged().input)

        // Append from the add-another editor → the last row's numbers.
        a.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
        a.dispatch(WorkoutFocusContract.ViewAction.LogSet)
        advanceUntilIdle()
        assertEquals(SetAction.ADD to SetInput.PREFILL_PREVIOUS_SET, bed.lastSetLogged().let { it.action to it.input })

        // The post-log re-arm keeps it a prefill until the user types.
        a.dispatch(WorkoutFocusContract.ViewAction.LogSet)
        advanceUntilIdle()
        assertEquals(SetInput.PREFILL_PREVIOUS_SET, bed.lastSetLogged().input)
        a.dispatch(WorkoutFocusContract.ViewAction.AddAnotherSet)
        a.dispatch(WorkoutFocusContract.ViewAction.KeypadDigit("5"))
        a.dispatch(WorkoutFocusContract.ViewAction.LogSet)
        advanceUntilIdle()
        assertEquals(SetInput.TYPED, bed.lastSetLogged().input)

        // Nothing to seed from → default.
        val b = bed.viewModel(recordId = "rB", exerciseId = "we-b")
        b.awaitLoaded()
        b.dispatch(WorkoutFocusContract.ViewAction.LogSet)
        advanceUntilIdle()
        assertEquals("ex-B" to SetInput.DEFAULT, bed.lastSetLogged().let { it.exerciseId to it.input })

        // A target carrying last session's set → prefill_last_time.
        val c = bed.viewModel(recordId = "rC", exerciseId = "we-c")
        c.awaitLoaded()
        c.dispatch(WorkoutFocusContract.ViewAction.EditSet("c1"))
        c.dispatch(WorkoutFocusContract.ViewAction.SaveSet)
        advanceUntilIdle()
        assertEquals("ex-C" to SetInput.PREFILL_LAST_TIME, bed.lastSetLogged().let { it.exerciseId to it.input })
    }

    @Test
    fun commitTarget_logsActionCommitTarget_forAllFourSeedSources() = focusTest(
        listOf(
            // OWN: a reps-only target has numbers of its own.
            focusRecord("rOwn", 0, listOf(focusMember("we-own", focusCatalog("Own"), listOf(focusSet("o1", reps = 8))))),
            // LAST_TIME: WorkoutFocusCommitTargetTest's last-session fixture.
            focusRecord(
                "rLast",
                1,
                listOf(
                    focusMember("we-last", focusCatalog("Last"), listOf(focusSet("l1"), focusSet("l2")))
                        .copy(lastOccurrence = PRIOR_SESSION),
                ),
            ),
            // PREVIOUS_SET: oneLoggedOneTarget.
            focusRecord(
                "rPrev",
                2,
                listOf(focusMember("we-prev", focusCatalog("Prev"), listOf(focusSet("p1", 80.0, 7), focusSet("p2")))),
            ),
            // NONE: nothing anywhere.
            focusRecord("rNone", 3, listOf(focusMember("we-none", focusCatalog("None"), listOf(focusSet("n1"))))),
        ),
    ) { bed ->
        val vm = bed.viewModel(recordId = "rOwn", exerciseId = "we-own")
        vm.awaitLoaded()
        listOf("o1", "l2", "p2", "n1").forEach { setId ->
            vm.dispatch(WorkoutFocusContract.ViewAction.CommitTarget(setId))
            advanceUntilIdle()
        }

        val logged = bed.tracker.events.filterIsInstance<AnalyticsEvent.SetLogged>()
        assertTrue(logged.all { it.action == SetAction.COMMIT_TARGET }, "every swipe-commit is commit_target: $logged")
        assertEquals(
            listOf(
                "ex-Own" to SetInput.UNCHANGED,
                "ex-Last" to SetInput.PREFILL_LAST_TIME,
                "ex-Prev" to SetInput.PREFILL_PREVIOUS_SET,
                "ex-None" to SetInput.DEFAULT,
            ),
            logged.map { it.exerciseId to it.input },
        )
    }

    @Test
    fun setLogged_inSession_isScopedToTheRecordsWorkoutNumber() = focusTest(
        listOf(
            focusRecord("r1", 0, listOf(focusMember("we-1", focusCatalog("First")))),
            focusRecord("r2", 1, listOf(focusMember("we-2", focusCatalog("Second")))).copy(workoutNumber = 2),
        ),
    ) { bed ->
        bed.sessions.running = focusSession() // workout 1 of FOCUS_DATE
        val second = bed.viewModel(recordId = "r2", exerciseId = "we-2")
        second.awaitLoaded()
        second.dispatch(WorkoutFocusContract.ViewAction.LogSet)
        advanceUntilIdle()
        assertEquals(2 to false, bed.lastSetLogged().let { it.workoutNumber to it.inSession })

        val first = bed.viewModel(recordId = "r1", exerciseId = "we-1")
        first.awaitLoaded()
        first.dispatch(WorkoutFocusContract.ViewAction.LogSet)
        advanceUntilIdle()
        assertEquals(1 to true, bed.lastSetLogged().let { it.workoutNumber to it.inSession })

        // The session moves to workout 2 of the same day: still "running here",
        // but the observer must re-scope in_session.
        bed.sessions.running = focusSession().copy(workoutNumber = 2)
        advanceUntilIdle()
        second.dispatch(WorkoutFocusContract.ViewAction.LogSet)
        advanceUntilIdle()
        assertEquals(2 to true, bed.lastSetLogged().let { it.workoutNumber to it.inSession })
    }

    // ── exercise_removed ────────────────────────────────────────────────

    @Test
    fun exerciseRemoved_firesPerExercise_whenDeleteRecordReturnsTrue() {
        val superset = workoutRecord(
            "ss",
            TODAY,
            1,
            members = listOf(
                focusMember("m1", focusCatalog("Squat"), listOf(focusSet("s1", 100.0, 5))),
                focusMember("m2", focusCatalog("Lunge"), listOf(focusSet("s2"))),
            ),
        )
        workoutTest(records = listOf(superset)) { h ->
            val vm = h.viewModel(TODAY, WorkoutEntry.HOME_START)
            advanceUntilIdle()
            vm.dispatch(WorkoutContract.ViewAction.DeleteRecord(superset))
            advanceUntilIdle()

            assertEquals(
                listOf(
                    removed("ss", TODAY, "ex-Squat", hadSets = true, RemoveEntry.WORKOUT),
                    removed("ss", TODAY, "ex-Lunge", hadSets = false, RemoveEntry.WORKOUT),
                ),
                h.tracker.events.filterIsInstance<AnalyticsEvent.ExerciseRemoved>(),
            )
        }

        focusTest(
            listOf(
                focusRecord("r1", 0, listOf(focusMember("we-1", focusCatalog("Bench"), listOf(focusSet("b1", 60.0, 8))))),
                focusRecord(
                    "r2",
                    1,
                    listOf(
                        focusMember("we-2", focusCatalog("Curl")),
                        focusMember("we-3", focusCatalog("Dip"), listOf(focusSet("d1", 10.0, 10))),
                    ),
                ),
            ),
        ) { bed ->
            // Single-exercise record → the whole record goes.
            val single = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            single.awaitLoaded()
            single.dispatch(WorkoutFocusContract.ViewAction.RemoveExerciseConfirmed)
            advanceUntilIdle()
            // Superset member → split off, then the split-off record goes.
            val member = bed.viewModel(recordId = "r2", exerciseId = "we-2")
            member.awaitLoaded()
            member.dispatch(WorkoutFocusContract.ViewAction.RemoveExerciseConfirmed)
            advanceUntilIdle()

            assertEquals(
                listOf(
                    removed("r1", FOCUS_DATE, "ex-Bench", hadSets = true, RemoveEntry.FOCUS),
                    removed("r2", FOCUS_DATE, "ex-Curl", hadSets = false, RemoveEntry.FOCUS),
                ),
                bed.tracker.events.filterIsInstance<AnalyticsEvent.ExerciseRemoved>(),
            )
        }
    }

    @Test
    fun exerciseRemoved_skipsWhenDeleteRecordReturnsFalse() {
        val gone = workoutRecord("gone", TODAY, 1)
        workoutTest { h ->
            // The row was already removed (a sync pull) when the delete arrives.
            val vm = h.viewModel(TODAY, WorkoutEntry.HOME_START)
            advanceUntilIdle()
            vm.dispatch(WorkoutContract.ViewAction.DeleteRecord(gone))
            advanceUntilIdle()

            assertEquals(1, h.repository.countOf("deleteRecord("))
            assertTrue(h.tracker.events.none { it is AnalyticsEvent.ExerciseRemoved })
        }

        focusTest(
            listOf(focusRecord("r1", 0, listOf(focusMember("we-1", focusCatalog("Bench"), listOf(focusSet("b1", 60.0, 8)))))),
        ) { bed ->
            val vm = bed.viewModel(recordId = "r1", exerciseId = "we-1")
            vm.awaitLoaded()
            bed.repository.replaceDay(emptyList())
            vm.dispatch(WorkoutFocusContract.ViewAction.RemoveExerciseConfirmed)
            advanceUntilIdle()

            assertEquals(1, bed.repository.countOf("deleteRecord("))
            assertTrue(bed.tracker.events.none { it is AnalyticsEvent.ExerciseRemoved })
        }
    }

    @Test
    fun exerciseRemoved_emitsOnce_forTwoConcurrentDeleteRequests() {
        val record = workoutRecord("r1", TODAY, 1)
        workoutTest(records = listOf(record)) { h ->
            val vm = h.viewModel(TODAY, WorkoutEntry.HOME_START)
            advanceUntilIdle()
            vm.dispatch(WorkoutContract.ViewAction.DeleteRecord(record))
            vm.dispatch(WorkoutContract.ViewAction.DeleteRecord(record))
            advanceUntilIdle()

            assertEquals(2, h.repository.countOf("deleteRecord("), "both requests reach the repository")
            assertEquals(1, h.tracker.events.count { it is AnalyticsEvent.ExerciseRemoved })
            assertEquals(
                2,
                h.sessions.calls.count { it.startsWith("getSessionByWorkoutNumber($TODAY,1)") },
                "pruneEmptiedPage runs for the losing request too",
            )
        }
    }

    // ── Harness ─────────────────────────────────────────────────────────

    private fun opened(date: LocalDate, number: Int, entry: WorkoutEntry, state: SessionState) =
        AnalyticsEvent.WorkoutOpened(
            journalId = FOCUS_JOURNAL_ID,
            workoutDate = date,
            workoutNumber = number,
            day = dayContextOf(date, TODAY),
            entry = entry,
            sessionState = state,
        )

    private fun focusOpened(recordId: String, exerciseId: String, entry: FocusEntry) =
        AnalyticsEvent.FocusOpened(
            journalId = FOCUS_JOURNAL_ID,
            workoutDate = FOCUS_DATE,
            workoutNumber = 1,
            recordId = recordId,
            exerciseId = exerciseId,
            entry = entry,
            startAddingSet = entry == FocusEntry.ADD_SET,
        )

    private fun removed(recordId: String, date: LocalDate, exerciseId: String, hadSets: Boolean, entry: RemoveEntry) =
        AnalyticsEvent.ExerciseRemoved(
            journalId = FOCUS_JOURNAL_ID,
            workoutDate = date,
            workoutNumber = 1,
            recordId = recordId,
            exerciseId = exerciseId,
            hadSets = hadSets,
            entry = entry,
        )

    private fun FocusBed.focusOpens() =
        tracker.events.filterIsInstance<AnalyticsEvent.FocusOpened>()

    private fun FocusBed.lastSetLogged() =
        tracker.events.filterIsInstance<AnalyticsEvent.SetLogged>().last()

    private fun workoutRecord(
        id: String,
        date: LocalDate,
        workoutNumber: Int,
        members: List<WorkoutExercise> =
            listOf(focusMember("$id-m", focusCatalog("Press-$id"), listOf(focusSet("$id-s", 50.0, 5)))),
    ): WorkoutRecord = focusRecord(id, 0, members).copy(date = date, workoutNumber = workoutNumber)

    private fun session(id: String, date: LocalDate, workoutNumber: Int, endedAt: Instant?) = WorkoutSession(
        id = id,
        userId = FOCUS_USER_ID,
        journalId = FOCUS_JOURNAL_ID,
        date = date,
        workoutNumber = workoutNumber,
        startedAt = STARTED,
        endedAt = endedAt,
    )

    private class Harness(records: List<WorkoutRecord>, sessions: List<WorkoutSession>) {
        val repository = RecordingRecordRepository(records)
        val sessions = FakeWorkoutSessions(sessions)
        val syncTrigger = FakeFocusSyncTrigger()
        val tracker = RecordingTracker()
        private val viewModels = mutableListOf<WorkoutViewModel>()

        fun viewModel(date: LocalDate, entry: WorkoutEntry, initialWorkoutNumber: Int? = null) = WorkoutViewModel(
            recordRepository = repository,
            sessionRepository = sessions,
            startWorkout = StartWorkoutUseCase(sessions, syncTrigger, tracker),
            endWorkout = EndWorkoutUseCase(sessions, repository, syncTrigger, tracker),
            syncTrigger = syncTrigger,
            quotaGate = WorkoutQuotaGate(repository),
            awaitSession = {
                UserSessionState(FOCUS_USER_ID, FOCUS_JOURNAL_ID, MeasurementSystem.KG_KM, LengthMeasurementSystem.CENTIMETERS)
            },
            initialDate = date,
            entry = entry,
            initialWorkoutNumber = initialWorkoutNumber,
            clock = FixedClock,
            timeZone = TimeZone.UTC,
            tracker = tracker,
        ).also { viewModels += it }

        fun opens() = tracker.events.filterIsInstance<AnalyticsEvent.WorkoutOpened>()

        fun dispose() = viewModels.forEach { it.dispose() }
    }

    /** `runTest` with `Dispatchers.Main` installed (viewModelScope) and the VMs disposed in a `finally`. */
    private fun workoutTest(
        records: List<WorkoutRecord> = emptyList(),
        sessions: List<WorkoutSession> = emptyList(),
        body: suspend TestScope.(Harness) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val harness = Harness(records, sessions)
        try {
            body(this, harness)
        } finally {
            harness.dispose()
            Dispatchers.resetMain()
        }
    }

    /**
     * Every session of the user in ONE flow, so the day list, the running
     * session and the per-slot read can never disagree.
     */
    private class FakeWorkoutSessions(initial: List<WorkoutSession>) : WorkoutSessionRepository {
        val all = MutableStateFlow(initial)
        val calls = mutableListOf<String>()
        var failSessionReads = false

        override suspend fun getSessionByWorkoutNumber(
            userId: String,
            journalId: String,
            date: LocalDate,
            workoutNumber: Int,
        ): WorkoutSession? {
            calls += "getSessionByWorkoutNumber($date,$workoutNumber)"
            if (failSessionReads) error("session read failed")
            return all.value.firstOrNull { it.journalId == journalId && it.date == date && it.workoutNumber == workoutNumber }
        }

        override suspend fun getSessionsForDay(userId: String, journalId: String, date: LocalDate): List<WorkoutSession> =
            all.value.filter { it.journalId == journalId && it.date == date }

        override fun getSessionsForDayFlow(userId: String, journalId: String, date: LocalDate): Flow<List<WorkoutSession>> =
            all.map { list -> list.filter { it.journalId == journalId && it.date == date } }

        override suspend fun getRunningSession(userId: String): WorkoutSession? = all.value.firstOrNull { it.isRunning }

        override fun getRunningSessionFlow(userId: String): Flow<WorkoutSession?> =
            all.map { list -> list.firstOrNull { it.isRunning } }

        override suspend fun countCompletedSessionsBetween(
            userId: String,
            journalId: String,
            from: LocalDate,
            to: LocalDate,
            excludeSessionUuid: String,
        ): Int = 0

        override suspend fun startSession(userId: String, journalId: String, date: LocalDate, workoutNumber: Int): WorkoutSession =
            unsupported()

        override suspend fun startSessionTracked(userId: String, journalId: String, date: LocalDate, workoutNumber: Int): StartedSession =
            unsupported()

        override suspend fun endSession(userId: String, endedAt: Instant?): WorkoutSession? = unsupported()

        override suspend fun deleteSession(userId: String, sessionUuid: String): Boolean {
            calls += "deleteSession($sessionUuid)"
            val existed = all.value.any { it.id == sessionUuid }
            all.value = all.value.filterNot { it.id == sessionUuid }
            return existed
        }

        override suspend fun deleteUserSessions(userId: String): Unit = unsupported()

        private fun unsupported(): Nothing = throw UnsupportedOperationException("not used by these tests")
    }

    private object FixedClock : Clock {
        override fun now(): Instant = NOW
    }

    private companion object {
        val TODAY = LocalDate(2026, 9, 4)
        val PAST = LocalDate(2026, 9, 1)
        val OTHER_PAST = LocalDate(2026, 9, 2)
        val NOW: Instant = Instant.parse("2026-09-04T10:00:00Z")
        val STARTED: Instant = Instant.parse("2026-09-04T09:30:00Z")

        val PRIOR_SESSION = LastOccurrence(
            date = LocalDate(2026, 3, 7),
            sets = listOf(focusSet("prior-1", 60.0, 12), focusSet("prior-2", 65.0, 9)),
        )
    }
}
