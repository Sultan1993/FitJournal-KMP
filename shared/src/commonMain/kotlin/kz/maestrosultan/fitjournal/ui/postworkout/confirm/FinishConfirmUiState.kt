package kz.maestrosultan.fitjournal.ui.postworkout.confirm

/**
 * One checklist row of the confirm sheet: an exercise with its logged/total
 * set progress. [allLogged] is precomputed so the composable renders a
 * check/dash without re-deriving the rule.
 */
data class FinishChecklistRow(
    val name: String,
    val loggedSets: Int,
    val totalSets: Int,
    /** Every set logged (and there is at least one) — the "done" affordance. */
    val allLogged: Boolean,
)

/**
 * Everything the end-workout confirm sheet renders — a pure data snapshot; all
 * formatting decisions live in [FinishConfirmViewModel], none in the
 * composable.
 *
 * [isFallback] is the summary-read-failure shell: zero counts and an empty
 * checklist, while the session-derived pieces ([dateText], the ticking
 * [durationText]) still render. Confirming from the fallback still works — the
 * finish event then carries an empty summary.
 */
data class FinishConfirmUiState(
    val loading: Boolean,
    val isFallback: Boolean,
    /** Eyebrow line, e.g. "Friday, 31 July" (LocaleFormatters.formatFullDate style). */
    val dateText: String,
    /** Tonnage number without its unit, e.g. "1580" — WorkoutValueFormatter-trimmed. */
    val tonnageValue: String,
    /** Its unit per the user's measurement system: "kg" / "lb". */
    val tonnageUnit: String,
    /** Elapsed h:mm (design's `duration h:mm`), re-derived every second while visible. */
    val durationText: String,
    val setsCount: Int,
    val exercisesCount: Int,
    val checklist: List<FinishChecklistRow>,
) {
    companion object {
        fun initial() = FinishConfirmUiState(
            loading = true,
            isFallback = false,
            dateText = "",
            tonnageValue = "",
            tonnageUnit = "",
            durationText = "",
            setsCount = 0,
            exercisesCount = 0,
            checklist = emptyList(),
        )
    }
}
