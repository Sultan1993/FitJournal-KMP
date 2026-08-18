package kz.maestrosultan.fitjournal.ui.workout.focus.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * An exercise/category thumbnail resolved from an asset-name string (already
 * a resolvable `composeResources` path — pill/title/picker/superset thumbs
 * carry these in [FocusUi][kz.maestrosultan.fitjournal.ui.workout.focus.FocusUi]
 * rather than a domain [kz.maestrosultan.fitjournal.domain.exercise.Exercise],
 * so this decodes by path directly instead of going through `ExerciseAvatar`.
 * Renders nothing (transparent) while loading or when the asset is missing —
 * callers own background/border/clip via [modifier].
 */
@Composable
fun FocusExerciseThumb(
    imageName: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap = rememberFocusThumbBitmap(imageName)
    Box(modifier = modifier) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Decodes a bundled image by path, same convention as `ExerciseAvatar`. */
@Composable
private fun rememberFocusThumbBitmap(imageName: String?): ImageBitmap? {
    if (imageName == null) return null
    var bitmap by remember(imageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageName) {
        // Not `runGuarded` (spec's SKIE-boundary wrapper, private + suspend on
        // WorkoutFocusViewModel): this never crosses the SKIE boundary, it
        // just decides whether to show nothing for a missing/bad asset path.
        bitmap = try {
            Res.readBytes(imageName).decodeToImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            null
        }
    }
    return bitmap
}

@Preview(name = "FocusExerciseThumb Light")
@Composable
private fun FocusExerciseThumbPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusExerciseThumb(imageName = null, modifier = Modifier.size(44.dp))
    }
}

@Preview(name = "FocusExerciseThumb Dark")
@Composable
private fun FocusExerciseThumbPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusExerciseThumb(imageName = null, modifier = Modifier.size(44.dp))
    }
}
