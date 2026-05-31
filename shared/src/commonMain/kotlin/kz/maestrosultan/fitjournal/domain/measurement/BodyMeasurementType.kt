package kz.maestrosultan.fitjournal.domain.measurement

/**
 * Body-measurement column on the Journal screen — what the user is tracking.
 *
 * `id` is the canonical wire/DB string used by Parse, AWS, and SQLite. iOS
 * historically wrote `rawValue` (the camelCase name); Android historically
 * wrote a snake→camel transform of `name`. Pinning the id explicitly here
 * removes the platform skew. The plural `PHOTOS` preserves the iOS string
 * so legacy iOS rows continue to round-trip; Android's pre-FJ-2.0 rows
 * stored the singular `"photo"` and need a one-off migration handled in
 * the data layer.
 */
enum class BodyMeasurementType(val id: String) {
    WEIGHT("weight"),
    FAT_PERCENTAGE("fatPercentage"),
    PHOTOS("photos"),
    NECK("neck"),
    CHEST("chest"),
    SHOULDERS("shoulders"),
    RIGHT_HAND("rightHand"),
    LEFT_HAND("leftHand"),
    WAIST("waist"),
    RIGHT_THIGH("rightThigh"),
    LEFT_THIGH("leftThigh"),
    RIGHT_CALF("rightCalf"),
    LEFT_CALF("leftCalf");

    val group: Group
        get() = when (this) {
            WEIGHT, FAT_PERCENTAGE, PHOTOS -> Group.COMMON
            else -> Group.BODY
        }

    val unit: BodyMeasurementUnit
        get() = when (this) {
            WEIGHT -> BodyMeasurementUnit.WEIGHT
            FAT_PERCENTAGE -> BodyMeasurementUnit.PERCENTAGE
            PHOTOS -> BodyMeasurementUnit.PHOTO
            else -> BodyMeasurementUnit.SIZE
        }

    enum class Group(val priority: Int) {
        COMMON(0),
        BODY(1),
    }

    companion object {
        fun create(id: String?): BodyMeasurementType? {
            if (id == null) return null
            // Pre-FJ-2.0 Android rows stored the singular `"photo"`; map
            // them to the unified plural so legacy data round-trips
            // without a separate SQL migration.
            val canonical = if (id == "photo") PHOTOS.id else id
            return entries.firstOrNull { it.id == canonical }
        }
    }
}
