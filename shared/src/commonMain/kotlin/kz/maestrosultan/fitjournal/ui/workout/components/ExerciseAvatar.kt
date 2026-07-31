package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.ui.theme.composeColor

/**
 * Exercise avatar for the workout list. v1 renders the primary muscle group as a
 * colour chip (the app's shared per-category colour) rather than a per-exercise
 * image — porting the hundreds of exercise drawables is a separate asset job.
 */
@Composable
fun ExerciseAvatar(
    category: CategoryType,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val color = category.composeColor()
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.34f)
                .clip(CircleShape)
                .background(color),
        )
    }
}
