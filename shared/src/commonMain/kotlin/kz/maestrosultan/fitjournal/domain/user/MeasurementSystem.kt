package kz.maestrosultan.fitjournal.domain.user

/**
 * User-selected weight + distance measurement system. Stable string `id`s
 * (`"kgKm"` / `"lbMi"`) map to AWS user prefs; the numeric `value` is
 * legacy persistence.
 *
 * `weightMultiplier` is the increment used by the +/- step buttons in the
 * weight picker. Pure numeric, lives in shared.
 *
 * Localized titles live as platform extensions:
 *  - iOS:     `extension MeasurementSystem { var title: String, weightTitleShort, distanceTitleShort }`
 *  - Android: `val MeasurementSystem.titleResId: Int`, etc.
 */
enum class MeasurementSystem(val value: Int) {
    KG_KM(0),
    LB_MI(1);

    val id: String
        get() = when (this) {
            KG_KM -> "kgKm"
            LB_MI -> "lbMi"
        }

    val weightMultiplier: Double
        get() = when (this) {
            KG_KM -> 0.25
            LB_MI -> 0.5
        }

    companion object {
        fun create(value: String?): MeasurementSystem = when (value) {
            KG_KM.id -> KG_KM
            LB_MI.id -> LB_MI
            else -> KG_KM
        }
    }
}
