package kz.maestrosultan.fitjournal.kmp.sqldelight

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kz.maestrosultan.fitjournal.kmp.FitJournalDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(FitJournalDatabase.Schema, "FitJournalDatabase.db")
    }
}
