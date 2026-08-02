package kz.maestrosultan.fitjournal.ui.postworkout.composer.editors

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_title_fallback
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ShareComposerContract
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/** Height of the field's tile — one line, never grows. */
private val FieldHeight = 52.dp

/**
 * Body of the Title panel: a single-line field prefilled with the card
 * headline, hard-capped at [maxLength] characters.
 *
 * State-in / callbacks-out — [title] is the rendered value, every keystroke goes
 * straight back out through [onTitleChange]; nothing is buffered here, so the
 * ViewModel stays the single source of truth and the live card updates as the
 * user types. Over-long input (a paste) is truncated rather than rejected.
 *
 * The body pads itself by [WindowInsets.ime] so the bottom-anchored
 * [EditorSheet] grows upward and clears the keyboard.
 */
@Composable
internal fun TitleEditor(
    title: String,
    onTitleChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    maxLength: Int = ShareComposerContract.ViewState.MAX_TITLE_LENGTH,
) {
    // The panel exists only to type in, so claim focus (and the keyboard) once
    // on enter. requestFocus is imperative work that must not run in the
    // composable body, hence the start-once effect.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(modifier = modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.ime)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(FieldHeight)
                .clip(RoundedCornerShape(14.dp))
                .background(EditorSheetDefaults.TileColor)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = title,
                onValueChange = { onTitleChange(it.take(maxLength)) },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                textStyle = FjTheme.typography.body.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                ),
                cursorBrush = SolidColor(EditorSheetDefaults.AccentColor),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        // isBlank, not isEmpty: a single space has length 1, so
                        // the placeholder would vanish while the card headline
                        // rendered invisible whitespace.
                        if (title.isBlank()) {
                            Text(
                                text = stringResource(Res.string.postworkout_title_fallback),
                                style = FjTheme.typography.body.copy(fontSize = 16.sp),
                                color = Color.White.copy(alpha = 0.32f),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "${title.length}/$maxLength",
            style = FjTheme.typography.caption,
            color = Color.White.copy(alpha = 0.38f),
            modifier = Modifier.align(Alignment.End),
        )
    }
}
