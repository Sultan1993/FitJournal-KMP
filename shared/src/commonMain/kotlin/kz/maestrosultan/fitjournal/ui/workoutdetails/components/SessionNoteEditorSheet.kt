package kz.maestrosultan.fitjournal.ui.workoutdetails.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_note
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_note_placeholder
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_note_save
import kz.maestrosultan.fitjournal.ui.common.FjPrimaryButton
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workoutdetails.WorkoutDetailsContract
import org.jetbrains.compose.resources.stringResource

/**
 * The shared session-note editor (design §7): a `surfaceElevated` bottom sheet
 * (the [ConfirmActionSheet][kz.maestrosultan.fitjournal.ui.common.ConfirmActionSheet]
 * precedent) holding a multiline field seeded with [NoteEditor.initialText] and a
 * Save CTA. Save emits the current text through [onSave]; every dismissal route
 * calls [onDismiss]. The caller renders this only while the editor should show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionNoteEditorSheet(
    editor: WorkoutDetailsContract.NoteEditor,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by remember(editor.sessionUuid) { mutableStateOf(editor.initialText) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FjTheme.colors.surfaceElevated,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = stringResource(Res.string.workout_details_note),
                style = FjTheme.typography.eyebrow.copy(fontSize = 10.sp),
                color = FjTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(FjTheme.colors.surface)
                    .padding(14.dp),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = FjTheme.typography.body.copy(
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = FjTheme.colors.textPrimary,
                    ),
                    cursorBrush = SolidColor(FjTheme.colors.brand),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.workout_details_note_placeholder),
                                style = FjTheme.typography.body.copy(fontSize = 15.sp),
                                color = FjTheme.colors.textTertiary,
                            )
                        }
                        innerTextField()
                    },
                )
            }
            FjPrimaryButton(
                text = stringResource(Res.string.workout_details_note_save),
                onClick = { onSave(text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}
