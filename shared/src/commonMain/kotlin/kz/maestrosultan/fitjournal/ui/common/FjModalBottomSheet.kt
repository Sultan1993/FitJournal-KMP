package kz.maestrosultan.fitjournal.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.kmp.design.RadiusTokens
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.common_sheet_drag_handle
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * THE bottom sheet. Every sheet in the app (CMP, Android, iOS) has this chrome:
 * top corners [RadiusTokens.sheet], the `sheet` surface, the `scrim` dim and one
 * [FjSheetDragHandle]. Content starts directly below the handle — the first
 * thing in it is the title (`cardTitle` at t20 Bold), no extra top padding.
 *
 * iOS native sheets mirror it in `presentBottomSheet` (UIViewControllerExtensions
 * .swift); Android native ones in `FJModalBottomSheet` / `FJModalBottomSheetLayout`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FjModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = RadiusTokens.sheet.dp, topEnd = RadiusTokens.sheet.dp),
        containerColor = FjTheme.colors.sheet,
        scrimColor = FjTheme.colors.scrim,
        dragHandle = { FjSheetDragHandle() },
        content = content,
    )
}

/**
 * The sheet drag handle: Material 3's own (`BottomSheetDefaults.DragHandle`) —
 * a 32×4 pill in `textSecondary`, 22 above and below it, so a 48-tall band.
 * Written out rather than delegated so the native twins can copy exact numbers
 * (iOS `SheetChrome`, Android passes its own colour).
 */
@Composable
fun FjSheetDragHandle(
    modifier: Modifier = Modifier,
    color: Color = FjTheme.colors.textSecondary,
) {
    // Labelled like M3's DragHandle, so TalkBack/VoiceOver don't skip it.
    val description = stringResource(Res.string.common_sheet_drag_handle)
    Box(
        modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 32.dp, height = 4.dp).clip(CircleShape).background(color))
    }
}
