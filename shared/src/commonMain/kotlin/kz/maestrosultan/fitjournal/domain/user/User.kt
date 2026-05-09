package kz.maestrosultan.fitjournal.domain.user

import kotlin.time.Instant

/**
 * Domain model of the signed-in user. Mutable session state — the
 * currently selected diary, the active subscription, the firebase token —
 * lives outside this type in platform-side stores; this type stays a
 * pure value object so it round-trips through SQLite and AWS without
 * extra plumbing.
 */
data class User(
    val id: String,
    /**
     * Firebase Auth uid — the sync key into AWSUser. Required for every
     * signed-in account; account flows must populate this before
     * constructing a `User`. Pre-FJ-2.0 iOS code allowed nil here, but
     * those rows never round-tripped to AWS so they're not in scope for
     * the unified domain.
     */
    val firebaseUserId: String,
    val name: String,
    val email: String?,
    val measurementSystem: MeasurementSystem,
    val lengthMeasurementSystem: LengthMeasurementSystem,
    /**
     * When the AWS account was created. Nullable because pre-FJ-2.0
     * Android installs persisted no creation timestamp, and we don't want
     * to fabricate one on first migrated launch. Populated for all rows
     * once the user has signed in against AWS at least once.
     */
    val createdDate: Instant?,
)
