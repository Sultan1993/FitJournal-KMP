package kz.maestrosultan.fitjournal.ui.postworkout.success

import kotlinx.datetime.LocalDate
import kz.maestrosultan.fitjournal.domain.exercise.CategoryType

/**
 * Display-ready state for the post-workout SUCCESS screen (spec §7.2), built
 * once by [WorkoutSuccessViewModel] from the rebuilt final summary — or from
 * the finish-time snapshot / bare fallback when the re-read fails.
 *
 * Formatting split: strings that need units or locale formatting
 * (`dateLine`, `tonnageText`, tile texts, PR weight texts) are formatted in
 * the ViewModel via `WorkoutValueFormatter`/`LocaleFormatters`; COUNTS stay
 * raw ([loggedSets], [exerciseCount], [RailAggregate.Reps.count], …) because
 * the composable renders them through plural resources. The saved-chip is
 * static — it needs no state.
 *
 * Section visibility: `null`/empty hides the section ([dateLine],
 * [tonnageText], [tiles], [personalRecord], [muscles], [exercises]).
 */
data class WorkoutSuccessUiState(
    val loading: Boolean = true,
    /** Localized top-3 muscle join ("Chest · Triceps · Abs") or the fallback title. */
    val title: String = "",
    /** "Wednesday, 22 July · 09:38–10:42"; null hides the line. */
    val dateLine: String? = null,
    /** Big tonnage value with unit ("1365 kg"); null (nothing logged) hides the block. */
    val tonnageText: String? = null,
    /** Caption counts — the composable pluralizes ("12 sets · 4 exercises"). */
    val loggedSets: Int = 0,
    val exerciseCount: Int = 0,
    val tiles: SuccessTiles? = null,
    val personalRecord: PersonalRecordUi? = null,
    /** Ranked muscle bars (SessionSummary order); empty hides the section. */
    val muscles: List<MuscleBarUi> = emptyList(),
    /** Exercise rail, day order; empty hides the section. */
    val exercises: List<RailLineUi> = emptyList(),
    /** One-shot success haptic on first appearance — consume via [WorkoutSuccessViewModel.onSuccessHapticPlayed]. */
    val playSuccessHaptic: Boolean = false,
)

/** The three stat tiles: duration, set count, "Nth workout this week". */
data class SuccessTiles(
    /** Elapsed h:mm, e.g. "1:04". */
    val durationText: String,
    /** Logged sets — the composable pluralizes the tile label. */
    val sets: Int,
    /** Already-localized ordinal ("3rd") — LocaleFormatters runs in the VM. */
    val weekOrdinalText: String,
)

/** PR card. Phrasing ("beat your previous best…") lives in the composable's Res strings. */
data class PersonalRecordUi(
    val exerciseName: String,
    /** "105 kg" — unit applied in the VM. */
    val weightText: String,
    /** null means the set was weight-only: omit the "× n" part entirely. */
    val reps: Int?,
    /** "100 kg" — the prior best being beaten. */
    val previousBestText: String,
    /** The composable turns this into the relative-date phrase. */
    val previousBestDate: LocalDate,
)

/** One bar of the muscle-distribution chart. */
data class MuscleBarUi(
    val category: CategoryType,
    val loggedSets: Int,
    /** loggedSets / the most-trained muscle's loggedSets, in (0, 1]. */
    val fraction: Float,
    /** Rank position for the color ramp: 0 = most-trained. */
    val rampIndex: Int,
)

/** One exercise line of the rail. */
data class RailLineUi(
    val name: String,
    val loggedSets: Int,
    val totalSets: Int,
    /** Spec §6 fallback chain; null when nothing was performed. */
    val aggregate: RailAggregate?,
)

/**
 * Exactly one aggregate family per line (mirrors
 * [kz.maestrosultan.fitjournal.domain.workout.summary.ExerciseLine]): weighted
 * work → [Tonnage]; bodyweight (zero-tonnage) work → [Reps];
 * DISTANCE_DURATION work → [DistanceDuration].
 */
sealed interface RailAggregate {
    /** "765 kg" — value + unit formatted in the VM (no pluralization involved). */
    data class Tonnage(val text: String) : RailAggregate

    /** Raw count — the composable pluralizes ("22 reps"). */
    data class Reps(val count: Int) : RailAggregate

    /** "8 km" + raw seconds — the composable formats/pluralizes the duration. */
    data class DistanceDuration(val distanceText: String, val durationSec: Int) : RailAggregate
}
