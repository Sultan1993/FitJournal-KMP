package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_workout_placeholder
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_first_workout_hint
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The first (empty) workout page — the day's primary empty state, restored from
 * native: the workout illustration above the "Press + to add exercises" hint.
 * Distinct from [AnotherWorkoutPlaceholder], which is the ephemeral N+1 "add
 * another workout" page. Tapping the illustration opens the add chooser, the
 * same as the bottom + button.
 */
@Composable
fun FirstWorkoutPlaceholder(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(Res.drawable.ic_workout_placeholder),
            contentDescription = null,
            // Native asset is 258x176; keep that ratio and cap the width.
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(258f / 176f)
                .clickable(onClick = onAddClick),
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(Res.string.workout_first_workout_hint),
            modifier = Modifier.widthIn(max = 300.dp),
            style = FjTheme.typography.body.copy(lineHeight = 20.sp),
            color = FjTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
