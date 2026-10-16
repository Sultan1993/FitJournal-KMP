package kz.maestrosultan.fitjournal.analytics

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.Collections
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.data.exercise.datasource.ExercisesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.datasource.CategoriesDBDataSource
import kz.maestrosultan.fitjournal.data.exercise.mapper.ExerciseDBMapper
import kz.maestrosultan.fitjournal.data.newTestDb
import kz.maestrosultan.fitjournal.data.record.datasource.WorkoutsDBDataSource
import kz.maestrosultan.fitjournal.data.record.repository.DefaultRecordRepository
import kz.maestrosultan.fitjournal.data.testExerciseMapper
import kz.maestrosultan.fitjournal.domain.quota.FreeQuotaSettings
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuota
import kz.maestrosultan.fitjournal.domain.quota.WorkoutQuotaGate
import kz.maestrosultan.fitjournal.domain.user.LengthMeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.user.UserSession
import kz.maestrosultan.fitjournal.domain.user.UserSessionState

/**
 * The runtime half of the analytics contract: [Analytics] is inert until
 * installed and can never throw, user properties follow the account, and the
 * paywall attribution binds only to a paywall that was actually presented.
 *
 * [Analytics], [PaywallAttribution], [UserSession] and [FreeQuotaSettings] are
 * process-wide objects and jvmTest runs every suite in one JVM, so each is reset
 * on both sides of every test.
 */
class AnalyticsHarnessTest {

    @BeforeTest
    fun resetBefore() = resetGlobals()

    @AfterTest
    fun resetAfter() = resetGlobals()

    private fun resetGlobals() {
        Analytics.reset()
        UserSession.clear()
        FreeQuotaSettings.reset()
    }

    @Test
    fun analyticsIsNoOpBeforeInstall() {
        exerciseEveryCall()

        val sink = RecordingSink()
        Analytics.install(sink)
        assertTrue(sink.calls.isEmpty(), "nothing is buffered before install: ${sink.calls}")

        Analytics.track(AnalyticsEvent.SignOut)
        assertEquals(listOf("logEvent:sign_out:{}"), sink.calls)
    }

    @Test
    fun trackerNeverThrows_whenSinkThrows() {
        Analytics.install(ThrowingSink())

        exerciseEveryCall()

        // State that lives outside the sink still moves even though every sink call threw.
        Analytics.paywallShown(PaywallEntry.HOME_CARD, "paywall_no_trial")
        assertEquals(PaywallEntry.HOME_CARD, PaywallAttribution.presented)
    }

    @Test
    fun screenView_isTrackedAsScreenViewEvent() {
        val sink = RecordingSink()
        Analytics.install(sink)

        ScreenName.entries.forEach(Analytics::screenView)

        assertEquals(
            ScreenName.entries.map { "logEvent:screen_view:{screen_name=${it.value}}" },
            sink.calls,
        )
        assertEquals(
            AnalyticsEvent.ScreenView(ScreenName.HOME).params,
            mapOf<String, Any>("screen_name" to "home"),
        )
    }

