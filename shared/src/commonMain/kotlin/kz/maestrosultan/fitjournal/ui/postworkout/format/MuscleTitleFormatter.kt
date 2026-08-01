package kz.maestrosultan.fitjournal.ui.postworkout.format

import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.domain.workout.summary.MuscleLoad
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_abs
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_back
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_biceps
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_calves
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_cardio
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_chest
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_forearms
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_glutes
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_hamstrings
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_other
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_quadriceps
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_shoulders
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_trapezius
import kz.maestrosultan.fitjournal.shared.generated.resources.category_name_triceps
import kz.maestrosultan.fitjournal.shared.generated.resources.postworkout_title_fallback
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

/**
 * Builds the post-workout headline from the ranked muscle-load list: the top 3
 * localized category names joined with " · " (e.g. "Chest · Triceps · Abs"),
 * or the localized fallback ("Workout") when nothing was logged.
 *
 * The list arrives ranked (SessionSummary ranks by logged sets); this class
 * trusts that order and never re-sorts.
 *
 * The two lookups default to compose-resources [getString] and are injectable
 * only so jvmTest stays deterministic (no resource loading, no locale
 * dependence). Production callers always use the defaults.
 */
internal class MuscleTitleFormatter(
    private val categoryName: suspend (CategoryType) -> String = { getString(it.nameRes) },
    private val fallbackTitle: suspend () -> String = { getString(Res.string.postworkout_title_fallback) },
) {

    suspend fun title(muscles: List<MuscleLoad>): String {
        if (muscles.isEmpty()) return fallbackTitle()
        return muscles.take(TOP_COUNT)
            .map { categoryName(it.category) }
            .joinToString(SEPARATOR)
    }

    private companion object {
        const val TOP_COUNT = 3
        const val SEPARATOR = " · "
    }
}

/**
 * `category_name_<identifier>` — one key per [CategoryType] in values/strings.xml.
 * Also read by the success screen's muscle bars, so the 14-way map lives here once.
 */
internal val CategoryType.nameRes: StringResource
    get() = when (this) {
        CategoryType.CHEST -> Res.string.category_name_chest
        CategoryType.BACK -> Res.string.category_name_back
        CategoryType.BICEPS -> Res.string.category_name_biceps
        CategoryType.TRICEPS -> Res.string.category_name_triceps
        CategoryType.FOREARMS -> Res.string.category_name_forearms
        CategoryType.SHOULDERS -> Res.string.category_name_shoulders
        CategoryType.TRAPEZIUS -> Res.string.category_name_trapezius
        CategoryType.QUADRICEPS -> Res.string.category_name_quadriceps
        CategoryType.HAMSTRINGS -> Res.string.category_name_hamstrings
        CategoryType.GLUTES -> Res.string.category_name_glutes
        CategoryType.CALVES -> Res.string.category_name_calves
        CategoryType.ABS -> Res.string.category_name_abs
        CategoryType.CARDIO -> Res.string.category_name_cardio
        CategoryType.OTHER -> Res.string.category_name_other
    }
