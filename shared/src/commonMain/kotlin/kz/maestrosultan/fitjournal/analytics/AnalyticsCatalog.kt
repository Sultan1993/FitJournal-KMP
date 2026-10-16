package kz.maestrosultan.fitjournal.analytics

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kz.maestrosultan.fitjournal.domain.workout.ResultType

/**
 * Every screen that can be reported. Only [instrumented] screens emit
 * `screen_view` today; the rest are declared so the vocabulary is closed and
 * instrumenting one later is a one-call change, not a schema change.
 */
enum class ScreenName(val value: String, val instrumented: Boolean) {
    START_LOGIN("start_login", true),
    ONBOARDING_UNITS("onboarding_units", true),
    HOME("home", true),
    WORKOUT("workout", true),
    WORKOUT_FOCUS("workout_focus", true),
    WORKOUT_FINISH("workout_finish", true),
    WORKOUT_DETAILS("workout_details", true),
    WORKOUT_LIST("workout_list", true),
    IMPORT_WORKOUT("import_workout", true),
    JOURNALS("journals", false),
    EXERCISES("exercises", false),
    NOTES("notes", false),
    MEASUREMENTS("measurements", false),
    SETTINGS("settings", false),
    CALCULATOR("calculator", false),
    WORKOUT_PLANS("workout_plans", false),
}

/** ACCOUNT properties describe the signed-in user and are cleared on logout; PROCESS ones describe the install. */
enum class UserPropertyScope { ACCOUNT, PROCESS }

enum class UserProperty(val value: String, val scope: UserPropertyScope) {
    SUB_TIER("sub_tier", UserPropertyScope.ACCOUNT),
    QUOTA_STATE("quota_state", UserPropertyScope.ACCOUNT),
    WORKOUTS_USED("workouts_used", UserPropertyScope.ACCOUNT),
    APP_BUILD("app_build", UserPropertyScope.PROCESS),
}

enum class ParamType { STRING, INT, DOUBLE }

/**
 * One event parameter. [name] is the TRANSPORT name the SDK is called with;
 * [exportName] is set only where the BigQuery export / DebugView surface it
 * under a different name (`screen_name` → `firebase_screen`).
 */
data class ParamSpec(
    val name: String,
    val type: ParamType,
    val description: String,
    val vocab: List<String>? = null,
    val exportName: String? = null,
)

data class EventSpec(val name: String, val meaning: String, val params: List<ParamSpec>, val funnels: List<String>)

data class UserPropertySpec(val property: UserProperty, val description: String, val vocab: List<String>?)

/**
 * The analytics dictionary: event specs, screens, user properties, one sample
 * per event, and the rendering of `docs/analytics/events.json`. Correctness
 * (GA4 limits, spec ↔ instance agreement, the checked-in JSON) is enforced by
 * `AnalyticsCatalogTest`, never at runtime.
 */
object AnalyticsCatalog {

    val events: List<EventSpec> = listOf(
        EventSpecs.SIGN_IN,
        EventSpecs.SIGN_OUT,
        EventSpecs.SCREEN_VIEW,
        EventSpecs.WORKOUT_SESSION_START,
        EventSpecs.WORKOUT_SESSION_END,
        EventSpecs.EXERCISE_ADDED,
        EventSpecs.SET_LOGGED,
        EventSpecs.WORKOUT_DELETED,
        EventSpecs.PAYWALL_REQUESTED,
        EventSpecs.PAYWALL_SHOWN,
        EventSpecs.PAYWALL_SKIPPED,
        EventSpecs.PURCHASE_RESULT,
        EventSpecs.REST_TIMER_STARTED,
        EventSpecs.FEATURE_USED,
    )

    val screens: List<ScreenName> = ScreenName.entries