    @Test
    fun userPropertiesSync_mapsQuotaToVocabulary(): Unit = runBlocking {
        // Pure mapping: the four sub_tier cases and the four quota states.
        assertEquals(QuotaProperties("entitled", "unlimited", null), QuotaProperties.of(WorkoutQuota.Unlimited, null, true))
        assertEquals(QuotaProperties("lapsed", "lapsed", "47"), QuotaProperties.of(WorkoutQuota.Lapsed(47), true, false))
        assertEquals(QuotaProperties("free", "metered", "3"), QuotaProperties.of(WorkoutQuota.Metered(3, 10), false, false))
        assertEquals(QuotaProperties("free", "exhausted", "10"), QuotaProperties.of(WorkoutQuota.Metered(10, 10), false, null))
        assertEquals(QuotaProperties("unknown", "unlimited", null), QuotaProperties.of(WorkoutQuota.Unlimited, null, null))
        // A former subscriber whose current entitlement is not resolved yet is not "lapsed".
        assertEquals("unknown", QuotaProperties.of(WorkoutQuota.Unlimited, true, null).subTier)

        // End to end: the real gate over real SQL, driven through UserSession + FreeQuotaSettings.
        val db = newTestDb()
        val categories = CategoriesDBDataSource(db.categoryQueries)
        val exercises = ExercisesDBDataSource(db.exercisesQueries, ExerciseDBMapper(categories))
        val workouts = WorkoutsDBDataSource(db.workoutRecordsQueries, db.workoutExercisesQueries, db.workoutSetsQueries)
        val gate = WorkoutQuotaGate(DefaultRecordRepository(workouts, exercises, testExerciseMapper))
        fun seedWorkouts(days: IntRange) = days.forEach { day ->
            db.workoutRecordsQueries.createWorkoutRecord(
                uuid = UUID.randomUUID().toString(), remoteId = null, userId = USER, journalId = "j1",
                date = LocalDate(2026, 9, day).toString(), position = day.toLong(), comment = null,
                startedAt = null, durationSec = null, pendingUpload = true,
                createdDate = SEEDED_AT, updatedDate = SEEDED_AT,
                workoutNumber = 1,
            )
        }

        val recorder = RecordingTracker()
        val second = RecordingTracker()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            UserSession.set(UserSessionState(USER, "j1", MeasurementSystem.KG_KM, LengthMeasurementSystem.CENTIMETERS))
            AnalyticsUserPropertiesSync.start(gate, recorder, scope)
            AnalyticsUserPropertiesSync.start(gate, second, scope) // idempotent: already running

            recorder.awaitProperties("unknown", "unlimited", null)

            FreeQuotaSettings.setEntitled(true)
            recorder.awaitProperties("entitled", "unlimited", null)

            FreeQuotaSettings.reset()
            seedWorkouts(1..3)
            FreeQuotaSettings.setLimit(10)
            FreeQuotaSettings.setHasEverSubscribed(false)
            FreeQuotaSettings.setEntitled(false)
            recorder.awaitProperties("free", "metered", "3")

            seedWorkouts(4..10)
            recorder.awaitProperties("free", "exhausted", "10")

            FreeQuotaSettings.setHasEverSubscribed(true)
            recorder.awaitProperties("lapsed", "lapsed", "10")

            // Logout: a late emission from the outgoing session, then the session clears.
            // Whatever the order, the last write for every ACCOUNT property is null.
            FreeQuotaSettings.setEntitled(true)
            UserSession.clear()
            Analytics.clearUser()
            recorder.awaitProperties(null, null, null)
            // Settings changes after logout must not re-populate them.
            FreeQuotaSettings.reset()
            FreeQuotaSettings.setEntitled(true)
            delay(100)
            assertEquals(
                mapOf(UserProperty.SUB_TIER to null, UserProperty.QUOTA_STATE to null, UserProperty.WORKOUTS_USED to null),
                synchronized(recorder.properties) { recorder.properties.toMap() },
            )

            assertTrue(second.properties.isEmpty(), "a second start must not start a second sync")
            val vocab = AnalyticsCatalog.userProperties.associate { it.property to it.vocab }
            assertTrue(vocab.getValue(UserProperty.SUB_TIER)!!.containsAll(listOf("entitled", "lapsed", "free", "unknown")))
            assertTrue(vocab.getValue(UserProperty.QUOTA_STATE)!!.containsAll(listOf("unlimited", "metered", "exhausted", "lapsed")))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun clearUser_preservesProcessScopedProperties() {
        val sink = RecordingSink()
        Analytics.install(sink)
        Analytics.setUserProperty(UserProperty.APP_BUILD, "release")

        // Sign in, account properties land, a paywall is up.
        Analytics.setUserId("uid-a")
        Analytics.setUserProperty(UserProperty.SUB_TIER, "free")
        Analytics.setUserProperty(UserProperty.QUOTA_STATE, "metered")
        Analytics.setUserProperty(UserProperty.WORKOUTS_USED, "3")
        Analytics.paywallShown(PaywallEntry.HOME_CARD, "paywall_no_trial")

        // Logout.
        Analytics.track(AnalyticsEvent.SignOut)
        Analytics.clearUser()
        assertNull(sink.currentUserId)
        assertEquals(
            mapOf("app_build" to "release", "sub_tier" to null, "quota_state" to null, "workouts_used" to null),
            sink.properties,
        )
        assertNull(PaywallAttribution.presented)
        assertEquals(1, sink.calls.count { it.startsWith("setUserProperty:app_build") }, "clearUser never touches app_build")

        // Sign in again as someone else.
        Analytics.setUserId("uid-b")
        Analytics.setUserProperty(UserProperty.SUB_TIER, "entitled")
        Analytics.setUserProperty(UserProperty.QUOTA_STATE, "unlimited")
        assertEquals("uid-b", sink.currentUserId)
        assertEquals("release", sink.properties["app_build"])
        assertEquals("entitled", sink.properties["sub_tier"])
    }

    @Test
    fun paywallAttribution_bindsToPresentedPaywall() {
        val sink = RecordingSink()
        Analytics.install(sink)

        Analytics.paywallRequested(PaywallEntry.IMPORT)
        assertNull(PaywallAttribution.presented, "a request presents nothing")
        Analytics.paywallSkipped(PaywallEntry.IMPORT, "no_rule_match")
        assertNull(PaywallAttribution.presented, "a skip presents nothing")

        Analytics.paywallRequested(PaywallEntry.LIST_CARD)
        Analytics.paywallShown(PaywallEntry.LIST_CARD, "paywall_no_trial")
        assertEquals(PaywallEntry.LIST_CARD, PaywallAttribution.presented)

        // A second request/skip while one is on screen does not steal the attribution.
        Analytics.paywallRequested(PaywallEntry.WORKOUT_SCREEN)
        Analytics.paywallSkipped(PaywallEntry.WORKOUT_SCREEN, "holdout")
        assertEquals(PaywallEntry.LIST_CARD, PaywallAttribution.presented)

        Analytics.paywallDismissed()
        assertNull(PaywallAttribution.presented)

        assertEquals(
            listOf(
                "logEvent:paywall_requested:{entry=import}",
                "logEvent:paywall_skipped:{entry=import, reason=no_rule_match}",
                "logEvent:paywall_requested:{entry=list_card}",
                "logEvent:paywall_shown:{entry=list_card, placement=paywall_no_trial}",
                "logEvent:paywall_requested:{entry=workout_screen}",
                "logEvent:paywall_skipped:{entry=workout_screen, reason=holdout}",
            ),
            sink.calls,
            "dismiss logs nothing",
        )
    }

    /**
     * [TrackScreenView]: one `screen_view` per ON_START (the synthetic one on
     * entering composition included), none on recomposition, none on ON_RESUME.
     */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun trackScreenView_firesOnEveryStart_neverOnRecomposition() = runComposeUiTest {
        val owner = TestLifecycleOwner()
        owner.registry.currentState = Lifecycle.State.RESUMED
        val recorder = RecordingTracker()
        var tick by mutableIntStateOf(0)

        setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                TrackScreenView(ScreenName.WORKOUT, recorder)
                BasicText("tick $tick")
            }
        }
        waitForIdle()
        assertEquals(listOf(ScreenName.WORKOUT), recorder.screens.toList(), "entering composition while started")

