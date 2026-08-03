package kz.maestrosultan.fitjournal.ui.postworkout.success

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_exercises
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_new_best
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_open_record
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_reps_format
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_saved_to_journal
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_section_muscles
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_section_what_you_did
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_sets
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_share_workout
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_tile_duration
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_tile_sets
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_tile_this_week
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_total_volume_caption
import kz.maestrosultan.fitjournal.ui.common.FjPrimaryButton
import kz.maestrosultan.fitjournal.ui.postworkout.format.nameRes
import kz.maestrosultan.fitjournal.ui.format.formatDuration
import kz.maestrosultan.fitjournal.ui.theme.FjTheme
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** "NEW BEST" label on the `accent` PR card (design W4b) — same ink as the W4a pill. */
private val PrLabelColor = Color(0xFF8A7326)

/** Trophy medallion behind the PR icon: `rgba(4,4,21,0.08)` from the frame. */
private val PrMedallionColor = Color(0x14040415)

/** Height of the gradient that fades scrolling content under the pinned footer. */
private val FooterFadeHeight = 44.dp

/** Muscle-bar entry animation: 300ms ease, staggered 40ms down the ranking. */
private const val BarAnimationMillis = 300
private const val BarStaggerMillis = 40

/** Rail row metrics — the connector inset is derived from them so it always meets the dot centers. */
private val RailRowVerticalPadding = 5.dp
private val RailDotSize = 9.dp
private val RailConnectorInset = RailRowVerticalPadding + RailDotSize / 2

/** The rail connector draws nothing testable, so the regression test asserts on its bounds. */
internal const val RailConnectorTestTag = "success_rail_connector"

/**
 * Post-workout SUCCESS screen (design frame W4b): a quiet journal moment —
 * date, muscle-group title, tonnage, three tiles, the PR card when one fired,
 * muscle bars, then the full exercise rail, all in one scroll fading under a
 * single pinned Share button.
 *
 * Content only: the close affordance is native chrome owned by the host
 * (liquid-glass bar item on iOS, the app's back convention on Android), and
 * the host also wraps this in `FitJournalTheme`.
 *
 * All values come pre-formatted from [WorkoutSuccessContract.ViewState]; the only
 * derivations here are presentation-level (plural selection, the tonnage
 * number/unit split, rail trailing assembly). [onHapticConsumed] fires once
 * when the state asks for the success haptic — the host performs it and tells
 * the ViewModel to clear the flag.
 */
@Composable
fun WorkoutSuccessScreen(
    state: WorkoutSuccessContract.ViewState,
    onShare: () -> Unit,
    onOpenRecord: () -> Unit,
    onHapticConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnHapticConsumed by rememberUpdatedState(onHapticConsumed)
    LaunchedEffect(state.playSuccessHaptic) {
        if (state.playSuccessHaptic) latestOnHapticConsumed()
    }

    Column(modifier.fillMaxSize().background(FjTheme.colors.background)) {
        // Fixed header row — design W4b marks it `flex: none`, so the
        // confirmation never scrolls away. The frame pairs it with the close
        // affordance on this same row; that button is native chrome owned by
        // the host (top-trailing liquid glass on iOS, the back convention on
        // Android), so the chip takes the leading side instead of colliding.
        if (!state.loading) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SavedToJournalChip()
            }
        }

        Box(Modifier.weight(1f)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 60.dp),
            ) {
                if (state.loading) return@Column

                Spacer(Modifier.height(24.dp))
                state.dateLine?.let { line ->
                    Text(
                        text = line,
                        style = FjTheme.typography.caption,
                        color = FjTheme.colors.textSecondary,
                    )
                    Spacer(Modifier.height(6.dp))
                }

                Text(
                    text = state.title,
                    style = FjTheme.typography.screenTitle.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 1.18.em,
                    ),
                    color = FjTheme.colors.textPrimary,
                )

                state.tonnageText?.let { tonnage ->
                    Spacer(Modifier.height(18.dp))
                    TonnageBlock(tonnage, state.loggedSets, state.exerciseCount)
                }

                state.tiles?.let { tiles ->
                    Spacer(Modifier.height(20.dp))
                    TilesRow(tiles)
                }

                state.personalRecord?.let { record ->
                    Spacer(Modifier.height(14.dp))
                    PersonalRecordCard(record)
                }

                if (state.muscles.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    SectionEyebrow(stringResource(Res.string.postworkout_section_muscles))
                    Spacer(Modifier.height(12.dp))
                    state.muscles.forEach { bar ->
                        MuscleBarRow(bar, Modifier.fillMaxWidth())
                    }
                }

                if (state.exercises.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionEyebrow(stringResource(Res.string.postworkout_section_what_you_did))
                        Text(
                            text = stringResource(Res.string.postworkout_open_record),
                            style = FjTheme.typography.label.copy(fontSize = 12.sp),
                            color = FjTheme.colors.brand,
                            modifier = Modifier.clickable(onClick = onOpenRecord),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    JournalRail(state.exercises)
                }
            }

            // Content slides under the footer rather than stopping short of it.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(FooterFadeHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                FjTheme.colors.background.copy(alpha = 0f),
                                FjTheme.colors.background,
                            ),
                        ),
                    ),
            )
        }

        // Gated on the same flag as the body: a live Share button pinned over a
        // blank screen would open the composer on a session that has not loaded.
        if (!state.loading) {
            FjPrimaryButton(
                text = stringResource(Res.string.postworkout_share_workout),
                onClick = onShare,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 26.dp),
                leadingIcon = { ShareGlyph() },
            )
        }
    }
}

