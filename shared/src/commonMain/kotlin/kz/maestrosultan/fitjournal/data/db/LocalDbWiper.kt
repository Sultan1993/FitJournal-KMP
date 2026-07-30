package kz.maestrosultan.fitjournal.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Drops every row in every user-scoped local SQLite table in a single
 * transaction. Used exactly once at the start of the FJ2.0 migration gate
 * to clear stale FJ1.x rows keyed by the legacy Parse `objectId` userId
 * — after the wipe, AWSUserMigrator stamps `User.userId` to the awsUserId
 * and `LocalDbHydrationMigrator` repopulates the DB from AWS under the
 * new key. The wipe is unconditional: every row in every table is dropped
 * (not scoped to a userId), because matching the FJ1.x parseUserId
 * across two upgrade paths is brittle and the post-wipe hydration
 * re-creates everything the user can see anyway.
 *
 * Pre-FJ-2.0 the app wrote to Parse directly, so the local SQLite never
 * accumulated `pendingUpload=1` rows on FJ1.x. True fresh installs of
 * FJ2.0 don't reach the wipe codepath until after sign-in (no
 * opportunity for offline writes yet). It is therefore safe to wipe
 * unconditionally on the not-yet-hydrated branch.
 */
interface LocalDbWiper {
    suspend fun wipeAll()
}

class DefaultLocalDbWiper(
    private val database: FitJournalDatabase,
) : LocalDbWiper {

    override suspend fun wipeAll() = withContext(Dispatchers.IO) {
        // Single transaction so a mid-wipe crash leaves the DB consistent
        // (all-or-nothing). Order is FK-leaf-first: child tables before
        // parents, so even if `PRAGMA foreign_keys` is OFF in some code
        // path the DELETEs don't trip a constraint or orphan rows.
        database.transaction {
            database.workoutSetsQueries.wipeAll()
            database.workoutExercisesQueries.wipeAll()
            database.workoutRecordsQueries.wipeAll()
            database.workoutSessionsQueries.wipeAll()
            database.journalsQueries.wipeAll()
            database.notesQueries.wipeAll()
            database.bodyMeasurementsQueries.wipeAll()
            database.exercisesQueries.wipeAll()
            database.categoryQueries.wipeAll()
        }
    }
}
