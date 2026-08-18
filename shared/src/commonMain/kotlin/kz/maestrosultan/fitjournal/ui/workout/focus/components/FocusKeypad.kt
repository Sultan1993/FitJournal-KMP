package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

private val KEY_ROWS = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", "⌫"),
)

private const val BACKSPACE_KEY = "⌫"

/**
 * Numeric keypad for the Focus set editor — 4x3 grid (1-9, ".", "0",
 * backspace), 1:1 with iOS `FocusKeypad` / Android's screen-embedded keypad
 * section. Stateless: every key press dispatches straight through [onDigit]
 * / [onBackspace] — the keypress transform (fresh-field replace/append) lives
 * in the VM's [kz.maestrosultan.fitjournal.ui.workout.focus.FocusInputState].
 */
@Composable
fun FocusKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        KEY_ROWS.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                row.forEach { key ->
                    FocusKeypadKey(
                        key = key,
                        onClick = { if (key == BACKSPACE_KEY) onBackspace() else onDigit(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun FocusKeypadKey(key: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (key == BACKSPACE_KEY) "⌫" else key
    Row(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = FjTheme.colors.brand),
                onClick = onClick,
            )
            .background(FjTheme.colors.background.copy(alpha = 0f)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = FjTheme.typography.body.copy(fontSize = 24.sp, fontWeight = FontWeight.Medium),
            color = if (key == BACKSPACE_KEY) FjTheme.colors.textSecondary else FjTheme.colors.textPrimary,
        )
    }
}

@Preview(name = "FocusKeypad Light")
@Composable
private fun FocusKeypadPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusKeypad(onDigit = {}, onBackspace = {})
    }
}

@Preview(name = "FocusKeypad Dark")
@Composable
private fun FocusKeypadPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusKeypad(onDigit = {}, onBackspace = {})
    }
}
