package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kz.maestrosultan.fitjournal.domain.workout.WorkoutRecord
import kz.maestrosultan.fitjournal.domain.workout.WorkoutSession
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_common_share
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_duration_hours_minutes
import kz.maestrosultan.fitjournal.shared.generated.resources.workout_duration_minutes
import kz.maestrosultan.fitjournal.ui.format.LocaleFormatters
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val SEPARATOR = " • "

/**
 * The page header for a finished, timed workout (mockup state 4b): the muscle
 * title becomes a card carrying the session's start–end time + duration on an
 * eyebrow line and a Share action. Only rendered when [session] is ended;
 * unfinished/never-timed workouts keep the plain [WorkoutMuscleHeader] title.
 */
@Composable
fun WorkoutSessionCard(
    records: List<WorkoutRecord>,
    session: WorkoutSession,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val endedAt = session.endedAt ?: return
    val muscles = remember(records) {
        records.flatMap { it.exercises }
            .map { it.exercise.primaryCategory }
            .distinctBy { it.type }
            .sortedBy { it.type.ordinal }
            .joinToString(SEPARATOR) { it.name }
    }

    val timeZone = TimeZone.currentSystemDefault()
    val start = LocaleFormatters.formatTimeShort(session.startedAt, timeZone)
    val end = LocaleFormatters.formatTimeShort(endedAt, timeZone)
    val totalMinutes = maxOf(1L, session.durationSec(endedAt) / 60)
    val duration = if (totalMinutes >= 60) {
        stringResource(Res.string.workout_duration_hours_minutes, (totalMinutes / 60).toInt(), (totalMinutes % 60).toInt())
    } else {
        stringResource(Res.string.workout_duration_minutes, totalMinutes.toInt())
    }
    val eyebrow = "$start – $end$SEPARATOR$duration".uppercase()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(FjTheme.colors.surface)
            .padding(start = 20.dp, top = 16.dp, bottom = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                style = FjTheme.typography.eyebrow,
                color = FjTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = muscles,
                style = FjTheme.typography.screenTitle,
                color = FjTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(FjTheme.colors.brand)
                .clickable(onClick = onShare),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_common_share),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
