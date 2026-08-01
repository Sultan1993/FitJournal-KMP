package kz.maestrosultan.fitjournal.ui.theme

import kz.maestrosultan.fitjournal.domain.exercise.CategoryType
import kz.maestrosultan.fitjournal.shared.generated.resources.Res
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_abs_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_back_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_biceps_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_calves_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_cardio_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_chest_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_forearms_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_glutes_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_hamstrings_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_quadriceps_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_shoulders_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_trapezius_small
import kz.maestrosultan.fitjournal.shared.generated.resources.ic_category_triceps_small
import org.jetbrains.compose.resources.DrawableResource

/**
 * Asset subfolder holding a category's bundled exercise images ("chest/",
 * "cardio/", …), or null for OTHER — mirrors the native `assetFolder()`.
 */
fun CategoryType.assetFolder(): String? =
    if (this == CategoryType.OTHER) null else "$identifier/"

/**
 * Small category icon (the fallback when an exercise has no bundled image), or
 * null for OTHER. Same vectors both apps use (`ic_category_*_small`).
 */
fun CategoryType.iconResource(): DrawableResource? = when (this) {
    CategoryType.CHEST -> Res.drawable.ic_category_chest_small
    CategoryType.BACK -> Res.drawable.ic_category_back_small
    CategoryType.BICEPS -> Res.drawable.ic_category_biceps_small
    CategoryType.TRICEPS -> Res.drawable.ic_category_triceps_small
    CategoryType.FOREARMS -> Res.drawable.ic_category_forearms_small
    CategoryType.SHOULDERS -> Res.drawable.ic_category_shoulders_small
    CategoryType.TRAPEZIUS -> Res.drawable.ic_category_trapezius_small
    CategoryType.QUADRICEPS -> Res.drawable.ic_category_quadriceps_small
    CategoryType.HAMSTRINGS -> Res.drawable.ic_category_hamstrings_small
    CategoryType.GLUTES -> Res.drawable.ic_category_glutes_small
    CategoryType.CALVES -> Res.drawable.ic_category_calves_small
    CategoryType.ABS -> Res.drawable.ic_category_abs_small
    CategoryType.CARDIO -> Res.drawable.ic_category_cardio_small
    CategoryType.OTHER -> null
}
