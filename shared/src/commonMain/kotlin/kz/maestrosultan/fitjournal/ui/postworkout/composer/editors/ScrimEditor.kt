package kz.maestrosultan.fitjournal.ui.postworkout.composer.editors

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

/**
 * Body of the Scrim panel: how hard the backdrop is darkened behind the card
 * block, 0 (off) .. 1 (full).
 *
 * Every drag position is reported through [onScrimChange] as it happens — the
 * card behind the panel repaints live, which is the whole reason this overlay
 * has no scrim of its own. There is no commit step; the ViewModel persists on
 * dismiss.
 */
@Composable
fun ScrimEditor(
    scrim: Float,
    onScrimChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The sheet header already names the control, so the only readout here
        // is the value itself.
        Text(
            text = "${(scrim.coerceIn(0f, 1f) * 100f).roundToInt()}%",
            style = FjTheme.typography.label.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.align(Alignment.End),
        )
        Slider(
            value = scrim,
            onValueChange = onScrimChange,
            modifier = Modifier.fillMaxWidth(),
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
        )
    }
}
