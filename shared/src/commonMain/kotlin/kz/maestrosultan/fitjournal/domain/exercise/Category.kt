package kz.maestrosultan.fitjournal.domain.exercise

/**
 * Muscle-group catalog row.
 *
 * `uuid` is the local SQLite primary key; `remoteId` is the AWS/Parse
 * object id. They are different by design: a category created locally
 * before sync has a uuid but no remoteId.
 */
data class Category(
    val uuid: String,
    val remoteId: String,
    val name: String,
    val type: CategoryType,
    val details: String?,
)
