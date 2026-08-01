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
 * The source-day body — three mutually-exclusive phases the picker renders.
 * [Loading] (spinner) / [Empty] (no workouts that day) / [Loaded] (the pager).
 * Data that only exists once records are in hand — pages, the current page, the
 * selection — lives inside [Loaded], so a spinner can never carry stale rows.
 */
sealed interface ImportContent {
    data object Loading : ImportContent
    data object Empty : ImportContent
    data class Loaded(
        val pages: List<ImportPage>,
        val currentPageIndex: Int,
        val selectedRecordIds: Set<String>,
    ) : ImportContent
}

/**
 * State for the "Copy from a workout" picker: the source day being copied from
 * ([content] renders it) and the always-present chrome around it — the
 * source-date header and its calendar. [importInProgress] only disables/animates
 * the Add button, so it stays a field. [canImport] gates that button.
 */
data class ImportWorkoutUiState(
    val sourceDate: LocalDate,
    val calendarExpanded: Boolean,
    val workoutDays: Set<LocalDate>,
    val content: ImportContent,
    val importInProgress: Boolean,
    val measurementSystem: MeasurementSystem,
) {
    val canImport: Boolean get() {
        val loaded = content as? ImportContent.Loaded ?: return false
        return loaded.selectedRecordIds.isNotEmpty() && !importInProgress
    }

    companion object {
        fun initial(sourceDate: LocalDate) = ImportWorkoutUiState(
            sourceDate = sourceDate,
            calendarExpanded = false,
            workoutDays = emptySet(),
            content = ImportContent.Loading,
            importInProgress = false,
            measurementSystem = MeasurementSystem.KG_KM,
        )
    }
}
