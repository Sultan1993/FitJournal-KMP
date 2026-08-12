package kz.maestrosultan.fitjournal.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.SynchronousFlag
import kz.maestrosultan.fitjournal.data.db.FitJournalDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // Per-connection PRAGMAs, applied via sqliter's DatabaseConfiguration (covers every
        // connection it opens, initial + pool):
        // - foreignKeyConstraints=true: required for ON DELETE CASCADE on workoutExercises/workoutSets.
        // - journalMode=DELETE (not WAL): WAL caused real data loss after iOS app suspend-kill —
        //   a relaunch after a successful tick found 3 MB of `.db-wal` pages SQLite refused to
        //   apply, so workout queries returned 0. DELETE fsyncs every commit straight to the main DB.
        // - maxReaderConnections=4: avoids a nested-query deadlock — some reads (e.g.
        //   `getExerciseByUuid`) run a synchronous lookup INSIDE the outer SELECT's row mapper, which
        //   deadlocks with a single connection. DELETE mode allows multiple readers (writes still
        //   serialize via file lock).
        // - synchronousFlag=NORMAL: still syncs the rollback journal at commit in DELETE mode;
        //   FULL is marginally safer but adds latency for no real crash-safety gain here.
        return NativeSqliteDriver(
            schema = FitJournalDatabase.Schema,
            name = "FitJournalDatabase.db",
            maxReaderConnections = 4,
            onConfiguration = { config: DatabaseConfiguration ->
                config.copy(
                    journalMode = JournalMode.DELETE,
                    extendedConfig = config.extendedConfig.copy(
                        foreignKeyConstraints = true,
                        synchronousFlag = SynchronousFlag.NORMAL
                    )
                )
            }
        )
    }
}
