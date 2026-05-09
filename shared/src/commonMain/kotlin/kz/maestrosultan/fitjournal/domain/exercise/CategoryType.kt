package kz.maestrosultan.fitjournal.domain.exercise

/**
 * Muscle-group category the exercise belongs to.
 *
 * `id` is the legacy 1-based integer that has been written to Parse/AWS
 * since v1; new rows still write it for back-compat. Image assets and
 * localized titles are platform-specific extensions — see
 * `KMPCategoryTypeExtensions.swift` (iOS) and `CategoryTypeExtensions.kt`
 * (Android).
 */
enum class CategoryType(val id: Int) {
    CHEST(1),
    BACK(2),
    BICEPS(3),
    TRICEPS(4),
    FOREARMS(5),
    SHOULDERS(6),
    TRAPEZIUS(7),
    QUADRICEPS(8),
    HAMSTRINGS(9),
    GLUTES(10),
    CALVES(11),
    ABS(12),
    CARDIO(13),
    OTHER(14);

    /**
     * Stable lowercase identifier used as the asset-name segment on both
     * platforms (e.g. iOS `"category.chest.small"`, Android
     * `"ic_category_chest_small"`). Lives on the shared enum so the asset
     * lookups on each side stay aligned without each platform inventing
     * its own string table.
     */
    val identifier: String
        get() = name.lowercase()

    /**
     * Hex colour shared by both platforms — the brand chose these once;
     * keeping them on the shared enum guarantees iOS and Android render
     * categories the same colour on charts, badges, and skeleton states.
     */
    val colorHex: String
        get() = when (this) {
            CHEST -> "#5e548e"
            BACK -> "#9f86c0"
            BICEPS -> "#f29e4c"
            TRICEPS -> "#f1c453"
            FOREARMS -> "#efea5a"
            SHOULDERS -> "#048ba8"
            TRAPEZIUS -> "#2c699a"
            QUADRICEPS -> "#b9e769"
            HAMSTRINGS -> "#83e377"
            GLUTES -> "#0db39e"
            CALVES -> "#16db93"
            ABS -> "#e28963"
            CARDIO -> "#eb4511"
            OTHER -> "#8e9aaf"
        }

    companion object {
        fun create(id: Int?): CategoryType =
            id?.let { value -> entries.firstOrNull { it.id == value } } ?: OTHER
    }
}
