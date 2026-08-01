package kz.maestrosultan.fitjournal.ui.workout.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kz.maestrosultan.fitjournal.domain.exercise.Exercise
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.ui.theme.assetFolder
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * `files/exercises/<folder>/<image1>.png` for an exercise's bundled image, or
 * null when its category has no folder (OTHER) or it carries no image name.
 * Mirrors native `Exercise.getExerciseImageFromAssets` (image1 then image2).
 */
fun exerciseAssetImagePath(exercise: Exercise): String? {
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
fun rememberExerciseImageBitmap(path: String?): ImageBitmap? {
    if (path == null) return null
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = runCatching { Res.readBytes(path).decodeToImageBitmap() }.getOrNull()
    }
    return bitmap
}
