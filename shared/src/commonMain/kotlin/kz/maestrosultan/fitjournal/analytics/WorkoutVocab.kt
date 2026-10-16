package kz.maestrosultan.fitjournal.analytics

/** Where an exercise add / picker open / replace came from. */
enum class ExerciseEntry(val value: String) {
    WORKOUT_BUTTON("workout_button"),
    FOCUS("focus"),
    EXERCISE_CALENDAR("exercise_calendar"),
    COPY("copy"),
    REPEAT("repeat"),
    REPLACE_WORKOUT("replace_workout"),
    REPLACE_FOCUS("replace_focus"),
}

/** How the picked exercise was found. */
enum class FoundVia(val value: String) { BROWSE("browse"), SEARCH("search"), CREATED_CUSTOM("created_custom") }

/** Where a logged set's numbers came from. */
enum class SetInput(val value: String) {
    PREFILL_LAST_TIME("prefill_last_time"),
    PREFILL_PREVIOUS_SET("prefill_previous_set"),
    DEFAULT("default"),
    TYPED("typed"),
    UNCHANGED("unchanged"),
}

/** Which route opened the workout screen. */
enum class WorkoutEntry(val value: String) {
    HOME_START("home_start"),
    DETAILS_EDIT("details_edit"),
    CALENDAR("calendar");

    companion object {
        fun fromWire(value: String): WorkoutEntry? = entries.firstOrNull { it.value == value }
    }
}

enum class SessionState(val value: String) { NONE("none"), RUNNING("running"), FINISHED("finished") }

enum class RemoveEntry(val value: String) { WORKOUT("workout"), FOCUS("focus") }

enum class FocusEntry(val value: String) { ROW_TAP("row_tap"), ADD_SET("add_set"), NEXT_EXERCISE("next_exercise") }

enum class RestTrigger(val value: String) { AUTO("auto"), MANUAL("manual") }

enum class RestOutcome(val value: String) { FINISHED("finished"), STOPPED("stopped"), RESTARTED("restarted") }