/** Static confirmation pill — the records were persisted before this screen opened. */
@Composable
private fun SavedToJournalChip() {
    Row(
        Modifier
            .clip(CircleShape)
            .background(FjTheme.colors.surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckGlyph()
        Spacer(Modifier.width(7.dp))
        Text(
            text = stringResource(Res.string.postworkout_saved_to_journal),
            style = FjTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            color = FjTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun TonnageBlock(tonnageText: String, loggedSets: Int, exerciseCount: Int) {
    // The state carries one formatted string ("1365 kg"); the frame sets the
    // number and the unit at different sizes, so split on the last space.
    val unitIndex = tonnageText.lastIndexOf(' ')
    val value = if (unitIndex > 0) tonnageText.substring(0, unitIndex) else tonnageText
    val unit = if (unitIndex > 0) tonnageText.substring(unitIndex + 1) else null

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = FjTheme.typography.numberLarge.copy(
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.02).em,
            ),
            color = FjTheme.colors.textPrimary,
        )
        unit?.let {
            Spacer(Modifier.width(7.dp))
            Text(
                text = it,
                style = FjTheme.typography.bodyStrong,
                color = FjTheme.colors.textTertiary,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(
            Res.string.postworkout_total_volume_caption,
            pluralStringResource(Res.plurals.postworkout_sets, loggedSets, loggedSets),
            pluralStringResource(Res.plurals.postworkout_exercises, exerciseCount, exerciseCount),
        ),
        style = FjTheme.typography.caption,
        color = FjTheme.colors.textSecondary,
    )
}

@Composable
private fun TilesRow(tiles: SuccessTiles) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        StatTile(
            eyebrow = stringResource(Res.string.postworkout_tile_duration),
            value = tiles.durationText,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            eyebrow = stringResource(Res.string.postworkout_tile_sets),
            value = tiles.sets.toString(),
            modifier = Modifier.weight(1f),
        )
        StatTile(
            eyebrow = stringResource(Res.string.postworkout_tile_this_week),
            value = tiles.weekOrdinalText,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatTile(eyebrow: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(FjTheme.colors.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = eyebrow,
            style = FjTheme.typography.eyebrow.copy(fontSize = 10.sp),
            color = FjTheme.colors.textTertiary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = value,
            style = FjTheme.typography.cardTitle.copy(fontSize = 19.sp),
            color = FjTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun PersonalRecordCard(record: PersonalRecordUi) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FjTheme.colors.accent)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(PrMedallionColor),
            contentAlignment = Alignment.Center,
        ) {
            TrophyGlyph()
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(Res.string.postworkout_new_best),
                style = FjTheme.typography.eyebrow.copy(fontSize = 10.sp),
                color = PrLabelColor,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                // "× n" is omitted entirely for a weight-only set (reps == null).
                text = buildString {
                    append(record.exerciseName)
                    append(" · ")
                    append(record.weightText)
                    record.reps?.let { append(" × $it") }
                },
                style = FjTheme.typography.bodyStrong.copy(fontSize = 14.5.sp),
                color = FjTheme.colors.textPrimary,
            )
        }
    }
}

@Composable
private fun SectionEyebrow(text: String) {
    Text(
        text = text,
        style = FjTheme.typography.eyebrow,
        color = FjTheme.colors.textTertiary,
    )
}

@Composable
private fun MuscleBarRow(bar: MuscleBarUi, modifier: Modifier = Modifier) {
    // rememberSaveable: the entrance animation is a one-time welcome, not a
    // property of the data. On Android the composer is a forward nav
    // destination, so this screen's composition is disposed while it is open
    // and rebuilt on the way back — with plain `remember` the bars reset and
    // re-grew every time the user opened and closed the composer. iOS never
    // showed it, because `.overFullScreen` keeps the screen alive underneath.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val fraction by animateFloatAsState(
        targetValue = if (started) bar.fraction else 0f,
        animationSpec = tween(
            durationMillis = BarAnimationMillis,
            delayMillis = bar.rampIndex * BarStaggerMillis,
            easing = FastOutSlowInEasing,
        ),
        label = "muscleBar",
    )
    val ramp = FjTheme.colors.brandRamp
    val fill = ramp[bar.rampIndex.coerceIn(0, ramp.lastIndex)]

    Row(
        modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(bar.category.nameRes),
            style = FjTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
            color = FjTheme.colors.textPrimary,
            modifier = Modifier.width(82.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(FjTheme.colors.surface),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(5.dp))
                    .background(fill),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = bar.loggedSets.toString(),
            style = FjTheme.typography.label.copy(fontSize = 11.5.sp),
            color = FjTheme.colors.textSecondary,
            modifier = Modifier.width(18.dp),
        )
    }
}