    val userProperties: List<UserPropertySpec> = listOf(
        UserPropertySpec(
            UserProperty.SUB_TIER,
            "Subscription tier: entitled now, lapsed (subscribed before, not now), free (never subscribed), " +
                "or unknown (not resolved yet). Trial and paid are not distinguished.",
            listOf("entitled", "lapsed", "free", "unknown"),
        ),
        UserPropertySpec(
            UserProperty.QUOTA_STATE,
            "Free-workout meter: unlimited (not metered), metered (free workouts left), exhausted (none left), " +
                "lapsed (former subscriber, no allowance).",
            listOf("unlimited", "metered", "exhausted", "lapsed"),
        ),
        UserPropertySpec(
            UserProperty.WORKOUTS_USED,
            "Workouts counted by the meter, as a decimal string; the whole library when lapsed; unset when unlimited.",
            null,
        ),
        UserPropertySpec(
            UserProperty.APP_BUILD,
            "Build flavour of the install. Every cookbook query excludes debug.",
            listOf("debug", "release"),
        ),
    )

    /** Exactly one instance per [AnalyticsEvent] case, every optional param populated. */
    fun samples(): List<AnalyticsEvent> {
        val journalId = "3f1c2a9e-5b7d-4e0a-9c1f-2d8e6b4a7c01"
        val date = LocalDate(2026, 9, 1)
        val exerciseId = "8a6e4d2c-1b3f-4a5e-8d7c-9e0f1a2b3c4d"
        return listOf(
            AnalyticsEvent.SignIn(SignInMethod.APPLE, isNewUser = true),
            AnalyticsEvent.SignOut,
            AnalyticsEvent.ScreenView(ScreenName.WORKOUT),
            AnalyticsEvent.WorkoutSessionStart(journalId, date, workoutNumber = 1),
            AnalyticsEvent.WorkoutSessionEnd(journalId, date, workoutNumber = 1, durationSec = 3_600, via = SessionEndVia.FINISH_SHEET),
            AnalyticsEvent.ExerciseAdded(
                journalId, date, workoutNumber = 1, exerciseId = exerciseId, count = 2,
                source = ExerciseSource.CATALOG, firstInSlot = true,
            ),
            AnalyticsEvent.SetLogged(
                journalId, date, workoutNumber = 1, recordId = "c4b2e8a6-0d1f-4c3b-a5e7-6f8d9c0b1a2e",
                exerciseId = exerciseId, setIndex = 0, resultType = ResultType.WEIGHT_REPS, action = SetAction.ADD,
            ),
            AnalyticsEvent.WorkoutDeleted(journalId, date, workoutNumber = 1),
            AnalyticsEvent.PaywallRequested(PaywallEntry.HOME_CARD),
            AnalyticsEvent.PaywallShown(PaywallEntry.HOME_CARD, placement = "paywall_no_trial"),
            AnalyticsEvent.PaywallSkipped(PaywallEntry.IMPORT, reason = "no_rule_match"),
            AnalyticsEvent.PurchaseResult(PurchaseOutcome.PURCHASED, productId = "fj_annual", entry = PaywallEntry.HOME_CARD),
            AnalyticsEvent.RestTimerStarted(durationSec = 90),
            AnalyticsEvent.FeatureUsed(Feature.ONE_RM_CALCULATOR),
        )
    }

    /** `docs/analytics/events.json`, byte for byte (the catalog test diffs it). */
    fun renderJson(): String {
        val root = buildJsonObject {
            put("version", 1)
            put("events", buildJsonArray { events.forEach { add(it.toJson()) } })
            put("screens", buildJsonArray {
                screens.forEach { screen ->
                    add(buildJsonObject {
                        put("name", screen.value)
                        put("instrumented", screen.instrumented)
                    })
                }
            })
            put("user_properties", buildJsonArray {
                userProperties.forEach { spec ->
                    add(buildJsonObject {
                        put("name", spec.property.value)
                        put("scope", spec.property.scope.name.lowercase())
                        put("description", spec.description)
                        spec.vocab?.let { put("vocab", it.toJson()) }
                    })
                }
            })
            put("limits", buildJsonObject {
                put("event_name_max", Ga4Limits.EVENT_NAME_MAX)
                put("params_per_event_max", Ga4Limits.PARAMS_PER_EVENT_MAX)
                put("param_value_max", Ga4Limits.PARAM_VALUE_MAX)
                put("user_property_name_max", Ga4Limits.USER_PROPERTY_NAME_MAX)
                put("user_property_value_max", Ga4Limits.USER_PROPERTY_VALUE_MAX)
            })
        }
        return PrettyJson.encodeToString(JsonElement.serializer(), root) + "\n"
    }

