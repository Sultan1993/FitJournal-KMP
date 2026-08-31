package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_menu_clear_set
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_menu_delete_set
import kz.maestrosultan.fitjournal.shared.generated.resources.focus_menu_log_set
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_check
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_delete
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_reset
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_set_label
import kz.maestrosultan.fitjournal.ui.common.MenuDivider
import kz.maestrosultan.fitjournal.ui.common.MenuRow
import kz.maestrosultan.fitjournal.ui.common.rememberSheetCloser
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Per-set action sheet, opened from the row's ⋮.
 *
 * Replaces the row's LEFT swipe, which had to go: it settled open and stayed
 * open, so a row inside the exercise pager held a horizontal drag the pager
 * wanted and then swallowed the next tap. The right swipe (commit the target)
 * survives — it acts and springs back within one fling, so it never holds the
 * gesture — and this sheet carries a duplicate of it, because a swipe is
 * unreachable with a screen reader.
 *
 * Built from the shared [MenuRow] / [MenuDivider] — icon + label rows with a
 * hairline before the destructive one, same as the exercise menu.
 *
 * Which rows appear is decided by the caller from the slot's own state, and the
 * two edit actions are mutually exclusive by construction — [canLogSet] is for
 * a set with nothing logged yet, [canClear] for one that has been. Usually that
 * makes two rows; it falls back to delete alone on a target row with no values
 * to log, and on an open row, whose editor already carries its own commit
 * button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusSetMenuSheet(
    setNumber: Int,
    canLogSet: Boolean,
    canClear: Boolean,
    onLogSet: () -> Unit,
    onClear: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Rows run their action after the sheet has slid out, not the instant it's tapped.
    val close = rememberSheetCloser(sheetState)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FjTheme.colors.sheet,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            // Says WHICH set you opened. The sheet covers the stack, so without
            // it there is nothing on screen tying these actions to a row.
            Text(
                text = "${stringResource(Res.string.workout_set_label).uppercase()} $setNumber",
                style = FjTheme.typography.caption.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                ),
                color = FjTheme.colors.textTertiary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )

            if (canLogSet) {
                MenuRow(
                    icon = Res.drawable.ic_common_check,
                    text = stringResource(Res.string.focus_menu_log_set),
                    onClick = { close(onLogSet) },
                )
            }
            if (canClear) {
                MenuRow(
                    icon = Res.drawable.ic_common_reset,
                    text = stringResource(Res.string.focus_menu_clear_set),
                    onClick = { close(onClear) },
                )
            }

            if (canLogSet || canClear) {
                MenuDivider()
            }

            MenuRow(
                icon = Res.drawable.ic_common_delete,
                text = stringResource(Res.string.focus_menu_delete_set),
                color = FjTheme.colors.negative,
                onClick = { close(onDelete) },
            )
        }
    }
}
