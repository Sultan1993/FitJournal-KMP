package kz.maestrosultan.fitjournal.analytics

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.ResultType

/**
 * The event catalog — the reporting contract. One case per GA4 event; each
 * carries its [spec] (name, meaning, param dictionary, funnels) and renders its
 * own wire [params].
 *
 * Wire rules, enforced by `AnalyticsCatalogTest` against [AnalyticsCatalog.samples]:
 *  - every value is a `Long`, `Double` or `String` — nothing else crosses to the SDKs;
 *  - `Int` renders as `Long`, `LocalDate` as `YYYY-MM-DD`, an enum as its wire value;
 *  - `Boolean` renders as the STRING `"true"` / `"false"`: GA4 app custom
 *    dimensions ignore integer values, and funnels filter on these;
 *  - a null optional param is ABSENT, never an empty string.
 *
 * Adding an event = a case here + its spec in [EventSpecs] + a sample in
 * [AnalyticsCatalog.samples] + a re-rendered `docs/analytics/events.json`.
 */
sealed class AnalyticsEvent {
    abstract val spec: EventSpec

    /** Values are `Long`, `Double` or `String` only. */
    abstract val params: Map<String, Any>

    data class SignIn(val method: SignInMethod, val isNewUser: Boolean) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.SIGN_IN
        override val params: Map<String, Any>
            get() = mapOf("method" to method.value, "is_new_user" to isNewUser.wire())
    }

    data object SignOut : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.SIGN_OUT
        override val params: Map<String, Any> get() = emptyMap()
    }

    data class ScreenView(val screen: ScreenName) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.SCREEN_VIEW
        override val params: Map<String, Any> get() = mapOf("screen_name" to screen.value)
    }

    data class WorkoutSessionStart(
        val journalId: String,
        val workoutDate: LocalDate,
        val workoutNumber: Int,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.WORKOUT_SESSION_START
        override val params: Map<String, Any> get() = slotParams(journalId, workoutDate, workoutNumber)
    }

    data class WorkoutSessionEnd(
        val journalId: String,
        val workoutDate: LocalDate,
        val workoutNumber: Int,
        val durationSec: Long,
        val via: SessionEndVia,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.WORKOUT_SESSION_END
        override val params: Map<String, Any>
            get() = slotParams(journalId, workoutDate, workoutNumber) +
                mapOf("duration_sec" to durationSec, "via" to via.value)
    }

    data class ExerciseAdded(
        val journalId: String,
        val workoutDate: LocalDate,
        val workoutNumber: Int,
        val exerciseId: String,
        val count: Int,
        val source: ExerciseSource,
        val firstInSlot: Boolean,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.EXERCISE_ADDED
        override val params: Map<String, Any>
            get() = slotParams(journalId, workoutDate, workoutNumber) + mapOf(
                "exercise_id" to exerciseId,
                "count" to count.toLong(),
                "source" to source.value,
                "first_in_slot" to firstInSlot.wire(),
            )
    }

    data class SetLogged(
        val journalId: String,
        val workoutDate: LocalDate,
        val workoutNumber: Int,
        val recordId: String,
        val exerciseId: String,
        val setIndex: Int,
        val resultType: ResultType,
        val action: SetAction,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.SET_LOGGED
        override val params: Map<String, Any>
            get() = slotParams(journalId, workoutDate, workoutNumber) + mapOf(
                "record_id" to recordId,
                "exercise_id" to exerciseId,
                "set_index" to setIndex.toLong(),
                "result_type" to resultType.wire(),
                "action" to action.value,
            )
    }

    data class WorkoutDeleted(
        val journalId: String,
        val workoutDate: LocalDate,
        val workoutNumber: Int,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.WORKOUT_DELETED
        override val params: Map<String, Any> get() = slotParams(journalId, workoutDate, workoutNumber)
    }

    data class PaywallRequested(val entry: PaywallEntry) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.PAYWALL_REQUESTED
        override val params: Map<String, Any> get() = mapOf("entry" to entry.value)
    }

    data class PaywallShown(val entry: PaywallEntry, val placement: String) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.PAYWALL_SHOWN
        override val params: Map<String, Any> get() = mapOf("entry" to entry.value, "placement" to placement)
    }

    data class PaywallSkipped(val entry: PaywallEntry, val reason: String) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.PAYWALL_SKIPPED
        override val params: Map<String, Any> get() = mapOf("entry" to entry.value, "reason" to reason)
    }

    /** [productId] is null on restore; [entry] is null when no paywall was presented. Both are then absent. */
    data class PurchaseResult(
        val outcome: PurchaseOutcome,
        val productId: String?,
        val entry: PaywallEntry?,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.PURCHASE_RESULT
        override val params: Map<String, Any>
            get() = buildMap {
                put("outcome", outcome.value)
                productId?.let { put("product_id", it) }
                entry?.let { put("entry", it.value) }
            }
    }

    data class RestTimerStarted(val durationSec: Long) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.REST_TIMER_STARTED
        override val params: Map<String, Any> get() = mapOf("duration_sec" to durationSec)
    }

    data class FeatureUsed(val feature: Feature) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.FEATURE_USED
        override val params: Map<String, Any> get() = mapOf("feature" to feature.value)
    }

    /** One per open request of the workout screen: construction and each distinct-date selection. */
    data class WorkoutOpened(
        val journalId: String,
        val workoutDate: LocalDate,
        val workoutNumber: Int,
        val day: DayContext,
        val entry: WorkoutEntry,
        val sessionState: SessionState,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.WORKOUT_OPENED
        override val params: Map<String, Any>
            get() = slotParams(journalId, workoutDate, workoutNumber) + day.params() + mapOf(
                "entry" to entry.value,
                "session_state" to sessionState.value,
            )
    }

    data class ExercisePickerOpened(val entry: ExerciseEntry) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.EXERCISE_PICKER_OPENED
        override val params: Map<String, Any> get() = mapOf("entry" to entry.value)
    }

    data class SetDeleted(
        val journalId: String,
        val workoutDate: LocalDate,
        val workoutNumber: Int,
        val recordId: String,
        val exerciseId: String,
        val setIndex: Int,
        val wasLogged: Boolean,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.SET_DELETED
        override val params: Map<String, Any>
            get() = slotParams(journalId, workoutDate, workoutNumber) + mapOf(
                "record_id" to recordId,
                "exercise_id" to exerciseId,
                "set_index" to setIndex.toLong(),
                "was_logged" to wasLogged.wire(),
            )
    }

    data class ExerciseRemoved(
        val journalId: String,
        val workoutDate: LocalDate,
        val workoutNumber: Int,
        val recordId: String,
        val exerciseId: String,
        val hadSets: Boolean,
        val entry: RemoveEntry,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.EXERCISE_REMOVED
        override val params: Map<String, Any>
            get() = slotParams(journalId, workoutDate, workoutNumber) + mapOf(
                "record_id" to recordId,
                "exercise_id" to exerciseId,
                "had_sets" to hadSets.wire(),
                "entry" to entry.value,
            )
    }

    /** [foundVia] is null when no picker was involved; it is then absent. */
    data class ExerciseReplaced(
        val journalId: String,
        val workoutDate: LocalDate,
        val workoutNumber: Int,
        val recordId: String,
        val exerciseId: String,
        val newExerciseId: String,
        val entry: ExerciseEntry,
        val foundVia: FoundVia?,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.EXERCISE_REPLACED
        override val params: Map<String, Any>
            get() = buildMap {
                putAll(slotParams(journalId, workoutDate, workoutNumber))
                put("record_id", recordId)
                put("exercise_id", exerciseId)
                put("new_exercise_id", newExerciseId)
                put("entry", entry.value)
                foundVia?.let { put("found_via", it.value) }
            }
    }

    data class FocusOpened(
        val journalId: String,
        val workoutDate: LocalDate,
        val workoutNumber: Int,
        val recordId: String,
        val exerciseId: String,
        val entry: FocusEntry,
        val startAddingSet: Boolean,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.FOCUS_OPENED
        override val params: Map<String, Any>
            get() = slotParams(journalId, workoutDate, workoutNumber) + mapOf(
                "record_id" to recordId,
                "exercise_id" to exerciseId,
                "entry" to entry.value,
                "start_adding_set" to startAddingSet.wire(),
            )
    }

    data class RestTimerEnded(
        val outcome: RestOutcome,
        val elapsedSec: Long,
        val durationSec: Long,
        val trigger: RestTrigger,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.REST_TIMER_ENDED
        override val params: Map<String, Any>
            get() = mapOf(
                "outcome" to outcome.value,
                "elapsed_sec" to elapsedSec,
                "duration_sec" to durationSec,
                "trigger" to trigger.value,
            )
    }

    data class CustomExerciseCreated(
        val exerciseId: String,
        val resultType: ResultType,
        val category: CategoryType,
    ) : AnalyticsEvent() {
        override val spec: EventSpec get() = EventSpecs.CUSTOM_EXERCISE_CREATED
        override val params: Map<String, Any>
            get() = mapOf(
                "exercise_id" to exerciseId,
                "result_type" to resultType.wire(),
                "category" to category.identifier,
            )
    }
}