    private val PrettyJson = Json { prettyPrint = true }

    private fun EventSpec.toJson() = buildJsonObject {
        put("name", name)
        put("meaning", meaning)
        put("funnels", funnels.toJson())
        put("params", buildJsonArray {
            params.forEach { param ->
                add(buildJsonObject {
                    put("name", param.name)
                    put("type", param.type.name.lowercase())
                    put("description", param.description)
                    param.vocab?.let { put("vocab", it.toJson()) }
                    param.exportName?.let { put("export_name", it) }
                })
            }
        })
    }

    private fun List<String>.toJson(): JsonArray = JsonArray(map(::JsonPrimitive))
}

/** The GA4 collection limits the catalog test enforces (and `events.json` publishes). */
internal object Ga4Limits {
    const val EVENT_NAMES_MAX = 500
    const val EVENT_NAME_MAX = 40
    const val PARAM_NAME_MAX = 40
    const val PARAMS_PER_EVENT_MAX = 25
    const val PARAM_VALUE_MAX = 100
    const val USER_PROPERTIES_MAX = 25
    const val USER_PROPERTY_NAME_MAX = 24
    const val USER_PROPERTY_VALUE_MAX = 36
}

/** The spec of every [AnalyticsEvent] case. Shared join-key params are declared once. */
internal object EventSpecs {

    private val JOURNAL_ID = ParamSpec("journal_id", ParamType.STRING, "Journal uuid (join key).")
    private val WORKOUT_DATE = ParamSpec("workout_date", ParamType.STRING, "Calendar day of the workout slot, YYYY-MM-DD (join key).")
    private val WORKOUT_NUMBER = ParamSpec("workout_number", ParamType.INT, "Workout number within the day, 1-based (join key; measure, never a filter).")
    private val EXERCISE_ID = ParamSpec("exercise_id", ParamType.STRING, "Exercise uuid (join key).")
    private val DURATION_SEC = ParamSpec("duration_sec", ParamType.INT, "Duration in seconds.")
    private val ENTRY = ParamSpec(
        "entry", ParamType.STRING, "Where the paywall was asked for; card vs gate derives from the value.",
        PaywallEntry.entries.map { it.value },
    )
    private val SLOT = listOf(JOURNAL_ID, WORKOUT_DATE, WORKOUT_NUMBER)

    val SIGN_IN = EventSpec(
        name = "sign_in",
        meaning = "A sign-in completed (Firebase Auth), once per sign-in, before AWS provisioning.",
        params = listOf(
            ParamSpec("method", ParamType.STRING, "Identity provider.", SignInMethod.entries.map { it.value }),
            ParamSpec("is_new_user", ParamType.STRING, "Firebase Auth's account-creation flag.", listOf("true", "false")),
        ),
        funnels = listOf("F1"),
    )

    val SIGN_OUT = EventSpec(
        name = "sign_out",
        meaning = "The user logged out.",
        params = emptyList(),
        funnels = emptyList(),
    )

    val SCREEN_VIEW = EventSpec(
        name = "screen_view",
        meaning = "A screen became visible (lifecycle STARTED), including returning to it.",
        params = listOf(
            ParamSpec(
                "screen_name", ParamType.STRING, "Catalog screen name.",
                vocab = ScreenName.entries.map { it.value },
                exportName = "firebase_screen",
            ),
        ),
        funnels = listOf("F1", "F3"),
    )

    val WORKOUT_SESSION_START = EventSpec(
        name = "workout_session_start",
        meaning = "The user started a workout session (Start).",
        params = SLOT,
        funnels = emptyList(),
    )

