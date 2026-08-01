package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.theme.composeColor
import kz.maestrosultan.fitjournal.ui.theme.iconResource
import org.jetbrains.compose.resources.painterResource

/**
 * Exercise avatar, 1:1 with the native `WorkoutExerciseImage`: a rounded box with
 * a 1dp category-colour border on a [textTertiary][FjTheme] fill, showing the
 * exercise's bundled image when it has one, else the category icon.
 */
@Composable
fun ExerciseAvatar(
    exercise: Exercise,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val category = exercise.primaryCategory.type
    val bitmap = rememberExerciseImageBitmap(exerciseAssetImagePath(exercise))
    val icon = category.iconResource()
    Box(
        modifier = modifier
            .size(size)
            .border(width = 1.dp, shape = RoundedCornerShape(12.dp), color = category.composeColor())
            .clip(RoundedCornerShape(12.dp))
            .background(FjTheme.colors.textTertiary),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            icon != null -> Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
