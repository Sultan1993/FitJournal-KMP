package kz.maestrosultan.fitjournal.data.db

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/**
 * Drops every row in every user-scoped table, in one transaction. Runs once
 * at the start of the FJ2.0 migration gate to clear stale FJ1.x rows keyed
 * by the legacy Parse `objectId` userId; AWSUserMigrator then restamps
 * `User.userId` to the awsUserId and `LocalDbHydrationMigrator` repopulates
 * from AWS. Unconditional (not scoped to a userId) because matching FJ1.x
 * parseUserId across two upgrade paths is brittle, and hydration recreates
 * everything anyway. Safe: pre-FJ2.0 writes went straight to Parse, so local
 * SQLite never held `pendingUpload=1` rows, and fresh FJ2.0 installs don't
 * reach this path before sign-in.
 */
interface LocalDbWiper {
    suspend fun wipeAll()
}

class DefaultLocalDbWiper(
    private val database: FitJournalDatabase,
) : LocalDbWiper {

    override suspend fun wipeAll() = withContext(Dispatchers.IO) {
        // FK-leaf-first order (children before parents) so DELETEs don't trip
        // a constraint even if `PRAGMA foreign_keys` is OFF on some path.
        database.transaction {
            database.workoutSetsQueries.wipeAll()
            database.workoutExercisesQueries.wipeAll()
            database.workoutRecordsQueries.wipeAll()
            database.workoutSessionsQueries.wipeAll()
            database.workoutNotesQueries.wipeAll()
            database.journalsQueries.wipeAll()
            database.notesQueries.wipeAll()
            database.bodyMeasurementsQueries.wipeAll()
            database.exercisesQueries.wipeAll()
            database.categoryQueries.wipeAll()
        }
    }
}
