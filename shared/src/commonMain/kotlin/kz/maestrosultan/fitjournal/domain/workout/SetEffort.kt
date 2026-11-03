package kz.maestrosultan.fitjournal.domain.workout

import kotlinx.serialization.Serializable

/**
 * How hard a set felt, as the user tags it. Optional: an untagged set is
 * [NORMAL], so "Normal" and "never tagged" are the same thing — anything that
 * reads this should key off [EASY] / [HARD] only.
 *
 * Not the legacy FJ1.x `difficultyType` (Light/Medium/Hard): that history is
 * deliberately not carried over. Stable ids — SQLite stores [id]; the sync
 * payload stores the enum name, and [NORMAL] is omitted from it entirely.
 */
@Serializable
enum class SetEffort(val id: Int) {
    NORMAL(0),
    EASY(1),
    HARD(2);

    companion object {
        /** Unknown ids (a newer client's tag) read as [NORMAL]. */
        fun fromId(id: Int): SetEffort = entries.firstOrNull { it.id == id } ?: NORMAL
    }
}
