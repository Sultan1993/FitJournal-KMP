package kz.maestrosultan.fitjournal.ui.workoutlist.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.empty_plates
import kz.maestrosultan.fitjournal.shared.generated.resources.history_empty_message
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Plates illustration + one muted line only — the journal row, when present,
 * is composed ABOVE this by [kz.maestrosultan.fitjournal.ui.workoutlist.WorkoutListScreen], not here.
 */
@Composable
fun WorkoutListEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(Res.drawable.empty_plates),
            contentDescription = null,
            modifier = Modifier.size(width = 214.dp, height = 166.dp).alpha(0.85f),
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(Res.string.history_empty_message),
            style = FjTheme.typography.body.copy(fontSize = 15.5.sp, lineHeight = 23.25.sp),
            color = FjTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