@Composable
private fun JournalRail(lines: List<RailLineUi>) {
    Box {
        // The connector is wrapped in a matchParentSize box rather than being
        // sized directly. This screen's whole body is a `verticalScroll`, which
        // measures children with maxHeight = Infinity, and `fillMaxSize()` is a
        // no-op on an unbounded axis — sized directly, the line measured 0.dp
        // tall and never drew at all. `matchParentSize` is resolved from the
        // Box's FINAL size, after the rows have determined it, so the inner
        // `fillMaxHeight()` sees a bounded constraint.
        Box(Modifier.matchParentSize()) {
            Box(
                Modifier
                    // Inset to the dot centers so the line spans first to last
                    // rather than overshooting both ends (design W4b: top 10,
                    // bottom 12, against this row metric).
                    .padding(start = 4.dp, top = RailConnectorInset, bottom = RailConnectorInset)
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(FjTheme.colors.brandSubtle)
                    .testTag(RailConnectorTestTag),
            )
        }
        Column {
            lines.forEach { line ->
                RailRow(line, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun RailRow(line: RailLineUi, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(vertical = RailRowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(RailDotSize).clip(CircleShape).background(FjTheme.colors.brand))
        Spacer(Modifier.width(14.dp))
        Text(
            text = line.name,
            style = FjTheme.typography.bodyStrong,
            color = FjTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        line.aggregate?.let { aggregate ->
            Text(
                text = railTrailing(line.loggedSets, aggregate),
                style = FjTheme.typography.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                color = FjTheme.colors.textSecondary,
            )
        }
    }
}

/**
 * "4 sets · 4,320 kg" / "4 sets · 58 reps" / "8 km · 0:32" (spec §6 chain).
 *
 * Strength rows lead with the set count because sets ARE the unit of work
 * there. Distance-duration rows drop it: for a run the distance and the clock
 * are the story, and "1 set · 8 km · 0:32" reads as noise.
 */
@Composable
private fun railTrailing(loggedSets: Int, aggregate: RailAggregate): String = when (aggregate) {
    is RailAggregate.Tonnage ->
        "${pluralStringResource(Res.plurals.postworkout_sets, loggedSets, loggedSets)} · ${aggregate.text}"

    is RailAggregate.Reps ->
        "${pluralStringResource(Res.plurals.postworkout_sets, loggedSets, loggedSets)} · " +
            stringResource(Res.string.postworkout_reps_format, aggregate.count)

    is RailAggregate.DistanceDuration -> buildString {
        aggregate.distanceText?.let { append(it) }
        if (aggregate.durationSec > 0) {
            if (isNotEmpty()) append(" · ")
            append(formatDuration(aggregate.durationSec.toLong()))
        }
    }
}

@Composable
private fun CheckGlyph() {
    val color = FjTheme.colors.positive
    Canvas(Modifier.size(13.dp)) {
        val w = size.width
        val h = size.height
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.14f, h * 0.54f),
            end = androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.8f),
            strokeWidth = w * 0.16f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.4f, h * 0.8f),
            end = androidx.compose.ui.geometry.Offset(w * 0.86f, h * 0.24f),
            strokeWidth = w * 0.16f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun TrophyGlyph() {
    val color = FjTheme.colors.textPrimary
    Canvas(Modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        // Cup bowl.
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.06f),
            size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.62f),
        )
        // Stem and base.
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.62f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.82f),
            strokeWidth = w * 0.1f,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.88f),
            end = androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.88f),
            strokeWidth = w * 0.12f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ShareGlyph() {
    Canvas(Modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.14f
        // Up arrow out of a tray — matches the frame's share affordance.
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.12f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.64f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.28f, h * 0.34f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.12f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.72f, h * 0.34f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.12f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.62f),
            end = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.82f, h * 0.62f),
            end = androidx.compose.ui.geometry.Offset(w * 0.82f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(w * 0.18f, h * 0.88f),
            end = androidx.compose.ui.geometry.Offset(w * 0.82f, h * 0.88f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
