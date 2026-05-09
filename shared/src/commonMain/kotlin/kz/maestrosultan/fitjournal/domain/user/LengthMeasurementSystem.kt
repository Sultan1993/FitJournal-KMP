package kz.maestrosultan.fitjournal.domain.user

/**
 * User-selected length measurement system (used for body measurements
 * like height / waist / etc). Stable string `id`s map to AWS user prefs;
 * numeric `value` is legacy persistence.
 *
 * Localized titles live as platform extensions:
 *  - iOS:     `extension LengthMeasurementSystem { var title, titleShort: String }`
 *  - Android: `val LengthMeasurementSystem.titleResId / titleShortResId: Int`
 */
enum class LengthMeasurementSystem(val value: Int) {
    CENTIMETERS(0),
    INCHES(1);

    val id: String
        get() = when (this) {
            CENTIMETERS -> "centimeters"
            INCHES -> "inches"
        }

    companion object {
        fun create(value: String?): LengthMeasurementSystem = when (value) {
            CENTIMETERS.id -> CENTIMETERS
            INCHES.id -> INCHES
            else -> CENTIMETERS
        }
    }
}