enum class SignInMethod(val value: String) { APPLE("apple"), GOOGLE("google") }

enum class SessionEndVia(val value: String) {
    FINISH_SHEET("finish_sheet"),
    END_BUTTON("end_button"),
    AUTO("auto"),
    DISCARD_EMPTY("discard_empty"),
}

enum class ExerciseSource(val value: String) { CATALOG("catalog"), COPY("copy"), REPEAT("repeat") }

enum class SetAction(val value: String) { ADD("add"), UPDATE("update"), COMMIT_TARGET("commit_target") }

enum class PurchaseOutcome(val value: String) {
    PURCHASED("purchased"),
    CANCELLED("cancelled"),
    FAILED("failed"),
    RESTORED("restored"),
    RESTORE_FAILED("restore_failed"),
}

enum class Feature(val value: String) {
    ONE_RM_CALCULATOR("one_rm_calculator"),
    FOCUS_HISTORY("focus_history"),
    SUPERSET("superset"),
    SHARE_WORKOUT("share_workout"),
    WORKOUT_SUMMARY("workout_summary"),
    EXERCISE_NOTE("exercise_note"),
    CALENDAR("calendar"),
    COPY_WORKOUT("copy_workout"),
    REPEAT_WORKOUT("repeat_workout"),
}

/** The workout-slot join key, identical on every event that carries it. */
private fun slotParams(journalId: String, workoutDate: LocalDate, workoutNumber: Int): Map<String, Any> = mapOf(
    "journal_id" to journalId,
    "workout_date" to workoutDate.toString(),
    "workout_number" to workoutNumber.toLong(),
)

private fun DayContext.params(): Map<String, Any> =
    mapOf("day_kind" to dayKind.value, "days_from_today" to daysFromToday.toLong())

private fun Boolean.wire(): String = if (this) "true" else "false"

/** `ResultType` is a domain enum without a wire value; its analytics vocabulary is the lowercased name. */
internal fun ResultType.wire(): String = name.lowercase()
