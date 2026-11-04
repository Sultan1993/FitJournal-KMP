package kz.maestrosultan.fitjournal.ui.workout.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.kmp.design.RadiusTokens
import kz.maestrosultan.fitjournal.kmp.design.SpaceTokens
import kz.maestrosultan.fitjournal.kmp.design.TypeTokens
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_note_placeholder
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_note_save
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_details_note_title
import kz.maestrosultan.fitjournal.ui.common.FjModalBottomSheet
import kz.maestrosultan.fitjournal.ui.common.rememberSheetCloser
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.workout.details.WorkoutDetailsContract
import org.jetbrains.compose.resources.stringResource

/**
 * Min height of the writing area (design 3a: the sheet opens roomy rather than
 * one line tall). Capped so a long note scrolls inside the field instead of
 * pushing the sheet past the screen.
 */
private val NoteFieldMinHeight = 140.dp
private val NoteFieldMaxHeight = 320.dp

/**
 * The workout-note sheet (design frames 3a/3b/3c): a title row with an inline
 * Save action, then the note text directly on the sheet surface — no framed
 * input tile, no character counter.
 *
 * Opens with the keyboard up and the caret placed (at the END of an existing
 * note, so editing doesn't start in front of the text).
 *
 * Save emits the current text via [onSave]; every dismissal route calls
 * [onDismiss]. Caller renders this only while the editor should show.
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
    // Save acts after the sheet has slid out, not the instant it's tapped.
    val close = rememberSheetCloser(sheetState)
    FjModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        NoteEditorContent(
            title = stringResource(Res.string.workout_details_note_title),
            placeholder = stringResource(Res.string.workout_details_note_placeholder),
            initialText = editor.initialText,
            // The editor identity: a different workout's note is a different
            // editing session, so both the draft and the focus request restart.
            editorKey = editor.workoutNumber,
            onSave = { text -> close { onSave(text) } },
        )
    }
}

/**
 * The note editor body — title row with the inline Save, then the text on the
 * sheet surface. Public so Android's exercise-note sheet (a nav sheet that already
 * has the house chrome) renders the same editor; iOS matches it natively because
 * its note field keeps a UIKit keyboard Done bar. Also split out for `@Preview`
 * (a `ModalBottomSheet` renders as a popup and shows up empty there).
 */
@Composable
fun NoteEditorContent(
    title: String,
    placeholder: String,
    initialText: String,
    editorKey: Any,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by remember(editorKey) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }

    // requestFocus is imperative work, so it needs the start-once effect rather
    // than running directly in the composable body.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(editorKey) {
        focusRequester.requestFocus()
    }

    // Dirty, NOT "non-blank": clearing an existing note is very much something
    // to save (it removes the note). A freshly opened empty note is therefore
    // the only case that starts dimmed — design 3a.
    val canSave = value.text != initialText

    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime)
            .padding(start = SpaceTokens.s16.dp, end = SpaceTokens.s16.dp, bottom = SpaceTokens.s24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = FjTheme.typography.screenTitle.copy(
                    fontSize = TypeTokens.t20.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = FjTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.workout_details_note_save),
                style = FjTheme.typography.bodyStrong,
                color = if (canSave) FjTheme.colors.brand else FjTheme.colors.textTertiary,
                // Padding grows the touch target leftward/vertically; the label
                // itself stays flush with the title's right margin.
                modifier = Modifier
                    .clip(RoundedCornerShape(RadiusTokens.chip.dp))
                    .clickable(enabled = canSave) { onSave(value.text) }
                    .padding(start = SpaceTokens.s16.dp, top = SpaceTokens.s8.dp, bottom = SpaceTokens.s8.dp),
            )
        }
        Spacer(Modifier.height(SpaceTokens.s12.dp))
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NoteFieldMinHeight, max = NoteFieldMaxHeight)
                .focusRequester(focusRequester),
            textStyle = FjTheme.typography.body.copy(
                fontSize = TypeTokens.t16.sp,
                color = FjTheme.colors.textPrimary,
            ),
            cursorBrush = SolidColor(FjTheme.colors.brand),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            decorationBox = { innerTextField ->
                if (value.text.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = FjTheme.typography.body.copy(fontSize = TypeTokens.t16.sp),
                        color = FjTheme.colors.textTertiary,
                    )
                }
                innerTextField()
            },
        )
    }
}

@Preview(name = "SessionNoteEditor Empty Light", widthDp = 402)
@Composable
private fun SessionNoteEditorEmptyPreviewLight() {
    NoteEditorSheetPreviewSurface(darkTheme = false) {
        NoteEditorContent(title = stringResource(Res.string.workout_details_note_title), placeholder = stringResource(Res.string.workout_details_note_placeholder), initialText = "", editorKey = 1, onSave = {})
    }
}

@Preview(name = "SessionNoteEditor Typed Light", widthDp = 402)
@Composable
private fun SessionNoteEditorTypedPreviewLight() {
    NoteEditorSheetPreviewSurface(darkTheme = false) {
        NoteEditorContent(title = stringResource(Res.string.workout_details_note_title), placeholder = stringResource(Res.string.workout_details_note_placeholder), initialText = PreviewNote, editorKey = 1, onSave = {})
    }
}

@Preview(name = "SessionNoteEditor Typed Dark", widthDp = 402)
@Composable
private fun SessionNoteEditorTypedPreviewDark() {
    NoteEditorSheetPreviewSurface(darkTheme = true) {
        NoteEditorContent(title = stringResource(Res.string.workout_details_note_title), placeholder = stringResource(Res.string.workout_details_note_placeholder), initialText = PreviewNote, editorKey = 1, onSave = {})
    }
}

private const val PreviewNote =
    "Short on sleep but pressing felt strong. Bench grip one finger wider — easier on the left shoulder"

/** The sheet surface the real host provides — `sheet`, not the screen `background`. */
@Composable
private fun NoteEditorSheetPreviewSurface(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    FitJournalTheme(darkTheme = darkTheme) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FjTheme.colors.sheet)
                .padding(top = SpaceTokens.s24.dp),
        ) {
            content()
        }
    }
}
