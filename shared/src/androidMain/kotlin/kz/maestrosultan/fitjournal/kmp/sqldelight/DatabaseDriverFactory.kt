package kz.maestrosultan.fitjournal.kmp.sqldelight

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kz.maestrosultan.fitjournal.kmp.FitJournal

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(FitJournal.Schema, context, "FitJournal.db")
    }
}
