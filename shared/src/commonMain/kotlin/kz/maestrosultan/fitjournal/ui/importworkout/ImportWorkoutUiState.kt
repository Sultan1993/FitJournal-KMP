package kz.maestrosultan.fitjournal.ui.importworkout

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord

/** One page of the import pager = one workout (by number) on the source day. */
data class ImportPage(
    val workoutNumber: Int,
    val records: List<WorkoutRecord>,
)

/**
 * State for the "Copy from a workout" picker: which past day we're copying from,
 * that day's records grouped into a per-workout pager, and which records are
 * selected. [canImport] gates the Add button.
 */
data class ImportWorkoutUiState(
    val loading: Boolean,
    val sourceDate: LocalDate,
    val calendarExpanded: Boolean,
    val workoutDays: Set<LocalDate>,
    val pages: List<ImportPage>,
    val currentPageIndex: Int,
    val selectedRecordIds: Set<String>,
    val measurementSystem: MeasurementSystem,
) {
    val canImport: Boolean get() = selectedRecordIds.isNotEmpty()

    companion object {
        fun initial(sourceDate: LocalDate) = ImportWorkoutUiState(
            loading = true,
            sourceDate = sourceDate,
            calendarExpanded = false,
            workoutDays = emptySet(),
            pages = emptyList(),
            currentPageIndex = 0,
            selectedRecordIds = emptySet(),
            measurementSystem = MeasurementSystem.KG_KM,
        )
    }
}
