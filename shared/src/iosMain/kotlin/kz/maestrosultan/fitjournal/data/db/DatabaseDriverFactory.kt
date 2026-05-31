package kz.maestrosultan.fitjournal.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.SynchronousFlag
import kz.maestrosultan.fitjournal.data.db.FitJournalDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        // Tuning notes (per-connection PRAGMAs are applied via the
        // sqliter DatabaseConfiguration knobs below — sqliter handles
        // them on every connection it opens, both initial and pool):
        //
        // - foreignKeyConstraints=true: required for the ON DELETE CASCADE
        //   rules on workoutExercises/workoutSets to fire.
        // - journalMode=DELETE: we tried WAL and hit repeated data loss
        //   on iOS after app suspend-kill. After a successful tick
        //   (`upserted=1145 ... — OK`), a relaunch found 3 MB of pages
        //   in `.db-wal` that SQLite refused to apply — main DB only
        //   carried schema + journals (956 KB), workout queries returned
        //   0. DELETE mode writes every commit straight to the main DB
        //   with an fsync; no WAL to leave stranded.
        // - maxReaderConnections=4: required to avoid a nested-query
        //   deadlock. Several reads in this codebase (e.g.
        //   `getExerciseByUuid`) make a synchronous category
        //   lookup INSIDE the row mapper of the outer SELECT —
        //   i.e. a SQL call while the prepared statement is still
        //   mid-iteration. With a single connection the inner call
        //   blocks forever waiting for the connection the outer
        //   iteration is holding; the entire app then loses DB access.
        //   DELETE mode allows multiple READ connections (writes still
        //   serialize via file lock), so 4 readers safely cover the
        //   nested-call pattern without bringing WAL semantics back.
        // - synchronousFlag=NORMAL: in DELETE journal mode this still
        //   syncs the rollback journal at commit. FULL is one step
        //   safer (also fsyncs after journal deletion) but adds
        //   latency without changing crash-safety in practice.
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
