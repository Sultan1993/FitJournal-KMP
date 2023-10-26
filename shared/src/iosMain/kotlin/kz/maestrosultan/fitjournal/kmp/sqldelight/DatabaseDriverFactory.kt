package kz.maestrosultan.fitjournal.kmp.sqldelight

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kz.maestrosultan.fitjournal.kmp.FitJournal

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(FitJournal.Schema, "FitJournal.db")
    }
}
