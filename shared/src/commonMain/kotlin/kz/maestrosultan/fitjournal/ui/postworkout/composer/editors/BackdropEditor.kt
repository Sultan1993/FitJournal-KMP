package kz.maestrosultan.fitjournal.ui.postworkout.composer.editors

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_backdrop_brand
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_backdrop_photo
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_backdrop_transparent
import kz.maestrosultan.fitjournal.ui.postworkout.composer.BackdropKind
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.stringResource

private val RowHeight = 52.dp
private val RowShape = RoundedCornerShape(14.dp)

/**
 * Body of the Backdrop panel: three rows, one per [BackdropKind].
 *
 * "Photo…" is not a plain selection — it has to raise the platform picker
 * first — so it calls [onPickPhoto] and only becomes the selected row once the
 * ViewModel has an actual bitmap. Brand and Transparent switch immediately via
 * [onSelect].
 */
@Composable
fun BackdropEditor(
    selected: BackdropKind,
    onSelect: (BackdropKind) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BackdropRow(
            label = stringResource(Res.string.postworkout_backdrop_photo),
            selected = selected == BackdropKind.Photo,
            onClick = onPickPhoto,
            modifier = Modifier.fillMaxWidth(),
        )
        BackdropRow(
            label = stringResource(Res.string.postworkout_backdrop_brand),
            selected = selected == BackdropKind.Brand,
            onClick = { onSelect(BackdropKind.Brand) },
            modifier = Modifier.fillMaxWidth(),
        )
        BackdropRow(
            label = stringResource(Res.string.postworkout_backdrop_transparent),
            selected = selected == BackdropKind.Transparent,
            onClick = { onSelect(BackdropKind.Transparent) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BackdropRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(RowHeight)
            .clip(RowShape)
            .background(EditorSheetDefaults.TileColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = FjTheme.typography.bodyStrong.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = Color.White.copy(alpha = if (selected) 1f else 0.78f),
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(FjTheme.colors.brand),
            )
        }
    }
}
