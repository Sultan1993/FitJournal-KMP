package kz.maestrosultan.fitjournal.ui.postworkout.composer.editors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_done
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_rail_backdrop
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_rail_layout
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_rail_scrim
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_rail_stats
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_rail_title
import kz.maestrosultan.fitjournal.ui.postworkout.composer.ComposerEditor
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Chrome for the composer's in-canvas editor overlays (design frame §7.3).
 *
 * Deliberately NOT a Material3 `ModalBottomSheet`: the live share card has to
 * stay visible and un-dimmed behind the panel, so this draws no scrim, hosts
 * itself inside the composer's own canvas [Box], and slides in from the bottom
 * edge of whatever it is given.
 *
 * The only slot is [content] — every editor supplies its own body and nothing
 * else. Header text, the Done affordance, the grabber and the surface are
 * design-pinned chrome the sheet owns on purpose, so all five panels stay
 * pixel-identical; see [EditorSheetDefaults] for the tokens the bodies share.
 *
 * [visible] drives the enter/exit animation, so the caller renders this
 * unconditionally (e.g. `visible = state.activeEditor == ComposerEditor.Layout`)
 * and gets a slide-out instead of an abrupt removal. While invisible nothing is
 * composed, so the canvas underneath keeps every pointer event.
 */
@Composable
internal fun EditorSheet(
    visible: Boolean,
    title: String,
    onDone: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(EditorSheetDefaults.AnimationMillis)),
            exit = fadeOut(tween(EditorSheetDefaults.AnimationMillis)),
        ) {
            OutsideTapCatcher(onTap = onDismissRequest, modifier = Modifier.fillMaxSize())
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(EditorSheetDefaults.AnimationMillis)) { it },
            exit = slideOutVertically(tween(EditorSheetDefaults.AnimationMillis)) { it },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(EditorSheetDefaults.Shape)
                    .background(EditorSheetDefaults.SurfaceColor)
                    // Own hit target so taps here don't fall through to the dismiss catcher beneath.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .padding(top = 10.dp, bottom = 22.dp),
            ) {
                Grabber(Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(14.dp))
                Header(title = title, onDone = onDone, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = EditorSheetDefaults.HorizontalPadding),
                    content = content,
                )
            }
        }
    }
}

/** Design tokens the five editor bodies share with the sheet chrome. */
internal object EditorSheetDefaults {

    /** Panel surface — fixed dark regardless of app theme; the card behind it is dark too. */
    val SurfaceColor: Color = Color(0xFF16161F)

    /** Fill for thumbnails, rows and chips sitting on [SurfaceColor]. */
    val TileColor: Color = Color(0xFF26262E)

    /** The pinned lilac used by the Done action and text cursors on this surface. */
    val AccentColor: Color = Color(0xFF9B93F6)

    val TopCornerRadius = 26.dp

    val Shape = RoundedCornerShape(topStart = TopCornerRadius, topEnd = TopCornerRadius)

    /** Gutter the sheet already applies around [EditorSheet]'s content slot. */
    val HorizontalPadding = 20.dp

    /** Slide/fade duration for both directions. */
    const val AnimationMillis: Int = 220

    /**
     * The rail label for [editor], which doubles as its panel title — one lookup
     * so the wiring can't drift from the rail.
     */
    @Composable
    fun titleFor(editor: ComposerEditor): String = stringResource(
        when (editor) {
            ComposerEditor.Title -> Res.string.postworkout_rail_title
            ComposerEditor.Layout -> Res.string.postworkout_rail_layout
            ComposerEditor.Backdrop -> Res.string.postworkout_rail_backdrop
            ComposerEditor.Stats -> Res.string.postworkout_rail_stats
            ComposerEditor.Scrim -> Res.string.postworkout_rail_scrim
        },
    )
}

/** 38x4.5 grabber at 22% white — the panel's only affordance besides Done. */
@Composable
private fun Grabber(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 4.5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(Color.White.copy(alpha = 0.22f)),
    )
}

/** 17 SemiBold title on the left, 14 Medium accent Done on the right. */
@Composable
private fun Header(title: String, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = EditorSheetDefaults.HorizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = FjTheme.typography.cardTitle.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(Res.string.postworkout_done),
            style = FjTheme.typography.button.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = EditorSheetDefaults.AccentColor,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onDone)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Transparent full-bleed dismiss target. The gesture detector is keyed on `Unit`
 * so it survives recomposition; [rememberUpdatedState] keeps it calling the
 * latest [onTap] instead of the one captured when the panel opened.
 */
@Composable
private fun OutsideTapCatcher(onTap: () -> Unit, modifier: Modifier = Modifier) {
    val latestOnTap by rememberUpdatedState(onTap)
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { latestOnTap() }
        },
    )
}
