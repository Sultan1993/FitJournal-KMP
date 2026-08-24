package kz.maestrosultan.fitjournal.ui.workout.list

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.calculation.WorkloadMuscleEntry
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.ui.quota.QuotaCardContent

/**
 * MVI contract for the WorkoutList screen. Public rather than internal because
 * native iOS/Android hosts read [ViewModel.viewState], collect
 * [ViewModel.viewEffect], and call [ViewModel.dispatch] across the SKIE bridge.
 */
object WorkoutListContract {

    interface ViewModel {
        val viewState: StateFlow<ViewState>
        val viewEffect: Flow<ViewEffect>
        fun dispatch(action: ViewAction)
    }

    data class ViewState(
        val content: Content,
        /** Month-calendar overlay open (toggled from the native bar button). */
        val calendarVisible: Boolean,
        /** Calendar dots: day -> distinct categories trained (same as WorkoutContract). */
        val workoutDays: Map<LocalDate, List<CategoryType>>,
        val measurementSystem: MeasurementSystem,
        /** Calendar's selectedDate source — mirror of WorkoutContract.ViewState.selectedDate. */
        val today: LocalDate,
        /**
         * Free-quota card, or null when there is nothing to draw — an entitled
         * user AND every unresolved state (see `WorkoutQuota.toCardContent`).
         *
         * Lives on ViewState rather than inside [Content] because the quota is
         * per ACCOUNT, not per journal: it must not be rebuilt or re-animated by
         * a journal switch. It is still RENDERED inside the content area, since
         * its place is directly under the journal picker.
         */
        val quota: QuotaCardContent? = null,
    ) {
        companion object { fun initial(today: LocalDate) = ViewState(Content.Loading, false, emptyMap(), MeasurementSystem.KG_KM, today) }
    }

    /** Loading/Empty/Loaded as sealed content — data lives in the case. */
    sealed interface Content {
        data object Loading : Content
        data class Empty(
            /** null when journals.size <= 1; non-null so an empty journal stays switchable (assumption 7). */
            val journalRow: JournalRow?,
        ) : Content
        data class Loaded(
            /** null when journals.size <= 1 — the row is then not composed at all. */
            val journalRow: JournalRow?,
            val hero: Hero,
            /** Newest first; only weeks containing >= 1 workout. */
            val weeks: List<WeekSection>,
        ) : Content
    }

    /** [id] identifies which journal the feed was built for — the switch animation's key. */
    data class JournalRow(val id: String, val name: String, val isPersonal: Boolean)

    data class Hero(
        val currentWeekTonnage: Double,
        /** null until any week before the current one has data. */
        val delta: Double?,
        val workoutCount: Int,
        /** Days strictly after today through the current locale week's end (0..6). */
        val daysLeft: Int,
        /** Exactly 11, oldest -> current; empty weeks carry tonnage = 0.0. */
        val slots: List<WeekSlot>,
        /** One per run of consecutive same-month slots; slotCount is the Row weight. */
        val monthLabels: List<MonthLabel>,
    )
    data class WeekSlot(
        val tonnage: Double,
        val isCurrentWeek: Boolean,
        val weekStart: LocalDate,
        val workoutCount: Int,
        /** Total cardio minutes that week (0 when none). */
        val durationMinutes: Int,
    )
    data class MonthLabel(val month1to12: Int, val slotCount: Int)

    data class WeekSection(
        val start: LocalDate,
        val endInclusive: LocalDate,
        val kind: WeekKind,
        val workoutCount: Int,
        val tonnage: Double,
        /** Total cardio minutes that week (0 when none). */
        val durationMinutes: Int,
        /** null iff no earlier week has any data. */
        val delta: Double?,
        /** WorkloadCalculator.calculate(weekRecords, showOther = true), ranked. */
        val muscleSplit: List<WorkloadMuscleEntry>,
        /** endInclusive.year != today.year, computed by the feed. */
        val titleShowsYear: Boolean,
        /** Newest date first. */
        val days: List<DayRow>,
    )
    enum class WeekKind { ThisWeek, LastWeek, Older }

    data class DayRow(
        val date: LocalDate,
        /** Ranked by set count desc, max 3 — rendered via CategoryType.nameRes joined " · ". */
        val topCategories: List<CategoryType>,
        val tonnage: Double,
        /** Distinct (date, workoutNumber) count. Rendered only when > 1. */
        val workoutCount: Int,
        val exerciseCount: Int,
        /** Filled sets only: weight != null || distance != null (workout-subtitle rule). */
        val setCount: Int,
        /** Total cardio minutes that day (0 when none). */
        val durationMinutes: Int,
        /** Total cardio distance that day, raw stored unit (0.0 when none). */
        val distance: Double,
    )

    sealed interface ViewAction {
        data object ToggleCalendar : ViewAction
        data class CalendarMonthChanged(val year: Int, val month: Int) : ViewAction
        /** Calendar day tap. Data-bearing day -> close calendar + OpenWorkoutDetails; else no-op. */
        data class SelectDate(val date: LocalDate) : ViewAction
        /** Day-row tap. */
        data class OpenDay(val date: LocalDate) : ViewAction
        data object OpenJournalPicker : ViewAction
        /** Quota card's primary CTA — Upgrade / See plans / Renew. */
        data object QuotaUpgradeTapped : ViewAction
        /**
         * Quota card's secondary CTA, lapsed state only. Raises the same paywall as
         * [QuotaUpgradeTapped] — the store's own Restore control lives there, and a
         * button that re-probed entitlement in place could only report success by
         * redrawing, leaving every failure silent.
         */
        data object QuotaRestoreTapped : ViewAction
    }

    sealed interface ViewEffect {
        data class OpenWorkoutDetails(val date: LocalDate) : ViewEffect
        data object OpenJournalPicker : ViewEffect
        /**
         * Raise the subscription paywall. The host owns Superwall — shared code
         * never learns which placement, or that Superwall exists at all.
         */
        data object ShowPaywall : ViewEffect
    }
}
