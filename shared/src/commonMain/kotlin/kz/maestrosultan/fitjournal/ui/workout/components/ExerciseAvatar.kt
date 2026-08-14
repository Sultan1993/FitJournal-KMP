package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import kz.maestrosultan.fitjournal.ui.theme.assetFolder
import kz.maestrosultan.fitjournal.ui.theme.composeColor
import kz.maestrosultan.fitjournal.ui.theme.iconResource
import org.jetbrains.compose.resources.decodeToImageBitmap
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

/**
 * `files/exercises/<folder>/<image1>.png` for an exercise's bundled image, or
 * null when its category has no folder (OTHER) or it carries no image name.
 * Mirrors native `Exercise.getExerciseImageFromAssets` (image1 then image2).
 */
private fun exerciseAssetImagePath(exercise: Exercise): String? {
    val folder = exercise.primaryCategory.type.assetFolder() ?: return null
    val name = exercise.image1 ?: exercise.image2 ?: return null
    return "files/exercises/$folder$name.png"
}

/**
 * Decode a bundled PNG (238 images, ~3.7 MB, shared in composeResources/files)
 * dynamically by path — the images are keyed at runtime, so static Res.drawable
 * accessors don't fit. Returns null while loading or if the file is absent.
 */
@Composable
private fun rememberExerciseImageBitmap(path: String?): ImageBitmap? {
    if (path == null) return null
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = runCatching { Res.readBytes(path).decodeToImageBitmap() }.getOrNull()
    }
    return bitmap
}
