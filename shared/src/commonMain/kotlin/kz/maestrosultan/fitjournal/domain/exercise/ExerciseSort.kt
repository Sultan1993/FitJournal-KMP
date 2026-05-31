package kz.maestrosultan.fitjournal.domain.exercise

/**
 * Canonical display order for [Exercise] lists across the app:
 * personal exercises first, then alphabetical by locale-resolved
 * `name`. Used by both the full exercise list and the by-category
 * list on Android; previously duplicated in two use cases.
 */
fun List<Exercise>.sortedByDisplayOrder(): List<Exercise> =
    sortedWith(compareByDescending<Exercise> { it.isPersonal }.thenBy { it.name })
