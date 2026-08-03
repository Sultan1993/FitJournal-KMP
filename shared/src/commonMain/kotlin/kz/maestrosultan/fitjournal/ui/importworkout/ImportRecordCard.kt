package kz.maestrosultan.fitjournal.ui.importworkout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.user.MeasurementSystem
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.ui.workout.components.WorkoutRecordCard

/**
 * Import-mode record card — the main list's rich card rendered read-only with
 * per-exercise selection circles. The whole card toggles selection; selection
 * is conveyed by the circles alone (no border or fill change — native parity).
 */
@Composable
fun ImportRecordCard(
    record: WorkoutRecord,
    measurementSystem: MeasurementSystem,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WorkoutRecordCard(
        record = record,
        measurementSystem = measurementSystem,
        onSetClick = { _, _ -> },
        onAddSet = {},
        onExerciseMenu = {},
        isImporting = true,
        isSelected = isSelected,
        modifier = modifier
            .fillMaxWidth()
            .testTag("import_record_card")
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onToggle),
    )
}
