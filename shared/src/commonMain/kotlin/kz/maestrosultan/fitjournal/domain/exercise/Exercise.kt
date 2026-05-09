package kz.maestrosultan.fitjournal.domain.exercise

import kz.maestrosultan.fitjournal.domain.workout.ResultType

/**
 * A catalog exercise (global seeded row or this user's custom). The domain
 * shape only carries fields the UI/use cases consume — sync metadata
 * (`pendingUpload`, `deletedAt`, `userId` on customs, `isGlobal`) lives on
 * `DBExerciseObject`.
 *
 * `uuid` is the local SQLite primary key. `remoteId` is the AWS/Parse
 * object id; before sync lands, a brand-new local exercise has a `uuid`
 * but no `remoteId`.
 */
data class Exercise(
    val uuid: String,
    val remoteId: String?,
    val name: String,
    val details: String?,
    val primaryCategory: Category,
    val secondaryCategories: List<Category>,
    val image1: String?,
    val image2: String?,
    val resultType: ResultType,
    val isPersonal: Boolean,
)

val Exercise.allCategories: List<Category>
    get() = listOf(primaryCategory) + secondaryCategories

val Exercise.hasImage: Boolean
    get() = image1 != null
