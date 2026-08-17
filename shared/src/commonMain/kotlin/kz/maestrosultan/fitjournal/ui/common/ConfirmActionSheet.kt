package kz.maestrosultan.fitjournal.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.common_cancel
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Reusable destructive-confirm bottom sheet — a title, a one-or-two-line message,
 * a filled [confirmLabel] action (brand-negative) and a subtle cancel button.
 * Always opens fully expanded. Hoist the open/close state in the caller and render
 * this only while it should be shown; every dismissal route calls [onDismiss].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmActionSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = stringResource(Res.string.common_cancel),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FjTheme.colors.sheet,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(
                text = title,
                style = FjTheme.typography.cardTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                color = FjTheme.colors.textPrimary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text = message,
                style = FjTheme.typography.body.copy(fontSize = 15.sp),
                color = FjTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 20.dp),
            )
            SheetButton(
                text = confirmLabel,
                container = FjTheme.colors.negative,
                content = Color.White,
                onClick = onConfirm,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            SheetButton(
                text = cancelLabel,
                // A subtle neutral fill that adapts to either theme.
                container = FjTheme.colors.textPrimary.copy(alpha = 0.08f),
                content = FjTheme.colors.textPrimary,
                onClick = onDismiss,
            )
        }
    }
}

@Composable
private fun SheetButton(
    text: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = FjTheme.typography.button, color = content)
    }
}
