package kz.maestrosultan.fitjournal.kmp.sqldelight

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.SynchronousFlag
import kz.maestrosultan.fitjournal.kmp.FitJournalDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // Tuning notes (per-connection PRAGMAs are applied via the
        // sqliter DatabaseConfiguration knobs below — sqliter handles
        // them on every connection it opens, both initial and pool):
        //
        // - foreignKeyConstraints=true: required for the ON DELETE CASCADE
        //   rules on workoutExercises/workoutSets to fire.
        // - maxReaderConnections=4: sqliter defaults to a single shared
        //   connection, which serializes every SELECT. With the sync
        //   worker pushing hundreds of records (each fetching a full
        //   tree before upload), foreground reads (opening exercise
        //   details, scrolling the list) queue behind it and hang.
        //   WAL + 4 readers lets concurrent SELECTs run in parallel;
        //   the single writer slot still serializes writes (correct).
        // - synchronousFlag=NORMAL: WAL+NORMAL is the standard high-perf
        //   setting. ~2-5x faster commits than the default FULL with
        //   the same crash safety in WAL mode (you can lose the most
        //   recent unsynced commit on power loss; corruption-free).
        //   journalMode is already WAL by default in sqliter.
        return NativeSqliteDriver(
            schema = FitJournalDatabase.Schema,
            name = "FitJournalDatabase.db",
            maxReaderConnections = 4,
            onConfiguration = { config: DatabaseConfiguration ->
                config.copy(
                    extendedConfig = config.extendedConfig.copy(
                        foreignKeyConstraints = true,
                        synchronousFlag = SynchronousFlag.NORMAL
                    )
                )
            }
        )
    }
}
