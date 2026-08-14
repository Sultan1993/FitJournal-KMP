package kz.maestrosultan.fitjournal.ui.workout.main.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.ui.theme.FjTheme

/**
 * Empty "add another workout" page. Centered dashed-square add button above a title
 * and a supporting line. All copy is passed in so this stays multiplatform-clean.
 */
@Composable
fun AnotherWorkoutPlaceholder(
    title: String,
    subtitle: String,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // FjTheme reads must happen in composition; capture the colors the draw phase needs.
    val borderColor = FjTheme.colors.border
    val glyphColor = FjTheme.colors.textSecondary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onAddClick)
                .drawBehind {
                    val stroke = 1.5.dp.toPx()
                    val inset = stroke / 2f
                    drawRoundRect(
                        color = borderColor,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(24.dp.toPx()),
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(6.dp.toPx(), 5.dp.toPx()),
                                0f,
                            ),
                        ),
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(26.dp)) {
                val armStroke = 2.5.dp.toPx()
                drawLine(
                    color = glyphColor,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = armStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = glyphColor,
                    start = Offset(size.width / 2f, 0f),
                    end = Offset(size.width / 2f, size.height),
                    strokeWidth = armStroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = FjTheme.typography.cardTitle.copy(fontWeight = FontWeight.SemiBold),
            color = FjTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            modifier = Modifier.widthIn(max = 300.dp),
            style = FjTheme.typography.body.copy(lineHeight = 20.sp),
            color = FjTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