    val WORKOUT_SESSION_END = EventSpec(
        name = "workout_session_end",
        meaning = "A running workout session ended.",
        params = SLOT + listOf(
            DURATION_SEC,
            ParamSpec("via", ParamType.STRING, "What ended the session.", SessionEndVia.entries.map { it.value }),
        ),
        funnels = listOf("F3", "F5"),
    )

    val EXERCISE_ADDED = EventSpec(
        name = "exercise_added",
        meaning = "Exercises were written into a workout slot (one event per write).",
        params = SLOT + listOf(
            ParamSpec("exercise_id", ParamType.STRING, "Uuid of the first exercise added (join key)."),
            ParamSpec("count", ParamType.INT, "Number of exercises added by this write."),
            ParamSpec("source", ParamType.STRING, "Where the exercises came from.", ExerciseSource.entries.map { it.value }),
            ParamSpec(
                "first_in_slot", ParamType.STRING,
                "The slot held no live record before this write (re-logging a fully deleted slot repeats it).",
                listOf("true", "false"),
            ),
        ),
        funnels = listOf("F1", "F3", "F4", "F5"),
    )

    val SET_LOGGED = EventSpec(
        name = "set_logged",
        meaning = "A set was written (added or updated) after the write succeeded.",
        params = SLOT + listOf(
            ParamSpec("record_id", ParamType.STRING, "Workout record uuid (join key)."),
            EXERCISE_ID,
            ParamSpec("set_index", ParamType.INT, "Position of the set within the exercise, 0-based."),
            ParamSpec("result_type", ParamType.STRING, "Measurement kind of the set.", ResultType.entries.map { it.wire() }),
            ParamSpec("action", ParamType.STRING, "Whether the set was added or edited.", SetAction.entries.map { it.value }),
        ),
        funnels = listOf("F3", "F5"),
    )

    val WORKOUT_DELETED = EventSpec(
        name = "workout_deleted",
        meaning = "A whole workout slot was deleted.",
        params = SLOT,
        funnels = emptyList(),
    )

    val PAYWALL_REQUESTED = EventSpec(
        name = "paywall_requested",
        meaning = "A native entry point asked Superwall for the quota paywall.",
        params = listOf(ENTRY),
        funnels = listOf("F2"),
    )

    val PAYWALL_SHOWN = EventSpec(
        name = "paywall_shown",
        meaning = "Superwall presented the paywall.",
        params = listOf(
            ENTRY,
            ParamSpec("placement", ParamType.STRING, "Superwall paywall identifier that was presented."),
        ),
        funnels = listOf("F2"),
    )

    val PAYWALL_SKIPPED = EventSpec(
        name = "paywall_skipped",
        meaning = "Superwall decided not to present a paywall for the request.",
        params = listOf(
            ENTRY,
            ParamSpec("reason", ParamType.STRING, "Superwall skip reason (enum case name)."),
        ),
        funnels = emptyList(),
    )

    val PURCHASE_RESULT = EventSpec(
        name = "purchase_result",
        meaning = "A purchase or restore finished (Qonversion result).",
        params = listOf(
            ParamSpec("outcome", ParamType.STRING, "How it ended.", PurchaseOutcome.entries.map { it.value }),
            ParamSpec("product_id", ParamType.STRING, "Store product id; absent on restore."),
            ENTRY.copy(description = "Entry of the paywall on screen at purchase time; absent when none was presented."),
        ),
        funnels = listOf("F2"),
    )

    val REST_TIMER_STARTED = EventSpec(
        name = "rest_timer_started",
        meaning = "A rest timer was started.",
        params = listOf(DURATION_SEC),
        funnels = listOf("F5"),
    )

    val FEATURE_USED = EventSpec(
        name = "feature_used",
        meaning = "The user opened or used one of the tracked features.",
        params = listOf(
            ParamSpec("feature", ParamType.STRING, "Which feature.", Feature.entries.map { it.value }),
        ),
        funnels = listOf("F5"),
    )
}
