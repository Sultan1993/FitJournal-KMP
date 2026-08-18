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
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.ui.theme.FitJournalTheme
import kz.maestrosultan.fitjournal.ui.theme.iconResource
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource

/**
 * An exercise/category thumbnail resolved from a single asset-name STRING —
 * the same either/or token the shared rest-info builder documents
 * ([kz.maestrosultan.fitjournal.ui.workout.focus.buildRestPresentationInfo]'s
 * `thumbName`, and iOS's `image1 ?? categoryType.imageSmallName`):
 *
 * - a bundled-image path (`files/exercises/<folder>/<name>.png`) — the exact
 *   form `ExerciseAvatar.exerciseAssetImagePath` builds — decoded via
 *   [Res.readBytes]; or
 * - a bare category identifier (`"chest"`, [CategoryType.identifier]) when
 *   the exercise carries no bundled image — resolved to that category's
 *   [iconResource] and drawn with `painterResource`, exactly [ExerciseAvatar][kz.maestrosultan.fitjournal.ui.workout.components.ExerciseAvatar]'s
 *   own fallback rule.
 *
 * `FocusUi` (`FocusPillUi.imageNames`, `FocusMemberItemUi.imageName`, …)
 * carries plain strings rather than a domain `Exercise`, so this resolves by
 * path/token directly instead of going through `ExerciseAvatar` itself.
 * Renders nothing (transparent) only when NEITHER form resolves — a missing
 * bundled file with an unrecognized token, which should not happen for a
 * real exercise but must not crash the row.
 */
@Composable
fun FocusExerciseThumb(
    imageName: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val bitmap = rememberFocusThumbBitmap(imageName)
    Box(modifier = modifier) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
            imageName != null -> {
                val icon = categoryIconFor(imageName)
                if (icon != null) {
                    Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        contentScale = contentScale,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** [CategoryType.identifier] reverse lookup — null when [token] isn't one. */
private fun categoryIconFor(token: String) =
    CategoryType.entries.firstOrNull { it.identifier == token }?.iconResource()

/**
 * Decodes a bundled image by path, same convention as `ExerciseAvatar`. Null
 * both while loading and when [imageName] isn't a decodable file path (the
 * category-identifier form always fails here, by design — the caller falls
 * back to [categoryIconFor]).
 */
@Composable
private fun rememberFocusThumbBitmap(imageName: String?): ImageBitmap? {
    if (imageName == null) return null
    var bitmap by remember(imageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(imageName) {
        // Not `runGuarded` (spec's SKIE-boundary wrapper, private + suspend on
        // WorkoutFocusViewModel): this never crosses the SKIE boundary, it
        // just decides whether to fall back to the category icon.
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

@Preview(name = "FocusExerciseThumb Light · bundled image")
@Composable
private fun FocusExerciseThumbPreviewLight() {
    FitJournalTheme(darkTheme = false) {
        FocusExerciseThumb(imageName = "files/exercises/chest/bench_press.png", modifier = Modifier.size(44.dp))
    }
}

@Preview(name = "FocusExerciseThumb Dark · category fallback")
@Composable
private fun FocusExerciseThumbPreviewDark() {
    FitJournalTheme(darkTheme = true) {
        FocusExerciseThumb(imageName = "chest", modifier = Modifier.size(44.dp))
    }
}