        repeat(3) { tick++; waitForIdle() }
        assertEquals(1, recorder.screens.size, "recomposition never logs")

        runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED } // e.g. a full-screen modal covers it
        runOnIdle { owner.registry.currentState = Lifecycle.State.STARTED } // and it is dismissed
        runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        waitForIdle()
        assertEquals(listOf(ScreenName.WORKOUT, ScreenName.WORKOUT), recorder.screens.toList())
        assertEquals(recorder.screens.map { AnalyticsEvent.ScreenView(it) }, recorder.events.toList())
    }

    /** Every public [Analytics] entry point once. */
    private fun exerciseEveryCall() {
        AnalyticsCatalog.samples().forEach(Analytics::track)
        Analytics.screenView(ScreenName.HOME)
        Analytics.setUserProperty(UserProperty.APP_BUILD, "debug")
        Analytics.setUserId("uid")
        Analytics.paywallRequested(PaywallEntry.HOME_CARD)
        Analytics.paywallShown(PaywallEntry.HOME_CARD, "paywall_no_trial")
        Analytics.paywallSkipped(PaywallEntry.IMPORT, "no_rule_match")
        Analytics.paywallDismissed()
        Analytics.clearUser()
    }

    private suspend fun RecordingTracker.awaitProperties(subTier: String?, quotaState: String?, workoutsUsed: String?) {
        val expected = mapOf(
            UserProperty.SUB_TIER to subTier,
            UserProperty.QUOTA_STATE to quotaState,
            UserProperty.WORKOUTS_USED to workoutsUsed,
        )
        val reached = withTimeoutOrNull(5_000) {
            while (synchronized(properties) { properties.toMap() } != expected) delay(10)
            true
        }
        assertEquals(expected, synchronized(properties) { properties.toMap() }, "properties never reached $expected")
        assertTrue(reached == true)
    }

    private class RecordingSink : AnalyticsSink {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        var currentUserId: String? = null
        val properties = mutableMapOf<String, String?>()

        override fun logEvent(name: String, params: Map<String, Any>) {
            calls += "logEvent:$name:$params"
        }

        override fun setUserId(userId: String?) {
            calls += "setUserId:$userId"
            currentUserId = userId
        }

        override fun setUserProperty(name: String, value: String?) {
            calls += "setUserProperty:$name:$value"
            properties[name] = value
        }
    }

    private class ThrowingSink : AnalyticsSink {
        override fun logEvent(name: String, params: Map<String, Any>) = throw IllegalStateException("logEvent")
        override fun setUserId(userId: String?) = throw IllegalStateException("setUserId")
        override fun setUserProperty(name: String, value: String?) = throw IllegalStateException("setUserProperty")
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private companion object {
        const val USER = "user-1"
        const val SEEDED_AT = "2026-09-01T10:00:00Z"
    }

    @Test
    fun skipReason_isNormalizedToOneVocabularyAcrossPlatforms() {
        assertEquals("no_audience_match", skipReasonWire("noAudienceMatch"))     // iOS case name
        assertEquals("no_audience_match", skipReasonWire("NoAudienceMatch"))     // Android class simpleName
        assertEquals("no_rule_match", skipReasonWire("no_rule_match"))           // already wire form
        assertEquals("holdout", skipReasonWire("Holdout"))
    }
}
